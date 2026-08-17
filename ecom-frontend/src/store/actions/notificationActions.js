import api from "../../api/api";

export const fetchNotifications = (page = 0, size = 20) => async (dispatch) => {
    try {
        const { data } = await api.get(`/notifications?page=${page}&size=${size}`);
        dispatch({ type: "SET_NOTIFICATIONS", payload: data });
    } catch (error) {
        // silently fail
    }
};

export const fetchUnreadNotificationCount = () => async (dispatch) => {
    try {
        const { data } = await api.get(`/notifications/unread-count`);
        dispatch({ type: "SET_UNREAD_NOTIFICATION_COUNT", payload: data.unreadCount });
    } catch (error) {
        // silently fail
    }
};

export const markNotificationsAsRead = () => async (dispatch) => {
    try {
        await api.put(`/notifications/mark-all-read`);
        dispatch({ type: "MARK_NOTIFICATIONS_READ" });
    } catch (error) {
        // silently fail
    }
};

export const addNotification = (notification) => (dispatch) => {
    dispatch({ type: "ADD_NOTIFICATION", payload: notification });
};
