const fmt = (n, d = 4) =>
  Number(n ?? 0).toLocaleString(undefined, { minimumFractionDigits: d, maximumFractionDigits: d });

function Side({ levels, kind }) {
  const max = Math.max(...levels.map((l) => Number(l.quantity)), 1);
  const color = kind === "bid" ? "var(--color-bid)" : "var(--color-ask)";
  return (
    <div className="space-y-[2px]">
      {levels.length === 0 ? (
        <p className="text-muted font-mono text-xs py-2">No {kind}s resting</p>
      ) : null}
      {levels.map((l, i) => (
        <div key={`${kind}-${i}`} className="relative font-mono text-xs px-2 py-[3px]">
          <div
            className="absolute inset-y-0 right-0 opacity-15"
            style={{ width: `${(Number(l.quantity) / max) * 100}%`, background: color }}
          />
          <div className="relative flex justify-between">
            <span style={{ color }}>{fmt(l.price, 2)}</span>
            <span className="text-ink/80">{fmt(l.quantity)}</span>
          </div>
        </div>
      ))}
    </div>
  );
}

export default function OrderBook({ book, connected, symbol }) {
  const bestBid = book.bids?.[0]?.price;
  const bestAsk = book.asks?.[0]?.price;
  const spread = bestBid && bestAsk ? Number(bestAsk) - Number(bestBid) : null;

  return (
    <section className="panel p-4">
      <header className="flex items-center justify-between mb-3">
        <div>
          <p className="label">Order book</p>
          <h2 className="font-mono text-sm mt-1">{symbol}</h2>
        </div>
        <span className="font-mono text-[10px] flex items-center gap-2">
          <span
            className="inline-block size-2 rounded-full"
            style={{ background: connected ? "var(--color-bid)" : "var(--color-amber)" }}
          />
          {connected ? "STOMP LIVE" : "POLLING"}
        </span>
      </header>

      <div className="grid grid-cols-2 gap-3">
        <div>
          <p className="label mb-1">Bids</p>
          <Side levels={book.bids || []} kind="bid" />
        </div>
        <div>
          <p className="label mb-1">Asks</p>
          <Side levels={book.asks || []} kind="ask" />
        </div>
      </div>

      <footer className="mt-3 pt-3 border-t border-edge font-mono text-xs text-muted flex justify-between">
        <span>Spread</span>
        <span className="text-ink">{spread === null ? "—" : fmt(spread, 2)}</span>
      </footer>
    </section>
  );
}
