# OpenEx 3.0

A self-contained crypto exchange sandbox: a Kotlin/Spring Boot matching engine with an immutable
double-entry ledger, a Python analytics + agentic-AI microservice, and a React trading terminal
that streams the order book over STOMP.

```text
  React terminal (Vite, JSX) ──REST──►  Spring Boot API  ──►  PostgreSQL (Flyway)
        │      ▲                              ▲
        │      └──────── STOMP /ws ───────────┘
        │                                     │ wallet tool call
        └──REST──► Flask analytics + LangChain agent ──► Ollama (local LLM)
                          │
                          └── Pandas/NumPy market simulator
```

## Build log

| Week | Theme | Delivered |
| ---- | ----- | --------- |
| 1 | Core engine & DB integrity | Price-time matching engine, immutable ledger, JWT auth, idempotent order + deposit APIs |
| 2 | Realtime UI | STOMP order-book broadcasts, React terminal (wallet, ticket, book, blotter) |
| 3 | Wrap-up | Observability & hardening, trade history + charts, admin & risk controls, deploy + docs |
| 3b | Analytics & agentic AI | Flask/Pandas market simulator, LangChain + Ollama agent with wallet tool calling, Chart.js analytics, floating AI chat, cold-start compose |

## Analytics & agentic AI (Python)

- `analytics/simulator.py` — Pandas/NumPy random walk with drift (5s ticks), rolling SMA-12 /
  SMA-48 and volatility, served as clean JSON arrays.
- `analytics/agent.py` — LangChain agent on a **local** Ollama model with a sell-side
  desk-analyst persona.
- `analytics/tools.py` — tools the LLM may call: `get_wallet_balance` and `get_open_orders`
  (HTTP into the Kotlin engine with the caller's JWT, which the model never sees) plus
  `get_market_snapshot`.
- Terminal: Chart.js price + moving-average chart and a floating **ATLAS** chat window that shows
  which tools the agent used. Details in `analytics/README.md`.

## Week 3 features

**Observability & hardening**
- Actuator endpoints: `/actuator/health`, `/actuator/metrics`, `/actuator/prometheus`
- Micrometer counters: `openex.orders.placed{symbol}`, `openex.trades.executed`, `openex.orders.cancelled`
- Request-id MDC logging (`X-Request-Id` echoed on every response) with latency per call
- Fixed-window in-memory rate limiter (`openex.rate-limit.requests-per-minute`, default 300) returning `429`
- Typed error envelopes for every failure mode (`market_halted`, `risk_limit`, `forbidden`, …)

**Trade history & charts**
- `GET /api/market/{symbol}/trades` — last 50 prints
- `GET /api/market/{symbol}/candles?interval=1&buckets=60` — OHLCV buckets built from trades
- `GET /api/market/{symbol}/stats` — rolling 24h last/high/low/change/volume
- Terminal: SVG candlestick chart with 1m/5m/15m toggles and a time & sales tape

**Admin & risk controls**
- Roles live in a dedicated `user_roles` table (`ADMIN`, `TRADER`) — never on the user row
- The first registered account (or any username in `ADMIN_USERNAMES`) bootstraps as admin
- `PUT /api/admin/markets/{symbol}` halts or resumes a market; halted markets reject new orders with `503`
- `PUT /api/admin/users/{id}/limits` sets per-user max order notional and max open orders
- `POST|DELETE /api/admin/users/{id}/roles` grants/revokes roles
- Append-only `audit_log` surfaced at `GET /api/admin/audit` and in the terminal's Admin tab

**Deploy & docs**
- `docker-compose up` runs Postgres, Redis and the API
- OpenAPI/Swagger UI at `http://localhost:8080/swagger-ui.html`
- CI builds and tests both the backend and the terminal

## Run it

### 0. Cold start — one command

```bash
ollama serve && ollama pull llama3   # once, on the host, for the AI assistant
docker compose up --build
```

Compose brings up Postgres and Redis with strict healthchecks, waits for both to be
`service_healthy` before starting the Kotlin API, then waits for the API's
`/actuator/health` before starting the Python analytics service. Nothing races the database.

| Service | URL |
| --- | --- |
| Kotlin API | `http://localhost:8080` |
| Swagger UI | `http://localhost:8080/swagger-ui.html` |
| Python analytics + AI | `http://localhost:5001` (`/health`) |
| Terminal (dev) | `http://localhost:5173` |

### 1. API from IntelliJ

1. `File → Open` and select the `backend/` folder (Gradle project, JDK 21).
2. Start only the datastores: `docker compose up postgres redis`.
3. Run `OpenExApplication` — Flyway applies `V1` and `V2` on boot.

From the shell instead:

```bash
cd backend && ./gradlew bootRun
```

### 2. Python analytics + AI service

```bash
cd analytics
python -m venv .venv && source .venv/bin/activate
pip install -r requirements.txt
python app.py     # http://localhost:5001
```

Requires Ollama on the host: `ollama serve` + `ollama pull llama3`
(set `OLLAMA_MODEL=mistral` to use Mistral instead). `GET /health` reports whether the
model is reachable and pulled.

### 3. Terminal

```bash
cp .env.example .env
npm install
npm run dev
```

Open `http://localhost:5173`, register (the first account becomes admin), fund the wallet from the
faucet, then trade. The Admin button appears for admin accounts; the **ASK ATLAS** button in the
bottom-right opens the AI assistant.

## Tests

```bash
cd backend && ./gradlew test
```

- `LedgerServiceTest` — double-entry balance and atomic rollback
- `MatchingEngineIntegrationTest` — concurrent matching and credit conservation
- `OrderApiIdempotencyTest` — JWT auth and idempotent replays
- `OrderBookBroadcastTest` — STOMP snapshot broadcasting
- `AdminRiskControlsTest` — role gating, market halts, per-user risk caps, audit trail

Agentic tool-calling check (API + analytics running):

```bash
curl -s -X POST localhost:5001/api/chat -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' -d '{"message":"What is my USD balance?"}' | jq
```

## Configuration

| Variable | Default | Purpose |
| -------- | ------- | ------- |
| `DB_URL` / `DB_USER` / `DB_PASSWORD` | local Postgres | Datasource |
| `JWT_SECRET` | dev placeholder | HMAC signing key (set a real one in production) |
| `ADMIN_USERNAMES` | empty | Comma-separated usernames auto-granted `ADMIN` |
| `MAX_ORDER_NOTIONAL` | `1000000` | Default per-order notional cap |
| `MAX_OPEN_ORDERS` | `50` | Default open-order cap |
| `RATE_LIMIT_RPM` | `300` | API requests per minute per caller |
| `KOTLIN_API_URL` | `http://localhost:8080` | Analytics service → core engine |
| `OLLAMA_URL` / `OLLAMA_MODEL` | `http://localhost:11434` / `llama3` | Local LLM for the agent |
| `VITE_API_URL` / `VITE_WS_URL` | `localhost:8080` | Terminal → API wiring |
| `VITE_ANALYTICS_URL` | `http://localhost:5001` | Terminal → Python service |

