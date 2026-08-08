import { useSelector, useDispatch } from "react-redux";
import { Link } from "react-router-dom";
import { FaTimes, FaStar, FaStarHalfAlt, FaBalanceScale, FaShoppingCart } from "react-icons/fa";
import { removeFromCompare, clearCompare, addToCart } from "../../store/actions";
import toast from "react-hot-toast";

const ComparePage = () => {
    const dispatch = useDispatch();
    const { compareList } = useSelector((state) => state.products);

    const handleRemove = (productId) => {
        dispatch(removeFromCompare(productId));
    };

    const handleClear = () => {
        dispatch(clearCompare());
    };

    const handleAddToCart = (product) => {
        dispatch(addToCart({
            image: product.image,
            productName: product.productName,
            description: product.description,
            specialPrice: product.specialPrice,
            price: product.price,
            productId: product.productId,
            quantity: product.quantity,
        }, 1, toast));
    };

    const renderStars = (rating) => {
        return [1, 2, 3, 4, 5].map((star) => {
            if (rating >= star) return <FaStar key={star} className="text-amber-400 text-sm inline" />;
            if (rating >= star - 0.5) return <FaStarHalfAlt key={star} className="text-amber-400 text-sm inline" />;
            return <FaStar key={star} className="text-gray-300 text-sm inline" />;
        });
    };

    if (!compareList || compareList.length === 0) {
        return (
            <div className="lg:px-14 sm:px-8 px-4 py-20 dark:bg-gray-950 dark:text-white min-h-screen">
                <div className="flex flex-col items-center justify-center text-gray-600 py-20">
                    <FaBalanceScale size={60} className="mb-4 text-gray-400" />
                    <h2 className="text-2xl font-semibold dark:text-white">No products to compare</h2>
                    <p className="text-gray-400 text-sm mt-2 mb-6">
                        Add products to compare by clicking the balance icon on any product card.
                    </p>
                    <Link
                        to="/products"
                        className="bg-blue-500 hover:bg-blue-600 text-white px-6 py-2 rounded-lg transition"
                    >
                        Browse Products
                    </Link>
                </div>
            </div>
        );
    }

    const specs = [
        { label: "Image", key: "image", type: "image" },
        { label: "Price", key: "price", type: "price" },
        { label: "Special Price", key: "specialPrice", type: "specialPrice" },
        { label: "Discount", key: "discount", type: "discount" },
        { label: "Category", key: "categoryName", type: "text" },
        { label: "Rating", key: "averageRating", type: "rating" },
        { label: "Reviews", key: "reviewCount", type: "text" },
        { label: "Availability", key: "quantity", type: "availability" },
        { label: "Description", key: "description", type: "text" },
    ];

    return (
        <div className="lg:px-14 sm:px-8 px-4 py-8 dark:bg-gray-950 dark:text-white min-h-screen">
            <div className="flex justify-between items-center mb-6">
                <div>
                    <h1 className="text-3xl font-bold text-slate-800 dark:text-white flex items-center gap-2">
                        <FaBalanceScale className="text-blue-500" />
                        Compare Products
                    </h1>
                    <p className="text-gray-500 text-sm mt-1">
                        Comparing {compareList.length} of 3 products side-by-side
                    </p>
                </div>
                <button
                    onClick={handleClear}
                    className="text-sm text-gray-500 hover:text-red-500 transition"
                >
                    Clear All
                </button>
            </div>

            <div className="overflow-x-auto">
                <table className="w-full border-collapse">
                    <thead>
                        <tr>
                            <th className="w-40 p-4 bg-gray-50 dark:bg-gray-800 border-b border-gray-200 dark:border-gray-700 text-left text-sm font-semibold text-gray-600 dark:text-gray-300">
                                Specification
                            </th>
                            {compareList.map((product) => (
                                <th key={product.productId} className="p-4 bg-gray-50 dark:bg-gray-800 border-b border-gray-200 dark:border-gray-700 min-w-48">
                                    <div className="flex flex-col items-center">
                                        <button
                                            onClick={() => handleRemove(product.productId)}
                                            className="self-end text-gray-400 hover:text-red-500 transition mb-1"
                                        >
                                            <FaTimes />
                                        </button>
                                        <img
                                            src={product.image}
                                            alt={product.productName}
                                            className="w-32 h-24 object-contain mb-2"
                                        />
                                        <span className="text-sm font-semibold text-slate-800 dark:text-white text-center">
                                            {product.productName}
                                        </span>
                                    </div>
                                </th>
                            ))}
                        </tr>
                    </thead>
                    <tbody>
                        {specs.map((spec) => (
                            <tr key={spec.key} className="border-b border-gray-100 dark:border-gray-800">
                                <td className="p-4 text-sm font-medium text-gray-600 dark:text-gray-300 bg-gray-50/50 dark:bg-gray-800/50">
                                    {spec.label}
                                </td>
                                {compareList.map((product) => (
                                    <td key={product.productId} className="p-4 text-sm text-center text-slate-700 dark:text-gray-200">
                                        {renderSpecValue(spec, product, renderStars, handleAddToCart)}
                                    </td>
                                ))}
                            </tr>
                        ))}
                        <tr className="border-b border-gray-100 dark:border-gray-800">
                            <td className="p-4 text-sm font-medium text-gray-600 dark:text-gray-300 bg-gray-50/50 dark:bg-gray-800/50">
                                Action
                            </td>
                            {compareList.map((product) => (
                                <td key={product.productId} className="p-4 text-center">
                                    <button
                                        onClick={() => handleAddToCart(product)}
                                        disabled={!product.quantity || product.quantity <= 0}
                                        className="inline-flex items-center bg-blue-500 hover:bg-blue-600 disabled:opacity-50 text-white px-3 py-2 rounded-lg text-sm transition"
                                    >
                                        <FaShoppingCart className="mr-1" />
                                        Add to Cart
                                    </button>
                                </td>
                            ))}
                        </tr>
                    </tbody>
                </table>
            </div>

            {compareList.length < 3 && (
                <div className="mt-6 text-center">
                    <Link
                        to="/products"
                        className="inline-block text-blue-500 hover:text-blue-600 text-sm font-medium"
                    >
                        + Add more products to compare ({3 - compareList.length} remaining)
                    </Link>
                </div>
            )}
        </div>
    );
};

function renderSpecValue(spec, product, renderStars, handleAddToCart) {
    switch (spec.type) {
        case "image":
            return null;
        case "price":
            return <span className="font-semibold">${Number(product.price).toFixed(2)}</span>;
        case "specialPrice":
            return product.specialPrice ? (
                <span className="font-semibold text-green-600">${Number(product.specialPrice).toFixed(2)}</span>
            ) : <span className="text-gray-400">—</span>;
        case "discount":
            return product.discount > 0 ? (
                <span className="text-orange-500 font-medium">{product.discount}%</span>
            ) : <span className="text-gray-400">—</span>;
        case "rating":
            return product.averageRating ? (
                <div className="flex items-center justify-center gap-1">
                    {renderStars(product.averageRating)}
                    <span className="ml-1 text-xs text-gray-500">{product.averageRating.toFixed(1)}</span>
                </div>
            ) : <span className="text-gray-400">No ratings</span>;
        case "availability":
            return product.quantity > 0 ? (
                <span className="text-green-600 font-medium">In Stock ({product.quantity})</span>
            ) : (
                <span className="text-red-500 font-medium">Out of Stock</span>
            );
        default:
            const value = product[spec.key];
            return value ? <span>{value}</span> : <span className="text-gray-400">—</span>;
    }
}

export default ComparePage;
