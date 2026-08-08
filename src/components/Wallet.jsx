import { useState } from "react";
import { api } from "../lib/api.js";

export default function Wallet({ balances, onChange }) {
  const [asset, setAsset] = useState("USD");
  const [amount, setAmount] = useState("10000");
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);

  const deposit = async (e) => {
    e.preventDefault();
    setBusy(true);
    setError("");
    try {
      await api.deposit(asset, amount);
      onChange?.();
    } catch (err) {
      setError(err.message);
    } finally {
      setBusy(false);
    }
  };

  const entries = Object.entries(balances || {});

  return (
    <section className="panel p-4 space-y-3">
      <p className="label">Wallet</p>

      <div className="space-y-1">
        {entries.length === 0 ? (
          <p className="text-muted font-mono text-xs">No funded assets yet.</p>
        ) : (
          entries.map(([code, value]) => (
            <div key={code} className="flex justify-between font-mono text-xs">
              <span className="text-muted">{code}</span>
              <span>{Number(value).toLocaleString(undefined, { maximumFractionDigits: 8 })}</span>
            </div>
          ))
        )}
      </div>

      <form onSubmit={deposit} className="grid grid-cols-[1fr_1.4fr_auto] gap-2 pt-2 border-t border-edge">
        <input
          className="field"
          value={asset}
          onChange={(e) => setAsset(e.target.value.toUpperCase())}
          aria-label="Asset"
          required
        />
        <input
          className="field"
          value={amount}
          onChange={(e) => setAmount(e.target.value)}
          aria-label="Amount"
          inputMode="decimal"
          required
        />
        <button className="btn" disabled={busy}>
          {busy ? "…" : "Fund"}
        </button>
      </form>
      {error ? <p className="text-ask font-mono text-xs">{error}</p> : null}
    </section>
  );
}
