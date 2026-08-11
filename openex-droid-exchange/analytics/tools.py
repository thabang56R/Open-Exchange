"""LangChain tools the agent may call.

Every tool is a plain Python function first (so it stays unit-testable) and is
exposed to the LLM through LangChain's @tool decorator.

Wallet access is scoped: the caller's JWT is carried in a context variable and
never exposed to the model, so the LLM can only ever read the balance of the
user who is currently chatting.
"""
from __future__ import annotations

import contextvars
import json
import os

import requests
from langchain_core.tools import tool

from simulator import simulator

KOTLIN_API_URL = os.environ.get("KOTLIN_API_URL", "http://localhost:8080")
REQUEST_TIMEOUT = 8

# JWT of the user currently chatting (set per request, never seen by the model)
current_token: contextvars.ContextVar[str | None] = contextvars.ContextVar(
    "current_token", default=None
)


def fetch_wallet_balances(token: str | None) -> dict:
    """HTTP GET the Kotlin engine's wallet endpoint with the caller's JWT."""
    if not token:
        return {"error": "not_authenticated", "message": "No session token was supplied."}
    try:
        res = requests.get(
            f"{KOTLIN_API_URL}/api/wallets/balances",
            headers={"Authorization": f"Bearer {token}"},
            timeout=REQUEST_TIMEOUT,
        )
    except requests.RequestException as exc:  # network / engine down
        return {"error": "engine_unreachable", "message": str(exc)}

    if res.status_code == 401:
        return {"error": "unauthorized", "message": "Session expired, ask the user to sign in again."}
    if not res.ok:
        return {"error": "engine_error", "status": res.status_code, "message": res.text[:200]}
    return res.json()


def fetch_open_orders(token: str | None) -> list | dict:
    if not token:
        return {"error": "not_authenticated"}
    try:
        res = requests.get(
            f"{KOTLIN_API_URL}/api/orders",
            headers={"Authorization": f"Bearer {token}"},
            timeout=REQUEST_TIMEOUT,
        )
        return res.json() if res.ok else {"error": "engine_error", "status": res.status_code}
    except requests.RequestException as exc:
        return {"error": "engine_unreachable", "message": str(exc)}


@tool("get_wallet_balance")
def get_wallet_balance_tool() -> str:
    """Return the signed-in user's wallet balances per asset from the exchange core engine.
    Use this whenever the user asks about their balance, funds, holdings or buying power."""
    return json.dumps(fetch_wallet_balances(current_token.get()))


@tool("get_open_orders")
def get_open_orders_tool() -> str:
    """Return the signed-in user's current orders (status, side, price, quantity).
    Use this when the user asks what orders they have working or recently placed."""
    return json.dumps(fetch_open_orders(current_token.get()))


@tool("get_market_snapshot")
def get_market_snapshot_tool(symbol: str = "") -> str:
    """Return the simulated market snapshot: price, moving averages, 1h high/low,
    change percent, volatility and trend. Pass a symbol such as BTC-USD, or leave
    it empty for every tracked market."""
    symbol = (symbol or "").strip().upper()
    if not symbol:
        return json.dumps(simulator.summary())
    try:
        return json.dumps(simulator.latest(symbol))
    except KeyError:
        return json.dumps({"error": "unknown_symbol", "known": simulator.symbols()})


AGENT_TOOLS = [get_wallet_balance_tool, get_open_orders_tool, get_market_snapshot_tool]
