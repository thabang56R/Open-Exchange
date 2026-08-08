import { useState } from "react";
import { api } from "../lib/api.js";

export default function Login({ onAuth }) {
  const [mode, setMode] = useState("login");
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);

  const submit = async (e) => {
    e.preventDefault();
    setError("");
    setBusy(true);
    try {
      const res =
        mode === "login"
          ? await api.login(username, password)
          : await api.register(username, password);
      onAuth(res.token);
    } catch (err) {
      setError(err.message);
    } finally {
      setBusy(false);
    }
  };

  return (
    <main className="min-h-full grid place-items-center px-4 py-10">
      <div className="w-full max-w-sm">
        <div className="mb-6">
          <p className="label">OpenEx 3.0</p>
          <h1 className="font-mono text-2xl tracking-tight mt-1">
            Trading <span className="text-amber">Terminal</span>
          </h1>
          <p className="text-muted text-sm mt-2">
            Authenticate to stream the live order book and route orders.
          </p>
        </div>

        <form onSubmit={submit} className="panel p-5 space-y-4">
          <div className="flex gap-2">
            {["login", "register"].map((m) => (
              <button
                key={m}
                type="button"
                onClick={() => setMode(m)}
                className="btn flex-1"
                style={m === mode ? { borderColor: "var(--color-amber)", color: "var(--color-amber)" } : undefined}
              >
                {m}
              </button>
            ))}
          </div>

          <div>
            <label className="label" htmlFor="username">Username</label>
            <input
              id="username"
              className="field mt-1"
              value={username}
              onChange={(e) => setUsername(e.target.value)}
              autoComplete="username"
              required
            />
          </div>

          <div>
            <label className="label" htmlFor="password">Password</label>
            <input
              id="password"
              type="password"
              className="field mt-1"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              autoComplete="current-password"
              minLength={8}
              required
            />
          </div>

          {error ? <p className="text-ask font-mono text-xs">{error}</p> : null}

          <button className="btn w-full" disabled={busy} type="submit">
            {busy ? "…" : mode === "login" ? "Sign in" : "Create account"}
          </button>
          <p className="text-muted font-mono text-[10px]">API · {api.baseUrl}</p>
        </form>
      </div>
    </main>
  );
}
