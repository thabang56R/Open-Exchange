const API_URL = import.meta.env.VITE_API_URL || "http://localhost:8080";

export const TOKEN_KEY = "openex.token";

export function getToken() {
  return localStorage.getItem(TOKEN_KEY);
}

export function setToken(token) {
  if (token) localStorage.setItem(TOKEN_KEY, token);
  else localStorage.removeItem(TOKEN_KEY);
}

export function newIdempotencyKey() {
  if (crypto.randomUUID) return crypto.randomUUID();
  return `${Date.now()}-${Math.random().toString(16).slice(2)}`;
}

async function request(path, { method = "GET", body, headers = {} } = {}) {
  const token = getToken();
  const res = await fetch(`${API_URL}${path}`, {
    method,
    headers: {
      "Content-Type": "application/json",
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...headers,
    },
    body: body ? JSON.stringify(body) : undefined,
  });

  const text = await res.text();
  const data = text ? JSON.parse(text) : null;
  if (!res.ok) {
    throw new Error(data?.message || data?.error || `Request failed (${res.status})`);
  }
  return data;
}

export const api = {
  baseUrl: API_URL,
  register: (username, password) =>
    request("/api/auth/register", { method: "POST", body: { username, password } }),
  login: (username, password) =>
    request("/api/auth/login", { method: "POST", body: { username, password } }),
  balances: () => request("/api/wallets/balances"),
  deposit: (asset, amount) =>
    request("/api/wallets/deposit", {
      method: "POST",
      body: { asset, amount },
      headers: { "Idempotency-Key": newIdempotencyKey() },
    }),
  placeOrder: (order) =>
    request("/api/orders", {
      method: "POST",
      body: order,
      headers: { "Idempotency-Key": newIdempotencyKey() },
    }),
  orders: () => request("/api/orders"),
  cancelOrder: (id) => request(`/api/orders/${id}`, { method: "DELETE" }),
  book: (symbol, levels = 15) =>
    request(`/api/market/${symbol}/book?levels=${levels}`),
};
