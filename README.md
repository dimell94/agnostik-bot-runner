# Agnostik Bot Runner

Spring Boot application that creates and drives bot users for the Agnostik corridor app. Each bot authenticates against the backend REST API, opens its own STOMP session, listens for live snapshots, and then acts either through fixed behavior or through an LLM-generated JSON decision.

## Agnostik Context ✨

This project runs bot users inside the Agnostik corridor. The bots follow the same movement, locking, live typing, and friendship rules as regular users, and interact with the same backend and WebSocket flow.

Related repositories:

- Backend: https://github.com/dimell94/agnostik-app
- Frontend: https://github.com/dimell94/agnostik-app-ui

## Table of Contents

- [Agnostik Context](#agnostik-context)
- [Architecture](#architecture)
- [What the Bot Runner Does](#what-the-bot-runner-does)
- [Tech Stack](#tech-stack)
- [Prerequisites](#prerequisites)
- [Configuration](#configuration)
- [Running Locally with Docker](#running-locally-with-docker)
- [Running Locally without Docker](#running-locally-without-docker)
- [Bot Lifecycle](#bot-lifecycle)
- [LLM Mode](#llm-mode)
- [Fixed-Behavior Mode](#fixed-behavior-mode)
- [REST and WebSocket Usage](#rest-and-websocket-usage)
- [Project Structure](#project-structure)
- [Troubleshooting](#troubleshooting)

## Architecture 🏗️

The application is centered around three layers:

1. `BotManager`
   - loads configured bots from `app.bots`
   - starts one `BotSession` per bot
   - schedules decision ticks every 8 seconds
2. `BotSession`
   - authenticates the bot
   - opens a STOMP connection
   - stores the latest snapshot
   - decides and executes actions
3. Transport services
   - `HttpClientService` sends REST commands to the backend
   - `StompClientService` handles `/ws` and `/app/text`
   - `LlmService` calls an OpenAI-compatible chat-completions style endpoint

## What the Bot Runner Does 🤖

- Each configured bot logs in, or registers first if login fails.
- After authentication, each bot connects to the backend WebSocket endpoint with the JWT in the STOMP `CONNECT` headers.
- Each bot subscribes to `/user/queue/snapshot`.
- Each bot keeps the most recent snapshot in memory.
- Every scheduled tick, each bot decides what to do next.
- Incoming friend requests are also handled immediately when a new snapshot arrives.

Supported actions:

- move left
- move right
- lock
- unlock
- send text
- send friend request
- accept friend request
- reject friend request

## Tech Stack 🧰

- Java 17
- Spring Boot 3.5.5
- Spring WebFlux `WebClient`
- Spring WebSocket / STOMP
- Jackson
- Lombok

## Prerequisites ✅

- Java 17 or Docker
- Running backend from the `agnostik-app` repository

Optional for LLM-driven bots:

- reachable LLM HTTP endpoint
- valid API key if your provider requires one

## Configuration ⚙️

Runtime configuration is defined in `src/main/resources/application.yml`.

### Server

- `server.port`: `8081`

### Backend connection

- `app.base-url` default: `http://localhost:8080`
- `app.ws-endpoint` default: `ws://localhost:8080/ws`

Docker compose overrides these to target the host machine:

- `APP_BASE_URL=http://host.docker.internal:8080`
- `APP_WS_ENDPOINT=ws://host.docker.internal:8080/ws`

### LLM settings

- `llm.url`
- `llm.api-key`
- `llm.api-key-env`
- `llm.model`
- `llm.max-tokens` default: `128`
- `llm.temperature` default: `1.4`
- `llm.timeout-ms` default: `20000`
- `llm.enabled` default in YAML: `true`
- `llm.min-interval-ms` default: `8000`

Environment placeholders used by default:

- `LLM_URL`
- `LLM_API_KEY`
- `LLM_MODEL`

### Bot definitions

Each entry under `app.bots` supports:

- `username`
- `password`
- `use-llm`
- `fixed-text`

Important current behavior:

- If `use-llm=false`, the bot uses fixed behavior.
- If `use-llm=true`, the bot still does nothing unless `llm.enabled=true`.
- The default config includes four bots.

## Running Locally with Docker 🐳

```bash
docker compose build
docker compose up -d
```

The container exposes:

- bot-runner on host port `8081`

Before starting it, make sure the backend is already reachable on the configured host/port.

## Running Locally without Docker ▶️

Example:

```bash
export APP_BASE_URL=http://localhost:8080
export APP_WS_ENDPOINT=ws://localhost:8080/ws

export LLM_URL=
export LLM_API_KEY=
export LLM_MODEL=

./gradlew bootRun
```

If you only want fixed-behavior bots, you can leave LLM variables unset as long as your bot configs use `use-llm: false`.

## Bot Lifecycle 🔄

For each bot:

1. Try `POST /api/auth/login`
2. If login fails, try `POST /api/auth/register`
3. Log in again to obtain a fresh JWT
4. Connect to `/ws`
5. Subscribe to `/user/queue/snapshot`
6. Store the latest snapshot
7. On each scheduled tick, call `decideAndAct()`

Decision timing details:

- `BotManager.tick()` runs every 8 seconds
- LLM-controlled bots also enforce `llm.min-interval-ms`
- Bots skip decision-making while they are simulating typing

Typing behavior:

- Text is sent incrementally in chunks to simulate live typing
- Previous text is erased gradually before the new text is typed
- Bot text is trimmed to 1000 characters before typing

## LLM Mode 🧠

When enabled:

- `BotSession` builds a prompt from the latest snapshot
- `LlmService` sends a single chat-completions style request
- The model is expected to return JSON only

Expected response schema:

```json
{
  "move": "left|right|none",
  "lock": "lock|unlock|none",
  "text": "string or empty",
  "request": "left|right|accept|reject|none"
}
```

Current implementation details to know:

- Invalid JSON or unsupported values are ignored
- The runner posts only one parsed action object per decision cycle
- Text longer than 1000 characters is truncated before typing
- The HTTP client expects a provider response containing `choices[].message.content`

## Fixed-Behavior Mode 🎭

For bots with `use-llm: false`:

- most bots use a legacy random behavior loop:
  - random movement when possible
  - random lock/unlock tendencies
  - repeated typing of `fixed-text`
- the bot named `bot3` has special lock/unlock cycle behavior:
  - locked phase for 15 seconds
  - unlocked phase for 30 seconds
  - random movement while unlocked

Independent of mode:

- incoming friend requests are auto-handled on snapshot receipt with random accept/reject

## REST and WebSocket Usage 🔌

### REST endpoints used

- `POST /api/auth/login`
- `POST /api/auth/register`
- `POST /api/presence/moveLeft`
- `POST /api/presence/moveRight`
- `POST /api/presence/lock`
- `POST /api/presence/unlock`
- `POST /api/presence/leave`
- `POST /api/requests/send/{direction}`
- `POST /api/requests/accept/{direction}`
- `POST /api/requests/reject/{direction}`

Note:

- The current HTTP client does not expose request cancellation; it only sends, accepts, and rejects.

### WebSocket/STOMP usage

- Connect to `app.ws-endpoint`
- Send `Authorization: Bearer <jwt>` in STOMP `CONNECT`
- Subscribe to `/user/queue/snapshot`
- Publish text to `/app/text`

## Project Structure 🗂️

- `bot`: bot orchestration and per-bot session logic
- `http`: REST client wrapper around backend endpoints
- `ws`: STOMP client setup
- `llm`: LLM HTTP client
- `config`: configuration properties classes
- `dto`: auth, snapshot, and text DTOs

## Troubleshooting 🛠️

| Issue | Likely Cause | What to Check |
| --- | --- | --- |
| Bots do not appear in the corridor | Backend is down or auth failed | Check `app.base-url`, backend logs, and login/register responses |
| Bots authenticate but never receive snapshots | WebSocket connection failed | Verify `app.ws-endpoint`, STOMP auth header, and backend `/ws` availability |
| LLM bots never act | `llm.enabled` is false or required config is missing | Check `llm.enabled`, `LLM_URL`, `LLM_MODEL`, and API key |
| LLM calls time out | Provider too slow or endpoint incompatible | Increase `llm.timeout-ms` and verify provider response shape |
| Bots send text but it looks choppy | This is expected | Typing is intentionally incremental to mimic live text entry |
| Bots seem inactive for a while | They may be waiting for the next tick or currently typing | Check the 8-second scheduler cadence and typing state |
| Friend requests behave unpredictably | Snapshot handler randomly accepts or rejects incoming requests | This is current implementation, not a deterministic policy |
