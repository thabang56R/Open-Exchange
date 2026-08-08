import { useCallback, useEffect, useState } from "react";
import OrderBook from "../components/OrderBook.jsx";
import OrderTicket from "../components/OrderTicket.jsx";
import Wallet from "../components/Wallet.jsx";
import Orders from "../components/Orders.jsx";
import { useOrderBook } from "../lib/useOrderBook.js";
import { api } from "../lib/api.js";

const MARKETS = ["BTC-USD", "ETH-USD", "SOL-USD"];

export default function Terminal({ onLogout }) {
  const [symbol, setSymbol] = useState(MARKETS[0]);
  const [balances, setBalances] = useState({});
  const [orders, setOrders] = useState([]);
  const { book, connected } = useOrderBook(symbol);

  const refresh = useCallback(() => {
    api.balances().then((r) => setBalances(r.balances || {})).catch(() => {});
    api.orders().then(setOrders).catch(() => {});
  }, []);

  useEffect(() => {
    refresh();
    const t = setInterval(refresh, 5000);
    return () => clearInterval(t);
  }, [refresh]);

  return (
    <div className="min-h-full">
      <header className="border-b border-edge px-4 py-3 flex items-center justify-between flex-wrap gap-3">
        <div className="flex items-baseline gap-3">
          <h1 className="font-mono text-sm tracking-widest">
            OPEN<span className="text-amber">EX</span> 3.0
          </h1>
          <span className="label">Realtime terminal</span>
        </div>
        <div className="flex items-center gap-2">
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
          <button className="btn !py-1 !px-2" onClick={onLogout}>
            Sign out
          </button>
        </div>
      </header>

      <main className="p-4 grid gap-4 lg:grid-cols-[320px_1fr_300px]">
        <div className="space-y-4">
          <Wallet balances={balances} onChange={refresh} />
          <OrderTicket symbol={symbol} onPlaced={refresh} />
        </div>

        <div className="space-y-4">
          <OrderBook book={book} connected={connected} symbol={symbol} />
          <Orders orders={orders} onChange={refresh} />
        </div>

        <aside className="panel p-4 space-y-2 h-fit">
          <p className="label">Session</p>
          <p className="font-mono text-xs text-muted">
            Order book frames arrive on <span className="text-ink">/topic/orderbook/{symbol}</span> and fall back to
            REST polling when the socket drops.
          </p>
          <p className="font-mono text-[10px] text-muted">API · {api.baseUrl}</p>
        </aside>
      </main>
    </div>
  );
}
