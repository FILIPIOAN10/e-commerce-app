import React, { useState } from "react";
import { FaSearch, FaBoxOpen, FaTruck } from "react-icons/fa";
import OrderTrackingModal from "../shared/OrderTrackingModal";
import api from "../../api/api";
import Skeleton from "../shared/Skeleton";

const TrackOrder = () => {
    const [orderId, setOrderId] = useState("");
    const [searchId, setSearchId] = useState(null);
    const [open, setOpen] = useState(false);
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState("");
    const [order, setOrder] = useState(null);

    const handleSearch = async (e) => {
        e.preventDefault();
        if (!orderId.trim()) return;
        setLoading(true);
        setError("");
        setOrder(null);
        try {
            const { data } = await api.get(`/orders/track/${orderId.trim()}`);
            setOrder(data);
            setSearchId(Number(orderId.trim()));
            setOpen(true);
        } catch (err) {
            setError(err?.response?.data?.message || "Order not found");
        } finally {
            setLoading(false);
        }
    };

    return (
        <div className="min-h-[calc(100vh-100px)] flex items-center justify-center px-4 py-10 dark:bg-gray-950">
            <div className="w-full max-w-2xl">
                <div className="text-center mb-8">
                    <div className="inline-flex items-center justify-center w-16 h-16 bg-blue-100 dark:bg-blue-900/30 rounded-full mb-4">
                        <FaTruck className="text-3xl text-blue-500 dark:text-blue-400" />
                    </div>
                    <h1 className="text-3xl font-bold text-slate-800 dark:text-white mb-2">Track Your Order</h1>
                    <p className="text-gray-500 dark:text-gray-400 text-sm">
                        Enter your order ID below to see the current status and tracking timeline.
                    </p>
                </div>

                <form onSubmit={handleSearch} className="flex gap-3 mb-6">
                    <div className="flex-1 relative">
                        <input
                            type="text"
                            value={orderId}
                            onChange={(e) => setOrderId(e.target.value)}
                            placeholder="Enter Order ID (e.g. 42)"
                            className="w-full border border-slate-300 dark:border-gray-600 dark:bg-gray-800 dark:text-white rounded-lg pl-4 pr-10 py-3 text-sm focus:outline-none focus:border-blue-500 focus:ring-2 focus:ring-blue-100"
                        />
                        <FaSearch className="absolute right-3 top-1/2 -translate-y-1/2 text-gray-400" />
                    </div>
                    <button
                        type="submit"
                        disabled={loading || !orderId.trim()}
                        className="bg-custom-blue hover:bg-blue-800 text-white font-semibold px-6 py-3 rounded-lg transition-colors disabled:opacity-50"
                    >
                        {loading ? "Searching..." : "Track"}
                    </button>
                </form>

                {loading && (
                    <div className="max-w-md mx-auto">
                        <Skeleton />
                    </div>
                )}

                {error && !loading && (
                    <div className="bg-red-50 dark:bg-red-900/20 border border-red-200 dark:border-red-800 rounded-lg p-6 text-center">
                        <FaBoxOpen className="text-4xl text-red-400 mx-auto mb-3" />
                        <p className="text-red-600 dark:text-red-400 font-medium">{error}</p>
                        <p className="text-gray-400 dark:text-gray-500 text-sm mt-1">
                            Make sure you entered the correct Order ID and that the order belongs to your account.
                        </p>
                    </div>
                )}

                {order && !loading && !error && (
                    <div className="bg-white dark:bg-gray-800 border border-gray-200 dark:border-gray-700 rounded-lg shadow-sm p-6">
                        <div className="flex items-center justify-between mb-4">
                            <div>
                                <p className="text-sm text-gray-500 dark:text-gray-400">Order #{order.orderId}</p>
                                <p className="text-sm text-gray-500 dark:text-gray-400">{order.orderDate}</p>
                            </div>
                            <span
                                className={`px-4 py-2 rounded-full text-sm font-semibold ${
                                    order.orderStatus === "Delivered"
                                        ? "bg-green-100 text-green-700 dark:bg-green-900/30 dark:text-green-400"
                                        : order.orderStatus === "Cancelled"
                                        ? "bg-red-100 text-red-600 dark:bg-red-900/30 dark:text-red-400"
                                        : "bg-blue-100 text-blue-700 dark:bg-blue-900/30 dark:text-blue-400"
                                }`}
                            >
                                {order.orderStatus}
                            </span>
                        </div>

                        {/* Mini timeline */}
                        {order.orderStatus !== "Cancelled" ? (
                            <div className="relative mb-4">
                                <div className="flex justify-between">
                                    {[
                                        { label: "Placed", icon: "📋" },
                                        { label: "Packed", icon: "📦" },
                                        { label: "Shipped", icon: "🚚" },
                                        { label: "Delivered", icon: "✅" },
                                    ].map((step, index) => {
                                        const steps = ["Placed", "Packed", "Shipped", "Delivered"];
                                        const currentIndex = steps.indexOf(order.orderStatus);
                                        const isCompleted = index <= currentIndex;
                                        return (
                                            <div key={step.label} className="flex flex-col items-center flex-1 relative z-10">
                                                <div
                                                    className={`w-10 h-10 rounded-full flex items-center justify-center text-lg ${
                                                        isCompleted
                                                            ? "bg-blue-500 text-white shadow-md"
                                                            : "bg-gray-100 dark:bg-gray-700 text-gray-400"
                                                    }`}
                                                >
                                                    {step.icon}
                                                </div>
                                                <span
                                                    className={`mt-1 text-xs font-medium ${
                                                        isCompleted ? "text-slate-800 dark:text-white" : "text-gray-400"
                                                    }`}
                                                >
                                                    {step.label}
                                                </span>
                                            </div>
                                        );
                                    })}
                                </div>
                                <div className="absolute top-5 left-0 right-0 h-1 bg-gray-200 dark:bg-gray-700 -z-0 mx-10">
                                    <div
                                        className="h-full bg-gradient-to-r from-blue-500 to-green-500 transition-all duration-700"
                                        style={{
                                            width: `${steps.indexOf(order.orderStatus) >= 0 ? (steps.indexOf(order.orderStatus) / 3) * 100 : 0}%`,
                                        }}
                                    />
                                </div>
                            </div>
                        ) : (
                            <div className="text-center py-4 text-red-500 dark:text-red-400 font-medium">
                                ❌ This order was cancelled
                            </div>
                        )}

                        <div className="border-t dark:border-gray-700 pt-4 mt-4">
                            <h3 className="font-semibold text-slate-800 dark:text-white mb-2">Items</h3>
                            <div className="space-y-2">
                                {order.items?.map((item) => (
                                    <div key={item.orderItemId} className="flex items-center gap-3">
                                        <img
                                            src={`${import.meta.env.VITE_BACK_END_URL || "http://localhost:8080"}/images/${item.product?.image}`}
                                            alt={item.product?.productName}
                                            className="w-10 h-10 rounded object-cover"
                                        />
                                        <div className="flex-1">
                                            <p className="text-sm font-medium text-slate-700 dark:text-gray-200">
                                                {item.product?.productName}
                                            </p>
                                            <p className="text-xs text-gray-500 dark:text-gray-400">
                                                Qty: {item.quantity} × ${item.orderedProductPrice}
                                            </p>
                                        </div>
                                    </div>
                                ))}
                            </div>
                        </div>

                        <div className="border-t dark:border-gray-700 pt-3 mt-3 flex justify-between items-center">
                            <span className="font-semibold text-slate-800 dark:text-white">Total</span>
                            <span className="text-lg font-bold text-slate-900 dark:text-white">
                                ${order.totalAmount?.toFixed(2)}
                            </span>
                        </div>
                    </div>
                )}

                {!order && !loading && !error && (
                    <div className="bg-gray-50 dark:bg-gray-800 border border-gray-200 dark:border-gray-700 rounded-lg p-8 text-center">
                        <FaSearch className="text-4xl text-gray-300 dark:text-gray-600 mx-auto mb-3" />
                        <p className="text-gray-400 dark:text-gray-500">
                            Enter an order ID above to track your order status.
                        </p>
                    </div>
                )}
            </div>

            <OrderTrackingModal open={open} setOpen={setOpen} orderId={searchId} />
        </div>
    );
};

export default TrackOrder;
