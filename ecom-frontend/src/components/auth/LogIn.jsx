import { useState } from "react";
import { useForm } from "react-hook-form";
import { data, Link, useNavigate } from "react-router-dom";
import { AiOutlineLogin } from "react-icons/ai";
import InputField from "../shared/InputField";
import { useDispatch } from "react-redux";
import { authenticateSignInUser } from "../../store/actions";
import toast from "react-hot-toast";
import Spinners from "../shared/Spinners";
import api from "../../api/api";
import { FaGithub } from "react-icons/fa"; 
import { FcGoogle } from "react-icons/fc";
import Verify2FALogin from "./Verify2FALogin";


const LogIn = () => {

    const navigate = useNavigate();
    const [loader,setLoader]= useState(false);
    const [needs2FA, setNeeds2FA] = useState(false);
    const [temp2FAToken, setTemp2FAToken] = useState(null);
    const [loginEmail, setLoginEmail] = useState(null);
    const dispatch = useDispatch();
    const {
        register,
        handleSubmit,
        reset,
        formState:{errors},

    } = useForm({
        mode:"onTouched",
    });

    const [hint,setHint] = useState(null);

    const fetchHint = async (username) => {
        console.log(" log1 " )
        try {
            const {data}= await api.get(`/auth/hint/${username}`);
            console.log("log 2 "+data);
            if(data?.message && data.message !== "No hit available"){
                setHint(data.message);
            }
            
        } catch (e) {
            console.log("Could not fetch hint",e);
        }
    };

    const loginHandler = async (data) => {
        console.log("Login Click");
        dispatch(authenticateSignInUser(
            data, 
            toast, 
            reset, 
            navigate, 
            setLoader, 
            fetchHint,
            setNeeds2FA,
            setTemp2FAToken,
            setLoginEmail
        ));
    };

            const handle2FASuccess = (authData) => {
                    console.log("=== 1. authData primit:", authData);
                    console.log("=== 2. typeof authData:", typeof authData);
                console.log("2FA success, authData:", authData); // vezi ce vine de la backend
                localStorage.setItem("auth", JSON.stringify(authData));
                dispatch({ type: "LOGIN_USER", payload: authData });
                toast.success("Login Success");
                setNeeds2FA(false);
                setTemp2FAToken(null);
                setLoginEmail(null);
                reset();
                navigate("/");
            };
    return (
        <div className="min-h-[calc(100vh-64px)] flex justify-center items-center">

            <form
                onSubmit={handleSubmit(loginHandler)}
                className="sm:w-112.5 w-90 shadow-custom py-8 sm:px-8 px-4 rounded-md"
            >

                {/* HEADER */}
                <div className="flex flex-col items-center justify-center space-y-4">
                    <AiOutlineLogin className="text-slate-800 text-5xl" />
                    <h1 className="text-slate-800 text-center font-montserrat lg:text-3xl text-2xl font-bold">
                        Login Here
                    </h1>
                </div>

                <hr className="mt-2 mb-5 text-black" />

                {/* INPUTS */}
                <div className="flex flex-col gap-3">
                    <InputField
                        label="UserName"
                        required
                        id="username"
                        type="text"
                        message="*UserName is required"
                        placeHolder="Enter your username"
                        register={register}
                        errors={errors}
                    />

                    <InputField
                        label="Password"
                        required
                        id="password"
                        type="password"
                        message="*Password is required"
                        placeHolder="Enter your password"
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
                            <Spinners /> Loading...
                        </>
                    ) : (
                        <>Login</>
                    )}
                </button>

                {/* OAUTH SECTION */}
                <div className="flex flex-col gap-2 mt-2">

                    <a
                        href={`${import.meta.env.VITE_BACK_END_URL}/oauth2/authorization/github`}
                        className="w-full flex items-center justify-center gap-2 bg-black text-white py-2 rounded-md hover:opacity-80 transition font-medium"
                    >
                        <FaGithub className="text-xl" />
                        <span>Login with GitHub</span>
                    </a>

                    <a
                        href={`${import.meta.env.VITE_BACK_END_URL}/oauth2/authorization/google`}
                        className="w-full flex items-center justify-center gap-2 bg-red-500 text-white py-2 rounded-md hover:opacity-80 transition font-medium"
                    >
                        {/* Adăugăm un fundal alb mic rotund în spatele iconiței Google dacă vrei să se vadă perfect pe roșu, sau o lăsăm simplă */}
                        <div className="bg-white p-0.5 rounded-full flex items-center justify-center">
                            <FcGoogle className="text-lg" />
                        </div>
                        <span>Login with Google</span>
                    </a>

                </div>

                {/* HINT */}
                {hint && (
                    <p className="text-amber-600 text-sm text-center mt-3 p-2 bg-amber-50 rounded">
                        Hint: {hint}
                    </p>
                )}

                {/* REGISTER */}
                <p className="text-center text-sm text-slate-700 mt-6">
                    Don't have an account?{" "}
                    <Link
                        className="font-semibold underline hover:text-black"
                        to="/register"
                    >
                        SignUp
                    </Link>
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