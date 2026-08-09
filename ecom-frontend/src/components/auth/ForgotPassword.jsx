import { useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { FaKey, FaPaperPlane } from "react-icons/fa";
import { useForm } from "react-hook-form";
import InputField from "../shared/InputField";
import Spinners from "../shared/Spinners";
import api from "../../api/api";
import toast from "react-hot-toast";

const ForgotPassword = () => {
    const navigate = useNavigate();
    const [loader, setLoader] = useState(false);
    const [sent, setSent] = useState(false);

    const {
        register,
        handleSubmit,
        reset,
        formState: { errors },
    } = useForm({
        mode: "onTouched",
    });

    const onSubmit = async (data) => {
        setLoader(true);
        try {
            await api.post("/auth/forgot-password", { email: data.email });
            setSent(true);
            toast.success("Reset link sent! Check your email.");
            reset();
        } catch (error) {
            toast.error(error?.response?.data?.message || "Failed to send reset link.");
        } finally {
            setLoader(false);
        }
    };

    return (
        <div className="min-h-[calc(100vh-64px)] flex justify-center items-center dark:bg-gray-950">
            <div className="sm:w-112.5 w-90 shadow-custom py-8 sm:px-8 px-4 rounded-md dark:bg-gray-800 dark:text-white">
                <div className="flex flex-col items-center justify-center space-y-4">
                    <FaKey className="text-slate-800 text-5xl dark:text-white" />
                    <h1 className="text-slate-800 text-center font-montserrat lg:text-3xl text-2xl font-bold dark:text-white">
                        Forgot Password?
                    </h1>
                    <p className="text-sm text-slate-600 dark:text-gray-400 text-center">
                        Enter your email address and we'll send you a link to reset your password.
                    </p>
                </div>

                <hr className="mt-2 mb-5 text-black dark:border-gray-600" />

                {!sent ? (
                    <form onSubmit={handleSubmit(onSubmit)} className="flex flex-col gap-3">
                        <InputField
                            label="Email"
                            required
                            id="email"
                            type="email"
                            message="*Email is required"
                            placeHolder="Enter your email"
                            register={register}
                            errors={errors}
                        />

                        <button
                            disabled={loader}
                            className="bg-button-gradient flex gap-2 items-center justify-center font-semibold text-white w-full py-2 hover:text-slate-400 transition-colors duration-100 rounded-sm my-3"
                            type="submit"
                        >
                            {loader ? (
                                <>
                                    <Spinners /> Sending...
                                </>
                            ) : (
                                <>
                                    <FaPaperPlane /> Send Reset Link
                                </>
                            )}
                        </button>
                    </form>
                ) : (
                    <div className="text-center py-4">
                        <p className="text-green-600 dark:text-green-400 font-medium mb-2">
                            Reset link has been sent to your email!
                        </p>
                        <p className="text-sm text-slate-600 dark:text-gray-400">
                            Check your inbox (and spam folder) for the password reset link.
                        </p>
                    </div>
                )}

                <p className="text-center text-sm text-slate-700 dark:text-gray-300 mt-6">
                    Remember your password?{" "}
                    <Link
                        className="font-semibold underline hover:text-black dark:hover:text-white"
                        to="/login"
                    >
                        Back to Login
                    </Link>
                </p>
            </div>
        </div>
    );
};

export default ForgotPassword;
