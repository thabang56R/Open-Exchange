import { useCallback, useEffect, useState } from "react";
import { api } from "../lib/api.js";

const MARKETS = ["BTC-USD", "ETH-USD", "SOL-USD"];

export default function AdminPanel() {
  const [users, setUsers] = useState([]);
  const [audit, setAudit] = useState([]);
  const [markets, setMarkets] = useState([]);
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);

  const refresh = useCallback(() => {
    api.adminUsers().then(setUsers).catch((e) => setError(e.message));
    api.adminAudit().then(setAudit).catch(() => {});
    api.marketStatus().then(setMarkets).catch(() => {});
  }, []);

  useEffect(() => {
    refresh();
    const t = setInterval(refresh, 8000);
    return () => clearInterval(t);
  }, [refresh]);

  const run = async (fn) => {
    setBusy(true);
    setError("");
    try {
      await fn();
      refresh();
    } catch (e) {
      setError(e.message);
    } finally {
      setBusy(false);
    }
  };

  const statusOf = (symbol) => markets.find((m) => m.symbol === symbol)?.status || "OPEN";

  return (
    <div className="space-y-4">
      {error ? <p className="font-mono text-xs" style={{ color: "var(--color-ask)" }}>{error}</p> : null}

      <section className="panel p-4 space-y-3">
        <p className="label">Market controls</p>
        <div className="space-y-2">
          {MARKETS.map((m) => {
            const halted = statusOf(m) === "HALTED";
            return (
              <div key={m} className="flex items-center justify-between font-mono text-xs">
                <span>{m}</span>
                <span style={{ color: halted ? "var(--color-ask)" : "var(--color-bid)" }}>{statusOf(m)}</span>
                <button
                  className="btn !py-1 !px-2"
                  disabled={busy}
                  onClick={() =>
                    run(() => api.setMarket(m, halted ? "OPEN" : "HALTED", halted ? null : "Admin halt"))
                  }
                >
                  {halted ? "Resume" : "Halt"}
                </button>
              </div>
            );
          })}
        </div>
      </section>

      <section className="panel p-4 space-y-3">
        <p className="label">Users &amp; risk limits</p>
        <div className="overflow-x-auto">
          <table className="w-full font-mono text-[11px]">
            <thead className="text-muted">
              <tr className="text-left">
                <th className="py-1">User</th>
                <th>Roles</th>
                <th>Max notional</th>
                <th>Max open</th>
                <th />
              </tr>
            </thead>
            <tbody>
              {users.map((u) => (
                <UserRow key={u.id} user={u} busy={busy} run={run} />
              ))}
            </tbody>
          </table>
        </div>
      </section>

      <section className="panel p-4 space-y-2">
        <p className="label">Audit trail</p>
        <div className="max-h-72 overflow-y-auto font-mono text-[11px] space-y-1">
          {audit.map((a) => (
            <div key={a.id} className="flex gap-3">
              <span className="text-muted">{new Date(a.createdAt).toLocaleTimeString()}</span>
              <span className="text-amber">{a.action}</span>
              <span className="text-ink/70 truncate">{a.detail}</span>
            </div>
          ))}
          {audit.length === 0 ? <p className="text-muted">No events recorded.</p> : null}
        </div>
      </section>
    </div>
  );
}

function UserRow({ user, busy, run }) {
  const [notional, setNotional] = useState(user.maxOrderNotional ?? "");
  const [open, setOpen] = useState(user.maxOpenOrders ?? "");
  const isAdmin = user.roles.includes("ADMIN");

  return (
    <tr className="border-t border-edge">
      <td className="py-1">{user.username}</td>
      <td>{user.roles.join(", ")}</td>
      <td>
        <input className="field !py-1 !w-28" value={notional} onChange={(e) => setNotional(e.target.value)} />
      </td>
      <td>
        <input className="field !py-1 !w-16" value={open} onChange={(e) => setOpen(e.target.value)} />
      </td>
      <td className="flex gap-1 py-1">
        <button
          className="btn !py-1 !px-2"
          disabled={busy}
          onClick={() => run(() => api.setLimits(user.id, notional || null, open ? Number(open) : null))}
        >
          Save
        </button>
        <button
          className="btn !py-1 !px-2"
          disabled={busy}
          onClick={() =>
            run(() => (isAdmin ? api.revokeRole(user.id, "ADMIN") : api.grantRole(user.id, "ADMIN")))
          }
        >
          {isAdmin ? "Revoke admin" : "Make admin"}
        </button>
      </td>
    </tr>
  );
}