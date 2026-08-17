import api from "../../api/api";

export const addUpdateUserAddress =
    (sendData, toast, addressId, setOpenAddressModal) => async (dispatch) => {
    dispatch({ type: "BUTTON_LOADER" });
    try {
        if (!addressId) {
            await api.post("/addresses", sendData);
        } else {
            await api.put(`/addresses/${addressId}`, sendData);
        }
        dispatch(getUserAddresses());
        toast.success("Address saved successfully");
        dispatch({ type: "IS_SUCCESS" });
    } catch (error) {
        toast.error(error?.response?.data?.message || "Internal Server Error");
        dispatch({ type: "IS_ERROR", payload: null });
    } finally {
        setOpenAddressModal(false);
    }
};

export const deleteUserAddress =
    (toast, addressId, setOpenDeleteModal) => async (dispatch) => {
    try {
        dispatch({ type: "BUTTON_LOADER" });
        await api.delete(`/addresses/${addressId}`);
        dispatch({ type: "IS_SUCCESS" });
        dispatch(getUserAddresses());
        dispatch(clearCheckoutAddress());
        toast.success("Address deleted successfully");
    } catch (error) {
        dispatch({
            type: "IS_ERROR",
            payload: error?.response?.data?.message || "Some Error Occured",
        });
    } finally {
        setOpenDeleteModal(false);
    }
};

export const clearCheckoutAddress = () => {
    return {
        type: "REMOVE_CHECKOUT_ADDRESS",
    }
};

export const getUserAddresses = () => async (dispatch) => {
    try {
        dispatch({ type: "IS_FETCHING" });
        const { data } = await api.get(`/users/addresses`);
        dispatch({ type: "USER_ADDRESS", payload: data });
        dispatch({ type: "IS_SUCCESS" });
    } catch (error) {
        dispatch({ type: "IS_ERROR", payload: error?.response?.data?.message || "Failed to fetch user addresses" });
    }
};

export const selectedUserCheckoutAddress = (address) => {
    localStorage.setItem("CHECKOUT_ADDRESS", JSON.stringify(address));

    return {
        type: "SELECT_CHECKOUT_ADDRESS",
        payload: address,
    }
};

export const addPaymentMethod = (method) => {
    return {
        type: "ADD_PAYMENT_METHOD",
        payload: method,
    }
};

export const updateProfile = (sendData, toast, setLoader) => async (dispatch, getState) => {
    try {
        setLoader(true);
        const { data } = await api.put("/auth/profile", sendData);
        const currentAuth = getState().auth.user;
        const updatedAuth = {
            ...currentAuth,
            email: data.email || currentAuth.email,
            phone: data.phone,
            avatarUrl: data.avatarUrl,
        };
        dispatch({ type: "LOGIN_USER", payload: updatedAuth });
        localStorage.setItem("auth", JSON.stringify(updatedAuth));
        toast.success("Profile updated successfully");
    } catch (error) {
        toast.error(error?.response?.data?.message || "Failed to update profile");
    } finally {
        setLoader(false);
    }
};

export const changePassword = (sendData, toast, setLoader, reset) => async () => {
    try {
        setLoader(true);
        const { data } = await api.post("/auth/profile/change-password", sendData);
        toast.success(data.message || "Password changed successfully");
        reset();
    } catch (error) {
        toast.error(error?.response?.data?.message || "Failed to change password");
    } finally {
        setLoader(false);
    }
};

export const uploadAvatar = (file, toast, setLoader) => async (dispatch, getState) => {
    try {
        setLoader(true);
        const formData = new FormData();
        formData.append("file", file);
        const { data } = await api.post("/auth/profile/avatar", formData, {
            headers: { "Content-Type": "multipart/form-data" },
        });
        const currentAuth = getState().auth.user;
        const updatedAuth = {
            ...currentAuth,
            avatarUrl: data.avatarUrl,
        };
        dispatch({ type: "LOGIN_USER", payload: updatedAuth });
        localStorage.setItem("auth", JSON.stringify(updatedAuth));
        toast.success("Avatar uploaded successfully");
        return data.avatarUrl;
    } catch (error) {
        toast.error(error?.response?.data?.message || "Failed to upload avatar");
    } finally {
        setLoader(false);
    }
};
