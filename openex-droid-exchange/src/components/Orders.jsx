import { api } from "../lib/api.js";

const STATUS_COLOR = {
  OPEN: "var(--color-amber)",
  PARTIALLY_FILLED: "var(--color-amber)",
  FILLED: "var(--color-bid)",
  CANCELLED: "var(--color-muted)",
};

/**
 * Orders component
 * Renders the user's active and historical orders with cancel functionality.
 */
export default function Orders({ orders, onChange }) {
  const cancelOrder = async (id) => {
    try {
      await api.cancelOrder(id);
      onChange?.();
    } catch {
      // Errors surfaced by refresh
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
            {orders.length === 0 && (
              <tr>
                <td colSpan={7} className="py-3 text-muted">
                  No orders routed yet.
                </td>
              </tr>
            )}

            {orders.map((order) => (
              <tr key={order.id} className="border-t border-edge">
                <td className="py-2">{order.symbol}</td>
                <td
                  style={{
                    color:
                      order.side === "BUY"
                        ? "var(--color-bid)"
                        : "var(--color-ask)",
                  }}
                >
                  {order.side}
                </td>
                <td>{order.price ?? "MKT"}</td>
                <td>{order.quantity}</td>
                <td>{order.filledQuantity}</td>
                <td
                  style={{
                    color: STATUS_COLOR[order.status] || "var(--color-ink)",
                  }}
                >
                  {order.status}
                </td>
                <td className="text-right">
                  {["OPEN", "PARTIALLY_FILLED"].includes(order.status) && (
                    <button
                      className="btn !py-1 !px-2"
                      onClick={() => cancelOrder(order.id)}
                    >
                      Cancel
                    </button>
                  )}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </section>
  );
}
