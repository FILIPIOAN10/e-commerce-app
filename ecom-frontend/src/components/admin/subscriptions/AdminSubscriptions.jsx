import { useEffect, useState } from "react";
import api from "../../../api/api";
import toast from "react-hot-toast";
import { FaPlus, FaTrash, FaEdit, FaSync, FaTag } from "react-icons/fa";

const AdminSubscriptions = () => {
    const [plans, setPlans] = useState([]);
    const [products, setProducts] = useState([]);
    const [loading, setLoading] = useState(true);
    const [editing, setEditing] = useState(null);

    const [form, setForm] = useState({
        name: "",
        description: "",
        productId: "",
        interval: "month",
        amount: 0,
        currency: "USD",
        active: true,
    });

    const fetchPlans = async () => {
        setLoading(true);
        try {
            const { data } = await api.get("/admin/subscriptions/plans");
            setPlans(data);
        } catch {
            toast.error("Failed to load plans");
        } finally {
            setLoading(false);
        }
    };

    const fetchProducts = async () => {
        try {
            const { data } = await api.get("/public/products?page=0&size=100");
            setProducts(data.content || []);
        } catch {
            toast.error("Failed to load products");
        }
    };

    useEffect(() => {
        fetchPlans();
        fetchProducts();
    }, []);

    const resetForm = () => {
        setForm({
            name: "",
            description: "",
            productId: "",
            interval: "month",
            amount: 0,
            currency: "USD",
            active: true,
        });
        setEditing(null);
    };

    const handleSubmit = async (e) => {
        e.preventDefault();
        if (!form.name.trim() || !form.productId) {
            toast.error("Name and product are required");
            return;
        }

        const payload = {
            ...form,
            productId: Number(form.productId),
            amount: Number(form.amount),
        };

        try {
            if (editing) {
                await api.put(`/admin/subscriptions/plans/${editing}`, payload);
                toast.success("Plan updated");
            } else {
                await api.post("/admin/subscriptions/plans", payload);
                toast.success("Plan created");
            }
            resetForm();
            fetchPlans();
        } catch (error) {
            const msg = error?.response?.data?.message || "Failed to save plan";
            toast.error(msg);
        }
    };

    const handleEdit = (plan) => {
        setEditing(plan.planId);
        setForm({
            name: plan.name,
            description: plan.description || "",
            productId: plan.productId || "",
            interval: plan.interval,
            amount: plan.amount,
            currency: plan.currency,
            active: plan.active,
        });
    };

    const handleDelete = async (planId) => {
        if (!confirm("Delete this plan?")) return;
        try {
            await api.delete(`/admin/subscriptions/plans/${planId}`);
            toast.success("Plan deleted");
            fetchPlans();
        } catch (error) {
            const msg = error?.response?.data?.message || "Failed to delete plan";
            toast.error(msg);
        }
    };

    return (
        <div className="pb-6 pt-20 px-4 max-w-7xl mx-auto">
            <div className="mb-6">
                <h1 className="text-3xl font-bold text-gray-800 dark:text-white">Subscription Plans</h1>
                <p className="text-gray-500 text-sm">Manage recurring subscription plans.</p>
            </div>

            <form onSubmit={handleSubmit} className="bg-white dark:bg-gray-800 rounded-lg shadow-sm p-4 mb-8 border border-gray-100 dark:border-gray-700">
                <div className="grid grid-cols-1 md:grid-cols-2 gap-4 mb-4">
                    <input
                        type="text"
                        placeholder="Plan name"
                        value={form.name}
                        onChange={(e) => setForm({ ...form, name: e.target.value })}
                        className="border border-gray-300 dark:border-gray-600 rounded-md p-2 text-sm dark:bg-gray-700 dark:text-white"
                    />
                    <select
                        value={form.productId}
                        onChange={(e) => setForm({ ...form, productId: e.target.value })}
                        className="border border-gray-300 dark:border-gray-600 rounded-md p-2 text-sm dark:bg-gray-700 dark:text-white"
                    >
                        <option value="">Select product</option>
                        {products.map((p) => (
                            <option key={p.productId} value={p.productId}>
                                {p.productName}
                            </option>
                        ))}
                    </select>
                </div>
                <div className="grid grid-cols-1 md:grid-cols-3 gap-4 mb-4">
                    <input
                        type="number"
                        step="0.01"
                        placeholder="Amount"
                        value={form.amount}
                        onChange={(e) => setForm({ ...form, amount: e.target.value })}
                        className="border border-gray-300 dark:border-gray-600 rounded-md p-2 text-sm dark:bg-gray-700 dark:text-white"
                    />
                    <select
                        value={form.interval}
                        onChange={(e) => setForm({ ...form, interval: e.target.value })}
                        className="border border-gray-300 dark:border-gray-600 rounded-md p-2 text-sm dark:bg-gray-700 dark:text-white"
                    >
                        <option value="month">Monthly</option>
                        <option value="year">Yearly</option>
                    </select>
                    <input
                        type="text"
                        placeholder="Currency"
                        value={form.currency}
                        onChange={(e) => setForm({ ...form, currency: e.target.value })}
                        className="border border-gray-300 dark:border-gray-600 rounded-md p-2 text-sm dark:bg-gray-700 dark:text-white"
                    />
                </div>
                <textarea
                    placeholder="Description"
                    value={form.description}
                    onChange={(e) => setForm({ ...form, description: e.target.value })}
                    className="w-full border border-gray-300 dark:border-gray-600 rounded-md p-2 text-sm mb-4 dark:bg-gray-700 dark:text-white"
                    rows={2}
                />
                <div className="flex items-center gap-4 mb-4">
                    <label className="flex items-center gap-2 text-sm text-gray-700 dark:text-gray-300">
                        <input
                            type="checkbox"
                            checked={form.active}
                            onChange={(e) => setForm({ ...form, active: e.target.checked })}
                            className="rounded"
                        />
                        Active
                    </label>
                </div>
                <div className="flex gap-2">
                    <button
                        type="submit"
                        className="flex items-center bg-purple-600 hover:bg-purple-700 text-white px-4 py-2 rounded-md text-sm font-semibold"
                    >
                        <FaPlus className="mr-1" />
                        {editing ? "Update Plan" : "Create Plan"}
                    </button>
                    {editing && (
                        <button
                            type="button"
                            onClick={resetForm}
                            className="px-4 py-2 text-gray-600 dark:text-gray-300 text-sm"
                        >
                            Cancel
                        </button>
                    )}
                </div>
            </form>

            {loading ? (
                <div className="text-center py-20 text-gray-500 dark:text-gray-400">Loading...</div>
            ) : plans.length === 0 ? (
                <div className="text-center text-gray-500 dark:text-gray-400 py-10">No plans yet.</div>
            ) : (
                <div className="overflow-x-auto bg-white dark:bg-gray-800 rounded-lg shadow-sm border border-gray-100 dark:border-gray-700">
                    <table className="w-full text-sm">
                        <thead className="bg-gray-50 dark:bg-gray-900 border-b dark:border-gray-700">
                            <tr>
                                <th className="px-4 py-3 text-left font-semibold text-gray-700 dark:text-gray-300">Name</th>
                                <th className="px-4 py-3 text-left font-semibold text-gray-700 dark:text-gray-300">Product</th>
                                <th className="px-4 py-3 text-left font-semibold text-gray-700 dark:text-gray-300">Price</th>
                                <th className="px-4 py-3 text-left font-semibold text-gray-700 dark:text-gray-300">Interval</th>
                                <th className="px-4 py-3 text-left font-semibold text-gray-700 dark:text-gray-300">Status</th>
                                <th className="px-4 py-3 text-right font-semibold text-gray-700 dark:text-gray-300">Actions</th>
                            </tr>
                        </thead>
                        <tbody className="divide-y divide-gray-100 dark:divide-gray-700">
                            {plans.map((plan) => (
                                <tr key={plan.planId} className="hover:bg-gray-50 dark:hover:bg-gray-700/50">
                                    <td className="px-4 py-3 text-gray-700 dark:text-gray-300 font-medium">{plan.name}</td>
                                    <td className="px-4 py-3 text-gray-600 dark:text-gray-400">{products.find(p => p.productId === plan.productId)?.productName || "—"}</td>
                                    <td className="px-4 py-3 text-gray-600 dark:text-gray-400">${plan.amount?.toFixed(2)} {plan.currency}</td>
                                    <td className="px-4 py-3 text-gray-600 dark:text-gray-400">{plan.interval}</td>
                                    <td className="px-4 py-3">
                                        {plan.active ? (
                                            <span className="bg-green-100 text-green-700 px-2 py-1 rounded-full text-xs font-semibold">Active</span>
                                        ) : (
                                            <span className="bg-gray-100 text-gray-600 px-2 py-1 rounded-full text-xs font-semibold">Inactive</span>
                                        )}
                                    </td>
                                    <td className="px-4 py-3 text-right">
                                        <div className="flex justify-end gap-2">
                                            <button
                                                onClick={() => handleEdit(plan)}
                                                className="bg-blue-500 hover:bg-blue-600 text-white p-2 rounded-md text-xs"
                                                title="Edit"
                                            >
                                                <FaEdit />
                                            </button>
                                            <button
                                                onClick={() => handleDelete(plan.planId)}
                                                className="bg-red-500 hover:bg-red-600 text-white p-2 rounded-md text-xs"
                                                title="Delete"
                                            >
                                                <FaTrash />
                                            </button>
                                        </div>
                                    </td>
                                </tr>
                            ))}
                        </tbody>
                    </table>
                </div>
            )}
        </div>
    );
};

export default AdminSubscriptions;
