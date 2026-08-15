import { useState } from 'react';
import { useDispatch } from 'react-redux';
import toast from 'react-hot-toast';
import { FaFileImport } from 'react-icons/fa';
import { importProducts } from '../../../store/actions';

const ProductImport = () => {
    const dispatch = useDispatch();
    const [file, setFile] = useState(null);

    const handleFileChange = (e) => {
        setFile(e.target.files[0]);
    };

    const handleSubmit = (e) => {
        e.preventDefault();
        if (!file) {
            toast.error('Please select a CSV file');
            return;
        }
        dispatch(importProducts(file, toast));
        setFile(null);
    };

    return (
        <div>
            <div className="pt-6 pb-6 flex items-center gap-3">
                <FaFileImport className="text-teal-500 text-2xl" />
                <h1 className="text-slate-800 dark:text-white text-3xl font-bold uppercase">Bulk Product Import</h1>
            </div>

            <div className="bg-white dark:bg-gray-800 rounded-lg shadow-lg p-6 border border-slate-200 dark:border-gray-700">
                <p className="text-sm text-gray-600 dark:text-gray-400 mb-4">
                    CSV format: <code>productName,description,price,discount,quantity,image,tags,categoryName</code>
                </p>
                <form onSubmit={handleSubmit} className="space-y-4">
                    <input
                        type="file"
                        accept=".csv"
                        onChange={handleFileChange}
                        className="block w-full text-sm text-gray-700 dark:text-gray-300
                                   file:mr-4 file:py-2 file:px-4
                                   file:rounded-full file:border-0
                                   file:text-sm file:font-semibold
                                   file:bg-teal-50 file:text-teal-700
                                   hover:file:bg-teal-100"
                    />
                    <button
                        type="submit"
                        className="bg-teal-500 hover:bg-teal-600 text-white px-4 py-2 rounded-lg text-sm font-medium"
                    >
                        Import CSV
                    </button>
                </form>
            </div>
        </div>
    );
};

export default ProductImport;
