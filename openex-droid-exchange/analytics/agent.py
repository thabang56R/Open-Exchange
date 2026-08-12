"""LangChain agent wired to a local Ollama model.

The agent runs a tool-calling loop: the LLM decides when to call
get_wallet_balance / get_open_orders / get_market_snapshot, we execute the tool
locally, feed the result back, and let the model answer in natural language.

If Ollama is unreachable the endpoint degrades gracefully instead of 500-ing.
"""
from __future__ import annotations

import json
import os

from langchain_core.messages import AIMessage, HumanMessage, SystemMessage, ToolMessage

from tools import AGENT_TOOLS

# Use the base llama3 model (≈4.7 GB) which is available in Ollama
OLLAMA_URL = os.environ.get("OLLAMA_URL", "http://localhost:11434")
OLLAMA_MODEL = os.environ.get("OLLAMA_MODEL", "llama3")
MAX_TOOL_STEPS = 5

SYSTEM_PROMPT = """You are ATLAS, the in-terminal trading assistant for OpenEx 3.0,
a simulated crypto exchange. You speak like a seasoned sell-side desk analyst:
precise, numerate, calm, never hyped.

Rules:
- Use your tools for anything factual about the user's account or the market.
  Never guess a balance, an order or a price — call the tool and quote the number.
- Quote figures with their asset code (e.g. "12,500.00 USD", "0.7500 BTC").
- All markets here are simulated sandbox data. Say so if the user treats it as real money.
- You may explain strategy, risk and mechanics, but never present it as licensed
  financial advice; add a one-line caveat when the user asks what to buy or sell.
- Be concise: a few sentences or a short bullet list. No preamble, no filler."""

_TOOLS_BY_NAME = {t.name: t for t in AGENT_TOOLS}
_llm = None


def _get_llm():
    """Lazily build the Ollama chat model bound to our tools."""
    global _llm
    if _llm is None:
        from langchain_ollama import ChatOllama

        _llm = ChatOllama(
            model=OLLAMA_MODEL, base_url=OLLAMA_URL, temperature=0.2
        ).bind_tools(AGENT_TOOLS)
    return _llm


def _to_messages(history: list[dict]) -> list:
    msgs: list = [SystemMessage(content=SYSTEM_PROMPT)]
    for m in history[-20:]:
        role = m.get("role")
        content = (m.get("content") or "").strip()
        if not content:
            continue
        msgs.append(AIMessage(content=content) if role == "assistant" else HumanMessage(content=content))
    return msgs


def run_agent(message: str, history: list[dict] | None = None) -> dict:
    """Run the tool-calling loop and return {reply, toolCalls}."""
    messages = _to_messages(history or [])
    messages.append(HumanMessage(content=message))

    llm = _get_llm()
    used: list[dict] = []

    for _ in range(MAX_TOOL_STEPS):
        ai: AIMessage = llm.invoke(messages)
        messages.append(ai)
        calls = getattr(ai, "tool_calls", None) or []
        if not calls:
            return {"reply": ai.content or "(no response)", "toolCalls": used}

        for call in calls:
            name = call.get("name")
            args = call.get("args") or {}
            tool = _TOOLS_BY_NAME.get(name)
            if tool is None:
                result = json.dumps({"error": "unknown_tool", "name": name})
            else:
                try:
                    result = tool.invoke(args)
                except Exception as exc:  # keep the loop alive on tool failure
                    result = json.dumps({"error": "tool_failed", "message": str(exc)})
            used.append({"name": name, "args": args, "result": result[:1000]})
            messages.append(ToolMessage(content=str(result), tool_call_id=call.get("id", name)))

    return {
        "reply": "I hit the tool-call limit before reaching an answer. Try narrowing the question.",
        "toolCalls": used,
    }


def ollama_health() -> dict:
    import requests

    try:
        res = requests.get(f"{OLLAMA_URL}/api/tags", timeout=4)
        tags = res.json().get("models", []) if res.ok else []
        names = [t.get("name", "") for t in tags]
        return {
            "reachable": res.ok,
            "url": OLLAMA_URL,
            "model": OLLAMA_MODEL,
            "modelPulled": any(n.split(":")[0] == OLLAMA_MODEL.split(":")[0] for n in names),
            "models": names,
        }
    except Exception as exc:
        return {"reachable": False, "url": OLLAMA_URL, "model": OLLAMA_MODEL, "error": str(exc)}
