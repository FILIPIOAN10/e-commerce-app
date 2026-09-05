export const productCatalogReducer = (state, action) => {
    const products = state?.products ?? null;
    const productPagination = state?.productPagination ?? {};

    switch (action.type) {
        case "FETCH_PRODUCTS":
            return {
                products: action.payload,
                productPagination: {
                    ...productPagination,
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
                productPagination: {
                    ...productPagination,
                    totalElements: deletedFromCurrentPage && productPagination.totalElements !== undefined
                        ? Math.max(productPagination.totalElements - 1, 0)
                        : productPagination.totalElements,
                },
            };
        }

        default:
            return { products };
    }
};
