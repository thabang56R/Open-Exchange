"""OpenEx 3.0 — Python analytics & AI microservice (Flask).

Endpoints
  GET  /health                     liveness + Ollama reachability
  GET  /api/analytics/symbols      tracked simulated markets
  GET  /api/analytics/history      historical ticks + moving averages
  GET  /api/analytics/latest       current tick for one symbol
  GET  /api/analytics/summary      current tick for every symbol
  POST /api/chat                   LangChain + Ollama agent with tool calling
"""
from __future__ import annotations

import logging
import os

from flask import Flask, jsonify, request
from flask_cors import CORS

from agent import OLLAMA_MODEL, ollama_health, run_agent
from simulator import simulator
from tools import current_token

logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(name)s - %(message)s")
log = logging.getLogger("openex.analytics")

app = Flask(__name__)
CORS(app, resources={r"/api/*": {"origins": "*"}, r"/health": {"origins": "*"}})


def _bearer() -> str | None:
    header = request.headers.get("Authorization", "")
    return header[7:].strip() if header.lower().startswith("bearer ") else None


@app.get("/health")
def health():
    return jsonify({"status": "UP", "service": "openex-analytics", "ollama": ollama_health()})


@app.get("/api/analytics/symbols")
def symbols():
    return jsonify(simulator.symbols())


@app.get("/api/analytics/history")
def history():
    symbol = request.args.get("symbol", "BTC-USD").upper()
    points = request.args.get("points", default=240, type=int)
    try:
        return jsonify({"symbol": symbol, "ticks": simulator.history(symbol, points)})
    except KeyError:
        return jsonify({"error": "unknown_symbol", "known": simulator.symbols()}), 404


@app.get("/api/analytics/latest")
def latest():
    symbol = request.args.get("symbol", "BTC-USD").upper()
    try:
        return jsonify(simulator.latest(symbol))
    except KeyError:
        return jsonify({"error": "unknown_symbol", "known": simulator.symbols()}), 404


@app.get("/api/analytics/summary")
def summary():
    return jsonify(simulator.summary())


@app.post("/api/chat")
def chat():
    # Force JSON parsing so headers don’t break it
    payload = request.get_json(force=True, silent=True) or {}
    log.info("RAW DATA: %s", request.data.decode("utf-8", errors="ignore"))
    log.info("PARSED JSON: %s", payload)

    message = (payload.get("message") or "").strip()
    if not message:
        return jsonify({"error": "empty_message", "message": "Send a non-empty 'message'."}), 400

    history = payload.get("history") or []
    token = current_token.set(_bearer())
    try:
        result = run_agent(message, history)
        return jsonify({"model": OLLAMA_MODEL, **result})
    except Exception as exc:
        log.exception("agent failure")
        return (
            jsonify(
                {
                    "error": "agent_unavailable",
                    "message": f"The local model could not be reached ({exc}). "
                               f"Start Ollama and run: ollama pull {OLLAMA_MODEL}",
                }
            ),
            503,
        )
    finally:
        current_token.reset(token)


if __name__ == "__main__":
    app.run(host="0.0.0.0", port=int(os.environ.get("PORT", 5001)), debug=False)
