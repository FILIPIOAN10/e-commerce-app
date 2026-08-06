import React, { useEffect } from 'react'
import DashboardOverview from './DashboardOverview'
import SalesChart from './SalesChart'
import TopProductsChart from './TopProductsChart'
import OrderStatusChart from './OrderStatusChart'
import { FaBoxOpen, FaDollarSign, FaShoppingCart, FaChartLine, FaChartBar, FaChartPie } from 'react-icons/fa';
import { MdAttachMoney } from 'react-icons/md';
import { useDispatch, useSelector } from 'react-redux';
import { analyticsAction, fetchSalesChartData, fetchTopProductsChartData, fetchOrderStatusChartData } from '../../../store/actions';
import Loader from '../../shared/Loader';
import ErrorPage from '../../shared/ErrorPage';

const Dashboard = () => {

    const dispatch = useDispatch();
    const {isLoading,errorMessage}=useSelector((state) => state.errors);

    const {
    analytics: { productCount,totalRevenue,totalOrders},
    salesChart,
    topProductsChart,
    orderStatusChart,
    } = useSelector((state) => state.admin);

    useEffect(() => {
        dispatch(analyticsAction());
        dispatch(fetchSalesChartData());
        dispatch(fetchTopProductsChartData());
        dispatch(fetchOrderStatusChartData());
    },[dispatch]);

    if(isLoading){
        return <Loader/>
    }
    
    if(errorMessage){
        return<ErrorPage message={errorMessage}/>;
    }
  return (
    <div className='pb-6'>
        <div className='flex md:flex-row mt-8 flex-col lg:justify-between
                    border border-slate-400 rounded-lg bg-linear-to-r
                    from-blue-50 to-blue-100 shadow-lg
                    dark:from-gray-800 dark:to-gray-900 dark:border-gray-700'>
                        <DashboardOverview
                            title="Total Products"
                            amount={productCount}
                            Icon={FaBoxOpen}
                            />
                        <DashboardOverview
                            title="Total Orders"
                            amount={totalOrders}
                            Icon={FaShoppingCart}
                            />
                        <DashboardOverview
                            title="Total Revenue"
                            amount={totalRevenue}
                            Icon={FaDollarSign}
                            revenue
                            />

        </div>

        {/* Sales Line Chart */}
        <div className='mt-8 bg-white dark:bg-gray-800 rounded-lg shadow-lg p-6 border border-slate-200 dark:border-gray-700'>
            <div className='flex items-center gap-2 mb-4'>
                <FaChartLine className='text-blue-600 dark:text-blue-400 text-xl' />
                <h2 className='text-xl font-bold text-slate-800 dark:text-white'>Monthly Sales Revenue</h2>
            </div>
            <SalesChart data={salesChart} />
        </div>

        {/* Two charts side by side */}
        <div className='mt-8 grid lg:grid-cols-2 gap-6'>
            {/* Top Products Bar Chart */}
            <div className='bg-white dark:bg-gray-800 rounded-lg shadow-lg p-6 border border-slate-200 dark:border-gray-700'>
                <div className='flex items-center gap-2 mb-4'>
                    <FaChartBar className='text-green-600 dark:text-green-400 text-xl' />
                    <h2 className='text-xl font-bold text-slate-800 dark:text-white'>Top 10 Best Selling Products</h2>
                </div>
                <TopProductsChart data={topProductsChart} />
            </div>

            {/* Order Status Pie Chart */}
            <div className='bg-white dark:bg-gray-800 rounded-lg shadow-lg p-6 border border-slate-200 dark:border-gray-700'>
                <div className='flex items-center gap-2 mb-4'>
                    <FaChartPie className='text-purple-600 dark:text-purple-400 text-xl' />
                    <h2 className='text-xl font-bold text-slate-800 dark:text-white'>Orders by Status</h2>
                </div>
                <OrderStatusChart data={orderStatusChart} />
            </div>
        </div>
    </div>
  )
}

export default Dashboard
