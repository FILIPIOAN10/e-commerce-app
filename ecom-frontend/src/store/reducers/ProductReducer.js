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
    bestSellers: [],
    newArrivals: [],
    onSaleProducts: [],
    compareList: [],
};

export const productReducer = (state = initialState, action ) => {
    switch (action.type){
        case "FETCH_PRODUCTS":
            return {
                ...state,
                products : action.payload,
                pagination :{
                    ...state.pagination,
                    pageNumber : action.pageNumber,
                    pageSize : action.pageSize,
                    totalElements : action.totalElements,
                    totalPages : action.totalPages,
                    lastPage : action.lastPage,
                },

            };
        

        case "DELETE_CATEGORY_SUCCESS": {
            const nextCategories = state.categories
                ? state.categories.filter((category) =>
                        category.categoryId !== action.payload && category.id !== action.payload
            )
            : state.categories;
            const deletedFromCurrentPage = state.categories?.length !==nextCategories?.length;

            return {
                ...state,
                categories: nextCategories,
                pagination: {
                    ...state.pagination,
                    totalElements: deletedFromCurrentPage && state.pagination.totalElements !==undefined
                    ? Math.max(state.pagination.totalElements -1,0)
                    : state.pagination.totalElements,
                },
            };
        
        }
        case "DELETE_PRODUCT_SUCCESS": {
            const nextProducts = state.products
                ? state.products.filter( (product) =>
                      product.productId !== action.payload && product.id !== action.payload
            )
            : state.products;
            const deletedFromCurrentPage = state.products?.length !== nextProducts?.length;
            return{
                ...state,
                products:nextProducts,
                pagination :{
                    ...state.pagination,
                    totalElements: deletedFromCurrentPage && state.pagination.totalElements !==undefined
                    ? Math.max(state.pagination.totalElements-1,0)
                    :state.pagination.totalElements,
                },
            };
        }

        case "FETCH_LOW_STOCK_PRODUCTS":
            return {
                ...state,
                lowStockProducts: action.payload,
                pagination: {
                    ...state.pagination,
                    pageNumber: action.pageNumber,
                    pageSize: action.pageSize,
                    totalElements: action.totalElements,
                    totalPages: action.totalPages,
                    lastPage: action.lastPage,
                },
            };

        case "SET_LOW_STOCK_COUNT":
            return {
                ...state,
                lowStockCount: action.payload,
            };

        case "FETCH_CATEGORIES":
            return {
                ...state,
                categories : action.payload,
                pagination :{
                    ...state.pagination,
                    pageNumber : action.pageNumber,
                    pageSize : action.pageSize,
                    totalElements : action.totalElements,
                    totalPages : action.totalPages,
                    lastPage : action.lastPage,
                },

            };
        case "FETCH_PRODUCT":
            return {
                ...state,
                selectedProduct: action.payload,
            };
        case "CLEAR_SELECTED_PRODUCT":
            return {
                ...state,
                selectedProduct: null,
            };
        case "FETCH_RECENTLY_VIEWED":
            return {
                ...state,
                recentlyViewed: action.payload,
            };

        case "SET_RECOMMENDED_PRODUCTS":
            return {
                ...state,
                recommendedProducts: action.payload,
            };

        case "SET_SIMILAR_PRODUCTS":
            return {
                ...state,
                similarProducts: action.payload,
            };

        case "SET_BEST_SELLERS":
            return {
                ...state,
                bestSellers: action.payload,
            };

        case "SET_NEW_ARRIVALS":
            return {
                ...state,
                newArrivals: action.payload,
            };

        case "SET_ON_SALE":
            return {
                ...state,
                onSaleProducts: action.payload,
            };

        case "ADD_TO_COMPARE": {
            const exists = state.compareList.some((p) => p.productId === action.payload.productId);
            if (exists) return state;
            if (state.compareList.length >= 3) return state;
            return {
                ...state,
                compareList: [...state.compareList, action.payload],
            };
        }

        case "REMOVE_FROM_COMPARE":
            return {
                ...state,
                compareList: state.compareList.filter((p) => p.productId !== action.payload),
            };

        case "CLEAR_COMPARE":
            return {
                ...state,
                compareList: [],
            };

        default:
            return state;
    }
};