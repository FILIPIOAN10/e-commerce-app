import React from 'react'
import { FaShoppingCart } from 'react-icons/fa';
import OrderTable from './OrderTable';
import { useSelector } from 'react-redux';
import useOrderFilter from '../../../hooks/useOrderFilter';

const Orders = () => {
 // const adminOrder = [ { "orderId": 1, "email": "admin@example.com", "items": [ { "orderItemId": 1, "product": { "productId": 1, "productName": "Nikon Z6 II", "image": "cal.png", "description": "Aparat foto mirrorless full-frame cu autofocus rapid și filmare 4K. Potrivit pentru fotografi profesioniști și creatori de conținut.", "quantity": 6, "price": 2000.0, "discount": 9.0, "specialPrice": 1820.0 }, "quantity": 1, "discount": 9.0, "orderedProductPrice": 1820.0 } ], "orderDate": "2026-02-01", "payment": { "paymentId": 1, "paymentMethod": "CARD", "pgPaymentId": "pi_sdacasca", "pgStatus": "succed", "pgResponseMessage": "Payment successful", "pgName": "Stripe" }, "totalAmount": 1820.0, "orderStatus": "Order Accepted!", "addressId": 1 }, ];
 // const pagination = { pageNumber:0,pageSize:50,totalElements:11,totalPages:1,lastPage:true};
  const {adminOrder,pagination} = useSelector((state) => state.order);
  

  // fetching all the orders
  useOrderFilter();

 const emptyOrder= !adminOrder || adminOrder?.length ===0;
  return (
    <div className='pb-6 pt-20 '>
      {emptyOrder ? (
        <div className='flex flex-col items-center justify-center text-gray-600 dark:text-gray-400 py-10'>
            <FaShoppingCart size={50} className='mb-3' />
            <h2 className='text-2xl font-semibold'>No Orders Placed Yet</h2>
        </div>
      ) :(

        <OrderTable adminOrder={adminOrder} pagination={pagination}/>
      )}
    </div>
  )
}

export default Orders
