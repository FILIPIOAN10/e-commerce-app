const initialState = {
    wishlist: [],
    isLoading: false,
    error: null,
};

const wishlistReducer = (state = initialState, action) => {
    switch (action.type) {
        case "addToWishlistSuccess":
            return {
                ...state,
                wishlist: [...state.wishlist, action.payload],
            };
        case "removeFromWishlistSuccess":
            return {
                ...state,
                wishlist: state.wishlist.filter(
                    (item) => String(item.productId) !== String(action.payload)
                ),
            };
        case "fetchWishlistSuccess":
            return {
                ...state,
                wishlist: action.payload,
                error: null,
            };
        case "wishlistError":
            return {
                ...state,
                error: action.payload,
            };
        case "clearWishlist":
            return {
                ...state,
                wishlist: [],
            };
        default:
            return state;
    }
};

export default wishlistReducer;
