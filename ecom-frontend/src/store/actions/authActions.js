import api from "../../api/api";

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

        if (data.needs2FA) {
            setNeeds2FA(true);
            setTemp2FAToken(data.temp2FAToken);
            setLoginEmail(sendData.username);
            return;
        }

        dispatch({ type: "LOGIN_USER", payload: data });
        localStorage.setItem("auth", JSON.stringify(data));
        reset();
        toast.success("Login Success");
        navigate("/");

    } catch (error) {
        toast.error(error?.response?.data?.message || "Internal Server Error");
        if (fetchHint) fetchHint(sendData.username);
    } finally {
        setLoader(false);
    }
};

export const registerNewUser
    = (sendData, toast, reset, navigate, setLoader) => async () => {
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
            toast.error(error?.response?.data?.message || error?.response?.data?.password || "Internal Server Error");
        } finally {
            setLoader(false);
        }
};

export const logOutUser = (navigate) => (dispatch) => {
    dispatch({ type: "LOG_OUT" });
    localStorage.removeItem("auth");
    navigate("/login");
};

export const fetchUserDetails = (token) => async (dispatch) => {
    const { data } = await api.get("/auth/user", {
        headers: { Authorization: `Bearer ${token}` }
    });

    const authData = {
        jwtToken: token,
        id: data.id,
        username: data.username,
        email: data.email,
        roles: data.roles,
        phone: data.phone,
        avatarUrl: data.avatarUrl,
    };

    dispatch({ type: "LOGIN_USER", payload: authData });
    localStorage.setItem("auth", JSON.stringify(authData));
};
