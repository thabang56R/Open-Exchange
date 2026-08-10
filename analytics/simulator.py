"""Market simulator: geometric random walk with drift + moving averages.

Pandas/NumPy do the heavy lifting; the Flask layer only serialises the frame.
"""
from __future__ import annotations

import threading
import time
from datetime import datetime, timedelta, timezone

import numpy as np
import pandas as pd

# symbol -> (seed price, annualised drift, volatility)
SYMBOLS = {
    "BTC-USD": (30000.0, 0.12, 0.65),
    "ETH-USD": (1800.0, 0.10, 0.75),
    "SOL-USD": (25.0, 0.18, 0.95),
}

STEP_SECONDS = 5
HISTORY_POINTS = 720  # one hour of 5s ticks


def _random_walk(seed_price: float, drift: float, vol: float, n: int, rng: np.random.Generator) -> np.ndarray:
    """Geometric Brownian motion sampled at STEP_SECONDS."""
    dt = STEP_SECONDS / (365 * 24 * 60 * 60)
    shocks = rng.normal(loc=(drift - 0.5 * vol**2) * dt, scale=vol * np.sqrt(dt), size=n)
    return seed_price * np.exp(np.cumsum(shocks))


class MarketSimulator:
    """Keeps a rolling DataFrame per symbol and advances it on a background thread."""

    def __init__(self, seed: int = 7) -> None:
        self._lock = threading.Lock()
        self._rng = np.random.default_rng(seed)
        self._frames: dict[str, pd.DataFrame] = {}
        now = datetime.now(timezone.utc).replace(microsecond=0)

        for symbol, (price, drift, vol) in SYMBOLS.items():
            prices = _random_walk(price, drift, vol, HISTORY_POINTS, self._rng)
            index = pd.date_range(
                end=now, periods=HISTORY_POINTS, freq=f"{STEP_SECONDS}s", tz=timezone.utc
            )
            self._frames[symbol] = self._with_indicators(
                pd.DataFrame({"time": index, "price": prices})
            )

        self._stop = threading.Event()
        self._thread = threading.Thread(target=self._loop, daemon=True)
        self._thread.start()

    # ---------------------------------------------------------------- internals
    @staticmethod
    def _with_indicators(df: pd.DataFrame) -> pd.DataFrame:
        df = df.copy()
        df["sma_fast"] = df["price"].rolling(window=12, min_periods=1).mean()
        df["sma_slow"] = df["price"].rolling(window=48, min_periods=1).mean()
        df["returns"] = df["price"].pct_change().fillna(0.0)
        df["volatility"] = df["returns"].rolling(window=48, min_periods=2).std().fillna(0.0)
        return df

    def _loop(self) -> None:
        while not self._stop.wait(STEP_SECONDS):
            self.tick()

    def tick(self) -> None:
        with self._lock:
            for symbol, (_, drift, vol) in SYMBOLS.items():
                df = self._frames[symbol]
                last_price = float(df["price"].iloc[-1])
                nxt = float(_random_walk(last_price, drift, vol, 1, self._rng)[0])
                row = {
                    "time": df["time"].iloc[-1] + timedelta(seconds=STEP_SECONDS),
                    "price": nxt,
                }
                df = pd.concat([df, pd.DataFrame([row])], ignore_index=True)
                self._frames[symbol] = self._with_indicators(df.tail(HISTORY_POINTS))

    # ------------------------------------------------------------------- public
    def symbols(self) -> list[str]:
        return list(SYMBOLS.keys())

    def frame(self, symbol: str) -> pd.DataFrame:
        with self._lock:
            if symbol not in self._frames:
                raise KeyError(symbol)
            return self._frames[symbol].copy()

    def history(self, symbol: str, points: int = 240) -> list[dict]:
        df = self.frame(symbol).tail(max(1, min(points, HISTORY_POINTS)))
        return [
            {
                "time": t.isoformat(),
                "price": round(float(p), 2),
                "smaFast": round(float(f), 2),
                "smaSlow": round(float(s), 2),
            }
            for t, p, f, s in zip(df["time"], df["price"], df["sma_fast"], df["sma_slow"])
        ]

    def latest(self, symbol: str) -> dict:
        df = self.frame(symbol)
        last = df.iloc[-1]
        first = df.iloc[0]
        change = float(last["price"]) - float(first["price"])
        return {
            "symbol": symbol,
            "time": last["time"].isoformat(),
            "price": round(float(last["price"]), 2),
            "smaFast": round(float(last["sma_fast"]), 2),
            "smaSlow": round(float(last["sma_slow"]), 2),
            "high": round(float(df["price"].max()), 2),
            "low": round(float(df["price"].min()), 2),
            "change": round(change, 2),
            "changePercent": round(change / float(first["price"]) * 100, 2),
            "volatility": round(float(last["volatility"]) * 100, 4),
            "trend": "bullish" if last["sma_fast"] >= last["sma_slow"] else "bearish",
        }

    def summary(self) -> list[dict]:
        return [self.latest(s) for s in self.symbols()]


simulator = MarketSimulator()
