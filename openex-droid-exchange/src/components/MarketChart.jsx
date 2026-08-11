import { useEffect, useRef, useState } from "react";
import { Line } from "react-chartjs-2";
import {
  CategoryScale,
  Chart as ChartJS,
  Filler,
  Legend,
  LineElement,
  LinearScale,
  PointElement,
  Tooltip,
} from "chart.js";
import { analytics } from "../lib/analyticsApi.js";

ChartJS.register(CategoryScale, LinearScale, PointElement, LineElement, Filler, Tooltip, Legend);

const fmt = (n, d = 2) =>
  Number(n ?? 0).toLocaleString(undefined, { minimumFractionDigits: d, maximumFractionDigits: d });

const css = (name, fallback) => {
  if (typeof window === "undefined") return fallback;
  const v = getComputedStyle(document.documentElement).getPropertyValue(name).trim();
  return v || fallback;
};

/** Chart.js line chart fed by the Python analytics microservice. */
export default function MarketChart({ symbol }) {
  const [ticks, setTicks] = useState([]);
  const [stat, setStat] = useState(null);
  const [error, setError] = useState(null);
  const [points, setPoints] = useState(120);
  const mounted = useRef(true);

  useEffect(() => {
    mounted.current = true;
    const pull = () => {
      analytics
        .history(symbol, points)
        .then((r) => {
          if (!mounted.current) return;
          setTicks(r.ticks || []);
          setError(null);
        })
        .catch((e) => mounted.current && setError(e.message));
      analytics.latest(symbol).then((s) => mounted.current && setStat(s)).catch(() => {});
    };
    pull();
    const t = setInterval(pull, 5000);
    return () => {
      mounted.current = false;
      clearInterval(t);
    };
  }, [symbol, points]);

  const bid = css("--color-bid", "#22c55e");
  const ask = css("--color-ask", "#ef4444");
  const amber = css("--color-amber", "#f59e0b");
  const muted = css("--color-muted", "#8a8f98");
  const up = Number(stat?.changePercent ?? 0) >= 0;

  const data = {
    labels: ticks.map((t) => new Date(t.time).toLocaleTimeString([], { hour: "2-digit", minute: "2-digit", second: "2-digit" })),
    datasets: [
      {
        label: "Price",
        data: ticks.map((t) => t.price),
        borderColor: up ? bid : ask,
        backgroundColor: "transparent",
        borderWidth: 1.5,
        pointRadius: 0,
        tension: 0.15,
        fill: false,
      },
      {
        label: "SMA 12",
        data: ticks.map((t) => t.smaFast),
        borderColor: amber,
        borderWidth: 1,
        borderDash: [4, 3],
        pointRadius: 0,
        tension: 0.2,
      },
      {
        label: "SMA 48",
        data: ticks.map((t) => t.smaSlow),
        borderColor: muted,
        borderWidth: 1,
        borderDash: [2, 4],
        pointRadius: 0,
        tension: 0.2,
      },
    ],
  };

  const options = {
    responsive: true,
    maintainAspectRatio: false,
    animation: false,
    interaction: { mode: "index", intersect: false },
    plugins: {
      legend: { labels: { color: muted, boxWidth: 10, font: { size: 10, family: "monospace" } } },
      tooltip: { callbacks: { label: (c) => `${c.dataset.label}: ${fmt(c.parsed.y)}` } },
    },
    scales: {
      x: {
        ticks: { color: muted, maxTicksLimit: 6, font: { size: 9, family: "monospace" } },
        grid: { display: false },
      },
      y: {
        ticks: { color: muted, font: { size: 9, family: "monospace" }, callback: (v) => fmt(v, 0) },
        grid: { color: "rgba(255,255,255,0.05)" },
      },
    },
  };

  return (
    <section className="panel p-4 space-y-3">
      <div className="flex items-center justify-between flex-wrap gap-2">
        <div className="flex items-baseline gap-3">
          <p className="label">Analytics · {symbol}</p>
          <span className="font-mono text-lg">{stat ? fmt(stat.price) : "—"}</span>
          <span className="font-mono text-xs" style={{ color: up ? bid : ask }}>
            {stat ? `${up ? "+" : ""}${fmt(stat.changePercent)}%` : "—"}
          </span>
          {stat ? (
            <span className="font-mono text-[10px] uppercase" style={{ color: stat.trend === "bullish" ? bid : ask }}>
              {stat.trend}
            </span>
          ) : null}
        </div>
        <div className="flex gap-1">
          {[60, 120, 240].map((p) => (
            <button
              key={p}
              className="btn !py-1 !px-2"
              onClick={() => setPoints(p)}
              style={p === points ? { borderColor: amber, color: amber } : undefined}
            >
              {p}
            </button>
          ))}
        </div>
      </div>

      {error ? (
        <p className="font-mono text-xs text-muted py-10 text-center">
          Python analytics service unreachable — start it with{" "}
          <span className="text-ink">python analytics/app.py</span>.
        </p>
      ) : (
        <div className="h-[220px]">
          <Line data={data} options={options} />
        </div>
      )}

      <div className="grid grid-cols-4 gap-2 font-mono text-[11px]">
        <div><p className="label">1h High</p>{stat ? fmt(stat.high) : "—"}</div>
        <div><p className="label">1h Low</p>{stat ? fmt(stat.low) : "—"}</div>
        <div><p className="label">SMA 12</p>{stat ? fmt(stat.smaFast) : "—"}</div>
        <div><p className="label">Vol σ</p>{stat ? `${fmt(stat.volatility, 3)}%` : "—"}</div>
      </div>
    </section>
  );
}
