import SockJS from "sockjs-client";
import { Client } from "@stomp/stompjs";

const BACKEND_URL = import.meta.env.VITE_BACK_END_URL || "http://localhost:8080";

let stompClient = null;
let onNotificationCallback = null;

export const connectWebSocket = (jwtToken, onNotification) => {
    if (stompClient && stompClient.active) {
        return;
    }

    onNotificationCallback = onNotification;

    stompClient = new Client({
        webSocketFactory: () => new SockJS(`${BACKEND_URL}/ws-notifications`),
        connectHeaders: {
            Authorization: `Bearer ${jwtToken}`,
        },
        reconnectDelay: 5000,
        heartbeatIncoming: 10000,
        heartbeatOutgoing: 10000,
        onConnect: () => {
            stompClient.subscribe("/user/queue/notifications", (message) => {
                const notification = JSON.parse(message.body);
                if (onNotificationCallback) {
                    onNotificationCallback(notification);
                }
            });
        },
        onStompError: (frame) => {
            console.error("STOMP error:", frame.headers["message"]);
        },
    });

    stompClient.activate();
};

export const disconnectWebSocket = () => {
    if (stompClient) {
        stompClient.deactivate();
        stompClient = null;
        onNotificationCallback = null;
    }
};
