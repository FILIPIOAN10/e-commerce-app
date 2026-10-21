import { Fragment, useEffect, useState } from "react";
import api from "../../../api/api";
import toast from "react-hot-toast";
import { FaCheck, FaComments, FaSync, FaTimes } from "react-icons/fa";

const REFRESH_MS = 30000;

// Refunds the AI assistant paused on. Approving resumes the agent: it files the
// return in the shop (it then shows in Returns as APPROVED, badge "AI") and tells
// the customer in their conversation. No money moves here — the refund is paid
// from Returns once the parcel is back, exactly like a customer-filed return.
const AdminSupportApprovals = () => {
    const [rows, setRows] = useState([]);
    const [loading, setLoading] = useState(true);
    const [notes, setNotes] = useState({});
    const [busy, setBusy] = useState(null);
    const [openTranscript, setOpenTranscript] = useState(null);
    const [transcript, setTranscript] = useState([]);

    const fetchPending = () =>
        api.get("/admin/support/approvals")
            .then(({ data }) => setRows(data))
            .catch((error) => toast.error(error?.response?.data?.message || "Failed to load the AI refund queue"))
            .finally(() => setLoading(false));

    useEffect(() => {
        fetchPending();
        const timer = setInterval(fetchPending, REFRESH_MS);
        return () => clearInterval(timer);
    }, []);

    const toggleTranscript = async (conversationId) => {
        if (openTranscript === conversationId) {
            setOpenTranscript(null);
            return;
        }
        try {
            const { data } = await api.get(`/admin/support/conversations/${conversationId}`);
            setTranscript(data.messages);
            setOpenTranscript(conversationId);
        } catch (error) {
            toast.error(error?.response?.data?.message || "Failed to load the conversation");
        }
    };

    const decide = async (conversationId, decision) => {
        setBusy(conversationId);
        try {
            const { data } = await api.post(`/admin/support/approvals/${conversationId}`, {
                decision,
                note: notes[conversationId] || null,
            });
            toast.success(decision === "approve" ? "Approved. The agent told the customer:" : "Rejected.");
            if (decision === "approve") toast(data.reply, { duration: 8000 });
            fetchPending();
        } catch (error) {
            toast.error(error?.response?.data?.message || "The decision could not be applied");
        } finally {
            setBusy(null);
        }
    };

    const money = (amount, currency) =>
        amount == null ? "—" : `${Number(amount).toFixed(2)} ${currency || ""}`.trim();

    return (
        <div className="pb-6 pt-20 px-4 max-w-7xl mx-auto">
            <div className="mb-6 flex items-start justify-between">
                <div>
                    <h1 className="text-3xl font-bold text-gray-800 dark:text-white">AI Refund Requests</h1>
                    <p className="text-gray-500 text-sm">
                        Refunds the support assistant prepared. Nothing is filed until you approve.
                    </p>
                </div>
                <button type="button" onClick={fetchPending} title="Refresh"
                        className="p-2 rounded text-gray-500 hover:bg-gray-100 dark:hover:bg-gray-800">
                    <FaSync />
                </button>
            </div>

            {loading ? (
                <div className="flex justify-center py-20">
                    <span className="text-gray-500 dark:text-gray-400">Loading...</span>
                </div>
            ) : rows.length === 0 ? (
                <div className="flex flex-col items-center justify-center py-20 bg-white dark:bg-gray-800 rounded-lg shadow-sm border border-gray-100 dark:border-gray-700">
                    <h2 className="text-gray-700 dark:text-gray-300 font-semibold">Nothing waiting</h2>
                    <p className="text-gray-400 text-sm">Refunds the assistant prepares will appear here.</p>
                </div>
            ) : (
                <div className="overflow-x-auto bg-white dark:bg-gray-800 rounded-lg shadow-sm border border-gray-100 dark:border-gray-700">
                    <table className="w-full text-sm">
                        <thead className="bg-gray-50 dark:bg-gray-900 border-b border-gray-200 dark:border-gray-700">
                            <tr>
                                {["Requested", "Customer", "Order", "Order status", "Amount", "Reason", "Note to customer", "Actions"].map((h) => (
                                    <th key={h} className="px-4 py-3 text-left font-semibold text-gray-700 dark:text-gray-300">{h}</th>
                                ))}
                            </tr>
                        </thead>
                        <tbody className="divide-y divide-gray-100 dark:divide-gray-700">
                            {rows.map((r) => (
                                <Fragment key={r.conversationId}>
                                    <tr className="hover:bg-gray-50 dark:hover:bg-gray-700/50 align-top">
                                        <td className="px-4 py-3 text-gray-600 dark:text-gray-400 whitespace-nowrap">
                                            {r.requestedAt ? new Date(r.requestedAt).toLocaleString() : "—"}
                                        </td>
                                        <td className="px-4 py-3 text-gray-600 dark:text-gray-400">
                                            <div>{r.customerName || "—"}</div>
                                            <div className="text-xs text-gray-400">{r.customerEmail}</div>
                                        </td>
                                        <td className="px-4 py-3 text-gray-600 dark:text-gray-400">#{r.orderId}</td>
                                        <td className="px-4 py-3 text-gray-600 dark:text-gray-400">{r.orderStatus}</td>
                                        <td className="px-4 py-3 text-gray-600 dark:text-gray-400 whitespace-nowrap">{money(r.orderTotal, r.currency)}</td>
                                        <td className="px-4 py-3 text-gray-600 dark:text-gray-400 max-w-xs">{r.reason}</td>
                                        <td className="px-4 py-3">
                                            <input
                                                aria-label={`Note for order ${r.orderId}`}
                                                maxLength={500}
                                                value={notes[r.conversationId] || ""}
                                                onChange={(e) => setNotes((n) => ({ ...n, [r.conversationId]: e.target.value }))}
                                                placeholder="Optional (shown on reject)"
                                                className="w-48 rounded border border-gray-300 dark:border-gray-600 bg-white dark:bg-gray-900 px-2 py-1 text-xs"
                                            />
                                        </td>
                                        <td className="px-4 py-3">
                                            <div className="flex items-center gap-2">
                                                <button type="button" onClick={() => toggleTranscript(r.conversationId)} title="Conversation"
                                                        className="p-2 rounded bg-gray-100 dark:bg-gray-700 text-gray-700 dark:text-gray-200">
                                                    <FaComments />
                                                </button>
                                                <button type="button" disabled={busy === r.conversationId} onClick={() => decide(r.conversationId, "approve")}
                                                        title="Approve" className="p-2 rounded bg-green-100 text-green-700 disabled:opacity-50">
                                                    <FaCheck />
                                                </button>
                                                <button type="button" disabled={busy === r.conversationId} onClick={() => decide(r.conversationId, "reject")}
                                                        title="Reject" className="p-2 rounded bg-red-100 text-red-700 disabled:opacity-50">
                                                    <FaTimes />
                                                </button>
                                            </div>
                                        </td>
                                    </tr>
                                    {openTranscript === r.conversationId && (
                                        <tr>
                                            <td colSpan={8} className="px-6 py-4 bg-gray-50 dark:bg-gray-900">
                                                <ol className="space-y-1">
                                                    {transcript.map((m, i) => (
                                                        <li key={i} className="text-sm">
                                                            <span className="font-semibold text-gray-700 dark:text-gray-300">
                                                                {m.role === "customer" ? "Customer" : "Assistant"}:
                                                            </span>{" "}
                                                            <span className="text-gray-600 dark:text-gray-400 whitespace-pre-wrap">{m.content}</span>
                                                        </li>
                                                    ))}
                                                </ol>
                                            </td>
                                        </tr>
                                    )}
                                </Fragment>
                            ))}
                        </tbody>
                    </table>
                </div>
            )}
        </div>
    );
};

export default AdminSupportApprovals;
