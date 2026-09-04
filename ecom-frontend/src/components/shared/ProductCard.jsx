import { useNavigate } from "react-router-dom";
import { FaShoppingCart, FaHeart, FaStar, FaStarHalfAlt, FaBalanceScale, FaCheck } from "react-icons/fa";
import truncateText from "../../utils/truncateText";
import { useDispatch, useSelector } from "react-redux";
import toast from "react-hot-toast";
import { addToCart, addToWishlist, addToCompare } from "../../store/actions";
import { useTranslation } from "react-i18next";
import { useLanguage } from "../../context/LanguageContext";

const ProductCard = ({
        productId,
        productName,
        image,
        description,
        quantity,
        price,
        discount,
        specialPrice,
        averageRating,
        reviewCount,
        categoryName,
        about = false,
}) => {
    const navigate = useNavigate();
    const btnLoader = false;
    const isAvailable = quantity && Number(quantity) > 0;
    const dispatch = useDispatch();
    const { t } = useTranslation("product");
    const lang = useLanguage();

    // ✅ verifică dacă e admin
    const { user } = useSelector((state) => state.auth);
    const isAdmin = Boolean(user?.roles?.includes("ROLE_ADMIN"));
    const { wishlist } = useSelector((state) => state.wishlist);
    const isInWishlist = wishlist?.some((w) => w.productId === productId);
    const { compareList } = useSelector((state) => state.products);
    const isInCompare = compareList?.some((p) => p.productId === productId);

    // Both of these sit inside the card's own onClick={handleProductView}, so
    // without stopPropagation the click adds the product AND navigates away to
    // its detail page - the same reason the add-to-cart button below stops it.
    const handleAddToWishlist = (e) => {
        e.stopPropagation();
        dispatch(addToWishlist(productId, toast));
    };

    const handleAddToCompare = (e) => {
        e.stopPropagation();
        if (compareList.length >= 3) {
            toast.error(t("compareLimit"));
            return;
        }
        dispatch(addToCompare({ productId, productName, image, description, quantity, price, discount, specialPrice, averageRating, reviewCount, categoryName }));
        toast.success(t("addedToCompare"));
    };

    const handleProductView = () => {
        if (!about) {
            navigate(`/${lang}/products/${productId}`);
        }
    };

    const addToCartHandler = (cartItems) => {
        dispatch(addToCart(cartItems, 1, toast, navigate));
    };

    const hasDiscount = Boolean(specialPrice) && Number(specialPrice) < Number(price);
    const percentOff = hasDiscount
        ? Math.round((1 - Number(specialPrice) / Number(price)) * 100)
        : 0;
    const displayPrice = Number(specialPrice || price);

    const priceBlock = (
        <div className="flex flex-col leading-tight">
            {hasDiscount && (
                <span className="tabular text-xs text-gray-400 line-through dark:text-gray-500">
                    ${Number(price).toFixed(2)}
                </span>
            )}
            <span className="tabular text-xl font-bold tracking-tight text-gray-900 dark:text-white">
                ${displayPrice.toFixed(2)}
            </span>
        </div>
    );

    return (
        <div className="card group flex flex-col">
            <div
                onClick={handleProductView}
                className="relative w-full overflow-hidden aspect-3/2 bg-gray-100 dark:bg-gray-800">
                <img
                    className="w-full h-full object-cover cursor-pointer transition-transform duration-500 ease-out group-hover:scale-105"
                    src={image}
                    alt={productName}
                    loading="lazy"
                />

                {percentOff > 0 && (
                    <span className="absolute top-3 left-3 rounded-full bg-sale px-2.5 py-1 text-xs font-bold text-white shadow-sm">
                        -{percentOff}%
                    </span>
                )}

                {!isAvailable && (
                    <div className="absolute inset-0 flex items-center justify-center bg-white/70 backdrop-blur-[2px] dark:bg-gray-950/70">
                        <span className="rounded-full bg-gray-900/90 px-3 py-1 text-xs font-semibold uppercase tracking-wider text-white">
                            {t("outOfStock")}
                        </span>
                    </div>
                )}

                {!about && (
                    <div className="absolute top-3 right-3 flex flex-col gap-2">
                        {user?.id && !isAdmin && (
                            <button
                                onClick={handleAddToWishlist}
                                data-testid="wishlist-button"
                                className={`grid h-9 w-9 place-items-center rounded-full bg-white/95 shadow-sm ring-1 ring-black/5 backdrop-blur transition
                                    ${isInWishlist ? "text-red-500" : "text-gray-500 hover:text-red-500"}
                                    dark:bg-gray-900/90 dark:ring-white/10`}
                                title={isInWishlist ? t("alreadyInWishlist") : t("addToWishlist")}
                                disabled={isInWishlist}
                            >
                                <FaHeart />
                            </button>
                        )}
                        <button
                            onClick={handleAddToCompare}
                            data-testid="compare-button"
                            className={`grid h-9 w-9 place-items-center rounded-full bg-white/95 shadow-sm ring-1 ring-black/5 backdrop-blur transition
                                ${isInCompare ? "text-brand-600" : "text-gray-500 hover:text-brand-600"}
                                dark:bg-gray-900/90 dark:ring-white/10`}
                            title={isInCompare ? t("inCompareList") : t("addToCompare")}
                            disabled={isInCompare}
                        >
                            {isInCompare ? <FaCheck /> : <FaBalanceScale />}
                        </button>
                    </div>
                )}
            </div>

            <div className="flex flex-1 flex-col gap-2 p-4">
                {categoryName && (
                    <span className="text-[11px] font-semibold uppercase tracking-wider text-gray-400 dark:text-gray-500">
                        {categoryName}
                    </span>
                )}

                <h2
                    onClick={handleProductView}
                    data-testid="product-name"
                    className="cursor-pointer text-base font-semibold leading-snug text-gray-900 transition-colors hover:text-brand-600 dark:text-white dark:hover:text-brand-300">
                    {truncateText(productName, 50)}
                </h2>

                {reviewCount > 0 && (
                    <div className="flex items-center gap-1.5">
                        <div className="flex items-center gap-0.5">
                            {[1, 2, 3, 4, 5].map((star) => {
                                const rating = averageRating || 0;
                                if (rating >= star) {
                                    return <FaStar key={star} className="text-amber-400 text-xs" />;
                                } else if (rating >= star - 0.5) {
                                    return <FaStarHalfAlt key={star} className="text-amber-400 text-xs" />;
                                } else {
                                    return <FaStar key={star} className="text-gray-200 text-xs dark:text-gray-700" />;
                                }
                            })}
                        </div>
                        <span className="tabular text-xs text-gray-500 dark:text-gray-400">
                            {averageRating?.toFixed(1)} ({reviewCount})
                        </span>
                    </div>
                )}

                <p className="line-clamp-2 min-h-10 text-sm leading-relaxed text-gray-500 dark:text-gray-400">
                    {truncateText(description, 80)}
                </p>

                {!about && !isAdmin && (
                    <div className="mt-auto flex items-end justify-between gap-3 pt-2">
                        {priceBlock}
                        <button
                            disabled={!isAvailable || btnLoader}
                            onClick={(e) => { e.stopPropagation(); addToCartHandler({ image, productName, description, specialPrice, price, productId, quantity }); }}
                            data-testid="add-to-cart-button"
                            className="btn-primary flex items-center justify-center gap-2 px-4 py-2.5 text-sm">
                            <FaShoppingCart className="text-sm" />
                            <span>{isAvailable ? t("addToCart") : t("outOfStock")}</span>
                        </button>
                    </div>
                )}

                {!about && isAdmin && (
                    <div className="mt-auto pt-2">{priceBlock}</div>
                )}
            </div>
        </div>
    );
};

export default ProductCard;