export const productDiscoveryReducer = (state, action) => {
    const recentlyViewed = state?.recentlyViewed ?? [];
    const recommendedProducts = state?.recommendedProducts ?? [];
    const similarProducts = state?.similarProducts ?? [];
    const bestSellers = state?.bestSellers ?? [];
    const newArrivals = state?.newArrivals ?? [];
    const onSaleProducts = state?.onSaleProducts ?? [];

    switch (action.type) {
        case "FETCH_RECENTLY_VIEWED":
            return { ...{ recentlyViewed, recommendedProducts, similarProducts, bestSellers, newArrivals, onSaleProducts }, recentlyViewed: action.payload };

        case "SET_RECOMMENDED_PRODUCTS":
            return { ...{ recentlyViewed, recommendedProducts, similarProducts, bestSellers, newArrivals, onSaleProducts }, recommendedProducts: action.payload };

        case "SET_SIMILAR_PRODUCTS":
            return { ...{ recentlyViewed, recommendedProducts, similarProducts, bestSellers, newArrivals, onSaleProducts }, similarProducts: action.payload };

        case "SET_BEST_SELLERS":
            return { ...{ recentlyViewed, recommendedProducts, similarProducts, bestSellers, newArrivals, onSaleProducts }, bestSellers: action.payload };

        case "SET_NEW_ARRIVALS":
            return { ...{ recentlyViewed, recommendedProducts, similarProducts, bestSellers, newArrivals, onSaleProducts }, newArrivals: action.payload };

        case "SET_ON_SALE":
            return { ...{ recentlyViewed, recommendedProducts, similarProducts, bestSellers, newArrivals, onSaleProducts }, onSaleProducts: action.payload };

        default:
            return { recentlyViewed, recommendedProducts, similarProducts, bestSellers, newArrivals, onSaleProducts };
    }
};
