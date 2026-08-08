import { useState } from "react";
import { api } from "../lib/api.js";

export default function OrderTicket({ symbol, onPlaced }) {
  const [side, setSide] = useState("BUY");
  const [type, setType] = useState("LIMIT");
  const [price, setPrice] = useState("30000");
  const [quantity, setQuantity] = useState("0.10");
  const [status, setStatus] = useState(null);
  const [busy, setBusy] = useState(false);

  const submit = async (e) => {
    e.preventDefault();
    setBusy(true);
    setStatus(null);
    try {
      const order = await api.placeOrder({
        symbol,
        side,
        type,
        price: type === "LIMIT" ? price : null,
        quantity,
      });
      setStatus({ ok: true, text: `${order.status} · filled ${order.filledQuantity}` });
      onPlaced?.();
    } catch (err) {
      setStatus({ ok: false, text: err.message });
    } finally {
      setBusy(false);
    }
  };

  const accent = side === "BUY" ? "var(--color-bid)" : "var(--color-ask)";

  return (
    <form onSubmit={submit} className="panel p-4 space-y-3">
      <p className="label">Order ticket</p>

      <div className="grid grid-cols-2 gap-2">
        {["BUY", "SELL"].map((s) => (
          <button
            key={s}
            type="button"
            className="btn"
            onClick={() => setSide(s)}
            style={
              side === s
                ? { borderColor: accent, color: accent }
                : undefined
            }
          >
            {s}
          </button>
        ))}
      </div>

      <div className="grid grid-cols-2 gap-2">
        {["LIMIT", "MARKET"].map((t) => (
          <button
            key={t}
            type="button"
            className="btn"
            onClick={() => setType(t)}
            style={type === t ? { borderColor: "var(--color-amber)", color: "var(--color-amber)" } : undefined}
          >
            {t}
          </button>
        ))}
      </div>

      {type === "LIMIT" ? (
        <div>
          <label className="label" htmlFor="price">Price</label>
          <input
            id="price"
            className="field mt-1"
            value={price}
            onChange={(e) => setPrice(e.target.value)}
            inputMode="decimal"
            required
          />
        </div>
      ) : null}

      <div>
        <label className="label" htmlFor="qty">Quantity</label>
        <input
          id="qty"
          className="field mt-1"
          value={quantity}
          onChange={(e) => setQuantity(e.target.value)}
          inputMode="decimal"
          required
        />
      </div>

      <button className="btn w-full" disabled={busy} style={{ borderColor: accent, color: accent }}>
        {busy ? "Routing…" : `${side} ${symbol}`}
      </button>

      {status ? (
        <p className="font-mono text-xs" style={{ color: status.ok ? "var(--color-bid)" : "var(--color-ask)" }}>
          {status.text}
        </p>
      ) : null}
      <p className="text-muted font-mono text-[10px]">Every submit carries a fresh Idempotency-Key.</p>
    </form>
  );
}
