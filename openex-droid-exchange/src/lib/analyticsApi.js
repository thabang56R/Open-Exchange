const ANALYTICS_URL = import.meta.env.VITE_ANALYTICS_URL || "http://localhost:5001";

import { getToken } from "./api.js";

async function request(path, { method = "GET", body } = {}) {
  const token = getToken();
  const res = await fetch(`${ANALYTICS_URL}${path}`, {
    method,
    headers: {
      "Content-Type": "application/json",
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
    },
    body: body ? JSON.stringify(body) : undefined,
  });
  const text = await res.text();
  const data = text ? JSON.parse(text) : null;
  if (!res.ok) throw new Error(data?.message || data?.error || `Analytics request failed (${res.status})`);
  return data;
}

export const analytics = {
  baseUrl: ANALYTICS_URL,
  health: () => request("/health"),
  symbols: () => request("/api/analytics/symbols"),
  history: (symbol, points = 240) =>
    request(`/api/analytics/history?symbol=${symbol}&points=${points}`),
  latest: (symbol) => request(`/api/analytics/latest?symbol=${symbol}`),
  summary: () => request("/api/analytics/summary"),
  chat: (message, history = []) => request("/api/chat", { method: "POST", body: { message, history } }),
};