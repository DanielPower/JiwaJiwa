import os
from dataclasses import dataclass


@dataclass(frozen=True)
class Config:
    anthropic_api_key: str
    # OAuth client IDs whose Google ID tokens we accept. The app asks Credential
    # Manager for a token addressed to the web client ID, so that's usually the only one.
    google_client_ids: tuple[str, ...]
    database_path: str = "/data/story-server.db"
    model: str = "claude-sonnet-5-5"
    max_tokens: int = 16000
    # Per Google account. A story is one request plus any rewrites, and one more
    # for the sentence translations.
    requests_per_hour: int = 20
    # Input plus output tokens over the last 24 hours, per Google account.
    # A medium story is roughly 20,000 to 40,000 tokens.
    tokens_per_day: int = 200_000
    # Requests one account may have waiting on Claude at once.
    max_in_flight: int = 2
    session_days: int = 180

    @staticmethod
    def from_env() -> "Config":
        env = os.environ
        ids = tuple(i.strip() for i in env.get("GOOGLE_CLIENT_IDS", "").split(",") if i.strip())
        if not env.get("ANTHROPIC_API_KEY"):
            raise SystemExit("ANTHROPIC_API_KEY is not set")
        if not ids:
            raise SystemExit("GOOGLE_CLIENT_IDS is not set")
        defaults = Config("", ())
        return Config(
            anthropic_api_key=env["ANTHROPIC_API_KEY"],
            google_client_ids=ids,
            database_path=env.get("DATABASE_PATH", defaults.database_path),
            model=env.get("CLAUDE_MODEL", defaults.model),
            max_tokens=int(env.get("CLAUDE_MAX_TOKENS", defaults.max_tokens)),
            requests_per_hour=int(env.get("REQUESTS_PER_HOUR", defaults.requests_per_hour)),
            tokens_per_day=int(env.get("TOKENS_PER_DAY", defaults.tokens_per_day)),
            max_in_flight=int(env.get("MAX_IN_FLIGHT", defaults.max_in_flight)),
            session_days=int(env.get("SESSION_DAYS", defaults.session_days)),
        )
