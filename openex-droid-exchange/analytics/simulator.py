"""
Market simulator: geometric random walk with drift + moving averages.

Pandas/NumPy do the heavy lifting; the Flask layer only serialises the frame.
"""

from __future__ import annotations

import threading
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


def _random_walk(
    seed_price: float,
    drift: float,
    vol: float,
    n: int,
    rng: np.random.Generator
) -> np.ndarray:
    """Geometric Brownian motion sampled at STEP_SECONDS."""
    dt = STEP_SECONDS / (365 * 24 * 60 * 60)
    shocks = rng.normal(
        loc=(drift - 0.5 * vol**2) * dt,
        scale=vol * np.sqrt(dt),
        size=n,
    )
    return seed_price * np.exp(np.cumsum(shocks))


class MarketSimulator:
    """
    Keeps a rolling DataFrame per symbol and advances it on a background thread.
    """

    def __init__(self, seed: int = 7) -> None:
        self._lock = threading.Lock()
        self._rng = np.random.default_rng(seed)
        self._frames: dict[str, pd.DataFrame] = {}
        now = datetime.now(timezone.utc).replace(microsecond=0)

        for symbol, (price, drift, vol) in SYMBOLS.items():
            prices = _random_walk(price, drift, vol, HISTORY_POINTS, self._rng)
            index = pd.date_range(
                end=now,
                periods=HISTORY_POINTS,
                freq=f"{STEP_SECONDS}s",
                tz=timezone.utc,
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
        """Add moving averages, returns, and volatility indicators."""
        df = df.copy()
        df["sma_fast"] = df["price"].rolling(window=12, min_periods=1).mean()
        df["sma_slow"] = df["price"].rolling(window=48, min_periods=1).mean()
        df["returns"] = df["price"].pct_change().fillna(0.0)
        df["volatility"] = (
            df["returns"].rolling(window=48, min_periods=2).std().fillna(0.0)
        )
        return df

    def _loop(self) -> None:
        """Background loop to advance ticks."""
        while not self._stop.wait(STEP_SECONDS):
            self.tick()

    def tick(self) -> None:
        """Advance the simulation by one tick per symbol."""
        with self._lock:
            for symbol, (_, drift, vol) in SYMBOLS.items():
                df = self._frames[symbol]
