import { useEffect, useState } from "react";
import { Routes, Route, Navigate, useNavigate, useLocation } from "react-router-dom";
import Login from "./pages/Login.jsx";
import Terminal from "./pages/Terminal.jsx";
import { getToken, setToken } from "./lib/api.js";

export default function App() {
  const [token, setTokenState] = useState(getToken());
  const navigate = useNavigate();
  const location = useLocation();

  useEffect(() => {
    if (!token && location.pathname !== "/login") navigate("/login", { replace: true });
    if (token && location.pathname === "/login") navigate("/", { replace: true });
  }, [token, location.pathname, navigate]);

  const onAuth = (value) => {
    setToken(value);
    setTokenState(value);
  };

  return (
    <Routes>
      <Route path="/login" element={<Login onAuth={onAuth} />} />
      <Route
        path="/"
        element={token ? <Terminal onLogout={() => onAuth(null)} /> : <Navigate to="/login" replace />}
      />
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  );
}
