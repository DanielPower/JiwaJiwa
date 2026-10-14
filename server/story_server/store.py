import hashlib
import secrets
import sqlite3
import threading
import time
from dataclasses import dataclass

SCHEMA = """
CREATE TABLE IF NOT EXISTS accounts (
    google_sub TEXT PRIMARY KEY,
    email TEXT NOT NULL,
    created_at INTEGER NOT NULL,
    -- Set by hand (sqlite3) to cut off an account that's abusing the service.
    blocked INTEGER NOT NULL DEFAULT 0
);
CREATE TABLE IF NOT EXISTS sessions (
    token_hash TEXT PRIMARY KEY,
    google_sub TEXT NOT NULL REFERENCES accounts(google_sub),
    expires_at INTEGER NOT NULL
);
CREATE TABLE IF NOT EXISTS requests (
    id INTEGER PRIMARY KEY,
    google_sub TEXT NOT NULL,
    started_at INTEGER NOT NULL,
    finished INTEGER NOT NULL DEFAULT 0,
    tokens INTEGER NOT NULL DEFAULT 0
);
CREATE INDEX IF NOT EXISTS requests_by_account ON requests(google_sub, started_at);
"""

HOUR = 3600
DAY = 24 * HOUR
# A request still marked unfinished after this long is assumed lost (say, the
# server restarted mid-request) and stops counting as in flight.
STALE_AFTER = 15 * 60


@dataclass(frozen=True)
class Account:
    google_sub: str
    email: str
    blocked: bool


@dataclass(frozen=True)
class Usage:
    requests_last_hour: int
    tokens_last_day: int
    in_flight: int


class Store:
    """SQLite storage for accounts, sessions and per-account usage."""

    def __init__(self, path: str, clock=time.time):
        self._db = sqlite3.connect(path, check_same_thread=False, isolation_level=None)
        self._db.execute("PRAGMA journal_mode=WAL")
        self._db.executescript(SCHEMA)
        # Admission checks and the insert that follows them must not interleave.
        self._lock = threading.Lock()
        self._clock = clock

    def now(self) -> int:
        return int(self._clock())

    def sign_in(self, google_sub: str, email: str, session_days: int) -> tuple[str, int]:
        """Record the account and start a session. Returns the token and its expiry."""
        now = self.now()
        token = secrets.token_urlsafe(32)
        expires_at = now + session_days * DAY
        with self._lock:
            self._db.execute(
                "INSERT INTO accounts (google_sub, email, created_at) VALUES (?, ?, ?) "
                "ON CONFLICT(google_sub) DO UPDATE SET email = excluded.email",
                (google_sub, email, now),
            )
            self._db.execute(
                "INSERT INTO sessions (token_hash, google_sub, expires_at) VALUES (?, ?, ?)",
                (_hash(token), google_sub, expires_at),
            )
            self._db.execute("DELETE FROM sessions WHERE expires_at < ?", (now,))
        return token, expires_at

    def sign_out(self, token: str) -> None:
        with self._lock:
            self._db.execute("DELETE FROM sessions WHERE token_hash = ?", (_hash(token),))

    def account_for(self, token: str) -> Account | None:
        row = self._db.execute(
            "SELECT a.google_sub, a.email, a.blocked FROM sessions s "
            "JOIN accounts a ON a.google_sub = s.google_sub "
            "WHERE s.token_hash = ? AND s.expires_at >= ?",
            (_hash(token), self.now()),
        ).fetchone()
        return Account(row[0], row[1], bool(row[2])) if row else None

    def usage(self, google_sub: str) -> Usage:
        now = self.now()
        row = self._db.execute(
            "SELECT "
            "  COALESCE(SUM(started_at >= ?), 0), "
            "  COALESCE(SUM(CASE WHEN started_at >= ? THEN tokens END), 0), "
            "  COALESCE(SUM(finished = 0 AND started_at >= ?), 0) "
            "FROM requests WHERE google_sub = ? AND started_at >= ?",
            (now - HOUR, now - DAY, now - STALE_AFTER, google_sub, now - DAY),
        ).fetchone()
        return Usage(row[0], row[1], row[2])

    def begin_request(self, google_sub: str, admit) -> int | None:
        """Start a request if `admit(usage)` allows it. Returns its id, or None."""
        with self._lock:
            if not admit(self.usage(google_sub)):
                return None
            cursor = self._db.execute(
                "INSERT INTO requests (google_sub, started_at) VALUES (?, ?)",
                (google_sub, self.now()),
            )
            return cursor.lastrowid

    def finish_request(self, request_id: int, tokens: int) -> None:
        with self._lock:
            self._db.execute(
                "UPDATE requests SET finished = 1, tokens = ? WHERE id = ?", (tokens, request_id)
            )
            # Keep two days, enough for the 24-hour window.
            self._db.execute("DELETE FROM requests WHERE started_at < ?", (self.now() - 2 * DAY,))


def _hash(token: str) -> str:
    return hashlib.sha256(token.encode()).hexdigest()
