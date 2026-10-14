import json

import anthropic
import httpx
import pytest
from fastapi.testclient import TestClient

from story_server.config import Config
from story_server.main import ClaudeReply, create_app
from story_server.store import DAY, HOUR, Store

CONVERSATION = {"system": "Write a story.", "messages": [{"role": "user", "content": "Go"}], "effort": "high"}
REPLY = {"content": [{"type": "text", "text": "むかしむかし"}], "stop_reason": "end_turn"}


class Clock:
    def __init__(self):
        self.now = 1_800_000_000.0

    def __call__(self):
        return self.now


class FakeClaude:
    def __init__(self, tokens=1000):
        self.tokens = tokens
        self.sent = []
        self.error = None

    def __call__(self, conversation):
        self.sent.append(conversation)
        if self.error:
            raise self.error
        return ClaudeReply(200, json.dumps(REPLY).encode(), self.tokens)


def google(token):
    if not token.startswith("good:"):
        raise ValueError("bad token")
    sub = token.removeprefix("good:")
    return sub, f"{sub}@example.com"


@pytest.fixture
def clock():
    return Clock()


@pytest.fixture
def claude():
    return FakeClaude()


@pytest.fixture
def config():
    return Config("key", ("client",), requests_per_hour=3, tokens_per_day=5000, max_in_flight=2)


@pytest.fixture
def client(tmp_path, clock, claude, config):
    store = Store(str(tmp_path / "test.db"), clock=clock)
    return TestClient(create_app(config, store=store, verify_google=google, claude=claude))


def sign_in(client, who="alice"):
    response = client.post("/v1/sessions", json={"id_token": f"good:{who}"})
    assert response.status_code == 200
    return {"authorization": f"Bearer {response.json()['token']}"}


def test_a_google_token_buys_a_session(client):
    response = client.post("/v1/sessions", json={"id_token": "good:alice"})
    assert response.status_code == 200
    assert response.json()["email"] == "alice@example.com"
    assert len(response.json()["token"]) > 30


def test_a_bad_google_token_is_rejected(client):
    response = client.post("/v1/sessions", json={"id_token": "forged"})
    assert response.status_code == 401
    assert response.json()["error"]["type"] == "authentication_error"


def test_messages_need_a_session(client):
    assert client.post("/v1/messages", json=CONVERSATION).status_code == 401
    bad = {"authorization": "Bearer nope"}
    assert client.post("/v1/messages", json=CONVERSATION, headers=bad).status_code == 401


def test_messages_return_claudes_reply_unchanged(client, claude):
    response = client.post("/v1/messages", json=CONVERSATION, headers=sign_in(client))
    assert response.status_code == 200
    assert response.json() == REPLY
    assert claude.sent[0].effort == "high"


def test_thinking_blocks_are_passed_back(client, claude):
    conversation = {
        "system": "s",
        "messages": [
            {"role": "user", "content": "Go"},
            {"role": "assistant", "content": [{"type": "thinking", "thinking": "", "signature": "abc"}, {"type": "text", "text": "hi"}]},
            {"role": "user", "content": "Again"},
        ],
    }
    response = client.post("/v1/messages", json=conversation, headers=sign_in(client))
    assert response.status_code == 200
    assert claude.sent[0].messages[1].content[0]["signature"] == "abc"


def test_requests_the_server_wont_send_are_rejected(client):
    headers = sign_in(client)
    for bad in (
        {**CONVERSATION, "effort": "max"},
        {**CONVERSATION, "messages": []},
        {**CONVERSATION, "messages": [{"role": "system", "content": "x"}]},
    ):
        assert client.post("/v1/messages", json=bad, headers=headers).status_code == 400
    huge = {**CONVERSATION, "messages": [{"role": "user", "content": "あ" * 400_000}]}
    assert client.post("/v1/messages", json=huge, headers=headers).status_code == 413


def test_requests_per_hour_are_limited_per_account(client, clock):
    alice = sign_in(client, "alice")
    for _ in range(3):
        assert client.post("/v1/messages", json=CONVERSATION, headers=alice).status_code == 200
    response = client.post("/v1/messages", json=CONVERSATION, headers=alice)
    assert response.status_code == 429
    assert "retry-after" in response.headers
    # Someone else isn't affected.
    assert client.post("/v1/messages", json=CONVERSATION, headers=sign_in(client, "bob")).status_code == 200
    clock.now += HOUR + 1
    assert client.post("/v1/messages", json=CONVERSATION, headers=alice).status_code == 200


def test_tokens_per_day_are_limited(client, clock, claude):
    alice = sign_in(client)
    claude.tokens = 5000
    assert client.post("/v1/messages", json=CONVERSATION, headers=alice).status_code == 200
    clock.now += HOUR + 1
    response = client.post("/v1/messages", json=CONVERSATION, headers=alice)
    assert response.status_code == 429
    assert "today" in response.json()["error"]["message"]
    clock.now += DAY
    assert client.post("/v1/messages", json=CONVERSATION, headers=alice).status_code == 200


def test_usage_reports_what_is_left(client, claude):
    alice = sign_in(client)
    client.post("/v1/messages", json=CONVERSATION, headers=alice)
    usage = client.get("/v1/usage", headers=alice).json()
    assert usage["tokens_last_day"] == {"used": 1000, "limit": 5000}
    assert usage["requests_last_hour"] == {"used": 1, "limit": 3}


def test_too_many_requests_at_once_are_refused(tmp_path, clock, config):
    store = Store(str(tmp_path / "t.db"), clock=clock)
    store.sign_in("alice", "a@example.com", 1)
    assert store.begin_request("alice", lambda u: u.in_flight < 2) is not None
    assert store.begin_request("alice", lambda u: u.in_flight < 2) is not None
    assert store.begin_request("alice", lambda u: u.in_flight < 2) is None
    # Requests stuck unfinished stop counting after a while.
    clock.now += 20 * 60
    assert store.begin_request("alice", lambda u: u.in_flight < 2) is not None


def test_a_failed_claude_call_still_counts_but_costs_no_tokens(client, claude):
    alice = sign_in(client)
    request = httpx.Request("POST", "https://api.anthropic.com/v1/messages")
    claude.error = anthropic.InternalServerError("boom", response=httpx.Response(529, request=request), body=None)
    response = client.post("/v1/messages", json=CONVERSATION, headers=alice)
    assert response.status_code == 503
    usage = client.get("/v1/usage", headers=alice).json()
    assert usage["requests_last_hour"]["used"] == 1
    assert usage["tokens_last_day"]["used"] == 0


def test_signing_out_ends_the_session(client):
    alice = sign_in(client)
    assert client.delete("/v1/sessions", headers=alice).status_code == 204
    assert client.get("/v1/usage", headers=alice).status_code == 401


def test_sessions_expire(client, clock, config):
    alice = sign_in(client)
    clock.now += config.session_days * DAY + 1
    assert client.get("/v1/usage", headers=alice).status_code == 401


def test_blocked_accounts_are_refused(client, tmp_path):
    alice = sign_in(client)
    import sqlite3

    db = sqlite3.connect(tmp_path / "test.db")
    db.execute("UPDATE accounts SET blocked = 1")
    db.commit()
    assert client.post("/v1/messages", json=CONVERSATION, headers=alice).status_code == 403
