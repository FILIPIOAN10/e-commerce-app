import React, { useEffect, useState } from 'react'
import { useForm } from 'react-hook-form'
import InputField from '../../shared/InputField';
import { Button, Skeleton } from '@mui/material';
import {addNewProductFromDashboard, fetchCategories, updateProductFromDashboard } from '../../../store/actions';
import { useDispatch, useSelector } from 'react-redux';
import Spinners from '../../shared/Spinners';
import toast from 'react-hot-toast';
import SelectTextField from '../../shared/SelectTextField';
import ErrorPage from '../../shared/ErrorPage';

const AddProductForm = ({setOpen,product,update=false}) => {
  const [loader,setLoader] = useState(false);
  const [selectCategory,setSelectedCategory] = useState();
  const {categories} = useSelector((state) => state.products); 
  const {categoryLoader,errorMessage} = useSelector((state) => state.errors);
  const { user } = useSelector((state) => state.auth);
  const isAdmin = user && user?.roles?.includes("ROLE_ADMIN");
  const dispatch = useDispatch();
  const {
        register,
        handleSubmit,
        reset,
        setValue,
        watch,
        formState: { errors}
        } = useForm({
            mode :"onTouched"
        });
    
    const price = watch("price");
    const discount = watch("discount");
    const calculateSpecialPrice = (priceValue, discountValue) =>{
        const numericPrice = Number(priceValue);
        const numericDiscount= Number(discountValue || 0);

        if(!Number.isFinite(numericPrice) || numericPrice<0){
            return "";
        }

        const boundedDiscount = Math.min(Math.max(Number.isFinite(numericDiscount) ? numericDiscount :0,0), 100);
        return Number((numericPrice - ((boundedDiscount *0.01) * numericPrice).toFixed(2)));
    };

    useEffect(() => {
        setValue("specialPrice", calculateSpecialPrice(price, discount), {
            shouldValidate: true,
            shouldDirty: true,
        });
    }, [price, discount, setValue]);
    const saveProductHandler = (data) => {

        const saveProductData = {
            ...data,
            price:Number(data.price),
            quantity:Number(data.quantity),
            discount:Number(data.discount || 0),
            specialPrice : calculateSpecialPrice(data.price,data.discount),
        };
        if(!update){

            if(!selectCategory?.categoryId){
                toast.error("Please select a category")
                return;
            }
            const sendData ={
                ...saveProductData,
                categoryId: selectCategory.categoryId,
            };
            // create new product logic 
            dispatch(addNewProductFromDashboard(
                sendData,toast,reset,setLoader,setOpen,isAdmin
            ));
        }else {
            const sendData ={
                ...saveProductData,
                id: product.id,
            };
            dispatch(updateProductFromDashboard(sendData,toast,reset,setLoader,setOpen,isAdmin));
        }
    };

        useEffect( () => {
            if( update && product){
                setValue("productName",product?.productName);
                setValue("price",product?.price);
                setValue("quantity",product?.quantity);
                setValue("discount",product?.discount);
                setValue("specialPrice",product?.specialPrice);
                setValue("description",product?.description);
                setValue("tags",product?.tags || "");
            }
        },[update,product]);

        useEffect( () => {
            if(!update){
                dispatch(fetchCategories());
            }

        },[dispatch,update] )


        useEffect(() => {
            if(categories){
                setSelectedCategory(categories[0])
            }
        }, [categories]);
        useEffect( () => {
            if(!categories && categories){
                setSelectedCategory(categories[0]);
            }

        },[categories,categoryLoader] )

        if (categoryLoader) return <Skeleton/>
        if (errorMessage) return <ErrorPage message={errorMessage}/>
    return (
        <div className='py-5 relative h-full'>
            <form className='space-y-4'
            
                onSubmit={handleSubmit(saveProductHandler)}>
                <div className='flex md:flex-row flex-col gap-4 w-full'>
                    <InputField 
                        label="Product Name"
                        required
                        id="productName"
                        type="text"
                        message="This field is required"
                        register={register}
                        placeHolder="Product Name"
                        errors={errors}
                        />
                    {!update && (
                        <SelectTextField
                            label="Select Categories"
                            select={selectCategory}
                            setSelect={setSelectedCategory}
                            lists={categories}
                        />
                    )}
                </div>


                <div className='flex md:flex-row flex-col gap-4 w-full'>
                    <InputField 
                        label="Price"
                        required
                        id="price"
                        type="number"
                        message="This field is required"
                        minValue={0}
                        step="0.01"
                        placeHolder="Product Price"
                        register={register}
                        errors={errors}
                        />

                    <InputField 
                        label="Quantity"
                        required
                        id="quantity"
                        type="number"
                        message="This field is required"
                        register={register}
                        placeHolder="Product Quantity"
                        minValue={0}
                        step="1"
                        errors={errors}
                        />
                </div>

        <div className="flex md:flex-row flex-col gap-4 w-full">
          <InputField
            label="Discount"
            id="discount"
            type="number"
            message="This field is required*"
            placeHolder="Product Discount"
            minValue={0}
            maxValue={100}
            step="0.01"
            register={register}
            errors={errors}
          />
          <InputField
            label="Special Price {calculated}"
            id="specialPrice"
            type="number"
            message="This field is required*"
            placeHolder="Calculated from price and discount"
            readOnly
            register={register}
            errors={errors}
          />
        </div>
        <div className="flex flex-col gap-4 w-full">
        <label htmlFor='desc'
            className='font-semibold text-sm text-slate-800'>
            Description
        </label>
        <textarea
            
            rows={5}
            placeholder="Add product description ..."
            className={`px-4 py-2 w-full border outline-none bg-transparent text-slate-800 rounded-md ${
                errors["description"]?.message ? "border-red-500": "border-slate-700"
            }`}
            maxLength={255}
            {...register("description",{
                required: {value: true, message:"Description is required"},
                minLength:{value:10, message:"Descrierea trebuie sa aiba cel putin 10 caractere"}
            })}
            />

                        {errors["description"]?.message && (
                <p className="text-sm font-semibold text-red-600 mt-0">
                    {errors["description"]?.message}
                </p>
            )}
        </div>
            <InputField
                label="Tags"
                id="tags"
                type="text"
                placeHolder="gradinarit,curte,pamant,unelte"
                register={register}
                errors={errors}
                />
                <div className='flex w-full justify-between items-center pt-4'>
                <Button disabled={loader}
                            onClick={() => setOpen(false)}
                            variant='outlined'
                            className='text-white py-[10px] px-4 text-sm font-medium'>
                            Cancel
                        </Button>
                        <Button
                        disabled={loader}
                        type='submit'
                        variant='contained'
                        color='primary'
                        className='bg-custom-blue text-white py-[10px] px-4 text-sm font-medium'
                        >
                            {loader ? (
                                <div className='flex gap-2 items-center'>
                                    <Spinners/> Loading...
                                </div>
                            ) : (
                                "Save"
                            )}  
                        </Button>
                    </div>
            </form>
        </div>
  )
}

export default AddProductForm
