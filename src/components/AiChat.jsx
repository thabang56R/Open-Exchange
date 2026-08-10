import { useEffect, useRef, useState } from "react";
import { analytics } from "../lib/analyticsApi.js";

const GREETING = {
  role: "assistant",
  content:
    "ATLAS online. I can read your wallet, your working orders and the simulated tape. Ask me anything — e.g. \"what's my USD balance?\"",
};

const SUGGESTIONS = ["What's my balance?", "Any open orders?", "How is BTC-USD trending?"];

/** Floating AI trading assistant backed by Flask + LangChain + local Ollama. */
export default function AiChat({ symbol }) {
  const [open, setOpen] = useState(false);
  const [messages, setMessages] = useState([GREETING]);
  const [input, setInput] = useState("");
  const [busy, setBusy] = useState(false);
  const scroller = useRef(null);
  const box = useRef(null);

  useEffect(() => {
    if (scroller.current) scroller.current.scrollTop = scroller.current.scrollHeight;
  }, [messages, busy, open]);

  useEffect(() => {
    if (open && box.current) box.current.focus();
  }, [open]);

  const send = async (text) => {
    const question = (text ?? input).trim();
    if (!question || busy) return;
    const history = messages.filter((m) => m !== GREETING).map(({ role, content }) => ({ role, content }));
    setMessages((m) => [...m, { role: "user", content: question }]);
    setInput("");
    setBusy(true);
    try {
      const res = await analytics.chat(question, history);
      setMessages((m) => [
        ...m,
        { role: "assistant", content: res.reply, tools: res.toolCalls || [], model: res.model },
      ]);
    } catch (e) {
      setMessages((m) => [...m, { role: "assistant", content: `⚠ ${e.message}`, error: true }]);
    } finally {
      setBusy(false);
      if (box.current) box.current.focus();
    }
  };

  if (!open) {
    return (
      <button
        className="btn fixed bottom-5 right-5 z-50 !px-4 !py-2 font-mono text-xs"
        style={{ borderColor: "var(--color-amber)", color: "var(--color-amber)", background: "var(--color-void)" }}
        onClick={() => setOpen(true)}
      >
        ▲ ASK ATLAS
      </button>
    );
  }

  return (
    <div className="fixed bottom-5 right-5 z-50 w-[min(380px,calc(100vw-2.5rem))] panel flex flex-col max-h-[70vh] shadow-2xl">
      <header className="flex items-center justify-between px-3 py-2 border-b border-edge">
        <div className="flex items-baseline gap-2">
          <p className="font-mono text-xs tracking-widest text-amber">ATLAS</p>
          <span className="label">AI desk analyst · {symbol}</span>
        </div>
        <button className="btn !py-[2px] !px-2" onClick={() => setOpen(false)}>
          ✕
        </button>
      </header>

      <div ref={scroller} className="flex-1 overflow-y-auto px-3 py-3 space-y-3">
        {messages.map((m, i) => (
          <div key={i} className={m.role === "user" ? "text-right" : ""}>
            <p className="label mb-1">{m.role === "user" ? "You" : "ATLAS"}</p>
            <p
              className="font-mono text-xs whitespace-pre-wrap inline-block text-left"
              style={
                m.role === "user"
                  ? { color: "var(--color-amber)" }
                  : m.error
                    ? { color: "var(--color-ask)" }
                    : undefined
              }
            >
              {m.content}
            </p>
            {m.tools?.length ? (
              <p className="label mt-1">tools · {m.tools.map((t) => t.name).join(", ")}</p>
            ) : null}
          </div>
        ))}
        {busy ? <p className="font-mono text-xs text-muted animate-pulse">ATLAS is thinking…</p> : null}
      </div>

      {messages.length <= 1 ? (
        <div className="px-3 pb-2 flex flex-wrap gap-1">
          {SUGGESTIONS.map((s) => (
            <button key={s} className="btn !py-1 !px-2 !text-[10px]" onClick={() => send(s)}>
              {s}
            </button>
          ))}
        </div>
      ) : null}

      <form
        className="flex gap-2 p-2 border-t border-edge"
        onSubmit={(e) => {
          e.preventDefault();
          send();
        }}
      >
        <input
          ref={box}
          className="field flex-1 !text-xs"
          placeholder="Ask about your book, balance or the tape…"
          value={input}
          onChange={(e) => setInput(e.target.value)}
          disabled={busy}
        />
        <button className="btn !px-3" type="submit" disabled={busy || !input.trim()}>
          Send
        </button>
      </form>
    </div>
  );
}
