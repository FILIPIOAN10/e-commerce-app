import { useEffect } from "react";
import { useDispatch, useSelector } from "react-redux";
import { fetchWishlist, removeFromWishlist } from "../../store/actions";
import { FaHeart, FaTrash } from "react-icons/fa";
import { Link } from "react-router-dom";
import Loader from "../shared/Loader";
import EmptyState from "../shared/EmptyState";

const Wishlist = () => {
    const dispatch = useDispatch();
    const { wishlist, error } = useSelector((state) => state.wishlist);
    const { user } = useSelector((state) => state.auth);

    useEffect(() => {
        if (user?.id) {
            dispatch(fetchWishlist(0, 50));
        }
    }, [dispatch, user]);

    const handleRemove = (productId) => {
        dispatch(removeFromWishlist(productId));
    };

    if (!user?.id) {
        return (
            <div className="lg:px-14 sm:px-8 px-4 py-14 2xl:w-[90%] 2xl:mx-auto">
                <div className="flex flex-col items-center justify-center min-h-96">
                    <FaHeart className="text-gray-300 text-6xl mb-4" />
                    <p className="text-gray-500 text-lg mb-4">Please log in to view your wishlist</p>
                    <Link to="/login" className="bg-blue-500 text-white px-6 py-2 rounded-lg hover:bg-blue-600 transition">
                        Login
                    </Link>
                </div>
            </div>
        );
    }

    return (
        <div className="lg:px-14 sm:px-8 px-4 py-14 2xl:w-[90%] 2xl:mx-auto">
            <h1 className="text-2xl font-bold text-slate-800 dark:text-white mb-8 flex items-center gap-2">
                <FaHeart className="text-red-500" />
                My Wishlist
            </h1>

            {error && (
                <div className="bg-red-50 text-red-600 p-4 rounded-lg mb-6">
                    {error}
                </div>
            )}

            {!error && wishlist.length === 0 && (
                <EmptyState
                    icon={FaHeart}
                    title="Your wishlist is empty"
                    message="Save products you love to find them easily later."
                    action={{ label: "Browse Products", path: "/products", icon: FaHeart }}
                />
            )}

            {wishlist.length > 0 && (
                <div className="pb-6 pt-4 grid 2xl:grid-cols-4 lg:grid-cols-3 sm:grid-cols-2 gap-y-6 gap-x-6">
                    {wishlist.map((item, i) => (
                        <div key={i} data-testid="wishlist-item" data-product-id={item.productId} className="border dark:border-gray-700 dark:bg-gray-800 rounded-lg shadow-xl overflow-hidden transition-shadow duration-300 relative">
                            <button
                                onClick={() => handleRemove(item.productId)}
                                data-testid="remove-from-wishlist"
                                className="absolute top-2 right-2 bg-white dark:bg-gray-700 rounded-full p-2 shadow-md hover:bg-red-50 dark:hover:bg-red-900/30 transition z-10"
                                title="Remove from wishlist"
                            >
                                <FaTrash className="text-red-500 text-sm" />
                            </button>
                            <div className="w-full overflow-hidden aspect-3/2">
                                <img
                                    className="w-full h-full object-cover"
                                    src={item.image}
                                    alt={item.productName}
                                />
                            </div>
                            <div className="p-4">
                                <h2 className="text-lg font-semibold mb-2">{item.productName}</h2>
                                <div className="min-h-20 max-h-20">
                                    <p className="text-gray-600 dark:text-gray-400 text-sm">{item.description}</p>
                                </div>
                                <div className="flex items-center justify-between mt-3">
                                    {item.specialPrice ? (
                                        <div className="flex flex-col">
                                            <span className="text-gray-400 line-through text-sm">
                                                ${Number(item.price).toFixed(2)}
                                            </span>
                                            <span className="text-xl font-bold text-slate-700">
                                                ${Number(item.specialPrice).toFixed(2)}
                                            </span>
                                        </div>
                                    ) : (
                                        <span className="text-xl font-bold text-slate-700">
                                            ${Number(item.price).toFixed(2)}
                                        </span>
                                    )}
                                    <Link
                                        to="/products"
                                        className="bg-blue-500 text-white py-2 px-4 rounded-lg hover:bg-blue-600 transition text-sm"
                                    >
                                        View
                                    </Link>
                                </div>
                            </div>
                        </div>
                    ))}
                </div>
            )}
        </div>
    );
};

export default Wishlist;
