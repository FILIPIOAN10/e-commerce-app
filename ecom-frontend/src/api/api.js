import axios from "axios";

const api = axios.create({
    baseURL: `${import.meta.env.VITE_BACK_END_URL}/api`,
    withCredentials: true,
});

api.interceptors.request.use((config) => {

    const requestUrl = config.url || "";
    const isAuthRequest = requestUrl.includes("/auth/");

    if(isAuthRequest){
        return config;
    }

    const auth = localStorage.getItem("auth");
    const user = auth ? JSON.parse(auth) : null;
    const jwtToken = user?.jwtToken;

    config.headers = config.headers || {};

    if(jwtToken){
        config.headers.Authorization = `Bearer ${jwtToken}`;
    }
    const csrfToken = document.cookie
        .split("; ")
        .find(row => row.startsWith("XSRF-TOKEN="))
        ?.split("=")[1];

    if (csrfToken) {
        config.headers["X-XSRF-TOKEN"] = csrfToken;
    }

    console.log("DEBUG API TOKEN TRIMIS:", jwtToken);
    return config;
});

api.interceptors.response.use(
    (response) => response,
    (error) => {
        const status = error?.response?.status;
        const requestUrl = error?.config?.url || "";
        const isAuthRequest =requestUrl.includes("/auth/");

        if(status == 401 && !isAuthRequest){
            localStorage.removeItem("auth");
            if(window.location.pathname !== "/login"){
                window.location.href ="/login";
            }
        }

        return Promise.reject(error);
    }
)

export default api;