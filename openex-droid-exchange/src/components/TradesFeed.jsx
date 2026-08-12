import { useEffect, useState } from "react";
import { api } from "../lib/api.js";

const formatNumber = (n, d = 2) =>
  Number(n ?? 0).toLocaleString(undefined, {
    minimumFractionDigits: d,
    maximumFractionDigits: d,
  });

/**
 * TradesFeed component
 * Displays recent trades for a given symbol with price movement coloring.
 */
export default function TradesFeed({ symbol }) {
  const [trades, setTrades] = useState([]);

  useEffect(() => {
    let alive = true;

    const fetchTrades = async () => {
      try {
        const t = await api.trades(symbol, 50); // ✅ matches updated api.js
        if (alive) setTrades(t);
      } catch {
        // ignore fetch errors
      }
    };

    fetchTrades();
    const timer = setInterval(fetchTrades, 3000);

    return () => {
      alive = false;
      clearInterval(timer);
    };
  }, [symbol]);

  return (
    <section className="panel p-4 space-y-2">
      <p className="label">Time &amp; Sales · {symbol}</p>

      {trades.length === 0 ? (
        <p className="font-mono text-xs text-muted py-3">
          No trades printed yet.
        </p>
      ) : (
        <div className="max-h-64 overflow-y-auto font-mono text-xs space-y-[2px]">
          {trades.map((trade, index) => {
            const prev = trades[index - 1]; // safer: compare with previous trade
            const isUp = !prev || Number(trade.price) >= Number(prev.price);

            return (
              <div key={trade.id ?? trade.tradeId ?? trade.time}>
                <div className="flex justify-between">
                  <span
                    style={{
                      color: isUp ? "var(--color-bid)" : "var(--color-ask)",
                    }}
                  >
                    {formatNumber(trade.price)}
                  </span>
                  <span className="text-ink/80">
                    {formatNumber(trade.amount ?? trade.quantity, 4)}
                  </span>
                  <span className="text-muted">
                    {new Date(trade.createdAt ?? trade.time).toLocaleTimeString()}
                  </span>
                </div>
              </div>
            );
          })}
        </div>
      )}
    </section>
  );
}

