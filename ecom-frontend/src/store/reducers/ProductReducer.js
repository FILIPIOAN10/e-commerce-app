import { productCatalogReducer } from "./productCatalogReducer";
import { productDetailReducer } from "./productDetailReducer";
import { categoryReducer } from "./categoryReducer";
import { productDiscoveryReducer } from "./productDiscoveryReducer";
import { compareReducer } from "./compareReducer";
import { lowStockReducer } from "./lowStockReducer";

const initialState = {
    products: null,
    selectedProduct: null,
    categories: null,
    lowStockProducts: null,
    lowStockCount: 0,
    pagination: {},
    recentlyViewed: [],
    recommendedProducts: [],
    similarProducts: [],
    frequentlyBoughtTogetherProducts: [],
    bestSellers: [],
    newArrivals: [],
    onSaleProducts: [],
    compareList: [],
};

export const productReducer = (state = initialState, action) => {
    const catalog = productCatalogReducer(state, action);
    const detail = productDetailReducer(state, action);
    const category = categoryReducer(state, action);
    const discovery = productDiscoveryReducer(state, action);
    const compare = compareReducer(state, action);
    const lowStock = lowStockReducer(state, action);

    return {
        ...state,
        ...catalog,
        ...detail,
        ...category,
        ...discovery,
        ...compare,
        ...lowStock,
    };
};
