import React, { useState, useEffect } from "react";
import { useDispatch, useSelector } from "react-redux";
import { DataGrid } from "@mui/x-data-grid";
import { FaFolderOpen, FaTag, FaEdit, FaTrash } from "react-icons/fa";
import { MdEdit, MdDelete } from "react-icons/md";
import toast from "react-hot-toast";

import Modal from "../../shared/Modal";
import AddCouponForm from "./AddCouponForm";
import Loader from "../../shared/Loader";
import { DeleteModal } from "../../shared/DeleteModal";
import ErrorPage from "../../shared/ErrorPage";
import { fetchAllCoupons, deleteCouponAction } from "../../../store/actions";

const Coupons = () => {
    const dispatch = useDispatch();
    const [openModal, setOpenModal] = useState(false);
    const [openUpdateModal, setOpenUpdateModal] = useState(false);
    const [openDeleteModal, setOpenDeleteModal] = useState(false);
    const [selectedCoupon, setSelectedCoupon] = useState(null);

    const { isLoading, errorMessage } = useSelector((state) => state.errors);
    const { coupons } = useSelector((state) => state.coupon);

    useEffect(() => {
        dispatch(fetchAllCoupons());
    }, [dispatch]);

    const tableRecords = coupons?.map((item) => ({
        id: item.couponId,
        code: item.code,
        discountPercent: item.discountPercent,
        expiryDate: item.expiryDate,
        maxUses: item.maxUses,
        usedCount: item.usedCount,
        active: item.active,
    }));

    const handleEdit = (coupon) => {
        setSelectedCoupon(coupon);
        setOpenUpdateModal(true);
    };

    const handleDelete = (coupon) => {
        setSelectedCoupon(coupon);
        setOpenDeleteModal(true);
    };

    const onDeleteHandler = () => {
        dispatch(deleteCouponAction(selectedCoupon?.id, toast, setOpenDeleteModal));
    };

    const columns = [
        {
            field: "code",
            headerName: "Code",
            width: 160,
            renderCell: (params) => (
                <span className="font-mono font-semibold text-slate-800 dark:text-gray-200 bg-slate-100 dark:bg-gray-700 px-2 py-1 rounded">
                    {params.value}
                </span>
            ),
        },
        {
            field: "discountPercent",
            headerName: "Discount",
            width: 120,
            renderCell: (params) => (
                <span className="text-green-600 font-semibold">
                    {params.value}%
                </span>
            ),
        },
        { field: "expiryDate", headerName: "Expiry Date", width: 150 },
        {
            field: "maxUses",
            headerName: "Max Uses",
            width: 120,
        },
        {
            field: "usedCount",
            headerName: "Used",
            width: 100,
            renderCell: (params) => (
                <span className="text-slate-600 dark:text-gray-400">
                    {params.value} / {params.row.maxUses}
                </span>
            ),
        },
        {
            field: "active",
            headerName: "Status",
            width: 130,
            renderCell: (params) => (
                <span
                    className={`px-2 py-1 rounded-full text-xs font-semibold ${
                        params.value
                            ? "bg-green-100 text-green-700"
                            : "bg-red-100 text-red-600"
                    }`}
                >
                    {params.value ? "Active" : "Inactive"}
                </span>
            ),
        },
        {
            field: "actions",
            headerName: "Actions",
            width: 160,
            sortable: false,
            renderCell: (params) => (
                <div className="flex gap-3 items-center h-full">
                    <button
                        onClick={() => handleEdit(params.row)}
                        className="flex items-center gap-1 text-blue-500 hover:text-blue-700 hover:bg-blue-50 px-2 py-1 rounded-md text-sm font-medium transition-colors"
                    >
                        <MdEdit className="text-base" />
                        Edit
                    </button>
                    <button
                        onClick={() => handleDelete(params.row)}
                        className="flex items-center gap-1 text-red-500 hover:text-red-700 hover:bg-red-50 px-2 py-1 rounded-md text-sm font-medium transition-colors"
                    >
                        <MdDelete className="text-base" />
                        Delete
                    </button>
                </div>
            ),
        },
    ];

    const emptyCoupons = !coupons || coupons?.length === 0;

    if (errorMessage) return <ErrorPage message={errorMessage} />;

    return (
        <div>
            <div className="pt-6 pb-10 flex justify-end">
                <button
                    onClick={() => setOpenModal(true)}
                    className="bg-custom-blue hover:bg-blue-800 text-white font-semibold py-2 px-4 flex items-center gap-2 rounded-md shadow-md transition-colors hover:text-slate-300 duration-300"
                >
                    <FaTag className="text-xl" />
                    Add Coupon
                </button>
            </div>
            {!emptyCoupons && (
                <h1 className="text-slate-800 dark:text-white text-3xl text-center font-bold pb-6 uppercase">
                    All Coupons
                </h1>
            )}

            {isLoading ? (
                <Loader />
            ) : (
                <>
                    {emptyCoupons ? (
                        <div className="flex flex-col items-center justify-center text-gray-600 dark:text-gray-400 py-10">
                            <FaFolderOpen size={50} className="mb-3" />
                            <h2 className="text-2xl font-semibold">
                                No Coupons Created Yet
                            </h2>
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
                            />
                        </div>
                    )}
                </>
            )}

            <Modal
                open={openUpdateModal || openModal}
                setOpen={openUpdateModal ? setOpenUpdateModal : setOpenModal}
                title={openUpdateModal ? "Update Coupon" : "Add Coupon"}
            >
                <AddCouponForm
                    setOpen={openUpdateModal ? setOpenUpdateModal : setOpenModal}
                    open={isLoading}
                    coupon={selectedCoupon}
                    update={openUpdateModal}
                />
            </Modal>

            <DeleteModal
                open={openDeleteModal}
                loader={isLoading}
                setOpen={setOpenDeleteModal}
                title="Are you sure you want to delete this coupon?"
                onDeleteHandler={onDeleteHandler}
            />
        </div>
    );
};

export default Coupons;
