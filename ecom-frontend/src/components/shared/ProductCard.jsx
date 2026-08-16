import { useState } from "react";
import { FaShoppingCart, FaHeart, FaStar, FaStarHalfAlt, FaBalanceScale, FaCheck } from "react-icons/fa";
import ProductViewModal from "./ProductViewModal";
import truncateText from "../../utils/truncateText";
import { useDispatch, useSelector } from "react-redux";
import toast from "react-hot-toast";
import { addToCart, addToWishlist, addToCompare } from "../../store/actions";

const ProductCard = ({
        productId,
        productName,
        image,
        description,
        quantity,
        price,
        discount,
        specialPrice,
        images,
        averageRating,
        reviewCount,
        categoryName,
        about = false,
}) => {
    const [openProductViewModal, setOpenProductViewModal] = useState(false);
    const btnLoader = false;
    const [selectedViewProduct, setSelectedViewProduct] = useState(null);
    const isAvailable = quantity && Number(quantity) > 0;
    const dispatch = useDispatch();

    // ✅ verifică dacă e admin
    const { user } = useSelector((state) => state.auth);
    const isAdmin = Boolean(user?.roles?.includes("ROLE_ADMIN"));
    const { wishlist } = useSelector((state) => state.wishlist);
    const isInWishlist = wishlist?.some((w) => w.productId === productId);
    const { compareList } = useSelector((state) => state.products);
    const isInCompare = compareList?.some((p) => p.productId === productId);

    const handleAddToWishlist = () => {
        dispatch(addToWishlist(productId));
    };

    const handleAddToCompare = () => {
        if (compareList.length >= 3) {
            toast.error("You can compare up to 3 products");
            return;
        }
        dispatch(addToCompare({ productId, productName, image, description, quantity, price, discount, specialPrice, averageRating, reviewCount, categoryName }));
        toast.success("Added to compare");
    };

    const handleProductView = (product) => {
        if (!about) {
            setSelectedViewProduct(product);
            setOpenProductViewModal(true);
        }
    };

    const addToCartHandler = (cartItems) => {
        dispatch(addToCart(cartItems, 1, toast));
    };

    return (
        <div className="border rounded-lg shadow-xl overflow-hidden transition-shadow duration-300 dark:bg-gray-800 dark:border-gray-700">
            <div onClick={() => handleProductView({ id: productId, productName, image, description, quantity, price, discount, specialPrice, images, categoryName, averageRating, reviewCount })}
                className="w-full overflow-hidden aspect-3/2">
                <img className="w-full h-full cursor-pointer transition-transform duration-300 transform hover:scale-105"
                    src={image}
                    alt={productName}
                />
            </div>
            <div className="p-4 relative">
                {!about && (
                    <div className="absolute top-3 right-3 flex flex-col gap-2">
                        {user?.id && !isAdmin && (
                            <button
                                onClick={handleAddToWishlist}
                                className={`${isInWishlist ? "text-red-500" : "text-gray-300 hover:text-red-400"} transition text-xl`}
                                title={isInWishlist ? "Already in wishlist" : "Add to wishlist"}
                                disabled={isInWishlist}
                            >
                                <FaHeart />
                            </button>
                        )}
                        <button
                            onClick={handleAddToCompare}
                            className={`${isInCompare ? "text-blue-500" : "text-gray-300 hover:text-blue-400"} transition text-xl`}
                            title={isInCompare ? "In compare list" : "Add to compare"}
                            disabled={isInCompare}
                        >
                            {isInCompare ? <FaCheck /> : <FaBalanceScale />}
                        </button>
                    </div>
                )}
                <h2 onClick={() => handleProductView({ id: productId, productName, image, description, quantity, price, discount, specialPrice, images, categoryName, averageRating, reviewCount })}
                    className="text-lg font-semibold mb-2 cursor-pointer pr-8 dark:text-white">
                    {truncateText(productName, 50)}
                </h2>

                {reviewCount > 0 && (
                    <div className="flex items-center gap-1 mb-2">
                        <div className="flex items-center">
                            {[1, 2, 3, 4, 5].map((star) => {
                                const rating = averageRating || 0;
                                if (rating >= star) {
                                    return <FaStar key={star} className="text-amber-400 text-sm" />;
                                } else if (rating >= star - 0.5) {
                                    return <FaStarHalfAlt key={star} className="text-amber-400 text-sm" />;
                                } else {
                                    return <FaStar key={star} className="text-gray-300 text-sm" />;
                                }
                            })}
                        </div>
                        <span className="text-xs text-gray-500 dark:text-gray-400 ml-1">
                            {averageRating?.toFixed(1)} ({reviewCount})
                        </span>
                    </div>
                )}

                <div className="min-h-20 max-h-20">
                    <p className="text-gray-600 text-sm dark:text-gray-300">{truncateText(description, 80)}</p>
                </div>

                {/* ✅ ascunde toată secțiunea de preț + cart pentru admin */}
                {!about && !isAdmin && (
                    <div className="flex items-center justify-between">
                        {specialPrice ? (
                            <div className="flex flex-col">
                                <span className="text-gray-400 line-through">
                                    ${Number(price).toFixed(2)}
                                </span>
                                <span className="text-xl font-bold text-slate-700 dark:text-white">
                                    ${Number(specialPrice).toFixed(2)}
                                </span>
                            </div>
                        ) : (
                            <div>
                                <span className="text-xl font-bold text-slate-700 dark:text-white">
                                    ${Number(price).toFixed(2)}
                                </span>
                            </div>
                        )}
                        <button
                            disabled={!isAvailable || btnLoader}
                            onClick={(e) => { e.stopPropagation(); addToCartHandler({ image, productName, description, specialPrice, price, productId, quantity }); }}
                            className={`bg-blue-500 ${isAvailable ? "opacity-100 hover:bg-blue-600" : "opacity-70"}
                                text-white py-2 px-3 rounded-lg items-center transition-colors duration-300 w-36 flex justify-center`}>
                            <FaShoppingCart className="mr-2" />
                            {isAvailable ? "Add to Cart" : "Stock Out"}
                        </button>
                    </div>
                )}

                {/* ✅ admin vede doar prețul, fără cart */}
                {!about && isAdmin && (
                    <div className="flex items-center">
                        {specialPrice ? (
                            <div className="flex flex-col">
                                <span className="text-gray-400 line-through">
                                    ${Number(price).toFixed(2)}
                                </span>
                                <span className="text-xl font-bold text-slate-700 dark:text-white">
                                    ${Number(specialPrice).toFixed(2)}
                                </span>
                            </div>
                        ) : (
                            <span className="text-xl font-bold text-slate-700 dark:text-white">
                                ${Number(price).toFixed(2)}
                            </span>
                        )}
                    </div>
                )}
            </div>
            {selectedViewProduct && (
                <ProductViewModal
                    open={openProductViewModal}
                    setOpen={setOpenProductViewModal}
                    product={selectedViewProduct}
                    isAvailable={isAvailable}
                />
            )}
        </div>
    );
};

export default ProductCard;