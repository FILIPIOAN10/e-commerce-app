export const compareReducer = (state, action) => {
    const compareList = state?.compareList ?? [];

    switch (action.type) {
        case "ADD_TO_COMPARE": {
            const exists = compareList.some((p) => p.productId === action.payload.productId);
            if (exists) return { compareList };
            if (compareList.length >= 3) return { compareList };
            return { compareList: [...compareList, action.payload] };
        }

        case "REMOVE_FROM_COMPARE":
            return { compareList: compareList.filter((p) => p.productId !== action.payload) };

        case "CLEAR_COMPARE":
            return { compareList: [] };

        default:
            return { compareList };
    }
};
