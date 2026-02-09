import axios from "axios";

const api = axios.create({
    baseURL:`${import.meta.env.VITE_BACK_END_URL}/api`,
    withCredentials: true, // 🔥 OBLIGATORIU pentru cookies
});


api.interceptors.request.use((config) => {
    const token = localStorage.getItem("JWT_TOKEN");
    if (token) {
        config.headers.Authorization = `Bearer ${token}`;
    }
    return config;
});

export default api;