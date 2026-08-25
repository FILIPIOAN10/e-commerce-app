import api from "../../api/api";

export const addToWishlist = (productId, toast) => async (dispatch) => {
    try {
        const { data } = await api.post(`/users/wishlist/${productId}`);
        dispatch({ type: "addToWishlistSuccess", payload: { productId } });
        toast?.success(data.message);
    } catch (error) {
        const msg = error?.response?.data?.message || "Failed to add to wishlist";
        toast?.error(msg);
    }
};

export const removeFromWishlist = (productId, toast) => async (dispatch) => {
    try {
        const { data } = await api.delete(`/users/wishlist/${productId}`);
        dispatch({ type: "removeFromWishlistSuccess", payload: productId });
        toast?.success(data.message);
    } catch (error) {
        const msg = error?.response?.data?.message || "Failed to remove from wishlist";
        toast?.error(msg);
    }
};

export const fetchWishlist = (pageNumber = 0, pageSize = 10) => async (dispatch) => {
    try {
        dispatch({ type: "wishlistError", payload: null });
        const { data } = await api.get(`/users/wishlist?pageNumber=${pageNumber}&pageSize=${pageSize}`);
        dispatch({ type: "fetchWishlistSuccess", payload: data.content });
    } catch (error) {
        const msg = error?.response?.data?.message || "Failed to fetch wishlist";
        dispatch({ type: "wishlistError", payload: msg });
    }
};
