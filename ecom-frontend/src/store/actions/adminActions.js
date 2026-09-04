import api from "../../api/api";
import { getAllCategoriesDashboard } from "./productActions";
import { apiSlice } from "../api/apiSlice";

// Mutations stay as thunks for now, but they refresh the list by
// invalidating the RTK Query cache rather than re-dispatching a read
// thunk. That keeps one owner of request status and lets the read
// thunks be deleted.

export const updateProductFromDashboard =
    (sendData, toast, reset, setLoader, setOpen, isAdmin) => async (dispatch) => {
        try {
            setLoader(true);

            const endpoint = isAdmin ? "/admin/products/" : "/seller/products/";
            await api.put(`${endpoint}${sendData.id}`, sendData);

            toast.success("Product update successful");

            dispatch(apiSlice.util.invalidateTags(["Product"]));

            setOpen(false);
            reset();
        } catch (error) {
            toast.error(error?.response?.data?.description || error?.response?.data?.message || "Product update failed");
        } finally {
            setLoader(false);
        }
};

// queryString is gone from the signature: it only existed to re-fetch the page
// the operator was on, which invalidating the Product tag now does for us.
export const deleteProduct =
    (setLoader, productId, toast, setOpenDeleteModal, isAdmin) => async (dispatch) => {

    try {
        setLoader(true);
        const endpoint = isAdmin ? "/admin/products/" : "/seller/products/";
        await api.delete(`${endpoint}${productId}`);
        dispatch({ type: "DELETE_PRODUCT_SUCCESS", payload: productId });
        toast.success("Product deleted successfully");

        setOpenDeleteModal(false);
        dispatch(apiSlice.util.invalidateTags(["Product"]));

    } catch (error) {
        toast.error(error?.response?.data?.message || "Some Error Occured");
    } finally {
        setLoader(false);
    }
};

export const addNewProductFromDashboard =
    (sendData, toast, reset, setLoader, setOpen, isAdmin) => async (dispatch) => {
        try {
            setLoader(true);
            const endpoint = isAdmin ? "/admin/categories/" : "/seller/categories/";
            await api.post(`${endpoint}${sendData.categoryId}/product`,
                sendData
            );
            toast.success("Product created successfully");
            reset();
            setOpen(false);
            dispatch(apiSlice.util.invalidateTags(["Product"]));
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
            dispatch(apiSlice.util.invalidateTags(["Product"]));
        } catch (error) {

            toast.error(error?.response?.data?.description || "Product Image upload failed");
        }
};

export const updateCategoryDashboardAction =
    (sendData, setOpen, categoryID, reset, toast) =>
    async (dispatch) => {
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
    (sendData, setOpen, reset, toast) => async (dispatch) => {
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
    (setOpen, categoryID, toast) => async (dispatch) => {
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

export const addNewDashboardSeller =
    (sendData, toast, reset, setOpen, setLoader) => async (dispatch) => {
        try {
            setLoader(true);
            await api.post("/auth/signup", sendData);
            reset();
            toast.success("Seller registered successfully!");

            dispatch(apiSlice.util.invalidateTags(["Seller"]));
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

export const createPromoCampaign = (payload, toast) => async (dispatch) => {
    try {
        const { data } = await api.post('/admin/promo-campaigns', payload);
        toast.success("Campaign created");
        dispatch(apiSlice.util.invalidateTags(["PromoCampaign"]));
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
        dispatch(apiSlice.util.invalidateTags(["PromoCampaign"]));
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
        dispatch(apiSlice.util.invalidateTags(["PromoCampaign"]));
    } catch (error) {
        const msg = error?.response?.data?.message || "Failed to delete campaign";
        toast.error(msg);
    }
};
