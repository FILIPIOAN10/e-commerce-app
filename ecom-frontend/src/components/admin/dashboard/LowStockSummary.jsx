import { useEffect } from 'react';
import { useDispatch, useSelector } from 'react-redux';
import { FaExclamationTriangle } from 'react-icons/fa';
import { fetchLowStockSummary } from '../../../store/actions';

const LowStockSummary = () => {
    const dispatch = useDispatch();
    const { lowStockSummary } = useSelector((state) => state.admin);

    useEffect(() => {
        dispatch(fetchLowStockSummary());
    }, [dispatch]);

    return (
        <div className="bg-white dark:bg-gray-800 rounded-lg shadow-lg p-6 border border-slate-200 dark:border-gray-700">
            <div className="flex items-center gap-2 mb-4">
                <FaExclamationTriangle className="text-orange-500 text-xl" />
                <h2 className="text-xl font-bold text-slate-800 dark:text-white">Low Stock Summary</h2>
            </div>
            {lowStockSummary ? (
                <>
                    <p className="text-2xl font-bold text-orange-600 dark:text-orange-400 mb-3">
                        {lowStockSummary.count} products low in stock
                    </p>
                    <ul className="space-y-2">
                        {lowStockSummary.products.map((p) => (
                            <li key={p.productId} className="text-sm text-gray-700 dark:text-gray-300 flex justify-between">
                                <span>{p.productName}</span>
                                <span className="font-semibold text-orange-600">{p.quantity} left</span>
                            </li>
                        ))}
                    </ul>
                </>
            ) : (
                <p className="text-gray-500 dark:text-gray-400">Loading...</p>
            )}
        </div>
    );
};

export default LowStockSummary;
