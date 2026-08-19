import api from "../../api/api";

/**
 * Axios-based base query for RTK Query.
 * Reuses the existing axios instance so CSRF handling, auth cookies and
 * the 401 redirect interceptor already configured on `api` stay intact.
 */
export const axiosBaseQuery =
    ({ baseUrl } = { baseUrl: "" }) =>
        async ({ url, method, body, params }) => {
            try {
                const result = await api({
                    url: baseUrl + url,
                    method,
                    data: body,
                    params,
                });
                return { data: result.data };
            } catch (axiosError) {
                const err = axiosError;
                return {
                    error: {
                        status: err.response?.status,
                        data: err.response?.data || err.message,
                    },
                };
            }
        };

export default axiosBaseQuery;
