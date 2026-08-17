import toast from "react-hot-toast";
import api from "../../api/api";
import { getAllCategoriesDashboard } from "./productActions";

export const dashboardProductsAction = (queryString, isAdmin) => async (dispatch, getState) => {
    try {
        dispatch({ type: "IS_FETCHING" });
        const { user } = getState().auth;
        const adminRequest = isAdmin ?? Boolean(user?.roles?.includes("ROLE_ADMIN"));
        const endpoint = adminRequest ? "/admin/products" : "/seller/products";
        const requestUrl = queryString ? `${endpoint}?${queryString}` : endpoint;
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
            payload: error?.response?.data?.message || "Failed to fetch dashboard products",
        });
    }
};

export const updateProductFromDashboard =
    (sendData, toast, reset, setLoader, setOpen, isAdmin) => async (dispatch) => {
        try {
            setLoader(true);

            const endpoint = isAdmin ? "/admin/products/" : "/seller/products/";
            await api.put(`${endpoint}${sendData.id}`, sendData);

            toast.success("Product update successful");

            await dispatch(dashboardProductsAction("", isAdmin));

            setOpen(false);
            reset();
        } catch (error) {
            toast.error(error?.response?.data?.description || error?.response?.data?.message || "Product update failed");
        } finally {
            setLoader(false);
        }
};

export const deleteProduct =
    (setLoader, productId, toast, setOpenDeleteModal, isAdmin, queryString = "") => async (dispatch, getState) => {

    try {
        setLoader(true);
        const endpoint = isAdmin ? "/admin/products/" : "/seller/products/";
        await api.delete(`${endpoint}${productId}`);
        dispatch({ type: "DELETE_PRODUCT_SUCCESS", payload: productId });
        toast.success("Product deleted successfully");

        setOpenDeleteModal(false);
        await dispatch(dashboardProductsAction(queryString, isAdmin));

    } catch (error) {
        toast.error(error?.response?.data?.message || "Some Error Occured");
    } finally {
        setLoader(false);
    }
};

export const addNewProductFromDashboard =
    (sendData, toast, reset, setLoader, setOpen, isAdmin) => async (dispatch, getState) => {
        try {
            setLoader(true);
            const endpoint = isAdmin ? "/admin/categories/" : "/seller/categories/";
            await api.post(`${endpoint}${sendData.categoryId}/product`,
                sendData
            );
            toast.success("Product created successfully");
            reset();
            setOpen(false);
            await dispatch(dashboardProductsAction("", isAdmin));
        } catch (error) {
            toast.error(error?.response?.data?.description || "Product creation failed");
        } finally {
            setLoader(false);
        }
};

export const updateProductImageFromDashboard =
    (formData, productId, toast, setLoader, setOpen, isAdmin) => async (dispatch) => {

        try {
            setLoader(true);
            const endpoint = isAdmin ? "/admin/products/" : "/seller/products/";
            await api.put(`${endpoint}${productId}/image`, formData);
            toast.success("Image update successful");
            setLoader(false);
            setOpen(false);
            await dispatch(dashboardProductsAction("", isAdmin));
        } catch (error) {

            toast.error(error?.response?.data?.description || "Product Image upload failed");
        }
};

export const updateCategoryDashboardAction =
    (sendData, setOpen, categoryID, reset, toast) =>
    async (dispatch, getState) => {
        try {
            dispatch({ type: "CATEGORY_LOADER" });

            await api.put(`/admin/categories/${categoryID}`, sendData);

            dispatch({ type: "CATEGORY_SUCCESS" });

            reset();
            toast.success("Category Update Successful");
            setOpen(false);
            await dispatch(getAllCategoriesDashboard());
        } catch (err) {
            toast.error(
                err?.response?.data?.categoryName || "Failed to update category"
            );

            dispatch({
                type: "IS_ERROR",
                payload: err?.response?.data?.message || "Internal Server Error",
            });
        }
    };

export const createCategoryDashboardAction =
    (sendData, setOpen, reset, toast) => async (dispatch, getState) => {
        try {
            dispatch({ type: "CATEGORY_LOADER" });
            await api.post("/admin/categories", sendData);
            dispatch({ type: "CATEGORY_SUCCESS" });
            reset();
            toast.success("Category Created Successful");
            setOpen(false);
            await dispatch(getAllCategoriesDashboard());
        } catch (err) {
            toast.error(
                err?.response?.data?.categoryName || "Failed to create new category"
            );

            dispatch({
                type: "IS_ERROR",
                payload: err?.response?.data?.message || "Internal Server Error",
            });
        }
    };

export const deleteCategoryDashboardAction =
    (setOpen, categoryID, toast) => async (dispatch, getState) => {
        try {
            dispatch({ type: "DELETE_CATEGORY_SUCCESS", payload: categoryID });
            dispatch({ type: "CATEGORY_LOADER" });

            await api.delete(`/admin/categories/${categoryID}`);

            dispatch({ type: "CATEGORY_SUCCESS" });

            toast.success("Category Delete Successful");
            setOpen(false);
        } catch (err) {
            toast.error(err?.response?.data?.message || "Failed to delete category");
            dispatch({
                type: "IS_ERROR",
                payload: err?.response?.data?.message || "Internal Server Error",
            });
        }

    };

