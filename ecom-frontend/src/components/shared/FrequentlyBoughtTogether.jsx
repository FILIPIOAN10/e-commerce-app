import { useEffect } from "react";
import { useDispatch, useSelector } from "react-redux";
import { useNavigate } from "react-router-dom";
import { fetchFrequentlyBoughtTogether } from "../../store/actions";
import { FaShoppingCart } from "react-icons/fa";

const FrequentlyBoughtTogether = ({ productId, onProductClick }) => {
    const dispatch = useDispatch();
    const navigate = useNavigate();
    const { frequentlyBoughtTogetherProducts } = useSelector((state) => state.products);

    useEffect(() => {
        if (productId) {
            dispatch(fetchFrequentlyBoughtTogether(productId, 4));
        }
    }, [dispatch, productId]);

    if (!frequentlyBoughtTogetherProducts || frequentlyBoughtTogetherProducts.length === 0) {
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
                <FaShoppingCart className="text-emerald-500" />
                Frequently Bought Together
            </h3>
            <div className="grid grid-cols-2 sm:grid-cols-4 gap-3">
                {frequentlyBoughtTogetherProducts.map((item) => (
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

export default FrequentlyBoughtTogether;
