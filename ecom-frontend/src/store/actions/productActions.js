import api from "../../api/api";

export const fetchProducts = (queryString = "") => async (dispatch) => {
    try {
        dispatch({ type: "IS_FETCHING" });
        const searchParams = new URLSearchParams(queryString);
        const keyword = searchParams.get("keyword");
        const category = searchParams.get("category");
        let requestUrl = `/public/products?${queryString}`;
        if (keyword && !category) {
            searchParams.delete("keyword");
            searchParams.set("q", keyword);
            searchParams.set("semantic", "true");
            requestUrl = `/public/products/search?${searchParams.toString()}`;
        }
        const { data } = await api.get(requestUrl);
        dispatch({
            type: "FETCH_PRODUCTS",
            payload: data.content,
            pageNumber: data.pageNumber,
            pageSize: data.pageSize,
            totalElements: data.totalElements,
            totalPages: data.totalPages,
            lastPage: data.lastPage,
        });
        dispatch({ type: "IS_SUCCESS" });
    } catch (error) {
        dispatch({
            type: "IS_ERROR",
            payload: error?.response?.data?.message || "Failed to fetch products",
        });
    }
};

export const fetchCategories = (queryString = "") => async (dispatch) => {
    try {
        dispatch({ type: "CATEGORY_LOADER" });
        const requestUrl = queryString ? `/public/categories?${queryString}` : "/public/categories";
        const { data } = await api.get(requestUrl);
        dispatch({
            type: "FETCH_CATEGORIES",
            payload: data.content,
            pageNumber: data.pageNumber,
            pageSize: data.pageSize,
            totalElements: data.totalElements,
            totalPages: data.totalPages,
            lastPage: data.lastPage,
        });
        dispatch({ type: "CATEGORY_SUCCESS" });
    } catch (error) {
        dispatch({
            type: "IS_ERROR",
            payload: error?.response?.data?.message || "Failed to fetch categories",
        });
    }
};

export const fetchProductById = (productId) => async (dispatch) => {
    try {
        dispatch({ type: "IS_FETCHING" });
        const { data } = await api.get(`/public/products/${productId}`);
        dispatch({ type: "FETCH_PRODUCT", payload: data });
        dispatch({ type: "IS_SUCCESS" });
    } catch (error) {
        dispatch({
            type: "IS_ERROR",
            payload: error?.response?.data?.message || "Failed to fetch product",
        });
    }
};

export const recordProductView = (productId) => async () => {
    try {
        await api.post(`/user/products/${productId}/view`);
    } catch {
        // silently ignore - product view tracking is non-critical
    }
};

export const fetchRecentlyViewedProducts = () => async (dispatch) => {
    try {
        const { data } = await api.get("/user/products/recently-viewed");
        dispatch({ type: "FETCH_RECENTLY_VIEWED", payload: data });
    } catch (error) {
        dispatch({ type: "IS_ERROR", payload: error?.response?.data?.message || "Failed to fetch recently viewed" });
    }
};

export const fetchRecommendedProducts = (limit = 8) => async (dispatch) => {
    try {
        const { data } = await api.get(`/user/recommendations?limit=${limit}`);
        dispatch({ type: "SET_RECOMMENDED_PRODUCTS", payload: data });
        return data;
    } catch {
        dispatch({ type: "SET_RECOMMENDED_PRODUCTS", payload: [] });
    }
};

export const fetchSimilarProducts = (productId, limit = 4) => async (dispatch) => {
    try {
        const { data } = await api.get(`/public/products/${productId}/similar?limit=${limit}`);
        dispatch({ type: "SET_SIMILAR_PRODUCTS", payload: data });
        return data;
    } catch {
        dispatch({ type: "SET_SIMILAR_PRODUCTS", payload: [] });
    }
};

export const fetchFrequentlyBoughtTogether = (productId, limit = 4) => async (dispatch) => {
    try {
        const { data } = await api.get(`/public/products/${productId}/frequently-bought-together?limit=${limit}`);
        dispatch({ type: "SET_FREQUENTLY_BOUGHT_TOGETHER", payload: data });
        return data;
    } catch {
        dispatch({ type: "SET_FREQUENTLY_BOUGHT_TOGETHER", payload: [] });
    }
};

export const fetchBestSellers = (limit = 8) => async (dispatch) => {
    try {
        const { data } = await api.get(`/public/products/featured?type=best-sellers&limit=${limit}`);
        dispatch({ type: "SET_BEST_SELLERS", payload: data });
    } catch (error) {
        dispatch({ type: "IS_ERROR", payload: error?.response?.data?.message || "Failed to fetch best sellers" });
        dispatch({ type: "SET_BEST_SELLERS", payload: [] });
    }
};

export const fetchNewArrivals = (limit = 8) => async (dispatch) => {
    try {
        const { data } = await api.get(`/public/products/featured?type=new-arrivals&limit=${limit}`);
        dispatch({ type: "SET_NEW_ARRIVALS", payload: data });
    } catch (error) {
        dispatch({ type: "IS_ERROR", payload: error?.response?.data?.message || "Failed to fetch new arrivals" });
        dispatch({ type: "SET_NEW_ARRIVALS", payload: [] });
    }
};

export const fetchOnSaleProducts = (limit = 8) => async (dispatch) => {
    try {
        const { data } = await api.get(`/public/products/featured?type=on-sale&limit=${limit}`);
        dispatch({ type: "SET_ON_SALE", payload: data });
    } catch (error) {
        dispatch({ type: "IS_ERROR", payload: error?.response?.data?.message || "Failed to fetch on-sale products" });
        dispatch({ type: "SET_ON_SALE", payload: [] });
    }
};

export const getAllCategoriesDashboard = (queryString = "") => async (dispatch) => {
    dispatch({ type: "CATEGORY_LOADER" });
    try {
        const requestUrl = queryString ? `/public/categories?${queryString}` : "/public/categories";
        const { data } = await api.get(requestUrl);
        dispatch({
            type: "FETCH_CATEGORIES",
            payload: data["content"],
            pageNumber: data["pageNumber"],
            pageSize: data["pageSize"],
            totalElements: data["totalElements"],
            totalPages: data["totalPages"],
            lastPage: data["lastPage"],
        });

        dispatch({ type: "CATEGORY_SUCCESS" });
    } catch (err) {
        dispatch({
            type: "IS_ERROR",
            payload: err?.response?.data?.message || "Failed to fetch categories",
        });
    }
};

export const addToCompare = (product) => (dispatch, getState) => {
    const { compareList } = getState().products;
    if (compareList.length >= 3) return;
    if (compareList.some((p) => p.productId === product.productId)) return;
    dispatch({ type: "ADD_TO_COMPARE", payload: product });
    localStorage.setItem("compareItems", JSON.stringify(getState().products.compareList));
};

export const removeFromCompare = (productId) => (dispatch, getState) => {
    dispatch({ type: "REMOVE_FROM_COMPARE", payload: productId });
    localStorage.setItem("compareItems", JSON.stringify(getState().products.compareList));
};

export const clearCompare = () => (dispatch, getState) => {
    dispatch({ type: "CLEAR_COMPARE" });
    localStorage.setItem("compareItems", JSON.stringify(getState().products.compareList));
};

