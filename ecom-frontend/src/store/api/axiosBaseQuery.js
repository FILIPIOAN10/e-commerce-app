<<<<<<< HEAD
=======
import { isRejectedWithValue } from "@reduxjs/toolkit";
>>>>>>> feat/rtk-query-dark-rate-limit
import api from "../../api/api";

/**
 * Axios-based base query for RTK Query.
<<<<<<< HEAD
 * Reuses the existing axios instance so CSRF handling, auth cookies and
 * the 401 redirect interceptor already configured on `api` stay intact.
 */
export const axiosBaseQuery =
    ({ baseUrl } = { baseUrl: "" }) =>
        async ({ url, method, body, params }) => {
=======
 * Reuses the existing axios instance so CSRF and 401 redirect logic stay intact.
 */
export const axiosBaseQuery =
    ({ baseUrl } = { baseUrl: "" }) =>
        async ({ url, method, body, params, responseHandler }) => {
>>>>>>> feat/rtk-query-dark-rate-limit
            try {
                const result = await api({
                    url: baseUrl + url,
                    method,
                    data: body,
                    params,
<<<<<<< HEAD
=======
                    responseHandler,
>>>>>>> feat/rtk-query-dark-rate-limit
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

<<<<<<< HEAD
export default axiosBaseQuery;
=======
export const isApiError = (action) => isRejectedWithValue(action);
>>>>>>> feat/rtk-query-dark-rate-limit
