import { FaShoppingCart, FaTag } from "react-icons/fa";

const BundleCard = ({ bundle, onAddToCart, loading }) => {
    return (
        <div className="bg-white dark:bg-gray-800 rounded-xl shadow-md border border-gray-100 dark:border-gray-700 overflow-hidden hover:shadow-lg transition p-4">
            <div className="flex justify-between items-start mb-3">
                <div>
                    <h3 className="text-lg font-bold text-gray-800 dark:text-white">{bundle.name}</h3>
                    <p className="text-sm text-gray-500 dark:text-gray-400 line-clamp-2">{bundle.description}</p>
                </div>
                <span className="bg-orange-100 text-orange-700 text-xs font-bold px-2 py-1 rounded-full flex items-center">
                    <FaTag className="mr-1" />
                    Save {bundle.discountPercentage}%
                </span>
            </div>

            <div className="flex gap-2 overflow-x-auto pb-3 mb-3">
                {bundle.products?.map((product) => (
                    <div key={product.productId} className="min-w-[80px] text-center">
                        <img
                            src={product.image}
                            alt={product.productName}
                            className="w-16 h-16 object-cover rounded-md mx-auto"
                            onError={(e) => { e.target.src = "/images/cal.png"; }}
                        />
                        <p className="text-xs text-gray-600 dark:text-gray-300 truncate w-20 mx-auto">
                            {product.productName}
                        </p>
                    </div>
                ))}
            </div>

            <div className="flex items-end gap-2 mb-4">
                <span className="text-2xl font-bold text-green-600 dark:text-green-400">
                    ${bundle.discountedPrice?.toFixed(2)}
                </span>
                <span className="text-sm text-gray-400 line-through">
                    ${bundle.bundlePrice?.toFixed(2)}
                </span>
                <span className="text-xs text-green-600 dark:text-green-400 font-medium ml-auto">
                    You save ${bundle.savings?.toFixed(2)}
                </span>
            </div>

            <button
                onClick={() => onAddToCart(bundle.bundleId)}
                disabled={loading}
                className="w-full flex items-center justify-center gap-2 bg-orange-500 hover:bg-orange-600 text-white py-2 rounded-md text-sm font-semibold transition disabled:opacity-50"
            >
                <FaShoppingCart />
                {loading ? "Adding..." : "Add Bundle to Cart"}
            </button>
        </div>
    );
};

export default BundleCard;
