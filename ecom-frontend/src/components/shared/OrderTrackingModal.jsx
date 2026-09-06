import React, { useState, useEffect } from "react";
import { FaBox, FaCheckCircle, FaClipboardList, FaShippingFast, FaTimesCircle, FaTruck } from "react-icons/fa";
import { Dialog, DialogBackdrop, DialogPanel, DialogTitle } from "@headlessui/react";
import { RxCross1 } from "react-icons/rx";
import { formatPrice } from "../../utils/formatPrice";
import Skeleton from "../shared/Skeleton";
import api from "../../api/api";

const TRACKING_STEPS = [
    { key: "Placed", label: "Order Placed", icon: FaClipboardList, color: "bg-blue-500" },
    { key: "Packed", label: "Packed", icon: FaBox, color: "bg-purple-500" },
    { key: "Shipped", label: "Shipped", icon: FaTruck, color: "bg-orange-500" },
    { key: "Delivered", label: "Delivered", icon: FaCheckCircle, color: "bg-green-500" },
];

const OrderTrackingModal = ({ open, setOpen, orderId }) => {
    const [order, setOrder] = useState(null);
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState("");

    useEffect(() => {
        if (open && orderId) {
            setLoading(true);
            setError("");
            api.get(`/orders/track/${orderId}`)
                .then(({ data }) => setOrder(data))
                .catch((err) => setError(err?.response?.data?.message || "Failed to load order"))
                .finally(() => setLoading(false));
        }
    }, [open, orderId]);

    const currentStepIndex = order
        ? TRACKING_STEPS.findIndex((s) => s.key === order.orderStatus)
        : -1;
    const isCancelled = order?.orderStatus === "Cancelled";

    return (
        <Dialog open={open} onClose={() => setOpen(false)} className="relative z-50">
            <DialogBackdrop className="fixed inset-0 bg-gray-500/75 transition-opacity" />
            <div className="fixed inset-0 overflow-y-auto">
                <div className="flex min-h-full items-center justify-center p-4">
                    <DialogPanel className="w-full max-w-2xl rounded-2xl bg-white dark:bg-gray-800 shadow-xl transition-all">
                        <div className="flex items-center justify-between px-6 py-4 border-b">
                            <DialogTitle className="text-lg font-bold text-slate-800 dark:text-white">
                                Order Tracking #{orderId}
                            </DialogTitle>
                            <button onClick={() => setOpen(false)} className="text-slate-500 dark:text-gray-400 hover:text-slate-700 dark:hover:text-gray-200">
                                <RxCross1 className="text-xl" />
                            </button>
                        </div>

                        <div className="p-6">
                            {loading ? (
                                <div className="py-8"><Skeleton /></div>
                            ) : error ? (
                                <div className="text-center py-8 text-red-500">{error}</div>
                            ) : order ? (
                                <>
                                    {/* Status badge */}
                                    <div className="flex items-center justify-between mb-6">
                                        <div>
                                            <p className="text-sm text-gray-500 dark:text-gray-400">Order Date</p>
                                            <p className="font-semibold text-slate-800 dark:text-white">{order.orderDate}</p>
                                        </div>
                                        <span
                                            className={`px-4 py-2 rounded-full text-sm font-semibold ${
                                                isCancelled
                                                    ? "bg-red-100 text-red-600"
                                                    : order.orderStatus === "Delivered"
                                                    ? "bg-green-100 text-green-700"
                                                    : "bg-blue-100 text-blue-700"
                                            }`}
                                        >
                                            {isCancelled ? "Cancelled" : order.orderStatus}
                                        </span>
                                    </div>

                                    {/* Timeline */}
                                    {!isCancelled ? (
                                        <div className="relative mb-8">
                                            <div className="flex justify-between">
                                                {TRACKING_STEPS.map((step, index) => {
                                                    const isCompleted = index <= currentStepIndex;
                                                    const isCurrent = index === currentStepIndex;
                                                    const Icon = step.icon;
                                                    return (
                                                        <div key={step.key} className="flex flex-col items-center relative z-10 flex-1">
                                                            <div
                                                                className={`w-12 h-12 rounded-full flex items-center justify-center transition-all duration-500 ${
                                                                    isCompleted
                                                                        ? `${step.color} text-white shadow-lg`
                                                                        : "bg-gray-100 dark:bg-gray-700 text-gray-400 dark:text-gray-500"
                                                                } ${isCurrent ? "ring-4 ring-offset-2 ring-blue-200 scale-110" : ""}`}
                                                            >
                                                                <Icon className="text-lg" />
                                                            </div>
                                                            <span
                                                                className={`mt-2 text-xs font-medium text-center ${
                                                                    isCompleted ? "text-slate-800 dark:text-white" : "text-gray-400 dark:text-gray-500"
                                                                }`}
                                                            >
                                                                {step.label}
                                                            </span>
                                                        </div>
                                                    );
                                                })}
                                            </div>
                                            {/* Progress line */}
                                            <div className="absolute top-6 left-0 right-0 h-1 bg-gray-200 dark:bg-gray-700 -z-0 mx-12">
                                                <div
                                                    className="h-full bg-gradient-to-r from-blue-500 via-purple-500 to-green-500 transition-all duration-700"
                                                    style={{
                                                        width: `${currentStepIndex >= 0 ? (currentStepIndex / (TRACKING_STEPS.length - 1)) * 100 : 0}%`,
                                                    }}
                                                />
                                            </div>
                                        </div>
                                    ) : (
                                        <div className="flex flex-col items-center justify-center py-8 text-red-500">
                                            <FaTimesCircle size={48} className="mb-3" />
                                            <p className="text-lg font-semibold">This order was cancelled</p>
                                        </div>
                                    )}

                                    {/* Order items */}
                                    <div className="border-t pt-4 mt-4">
                                        <h3 className="font-semibold text-slate-800 dark:text-white mb-3">Items</h3>
                                        <div className="space-y-2">
                                            {order.items?.map((item) => (
                                                <div key={item.orderItemId} className="flex items-center gap-3">
                                                    <img
                                                        src={`${import.meta.env.VITE_BACK_END_URL || "http://localhost:8080"}/images/${item.product?.image}`}
                                                        alt={item.product?.productName}
                                                        className="w-12 h-12 rounded object-cover"
                                                    />
                                                    <div className="flex-1">
                                                        <p className="text-sm font-medium text-slate-700 dark:text-gray-200">
                                                            {item.product?.productName}
                                                        </p>
                                                        <p className="text-xs text-gray-500 dark:text-gray-400">
                                                            Qty: {item.quantity} x ${item.orderedProductPrice}
                                                        </p>
                                                    </div>
                                                </div>
                                            ))}
                                        </div>
                                    </div>

                                    {/* Totals */}
                                    <div className="border-t pt-4 mt-4 space-y-1 text-sm text-gray-600 dark:text-gray-300">
                                        {order.discountAmount > 0 && (
                                            <div className="flex justify-between">
                                                <span>Discount</span>
                                                <span>-{formatPrice(order.discountAmount)}</span>
                                            </div>
                                        )}
                                        <div className="flex justify-between">
                                            <span>Shipping</span>
                                            <span>{formatPrice(order.shippingCost || 0)}</span>
                                        </div>
                                        {order.taxAmount > 0 && (
                                            <div className="flex justify-between">
                                                <span>VAT</span>
                                                <span>{formatPrice(order.taxAmount)}</span>
                                            </div>
                                        )}
                                        <div className="flex justify-between items-center pt-1">
                                            <span className="font-semibold text-slate-800 dark:text-white">Total Amount</span>
                                            <span className="text-xl font-bold text-slate-900 dark:text-white">
                                                {formatPrice(order.totalAmount)}
                                            </span>
                                        </div>
                                    </div>
                                </>
                            ) : null}
                        </div>
                    </DialogPanel>
                </div>
            </div>
        </Dialog>
    );
};

export default OrderTrackingModal;
