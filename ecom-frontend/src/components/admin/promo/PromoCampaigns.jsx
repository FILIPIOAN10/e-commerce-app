import { useState } from 'react';
import { useDispatch, useSelector } from 'react-redux';
import { DataGrid } from '@mui/x-data-grid';
import toast from 'react-hot-toast';
import { FaBullhorn, FaTrash } from 'react-icons/fa';
import { createPromoCampaign, updatePromoCampaign, deletePromoCampaign } from '../../../store/actions';
import Loader from '../../shared/Loader';
import ErrorPage from '../../shared/ErrorPage';
import { useGetPromoCampaignsQuery } from '../../../store/api/adminApi';
import { useGetProductsQuery } from '../../../store/api/productApi';

const emptyCampaign = {
    name: '',
    discountPercent: 0,
    startTime: '',
    endTime: '',
    active: true,
    productIds: [],
};

const PromoCampaigns = () => {
    const dispatch = useDispatch();
    const { promoCampaigns, promoCampaignTotal } = useSelector((state) => state.admin);
    const { products } = useSelector((state) => state.products);
    const [form, setForm] = useState(emptyCampaign);
    const [editing, setEditing] = useState(null);
    const [page, setPage] = useState(0);

    // As with coupons, the mutations are still thunks writing to the reducer,
    // so the list must refetch on mount rather than serving a stale cache.
    const { isLoading, error } = useGetPromoCampaignsQuery(
        { pageNumber: page, pageSize: 10 },
        { refetchOnMountOrArgChange: true },
    );
    // The product picker. The server clamps pageSize to 100 (PageSizeLimitFilter),
    // so ask for exactly that rather than a number the filter will silently cut.
    useGetProductsQuery('pageNumber=0&pageSize=100');

    const errorMessage = error ? error?.data?.message || 'Failed to load campaigns' : null;

    const handleChange = (e) => {
        const { name, value, type, checked } = e.target;
        setForm((prev) => ({
            ...prev,
            [name]: type === 'checkbox' ? checked : value,
        }));
    };

    const handleProductSelect = (e) => {
        const selected = Array.from(e.target.selectedOptions, (o) => Number(o.value));
        setForm((prev) => ({ ...prev, productIds: selected }));
    };

    const handleSubmit = (e) => {
        e.preventDefault();
        if (editing) {
            dispatch(updatePromoCampaign(editing, form, toast));
            setEditing(null);
        } else {
            dispatch(createPromoCampaign(form, toast));
        }
        setForm(emptyCampaign);
    };

    const handleEdit = (row) => {
        setEditing(row.id);
        setForm({
            name: row.name,
            discountPercent: row.discountPercent,
            startTime: row.startTime,
            endTime: row.endTime,
            active: row.active,
            productIds: row.productIds || [],
        });
    };

    const handleDelete = (id) => {
        if (window.confirm('Delete this campaign?')) {
            dispatch(deletePromoCampaign(id, toast));
        }
    };

    const columns = [
        { field: 'id', headerName: 'ID', width: 80 },
        { field: 'name', headerName: 'Name', width: 200 },
        { field: 'discountPercent', headerName: 'Discount %', width: 120 },
        { field: 'startTime', headerName: 'Start', width: 160 },
        { field: 'endTime', headerName: 'End', width: 160 },
        { field: 'active', headerName: 'Active', width: 100 },
        {
            field: 'actions',
            headerName: 'Actions',
            width: 150,
            sortable: false,
            renderCell: (params) => (
                <div className="flex items-center gap-2">
                    <button
                        onClick={() => handleEdit(params.row)}
                        className="text-blue-500 hover:underline text-sm"
                    >
                        Edit
                    </button>
                    <button onClick={() => handleDelete(params.row.id)} className="text-red-500 hover:underline">
                        <FaTrash />
                    </button>
                </div>
            ),
        },
    ];

    const rows = (promoCampaigns ?? []).map((c) => ({ ...c, id: c.id }));

    if (errorMessage) return <ErrorPage message={errorMessage} />;

    return (
        <div>
            <div className="pt-6 pb-6 flex items-center gap-3">
                <FaBullhorn className="text-purple-500 text-2xl" />
                <h1 className="text-slate-800 dark:text-white text-3xl font-bold uppercase">Promo Campaigns</h1>
            </div>

            <div className="bg-white dark:bg-gray-800 rounded-lg shadow-lg p-6 border border-slate-200 dark:border-gray-700 mb-6">
                <h2 className="text-lg font-semibold mb-4 dark:text-white">{editing ? 'Edit' : 'New'} Campaign</h2>
                <form onSubmit={handleSubmit} className="grid md:grid-cols-2 gap-4">
                    <input
                        name="name"
                        value={form.name}
                        onChange={handleChange}
                        placeholder="Campaign name"
                        className="border dark:border-gray-600 dark:bg-gray-700 dark:text-gray-200 rounded-lg p-2 text-sm"
                        required
                    />
                    <input
                        name="discountPercent"
                        type="number"
                        value={form.discountPercent}
                        onChange={handleChange}
                        placeholder="Discount %"
                        className="border dark:border-gray-600 dark:bg-gray-700 dark:text-gray-200 rounded-lg p-2 text-sm"
                        required
                    />
                    <input
                        name="startTime"
                        type="datetime-local"
                        value={form.startTime}
                        onChange={handleChange}
                        className="border dark:border-gray-600 dark:bg-gray-700 dark:text-gray-200 rounded-lg p-2 text-sm"
                        required
                    />
                    <input
                        name="endTime"
                        type="datetime-local"
                        value={form.endTime}
                        onChange={handleChange}
                        className="border dark:border-gray-600 dark:bg-gray-700 dark:text-gray-200 rounded-lg p-2 text-sm"
                        required
                    />
                    <select
                        multiple
                        value={form.productIds}
                        onChange={handleProductSelect}
                        className="border dark:border-gray-600 dark:bg-gray-700 dark:text-gray-200 rounded-lg p-2 text-sm h-32 md:col-span-2"
                    >
                        {(products ?? []).map((p) => (
                            <option key={p.productId} value={p.productId}>
                                {p.productName}
                            </option>
                        ))}
                    </select>
                    <label className="flex items-center gap-2 md:col-span-2 dark:text-gray-200">
                        <input
                            name="active"
                            type="checkbox"
                            checked={form.active}
                            onChange={handleChange}
                        />
                        Active
                    </label>
                    <div className="md:col-span-2 flex gap-2">
                        <button type="submit" className="bg-purple-500 hover:bg-purple-600 text-white px-4 py-2 rounded-lg text-sm font-medium">
                            {editing ? 'Update' : 'Create'}
                        </button>
                        {editing && (
                            <button
                                type="button"
                                onClick={() => { setEditing(null); setForm(emptyCampaign); }}
                                className="border dark:border-gray-600 dark:text-gray-200 px-4 py-2 rounded-lg text-sm font-medium"
                            >
                                Cancel
                            </button>
                        )}
                    </div>
                </form>
            </div>

            {isLoading ? <Loader /> : (
                <div className="max-w-fit mx-auto">
                    <DataGrid
                        className="w-full"
                        rows={rows}
                        columns={columns}
                        disableRowSelectionOnClick
                        disableColumnResize
                        pageSizeOptions={[10]}
                        rowCount={promoCampaignTotal || 0}
                        paginationMode="server"
                        paginationModel={{ page, pageSize: 10 }}
                        onPaginationModelChange={(model) => setPage(model.page)}
                    />
                </div>
            )}
        </div>
    );
};

export default PromoCampaigns;
