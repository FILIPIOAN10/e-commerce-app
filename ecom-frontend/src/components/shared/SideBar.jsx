import React, { useEffect } from 'react'
import { FaTachometerAlt } from 'react-icons/fa';
import { Link, useLocation } from 'react-router-dom'
import { adminNavigation ,sellerNavigation} from '../../utils';
import { ClassNames } from '@emotion/react';
import classNames from 'classnames';
import { useSelector, useDispatch } from 'react-redux';
import { fetchLowStockCount } from '../../store/actions';
import { useLanguage } from '../../context/LanguageContext';

const SideBar = () => {
  
    const pathName = useLocation().pathname.replace(/^\/[a-z]{2}/, '') || '/';
    const {user} = useSelector((state) => state.auth);
    const { lowStockCount } = useSelector((state) => state.products);
    const dispatch = useDispatch();
    const lang = useLanguage();
    
    const isAdmin = user && user?.roles?.includes("ROLE_ADMIN");

    const sideBarLayout = isAdmin ? adminNavigation : sellerNavigation;

    useEffect(() => {
        if (user && (isAdmin || user?.roles?.includes("ROLE_SELLER"))) {
            dispatch(fetchLowStockCount());
        }
    }, [dispatch, user, isAdmin]);

    return (
    <div className='flex grow flex-col gap-y-7 overflow-y-auto bg-custom-gradient px-6 pb-4 dark:bg-gray-900'>
        {/* Set Header */}
        <div className='flex h-16 shrink-0 gap-x-3 pt-2'>
            <FaTachometerAlt  className='h-8 w-8 text-indigo-500' />
            <h1 className='text-white tex-xl font-bold'>
                {isAdmin ? "Admin Panel": "Seller Panel"}
            </h1>
        </div>
        <nav className='flex flex-1 flex-col'>
            <ul role ='list' className='flex flex-1 flex-col gap-y-7'>
                <li>
                    <ul role= 'list' className='-mx-2 space-y-4'>
                            {sideBarLayout.map((item) => (
                                <li key={item.name}>
                                    <Link 
                                    to={`/${lang}${item.href}`}
                                    className={classNames(
                                        pathName === item.href ?
                                        "bg-custom-blue text-white"
                                        : "text-gray-400 hover:bg-gray-800 hover:text-white",
                                        "group flex gap-x-3 rounded-md p-2 text-sm font-semibold leading-6" 
                                    )}>
                                        <item.icon className='text-2xl' />
                                        {item.name}
                                        {item.name === "Low Stock" && lowStockCount > 0 && (
                                            <span className="ml-auto bg-red-500 text-white text-xs font-bold rounded-full px-2 py-0.5">
                                                {lowStockCount}
                                            </span>
                                        )}
                                    </Link>
                                </li>

                            ))}
                    </ul>
                </li>
            </ul>
        </nav>

    </div>
  )
}

export default SideBar
