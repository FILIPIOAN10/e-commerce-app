import SockJS from "sockjs-client";
import { Client } from "@stomp/stompjs";

const BACKEND_URL = import.meta.env.VITE_BACK_END_URL || "http://localhost:8080";

let stompClient = null;
let onNotificationCallback = null;

export const connectWebSocket = (onNotification) => {
    // Always take the latest callback: a caller that reconnects with a new
    // handler must not be left wired to the previous one.
    onNotificationCallback = onNotification;

    if (stompClient && stompClient.active) {
        return;
    }

    const client = new Client({
        webSocketFactory: () => new SockJS(`${BACKEND_URL}/api/ws-notifications`),
        reconnectDelay: 5000,
        heartbeatIncoming: 10000,
        heartbeatOutgoing: 10000,
        onConnect: () => {
            client.subscribe("/user/queue/notifications", (message) => {
                let notification;
                try {
                    notification = JSON.parse(message.body);
                } catch {
                    // A malformed frame must not throw inside the STOMP handler,
                    // which would tear down the subscription for every later one.
                    console.warn("Discarded malformed notification frame");
                    return;
                }
                onNotificationCallback?.(notification);
            });
        },
        onStompError: (frame) => {
            console.error("STOMP error:", frame.headers["message"]);
        },
    });

    stompClient = client;
    client.activate();
};

/**
 * Closes the connection and resolves only once the socket is actually gone.
 *
 * deactivate() is asynchronous. Clearing the module reference before it settles
 * made the guard in connectWebSocket read false while the old socket was still
 * closing, so a mount → cleanup → mount cycle (React StrictMode does exactly
 * this) left two live SockJS connections, two subscriptions, and every
 * notification dispatched twice.
 */
export const disconnectWebSocket = async () => {
    const client = stompClient;
    stompClient = null;
    onNotificationCallback = null;
    if (client) {
        try {
            await client.deactivate();
        } catch {
            // Already closing or never connected; nothing to unwind.
        }
    }
};