export const getAllSellersDashboard =
    (queryString) => async (dispatch, getState) => {
        const { user } = getState().auth;
        try {
            dispatch({ type: "IS_FETCHING" });
            const { data } = await api.get(`/auth/sellers?${queryString}`);
            dispatch({
                type: "GET_SELLERS",
                payload: data["content"],
                pageNumber: data["pageNumber"],
                pageSize: data["pageSize"],
                totalElements: data["totalElements"],
                totalPages: data["totalPages"],
                lastPage: data["lastPage"],
            });

            dispatch({ type: "IS_SUCCESS" });
        } catch (err) {
            dispatch({
                type: "IS_ERROR",
                payload: err?.response?.data?.message || "Failed to fetch sellers data",
            });
        }
    };

export const addNewDashboardSeller =
    (sendData, toast, reset, setOpen, setLoader) => async (dispatch) => {
        try {
            setLoader(true);
            await api.post("/auth/signup", sendData);
            reset();
            toast.success("Seller registered successfully!");

            await dispatch(getAllSellersDashboard());
        } catch (err) {
            toast.error(
                err?.response?.data?.message ||
                err?.response?.data?.password ||
                "Internal Server Error"
            );
        } finally {
            setLoader(false);
            setOpen(false);
        }
    };

export const fetchLowStockProducts = (pageNumber = 0, pageSize = 10) => async (dispatch, getState) => {
    try {
        dispatch({ type: "IS_FETCHING" });
        const user = getState().auth.user;
        const isAdmin = user?.roles?.includes("ROLE_ADMIN");
        const endpoint = isAdmin
            ? `/admin/low-stock?pageNumber=${pageNumber}&pageSize=${pageSize}`
            : `/seller/low-stock?pageNumber=${pageNumber}&pageSize=${pageSize}`;
        const { data } = await api.get(endpoint);
        dispatch({
            type: "FETCH_LOW_STOCK_PRODUCTS",
            payload: data.content,
            pageNumber: data.pageNumber,
            pageSize: data.pageSize,
            totalElements: data.totalElements,
            totalPages: data.totalPages,
            lastPage: data.lastPage,
        });
        dispatch({ type: "IS_SUCCESS" });
    } catch (error) {
        dispatch({ type: "IS_ERROR", payload: error?.response?.data?.message });
    }
};

export const fetchLowStockCount = () => async (dispatch) => {
    try {
        const { data } = await api.get("/low-stock/count");
        dispatch({ type: "SET_LOW_STOCK_COUNT", payload: data });
    } catch (error) {
        dispatch({ type: "IS_ERROR", payload: error?.response?.data?.message || "Failed to fetch low stock count" });
    }
};

export const importProducts = (file, toast) => async (dispatch) => {
    try {
        dispatch({ type: "IS_FETCHING" });
        const formData = new FormData();
        formData.append("file", file);
        const { data } = await api.post('/admin/products/import', formData, {
            headers: { 'Content-Type': 'multipart/form-data' }
        });
        toast.success(data.message);
        dispatch({ type: "IS_SUCCESS" });
    } catch (error) {
        const msg = error?.response?.data?.message || "Failed to import products";
        toast.error(msg);
        dispatch({ type: "IS_ERROR", payload: msg });
    }
};

export const fetchPromoCampaigns = (pageNumber = 0, pageSize = 10) => async (dispatch) => {
    try {
        const { data } = await api.get(`/admin/promo-campaigns?pageNumber=${pageNumber}&pageSize=${pageSize}`);
        dispatch({ type: "FETCH_PROMO_CAMPAIGNS", payload: data });
    } catch (error) {
        dispatch({
            type: "IS_ERROR",
            payload: error?.response?.data?.message || "Failed to fetch promo campaigns"
        });
    }
};

export const createPromoCampaign = (payload, toast) => async (dispatch) => {
    try {
        const { data } = await api.post('/admin/promo-campaigns', payload);
        toast.success("Campaign created");
        dispatch(fetchPromoCampaigns());
        return data;
    } catch (error) {
        const msg = error?.response?.data?.message || "Failed to create campaign";
        toast.error(msg);
    }
};

export const updatePromoCampaign = (id, payload, toast) => async (dispatch) => {
    try {
        const { data } = await api.put(`/admin/promo-campaigns/${id}`, payload);
        toast.success("Campaign updated");
        dispatch(fetchPromoCampaigns());
        return data;
    } catch (error) {
        const msg = error?.response?.data?.message || "Failed to update campaign";
        toast.error(msg);
    }
};

export const deletePromoCampaign = (id, toast) => async (dispatch) => {
    try {
        await api.delete(`/admin/promo-campaigns/${id}`);
        toast.success("Campaign deleted");
        dispatch(fetchPromoCampaigns());
    } catch (error) {
        const msg = error?.response?.data?.message || "Failed to delete campaign";
        toast.error(msg);
    }
};
