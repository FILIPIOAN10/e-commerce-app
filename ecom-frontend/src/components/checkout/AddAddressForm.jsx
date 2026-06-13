import React, { useEffect, useState } from 'react'
import InputField from '../shared/InputField';
import SelectField from '../shared/SelectField'; 
import { useForm } from 'react-hook-form';
import { FaAddressCard } from 'react-icons/fa';
import { useDispatch, useSelector } from 'react-redux';
import toast from 'react-hot-toast';
import { addUpdateUserAddress } from '../../store/actions';
import Spinners from "../shared/Spinners";

import { Country, State, City } from 'country-state-city';

const AddAddressForm = ({address, setOpenAddressModal}) => {
    const dispatch = useDispatch();
    const {btnLoader} = useSelector((state) => state.errors);

    const [countries, setCountries] = useState([]);
    const [states, setStates] = useState([]);
    const [cities, setCities] = useState([]);
    
    const {
        register,
        handleSubmit,
        setValue,
        watch, 
        formState:{errors},
    } = useForm({
        mode:"onTouched",
    });

    const selectedCountryName = watch("country");
    const selectedStateName = watch("state");

    const onSaveAddressHandler = async (data) => {
        dispatch(addUpdateUserAddress(
            data,
            toast,
            address?.addressId,
            setOpenAddressModal
        ));
    };

    // =========================================================================
    // MODIFICARE: FUNCȚIE PENTRU CONFIGURAREA VALIDĂRII DINAMICE A PINCODE-ULUI
    // =========================================================================
    const getPincodeValidationRules = () => {
        if (selectedCountryName === "Romania") {
            return {
                pattern: {
                    value: /^\d{6}$/,
                    message: "Codul poștal din România trebuie să aibă exact 6 cifre (ex: 400123)"
                }
            };
        }
        if (selectedCountryName === "United States") {
            return {
                pattern: {
                    value: /^\d{5}$/,
                    message: "Codul poștal din SUA trebuie să aibă exact 5 cifre (ex: 90210)"
                }
            };
        }
        // Pentru restul țărilor, lăsăm validarea standard fără un tipar strict de cifre
        return {};
    };

    useEffect(() => {
        const allCountries = Country.getAllCountries().map(c => c.name);
        setCountries(allCountries);
    }, []);

    useEffect(() => {
        if (selectedCountryName) {
            const countryObj = Country.getAllCountries().find(c => c.name === selectedCountryName);
            if (countryObj) {
                const countryStates = State.getStatesOfCountry(countryObj.isoCode).map(s => s.name);
                setStates(countryStates);
                
                if (address?.addressId && address.country === selectedCountryName) {
                    setValue("state", address.state);
                } else {
                    setValue("state", "");
                    setValue("city", "");
                    setCities([]);
                }
            }
        } else {
            setStates([]);
            setCities([]);
        }
    }, [selectedCountryName, address, setValue]);

    useEffect(() => {
        if (selectedStateName && selectedCountryName) {
            const countryObj = Country.getAllCountries().find(c => c.name === selectedCountryName);
            if (countryObj) {
                const stateObj = State.getStatesOfCountry(countryObj.isoCode).find(s => s.name === selectedStateName);
                if (stateObj) {
                    const stateCities = City.getCitiesOfState(countryObj.isoCode, stateObj.isoCode).map(c => c.name);
                    setCities(stateCities);

                    if (address?.addressId && address.state === selectedStateName) {
                        setValue("city", address.city);
                    } else {
                        setValue("city", "");
                    }
                }
            }
        } else {
            setCities([]);
        }
    }, [selectedStateName, selectedCountryName, address, setValue]);

    useEffect (() => {
        if(address?.addressId) {
            setValue("buildingName", address?.buildingName);
            setValue("street", address?.street);
            setValue("pincode", address?.pincode);
            setValue("country", address?.country); 
        }
    }, [address, setValue]);
        
    return (
        <div className="">
            <form onSubmit={handleSubmit(onSaveAddressHandler)} className="">
                <div className="flex justify-center items-center mb-4 font-semibold text-2xl text-slate-800 py-2 px-4">
                    <FaAddressCard className="mr-2 text-2xl"/>
                    {!address?.addressId ? "Add Address" : "Update Address"}
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

                    <SelectField
                        label="Country"
                        required
                        id="country"
                        options={countries}
                        placeholder="Select Country"
                        message="*Country is required"
                        register={register}
                        errors={errors}
                    />

                    <SelectField
                        label="State / Region"
                        required
                        id="state"
                        options={states}
                        placeholder="Select State"
                        message="*State is required"
                        disabled={!selectedCountryName}
                        register={register}
                        errors={errors}
                    />

                    <SelectField
                        label="City"
                        required
                        id="city"
                        options={cities}
                        placeholder="Select City"
                        message="*City is required"
                        disabled={!selectedStateName}
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
                        label="Pincode"
                        required
                        id="pincode"
                        type="text"
                        message="*Pincode is required"
                        placeHolder="Enter Pincode"
                        min={4}
                        minLengthMessage="Codul postal trebuie sa aiba cel putin 4 caractere"
                        register={(name, options) => register(name, { ...options, ...getPincodeValidationRules() })}
                        errors={errors}
                    />
                </div>

                <button
                    disabled={btnLoader}
                    className="text-white bg-custom-blue px-4 py-2 rounded-md mt-4 w-full font-semibold"
                    type="submit"
                >
                    {btnLoader ? (
                        <><Spinners/> Loading...</>
                    ) : (
                        <>Save Address</>
                    )}
                </button>
            </form>
        </div>
    );
}

export default AddAddressForm;