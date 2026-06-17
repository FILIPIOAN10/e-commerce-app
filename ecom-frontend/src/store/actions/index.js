import toast from "react-hot-toast";
import api from "../../api/api"
import { duration } from "@mui/material";
import { get } from "react-hook-form";

export const fetchProducts = (queryString) => async (dispatch) => {
    try {
        dispatch({ type: "IS_FETCHING" });
        const searchParams = new URLSearchParams(queryString);
        const keyword = searchParams.get("keyword");
        const category = searchParams.get("category");
        let requestUrl = `/public/products?${queryString}`;
        if( keyword && !category){
            searchParams.delete("keyword")
            searchParams.set("q",keyword)
            searchParams.set("semantic","true");
            requestUrl = `/public/products/search?${searchParams.toString()}`;
        }
        const {data} = await api.get(requestUrl);
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
        console.log(error);
        dispatch({ 
            type: "IS_ERROR",
            payload: error?.response?.data?.message || "Failed to fetch products",
         });
    }
};


export const fetchCategories = () => async (dispatch) => {
    try {
        dispatch({ type: "CATEGORY_LOADER" });
        const { data } = await api.get(`/public/categories`);
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
        console.log(error);
        dispatch({ 
            type: "IS_ERROR",
            payload: error?.response?.data?.message || "Failed to fetch categories",
         });
    }
};


export const addToCart = (data, qty = 1, toast) => 
    (dispatch, getState) => {
        // Find the product
        const { products } = getState().products;
        const getProduct = products.find(
            (item) => item.productId === data.productId
        );

        // Check for stocks
        const isQuantityExist = getProduct.quantity >= qty;

        // If in stock -> add
        if (isQuantityExist) {
            dispatch({ type: "ADD_CART", payload: {...data, quantity: qty}});
            toast.success(`${data?.productName} added to the cart`);
            localStorage.setItem("cartItems", JSON.stringify(getState().carts.cart));
        } else {
            // error
            toast.error("Out of stock");
        }
};


export const increaseCartQuantity = 
    (data, toast, currentQuantity, setCurrentQuantity) =>
    (dispatch, getState) => {
        // Find the product
        const { products } = getState().products;
        
        const getProduct = products.find(
            (item) => item.productId === data.productId
        );

        const isQuantityExist = getProduct.quantity >= currentQuantity + 1;

        if (isQuantityExist) {
            const newQuantity = currentQuantity + 1;
            setCurrentQuantity(newQuantity);

            dispatch({
                type: "ADD_CART",
                payload: {...data, quantity: newQuantity },
            });
            localStorage.setItem("cartItems", JSON.stringify(getState().carts.cart));
        } else {
            toast.error("Quantity Reached to Limit");
        }

    };



export const decreaseCartQuantity = 
    (data, newQuantity) => (dispatch, getState) => {
        dispatch({
            type: "ADD_CART",
            payload: {...data, quantity: newQuantity},
        });
        localStorage.setItem("cartItems", JSON.stringify(getState().carts.cart));
    }

export const removeFromCart =  (data, toast) => (dispatch, getState) => {
    dispatch({type: "REMOVE_CART", payload: data });
    toast.success(`${data.productName} removed from cart`);
    localStorage.setItem("cartItems", JSON.stringify(getState().carts.cart));
}



export const authenticateSignInUser = (
    sendData, toast, reset, navigate, setLoader, fetchHint,
    setNeeds2FA, setTemp2FAToken, setLoginEmail
) => async (dispatch) => {
    try {
        setLoader(true);
        localStorage.removeItem("auth");

        const loginData = {
            ...sendData,
            username: String(sendData.username || "").trim(),
        };

        const { data } = await api.post("/auth/signin", loginData);
        console.log("=== SIGNIN RESPONSE ===", data);

        // Dacă backend-ul cere 2FA
        if (data.needs2FA) {                        // <- fix: era mfaRequired
            setNeeds2FA(true);
            setTemp2FAToken(data.temp2FAToken);     // <- fix: era jwtToken
            setLoginEmail(sendData.username);
            return;
        }

        // Login normal fără 2FA
        dispatch({ type: "LOGIN_USER", payload: data });
        localStorage.setItem("auth", JSON.stringify(data));
        reset();
        toast.success("Login Success");
        navigate("/");

    } catch (error) {
        console.log(error);
        toast.error(error?.response?.data?.message || "Internal Server Error");
        if (fetchHint) fetchHint(sendData.username);
    } finally {
        setLoader(false);
    }
};

