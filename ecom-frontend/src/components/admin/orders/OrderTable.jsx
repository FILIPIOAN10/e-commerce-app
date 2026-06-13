import { DataGrid } from '@mui/x-data-grid'
import { adminOrderTableColumn } from '../../helper/tableColumn';
import { useState } from 'react';
import { useLocation, useNavigate, useSearchParams } from 'react-router-dom';
import UpdateOrderForm from './UpdateOrderForm';
import Modal from '../../shared/Modal';
import { useSelector } from 'react-redux';

const OrderTable = ({adminOrder, pagination}) => {

  const [updateOpenModal, setUpdateOpenModal] = useState(false);
  const [selectedItem, setSelectedItem] = useState("");
  const [loader, setLoader] = useState(false);
  const navigate = useNavigate();
  const [currentPage, setCurrentPage] = useState(
    pagination?.pageNumber + 1 || 1
  );

  const [searchParams] = useSearchParams();
  const params = new URLSearchParams(searchParams);
  const pathname = useLocation().pathname;

  const { user } = useSelector((state) => state.auth);
  const isAdmin = Boolean(user?.roles?.includes("ROLE_ADMIN"));
  // ✅ adaugă seller
  const isSeller = Boolean(user?.roles?.includes("ROLE_SELLER"));
  // ✅ admin sau seller pot edita
  const canEdit = isAdmin || isSeller;

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

  return (
    <div>
      <h1 className='text-slate-800 text-3xl text-center font-bold pb-6'>
        All Orders
      </h1>
      <div>
        <DataGrid
          className='w-full'
          rows={tableRecords}
          // ✅ coloana Edit apare pentru admin și seller
          columns={adminOrderTableColumn(handleEdit, canEdit)}
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
    </div>
  )
}

export default OrderTable