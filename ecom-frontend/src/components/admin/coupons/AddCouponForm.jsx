import React, { useState, useEffect } from "react";
import { useDispatch } from "react-redux";
import toast from "react-hot-toast";
import { createCouponAction, updateCouponAction } from "../../../store/actions";
import InputField from "../../shared/InputField";
import { useForm } from "react-hook-form";

const AddCouponForm = ({ setOpen, open, coupon, update = false }) => {
    const dispatch = useDispatch();

    const {
        register,
        handleSubmit,
        reset,
        setValue,
        watch,
        formState: { errors },
    } = useForm({
        mode: "onTouched",
        defaultValues: {
            code: "",
            discountPercent: 10,
            expiryDate: "",
            maxUses: 100,
            active: true,
        },
    });

    const isActive = watch("active");

    useEffect(() => {
        if (update && coupon) {
            setValue("code", coupon.code || "");
            setValue("discountPercent", coupon.discountPercent || 10);
            setValue("expiryDate", coupon.expiryDate || "");
            setValue("maxUses", coupon.maxUses || 100);
            setValue("active", coupon.active !== false);
        } else {
            reset();
        }
    }, [update, coupon, setValue, reset]);

    const onSubmit = (data) => {
        if (!data.expiryDate) {
            toast.error("Expiry date is required");
            return;
        }
        const couponData = {
            code: data.code.toUpperCase(),
            discountPercent: Number(data.discountPercent),
            expiryDate: data.expiryDate,
            maxUses: Number(data.maxUses),
            active: data.active,
        };

        if (update && coupon) {
            dispatch(updateCouponAction(coupon.id, couponData, toast, setOpen));
        } else {
            dispatch(createCouponAction(couponData, toast, setOpen));
        }
    };

    return (
        <div className="py-5 relative h-full">
            <form className="space-y-5" onSubmit={handleSubmit(onSubmit)}>
                <div className="flex md:flex-row flex-col gap-4 w-full">
                    <InputField
                        label="Coupon Code"
                        required
                        id="code"
                        type="text"
                        message="This field is required*"
                        placeHolder="e.g. SAVE10"
                        register={register}
                        errors={errors}
                        min={3}
                    />
                </div>

                <div className="flex md:flex-row gap-4 w-full">
                    <div className="flex-1">
                        <InputField
                            label="Discount Percent"
                            required
                            id="discountPercent"
                            type="number"
                            message="This field is required*"
                            placeHolder="10"
                            register={register}
                            errors={errors}
                            minValue={1}
                            maxValue={100}
                        />
                    </div>
                    <div className="flex-1">
                        <InputField
                            label="Max Uses"
                            required
                            id="maxUses"
                            type="number"
                            message="This field is required*"
                            placeHolder="100"
                            register={register}
                            errors={errors}
                            minValue={1}
                        />
                    </div>
                </div>

                <div className="flex md:flex-row flex-col gap-4 w-full">
                    <div className="flex flex-col gap-1 w-full">
                        <label
                            htmlFor="expiryDate"
                            className="font-semibold text-sm text-slate-800"
                        >
                            Expiry Date
                        </label>
                        <input
                            type="date"
                            id="expiryDate"
                            className={`px-2 py-2 border outline-none bg-transparent text-slate-800 rounded-md ${
                                errors.expiryDate?.message ? "border-red-500" : "border-slate-700"
                            }`}
                            {...register("expiryDate", { required: { value: true, message: "Expiry date is required*" } })}
                        />
                        {errors.expiryDate?.message && (
                            <p className="text-sm font-semibold text-red-600 mt-0">
                                {errors.expiryDate?.message}
                            </p>
                        )}
                    </div>
                </div>

                <div className="flex items-center gap-3 pt-1">
                    <label className="relative inline-flex items-center cursor-pointer">
                        <input
                            type="checkbox"
                            className="sr-only peer"
                            {...register("active")}
                        />
                        <div className="w-11 h-6 bg-gray-200 peer-focus:outline-none rounded-full peer peer-checked:after:translate-x-full peer-checked:after:border-white after:content-[''] after:absolute after:top-[2px] after:left-[2px] after:bg-white after:border-gray-300 after:border after:rounded-full after:h-5 after:w-5 after:transition-all peer-checked:bg-blue-500"></div>
                    </label>
                    <span className="font-semibold text-sm text-slate-800">
                        {isActive ? "Active" : "Inactive"}
                    </span>
                </div>

                <div className="flex w-full justify-between items-center absolute bottom-14">
                    <button
                        disabled={open}
                        onClick={() => setOpen(false)}
                        type="button"
                        className="border border-borderColor rounded-[5px] font-metropolis text-textColor py-[10px] px-4 text-sm font-medium hover:bg-gray-50 transition-colors"
                    >
                        Cancel
                    </button>
                    <button
                        disabled={open}
                        type="submit"
                        className="font-metropolis rounded-[5px] bg-custom-blue hover:bg-blue-800 text-white py-[10px] px-6 text-sm font-medium transition-colors"
                    >
                        {open ? "Loading.." : update ? "Update Coupon" : "Create Coupon"}
                    </button>
                </div>
            </form>
        </div>
    );
};

export default AddCouponForm;
