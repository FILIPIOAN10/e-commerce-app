export const lowStockReducer = (state, action) => {
    const lowStockProducts = state?.lowStockProducts ?? null;
    const lowStockCount = state?.lowStockCount ?? 0;
    const lowStockPagination = state?.lowStockPagination ?? {};

    switch (action.type) {
        case "FETCH_LOW_STOCK_PRODUCTS":
            return {
                lowStockProducts: action.payload,
                lowStockCount,
                lowStockPagination: {
                    ...lowStockPagination,
                    pageNumber: action.pageNumber,
                    pageSize: action.pageSize,
                    totalElements: action.totalElements,
                    totalPages: action.totalPages,
                    lastPage: action.lastPage,
                },
            };

        case "SET_LOW_STOCK_COUNT":
            return { lowStockProducts, lowStockCount: action.payload };

        default:
            return { lowStockProducts, lowStockCount };
    }
};
