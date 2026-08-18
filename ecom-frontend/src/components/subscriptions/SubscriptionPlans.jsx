import { useEffect, useState } from "react";
import api from "../../api/api";
import toast from "react-hot-toast";
import Loader from "../shared/Loader";
import { FaBoxOpen, FaSync, FaCalendarAlt } from "react-icons/fa";
import { useSelector } from "react-redux";

const SubscriptionPlans = () => {
    const [plans, setPlans] = useState([]);
    const [loading, setLoading] = useState(true);
    const [subscribingId, setSubscribingId] = useState(null);
    const { user } = useSelector((state) => state.auth);

    const fetchPlans = async () => {
        setLoading(true);
        try {
            const { data } = await api.get("/public/subscriptions/plans");
            setPlans(data);
        } catch {
            toast.error("Failed to load subscription plans");
        } finally {
            setLoading(false);
        }
    };

    useEffect(() => {
        fetchPlans();
    }, []);

    const handleSubscribe = async (planId) => {
        if (!user?.id) {
            toast.error("Please log in to subscribe");
            return;
        }
        setSubscribingId(planId);
        try {
            const { data } = await api.post(`/subscriptions/plans/${planId}/checkout`);
            if (data.checkoutUrl) {
                window.location.href = data.checkoutUrl;
            } else {
                toast.error("No checkout URL received");
            }
        } catch (error) {
            const msg = error?.response?.data?.message || "Failed to start subscription";
            toast.error(msg);
        } finally {
            setSubscribingId(null);
        }
    };

    return (
        <div className="min-h-screen dark:bg-gray-950 dark:text-white py-6 px-4 sm:px-8 lg:px-14">
            <div className="mb-8 text-center">
                <h1 className="text-3xl font-bold text-slate-800 dark:text-white mb-2">Subscriptions</h1>
                <p className="text-gray-500 dark:text-gray-400">Subscribe to your favorite consumable products and save.</p>
            </div>

            {loading ? (
                <div className="flex justify-center py-20">
                    <Loader />
                </div>
            ) : plans.length === 0 ? (
                <div className="flex flex-col items-center justify-center text-gray-600 dark:text-gray-400 py-20">
                    <FaBoxOpen className="text-5xl mb-4" />
                    <h2 className="text-xl font-semibold">No plans available</h2>
                    <p className="text-sm">Check back later for subscription offers.</p>
                </div>
            ) : (
                <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-6">
                    {plans.map((plan) => (
                        <div
                            key={plan.planId}
                            className="bg-white dark:bg-gray-800 rounded-xl shadow-md border border-gray-100 dark:border-gray-700 p-5 hover:shadow-lg transition"
                        >
                            <h3 className="text-xl font-bold text-gray-800 dark:text-white mb-1">{plan.name}</h3>
                            <p className="text-sm text-gray-500 dark:text-gray-400 mb-4">{plan.description}</p>

                            <div className="flex items-baseline gap-2 mb-4">
                                <span className="text-3xl font-bold text-green-600 dark:text-green-400">
                                    ${plan.amount?.toFixed(2)}
                                </span>
                                <span className="text-gray-500 dark:text-gray-400 text-sm">/ {plan.interval}</span>
                            </div>

                            <div className="flex items-center gap-2 text-sm text-gray-600 dark:text-gray-400 mb-6">
                                <FaCalendarAlt />
                                <span>Billed {plan.interval}</span>
                            </div>

                            <button
                                onClick={() => handleSubscribe(plan.planId)}
                                disabled={subscribingId === plan.planId}
                                className="w-full flex items-center justify-center gap-2 bg-purple-600 hover:bg-purple-700 text-white py-2 rounded-md text-sm font-semibold transition disabled:opacity-50"
                            >
                                <FaSync />
                                {subscribingId === plan.planId ? "Redirecting..." : "Subscribe"}
                            </button>
                        </div>
                    ))}
                </div>
            )}
        </div>
    );
};

export default SubscriptionPlans;
