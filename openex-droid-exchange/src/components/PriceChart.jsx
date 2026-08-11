import { useEffect, useState } from "react";
import { api } from "../lib/api.js";

const fmt = (n, d = 2) =>
  Number(n ?? 0).toLocaleString(undefined, { minimumFractionDigits: d, maximumFractionDigits: d });

/** Lightweight SVG candlestick chart — no charting dependency. */
export default function PriceChart({ symbol }) {
  const [candles, setCandles] = useState([]);
  const [stats, setStats] = useState(null);
  const [interval_, setInterval_] = useState(1);

  useEffect(() => {
    let alive = true;
    const pull = () => {
      api.candles(symbol, interval_, 60).then((c) => alive && setCandles(c)).catch(() => {});
      api.stats(symbol).then((s) => alive && setStats(s)).catch(() => {});
    };
    pull();
    const t = setInterval(pull, 5000);
    return () => {
      alive = false;
      clearInterval(t);
    };
  }, [symbol, interval_]);

  const W = 640;
  const H = 200;
  const highs = candles.map((c) => Number(c.high));
  const lows = candles.map((c) => Number(c.low));
  const max = highs.length ? Math.max(...highs) : 1;
  const min = lows.length ? Math.min(...lows) : 0;
  const span = max - min || 1;
  const step = candles.length ? W / candles.length : W;
  const y = (v) => H - ((Number(v) - min) / span) * (H - 16) - 8;
  const up = Number(stats?.changePercent ?? 0) >= 0;

  return (
    <section className="panel p-4 space-y-3">
      <div className="flex items-center justify-between flex-wrap gap-2">
        <div className="flex items-baseline gap-3">
          <p className="label">Price · {symbol}</p>
          <span className="font-mono text-lg">{stats?.last ? fmt(stats.last) : "—"}</span>
          <span
            className="font-mono text-xs"
            style={{ color: up ? "var(--color-bid)" : "var(--color-ask)" }}
          >
            {stats?.changePercent != null ? `${up ? "+" : ""}${fmt(stats.changePercent)}%` : "—"}
          </span>
        </div>
        <div className="flex gap-1">
          {[1, 5, 15].map((m) => (
            <button
              key={m}
              className="btn !py-1 !px-2"
              onClick={() => setInterval_(m)}
              style={m === interval_ ? { borderColor: "var(--color-amber)", color: "var(--color-amber)" } : undefined}
            >
              {m}m
            </button>
          ))}
        </div>
      </div>

      {candles.length === 0 ? (
        <p className="font-mono text-xs text-muted py-10 text-center">
          No prints yet — place crossing orders to draw the tape.
        </p>
      ) : (
        <svg viewBox={`0 0 ${W} ${H}`} className="w-full h-[200px]">
          {candles.map((c, i) => {
            const x = i * step + step / 2;
            const bull = Number(c.close) >= Number(c.open);
            const color = bull ? "var(--color-bid)" : "var(--color-ask)";
            const top = y(Math.max(c.open, c.close));
            const bottom = y(Math.min(c.open, c.close));
            return (
              <g key={c.time}>
                <line x1={x} x2={x} y1={y(c.high)} y2={y(c.low)} stroke={color} strokeWidth="1" />
                <rect
                  x={x - Math.max(step * 0.3, 1)}
                  y={top}
                  width={Math.max(step * 0.6, 2)}
                  height={Math.max(bottom - top, 1)}
                  fill={color}
                />
              </g>
            );
          })}
        </svg>
      )}

      <div className="grid grid-cols-4 gap-2 font-mono text-[11px]">
        <div><p className="label">24h High</p>{stats?.high ? fmt(stats.high) : "—"}</div>
        <div><p className="label">24h Low</p>{stats?.low ? fmt(stats.low) : "—"}</div>
        <div><p className="label">Volume</p>{fmt(stats?.volume, 4)}</div>
        <div><p className="label">Prints</p>{stats?.trades ?? 0}</div>
      </div>
    </section>
  );
}