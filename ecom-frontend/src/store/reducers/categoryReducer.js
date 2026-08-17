export const categoryReducer = (state, action) => {
    const categories = state?.categories ?? null;
    const pagination = state?.pagination ?? {};

    switch (action.type) {
        case "FETCH_CATEGORIES":
            return {
                categories: action.payload,
                pagination: {
                    ...pagination,
                    pageNumber: action.pageNumber,
                    pageSize: action.pageSize,
                    totalElements: action.totalElements,
                    totalPages: action.totalPages,
                    lastPage: action.lastPage,
                },
            };

        case "DELETE_CATEGORY_SUCCESS": {
            const nextCategories = categories
                ? categories.filter((category) =>
                    category.categoryId !== action.payload && category.id !== action.payload
                )
                : categories;
            const deletedFromCurrentPage = categories?.length !== nextCategories?.length;

            return {
                categories: nextCategories,
                pagination: {
                    ...pagination,
                    totalElements: deletedFromCurrentPage && pagination.totalElements !== undefined
                        ? Math.max(pagination.totalElements - 1, 0)
                        : pagination.totalElements,
                },
            };
        }

        default:
            return { categories };
    }
};
