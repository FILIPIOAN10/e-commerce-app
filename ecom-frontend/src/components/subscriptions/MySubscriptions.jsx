import { useEffect, useState } from "react";
import api from "../../api/api";
import toast from "react-hot-toast";
import Loader from "../shared/Loader";
import { FaBoxOpen, FaTimes, FaCheck, FaClock, FaBan } from "react-icons/fa";

const MySubscriptions = () => {
    const [subscriptions, setSubscriptions] = useState([]);
    const [loading, setLoading] = useState(true);

    const fetchSubscriptions = async () => {
        setLoading(true);
        try {
            const { data } = await api.get("/subscriptions/my");
            setSubscriptions(data);
        } catch {
            toast.error("Failed to load subscriptions");
        } finally {
            setLoading(false);
        }
    };

    useEffect(() => {
        fetchSubscriptions();
    }, []);

    const handleCancel = async (id) => {
        if (!confirm("Cancel this subscription?")) return;
        try {
            await api.post(`/subscriptions/${id}/cancel`);
            toast.success("Subscription canceled");
            fetchSubscriptions();
        } catch (error) {
            const msg = error?.response?.data?.message || "Failed to cancel subscription";
            toast.error(msg);
        }
    };

    const getStatusBadge = (status) => {
        switch (status) {
            case "ACTIVE":
                return <span className="bg-green-100 text-green-700 px-2 py-1 rounded-full text-xs font-semibold flex items-center gap-1"><FaCheck /> Active</span>;
            case "PENDING":
                return <span className="bg-yellow-100 text-yellow-700 px-2 py-1 rounded-full text-xs font-semibold flex items-center gap-1"><FaClock /> Pending</span>;
            case "CANCELED":
                return <span className="bg-red-100 text-red-700 px-2 py-1 rounded-full text-xs font-semibold flex items-center gap-1"><FaBan /> Canceled</span>;
            default:
                return <span className="bg-gray-100 text-gray-700 px-2 py-1 rounded-full text-xs font-semibold">{status}</span>;
        }
    };

    return (
        <div className="min-h-screen dark:bg-gray-950 dark:text-white py-6 px-4 sm:px-8 lg:px-14">
            <h1 className="text-3xl font-bold text-slate-800 dark:text-white mb-6 text-center">My Subscriptions</h1>

            {loading ? (
                <div className="flex justify-center py-20">
                    <Loader />
                </div>
            ) : subscriptions.length === 0 ? (
                <div className="flex flex-col items-center justify-center text-gray-600 dark:text-gray-400 py-20">
                    <FaBoxOpen className="text-5xl mb-4" />
                    <h2 className="text-xl font-semibold">No active subscriptions</h2>
                </div>
            ) : (
                <div className="max-w-4xl mx-auto space-y-4">
                    {subscriptions.map((sub) => (
                        <div
                            key={sub.id}
                            className="bg-white dark:bg-gray-800 rounded-xl shadow-sm border border-gray-100 dark:border-gray-700 p-5"
                        >
                            <div className="flex justify-between items-start mb-2">
                                <div>
                                    <h3 className="text-lg font-bold text-gray-800 dark:text-white">
                                        {sub.plan?.name}
                                    </h3>
                                    <p className="text-sm text-gray-500 dark:text-gray-400">
                                        {sub.plan?.description}
                                    </p>
                                </div>
                                {getStatusBadge(sub.status)}
                            </div>

                            <div className="text-sm text-gray-600 dark:text-gray-300 space-y-1 mb-4">
                                <p>
                                    <span className="font-semibold">Amount:</span>{" "}
                                    ${sub.plan?.amount?.toFixed(2)} / {sub.plan?.interval}
                                </p>
                                {sub.currentPeriodStart && sub.currentPeriodEnd && (
                                    <p>
                                        <span className="font-semibold">Period:</span>{" "}
                                        {new Date(sub.currentPeriodStart).toLocaleDateString()} -{" "}
                                        {new Date(sub.currentPeriodEnd).toLocaleDateString()}
                                    </p>
                                )}
                                {sub.canceledAt && (
                                    <p className="text-red-500">Canceled on {new Date(sub.canceledAt).toLocaleDateString()}</p>
                                )}
                            </div>

                            {sub.status === "ACTIVE" && (
                                <button
                                    onClick={() => handleCancel(sub.id)}
                                    className="flex items-center gap-1 text-sm text-red-600 hover:text-red-800 dark:hover:text-red-400 font-semibold"
                                >
                                    <FaTimes /> Cancel
                                </button>
                            )}
                        </div>
                    ))}
                </div>
            )}
        </div>
    );
};

export default MySubscriptions;
