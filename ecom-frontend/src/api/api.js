import axios from "axios";

const api = axios.create({
    baseURL: `${import.meta.env.VITE_BACK_END_URL}/api`,
    withCredentials: true,
});

api.interceptors.request.use((config) => {

    const requestUrl = config.url || "";

    // Exclude DOAR endpoint-urile publice de auth, nu toate /auth/
    const isPublicAuthRequest = 
        requestUrl.includes("/auth/signin") || 
        requestUrl.includes("/auth/signup") ||
        requestUrl.includes("/auth/signout") ||
        requestUrl.includes("/auth/public/");

    config.headers = config.headers || {};

    if(!isPublicAuthRequest){
        const auth = localStorage.getItem("auth");
        const user = auth ? JSON.parse(auth) : null;
        const jwtToken = user?.jwtToken;

        if(jwtToken){
            config.headers.Authorization = `Bearer ${jwtToken}`;
        }
    }

    const csrfToken = document.cookie
        .split("; ")
        .find(row => row.startsWith("XSRF-TOKEN="))
        ?.split("=")[1];

    if (csrfToken) {
        config.headers["X-XSRF-TOKEN"] = csrfToken;
    }

    console.log("DEBUG API TOKEN TRIMIS:", config.headers.Authorization);
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
            requestUrl.includes("/auth/public/");

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