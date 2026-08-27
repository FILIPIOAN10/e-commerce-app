import React, { useEffect } from 'react';
import { FaShoppingCart } from 'react-icons/fa';
import { MdArrowBack } from 'react-icons/md';
import { useDispatch, useSelector } from 'react-redux';
import { useLocation } from 'react-router-dom';

import { getUserOrders } from '../../store/actions';
import OrderTable from '../admin/orders/OrderTable';
import Skeleton from '../shared/Skeleton';
import EmptyState from '../shared/EmptyState';
import { useLanguage } from '../../context/LanguageContext';

const ProfileOrders = () => {
  const dispatch = useDispatch();
  const location = useLocation();
  const lang = useLanguage();

  const { userOrders, pagination } = useSelector((state) => state.order);
  const { isLoading } = useSelector((state) => state.errors);

  useEffect(() => {
    const queryString = location.search.startsWith('?')
      ? location.search.substring(1)
      : location.search;

    dispatch(getUserOrders(queryString));
  }, [dispatch, location.search]);

  if (isLoading) {
    return (
      <div className='pb-6 pt-20 px-4 max-w-7xl mx-auto'>
        <h1 className='text-3xl font-bold text-gray-800 dark:text-white mb-2'>Istoricul Meu de Comenzi</h1>
        <Skeleton variant="table" count={5} />
      </div>
    );
  }

  const emptyOrder = !userOrders || userOrders?.length === 0;

  return (
    <div className='pb-6 pt-20 px-4 max-w-7xl mx-auto'>
      <div className='mb-6'>
        <h1 className='text-3xl font-bold text-gray-800 dark:text-white'>Istoricul Meu de Comenzi</h1>
        <p className='text-gray-500 dark:text-gray-400 text-sm'>Aici poți urmări starea tuturor comenzilor tale plasate.</p>
      </div>

      {emptyOrder ? (
        <EmptyState
          icon={FaShoppingCart}
          title="Nu ai plasat nicio comandă încă"
          message="Comenzile tale finalizate vor apărea în această listă."
          action={{ label: "Start Shopping", path: `/${lang}`, icon: MdArrowBack }}
        />
      ) : (
        /* Trimitem `userOrders` ca prop-ul `adminOrder` în componenta ta de tabelă.
           Dacă în interiorul OrderTable nu ai editări sau butoane condiționate de rol, 
           tabela va randa istoricul perfect!
        */
        <OrderTable adminOrder={userOrders} pagination={pagination} />
      )}
    </div>
  );
};

export default ProfileOrders;