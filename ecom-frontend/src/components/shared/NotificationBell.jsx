import { useEffect, useState, useRef } from "react";
import { useDispatch, useSelector } from "react-redux";
import { FaBell, FaCircle } from "react-icons/fa";
import {
    fetchNotifications,
    fetchUnreadNotificationCount,
    markNotificationsAsRead,
    addNotification,
} from "../../store/actions";
import { connectWebSocket, disconnectWebSocket } from "../../services/websocketService";

const NotificationBell = () => {
    const dispatch = useDispatch();
    const { user } = useSelector((state) => state.auth);
    const { notifications, unreadCount } = useSelector((state) => state.notification);
    const [dropdownOpen, setDropdownOpen] = useState(false);
    const dropdownRef = useRef(null);

    // Connect WebSocket when user logs in
    useEffect(() => {
        if (user?.jwtToken) {
            connectWebSocket(user.jwtToken, (notification) => {
                dispatch(addNotification(notification));
            });
            dispatch(fetchNotifications());
            dispatch(fetchUnreadNotificationCount());
        }
        return () => {
            disconnectWebSocket();
        };
    }, [user?.jwtToken, dispatch]);

    // Close dropdown when clicking outside
    useEffect(() => {
        const handleClickOutside = (e) => {
            if (dropdownRef.current && !dropdownRef.current.contains(e.target)) {
                setDropdownOpen(false);
            }
        };
        document.addEventListener("mousedown", handleClickOutside);
        return () => document.removeEventListener("mousedown", handleClickOutside);
    }, []);

    const handleBellClick = () => {
        const newOpen = !dropdownOpen;
        setDropdownOpen(newOpen);
        if (newOpen && unreadCount > 0) {
            dispatch(markNotificationsAsRead());
        }
    };

    if (!user?.id) return null;

    return (
        <div className="relative" ref={dropdownRef}>
            <button
                onClick={handleBellClick}
                className="relative text-white hover:text-yellow-300 transition-colors text-xl"
                title="Notifications"
            >
                <FaBell />
                {unreadCount > 0 && (
                    <span className="absolute -top-1 -right-1 bg-red-500 text-white text-[10px] font-bold rounded-full min-w-[16px] h-[16px] flex items-center justify-center px-1">
                        {unreadCount > 99 ? "99+" : unreadCount}
                    </span>
                )}
            </button>

            {dropdownOpen && (
                <div className="absolute right-0 mt-2 w-80 bg-white dark:bg-gray-800 rounded-lg shadow-xl border border-gray-200 dark:border-gray-700 z-50 max-h-96 overflow-y-auto">
                    <div className="px-4 py-3 border-b border-gray-200 dark:border-gray-700">
                        <h3 className="text-sm font-semibold text-gray-800 dark:text-white">
                            Notifications
                        </h3>
                    </div>

                    {notifications.length === 0 ? (
                        <div className="px-4 py-8 text-center text-gray-500 dark:text-gray-400 text-sm">
                            No notifications yet
                        </div>
                    ) : (
                        <div className="divide-y divide-gray-100 dark:divide-gray-700">
                            {notifications.slice(0, 15).map((n) => (
                                <div
                                    key={n.id}
                                    className={`px-4 py-3 hover:bg-gray-50 dark:hover:bg-gray-700/50 transition ${
                                        !n.read ? "bg-blue-50 dark:bg-blue-900/20" : ""
                                    }`}
                                >
                                    <div className="flex items-start gap-2">
                                        {!n.read && (
                                            <FaCircle className="text-blue-500 text-[8px] mt-1.5 flex-shrink-0" />
                                        )}
                                        <div className="flex-1 min-w-0">
                                            <p className="text-sm font-medium text-gray-800 dark:text-gray-200">
                                                {n.title}
                                            </p>
                                            <p className="text-xs text-gray-600 dark:text-gray-400 mt-0.5">
                                                {n.message}
                                            </p>
                                            {n.createdAt && (
                                                <p className="text-[10px] text-gray-400 dark:text-gray-500 mt-1">
                                                    {new Date(n.createdAt).toLocaleString()}
                                                </p>
                                            )}
                                        </div>
                                    </div>
                                </div>
                            ))}
                        </div>
                    )}
                </div>
            )}
        </div>
    );
};

export default NotificationBell;
