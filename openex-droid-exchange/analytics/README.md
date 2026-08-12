# OpenEx Analytics & AI service (Python)

Flask microservice that (1) simulates a market feed with Pandas/NumPy and
(2) runs a LangChain agent on a **local** Ollama model that can call the Kotlin
engine to read the signed-in user's wallet.

## Run locally

```bash
cd analytics
py -m venv .venv && source .venv/bin/activate   # Windows: .venv\Scripts\activate
pip install -r requirements.txt
export KOTLIN_API_URL=http://localhost:8080
export OLLAMA_URL=http://localhost:11434
export OLLAMA_MODEL=llama3
py app.py          # http://localhost:5001
```

## Ollama

```bash
# https://ollama.com/download
ollama serve
ollama pull llama3      # or: ollama pull mistral  (then OLLAMA_MODEL=mistral)
```

`GET /health` reports whether Ollama is reachable and whether the model is pulled.

## Endpoints

| Method | Path | Returns |
| --- | --- | --- |
| GET | `/health` | liveness + Ollama status |
| GET | `/api/analytics/symbols` | tracked symbols |
| GET | `/api/analytics/history?symbol=BTC-USD&points=240` | `{ticks:[{time,price,smaFast,smaSlow}]}` |
| GET | `/api/analytics/latest?symbol=BTC-USD` | current tick + 1h stats |
| GET | `/api/analytics/summary` | latest tick for every symbol |
| POST | `/api/chat` | `{reply, toolCalls[], model}` |

`POST /api/chat` body: `{"message": "...", "history": [{"role":"user","content":"..."}]}`.
Forward the trader's JWT as `Authorization: Bearer <token>` — it is stored in a
request-scoped context variable and used only by the wallet/orders tools; the
model never sees it.

## Agent tools

| Tool | Backing call |
| --- | --- |
| `get_wallet_balance` | `GET {KOTLIN_API_URL}/api/wallets/balances` |
| `get_open_orders` | `GET {KOTLIN_API_URL}/api/orders` |
| `get_market_snapshot` | in-process Pandas simulator |

## Verify tool calling

```bash
TOKEN=$(curl -s -X POST localhost:8080/api/auth/register -H 'Content-Type: application/json' \
  -d '{"username":"atlas","password":"rebelbase1"}' | jq -r .token)
curl -s -X POST localhost:8080/api/wallets/deposit -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' -H "Idempotency-Key: $(uuidgen)" \
  -d '{"asset":"USD","amount":"100000"}'

curl -s -X POST localhost:5001/api/chat -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' -d '{"message":"What is my USD balance?"}' | jq
```

The response quotes `100,000.00 USD` and `toolCalls[0].name == "get_wallet_balance"`.
