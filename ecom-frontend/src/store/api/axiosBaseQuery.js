import { isRejectedWithValue } from "@reduxjs/toolkit";
import api from "../../api/api";

/**
 * Axios-based base query for RTK Query.
 * Reuses the existing axios instance so CSRF and 401 redirect logic stay intact.
 */
export const axiosBaseQuery =
    ({ baseUrl } = { baseUrl: "" }) =>
        async ({ url, method, body, params, responseHandler }) => {
            try {
                const result = await api({
                    url: baseUrl + url,
                    method,
                    data: body,
                    params,
                    responseHandler,
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

export const isApiError = (action) => isRejectedWithValue(action);
