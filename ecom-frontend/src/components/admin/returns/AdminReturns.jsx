import { useEffect, useState } from "react";
import api from "../../../api/api";
import toast from "react-hot-toast";
import { FaCheck, FaTimes, FaDollarSign, FaTruck, FaSync } from "react-icons/fa";

const AdminReturns = () => {
    const [returns, setReturns] = useState([]);
    const [loading, setLoading] = useState(true);
    const [page, setPage] = useState(0);
    const [totalPages, setTotalPages] = useState(0);

    const fetchReturns = async (p = 0) => {
        setLoading(true);
        try {
            const { data } = await api.get(`/admin/returns?page=${p}&size=10`);
            setReturns(data.content);
            setTotalPages(data.totalPages);
            setPage(p);
        } catch {
            toast.error("Failed to load return requests");
        } finally {
            setLoading(false);
        }
    };

    useEffect(() => {
        fetchReturns();
    }, []);

    const handleApprove = async (returnId) => {
        try {
            await api.put(`/admin/returns/${returnId}/approve`, { adminNote: "Approved by admin" });
            toast.success("Return approved");
            fetchReturns(page);
        } catch (error) {
            const msg = error?.response?.data?.message || "Failed to approve return";
            toast.error(msg);
        }
    };

    const handleReject = async (returnId) => {
        try {
            await api.put(`/admin/returns/${returnId}/reject`, { adminNote: "Rejected by admin" });
            toast.success("Return rejected");
            fetchReturns(page);
        } catch (error) {
            const msg = error?.response?.data?.message || "Failed to reject return";
            toast.error(msg);
        }
    };

    const handleRefund = async (returnId) => {
        try {
            await api.put(`/admin/returns/${returnId}/refund`);
            toast.success("Marked as refunded");
            fetchReturns(page);
        } catch (error) {
            const msg = error?.response?.data?.message || "Failed to mark as refunded";
            toast.error(msg);
        }
    };

    const handleRefreshTracking = async (returnId) => {
        try {
            const { data } = await api.post(`/admin/returns/${returnId}/track`);
            toast.success(`Tracking updated: ${data.trackingStatus || data.status}`);
            fetchReturns(page);
        } catch (error) {
            const msg = error?.response?.data?.message || "Failed to refresh tracking";
            toast.error(msg);
        }
    };

    const statusColors = {
        "REQUESTED": "bg-yellow-100 text-yellow-700",
        "APPROVED": "bg-blue-100 text-blue-700",
        "SHIPPED_BACK": "bg-indigo-100 text-indigo-700",
        "REFUNDED": "bg-green-100 text-green-700",
        "REJECTED": "bg-red-100 text-red-700",
    };

    return (
        <div className="pb-6 pt-20 px-4 max-w-7xl mx-auto">
            <div className="mb-6">
                <h1 className="text-3xl font-bold text-gray-800 dark:text-white">Return Requests</h1>
                <p className="text-gray-500 text-sm">Manage customer return and refund requests.</p>
            </div>

            {loading ? (
                <div className="flex justify-center py-20">
                    <span className="text-gray-500 dark:text-gray-400">Loading...</span>
                </div>
            ) : returns.length === 0 ? (
                <div className="flex flex-col items-center justify-center text-gray-600 dark:text-gray-400 py-20 bg-white dark:bg-gray-800 rounded-lg shadow-sm border border-gray-100 dark:border-gray-700">
                    <h2 className="text-gray-700 dark:text-gray-300 font-semibold">No return requests</h2>
                    <p className="text-gray-400 dark:text-gray-700 font-semibold">Return requests will appear here when customers submit them.</p>
                </div>
            ) : (
                <>
                    <div className="overflow-x-auto bg-white dark:bg-gray-800 rounded-lg shadow-sm border border-gray-100 dark:border-gray-700">
                        <table className="w-full text-sm">
                            <thead className="bg-gray-50 dark:bg-gray-900 border-b border-gray-200 dark:border-gray-700">
                                <tr>
                                    <th className="px-4 py-3 text-left font-semibold text-gray-700 dark:text-gray-300">ID</th>
                                    <th className="px-4 py-3 text-left font-semibold text-gray-700 dark:text-gray-300">Order ID</th>
                                    <th className="px-4 py-3 text-left font-semibold text-gray-700 dark:text-gray-300">Customer</th>
                                    <th className="px-4 py-3 text-left font-semibold text-gray-700 dark:text-gray-300">Reason</th>
                                    <th className="px-4 py-3 text-left font-semibold text-gray-700 dark:text-gray-300">Refund</th>
                                    <th className="px-4 py-3 text-left font-semibold text-gray-700 dark:text-gray-300">Status</th>
                                    <th className="px-4 py-3 text-left font-semibold text-gray-700 dark:text-gray-300">Carrier / Tracking</th>
                                    <th className="px-4 py-3 text-left font-semibold text-gray-700 dark:text-gray-300">Tracking Status</th>
                                    <th className="px-4 py-3 text-left font-semibold text-gray-700 dark:text-gray-300">Date</th>
                                    <th className="px-4 py-3 text-center font-semibold text-gray-700 dark:text-gray-300">Actions</th>
                                </tr>
                            </thead>
                            <tbody className="divide-y divide-gray-100 dark:divide-gray-700">
                                {returns.map((r) => (
                                    <tr key={r.id} className="hover:bg-gray-50 dark:hover:bg-gray-700/50">
                                        <td className="px-4 py-3 text-gray-600 dark:text-gray-400">#{r.id}</td>
                                        <td className="px-4 py-3 text-gray-600 dark:text-gray-400">#{r.orderId}</td>
                                        <td className="px-4 py-3 text-gray-600 dark:text-gray-400">{r.userEmail}</td>
                                        <td className="px-4 py-3 text-gray-600 dark:text-gray-400 max-w-xs truncate">{r.reason}</td>
                                        <td className="px-4 py-3 text-gray-600 dark:text-gray-400">${r.refundAmount?.toFixed(2)}</td>
                                        <td className="px-4 py-3">
                                            <span className={`px-2 py-1 rounded-full text-xs font-semibold ${statusColors[r.status] || "bg-gray-100 dark:bg-gray-700 text-gray-600 dark:text-gray-400"}`}>
                                                {r.status}
                                            </span>
                                        </td>
                                        <td className="px-4 py-3 text-gray-600 dark:text-gray-400 text-xs">
                                            {r.carrierName ? (
                                                <>
                                                    <div>{r.carrierName}</div>
                                                    <div className="text-gray-400">{r.trackingNumber}</div>
                                                </>
                                            ) : (
                                                <span className="text-gray-400">—</span>
                                            )}
                                        </td>
                                        <td className="px-4 py-3">
                                            {r.trackingStatus ? (
                                                <span className={`px-2 py-1 rounded-full text-xs font-semibold ${
                                                    r.trackingStatus === "DELIVERED"
                                                        ? "bg-green-100 text-green-700"
                                                        : r.trackingStatus === "EXCEPTION"
                                                        ? "bg-red-100 text-red-700"
                                                        : "bg-blue-100 text-blue-700"
                                                }`}>
                                                    {r.trackingStatus}
                                                </span>
                                            ) : (
                                                <span className="text-gray-400 text-xs">—</span>
                                            )}
                                        </td>
                                        <td className="px-4 py-3 text-gray-500 dark:text-gray-400 text-xs">
                                            {r.requestedAt ? new Date(r.requestedAt).toLocaleDateString() : "—"}
                                        </td>
                                        <td className="px-4 py-3">
                                            <div className="flex justify-center gap-1 flex-wrap">
                                                {r.status === "REQUESTED" && (
                                                    <>
                                                        <button
                                                            onClick={() => handleApprove(r.id)}
                                                            className="flex items-center bg-green-500 hover:bg-green-600 text-white px-2 h-8 rounded-md text-xs"
                                                        >
                                                            <FaCheck className="mr-1" />
                                                            Approve
                                                        </button>
                                                        <button
                                                            onClick={() => handleReject(r.id)}
                                                            className="flex items-center bg-red-500 hover:bg-red-600 text-white px-2 h-8 rounded-md text-xs"
                                                        >
                                                            <FaTimes className="mr-1" />
                                                            Reject
                                                        </button>
                                                    </>
                                                )}
                                                {(r.status === "APPROVED" || r.status === "SHIPPED_BACK") && (
                                                    <>
                                                        <button
                                                            onClick={() => handleRefreshTracking(r.id)}
                                                            className="flex items-center bg-indigo-500 hover:bg-indigo-600 text-white px-2 h-8 rounded-md text-xs"
                                                            title="Refresh tracking"
                                                        >
                                                            <FaSync className="mr-1" />
                                                            Track
                                                        </button>
                                                        {r.status === "SHIPPED_BACK" && (
                                                            <button
                                                                onClick={() => handleRefund(r.id)}
                                                                className="flex items-center bg-blue-500 hover:bg-blue-600 text-white px-2 h-8 rounded-md text-xs"
                                                            >
                                                                <FaDollarSign className="mr-1" />
                                                                Refund
                                                            </button>
                                                        )}
                                                    </>
                                                )}
                                                {(r.status === "REJECTED" || r.status === "REFUNDED") && (
                                                    <span className="text-xs text-gray-400">—</span>
                                                )}
                                            </div>
                                        </td>
                                    </tr>
                                ))}
                            </tbody>
                        </table>
                    </div>

                    {totalPages > 1 && (
                        <div className="flex justify-center gap-2 mt-4">
                            <button
                                onClick={() => fetchReturns(page - 1)}
                                disabled={page === 0}
                                className="px-3 py-1 bg-gray-200 rounded text-sm disabled:opacity-50"
                            >
                                Prev
                            </button>
                            <span className="px-3 py-1 text-sm text-gray-600 dark:text-gray-400">
                                Page {page + 1} of {totalPages}
                            </span>
                            <button
                                onClick={() => fetchReturns(page + 1)}
                                disabled={page >= totalPages - 1}
                                className="px-3 py-1 bg-gray-200 rounded text-sm disabled:opacity-50"
                            >
                                Next
                            </button>
                        </div>
                    )}
                </>
            )}
        </div>
    );
};

export default AdminReturns;
