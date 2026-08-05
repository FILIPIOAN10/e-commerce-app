const initialState = {
    products: null,
    categories: null,
    lowStockProducts: null,
    lowStockCount: 0,
    pagination: {},
    recentlyViewed: [],
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
        case "FETCH_RECENTLY_VIEWED":
            return {
                ...state,
                recentlyViewed: action.payload,
            };

        default:
            return state;
    }
};