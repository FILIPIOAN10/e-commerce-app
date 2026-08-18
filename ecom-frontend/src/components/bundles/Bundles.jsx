import { useEffect, useState } from "react";
import api from "../../api/api";
import toast from "react-hot-toast";
import BundleCard from "./BundleCard";
import Loader from "../shared/Loader";
import { FaBoxOpen } from "react-icons/fa";

const Bundles = () => {
    const [bundles, setBundles] = useState([]);
    const [loading, setLoading] = useState(true);
    const [addingId, setAddingId] = useState(null);

    const fetchBundles = async () => {
        setLoading(true);
        try {
            const { data } = await api.get("/public/bundles");
            setBundles(data);
        } catch {
            toast.error("Failed to load bundles");
        } finally {
            setLoading(false);
        }
    };

    useEffect(() => {
        fetchBundles();
    }, []);

    const handleAddToCart = async (bundleId) => {
        setAddingId(bundleId);
        try {
            await api.post(`/carts/bundles/${bundleId}`);
            toast.success("Bundle added to cart");
        } catch (error) {
            const msg = error?.response?.data?.message || "Failed to add bundle";
            toast.error(msg);
        } finally {
            setAddingId(null);
        }
    };

    return (
        <div className="min-h-screen dark:bg-gray-950 dark:text-white py-6 px-4 sm:px-8 lg:px-14">
            <div className="mb-8 text-center">
                <h1 className="text-3xl font-bold text-slate-800 dark:text-white mb-2">Product Bundles</h1>
                <p className="text-gray-500 dark:text-gray-400">Buy together and save more.</p>
            </div>

            {loading ? (
                <div className="flex justify-center py-20">
                    <Loader />
                </div>
            ) : bundles.length === 0 ? (
                <div className="flex flex-col items-center justify-center text-gray-600 dark:text-gray-400 py-20">
                    <FaBoxOpen className="text-5xl mb-4" />
                    <h2 className="text-xl font-semibold">No bundles available</h2>
                    <p className="text-sm">Check back later for special offers.</p>
                </div>
            ) : (
                <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-6">
                    {bundles.map((bundle) => (
                        <BundleCard
                            key={bundle.bundleId}
                            bundle={bundle}
                            onAddToCart={handleAddToCart}
                            loading={addingId === bundle.bundleId}
                        />
                    ))}
                </div>
            )}
        </div>
    );
};

export default Bundles;
