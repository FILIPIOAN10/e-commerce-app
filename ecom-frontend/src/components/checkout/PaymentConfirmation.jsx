import { Skeleton } from '@mui/material';
import React, { useEffect, useState } from 'react'
import { FaCheckCircle } from 'react-icons/fa';
import { useDispatch, useSelector } from 'react-redux';
import { useLocation } from 'react-router-dom'
import { stripePaymentConfirmation } from '../../store/actions';
import toast from 'react-hot-toast';

const PaymentConfirmation = () => {
    const location = useLocation();
    const searchParams = new URLSearchParams(location.search);
    const dispatch = useDispatch();
    const [, setErrorMessage] = useState("");
    const {cart} = useSelector((state)=> state.carts);
    const [loading,setLoading] = useState(false);

    const paymentIntent = searchParams.get("payment_intent");
    const clientSecret = searchParams.get("payment_intent_client_secret");

    const redirectStatus = searchParams.get("redirect_status");

      const {selectedUserCheckoutAddress} = useSelector(
          (state) => state.auth
        );
    useEffect(() => {
        // Checkout is complete, clear saved progress
        sessionStorage.removeItem("checkoutProgress");
    }, []);

    useEffect(() => {
        if(paymentIntent &&
            clientSecret &&
            redirectStatus &&
            cart &&
            cart?.length > 0)
        {
            console.log(selectedUserCheckoutAddress);
            const sendData = {
                    "addressId":selectedUserCheckoutAddress.addressId,
                    "pgName":"Stripe",
                    "pgPaymentId":paymentIntent,
                    "pgStatus":"succeeded",
                    "pgResponseMessage":"Payment successful"
                };
                console.log(selectedUserCheckoutAddress);
                console.log(sendData);
            dispatch(stripePaymentConfirmation(sendData, setErrorMessage,setLoading,toast));
        }
    },[paymentIntent,clientSecret,redirectStatus,cart,dispatch,selectedUserCheckoutAddress]);
  return (
    <div className='min-h-screen flex items-center justify-center dark:bg-gray-950'>
      {loading ? (
        <div className='max-w-xl mx-auto'>
                <Skeleton/>
                </div>
      ) :(
        <div className="p-8 rounded-lg shadow-lg text-center max-w-md mx-auto border border-gray-200 dark:border-gray-700 dark:bg-gray-800 dark:text-white">
            <div className="text-green-500 mb-4 flex justify-center">
                <FaCheckCircle size={64}/>
            </div>
            <h2  className='text-3xl font-bold text-gray-800 dark:text-white mb-2'>Payment Successful!</h2>
            <p className="dark:text-gray-300">
                Thank you for your purchase! Your payment was successful,
                processing your order. 
            </p>
        </div>
      )}
    </div>
  )
}

export default PaymentConfirmation
