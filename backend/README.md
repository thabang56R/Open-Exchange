# OpenEx 3.0 — Week 1: Core Engine & DB Integrity

Kotlin + Spring Boot backend with a strict immutable double-entry ledger and an
in-memory price-time-priority matching engine.

## Clone & open in IntelliJ IDEA

```sh
git clone <repo-url>
cd <repo>/backend
./gradlew test          # Windows: gradlew.bat test
```

1. `File > Open…` and select the **`backend`** folder (it contains `build.gradle.kts`).
2. IntelliJ auto-imports the Gradle project. Set the Project SDK to **JDK 21** (Temurin recommended).
3. Start infrastructure: `docker compose up -d postgres redis` (from the repo root).
4. Run the bundled **OpenEx API** run configuration, `OpenExApplication.kt` (green gutter arrow), or `./gradlew bootRun`.
5. Run all tests with `./gradlew test` or right-click `src/test/kotlin`.

The Gradle wrapper (`gradlew`, `gradlew.bat`, `gradle/wrapper/`) is committed, so no local
Gradle install is required. Env defaults live in `.env.example`; the IntelliJ run
configuration in `.idea/runConfigurations/` already sets them.


## Stack

| Sector | Tech |
|---|---|
| Core Reactor | Kotlin 1.9 + Spring Boot 3.3 (Gradle Kotlin DSL) |
| The Vault | PostgreSQL 16 + Flyway |
| Cache / idempotency infra | Redis (compose service, ready for week 2) |
| Cargo Transports | Docker + Compose |

## Architecture (Week 1)

```text
POST /api/orders ──► OrderService ──► MatchingEngineService (price-time priority)
                          │                    │
                          │                    ▼
                          └──────────► LedgerService ──► ledger_entries (append-only)
```

- **Double entry**: `LedgerService.post()` rejects any transaction whose debits and
  credits do not net to zero *per asset*. Everything runs inside `@Transactional`,
  so a failed leg rolls back the entire trade.
- **No UPDATE/DELETE on `ledger_entries`** — balances are derived by summing entries.
- **Idempotency**: `Idempotency-Key` header + `idempotency_keys` table. Replaying the
  same key with the same body returns the cached response; a different body → `409`.
- **Matching**: bids high→low, asks low→high, FIFO inside a price level, maker price
  wins, no self-trades, market remainder is cancelled (never rests).

## API quick tour

```bash
# 1. Register (returns a stateless JWT)
TOKEN=$(curl -s -X POST localhost:8080/api/auth/register \
  -H 'Content-Type: application/json' \
  -d '{"username":"luke","password":"rebelbase1"}' | jq -r .token)

# 2. Faucet deposit
curl -s -X POST localhost:8080/api/wallets/deposit \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -H "Idempotency-Key: $(uuidgen)" \
  -d '{"asset":"USD","amount":"100000"}'

# 3. Place a limit order (replay-safe)
KEY=$(uuidgen)
curl -s -X POST localhost:8080/api/orders \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -H "Idempotency-Key: $KEY" \
  -d '{"symbol":"BTC-USD","side":"BUY","type":"LIMIT","price":"30000","quantity":"1"}'

# Same key again -> identical order id, no duplicate
curl -s -X POST localhost:8080/api/orders \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -H "Idempotency-Key: $KEY" \
  -d '{"symbol":"BTC-USD","side":"BUY","type":"LIMIT","price":"30000","quantity":"1"}'

# 4. Balances and public order book
curl -s localhost:8080/api/wallets/balances -H "Authorization: Bearer $TOKEN"
curl -s localhost:8080/api/market/BTC-USD/book
```

## Tests (the Week 1 deliverables)

| Test | Proves |
|---|---|
| `LedgerServiceTest` | entries always sum to zero; unbalanced posts roll back completely |
| `OrderApiIdempotencyTest` | JWT auth, faucet deposit, duplicate key returns the cached order |
| `MatchingEngineIntegrationTest` | limit/market matching, price-time priority, 10 concurrent orders conserve every credit |

Run them: `cd backend && gradle test` (tests use in-memory H2, no Docker needed).
