const initialState = {
    notifications: [],
    unreadCount: 0,
};

export const notificationReducer = (state = initialState, action) => {
    switch (action.type) {
        case "SET_NOTIFICATIONS":
            return {
                ...state,
                notifications: action.payload,
            };
        case "SET_UNREAD_NOTIFICATION_COUNT":
            return {
                ...state,
                unreadCount: action.payload,
            };
        case "ADD_NOTIFICATION":
            return {
                ...state,
                notifications: [action.payload, ...state.notifications],
                unreadCount: state.unreadCount + 1,
            };
        case "MARK_NOTIFICATIONS_READ":
            return {
                ...state,
                notifications: state.notifications.map((n) => ({ ...n, read: true })),
                unreadCount: 0,
            };
        default:
            return state;
    }
};
