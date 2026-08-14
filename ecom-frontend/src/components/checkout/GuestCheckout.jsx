import React, { useState } from 'react'
import { useSelector } from 'react-redux'
import { useNavigate } from 'react-router-dom'
import toast from 'react-hot-toast'
import { formatPrice } from '../../utils/formatPrice'
import { placeGuestOrder } from '../../store/actions'
import { useDispatch } from 'react-redux'

const GuestCheckout = () => {
    const { cart } = useSelector((state) => state.carts)
    const { appliedCoupons, shippingCost, discountAmount, finalAmount } = useSelector((state) => state.coupon)
    const dispatch = useDispatch()
    const navigate = useNavigate()

    const [form, setForm] = useState({
        email: '',
        buildingName: '',
        street: '',
        city: '',
        state: '',
        country: 'Romania',
        pincode: '',
    })
    const [loading, setLoading] = useState(false)

    const subtotal = cart?.reduce((acc, cur) => acc + Number(cur?.specialPrice) * Number(cur?.quantity), 0) || 0

    const handleChange = (e) => {
        setForm({ ...form, [e.target.name]: e.target.value })
    }

    const handleSubmit = async (e) => {
        e.preventDefault()
        if (!form.email || !form.street || !form.city || !form.country || !form.pincode) {
            toast.error('Please fill in the address and email')
            return
        }
        if (!cart || cart.length === 0) {
            toast.error('Your cart is empty')
            return
        }

        const address = { ...form }
        const items = cart.map((item) => ({ productId: item.productId, quantity: item.quantity }))

        setLoading(true)
        dispatch(placeGuestOrder({
            email: form.email,
            address,
            items,
            couponCodes: appliedCoupons || [],
            paymentMethod: 'Cash on Delivery',
        }, setLoading, navigate, toast))
    }

    return (
        <div className='py-14 px-4 min-h-[calc(100vh-100px)]'>
            <h1 className='text-3xl font-bold text-center mb-8'>Guest Checkout</h1>
            <div className='max-w-4xl mx-auto flex flex-wrap gap-6'>
                <div className='w-full lg:w-7/12 space-y-4'>
                    <form onSubmit={handleSubmit} className='space-y-4 p-4 border rounded-lg'>
                        <h2 className='text-xl font-semibold'>Contact & Shipping</h2>
                        <input
                            name='email'
                            type='email'
                            placeholder='Email'
                            value={form.email}
                            onChange={handleChange}
                            className='w-full border rounded-lg px-3 py-2'
                            required
                        />
                        <input
                            name='buildingName'
                            placeholder='Building Name'
                            value={form.buildingName}
                            onChange={handleChange}
                            className='w-full border rounded-lg px-3 py-2'
                        />
                        <input
                            name='street'
                            placeholder='Street'
                            value={form.street}
                            onChange={handleChange}
                            className='w-full border rounded-lg px-3 py-2'
                            required
                        />
                        <input
                            name='city'
                            placeholder='City'
                            value={form.city}
                            onChange={handleChange}
                            className='w-full border rounded-lg px-3 py-2'
                            required
                        />
                        <input
                            name='state'
                            placeholder='State'
                            value={form.state}
                            onChange={handleChange}
                            className='w-full border rounded-lg px-3 py-2'
                        />
                        <input
                            name='country'
                            placeholder='Country'
                            value={form.country}
                            onChange={handleChange}
                            className='w-full border rounded-lg px-3 py-2'
                            required
                        />
                        <input
                            name='pincode'
                            placeholder='Pincode'
                            value={form.pincode}
                            onChange={handleChange}
                            className='w-full border rounded-lg px-3 py-2'
                            required
                        />
                        <button
                            disabled={loading}
                            className='bg-custom-blue text-white px-4 py-2 rounded-lg w-full font-semibold disabled:opacity-50'
                        >
                            {loading ? 'Placing Order...' : 'Place Order'}
                        </button>
                    </form>
                </div>

                <div className='w-full lg:w-4/12'>
                    <div className='border rounded-lg p-4 space-y-2'>
                        <h2 className='text-xl font-semibold mb-2'>Order Summary</h2>
                        {cart?.map((item) => (
                            <div key={item.productId} className='flex justify-between text-sm'>
                                <span>{item.productName} x {item.quantity}</span>
                                <span>{formatPrice(item.specialPrice * item.quantity)}</span>
                            </div>
                        ))}
                        <div className='border-t pt-2 space-y-1'>
                            <div className='flex justify-between'>
                                <span>Subtotal</span>
                                <span>{formatPrice(subtotal)}</span>
                            </div>
                            <div className='flex justify-between'>
                                <span>Discount</span>
                                <span>-{formatPrice(discountAmount || 0)}</span>
                            </div>
                            <div className='flex justify-between'>
                                <span>Shipping</span>
                                <span>{formatPrice(shippingCost || 0)}</span>
                            </div>
                            <div className='flex justify-between font-semibold text-lg'>
                                <span>Total</span>
                                <span>{formatPrice(finalAmount || subtotal)}</span>
                            </div>
                        </div>
                    </div>
                </div>
            </div>
        </div>
    )
}

export default GuestCheckout
