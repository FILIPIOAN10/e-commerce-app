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
    // One paginator per list. These were a single shared `pagination` key, and
    // because categoryReducer is merged after productCatalogReducer below, a
    // FETCH_CATEGORIES landing after a FETCH_PRODUCTS overwrote the catalog's
    // page count with the category list's. On /products, which fires both in
    // parallel, that regularly left a 12-page catalog showing one page.
    productPagination: {},
    categoryPagination: {},
    lowStockPagination: {},
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
    const slices = [
        productCatalogReducer(state, action),
        productDetailReducer(state, action),
        categoryReducer(state, action),
        productDiscoveryReducer(state, action),
        compareReducer(state, action),
        lowStockReducer(state, action),
    ];

    const next = Object.assign({}, state, ...slices);

    // Every sub-reducer echoes its own keys back for actions it does not handle,
    // so the merge above produces a new object on every dispatch in the app —
    // RTK Query's internal lifecycle actions, notifications, auth, all of it.
    // useSelector compares by reference, so that re-rendered every consumer of
    // state.products each time. Hand back the same reference when nothing moved.
    const changed = Object.keys(next).some((key) => next[key] !== state[key]);
    return changed ? next : state;
};
