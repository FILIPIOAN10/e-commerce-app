import { useEffect, useState } from "react";
import { useParams, Link } from "react-router-dom";
import { useDispatch, useSelector } from "react-redux";
import toast from "react-hot-toast";
import {
  FaShoppingCart,
  FaHeart,
  FaRegHeart,
  FaBalanceScale,
  FaCheck,
  FaStar,
  FaStarHalfAlt,
  FaChevronLeft,
  FaChevronRight,
  FaBoxOpen,
} from "react-icons/fa";
import Skeleton from "../shared/Skeleton";
import Breadcrumb from "../shared/Breadcrumb";
import ReviewsSection from "../shared/ReviewsSection";
import QuestionsSection from "../shared/QuestionsSection";
import SimilarProducts from "../shared/SimilarProducts";
import FrequentlyBoughtTogether from "../shared/FrequentlyBoughtTogether";
import TrustBadges from "../shared/TrustBadges";
import { Helmet } from "react-helmet-async";
import {
  fetchProductById,
  recordProductView,
  addToCart,
  addToWishlist,
  addToCompare,
} from "../../store/actions";

const ProductDetail = () => {
  const { productId } = useParams();
  const dispatch = useDispatch();

  const product = useSelector((state) => state.products.selectedProduct);
  const { isLoading, errorMessage } = useSelector((state) => state.errors);
  const { user } = useSelector((state) => state.auth);
  const { wishlist } = useSelector((state) => state.wishlist);
  const { compareList } = useSelector((state) => state.products);

  const [selectedImage, setSelectedImage] = useState(0);
  const [activeTab, setActiveTab] = useState("reviews");

  const isAdmin = Boolean(user?.roles?.includes("ROLE_ADMIN"));
  const isAvailable = product && Number(product.quantity) > 0;

  useEffect(() => {
    if (productId) {
      dispatch(fetchProductById(productId));
    }
    return () => {
      dispatch({ type: "CLEAR_SELECTED_PRODUCT" });
    };
  }, [productId, dispatch]);

  useEffect(() => {
    if (product?.productId) {
      if (user) {
        dispatch(recordProductView(product.productId));
      }
      setSelectedImage(0);
    }
  }, [product?.productId, user, dispatch]);

  if (isLoading || (!product && !errorMessage)) {
    return (
      <div className="min-h-screen bg-gray-50 dark:bg-gray-950 px-4 sm:px-6 lg:px-8 py-8">
        <Skeleton variant="detail" />
      </div>
    );
  }

  if (errorMessage || !product) {
    return (
      <div className="min-h-screen flex flex-col items-center justify-center bg-gray-50 dark:bg-gray-950 px-4">
        <FaBoxOpen className="text-6xl text-gray-400 mb-4" />
        <h2 className="text-2xl font-semibold text-gray-800 dark:text-white mb-2">
          Product not found
        </h2>
        <p className="text-gray-500 dark:text-gray-400 mb-6 text-center">
          {errorMessage || "We couldn't find the product you were looking for."}
        </p>
        <Link
          to="/products"
          className="px-5 py-2.5 bg-blue-500 hover:bg-blue-600 text-white rounded-lg font-medium transition"
        >
          Back to products
        </Link>
      </div>
    );
  }

  const {
    productName,
    categoryName,
    description,
    quantity,
    price,
    specialPrice,
    discount,
    averageRating,
    reviewCount,
    images,
    image,
  } = product;

  const galleryImages =
    images && images.length > 0 ? images : image ? [image] : [];
  const hasGallery = galleryImages.length > 1;

  const isInWishlist = wishlist?.some((w) => w.productId === product.productId);
  const isInCompare = compareList?.some((p) => p.productId === product.productId);

  const handleAddToCart = () => {
    if (!isAvailable) {
      toast.error("Out of stock");
      return;
    }
    dispatch(
      addToCart(
        {
          productId: product.productId,
          productName,
          image: galleryImages[0] || image,
          description,
          price,
          specialPrice,
          quantity,
        },
        1,
        toast
      )
    );
  };

  const handleAddToWishlist = () => {
    if (isInWishlist) return;
    dispatch(addToWishlist(product.productId, toast));
  };

  const handleAddToCompare = () => {
    if (isInCompare) return;
    if (compareList.length >= 3) {
      toast.error("You can compare up to 3 products");
      return;
    }
    dispatch(
      addToCompare({
        productId: product.productId,
        productName,
        image: galleryImages[0] || image,
        description,
        quantity,
        price,
        discount,
        specialPrice,
        averageRating,
        reviewCount,
        categoryName,
      })
    );
    toast.success("Added to compare");
  };

  const handlePrevImage = () => {
    setSelectedImage((prev) =>
      prev === 0 ? galleryImages.length - 1 : prev - 1
    );
  };

  const handleNextImage = () => {
    setSelectedImage((prev) =>
      prev === galleryImages.length - 1 ? 0 : prev + 1
    );
  };

  const displayPrice = Number(specialPrice || price).toFixed(2);
  const hasDiscount =
    specialPrice && Number(specialPrice) > 0 && Number(specialPrice) < Number(price);

  const tabs = [
    { key: "reviews", label: `Reviews ${reviewCount ? `(${reviewCount})` : ""}` },
    { key: "qna", label: "Q&A" },
    { key: "similar", label: "Similar Products" },
    { key: "fbt", label: "Frequently Bought" },
  ];

  const breadcrumbItems = [
    { label: "Home", path: "/" },
    { label: "Products", path: "/products" },
    ...(categoryName
      ? [
          {
            label: categoryName,
            path: `/products?category=${encodeURIComponent(categoryName)}`,
          },
        ]
      : []),
    { label: productName },
  ];

  return (
    <div className="min-h-screen bg-gray-50 dark:bg-gray-950 dark:text-gray-100">
      <Helmet>
        <title>{`${productName} | E-Commerce`}</title>
        <meta name="description" content={description?.substring(0, 160) || productName} />
        <meta property="og:title" content={productName} />
        <meta property="og:description" content={description?.substring(0, 160) || productName} />
        <meta property="og:type" content="product" />
        {image && <meta property="og:image" content={image} />}
      </Helmet>
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8">
        <Breadcrumb items={breadcrumbItems} />

        <div className="grid grid-cols-1 lg:grid-cols-2 gap-10 mt-6">
          {/* Image gallery */}
          <div className="space-y-4">
            <div className="relative aspect-[4/3] bg-white dark:bg-gray-900 rounded-2xl overflow-hidden shadow-md border border-gray-100 dark:border-gray-800 group">
              {galleryImages.length > 0 ? (
                <>
                  <img
                    src={galleryImages[selectedImage]}
                    alt={productName}
                    className="w-full h-full object-contain p-4"
                  />
                  {hasGallery && (
                    <>
                      <button
                        onClick={handlePrevImage}
                        className="absolute left-3 top-1/2 -translate-y-1/2 bg-white/90 dark:bg-gray-800/90 hover:bg-white dark:hover:bg-gray-700 p-2 rounded-full shadow transition opacity-0 group-hover:opacity-100"
                        aria-label="Previous image"
                      >
                        <FaChevronLeft className="text-gray-700 dark:text-gray-200" />
                      </button>
                      <button
                        onClick={handleNextImage}
                        className="absolute right-3 top-1/2 -translate-y-1/2 bg-white/90 dark:bg-gray-800/90 hover:bg-white dark:hover:bg-gray-700 p-2 rounded-full shadow transition opacity-0 group-hover:opacity-100"
                        aria-label="Next image"
                      >
                        <FaChevronRight className="text-gray-700 dark:text-gray-200" />
                      </button>
                    </>
                  )}
                </>
              ) : (
                <div className="w-full h-full flex items-center justify-center text-gray-400">
                  <FaBoxOpen className="text-6xl" />
                </div>
              )}
            </div>

            {hasGallery && (
              <div className="flex gap-3 overflow-x-auto pb-2">
                {galleryImages.map((img, index) => (
                  <button
                    key={index}
                    onClick={() => setSelectedImage(index)}
                    className={`flex-shrink-0 w-20 h-20 rounded-xl overflow-hidden border-2 transition ${
                      selectedImage === index
                        ? "border-blue-500 ring-2 ring-blue-200 dark:ring-blue-900"
                        : "border-transparent opacity-70 hover:opacity-100"
                    }`}
                  >
                    <img
                      src={img}
                      alt={`${productName} ${index + 1}`}
                      className="w-full h-full object-cover"
                    />
                  </button>
                ))}
              </div>
            )}
          </div>

          {/* Product info */}
          <div className="flex flex-col">
            {categoryName && (
              <Link
                to={`/products?category=${encodeURIComponent(categoryName)}`}
                className="text-sm font-medium text-blue-500 hover:text-blue-600 w-fit mb-2"
              >
                {categoryName}
              </Link>
            )}

            <h1 className="text-3xl sm:text-4xl font-bold text-gray-900 dark:text-white mb-3">
              {productName}
            </h1>

            <div className="flex flex-wrap items-center gap-4 mb-4">
              {reviewCount > 0 ? (
                <div className="flex items-center gap-2 bg-amber-50 dark:bg-amber-900/20 px-3 py-1.5 rounded-full">
                  <div className="flex items-center gap-0.5">
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
                  <span className="text-sm font-medium text-gray-700 dark:text-gray-300">
                    {Number(averageRating).toFixed(1)} ({reviewCount})
                  </span>
                </div>
              ) : (
                <span className="text-sm text-gray-500 dark:text-gray-400">
                  No reviews yet
                </span>
              )}

              <span
                className={`inline-flex items-center gap-1.5 px-3 py-1.5 rounded-full text-sm font-medium ${
                  isAvailable
                    ? "bg-emerald-50 text-emerald-700 dark:bg-emerald-900/20 dark:text-emerald-400"
                    : "bg-rose-50 text-rose-700 dark:bg-rose-900/20 dark:text-rose-400"
                }`}
              >
                {isAvailable ? <FaCheck size={12} /> : null}
                {isAvailable ? `${quantity} in stock` : "Out of stock"}
              </span>
            </div>

            <div className="flex items-end gap-4 mb-6">
              <span className="text-4xl font-bold text-slate-800 dark:text-white">
                ${displayPrice}
              </span>
              {hasDiscount && (
                <>
                  <span className="text-xl text-gray-400 line-through mb-1">
                    ${Number(price).toFixed(2)}
                  </span>
                  {discount > 0 && (
                    <span className="mb-1.5 bg-rose-100 text-rose-700 dark:bg-rose-900/30 dark:text-rose-300 px-2.5 py-1 rounded-md text-sm font-semibold">
                      -{Number(discount).toFixed(0)}%
                    </span>
                  )}
                </>
              )}
            </div>

            <p className="text-gray-600 dark:text-gray-300 leading-relaxed mb-8">
              {description}
            </p>

            {/* Actions */}
            <div className="flex flex-wrap items-center gap-3 mb-8">
              {!isAdmin && (
                <button
                  onClick={handleAddToCart}
                  disabled={!isAvailable}
                  className={`flex items-center gap-2 px-6 py-3 rounded-xl font-semibold transition ${
                    isAvailable
                      ? "bg-blue-500 hover:bg-blue-600 text-white shadow-lg shadow-blue-500/25"
                      : "bg-gray-300 text-gray-500 cursor-not-allowed"
                  }`}
                >
                  <FaShoppingCart />
                  {isAvailable ? "Add to Cart" : "Out of Stock"}
                </button>
              )}

              {user && !isAdmin && (
                <button
                  onClick={handleAddToWishlist}
                  disabled={isInWishlist}
                  className={`flex items-center gap-2 px-5 py-3 rounded-xl font-semibold border transition ${
                    isInWishlist
                      ? "bg-rose-50 border-rose-200 text-rose-500 cursor-default"
                      : "border-gray-300 dark:border-gray-700 text-gray-700 dark:text-gray-200 hover:border-rose-300 hover:text-rose-500"
                  }`}
                >
                  {isInWishlist ? <FaHeart /> : <FaRegHeart />}
                  {isInWishlist ? "Saved" : "Wishlist"}
                </button>
              )}

              <button
                onClick={handleAddToCompare}
                disabled={isInCompare}
                className={`flex items-center gap-2 px-5 py-3 rounded-xl font-semibold border transition ${
                  isInCompare
                    ? "bg-blue-50 border-blue-200 text-blue-600 cursor-default"
                    : "border-gray-300 dark:border-gray-700 text-gray-700 dark:text-gray-200 hover:border-blue-300 hover:text-blue-600"
                }`}
              >
                {isInCompare ? <FaCheck /> : <FaBalanceScale />}
                {isInCompare ? "In Compare" : "Compare"}
              </button>
            </div>

            {/* Trust badges */}
            <TrustBadges />
          </div>
        </div>

        {/* Tabs */}
        <div className="mt-14">
          <div className="flex border-b border-gray-200 dark:border-gray-800 mb-6 overflow-x-auto">
            {tabs.map((tab) => (
              <button
                key={tab.key}
                onClick={() => setActiveTab(tab.key)}
                className={`px-5 py-3 text-sm sm:text-base font-semibold whitespace-nowrap transition border-b-2 ${
                  activeTab === tab.key
                    ? "border-blue-500 text-blue-600 dark:text-blue-400"
                    : "border-transparent text-gray-500 dark:text-gray-400 hover:text-gray-700 dark:hover:text-gray-300"
                }`}
              >
                {tab.label}
              </button>
            ))}
          </div>

          <div className="min-h-[200px]">
            {activeTab === "reviews" && (
              <ReviewsSection productId={product.productId} />
            )}
            {activeTab === "qna" && (
              <QuestionsSection productId={product.productId} />
            )}
            {activeTab === "similar" && (
              <SimilarProducts productId={product.productId} />
            )}
            {activeTab === "fbt" && (
              <FrequentlyBoughtTogether productId={product.productId} />
            )}
          </div>
        </div>
      </div>
    </div>
  );
};

export default ProductDetail;
