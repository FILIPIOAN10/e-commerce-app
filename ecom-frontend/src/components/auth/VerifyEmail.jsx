import { useEffect, useState } from "react";
import { useSearchParams, useNavigate } from "react-router-dom";
import { FaCheckCircle, FaExclamationCircle, FaSpinner } from "react-icons/fa";
import api from "../../api/api";

const VerifyEmail = () => {
    const [searchParams] = useSearchParams();
    const navigate = useNavigate();
    const [status, setStatus] = useState("loading");
    const [message, setMessage] = useState("");

    useEffect(() => {
        const token = searchParams.get("token");
        if (!token) {
            setStatus("error");
            setMessage("No verification token found in URL.");
            return;
        }

        const verify = async () => {
            try {
                const { data } = await api.get(`/auth/verify-email?token=${token}`);
                setStatus("success");
                setMessage(data.message || "Email verified successfully!");
                setTimeout(() => navigate("/login"), 3000);
            } catch (error) {
                setStatus("error");
                setMessage(error?.response?.data?.message || "Email verification failed. The link may be expired or invalid.");
            }
        };
        verify();
    }, [searchParams, navigate]);

    return (
        <div className="min-h-screen flex items-center justify-center bg-gray-50 dark:bg-gray-950 px-4">
            <div className="max-w-md w-full bg-white dark:bg-gray-800 rounded-lg shadow-lg p-8 text-center">
                {status === "loading" && (
                    <>
                        <FaSpinner className="animate-spin text-blue-500 text-5xl mx-auto mb-4" />
                        <h2 className="text-xl font-semibold text-gray-800 dark:text-white mb-2">
                            Verifying your email...
                        </h2>
                        <p className="text-gray-500 dark:text-gray-400">Please wait a moment.</p>
                    </>
                )}

                {status === "success" && (
                    <>
                        <FaCheckCircle className="text-green-500 text-5xl mx-auto mb-4" />
                        <h2 className="text-xl font-semibold text-gray-800 dark:text-white mb-2">
                            Email Verified!
                        </h2>
                        <p className="text-gray-600 dark:text-gray-300 mb-4">{message}</p>
                        <p className="text-sm text-gray-400 dark:text-gray-500">
                            Redirecting to login in 3 seconds...
                        </p>
                        <button
                            onClick={() => navigate("/login")}
                            className="mt-4 px-6 py-2 bg-blue-600 hover:bg-blue-700 text-white rounded-md font-medium transition"
                        >
                            Go to Login
                        </button>
                    </>
                )}

                {status === "error" && (
                    <>
                        <FaExclamationCircle className="text-red-500 text-5xl mx-auto mb-4" />
                        <h2 className="text-xl font-semibold text-gray-800 dark:text-white mb-2">
                            Verification Failed
                        </h2>
                        <p className="text-gray-600 dark:text-gray-300 mb-4">{message}</p>
                        <button
                            onClick={() => navigate("/login")}
                            className="mt-4 px-6 py-2 bg-blue-600 hover:bg-blue-700 text-white rounded-md font-medium transition"
                        >
                            Back to Login
                        </button>
                    </>
                )}
            </div>
        </div>
    );
};

export default VerifyEmail;
