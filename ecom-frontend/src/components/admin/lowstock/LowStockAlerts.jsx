import React, { useState } from "react";
import { useSelector } from "react-redux";
import { DataGrid } from "@mui/x-data-grid";
import { FaExclamationTriangle, FaBoxOpen } from "react-icons/fa";
import Loader from "../../shared/Loader";
import ErrorPage from "../../shared/ErrorPage";
import { useGetLowStockProductsQuery } from "../../../store/api/adminApi";


const LowStockAlerts = () => {
    const { lowStockProducts, pagination } = useSelector((state) => state.products);
    const { user } = useSelector((state) => state.auth);
    const [page, setPage] = useState(0);

    // Admins see every product, sellers only their own - the endpoint differs,
    // so the role has to reach the query rather than being read inside a thunk.
    const isAdmin = Boolean(user?.roles?.includes("ROLE_ADMIN"));
    const { isLoading, error } = useGetLowStockProductsQuery({ isAdmin, pageNumber: page, pageSize: 10 });
    const errorMessage = error ? error?.data?.message || "Failed to load low-stock products" : null;


    const tableRecords = lowStockProducts?.map((item) => ({
        id: item.productId,
        productName: item.productName,
        quantity: item.quantity,
        lowStockThreshold: item.lowStockThreshold || 10,
        price: item.price,
        specialPrice: item.specialPrice,
        image: item.image,
    }));

    const columns = [
        {
            field: "image",
            headerName: "Image",
            width: 80,
            renderCell: (params) =>
                params.value ? (
                    <img
                        src={params.value}
                        alt="product"
                        className="w-10 h-10 object-cover rounded"
                    />
                ) : (
                    <FaBoxOpen className="text-gray-400 text-2xl" />
                ),
            sortable: false,
        },
        { field: "productName", headerName: "Product Name", width: 250 },
        {
            field: "quantity",
            headerName: "Stock",
            width: 100,
            renderCell: (params) => (
                <span
                    className={`px-2 py-1 rounded-full text-xs font-semibold ${
                        params.value === 0
                            ? "bg-red-100 text-red-700"
                            : "bg-orange-100 text-orange-700"
                    }`}
                >
                    {params.value}
                </span>
            ),
        },
        {
            field: "lowStockThreshold",
            headerName: "Threshold",
            width: 100,
            renderCell: (params) => (
                <span className="text-slate-500 dark:text-gray-400">{params.value}</span>
            ),
        },
        {
            field: "price",
            headerName: "Price",
            width: 100,
            renderCell: (params) => `$${params.value}`,
        },
        {
            field: "specialPrice",
            headerName: "Special Price",
            width: 120,
            renderCell: (params) => `$${params.value}`,
        },
    ];

    const isEmpty = !lowStockProducts || lowStockProducts.length === 0;

    if (errorMessage) return <ErrorPage message={errorMessage} />;

    return (
        <div>
            <div className="pt-6 pb-6 flex items-center gap-3">
                <FaExclamationTriangle className="text-orange-500 text-2xl" />
                <h1 className="text-slate-800 dark:text-white text-3xl font-bold uppercase">
                    Low Stock Alerts
                </h1>
            </div>

            {isLoading ? (
                <Loader />
            ) : isEmpty ? (
                <div className="flex flex-col items-center justify-center text-gray-600 dark:text-gray-400 py-10">
                    <FaBoxOpen size={50} className="mb-3 text-green-500" />
                    <h2 className="text-2xl font-semibold">
                        All Products Are Well Stocked
                    </h2>
                    <p className="text-sm text-gray-400 dark:text-gray-500 mt-2">
                        No products below their low stock threshold.
                    </p>
                </div>
            ) : (
                <div className="max-w-fit mx-auto">
                    <DataGrid
                        className="w-full"
                        rows={tableRecords}
                        columns={columns}
                        disableRowSelectionOnClick
                        disableColumnResize
                        pageSizeOptions={[10]}
                        pagination
                        rowCount={pagination?.totalElements || 0}
                        paginationMode="server"
                        page={page}
                        onPageChange={(newPage) => setPage(newPage)}
                    />
                </div>
            )}
        </div>
    );
};

export default LowStockAlerts;
