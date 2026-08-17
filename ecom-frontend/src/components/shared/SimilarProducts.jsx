import { useEffect } from "react";
import { useDispatch, useSelector } from "react-redux";
import { useNavigate } from "react-router-dom";
import { fetchSimilarProducts } from "../../store/actions";
import { FaTags } from "react-icons/fa";

const SimilarProducts = ({ productId, onProductClick }) => {
    const dispatch = useDispatch();
    const navigate = useNavigate();
    const { similarProducts } = useSelector((state) => state.products);

    useEffect(() => {
        if (productId) {
            dispatch(fetchSimilarProducts(productId, 4));
        }
    }, [dispatch, productId]);

    if (!similarProducts || similarProducts.length === 0) {
        return null;
    }

    const handleClick = (item) => {
        if (onProductClick) {
            onProductClick(item);
        } else {
            navigate(`/products/${item.productId}`);
        }
    };

    return (
        <div className="px-2 py-4">
            <h3 className="text-lg font-semibold text-gray-800 dark:text-white mb-3 flex items-center gap-2">
                <FaTags className="text-blue-500" />
                Similar Products
            </h3>
            <div className="grid grid-cols-2 sm:grid-cols-4 gap-3">
                {similarProducts.map((item) => (
                    <div
                        key={item.productId}
                        className="flex flex-col items-center text-center p-2 rounded-lg hover:bg-gray-50 dark:hover:bg-gray-700 transition cursor-pointer"
                        onClick={() => handleClick(item)}
                    >
                        {item.image && (
                            <img
                                src={item.image}
                                alt={item.productName}
                                className="w-16 h-16 object-cover rounded-md mb-2"
                            />
                        )}
                        <span className="text-xs font-medium text-gray-700 dark:text-gray-300 line-clamp-2">
                            {item.productName}
                        </span>
                        <span className="text-xs text-slate-600 dark:text-gray-400 font-semibold mt-1">
                            ${Number(item.specialPrice || item.price).toFixed(2)}
                        </span>
                    </div>
                ))}
            </div>
        </div>
    );
};

export default SimilarProducts;
