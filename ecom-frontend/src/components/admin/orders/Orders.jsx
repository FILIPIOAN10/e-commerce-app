import React from 'react'
import { FaShoppingCart } from 'react-icons/fa';
import OrderTable from './OrderTable';
import { useSelector } from 'react-redux';
import useOrderFilter from '../../../hooks/useOrderFilter';
import Skeleton from '../../shared/Skeleton';

const Orders = () => {
  const {adminOrder,pagination} = useSelector((state) => state.order);
  const { isLoading } = useSelector((state) => state.errors);

  // fetching all the orders
  useOrderFilter();

  if (isLoading) {
    return (
      <div className='pb-6 pt-20 px-4 max-w-7xl mx-auto'>
        <Skeleton variant="table" count={5} />
      </div>
    );
  }

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
