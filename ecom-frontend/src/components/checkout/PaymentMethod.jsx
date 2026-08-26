import { FormControl, FormControlLabel, Radio, RadioGroup } from '@mui/material'
import React, { useEffect } from 'react'
import { useDispatch, useSelector } from 'react-redux'
import { addPaymentMethod } from '../../store/actions';
import { useCreateCartMutation } from '../../store/api/cartApi';
import toast from 'react-hot-toast';

const PaymentMethod = () => {

    const dispatch = useDispatch();
    const [createCart, { isLoading: isCreatingCart }] = useCreateCartMutation();
    const {paymentMethod} = useSelector((state) => state.payment);
    const {cart,cartId} = useSelector((state) => state.carts);
    const {errorMessage} = useSelector((state) => state.errors);


    useEffect( () => {
      if(cart.length > 0 && !cartId &&  !errorMessage && !isCreatingCart ){
        const sendCartItems = cart.map((item) => {
          return{
            productId :item.productId,
            quantity :item.quantity,
          };
        });
        createCart(sendCartItems)
            .unwrap()
            .catch((error) => toast.error(error?.data?.message || "Failed to create cart"));
      }

    },[createCart, isCreatingCart, cartId, cart, errorMessage]);
    const paymentMethodHandler = (method) => {
      dispatch(addPaymentMethod(method));
    }
  return (
    <div className='max-w-md mx-auto p-5 card mt-16'>
        <h1 className='text-2xl font-semibold mb-4 text-heading'>Select Payment Method</h1>
        
            <FormControl>
            <RadioGroup
                aria-label="payment method"
                name="paymentMethod"
                value={paymentMethod}
                onChange={ (e) => paymentMethodHandler(e.target.value)}
            >
                <FormControlLabel
                 value="Stripe" 
                 control={<Radio color='primary' />} 
                 label="Stripe"
                 className='text-gray-700 dark:text-gray-300' />
                {/* <FormControlLabel 
                value="Paypal" 
                control={<Radio color='primary' />} 
                label="Paypal"
                className='text-gray-700'
                 /> */}
            </RadioGroup>
            </FormControl>
    </div>
  )
}

export default PaymentMethod
