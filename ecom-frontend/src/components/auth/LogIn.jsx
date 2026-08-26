import { useState } from "react";
import { useForm } from "react-hook-form";
import { Link, useNavigate } from "react-router-dom";
import { AiOutlineLogin } from "react-icons/ai";
import { FaLock } from "react-icons/fa";
import InputField from "../shared/InputField";
import { useDispatch } from "react-redux";
import { authenticateSignInUser } from "../../store/actions";
import toast from "react-hot-toast";
import Spinners from "../shared/Spinners";
import { FaGithub } from "react-icons/fa";
import { FcGoogle } from "react-icons/fc";
import Verify2FALogin from "./Verify2FALogin";
import api from "../../api/api";
import { useTranslation } from "react-i18next";
import LangLink from "../shared/LangLink";
import { useLanguage } from "../../context/LanguageContext";


const LogIn = () => {

    const navigate = useNavigate();
    const lang = useLanguage();
    const [loader, setLoader] = useState(false);
    const [requestingUnlock, setRequestingUnlock] = useState(false);
    const [needs2FA, setNeeds2FA] = useState(false);
    const [temp2FAToken, setTemp2FAToken] = useState(null);
    const [loginEmail, setLoginEmail] = useState(null);
    const [lockedUsername, setLockedUsername] = useState(null);
    const [unlockRequested, setUnlockRequested] = useState(false);
    const dispatch = useDispatch();
    const { t } = useTranslation("auth");
    const {
        register,
        handleSubmit,
        reset,
        formState:{errors},

    } = useForm({
        mode:"onTouched",
    });

    const loginHandler = async (data) => {
        setLockedUsername(null);
        setUnlockRequested(false);
        dispatch(authenticateSignInUser(
            data,
            toast,
            reset,
            navigate,
            setLoader,
            setNeeds2FA,
            setTemp2FAToken,
            setLoginEmail,
            setLockedUsername
        ));
    };

    const handleRequestUnlock = async () => {
        if (!lockedUsername) return;
        setRequestingUnlock(true);
        try {
            const { data } = await api.post("/auth/unlock-request", { username: lockedUsername });
            toast.success(data?.message || t("unlockRequestSent"));
            setUnlockRequested(true);
        } catch (error) {
            toast.error(error?.response?.data?.message || t("requestUnlock"));
        } finally {
            setRequestingUnlock(false);
        }
    };

            const handle2FASuccess = (authData) => {
                    console.log("=== 1. authData primit:", authData);
                    console.log("=== 2. typeof authData:", typeof authData);
                console.log("2FA success, authData:", authData); // vezi ce vine de la backend
                localStorage.setItem("auth", JSON.stringify(authData));
                dispatch({ type: "LOGIN_USER", payload: authData });
                toast.success(t("loginSuccess"));
                setNeeds2FA(false);
                setTemp2FAToken(null);
                setLoginEmail(null);
                reset();
                navigate(`/${lang}`);
            };
    return (
        <div className="min-h-[calc(100vh-64px)] flex justify-center items-center dark:bg-gray-950">

            <form
                onSubmit={handleSubmit(loginHandler)}
                className="sm:w-112.5 w-90 shadow-custom py-8 sm:px-8 px-4 rounded-md dark:bg-gray-800 dark:text-white"
            >

                {/* HEADER */}
                <div className="flex flex-col items-center justify-center space-y-4">
                    <AiOutlineLogin className="text-slate-800 text-5xl dark:text-white" />
                    <h1 className="text-slate-800 text-center font-montserrat lg:text-3xl text-2xl font-bold dark:text-white">
                        {t("loginHere")}
                    </h1>
                </div>

                <hr className="mt-2 mb-5 text-black dark:border-gray-600" />

                {/* INPUTS */}
                <div className="flex flex-col gap-3">
                    <InputField
                        label={t("userName")}
                        required
                        id="username"
                        type="text"
                        message={t("userNameRequired")}
                        placeHolder={t("enterUsername")}
                        register={register}
                        errors={errors}
                    />

                    <InputField
                        label={t("password")}
                        required
                        id="password"
                        type="password"
                        message={t("passwordRequired")}
                        placeHolder={t("enterPassword")}
                        register={register}
                        errors={errors}
                    />
                </div>

                {/* LOGIN BUTTON */}
                <button
                    disabled={loader}
                    className="bg-button-gradient flex gap-2 items-center justify-center font-semibold text-white w-full py-2 hover:text-slate-400 transition-colors duration-100 rounded-sm my-3"
                    type="submit"
                >
                    {loader ? (
                        <>
                            <Spinners /> {t("loading", { ns: "common" })}
                        </>
                    ) : (
                        <>{t("login")}</>
                    )}
                </button>

                {/* ACCOUNT LOCKED NOTICE */}
                {lockedUsername && (
                    <div className="flex flex-col gap-2 mt-3 p-3 rounded-md bg-red-50 dark:bg-red-900/20 border border-red-200 dark:border-red-800">
                        <div className="flex items-center gap-2 text-red-700 dark:text-red-400 text-sm font-medium">
                            <FaLock />
                            <span>{t("accountLocked")}</span>
                        </div>
                        {unlockRequested ? (
                            <p className="text-sm text-green-700 dark:text-green-400">
                                {t("unlockRequestSent")}
                            </p>
                        ) : (
                            <button
                                type="button"
                                onClick={handleRequestUnlock}
                                disabled={requestingUnlock}
                                className="self-start text-sm font-semibold text-red-700 dark:text-red-400 underline hover:text-red-900 dark:hover:text-red-300 disabled:opacity-60"
                            >
                                {requestingUnlock ? t("sendingRequest") : t("requestUnlock")}
                            </button>
                        )}
                    </div>
                )}

                {/* FORGOT PASSWORD */}
                <div className="text-right mt-1">
                    <LangLink
                        to="/forgot-password"
                        className="text-sm text-blue-600 hover:underline font-medium"
                    >
                        {t("forgotPassword")}
                    </LangLink>
                </div>

                {/* OAUTH SECTION */}
                <div className="flex flex-col gap-2 mt-2">

                    <a
                        href={`${import.meta.env.VITE_BACK_END_URL || "http://localhost:8080"}/oauth2/authorization/github`}
                        className="w-full flex items-center justify-center gap-2 bg-black text-white py-2 rounded-md hover:opacity-80 transition font-medium"
                    >
                        <FaGithub className="text-xl" />
                        <span>{t("loginWithGithub")}</span>
                    </a>

                    <a
                        href={`${import.meta.env.VITE_BACK_END_URL || "http://localhost:8080"}/oauth2/authorization/google`}
                        className="w-full flex items-center justify-center gap-2 bg-red-500 text-white py-2 rounded-md hover:opacity-80 transition font-medium"
                    >
                        {/* Adăugăm un fundal alb mic rotund în spatele iconiței Google dacă vrei să se vadă perfect pe roșu, sau o lăsăm simplă */}
                        <div className="bg-white p-0.5 rounded-full flex items-center justify-center">
                            <FcGoogle className="text-lg" />
                        </div>
                        <span>{t("loginWithGoogle")}</span>
                    </a>

                </div>

                {/* REGISTER */}
                <p className="text-center text-sm text-slate-700 dark:text-gray-300 mt-6">
                    {t("dontHaveAccount")}{" "}
                    <LangLink
                        className="font-semibold underline hover:text-black dark:hover:text-white"
                        to="/register"
                    >
                        {t("signUp")}
                    </LangLink>
                </p>

            </form>

            {/* 2FA VERIFICATION MODAL */}
            {needs2FA && temp2FAToken && (
                <Verify2FALogin
                    jwtToken={temp2FAToken}
                    email={loginEmail}
                    onVerifySuccess={handle2FASuccess}
                    onCancel={() => {
                        setNeeds2FA(false);
                        setTemp2FAToken(null);
                        setLoginEmail(null);
                    }}
                />
            )}
        </div>
    );
}
export default LogIn;