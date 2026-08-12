import { useEffect, useRef, useState } from "react";
import { Client } from "@stomp/stompjs";
import { api } from "./api.js";

const WS_URL =
  import.meta.env.VITE_WS_URL ||
  api.baseUrl.replace(/^http/, "ws") + "/ws";

/**
 * Hook: useOrderBook
 * Subscribes to /topic/orderbook/{symbol} via STOMP WebSockets.
 * Falls back to REST polling whenever the socket is unavailable.
 */
export function useOrderBook(symbol) {
  const [book, setBook] = useState({ symbol, bids: [], asks: [] });
  const [connected, setConnected] = useState(false);

  // Keep latest symbol reference for subscription
  const symbolRef = useRef(symbol);
  symbolRef.current = symbol;

  useEffect(() => {
    let cancelled = false;

    const pull = async () => {
      try {
        const data = await api.book(symbol);
        if (!cancelled) setBook(data);
      } catch {
        // ignore polling errors
      }
    };

    // Initial fetch + polling fallback
    pull();
    const poll = setInterval(() => {
      if (!cancelled) pull();
    }, 4000);

    // STOMP client setup
    const client = new Client({
      brokerURL: WS_URL,
      reconnectDelay: 3000,
      onConnect: () => {
        setConnected(true);
        client.subscribe(`/topic/orderbook/${symbol}`, (frame) => {
          try {
            const payload = JSON.parse(frame.body);
            setBook(payload);
          } catch {
            // ignore malformed frames
          }
        });
      },
      onWebSocketClose: () => setConnected(false),
      onStompError: () => setConnected(false),
    });

    client.activate();

    return () => {
      cancelled = true;
      clearInterval(poll);
      client.deactivate();
    };
  }, [symbol]);

  return { book, connected };
}

