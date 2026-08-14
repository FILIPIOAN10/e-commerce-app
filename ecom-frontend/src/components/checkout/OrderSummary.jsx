import React, { useEffect } from 'react'
import { formatPriceCalculation } from '../../utils/formatPrice'
import { useDispatch, useSelector } from 'react-redux'
import CouponInput from './CouponInput'
import { previewOrder } from '../../store/actions'

const OrderSummary = ({totalPrice,cart,address,paymentMethod}) => {
  const dispatch = useDispatch();
  const { appliedCoupons, discountAmount, shippingCost, finalAmount } = useSelector((state) => state.coupon)
  const displaySubtotal = totalPrice;
  const displayDiscount = discountAmount || 0;
  const displayShipping = shippingCost || 0;
  const displayTotal = finalAmount > 0 ? finalAmount : (displaySubtotal - displayDiscount + displayShipping);

  useEffect(() => {
    if (address?.addressId) {
      dispatch(previewOrder(address.addressId));
    }
  }, [dispatch, address, appliedCoupons]);

  return (
    <div className="container mx-auto px-4 mb-8">
        <div className="flex flex-wrap">
            <div className="w-full lg:w-8/12 pr-4">
                <div className="space-y-4">
                    <div className="p-4 border rounded-lg shadow-custom">
                        <h2 className="text-2xl  font-semibold mb-2">Billing Address</h2>
                        <p>
                            <strong>
                                Building Name:
                            </strong>
                            {address?.buildingName}
                        </p>
                        <p>
                            <strong>City:</strong>
                            {address?.city}
                        </p>
                        <p>
                            <strong>Street:</strong>
                            {address?.street}
                        </p>

                        <p>
                            <strong>State:</strong>
                            {address?.state}
                        </p>

                        <p>
                            <strong>Pincode:</strong>
                            {address?.pincode}
                        </p>

                        <p>
                            <strong>Country:</strong>
                            {address?.country}
                        </p>
                    </div>
                    <div className='p-4 border rounded-lg'>
                        <h2 className='text-2xl font-semibold mb-2'>
                            Payment Method
                        </h2>
                        <p>
                            <strong>Method:</strong>
                            {paymentMethod}
                        </p>
                    </div>
                    <div className='p-4 border rounded-lg shadow-sm'>
                        <h2 className='text-2xl font-semibold mb-2'>
                            Order Items
                        </h2>
                        <div className='space-y-2'>
                            {cart?.map((item) => (
                                <div key={item?.productId} className='flex items-center'>
                                    <img src= {`${import.meta.env.VITE_BACK_END_URL}/images/${
                                        item?.image
                                    }`}
                                        alt='Product'
                                        className='w-12 h-12 rounded'
                                        >
                                    </img>
                                    <div className='text-gray-500'>
                                        <p>
                                            {item?.productName}
                                        </p>
                                        <p>
                                            {item?.quantity} x ${item?.specialPrice} =${
                                                formatPriceCalculation(item?.quantity,item?.specialPrice)
                                            }
                                        </p>
                                    </div>
                                </div>

                            ))}
                        </div>
                    </div>
                </div>
            </div>

        <div className="w-full lg:w-4/12 mt-4 lg:mt-0">
            <div className="border rounded-lg shadow-sm p-4 space-y-4">
                <h2 className="text-2xl font-semibold mb-b">Order Summary</h2>

                    <div className='space-y-2'>
                    <div className="flex justify-between">
                        <span>Products</span>
                        <span>${formatPriceCalculation(displaySubtotal,1)}</span>
                    </div>
                    {appliedCoupons && appliedCoupons.length > 0 && (
                        <div className="flex justify-between text-green-600">
                            <span>Coupons ({appliedCoupons.join(", ")})</span>
                            <span>-${formatPriceCalculation(displayDiscount,1)}</span>
                        </div>
                    )}
                    <div className="flex justify-between">
                        <span>Shipping</span>
                        <span>${formatPriceCalculation(displayShipping,1)}</span>
                    </div>
                    <div className="flex justify-between font-semibold">
                            <span>Total</span>
                            <span>${formatPriceCalculation(displayTotal,1)}</span>
                    </div>
                </div>

                <CouponInput orderAmount={totalPrice} />
            </div>
        </div>
        </div>
    </div>
  )
}

export default OrderSummary
