import axios from "axios";

const api = axios.create({
    baseURL: `${import.meta.env.VITE_BACK_END_URL || "http://localhost:8080"}/api`,
    withCredentials: true,
});

api.interceptors.request.use((config) => {
    config.headers = config.headers || {};

    const csrfToken = document.cookie
        .split("; ")
        .find(row => row.startsWith("XSRF-TOKEN="))
        ?.split("=")[1];

    if (csrfToken) {
        config.headers["X-XSRF-TOKEN"] = csrfToken;
    }

    return config;
});

api.interceptors.response.use(
    (response) => response,
    (error) => {
        const status = error?.response?.status;
        const requestUrl = error?.config?.url || "";
        const isPublicAuthRequest = 
            requestUrl.includes("/auth/signin") || 
            requestUrl.includes("/auth/signup") ||
            requestUrl.includes("/auth/signout") ||
            requestUrl.includes("/auth/public/") ||
            requestUrl.includes("/auth/forgot-password") ||
            requestUrl.includes("/auth/reset-password");

        if(status == 401 && !isPublicAuthRequest){
            localStorage.removeItem("auth");
            if(window.location.pathname !== "/login"){
                window.location.href = "/login";
            }
        }

        return Promise.reject(error);
    }
)

export default api;