export const registerNewUser 
    = (sendData, toast, reset, navigate, setLoader) => async (dispatch) => {
        try {
            setLoader(true);

            const payload = {
                ...sendData,
                roles: sendData.role ? [sendData.role] : ["ROLE_USER"],
            };
            delete payload.role; 
            const { data } = await api.post("/auth/signup", payload);
            reset();
            toast.success(data?.message || "User Registered Successfully");
            navigate("/login");
        } catch (error) {
            console.log(error);
            toast.error(error?.response?.data?.message || error?.response?.data?.password || "Internal Server Error");
        } finally {
            setLoader(false);
        }
};


export const logOutUser = (navigate) => (dispatch) => {
    dispatch({ type:"LOG_OUT" });
    localStorage.removeItem("auth");
    navigate("/login");
};

export const addUpdateUserAddress =
     (sendData, toast, addressId, setOpenAddressModal) => async (dispatch, getState) => {
    /*
    const { user } = getState().auth;
    await api.post(`/addresses`, sendData, {
          headers: { Authorization: "Bearer " + user.jwtToken },
        });
    */
    dispatch({ type:"BUTTON_LOADER" });
    try {
        if (!addressId) {
            const { data } = await api.post("/addresses", sendData);
        } else {
            await api.put(`/addresses/${addressId}`, sendData);
        }
        dispatch(getUserAddresses());
        toast.success("Address saved successfully");
        dispatch({ type:"IS_SUCCESS" });
    } catch (error) {
        console.log(error);
        toast.error(error?.response?.data?.message || "Internal Server Error");
        dispatch({ type:"IS_ERROR", payload: null });
    } finally {
        setOpenAddressModal(false);
    }
};


