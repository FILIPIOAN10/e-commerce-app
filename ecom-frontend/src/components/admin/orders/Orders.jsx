import React from 'react'
import { FaShoppingCart } from 'react-icons/fa';
import OrderTable from './OrderTable';
import { useSelector } from 'react-redux';
import useOrderFilter from '../../../hooks/useOrderFilter';
import Skeleton from '../../shared/Skeleton';
import EmptyState from '../../shared/EmptyState';

const Orders = () => {
  const {adminOrder,pagination} = useSelector((state) => state.order);


  // fetching all the orders
  const { isLoading } = useOrderFilter();

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
        <EmptyState
          icon={FaShoppingCart}
          title="No Orders Placed Yet"
          message="Orders from customers will appear here."
        />
      ) :(

        <OrderTable adminOrder={adminOrder} pagination={pagination}/>
      )}
    </div>
  )
}

export default Orders
