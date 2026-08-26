import React, { useState } from 'react'
import { useForm } from 'react-hook-form';
import { FaUserPlus } from 'react-icons/fa';
import { Link, useNavigate } from 'react-router-dom';
import InputField from '../shared/InputField';
import { useDispatch } from 'react-redux';
import { registerNewUser } from '../../store/actions';
import toast from 'react-hot-toast';
import Spinners from '../shared/Spinners';
import { useTranslation } from 'react-i18next';

const Register = () => {
    const navigate = useNavigate();
    const dispatch = useDispatch();
    const [loader,setLoader]= useState(false);
    const { t } = useTranslation("auth");

    const {
        register,
        handleSubmit,
        reset,
        formState:{errors},

    } = useForm({
        mode:"onTouched",
    });

    const registerHandler = async (data) => {
        dispatch(registerNewUser(data,toast,reset,navigate,setLoader));

    };

    return (
        <div className="min-h-[calc(100vh-64px)] flex justify-center items-center dark:bg-gray-950">
            <form
            onSubmit={handleSubmit(registerHandler)}
            className="sm:w-112.5 w-90 shadow-custom py-8 sm:px-8 px-4 rounded-md dark:bg-gray-800 dark:text-white"
            >
                <div className=" flex flex-col items-center justify-center space-y-4">
                    <FaUserPlus className="text-slate-800 text-5xl dark:text-white"/>
                    <h1 className="text-slate-800 text-center font-montserrat lg:text-3xl text-2xl font-bold dark:text-white">
                        {t("registerHere")}
                    </h1>
                </div>
                <hr className="mt-2 mb-5 text-black dark:border-gray-600"/>
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
                    
                        label={t("email")}
                        required
                        id="email"
                        type="email"
                        message={t("emailRequired")}
                        placeHolder={t("enterEmail")}
                        register={register}
                        errors={errors}
                    
                    />

                    <InputField
                    
                        label={t("password")}
                        required
                        id="password"
                        min={6}
                        type="password"
                        message={t("passwordRequired")}
                        placeHolder={t("enterPassword")}
                        register={register}
                        errors={errors}
                    
                    />
                    <InputField 
                        label={t("passwordHint")}
                        id="passwordHint"
                        type="text"
                        placeHolder="Ex: Numele animalului meu"
                        register={register}
                        errors={errors} 
            
                    />

                    <div className="flex flex-col gap-1">
                        <label className="text-slate-700 dark:text-gray-300 font-medium text-sm">{t("role")}</label>
                        <select
                            {...register("role", { required: true })}
                            className="border border-gray-300 dark:border-gray-600 dark:bg-gray-700 rounded-sm px-3 py-2 text-slate-700 dark:text-gray-200 focus:outline-none focus:ring-2 focus:ring-purple-400"
                        >
                            <option value="">{t("selectRole")}</option>
                            <option value="ROLE_USER">{t("user")}</option>
                            <option value="ROLE_SELLER">{t("seller")}</option>
                        </select>
                        {errors.role && (
                            <span className="text-red-500 text-xs">{t("roleRequired")}</span>
                        )}
                    </div>
                </div>


                <button
                    disabled={loader}
                    className="bg-button-gradient flex gap-2 items-center justify-center font-semibold text-white w-full py-2 hover:text-slate-400 transition-colors duration-100 rounded-sm my-3"
                    type="submit"
                >
                    {loader ? (
                    <>

                        <Spinners/> {t("loading", { ns: "common" })}   </>
                        
                        
                       
                    ) : (
                        <> {t("register")}</>
                       
                    )}
                  
                </button>
                <p className="text-center text-sm text-slate-700 dark:text-gray-300 mt-6">
                    {t("alreadyHaveAccount")}
                    <Link 
                    className="font-semibold underline hover:text-black dark:hover:text-white"
                    to="/login"
                    >
                    <span>{t("login")}</span>
                    </Link>
                </p>
            </form>
        </div>
    );
}

export default Register