export const deleteUserAddress = 
    (toast, addressId, setOpenDeleteModal) => async (dispatch, getState) => {
    try {
        dispatch({ type: "BUTTON_LOADER" });
        await api.delete(`/addresses/${addressId}`);
        dispatch({ type: "IS_SUCCESS" });
        dispatch(getUserAddresses());
        dispatch(clearCheckoutAddress());
        toast.success("Address deleted successfully");
    } catch (error) {
        console.log(error);
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

export const getUserAddresses = () => async (dispatch, getState) => {
    try {
        dispatch({ type: "IS_FETCHING" });
        const { data } = await api.get(`/users/addresses`);
        dispatch({type: "USER_ADDRESS", payload: data});
        dispatch({ type: "IS_SUCCESS" });
    } catch (error) {
        console.log(error);
        dispatch({ 
            type: "IS_ERROR",
            payload: error?.response?.data?.message || "Failed to fetch user addresses",
         });
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


export const createUserCart = (sendCartItems) => async (dispatch, getState) => {
    try {
        dispatch({ type: "IS_FETCHING" });
        await api.post('/cart/create', sendCartItems);
        await dispatch(getUserCart());
    } catch (error) {
        console.log(error);
        dispatch({ 
            type: "IS_ERROR",
            payload: error?.response?.data?.message || "Failed to create cart items",
         });
    }
};


export const getUserCart = () => async (dispatch, getState) => {
    try {
        dispatch({ type: "IS_FETCHING" });
        const { data } = await api.get('/carts/users/cart');
        
        dispatch({
            type: "GET_USER_CART_PRODUCTS",
            payload: data.products,
            totalPrice: data.totalPrice,
            cartId: data.cartId
        })
        localStorage.setItem("cartItems", JSON.stringify(getState().carts.cart));
        dispatch({ type: "IS_SUCCESS" });
    } catch (error) {
        console.log(error);
        dispatch({ 
            type: "IS_ERROR",
            payload: error?.response?.data?.message || "Failed to fetch cart items",
         });
    }
};


export const createStripePaymentSecret 
    = (sendData) => async (dispatch, getState) => {
        try {
            dispatch({ type: "IS_FETCHING" });
            const { data } = await api.post("/order/stripe-client-secret", sendData);
            dispatch({ type: "CLIENT_SECRET", payload: data });
              localStorage.setItem("client-secret", JSON.stringify(data));
              dispatch({ type: "IS_SUCCESS" });
        } catch (error) {
            console.log(error);
            toast.error(error?.response?.data?.message || "Failed to create client secret");
        }
};


export const stripePaymentConfirmation 
    = (sendData, setErrorMesssage, setLoadng, toast) => async (dispatch, getState) => {
        try {
            const response  = await api.post("/order/users/payments/online", sendData);
            if (response.data) {
                localStorage.removeItem("CHECKOUT_ADDRESS");
                localStorage.removeItem("cartItems");
                localStorage.removeItem("client-secret");
                dispatch({ type: "REMOVE_CLIENT_SECRET_ADDRESS"});
                dispatch({ type: "CLEAR_CART"});
                toast.success("Order Accepted");
              } else {
                setErrorMesssage("Payment Failed. Please try again.");
              }
        } catch (error) {
            setErrorMesssage("Payment Failed. Please try again.");
        }
};

export const analyticsAction = () => async (dispatch, getState) => {
        try {
            dispatch({ type: "IS_FETCHING"});
            const { data } = await api.get('/admin/app/analytics');
            dispatch({
                type: "FETCH_ANALYTICS",
                payload: data,
            })
            dispatch({ type: "IS_SUCCESS"});
        } catch (error) {
            dispatch({ 
                type: "IS_ERROR",
                payload: error?.response?.data?.message || "Failed to fetch analytics data",
            });
        }
};

export const getOrdersForDashboard = (queryString= "", isAdmin) => async (dispatch, getState) => {
    try {
        dispatch({ type: "IS_FETCHING" });
        const {user} = getState().auth;
        const adminRequest = isAdmin ?? Boolean(user?.roles?.includes("ROLE_ADMIN"));
        const endpoint = adminRequest ? "/admin/orders" : "/seller/orders";
        const requestUrl = queryString ? `${endpoint}?${queryString}`:endpoint;
        const {data} = await api.get(requestUrl);
        dispatch({
            type: "GET_ADMIN_ORDERS",
            payload: data.content,
            pageNumber: data.pageNumber,
            pageSize: data.pageSize,
            totalElements: data.totalElements,
            totalPages: data.totalPages,
            lastPage: data.lastPage,
        });
        dispatch({ type: "IS_SUCCESS" });
    } catch (error) {
        console.log(error);
        dispatch({ 
            type: "IS_ERROR",
            payload: error?.response?.data?.message || "Failed to fetch orders data",
         });
    }
};



export const updateOrderStatusFromDashboard =
     (orderId, orderStatus, toast, setLoader, isAdmin) => async (dispatch, getState) => {
    try {
        setLoader(true);
        const {user} = getState().auth;
        const adminRequest = isAdmin ?? Boolean(user?.roles?.includes("ROLE_ADMIN"));
        const endpoint = adminRequest ? "/admin/orders/" : "/seller/orders/";

        const { data } = await api.put(`${endpoint}${orderId}/status`, { status: orderStatus });

        toast.success(data.message || "Order updated successfully");
        await dispatch(getOrdersForDashboard("", adminRequest));
    } catch (error) {
        console.log(error);
        toast.error(error?.response?.data?.message || "Internal Server Error");
    } finally {
        setLoader(false);
    }
};


export const dashboardProductsAction = (queryString, isAdmin) => async (dispatch,getState) => {
    try {
        dispatch({ type: "IS_FETCHING" });
        const {user} = getState().auth;
        const adminRequest = isAdmin ?? Boolean(user?.roles?.includes("ROLE_ADMIN"));
        const endpoint = adminRequest ? "/admin/products" : "/seller/products";
        const requestUrl = queryString ? `${endpoint}?${queryString}`:endpoint;
        const {data} = await api.get(requestUrl);
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
        console.log(error);
        dispatch({ 
            type: "IS_ERROR",
            payload: error?.response?.data?.message || "Failed to fetch dashboard products",
         });
    }
};


export const getAllCategoriesDashboard = (queryString="") => async (dispatch) => {
  dispatch({ type: "CATEGORY_LOADER" });
  try {
    const requestUrl = queryString ? `/public/categories?${queryString}` : "/public/categories";
    const {data} = await api.get(requestUrl);
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
    console.log(err);

    dispatch({
      type: "IS_ERROR",
      payload: err?.response?.data?.message || "Failed to fetch categories",
    });
  }
};


export const updateCategoryDashboardAction =
  (sendData, setOpen, categoryID, reset, toast) =>
  async (dispatch, getState) => {
    try {
      dispatch({ type: "CATEGORY_LOADER" });

    
      await api.put(`/admin/categories/${categoryID}`,sendData);

      dispatch({ type: "CATEGORY_SUCCESS" });

      reset();
      toast.success("Category Update Successful");
      setOpen(false);
      await dispatch(getAllCategoriesDashboard());
    } catch (err) {
      console.log(err);
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
      console.log(err);
      toast.error(
        err?.response?.data?.categoryName || "Failed to create new category"
      );

      dispatch({
        type: "IS_ERROR",
        payload: err?.response?.data?.message || "Internal Server Error",
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
            console.error("Product update failed", error?.response?.status, error?.config?.url, error);
            toast.error(error?.response?.data?.description || error?.response?.data?.message || "Product update failed");
        } finally {
            setLoader(false);
        }
}

export const deleteProduct = 
    (setLoader,productId,toast,setOpenDeleteModal,isAdmin, queryString = "")=> async (dispatch,getState) => {

    try {
        setLoader(true);
        const endpoint = isAdmin ? "/admin/products/" : "/seller/products/";
        await api.delete(`${endpoint}${productId}`);
        dispatch({ type :"DELETE_PRODUCT_SUCCESS",payload :productId});
        toast.success("Product deleted successfully");

        setOpenDeleteModal(false);
        await dispatch(dashboardProductsAction(queryString,isAdmin));
     
    } catch (error){    
        console.log("Product delete failed",error?.response?.status, error?.config?.url,error);
        toast.error(error?.response?.data?.message || "Some Error Occured");
    } finally{
        setLoader(false);
    }
};

export const addNewProductFromDashboard = 
    (sendData, toast, reset, setLoader, setOpen, isAdmin) => async(dispatch, getState) => {
        try {
            setLoader(true);
            const endpoint = isAdmin ? "/admin/categories/" : "/seller/categories/";
            await api.post(`${endpoint}${sendData.categoryId}/product`,
                sendData
            );
            toast.success("Product created successfully");
            reset();
            setOpen(false);
            await dispatch(dashboardProductsAction("",isAdmin));
        } catch (error) {
            console.error(error);
            toast.error(error?.response?.data?.description || "Product creation failed");
        } finally {
            setLoader(false);
        }
};
export const updateProductImageFromDashboard = 
    (formData,productId,toast,setLoader,setOpen,isAdmin) => async (dispatch) => {

    try {
        setLoader(true);
        const endpoint = isAdmin ? "/admin/products/" : "/seller/products/";
        await api.put(`${endpoint}${productId}/image`, formData);
        toast.success("Image update successful");
        setLoader(false);
        setOpen(false);
        await dispatch(dashboardProductsAction("",isAdmin));
    } catch (error){    
      
        toast.error(error?.response?.data?.description || "Product Image upload failed");
    }
};

export const deleteCategoryDashboardAction =
  (setOpen, categoryID, toast) => async (dispatch, getState) => {
    try {
      dispatch({type:"DELETE_CATEGORY_SUCCESS",payload:categoryID});
      dispatch({ type: "CATEGORY_LOADER" });

      await api.delete(`/admin/categories/${categoryID}`);

      dispatch({ type: "CATEGORY_SUCCESS" });

      toast.success("Category Delete Successful");
      setOpen(false);
    } catch (err) {
      console.log(err);
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
      console.log(err);
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
      console.log(err);
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


// @Prefilter Demo - Cart Items (filters items with qty 1-10)

export const createCartWithFilteredItems =
  (cartItems,toast,setLoader) => async (dispatch) => {
    try {
        setLoader(true);
        const {data} = await api.post("/cart/create",cartItems);
        toast.success("Cart updated! Items with invalid quantity were filtered out.")
        dispatch({ type: "IS_SUCCESS"});
        return data;
    }catch (error){
        console.log(error);
        toast.error(error?.response?.data?.message || "Failed to create cart");
        dispatch({
            type: "IS_ERROR",
            payload: error?.response?.data?.message || "Failed to created cart",
        });
    } finally{
        setLoader(false);
    }
  };


  // @PostFilter Demo - Orders List (ADMIN see all, users see only their orders)
  export const getFilteredOrdersList = () => async (dispatch) =>{
    try{
        dispatch({ type: "IS_FETCHING"});
        const { data } = await api.get("/orders/list");
        dispatch({
            type: "FETCH_FILTERED_ORDERS",
            payload: data,
        });
        dispatch ({ type: "IS_SUCCESS"});
        return data;
    } catch (error){
        console.log(error);
        dispatch({
            type: "IS_ERROR",
            payload:error?.response?.data?.message || "Failed to fetch orders",
        });
    }
  };

  export const getFilteredProductsForDisplay = () => async (dispatch) => {
    try{
        dispatch({ type: "IS_FETCHING"});
        const { data } = await api.get("/products/display");
        dispatch({
            type:"FETCH_FILTERED_PRODUCTS",
            payload:data,
        });
        dispatch({ type :"IS_SUCCESS"});
        return data; 
    }catch (error){
        console.log(error);
        dispatch({
            type: "IS_ERROR",
            payload:error?.response?.data?.message || "Failed to fetch filtered products",
        });
    }
  };
  
export const getUserOrders = (queryString = "") => async (dispatch) => {
    try {
        dispatch({ type: "IS_FETCHING" });
        
        const requestUrl = queryString ? `/orders/my-orders?${queryString}` : "/orders/my-orders";
        
        const { data } = await api.get(requestUrl);
        
        dispatch({
            type: "GET_USER_ORDERS",
            payload: data.content,
            pageNumber: data.pageNumber,
            pageSize: data.pageSize,
            totalElements: data.totalElements,
            totalPages: data.totalPages,
            lastPage: data.lastPage,
        });
        
        dispatch({ type: "IS_SUCCESS" });
    } catch (error) {
        console.log("Failed to fetch user orders:", error);
        dispatch({ 
            type: "IS_ERROR",
            payload: error?.response?.data?.message || "Failed to fetch your orders",
         });
    }
};

export const fetchUserDetails = (token) => async (dispatch) => {
    try {
        const { data } = await api.get("/auth/user", {
            headers: { Authorization: `Bearer ${token}` }
        });

        const authData = {
            jwtToken: token,
            id: data.id,
            username: data.username,
            email: data.email,
            roles: data.roles,
        };

        dispatch({ type: "LOGIN_USER", payload: authData });
        localStorage.setItem("auth", JSON.stringify(authData));
    } catch (error) {
        console.log("Failed to fetch user details", error);
        throw error;
    }
};