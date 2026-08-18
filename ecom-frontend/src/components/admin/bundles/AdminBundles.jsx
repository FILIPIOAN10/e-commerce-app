import { useEffect, useState } from "react";
import api from "../../../api/api";
import toast from "react-hot-toast";
import { FaPlus, FaTrash, FaEdit, FaTag, FaCheck, FaTimes } from "react-icons/fa";

const AdminBundles = () => {
    const [bundles, setBundles] = useState([]);
    const [products, setProducts] = useState([]);
    const [loading, setLoading] = useState(true);
    const [editing, setEditing] = useState(null);

    const [form, setForm] = useState({
        name: "",
        description: "",
        discountPercentage: 0,
        active: true,
        products: [],
    });

    const fetchBundles = async () => {
        setLoading(true);
        try {
            const { data } = await api.get("/admin/bundles");
            setBundles(data);
        } catch {
            toast.error("Failed to load bundles");
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
        fetchBundles();
        fetchProducts();
    }, []);

    const resetForm = () => {
        setForm({ name: "", description: "", discountPercentage: 0, active: true, products: [] });
        setEditing(null);
    };

    const handleSubmit = async (e) => {
        e.preventDefault();
        if (!form.name.trim() || form.products.length === 0) {
            toast.error("Name and at least one product are required");
            return;
        }

        const payload = {
            ...form,
            products: form.products.map((productId) => ({ productId })),
        };

        try {
            if (editing) {
                await api.put(`/admin/bundles/${editing}`, payload);
                toast.success("Bundle updated");
            } else {
                await api.post("/admin/bundles", payload);
                toast.success("Bundle created");
            }
            resetForm();
            fetchBundles();
        } catch (error) {
            const msg = error?.response?.data?.message || "Failed to save bundle";
            toast.error(msg);
        }
    };

    const handleEdit = (bundle) => {
        setEditing(bundle.bundleId);
        setForm({
            name: bundle.name,
            description: bundle.description || "",
            discountPercentage: bundle.discountPercentage,
            active: bundle.active,
            products: bundle.products?.map((p) => p.productId) || [],
        });
    };

    const handleDelete = async (bundleId) => {
        if (!confirm("Delete this bundle?")) return;
        try {
            await api.delete(`/admin/bundles/${bundleId}`);
            toast.success("Bundle deleted");
            fetchBundles();
        } catch (error) {
            const msg = error?.response?.data?.message || "Failed to delete bundle";
            toast.error(msg);
        }
    };

    const toggleProduct = (productId) => {
        setForm((prev) => {
            const exists = prev.products.includes(productId);
            return {
                ...prev,
                products: exists
                    ? prev.products.filter((id) => id !== productId)
                    : [...prev.products, productId],
            };
        });
    };

    return (
        <div className="pb-6 pt-20 px-4 max-w-7xl mx-auto">
            <div className="mb-6">
                <h1 className="text-3xl font-bold text-gray-800 dark:text-white">Product Bundles</h1>
                <p className="text-gray-500 text-sm">Create bundles and offer discounts.</p>
            </div>

            <form onSubmit={handleSubmit} className="bg-white dark:bg-gray-800 rounded-lg shadow-sm p-4 mb-8 border border-gray-100 dark:border-gray-700">
                <div className="grid grid-cols-1 md:grid-cols-2 gap-4 mb-4">
                    <input
                        type="text"
                        placeholder="Bundle name"
                        value={form.name}
                        onChange={(e) => setForm({ ...form, name: e.target.value })}
                        className="border border-gray-300 dark:border-gray-600 rounded-md p-2 text-sm dark:bg-gray-700 dark:text-white"
                    />
                    <input
                        type="number"
                        placeholder="Discount %"
                        value={form.discountPercentage}
                        onChange={(e) => setForm({ ...form, discountPercentage: Number(e.target.value) })}
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

                <div className="mb-4">
                    <p className="text-sm font-semibold text-gray-700 dark:text-gray-300 mb-2">Select products</p>
                    <div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 gap-2 max-h-48 overflow-y-auto border rounded-md p-2 dark:border-gray-600">
                        {products.map((product) => (
                            <label
                                key={product.productId}
                                className="flex items-center gap-2 text-sm text-gray-700 dark:text-gray-300"
                            >
                                <input
                                    type="checkbox"
                                    checked={form.products.includes(product.productId)}
                                    onChange={() => toggleProduct(product.productId)}
                                    className="rounded"
                                />
                                <span className="truncate">{product.productName}</span>
                            </label>
                        ))}
                    </div>
                </div>

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
                        className="flex items-center bg-green-500 hover:bg-green-600 text-white px-4 py-2 rounded-md text-sm font-semibold"
                    >
                        <FaPlus className="mr-1" />
                        {editing ? "Update Bundle" : "Create Bundle"}
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
            ) : bundles.length === 0 ? (
                <div className="text-center text-gray-500 dark:text-gray-400 py-10">No bundles yet.</div>
            ) : (
                <div className="overflow-x-auto bg-white dark:bg-gray-800 rounded-lg shadow-sm border border-gray-100 dark:border-gray-700">
                    <table className="w-full text-sm">
                        <thead className="bg-gray-50 dark:bg-gray-900 border-b dark:border-gray-700">
                            <tr>
                                <th className="px-4 py-3 text-left font-semibold text-gray-700 dark:text-gray-300">Name</th>
                                <th className="px-4 py-3 text-left font-semibold text-gray-700 dark:text-gray-300">Products</th>
                                <th className="px-4 py-3 text-left font-semibold text-gray-700 dark:text-gray-300">Discount</th>
                                <th className="px-4 py-3 text-left font-semibold text-gray-700 dark:text-gray-300">Status</th>
                                <th className="px-4 py-3 text-right font-semibold text-gray-700 dark:text-gray-300">Actions</th>
                            </tr>
                        </thead>
                        <tbody className="divide-y divide-gray-100 dark:divide-gray-700">
                            {bundles.map((b) => (
                                <tr key={b.bundleId} className="hover:bg-gray-50 dark:hover:bg-gray-700/50">
                                    <td className="px-4 py-3 text-gray-700 dark:text-gray-300 font-medium">{b.name}</td>
                                    <td className="px-4 py-3 text-gray-600 dark:text-gray-400">{b.products?.length || 0} products</td>
                                    <td className="px-4 py-3 text-gray-600 dark:text-gray-400">{b.discountPercentage}%</td>
                                    <td className="px-4 py-3">
                                        {b.active ? (
                                            <span className="bg-green-100 text-green-700 px-2 py-1 rounded-full text-xs font-semibold">Active</span>
                                        ) : (
                                            <span className="bg-gray-100 text-gray-600 px-2 py-1 rounded-full text-xs font-semibold">Inactive</span>
                                        )}
                                    </td>
                                    <td className="px-4 py-3 text-right">
                                        <div className="flex justify-end gap-2">
                                            <button
                                                onClick={() => handleEdit(b)}
                                                className="bg-blue-500 hover:bg-blue-600 text-white p-2 rounded-md text-xs"
                                                title="Edit"
                                            >
                                                <FaEdit />
                                            </button>
                                            <button
                                                onClick={() => handleDelete(b.bundleId)}
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

export default AdminBundles;
