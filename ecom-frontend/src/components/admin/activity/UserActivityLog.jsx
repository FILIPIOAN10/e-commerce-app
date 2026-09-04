import { useState } from 'react';
import { useSelector } from 'react-redux';
import { DataGrid } from '@mui/x-data-grid';
import { FaClipboardList } from 'react-icons/fa';

import Loader from '../../shared/Loader';
import ErrorPage from '../../shared/ErrorPage';
import { useGetActivityLogsQuery } from '../../../store/api/adminApi';

const UserActivityLog = () => {
    const { activityLogs, activityLogTotal } = useSelector((state) => state.admin);
    const [page, setPage] = useState(0);

    // Own status: this screen no longer displays another admin page's failure.
    const { isLoading, error } = useGetActivityLogsQuery({ pageNumber: page, pageSize: 20 });
    const errorMessage = error ? error?.data?.message || "Failed to load activity logs" : null;


    const columns = [
        { field: 'id', headerName: 'ID', width: 80 },
        { field: 'username', headerName: 'Username', width: 200 },
        { field: 'action', headerName: 'Action', width: 180 },
        { field: 'details', headerName: 'Details', flex: 1 },
        { field: 'createdAt', headerName: 'Date', width: 160 },
    ];

    const rows = activityLogs.map((log) => ({
        id: log.id,
        username: log.username,
        action: log.action,
        details: log.details || '-',
        createdAt: log.createdAt,
    }));

    if (errorMessage) return <ErrorPage message={errorMessage} />;

    return (
        <div>
            <div className="pt-6 pb-6 flex items-center gap-3">
                <FaClipboardList className="text-blue-500 text-2xl" />
                <h1 className="text-slate-800 dark:text-white text-3xl font-bold uppercase">User Activity Log</h1>
            </div>
            {isLoading ? <Loader /> : (
                <div className="max-w-fit mx-auto">
                    <DataGrid
                        className="w-full"
                        rows={rows}
                        columns={columns}
                        disableRowSelectionOnClick
                        disableColumnResize
                        pageSizeOptions={[20]}
                        pagination
                        rowCount={activityLogTotal || 0}
                        paginationMode="server"
                        page={page}
                        onPageChange={(newPage) => setPage(newPage)}
                    />
                </div>
            )}
        </div>
    );
};

export default UserActivityLog;
