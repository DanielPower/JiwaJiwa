# Story server

The app's stories are written by Claude. Rather than each user bringing their
own Claude API key, the app signs in with Google and sends its Claude
requests here. The server holds the API key, checks the sign-in, rate limits
each Google account, and forwards the request to the Claude API.

The story logic (target words, known-word checks, rewrites, translations)
stays in the app, which has the user's progress. The server only decides the
model, the token cap and the limits, and passes Claude's replies back as is.

## Running it

```sh
cd server
cp .env.example .env        # fill in ANTHROPIC_API_KEY and GOOGLE_CLIENT_IDS
docker compose up -d
```

`compose.yaml` pulls `ghcr.io/danielpower/jiwajiwa-story-server:latest`, which
`.github/workflows/server.yml` builds and pushes on every push to `main` that
touches `server/` (also tagged `sha-<commit>`). `docker compose build` builds
it locally instead. The SQLite database lives in the `story-data` volume.

The port is bound to `127.0.0.1:8080`. Put a reverse proxy that serves HTTPS
in front of it (Android refuses plain HTTP), and give it a read timeout of at
least 10 minutes, because a long story with thinking can take a few minutes.

If the ghcr package is private, either make it public (Package settings on
GitHub) or `docker login ghcr.io` on the host with a token that can read
packages.

## Settings

| Variable | Default | What it does |
| --- | --- | --- |
| `ANTHROPIC_API_KEY` | required | The Claude API key |
| `GOOGLE_CLIENT_IDS` | required | Web OAuth client ID(s) the Google ID tokens must be addressed to, comma-separated |
| `CLAUDE_MODEL` | `claude-sonnet-5-5` | Model for every request |
| `CLAUDE_MAX_TOKENS` | `16000` | Output cap per request |
| `REQUESTS_PER_HOUR` | `20` | Per account. A story is one request plus any rewrites, and one more for its translations |
| `TOKENS_PER_DAY` | `200000` | Per account, input plus output over the last 24 hours. A medium story is very roughly 20,000 to 40,000 |
| `MAX_IN_FLIGHT` | `2` | Requests one account can have waiting on Claude at once |
| `SESSION_DAYS` | `180` | How long a sign-in lasts before the app has to sign in again |

To cut off an account:

```sh
docker compose exec story-server python -c "import sqlite3; db = sqlite3.connect('/data/story-server.db'); db.execute(\"UPDATE accounts SET blocked = 1 WHERE email = 'someone@example.com'\"); db.commit()"
```

## API

All errors look like the Claude API's: `{"type": "error", "error": {"type": ..., "message": ...}}`,
with a message that can be shown to the user as is.

| Endpoint | What it does |
| --- | --- |
| `POST /v1/sessions` `{"id_token": "..."}` | Checks a Google ID token and returns `{"token", "expires_at", "email"}` |
| `DELETE /v1/sessions` | Signs out (ends this session) |
| `GET /v1/usage` | This account's usage and limits |
| `POST /v1/messages` `{"system", "messages", "effort"}` | Sends a conversation to Claude and returns the Messages API response unchanged. `effort` is `low`, `medium` or `high`. A `429` means a limit was hit and carries `Retry-After` |
| `GET /healthz` | Liveness check |

Everything except `/healthz` and `POST /v1/sessions` needs `Authorization: Bearer <token>`.

## Google sign-in setup

In the [Google Cloud console](https://console.cloud.google.com/), in one project:

1. **Google Auth Platform → Branding**: app name Jiwa Jiwa, a support email,
   and the app's privacy policy link. **Audience**: External, then publish it
   to production. Sign-in only asks for the basic `openid`, `email` and
   `profile` scopes, so Google doesn't need to verify the app.
2. **Clients → Create client → Web application**, named "Story server". Its
   client ID goes in `GOOGLE_CLIENT_IDS` and in `googleWebClientId` in the root `gradle.properties`. It needs no
   redirect URIs, and the server never uses its secret.
3. **Clients → Create client → Android** for each signing key that will run
   the app, with package `com.crispyumbrella.jiwajiwa` and the key's SHA-1:
   - the Play app signing key (Play Console → Test and release → App
     integrity → App signing), for installs from Play;
   - the upload key (same page), for the APKs on GitHub Releases.

   These clients aren't referenced anywhere; Google uses them to check that
   the app asking for a token really is Jiwa Jiwa.

## Development

```sh
cd server
python3 -m venv .venv && .venv/bin/pip install -r requirements-dev.txt
.venv/bin/python -m pytest
```
