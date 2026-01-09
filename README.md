# Agnostik Bot Runner

Spring Boot app that runs bot users for the Agnostik App. It talks to the backend (`/api`) for auth/moves/requests and to the `/ws` STOMP endpoint for live snapshots and text updates. For the Agnostik App context, see the agnostik-app README: https://github.com/dimell94/agnostik-app#readme

## General Requirements
- Java 17 (tested with Amazon Corretto 17.0.15_6)
- Gradle 8.14.3
- Docker

## Quick Start via CLI
```bash
   git clone https://github.com/dimell94/agnostik-bot-runner.git
   cd agnostik-bot-runner
   docker compose build 
   docker compose up -d
   ```


## What it does
- `BotManager` loads bots from config, logs in/registers, opens STOMP sessions, and every 8s (configurable) calls `decideAndAct`.
- Per bot:
  - If `use-llm=true` and `llm.enabled=true`, sends the snapshot as a prompt to the LLM and applies JSON actions: move left/right, lock/unlock, send/accept/reject friend request, send text.
  - Otherwise runs fixed behavior/message (and a lock/unlock cycle for `bot3` in the default config).
- REST: `/api/presence/*` (move/lock/unlock/leave), `/api/requests/*` (friend requests).
- STOMP: subscribe `/user/queue/snapshot`; send text to `/app/text`.

## Configuration tips
- Enable LLM: set env vars `LLM_URL`, `LLM_API_KEY`, `LLM_MODEL`, then set `use-llm: true` for the bots you want to drive with the model.
- Cadence/LLM: tune `llm.min-interval-ms`, `max-tokens`, `temperature`, `timeout-ms`.
- Fixed bots: set `use-llm=false` and provide `fixed-text` for deterministic behavior.

## Troubleshooting
- No snapshots: check `app.ws-endpoint` and WS reachability.
- 401/403: verify credentials and `app.base-url`.
- LLM timeouts/empty: confirm `llm.url` and API key; increase `timeout-ms`.
- Too chatty/slow: adjust `min-interval-ms` and `temperature`.
