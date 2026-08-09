import React, { useState, useEffect, useCallback } from "react";
import { FaTrashAlt, FaUsers, FaSearch, FaShieldAlt, FaCheckCircle, FaTimesCircle } from "react-icons/fa";
import { DataGrid } from "@mui/x-data-grid";
import api from "../../../api/api";
import toast from "react-hot-toast";
import Loader from "../../shared/Loader";

const AdminUsers = () => {
    const [users, setUsers] = useState([]);
    const [pagination, setPagination] = useState({});
    const [loading, setLoading] = useState(true);
    const [search, setSearch] = useState("");
    const [currentPage, setCurrentPage] = useState(0);
    const [pageSize, setPageSize] = useState(20);
    const [confirmDelete, setConfirmDelete] = useState(null);

    const fetchUsers = useCallback(async (page, size) => {
        setLoading(true);
        try {
            const resp = await api.get(`/auth/users`, {
                params: { pageNumber: page, pageSize: size },
            });
            setUsers(resp.data.content || []);
            setPagination({
                pageNumber: resp.data.pageNumber,
                pageSize: resp.data.pageSize,
                totalElements: resp.data.totalElements,
                totalPages: resp.data.totalPages,
                lastPage: resp.data.lastPage,
            });
        } catch (err) {
            const msg = err.response?.data?.message || "Failed to fetch users";
            toast.error(msg);
        } finally {
            setLoading(false);
        }
    }, []);

    useEffect(() => {
        fetchUsers(currentPage, pageSize);
    }, [currentPage, pageSize, fetchUsers]);

    const handleDelete = async (userId, username) => {
        try {
            await api.delete(`/auth/users/${userId}`);
            toast.success(`User "${username}" deleted successfully`);
            setConfirmDelete(null);
            fetchUsers(currentPage, pageSize);
        } catch (err) {
            const msg = err.response?.data?.message || "Failed to delete user";
            toast.error(msg);
        }
    };

    const filteredUsers = search
        ? users.filter(
              (u) =>
                  u.username?.toLowerCase().includes(search.toLowerCase()) ||
                  u.email?.toLowerCase().includes(search.toLowerCase())
          )
        : users;

    const tableRecords = filteredUsers?.map((item) => {
        const roles = item.roles?.map((r) => r.roleName?.replace("ROLE_", "")).join(", ") || "USER";
        return {
            id: item.userId,
            userId: item.userId,
            username: item.username,
            email: item.email,
            roles: roles,
            verified: item.verified,
            twoFactorEnabled: item.twoFactorEnabled,
            phone: item.phone || "-",
        };
    });

    const columns = [
        { field: "userId", headerName: "ID", width: 70 },
        { field: "username", headerName: "Username", width: 150 },
        { field: "email", headerName: "Email", width: 220 },
        { field: "roles", headerName: "Roles", width: 150 },
        {
            field: "verified",
            headerName: "Verified",
            width: 110,
            renderCell: (params) => (
                <div className="flex items-center justify-center h-full">
                    {params.value ? (
                        <span className="flex items-center gap-1 text-green-600 dark:text-green-400 text-sm">
                            <FaCheckCircle /> Yes
                        </span>
                    ) : (
                        <span className="flex items-center gap-1 text-red-500 text-sm">
                            <FaTimesCircle /> No
                        </span>
                    )}
                </div>
            ),
        },
        {
            field: "twoFactorEnabled",
            headerName: "2FA",
            width: 90,
            renderCell: (params) => (
                <div className="flex items-center justify-center h-full">
                    {params.value ? (
                        <span className="flex items-center gap-1 text-blue-600 dark:text-blue-400 text-sm">
                            <FaShieldAlt /> On
                        </span>
                    ) : (
                        <span className="text-gray-400 text-sm">Off</span>
                    )}
                </div>
            ),
        },
        { field: "phone", headerName: "Phone", width: 120 },
        {
            field: "actions",
            headerName: "Actions",
            width: 100,
            sortable: false,
            renderCell: (params) => (
                <div className="flex items-center justify-center h-full">
                    <button
                        onClick={() => setConfirmDelete({ userId: params.row.userId, username: params.row.username })}
                        className="text-red-500 hover:text-red-700 hover:bg-red-50 dark:hover:bg-red-900/20 p-2 rounded-lg transition-colors"
                        title="Delete user"
                    >
                        <FaTrashAlt />
                    </button>
                </div>
            ),
        },
    ];

    const handlePaginationChange = (paginationModel) => {
        setCurrentPage(paginationModel.page);
        setPageSize(paginationModel.pageSize);
    };

    return (
        <div className="pb-6 pt-6">
            <div className="flex items-center justify-between mb-6">
                <h1 className="text-3xl font-bold text-slate-800 dark:text-white flex items-center gap-3">
                    <FaUsers className="text-blue-600 dark:text-blue-400" />
                    User Management
                </h1>
                <div className="relative">
                    <FaSearch className="absolute left-3 top-1/2 -translate-y-1/2 text-gray-400 text-sm" />
                    <input
                        type="text"
                        placeholder="Search users..."
                        value={search}
                        onChange={(e) => setSearch(e.target.value)}
                        className="pl-9 pr-4 py-2 rounded-lg border border-slate-300 dark:border-gray-600 bg-white dark:bg-gray-800 text-slate-800 dark:text-white text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
                    />
                </div>
            </div>

            {loading ? (
                <Loader />
            ) : tableRecords?.length === 0 ? (
                <div className="flex flex-col items-center justify-center text-gray-600 dark:text-gray-400 py-10">
                    <FaUsers size={50} className="mb-3 opacity-50" />
                    <h2 className="text-2xl font-semibold">No Users Found</h2>
                </div>
            ) : (
                <div className="max-w-fit mx-auto bg-white dark:bg-gray-800 rounded-lg shadow-lg">
                    <DataGrid
                        className="w-full"
                        rows={tableRecords}
                        getRowClassName={(params) =>
                            params.indexRelativeToCurrentPage % 2 === 0
                                ? "bg-white dark:bg-gray-800"
                                : "bg-slate-50 dark:bg-gray-900"
                        }
                        columns={columns}
                        paginationMode="server"
                        rowCount={pagination.totalElements || 0}
                        initialState={{
                            pagination: {
                                paginationModel: {
                                    pageSize: pageSize,
                                    page: currentPage,
                                },
                            },
                        }}
                        onPaginationModelChange={handlePaginationChange}
                        disableRowSelectionOnClick
                        disableColumnResize
                        pagination
                        pageSizeOptions={[10, 20, 50]}
                        paginationOptions={{
                            showFirstButton: true,
                            showLastButton: true,
                        }}
                        sx={{
                            "& .MuiDataGrid-columnHeaders": {
                                backgroundColor: "rgb(241 245 249)",
                                color: "rgb(30 41 59)",
                                fontWeight: "bold",
                            },
                            "& .MuiDataGrid-cell": {
                                borderColor: "rgb(226 232 240)",
                            },
                        }}
                    />
                </div>
            )}

            {confirmDelete && (
                <div
                    className="fixed inset-0 z-50 flex items-center justify-center bg-black/50"
                    onClick={() => setConfirmDelete(null)}
                >
                    <div
                        className="bg-white dark:bg-gray-800 rounded-lg shadow-xl p-6 max-w-md w-full mx-4"
                        onClick={(e) => e.stopPropagation()}
                    >
                        <div className="flex items-center gap-3 mb-4">
                            <div className="bg-red-100 dark:bg-red-900/30 p-3 rounded-full">
                                <FaTrashAlt className="text-red-600 dark:text-red-400 text-xl" />
                            </div>
                            <h3 className="text-xl font-bold text-slate-800 dark:text-white">Delete User</h3>
                        </div>
                        <p className="text-slate-600 dark:text-gray-300 mb-6">
                            Are you sure you want to delete user{" "}
                            <span className="font-semibold text-slate-800 dark:text-white">
                                "{confirmDelete.username}"
                            </span>
                            ? This action cannot be undone and will remove all associated data
                            (orders, cart, reviews, addresses, etc.).
                        </p>
                        <div className="flex justify-end gap-3">
                            <button
                                onClick={() => setConfirmDelete(null)}
                                className="px-4 py-2 rounded-lg border border-slate-300 dark:border-gray-600 text-slate-700 dark:text-gray-200 hover:bg-slate-100 dark:hover:bg-gray-700 transition-colors"
                            >
                                Cancel
                            </button>
                            <button
                                onClick={() => handleDelete(confirmDelete.userId, confirmDelete.username)}
                                className="px-4 py-2 rounded-lg bg-red-600 text-white hover:bg-red-700 transition-colors font-medium"
                            >
                                Delete User
                            </button>
                        </div>
                    </div>
                </div>
            )}
        </div>
    );
};

export default AdminUsers;
