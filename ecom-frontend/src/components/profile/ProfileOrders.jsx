import React, { useEffect } from 'react';
import { FaShoppingCart } from 'react-icons/fa';
import { useDispatch, useSelector } from 'react-redux';
import { useLocation } from 'react-router-dom';

import { getUserOrders } from '../../store/actions';

import OrderTable from '../admin/orders/OrderTable';

const ProfileOrders = () => {
  const dispatch = useDispatch();
  const location = useLocation();
  
  // Extragem starea dedicată utilizatorului normal din reducer-ul modificat anterior
  const { userOrders, pagination } = useSelector((state) => state.order);

  // Ascultăm schimbările de query parametri (de exemplu, când se schimbă pagina: ?pageNumber=1)
  useEffect(() => {
    const queryString = location.search.startsWith('?') 
      ? location.search.substring(1) 
      : location.search;
      
    dispatch(getUserOrders(queryString));
  }, [dispatch, location.search]);

  const emptyOrder = !userOrders || userOrders?.length === 0;

  return (
    <div className='pb-6 pt-20 px-4 max-w-7xl mx-auto'>
      <div className='mb-6'>
        <h1 className='text-3xl font-bold text-gray-800'>Istoricul Meu de Comenzi</h1>
        <p className='text-gray-500 text-sm'>Aici poți urmări starea tuturor comenzilor tale plasate.</p>
      </div>

      {emptyOrder ? (
        <div className='flex flex-col items-center justify-center text-gray-600 py-20 bg-white rounded-lg shadow-sm border border-gray-100'>
          <FaShoppingCart size={50} className='mb-3 text-gray-400' />
          <h2 className='text-2xl font-semibold'>Nu ai plasat nicio comandă încă</h2>
          <p className='text-gray-400 text-sm mt-1'>Comenzile tale finalizate vor apărea în această listă.</p>
        </div>
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