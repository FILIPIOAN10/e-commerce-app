export const productCatalogReducer = (state, action) => {
    const products = state?.products ?? null;
    const pagination = state?.pagination ?? {};

    switch (action.type) {
        case "FETCH_PRODUCTS":
            return {
                products: action.payload,
                pagination: {
                    ...pagination,
                    pageNumber: action.pageNumber,
                    pageSize: action.pageSize,
                    totalElements: action.totalElements,
                    totalPages: action.totalPages,
                    lastPage: action.lastPage,
                },
            };

        case "FETCH_FILTERED_PRODUCTS":
            return { products: action.payload };

        case "DELETE_PRODUCT_SUCCESS": {
            const nextProducts = products
                ? products.filter((product) =>
                    product.productId !== action.payload && product.id !== action.payload
                )
                : products;
            const deletedFromCurrentPage = products?.length !== nextProducts?.length;

            return {
                products: nextProducts,
                pagination: {
                    ...pagination,
                    totalElements: deletedFromCurrentPage && pagination.totalElements !== undefined
                        ? Math.max(pagination.totalElements - 1, 0)
                        : pagination.totalElements,
                },
            };
        }

        default:
            return { products };
    }
};
