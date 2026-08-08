import { api } from "../lib/api.js";

const statusColor = {
  OPEN: "var(--color-amber)",
  PARTIALLY_FILLED: "var(--color-amber)",
  FILLED: "var(--color-bid)",
  CANCELLED: "var(--color-muted)",
};

export default function Orders({ orders, onChange }) {
  const cancel = async (id) => {
    try {
      await api.cancelOrder(id);
      onChange?.();
    } catch (_) {
      /* surfaced by refresh */
    }
  };

  return (
    <section className="panel p-4">
      <p className="label mb-3">My orders</p>

      <div className="overflow-x-auto">
        <table className="w-full font-mono text-xs">
          <thead>
            <tr className="text-muted text-left">
              <th className="font-normal pb-2">Symbol</th>
              <th className="font-normal pb-2">Side</th>
              <th className="font-normal pb-2">Price</th>
              <th className="font-normal pb-2">Qty</th>
              <th className="font-normal pb-2">Filled</th>
              <th className="font-normal pb-2">Status</th>
              <th className="pb-2" />
            </tr>
          </thead>
          <tbody>
            {orders.length === 0 ? (
              <tr>
                <td colSpan={7} className="py-3 text-muted">
                  No orders routed yet.
                </td>
              </tr>
            ) : null}
            {orders.map((o) => (
              <tr key={o.id} className="border-t border-edge">
                <td className="py-2">{o.symbol}</td>
                <td style={{ color: o.side === "BUY" ? "var(--color-bid)" : "var(--color-ask)" }}>{o.side}</td>
                <td>{o.price ?? "MKT"}</td>
                <td>{o.quantity}</td>
                <td>{o.filledQuantity}</td>
                <td style={{ color: statusColor[o.status] || "var(--color-ink)" }}>{o.status}</td>
                <td className="text-right">
                  {o.status === "OPEN" || o.status === "PARTIALLY_FILLED" ? (
                    <button className="btn !py-1 !px-2" onClick={() => cancel(o.id)}>
                      Cancel
                    </button>
                  ) : null}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </section>
  );
}
