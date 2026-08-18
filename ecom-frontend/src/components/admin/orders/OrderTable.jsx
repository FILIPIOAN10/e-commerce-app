import { DataGrid } from '@mui/x-data-grid'
import { adminOrderTableColumn } from '../../helper/tableColumn';
import { useState, useEffect } from 'react';
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
  const [selectedReturn, setSelectedReturn] = useState(null);
  const [returnReason, setReturnReason] = useState('');
  const [carrierName, setCarrierName] = useState('');
  const [trackingNumber, setTrackingNumber] = useState('');
  const [returnMap, setReturnMap] = useState({});
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

  useEffect(() => {
    if (canEdit) {
      setReturnMap({});
      return;
    }
    const loadReturns = async () => {
      try {
        const { data } = await api.get('/orders/my-returns?page=0&size=100');
        const map = {};
        data.content?.forEach((ret) => {
          map[ret.orderId] = ret;
        });
        setReturnMap(map);
      } catch {
        setReturnMap({});
      }
    };
    loadReturns();
  }, [canEdit, adminOrder]);

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
    } catch {
      toast.error(`Failed to export ${format.toUpperCase()}`);
    } finally {
      setExporting(false);
    }
  };

  const tableRecords = adminOrder?.map((item) => {
    const ret = returnMap[item.orderId];
    return {
      id: item.orderId,
      email: item.email,
      totalAmount: item.totalAmount ?? 0,
      status: item.orderStatus,
      date: item.orderDate,
      returnId: ret?.id,
      returnStatus: ret?.status,
      trackingNumber: ret?.trackingNumber,
      trackingStatus: ret?.trackingStatus,
      carrierName: ret?.carrierName,
    };
  });

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
    const ret = returnMap[order.id];
    setSelectedReturn(ret || null);
    setReturnReason('');
    setCarrierName(ret?.carrierName || '');
    setTrackingNumber(ret?.trackingNumber || '');
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

  const submitTracking = async () => {
    if (!trackingNumber.trim()) {
      toast.error('Please provide a tracking number');
      return;
    }
    setSubmittingReturn(true);
    try {
      await api.put(`/returns/${selectedReturn.id}/tracking`, {
        carrierName,
        trackingNumber,
      });
      toast.success('Tracking number submitted');
      setReturnOpenModal(false);
      if (pathname.includes('my-orders') || pathname.includes('profile')) {
        window.location.reload();
      }
    } catch (error) {
      const msg = error?.response?.data?.message || 'Failed to submit tracking number';
      toast.error(msg);
    } finally {
      setSubmittingReturn(false);
    }
  };

  const getReturnModalTitle = () => {
    if (!selectedReturn) return 'Request Return';
    if (selectedReturn.status === 'REQUESTED') return 'Return Pending Approval';
    if (selectedReturn.status === 'APPROVED') return 'Add Return Tracking';
    if (selectedReturn.status === 'SHIPPED_BACK') return 'Return Tracking';
    if (selectedReturn.status === 'REFUNDED') return 'Return Completed';
    if (selectedReturn.status === 'REJECTED') return 'Return Rejected';
    return 'Return';
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
        title={getReturnModalTitle()}
      >
        <div className='p-4'>
          {!selectedReturn && (
            <>
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
            </>
          )}

          {selectedReturn?.status === 'REQUESTED' && (
            <div className='text-sm text-gray-600'>
              <p className='mb-2'>Your return request for order #{returnOrderId} is pending admin approval.</p>
              <p className='text-gray-500'>Reason: {selectedReturn.reason}</p>
            </div>
          )}

          {selectedReturn?.status === 'REJECTED' && (
            <div className='text-sm text-red-600'>
              <p>Your return request was rejected.</p>
              {selectedReturn.adminNote && <p className='text-gray-500 mt-1'>Note: {selectedReturn.adminNote}</p>}
            </div>
          )}

          {selectedReturn?.status === 'APPROVED' && (
            <>
              <p className='text-sm text-gray-600 mb-3'>
                Your return was approved. Provide the carrier and tracking number.
              </p>
              <input
                type='text'
                value={carrierName}
                onChange={(e) => setCarrierName(e.target.value)}
                placeholder='Carrier (e.g. DHL, FanCourier)'
                className='w-full border border-gray-300 rounded-md p-3 text-sm mb-3 focus:outline-none focus:ring-2 focus:ring-orange-500'
              />
              <input
                type='text'
                value={trackingNumber}
                onChange={(e) => setTrackingNumber(e.target.value)}
                placeholder='Tracking number'
                className='w-full border border-gray-300 rounded-md p-3 text-sm mb-3 focus:outline-none focus:ring-2 focus:ring-orange-500'
              />
              <p className='text-xs text-gray-500 mb-3'>
                Tip: for the mock courier, numbers ending in <strong>DEL</strong> simulate delivery.
              </p>
              <div className='flex justify-end gap-2 mt-4'>
                <button
                  onClick={() => setReturnOpenModal(false)}
                  className='px-4 py-2 text-gray-600 hover:text-gray-800 text-sm'
                >
                  Cancel
                </button>
                <button
                  onClick={submitTracking}
                  disabled={submittingReturn}
                  className='px-4 py-2 bg-orange-500 hover:bg-orange-600 text-white rounded-md text-sm disabled:opacity-50'
                >
                  {submittingReturn ? 'Submitting...' : 'Submit Tracking'}
                </button>
              </div>
            </>
          )}

          {selectedReturn?.status === 'SHIPPED_BACK' && (
            <div className='text-sm text-gray-600'>
              <p className='mb-2'>Return is on the way.</p>
              <p className='text-gray-500'>Carrier: {selectedReturn.carrierName || '—'}</p>
              <p className='text-gray-500'>Tracking: {selectedReturn.trackingNumber || '—'}</p>
              <p className='text-gray-500'>
                Tracking status: {' '}
                <span className={`px-2 py-0.5 rounded-full text-xs font-semibold ${
                  selectedReturn.trackingStatus === 'DELIVERED'
                    ? 'bg-green-100 text-green-700'
                    : selectedReturn.trackingStatus === 'EXCEPTION'
                    ? 'bg-red-100 text-red-700'
                    : 'bg-blue-100 text-blue-700'
                }`}>
                  {selectedReturn.trackingStatus || 'Pending'}
                </span>
              </p>
            </div>
          )}

          {selectedReturn?.status === 'REFUNDED' && (
            <div className='text-sm text-green-600'>
              <p>Return completed and refund of ${selectedReturn.refundAmount?.toFixed(2)} processed.</p>
              {selectedReturn.trackingNumber && (
                <p className='text-gray-500 mt-1'>Tracking: {selectedReturn.trackingNumber}</p>
              )}
            </div>
          )}
        </div>
      </Modal>
    </div>
  )
}

export default OrderTable