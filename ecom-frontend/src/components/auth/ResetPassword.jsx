import { useState } from "react";
import { Link, useSearchParams, useNavigate } from "react-router-dom";
import { FaKey, FaCheckCircle, FaExclamationCircle } from "react-icons/fa";
import { useForm } from "react-hook-form";
import InputField from "../shared/InputField";
import Spinners from "../shared/Spinners";
import api from "../../api/api";
import toast from "react-hot-toast";

const ResetPassword = () => {
    const [searchParams] = useSearchParams();
    const navigate = useNavigate();
    const [loader, setLoader] = useState(false);
    const [status, setStatus] = useState("form");
    const [message, setMessage] = useState("");

    const token = searchParams.get("token");

    const {
        register,
        handleSubmit,
        watch,
        reset,
        formState: { errors },
    } = useForm({
        mode: "onTouched",
    });

    const password = watch("newPassword", "");

    const onSubmit = async (data) => {
        if (!token) {
            setStatus("error");
            setMessage("No reset token found in URL. Please use the link from your email.");
            return;
        }

        if (data.newPassword !== data.confirmPassword) {
            toast.error("Passwords do not match!");
            return;
        }

        setLoader(true);
        try {
            const { data: resp } = await api.post("/auth/reset-password", {
                token: token,
                newPassword: data.newPassword,
            });
            setStatus("success");
            setMessage(resp.message || "Password reset successfully!");
            toast.success("Password reset successfully!");
            reset();
            setTimeout(() => navigate("/login"), 3000);
        } catch (error) {
            setStatus("error");
            setMessage(error?.response?.data?.message || "Failed to reset password. The link may be expired or invalid.");
            toast.error("Failed to reset password.");
        } finally {
            setLoader(false);
        }
    };

    if (!token) {
        return (
            <div className="min-h-[calc(100vh-64px)] flex justify-center items-center dark:bg-gray-950">
                <div className="sm:w-112.5 w-90 shadow-custom py-8 sm:px-8 px-4 rounded-md dark:bg-gray-800 dark:text-white text-center">
                    <FaExclamationCircle className="text-red-500 text-5xl mx-auto mb-4" />
                    <h1 className="text-xl font-bold text-slate-800 dark:text-white mb-2">
                        Invalid Reset Link
                    </h1>
                    <p className="text-slate-600 dark:text-gray-400 mb-4">
                        No reset token found. Please request a new password reset link.
                    </p>
                    <Link
                        to="/forgot-password"
                        className="text-blue-600 hover:underline font-medium"
                    >
                        Request New Link
                    </Link>
                </div>
            </div>
        );
    }

    if (status === "success") {
        return (
            <div className="min-h-[calc(100vh-64px)] flex justify-center items-center dark:bg-gray-950">
                <div className="sm:w-112.5 w-90 shadow-custom py-8 sm:px-8 px-4 rounded-md dark:bg-gray-800 dark:text-white text-center">
                    <FaCheckCircle className="text-green-500 text-5xl mx-auto mb-4" />
                    <h1 className="text-xl font-bold text-slate-800 dark:text-white mb-2">
                        Password Reset!
                    </h1>
                    <p className="text-slate-600 dark:text-gray-400 mb-4">{message}</p>
                    <p className="text-sm text-slate-400 dark:text-gray-500">
                        Redirecting to login in 3 seconds...
                    </p>
                    <button
                        onClick={() => navigate("/login")}
                        className="mt-4 px-6 py-2 bg-blue-600 hover:bg-blue-700 text-white rounded-md font-medium transition"
                    >
                        Go to Login
                    </button>
                </div>
            </div>
        );
    }

    if (status === "error") {
        return (
            <div className="min-h-[calc(100vh-64px)] flex justify-center items-center dark:bg-gray-950">
                <div className="sm:w-112.5 w-90 shadow-custom py-8 sm:px-8 px-4 rounded-md dark:bg-gray-800 dark:text-white text-center">
                    <FaExclamationCircle className="text-red-500 text-5xl mx-auto mb-4" />
                    <h1 className="text-xl font-bold text-slate-800 dark:text-white mb-2">
                        Reset Failed
                    </h1>
                    <p className="text-slate-600 dark:text-gray-400 mb-4">{message}</p>
                    <Link
                        to="/forgot-password"
                        className="text-blue-600 hover:underline font-medium"
                    >
                        Request New Link
                    </Link>
                </div>
            </div>
        );
    }

    return (
        <div className="min-h-[calc(100vh-64px)] flex justify-center items-center dark:bg-gray-950">
            <form
                onSubmit={handleSubmit(onSubmit)}
                className="sm:w-112.5 w-90 shadow-custom py-8 sm:px-8 px-4 rounded-md dark:bg-gray-800 dark:text-white"
            >
                <div className="flex flex-col items-center justify-center space-y-4">
                    <FaKey className="text-slate-800 text-5xl dark:text-white" />
                    <h1 className="text-slate-800 text-center font-montserrat lg:text-3xl text-2xl font-bold dark:text-white">
                        Reset Password
                    </h1>
                    <p className="text-sm text-slate-600 dark:text-gray-400 text-center">
                        Enter your new password below.
                    </p>
                </div>

                <hr className="mt-2 mb-5 text-black dark:border-gray-600" />

                <div className="flex flex-col gap-3">
                    <InputField
                        label="New Password"
                        required
                        id="newPassword"
                        type="password"
                        min={6}
                        minLengthMessage="*Password must be at least 6 characters"
                        message="*Password is required"
                        placeHolder="Enter new password"
                        register={register}
                        errors={errors}
                    />

                    <InputField
                        label="Confirm Password"
                        required
                        id="confirmPassword"
                        type="password"
                        min={6}
                        minLengthMessage="*Password must be at least 6 characters"
                        message="*Please confirm your password"
                        placeHolder="Confirm new password"
                        register={register}
                        errors={errors}
                    />
                </div>

                <button
                    disabled={loader}
                    className="bg-button-gradient flex gap-2 items-center justify-center font-semibold text-white w-full py-2 hover:text-slate-400 transition-colors duration-100 rounded-sm my-3"
                    type="submit"
                >
                    {loader ? (
                        <>
                            <Spinners /> Resetting...
                        </>
                    ) : (
                        <>Reset Password</>
                    )}
                </button>

                <p className="text-center text-sm text-slate-700 dark:text-gray-300 mt-6">
                    Remember your password?{" "}
                    <Link
                        className="font-semibold underline hover:text-black dark:hover:text-white"
                        to="/login"
                    >
                        Back to Login
                    </Link>
                </p>
            </form>
        </div>
    );
};

export default ResetPassword;
