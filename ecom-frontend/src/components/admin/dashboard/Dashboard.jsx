import React from 'react'
import DashboardOverview from './DashboardOverview'
import SalesChart from './SalesChart'
import TopProductsChart from './TopProductsChart'
import OrderStatusChart from './OrderStatusChart'
import RevenueByCategoryChart from './RevenueByCategoryChart'
import LowStockSummary from './LowStockSummary'
import { FaBoxOpen, FaDollarSign, FaShoppingCart, FaChartLine, FaChartBar, FaChartPie } from 'react-icons/fa';
import { MdAttachMoney } from 'react-icons/md';
import { useSelector } from 'react-redux';
import { useGetAnalyticsQuery, useGetAnalyticsChartQuery } from '../../../store/api/adminApi';
import Loader from '../../shared/Loader';
import ErrorPage from '../../shared/ErrorPage';

const Dashboard = () => {

    const {
    analytics: { productCount,totalRevenue,totalOrders},
    salesChart,
    topProductsChart,
    orderStatusChart,
    revenueByCategoryChart,
    } = useSelector((state) => state.admin);

    // Only the headline figures gate the page. Previously all five requests
    // shared one flag, so a single chart endpoint failing replaced the whole
    // dashboard with an error - including the numbers that had loaded fine.
    const { isLoading, error } = useGetAnalyticsQuery(undefined, {
        refetchOnMountOrArgChange: true,
    });
    const errorMessage = error ? error?.data?.message || 'Failed to load analytics' : null;

    // Each chart is independent: one failing leaves that chart empty and the
    // rest of the dashboard intact.
    useGetAnalyticsChartQuery('sales');
    useGetAnalyticsChartQuery('top-products');
    useGetAnalyticsChartQuery('order-status');
    useGetAnalyticsChartQuery('revenue-by-category');

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

        <div className='mt-8 grid lg:grid-cols-2 gap-6'>
            <div className='bg-white dark:bg-gray-800 rounded-lg shadow-lg p-6 border border-slate-200 dark:border-gray-700'>
                <div className='flex items-center gap-2 mb-4'>
                    <FaChartBar className='text-blue-600 dark:text-blue-400 text-xl' />
                    <h2 className='text-xl font-bold text-slate-800 dark:text-white'>Revenue by Category</h2>
                </div>
                <RevenueByCategoryChart data={revenueByCategoryChart} />
            </div>
            <LowStockSummary />
        </div>
    </div>
  )
}

export default Dashboard
