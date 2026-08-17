export const productDetailReducer = (state, action) => {
    const selectedProduct = state?.selectedProduct ?? null;

    switch (action.type) {
        case "FETCH_PRODUCT":
            return { selectedProduct: action.payload };

        case "CLEAR_SELECTED_PRODUCT":
            return { selectedProduct: null };

        default:
            return { selectedProduct };
    }
};
