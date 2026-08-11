import { DataGrid } from '@mui/x-data-grid'
import { adminOrderTableColumn } from '../../helper/tableColumn';
import { useState } from 'react';
import { useLocation, useNavigate, useSearchParams } from 'react-router-dom';
import UpdateOrderForm from './UpdateOrderForm';
import Modal from '../../shared/Modal';
import OrderTrackingModal from '../../shared/OrderTrackingModal';
import { useSelector } from 'react-redux';
import { FaFileCsv, FaFilePdf } from 'react-icons/fa';
import api from '../../../api/api';
import toast from 'react-hot-toast';

const OrderTable = ({adminOrder, pagination}) => {

  const [updateOpenModal, setUpdateOpenModal] = useState(false);
  const [trackOpenModal, setTrackOpenModal] = useState(false);
  const [returnOpenModal, setReturnOpenModal] = useState(false);
  const [returnOrderId, setReturnOrderId] = useState(null);
  const [returnReason, setReturnReason] = useState('');
  const [selectedItem, setSelectedItem] = useState("");
  const [trackOrderId, setTrackOrderId] = useState(null);
  const [loader, setLoader] = useState(false);
  const [exporting, setExporting] = useState(false);
  const [submittingReturn, setSubmittingReturn] = useState(false);
  const navigate = useNavigate();
  const [currentPage, setCurrentPage] = useState(
    pagination?.pageNumber + 1 || 1
  );

  const [searchParams] = useSearchParams();
  const params = new URLSearchParams(searchParams);
  const pathname = useLocation().pathname;

  const { user } = useSelector((state) => state.auth);
  const isAdmin = Boolean(user?.roles?.includes("ROLE_ADMIN"));
  const isSeller = Boolean(user?.roles?.includes("ROLE_SELLER"));
  const canEdit = isAdmin || isSeller;

  const handleExport = async (format) => {
    setExporting(true);
    try {
      const response = await api.get(`/admin/orders/export/${format}`, {
        responseType: 'blob',
      });
      const url = window.URL.createObjectURL(new Blob([response.data]));
      const link = document.createElement('a');
      link.href = url;
      link.setAttribute('download', `orders.${format}`);
      document.body.appendChild(link);
      link.click();
      link.remove();
      window.URL.revokeObjectURL(url);
      toast.success(`Orders exported as ${format.toUpperCase()}`);
    } catch (error) {
      toast.error(`Failed to export ${format.toUpperCase()}`);
    } finally {
      setExporting(false);
    }
  };

  const tableRecords = adminOrder?.map((item) => ({
    id: item.orderId,
    email: item.email,
    totalAmount: item.totalAmount ?? 0,
    status: item.orderStatus,
    date: item.orderDate,
  }));

  const handlePaginationChange = (paginationModel) => {
    const page = paginationModel.page + 1;
    setCurrentPage(page);
    params.set("page", page.toString());
    navigate(`${pathname}?${params}`);
  }

  const handleEdit = (order) => {
    setSelectedItem(order);
    setUpdateOpenModal(true);
  }

  const handleTrack = (order) => {
    setTrackOrderId(order.id);
    setTrackOpenModal(true);
  }

  const handleInvoice = async (order) => {
    try {
      const response = await api.get(`/orders/invoice/${order.id}`, {
        responseType: 'blob',
      });
      const url = window.URL.createObjectURL(new Blob([response.data]));
      const link = document.createElement('a');
      link.href = url;
      link.setAttribute('download', `invoice-${order.id}.pdf`);
      document.body.appendChild(link);
      link.click();
      link.remove();
      window.URL.revokeObjectURL(url);
      toast.success('Invoice downloaded');
    } catch {
      toast.error('Failed to download invoice');
    }
  };

  const handleReturn = (order) => {
    setReturnOrderId(order.id);
    setReturnReason('');
    setReturnOpenModal(true);
  };

  const submitReturn = async () => {
    if (!returnReason.trim()) {
      toast.error('Please provide a reason for the return');
      return;
    }
    setSubmittingReturn(true);
    try {
      await api.post(`/orders/${returnOrderId}/return`, { reason: returnReason });
      toast.success('Return request submitted');
      setReturnOpenModal(false);
      if (pathname.includes('my-orders') || pathname.includes('profile')) {
        window.location.reload();
      }
    } catch (error) {
      const msg = error?.response?.data?.message || 'Failed to submit return request';
      toast.error(msg);
    } finally {
      setSubmittingReturn(false);
    }
  };

  return (
    <div>
      <div className='flex justify-between items-center pb-6'>
        <h1 className='text-slate-800 text-3xl font-bold dark:text-white'>
          All Orders
        </h1>
        {isAdmin && (
          <div className='flex gap-2'>
            <button
              onClick={() => handleExport('csv')}
              disabled={exporting}
              className='flex items-center gap-2 bg-green-600 hover:bg-green-700 text-white px-4 py-2 rounded-md transition disabled:opacity-50'
            >
              <FaFileCsv size={18} />
              Export CSV
            </button>
            <button
              onClick={() => handleExport('pdf')}
              disabled={exporting}
              className='flex items-center gap-2 bg-red-600 hover:bg-red-700 text-white px-4 py-2 rounded-md transition disabled:opacity-50'
            >
              <FaFilePdf size={18} />
              Export PDF
            </button>
          </div>
        )}
      </div>
      <div>
        <DataGrid
          className='w-full'
          rows={tableRecords}
          // ✅ coloana Edit apare pentru admin și seller
          columns={adminOrderTableColumn(handleEdit, canEdit, handleTrack, handleInvoice, !canEdit ? handleReturn : null)}
          paginationMode='server'
          rowCount={pagination?.totalElements || 0}
          initialState={{
            pagination: {
              paginationModel: {
                pageSize: pagination?.pageSize || 10,
                page: currentPage - 1,
              },
            },
          }}
          onPaginationModelChange={handlePaginationChange}
          disableRowSelectionOnClick
          pageSizeOptions={[pagination?.pageSize || 10]}
          pagination
        />
      </div>

      {/* ✅ modalul apare pentru admin și seller */}
      {canEdit && (
        <Modal
          open={updateOpenModal}
          setOpen={setUpdateOpenModal}
          title='Update Order Status'>
          <UpdateOrderForm
            setOpen={setUpdateOpenModal}
            open={updateOpenModal}
            loader={loader}
            setLoader={setLoader}
            selectedId={selectedItem.id}
            selectedItem={selectedItem}
          />
        </Modal>
      )}

      <OrderTrackingModal
        open={trackOpenModal}
        setOpen={setTrackOpenModal}
        orderId={trackOrderId}
      />

      <Modal
        open={returnOpenModal}
        setOpen={setReturnOpenModal}
        title='Request Return'
      >
        <div className='p-4'>
          <p className='text-sm text-gray-600 mb-3'>
            Order #{returnOrderId} — Please provide a reason for your return request.
          </p>
          <textarea
            value={returnReason}
            onChange={(e) => setReturnReason(e.target.value)}
            placeholder='Describe the reason for return...'
            className='w-full border border-gray-300 rounded-md p-3 text-sm min-h-24 focus:outline-none focus:ring-2 focus:ring-orange-500'
            rows={4}
          />
          <div className='flex justify-end gap-2 mt-4'>
            <button
              onClick={() => setReturnOpenModal(false)}
              className='px-4 py-2 text-gray-600 hover:text-gray-800 text-sm'
            >
              Cancel
            </button>
            <button
              onClick={submitReturn}
              disabled={submittingReturn}
              className='px-4 py-2 bg-orange-500 hover:bg-orange-600 text-white rounded-md text-sm disabled:opacity-50'
            >
              {submittingReturn ? 'Submitting...' : 'Submit Return'}
            </button>
          </div>
        </div>
      </Modal>
    </div>
  )
}

export default OrderTable