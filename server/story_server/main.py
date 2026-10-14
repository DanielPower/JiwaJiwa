"""
The story server. The app signs in with Google, then sends its Claude
conversations here instead of to the Claude API, so the API key never leaves
the server. Each Google account is rate limited.
"""

import logging
from dataclasses import dataclass
from typing import Any, Callable, Literal

import anthropic
from fastapi import Depends, FastAPI, Request
from fastapi.concurrency import run_in_threadpool
from fastapi.responses import JSONResponse, Response
from pydantic import BaseModel, Field, ValidationError

from .config import Config
from .store import DAY, HOUR, Account, Store, Usage

log = logging.getLogger("story_server")

# Big enough for a long story, its rewrites and their thinking blocks.
MAX_BODY_BYTES = 1_000_000


class SignIn(BaseModel):
    id_token: str


class Message(BaseModel):
    role: Literal["user", "assistant"]
    # A string for the app's turns, or Claude's content blocks sent back unchanged.
    content: str | list[dict[str, Any]]


class Conversation(BaseModel):
    system: str = Field(max_length=50_000)
    messages: list[Message] = Field(min_length=1, max_length=24)
    effort: Literal["low", "medium", "high"] = "high"


@dataclass(frozen=True)
class ClaudeReply:
    status: int
    body: bytes
    tokens: int


class ApiError(Exception):
    def __init__(self, status: int, kind: str, message: str, headers: dict[str, str] | None = None):
        self.status, self.kind, self.message, self.headers = status, kind, message, headers


def verify_google_token(client_ids: tuple[str, ...]) -> Callable[[str], tuple[str, str]]:
    from google.auth.transport import requests as google_requests
    from google.oauth2 import id_token

    def verify(token: str) -> tuple[str, str]:
        claims = id_token.verify_oauth2_token(token, google_requests.Request(), audience=list(client_ids))
        return claims["sub"], claims.get("email", "")

    return verify


def claude_client(config: Config) -> Callable[[Conversation], ClaudeReply]:
    # A long story with thinking can take a few minutes.
    client = anthropic.Anthropic(api_key=config.anthropic_api_key, timeout=600, max_retries=2)

    def send(conversation: Conversation) -> ClaudeReply:
        raw = client.beta.messages.with_raw_response.create(
            model=config.model,
            max_tokens=config.max_tokens,
            system=conversation.system,
            messages=[m.model_dump() for m in conversation.messages],
            output_config={"effort": conversation.effort},
            # If a safety classifier declines, the API retries on its recommended fallback model.
            fallbacks="default",
            betas=["server-side-fallback-2026-07-01"],
        )
        usage = raw.parse().usage
        tokens = (
            usage.input_tokens
            + usage.output_tokens
            + (usage.cache_creation_input_tokens or 0)
            + (usage.cache_read_input_tokens or 0)
        )
        # The app reads the Messages API response as is.
        return ClaudeReply(200, raw.http_response.content, tokens)

    return send


def create_app(
    config: Config,
    store: Store | None = None,
    verify_google: Callable[[str], tuple[str, str]] | None = None,
    claude: Callable[[Conversation], ClaudeReply] | None = None,
) -> FastAPI:
    store = store or Store(config.database_path)
    verify_google = verify_google or verify_google_token(config.google_client_ids)
    claude = claude or claude_client(config)

    app = FastAPI(title="Jiwa Jiwa story server", docs_url=None, redoc_url=None)

    @app.exception_handler(ApiError)
    async def api_error(_: Request, e: ApiError):
        # Shaped like the Claude API's errors, which the app already reads.
        return JSONResponse(
            {"type": "error", "error": {"type": e.kind, "message": e.message}},
            status_code=e.status,
            headers=e.headers,
        )

    def bearer(request: Request) -> str:
        header = request.headers.get("authorization", "")
        if not header.lower().startswith("bearer "):
            raise ApiError(401, "authentication_error", "Sign in to write stories.")
        return header[7:].strip()

    def account(token: str = Depends(bearer)) -> Account:
        found = store.account_for(token)
        if found is None:
            raise ApiError(401, "authentication_error", "Your sign-in has expired. Sign in again.")
        if found.blocked:
            raise ApiError(403, "permission_error", "This account can't write stories.")
        return found

    def usage_json(usage: Usage) -> dict:
        return {
            "requests_last_hour": {"used": usage.requests_last_hour, "limit": config.requests_per_hour},
            "tokens_last_day": {"used": usage.tokens_last_day, "limit": config.tokens_per_day},
        }

    @app.get("/healthz")
    def healthz():
        return {"ok": True}

    @app.post("/v1/sessions")
    def sign_in(body: SignIn):
        try:
            sub, email = verify_google(body.id_token)
        except Exception as e:  # google-auth raises ValueError and friends for bad tokens
            log.info("rejected Google token: %s", e)
            raise ApiError(401, "authentication_error", "Google sign-in failed. Try again.")
        token, expires_at = store.sign_in(sub, email, config.session_days)
        return {"token": token, "expires_at": expires_at, "email": email}

    @app.delete("/v1/sessions", status_code=204)
    def sign_out(token: str = Depends(bearer)):
        store.sign_out(token)
        return Response(status_code=204)

    @app.get("/v1/usage")
    def usage(who: Account = Depends(account)):
        return {"email": who.email, **usage_json(store.usage(who.google_sub))}

    @app.post("/v1/messages")
    async def messages(request: Request, who: Account = Depends(account)):
        body = await request.body()
        if len(body) > MAX_BODY_BYTES:
            raise ApiError(413, "request_too_large", "This story is too long to continue.")
        try:
            conversation = Conversation.model_validate_json(body)
        except ValidationError as e:
            raise ApiError(400, "invalid_request_error", f"Bad request: {e.errors()[0]['msg']}")

        refusal: list[ApiError] = []

        def admit(usage: Usage) -> bool:
            if usage.in_flight >= config.max_in_flight:
                refusal.append(ApiError(429, "rate_limit_error", "Another story is still being written. Try again when it's done.", {"retry-after": "30"}))
            elif usage.requests_last_hour >= config.requests_per_hour:
                refusal.append(ApiError(429, "rate_limit_error", "You've written a lot of stories this hour. Try again later.", {"retry-after": str(HOUR // 4)}))
            elif usage.tokens_last_day >= config.tokens_per_day:
                refusal.append(ApiError(429, "rate_limit_error", "You've reached today's story limit. Try again tomorrow.", {"retry-after": str(DAY // 4)}))
            return not refusal

        request_id = store.begin_request(who.google_sub, admit)
        if request_id is None:
            raise refusal[0]

        tokens = 0
        try:
            reply = await run_in_threadpool(claude, conversation)
            tokens = reply.tokens
            return Response(reply.body, status_code=reply.status, media_type="application/json")
        except anthropic.APIStatusError as e:
            log.warning("Claude API error %s: %s", e.status_code, e.message)
            if e.status_code == 400:
                raise ApiError(400, "invalid_request_error", e.message)
            if e.status_code in (429, 529) or e.status_code >= 500:
                raise ApiError(503, "overloaded_error", "Claude is busy right now. Try again in a minute.")
            raise ApiError(502, "api_error", "The story server couldn't reach Claude.")
        except anthropic.APIConnectionError as e:
            log.warning("couldn't reach Claude: %s", e)
            raise ApiError(502, "api_error", "The story server couldn't reach Claude.")
        finally:
            store.finish_request(request_id, tokens)

    return app


def app() -> FastAPI:
    """Entry point for uvicorn --factory."""
    logging.basicConfig(level=logging.INFO)
    return create_app(Config.from_env())
