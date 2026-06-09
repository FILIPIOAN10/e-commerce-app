import React, { useEffect } from 'react'
import InputField from '../shared/InputField';
import { useForm } from 'react-hook-form';
import { AiOutlineLogin } from "react-icons/ai";
import { FaAddressCard } from 'react-icons/fa';
import { useDispatch, useSelector } from 'react-redux';
import { Link } from 'react-router-dom';
import toast from 'react-hot-toast';
import { addUpdateUserAddress } from '../../store/actions';
import Spinners from "../shared/Spinners";
const AddAddressForm = ({address,setOpenAddressModal}) => {
    const dispatch = useDispatch();

    const {btnLoader} = useSelector((state) => state.errors);
        const {
            register,
            handleSubmit,
            setValue,
            reset,
            formState:{errors},
    
        } = useForm({
            mode:"onTouched",
        });

        const onSaveAddressHandler = async (data) => {
            
                dispatch(addUpdateUserAddress(
                    data,
                    toast,
                    address?.addressId,
                    setOpenAddressModal
                ));
            };


    useEffect (() => {
        if(address?.addressId) {
            setValue("buildingName", address?.buildingName);
            setValue("city",address?.city);
            setValue("street",address?.street);
            setValue("state",address?.state);
            setValue("pincode",address?.pincode);
            setValue("country",address?.country);
        }
    },[address]);
        
    return (
        <div className="">
            <form
            onSubmit={handleSubmit(onSaveAddressHandler)}
            className=""
            >
                <div className="flex justify-center items-center mb-4 font-semibold text-2xl text-slate-800 py-2 px-4">
                    <FaAddressCard className="mr-2 text-2xl"/>
                    {!address?.addressId ? "Add Address" :"Update Addres"}
                </div>
                <div className="flex flex-col gap-4">
                    <InputField
                
                        label="Building Name"
                        required
                        id="buildingName"
                        type="text"
                        message="*Building Name is required"
                        placeHolder="Enter Building Name"
                        min={5}
                        minLengthMessage="Building name trebuie sa aiba cel putin 5 caractere"
                        register={register}
                        errors={errors}
                    
                    />


                    <InputField
                    
                        label="City"
                        required
                        id="city"
                        type="text"
                        message="*City is required"
                        placeHolder="Enter City"
                        min={2}
                        minLengthMessage="Orasul trebuie sa aiba cel putin 2 caractere"
                        register={register}
                        errors={errors}
                    
                    />

                    <InputField
                    
                        label="State"
                        required
                        id="state"
                        type="text"
                        message="*State is required"
                        placeHolder="Enter State"
                        min={4}
                        minLengthMessage="Judetul trebuie sa aiba cel putin 2 caractere"
                        register={register}
                        errors={errors}
                    
                    />

                    
                    <InputField
                    
                        label="Pincode"
                        required
                        id="pincode"
                        type="text"
                        message="*Pincode is required"
                        placeHolder="Enter Pincode"
                        min={4}
                        minLengthMessage="Codul postal trebuie sa aiba cel putin 4 caractere"
                        register={register}
                        errors={errors}
                    
                    />

                <InputField
                    
                        label="Street"
                        required
                        id="street"
                        type="text"
                        message="*Street is required"
                        placeHolder="Enter Street"
                        min={5}
                        minLengthMessage="Strada trebuie sa aiba cel putin 5 caractere"
                        register={register}
                        errors={errors}
                    
                    />

                    
                <InputField
                    
                        label="Country"
                        required
                        id="country"
                        type="text"
                        message="*Country is required"
                        placeHolder="Enter Country"
                        register={register}
                        errors={errors}
                    
                    />
                </div>


                <button
                    disabled={btnLoader}
                    className="text-white bg-custom-blue px-4 py-2 rounded-md mt-4"
                    type="submit"
                >
                    {btnLoader ? (
                        <> 
                       <Spinners/> Loading...   </>
                       
                    ) : (
                        <> Save</>
                       
                    )}
                  
                </button>

            </form>
        </div>
    );
}

export default AddAddressForm
