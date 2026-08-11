import { useEffect, useState } from "react";
import { api } from "../lib/api.js";

const fmt = (n, d = 2) =>
  Number(n ?? 0).toLocaleString(undefined, { minimumFractionDigits: d, maximumFractionDigits: d });

export default function TradesFeed({ symbol }) {
  const [trades, setTrades] = useState([]);

  useEffect(() => {
    let alive = true;
    const pull = () => api.trades(symbol).then((t) => alive && setTrades(t)).catch(() => {});
    pull();
    const t = setInterval(pull, 3000);
    return () => {
      alive = false;
      clearInterval(t);
    };
  }, [symbol]);

  return (
    <section className="panel p-4 space-y-2">
      <p className="label">Time &amp; sales · {symbol}</p>
      {trades.length === 0 ? (
        <p className="font-mono text-xs text-muted py-3">No trades printed yet.</p>
      ) : (
        <div className="max-h-64 overflow-y-auto font-mono text-xs space-y-[2px]">
          {trades.map((t, i) => {
            const prev = trades[i + 1];
            const up = !prev || Number(t.price) >= Number(prev.price);
            return (
              <div key={t.id} className="flex justify-between">
                <span style={{ color: up ? "var(--color-bid)" : "var(--color-ask)" }}>{fmt(t.price)}</span>
                <span className="text-ink/80">{fmt(t.quantity, 4)}</span>
                <span className="text-muted">{new Date(t.createdAt).toLocaleTimeString()}</span>
              </div>
            );
          })}
        </div>
      )}
    </section>
  );
}