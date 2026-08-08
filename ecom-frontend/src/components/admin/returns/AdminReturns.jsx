import { useEffect, useState } from "react";
import api from "../../../api/api";
import toast from "react-hot-toast";
import { FaCheck, FaTimes, FaDollarSign } from "react-icons/fa";

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
        } catch (error) {
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

    const statusColors = {
        "PENDING": "bg-yellow-100 text-yellow-700",
        "APPROVED": "bg-blue-100 text-blue-700",
        "REJECTED": "bg-red-100 text-red-700",
        "REFUNDED": "bg-green-100 text-green-700",
    };

    return (
        <div className="pb-6 pt-20 px-4 max-w-7xl mx-auto">
            <div className="mb-6">
                <h1 className="text-3xl font-bold text-gray-800 dark:text-white">Return Requests</h1>
                <p className="text-gray-500 text-sm">Manage customer return and refund requests.</p>
            </div>

            {loading ? (
                <div className="flex justify-center py-20">
                    <span className="text-gray-500">Loading...</span>
                </div>
            ) : returns.length === 0 ? (
                <div className="flex flex-col items-center justify-center text-gray-600 py-20 bg-white rounded-lg shadow-sm border border-gray-100">
                    <h2 className="text-2xl font-semibold">No return requests</h2>
                    <p className="text-gray-400 text-sm mt-1">Return requests will appear here when customers submit them.</p>
                </div>
            ) : (
                <>
                    <div className="overflow-x-auto bg-white rounded-lg shadow-sm border border-gray-100">
                        <table className="w-full text-sm">
                            <thead className="bg-gray-50 border-b border-gray-200">
                                <tr>
                                    <th className="px-4 py-3 text-left font-semibold text-gray-700">ID</th>
                                    <th className="px-4 py-3 text-left font-semibold text-gray-700">Order ID</th>
                                    <th className="px-4 py-3 text-left font-semibold text-gray-700">Customer</th>
                                    <th className="px-4 py-3 text-left font-semibold text-gray-700">Reason</th>
                                    <th className="px-4 py-3 text-left font-semibold text-gray-700">Refund</th>
                                    <th className="px-4 py-3 text-left font-semibold text-gray-700">Status</th>
                                    <th className="px-4 py-3 text-left font-semibold text-gray-700">Date</th>
                                    <th className="px-4 py-3 text-center font-semibold text-gray-700">Actions</th>
                                </tr>
                            </thead>
                            <tbody className="divide-y divide-gray-100">
                                {returns.map((r) => (
                                    <tr key={r.id} className="hover:bg-gray-50">
                                        <td className="px-4 py-3 text-gray-600">#{r.id}</td>
                                        <td className="px-4 py-3 text-gray-600">#{r.orderId}</td>
                                        <td className="px-4 py-3 text-gray-600">{r.userEmail}</td>
                                        <td className="px-4 py-3 text-gray-600 max-w-xs truncate">{r.reason}</td>
                                        <td className="px-4 py-3 text-gray-600">${r.refundAmount?.toFixed(2)}</td>
                                        <td className="px-4 py-3">
                                            <span className={`px-2 py-1 rounded-full text-xs font-semibold ${statusColors[r.status] || "bg-gray-100 text-gray-600"}`}>
                                                {r.status}
                                            </span>
                                        </td>
                                        <td className="px-4 py-3 text-gray-500 text-xs">
                                            {r.requestedAt ? new Date(r.requestedAt).toLocaleDateString() : "—"}
                                        </td>
                                        <td className="px-4 py-3">
                                            <div className="flex justify-center gap-1">
                                                {r.status === "PENDING" && (
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
                                                {r.status === "APPROVED" && (
                                                    <button
                                                        onClick={() => handleRefund(r.id)}
                                                        className="flex items-center bg-blue-500 hover:bg-blue-600 text-white px-2 h-8 rounded-md text-xs"
                                                    >
                                                        <FaDollarSign className="mr-1" />
                                                        Mark Refunded
                                                    </button>
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
                            <span className="px-3 py-1 text-sm text-gray-600">
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
