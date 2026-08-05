import React, { useState } from 'react'
import { useForm } from 'react-hook-form';
import { FaUserPlus } from 'react-icons/fa';
import { Link, useNavigate } from 'react-router-dom';
import InputField from '../shared/InputField';
import { useDispatch } from 'react-redux';
import { registerNewUser } from '../../store/actions';
import toast from 'react-hot-toast';
import Spinners from '../shared/Spinners';

const Register = () => {
    const navigate = useNavigate();
    const dispatch = useDispatch();
    const [loader,setLoader]= useState(false);

    const {
        register,
        handleSubmit,
        reset,
        formState:{errors},

    } = useForm({
        mode:"onTouched",
    });

    const registerHandler = async (data) => {
        console.log("Register Click");
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
                        Register Here
                    </h1>
                </div>
                <hr className="mt-2 mb-5 text-black"/>
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
                    
                        label="Email"
                        required
                        id="email"
                        type="email"
                        message="*Email is required"
                        placeHolder="Enter your email"
                        register={register}
                        errors={errors}
                    
                    />

                    <InputField
                    
                        label="Password"
                        required
                        id="password"
                        min={6}
                        type="password"
                        message="*Password is required"
                        placeHolder="Enter your password"
                        register={register}
                        errors={errors}
                    
                    />
                    <InputField 
                        label="Password Hint"
                        id="passwordHint"
                        type="text"
                        placeHolder="Ex: Numele animalului meu"
                        register={register}
                        errors={errors} 
            
                    />

                    <div className="flex flex-col gap-1">
                        <label className="text-slate-700 font-medium text-sm">Role</label>
                        <select
                            {...register("role", { required: true })}
                            className="border border-gray-300 rounded-sm px-3 py-2 text-slate-700 focus:outline-none focus:ring-2 focus:ring-purple-400"
                        >
                            <option value="">Select a role</option>
                            <option value="ROLE_USER">User</option>
                            <option value="ROLE_SELLER">Seller</option>
                        </select>
                        {errors.role && (
                            <span className="text-red-500 text-xs">*Role is required</span>
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

                        <Spinners/> Loading...   </>
                        
                        
                       
                    ) : (
                        <> Register</>
                       
                    )}
                  
                </button>
                <p className="text-center text-sm text-slate-700 mt-6">
                    Already have an account?
                    <Link 
                    className="font-semibold underline hover:text-black"
                    to="/login"
                    >
                    <span>Login</span>
                    </Link>
                </p>
            </form>
        </div>
    );
}

export default Register
