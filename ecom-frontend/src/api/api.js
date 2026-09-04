import axios from "axios";
import i18n from "../i18n";

const api = axios.create({
    baseURL: `${import.meta.env.VITE_BACK_END_URL || "http://localhost:8080"}/api`,
    withCredentials: true,
});

api.interceptors.request.use((config) => {
    config.headers = config.headers || {};

    config.headers["Accept-Language"] = i18n.language || "en";

    const csrfToken = document.cookie
        .split("; ")
        .find(row => row.startsWith("XSRF-TOKEN="))
        ?.split("=")[1];

    if (csrfToken) {
        config.headers["X-XSRF-TOKEN"] = csrfToken;
    }

    return config;
});

// One in-flight rotation shared by every request that 401s at the same moment.
// Without this, a page firing six parallel calls would trigger six rotations and
// five of them would lose the race against the newly issued refresh token.
let refreshing = null;

const signOutAndRedirect = () => {
    localStorage.removeItem("auth");
    const lang = i18n.language || "en";
    if (!window.location.pathname.includes(`/${lang}/login`)) {
        window.location.assign(`/${lang}/login`);
    }
};

api.interceptors.response.use(
    (response) => response,
    async (error) => {
        const status = error?.response?.status;
        const original = error?.config;
        const requestUrl = original?.url || "";

        // Routes that legitimately answer 401 and must never trigger a refresh —
        // /auth/refresh included, or a failed rotation would recurse.
        const isPublicAuthRequest =
            requestUrl.includes("/auth/signin") ||
            requestUrl.includes("/auth/signup") ||
            requestUrl.includes("/auth/signout") ||
            requestUrl.includes("/auth/refresh") ||
            requestUrl.includes("/auth/public/") ||
            requestUrl.includes("/auth/forgot-password") ||
            requestUrl.includes("/auth/reset-password");

        if (status !== 401 || isPublicAuthRequest || !original || original._retried) {
            return Promise.reject(error);
        }

        // The access token lasts 15 minutes, the refresh cookie 7 days. Rotate
        // and replay rather than throwing the customer back to the login page
        // mid-checkout; only a failed rotation means the session is really over.
        original._retried = true;
        try {
            refreshing = refreshing || api.post("/auth/refresh").finally(() => {
                refreshing = null;
            });
            await refreshing;
            return api(original);
        } catch {
            signOutAndRedirect();
            return Promise.reject(error);
        }
    }
)

export default api;