import { useEffect, useRef, useState } from "react";
import { Client } from "@stomp/stompjs";
import { api } from "./api.js";

const WS_URL =
  import.meta.env.VITE_WS_URL ||
  api.baseUrl.replace(/^http/, "ws") + "/ws";

/**
 * Subscribes to /topic/orderbook/{symbol} over STOMP and falls back to REST
 * polling whenever the socket is unavailable.
 */
export function useOrderBook(symbol) {
  const [book, setBook] = useState({ symbol, bids: [], asks: [] });
  const [connected, setConnected] = useState(false);
  const symbolRef = useRef(symbol);
  symbolRef.current = symbol;

  useEffect(() => {
    let cancelled = false;

    const pull = () =>
      api
        .book(symbol)
        .then((data) => {
          if (!cancelled) setBook(data);
        })
        .catch(() => {});

    pull();
    const poll = setInterval(() => {
      if (!cancelled) pull();
    }, 4000);

    const client = new Client({
      brokerURL: WS_URL,
      reconnectDelay: 3000,
      onConnect: () => {
        setConnected(true);
        client.subscribe(`/topic/orderbook/${symbol}`, (frame) => {
          try {
            setBook(JSON.parse(frame.body));
          } catch (_) {
            /* ignore malformed frame */
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
