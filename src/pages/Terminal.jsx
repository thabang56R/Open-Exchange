import { useCallback, useEffect, useState } from "react";
import OrderBook from "../components/OrderBook.jsx";
import OrderTicket from "../components/OrderTicket.jsx";
import Wallet from "../components/Wallet.jsx";
import Orders from "../components/Orders.jsx";
import PriceChart from "../components/PriceChart.jsx";
import MarketChart from "../components/MarketChart.jsx";
import TradesFeed from "../components/TradesFeed.jsx";
import AdminPanel from "../components/AdminPanel.jsx";
import AiChat from "../components/AiChat.jsx";
import { useOrderBook } from "../lib/useOrderBook.js";
import { api } from "../lib/api.js";
import { analytics } from "../lib/analyticsApi.js";

const MARKETS = ["BTC-USD", "ETH-USD", "SOL-USD"];

export default function Terminal({ onLogout }) {
  const [symbol, setSymbol] = useState(MARKETS[0]);
  const [balances, setBalances] = useState({});
  const [orders, setOrders] = useState([]);
  const [me, setMe] = useState(null);
  const [marketStates, setMarketStates] = useState([]);
  const [tab, setTab] = useState("trade");
  const { book, connected } = useOrderBook(symbol);

  const refresh = useCallback(() => {
    api.balances().then((r) => setBalances(r.balances || {})).catch(() => {});
    api.orders().then(setOrders).catch(() => {});
    api.marketStatus().then(setMarketStates).catch(() => {});
  }, []);

  useEffect(() => {
    api.me().then(setMe).catch(() => {});
    refresh();
    const t = setInterval(refresh, 5000);
    return () => clearInterval(t);
  }, [refresh]);

  const halted = marketStates.find((m) => m.symbol === symbol)?.status === "HALTED";

  return (
    <div className="min-h-full">
      <header className="border-b border-edge px-4 py-3 flex items-center justify-between flex-wrap gap-3">
        <div className="flex items-baseline gap-3">
          <h1 className="font-mono text-sm tracking-widest">
            OPEN<span className="text-amber">EX</span> 3.0
          </h1>
          <span className="label">Realtime terminal</span>
          {halted ? (
            <span className="font-mono text-[10px] px-2 py-[2px] rounded" style={{ background: "var(--color-ask)", color: "var(--color-void)" }}>
              {symbol} HALTED
            </span>
          ) : null}
        </div>
        <div className="flex items-center gap-2 flex-wrap">
          {MARKETS.map((m) => (
            <button
              key={m}
              className="btn !py-1 !px-2"
              onClick={() => setSymbol(m)}
              style={m === symbol ? { borderColor: "var(--color-amber)", color: "var(--color-amber)" } : undefined}
            >
              {m}
            </button>
          ))}
          {me?.admin ? (
            <button
              className="btn !py-1 !px-2"
              onClick={() => setTab(tab === "admin" ? "trade" : "admin")}
              style={tab === "admin" ? { borderColor: "var(--color-amber)", color: "var(--color-amber)" } : undefined}
            >
              {tab === "admin" ? "Terminal" : "Admin"}
            </button>
          ) : null}
          <button className="btn !py-1 !px-2" onClick={onLogout}>
            Sign out
          </button>
        </div>
      </header>

      {tab === "admin" ? (
        <main className="p-4 max-w-5xl mx-auto">
          <AdminPanel />
        </main>
      ) : (
        <main className="p-4 grid gap-4 lg:grid-cols-[320px_1fr_300px]">
          <div className="space-y-4">
            <Wallet balances={balances} onChange={refresh} />
            <OrderTicket symbol={symbol} onPlaced={refresh} />
          </div>

          <div className="space-y-4">
            <MarketChart symbol={symbol} />
            <PriceChart symbol={symbol} />
            <OrderBook book={book} connected={connected} symbol={symbol} />
            <Orders orders={orders} onChange={refresh} />
          </div>

          <aside className="space-y-4">
            <TradesFeed symbol={symbol} />
            <div className="panel p-4 space-y-2">
              <p className="label">Session</p>
              <p className="font-mono text-xs text-muted">
                Book frames arrive on <span className="text-ink">/topic/orderbook/{symbol}</span> and fall back to
                REST polling when the socket drops.
              </p>
              <p className="font-mono text-[10px] text-muted">
                Roles · {me?.roles?.join(", ") || "—"}
              </p>
              <p className="font-mono text-[10px] text-muted">API · {api.baseUrl}</p>
              <p className="font-mono text-[10px] text-muted">Analytics · {analytics.baseUrl}</p>
            </div>
          </aside>
        </main>
      )}

      {tab === "admin" ? null : <AiChat symbol={symbol} />}
    </div>
  );
}
