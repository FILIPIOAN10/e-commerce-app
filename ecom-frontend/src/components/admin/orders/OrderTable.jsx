
import { DataGrid } from '@mui/x-data-grid'
import { adminOrderTableColumn } from '../../helper/tableColumn';
import { current } from '@reduxjs/toolkit';
import { useState } from 'react';
import { useLocation, useNavigate, useSearchParams } from 'react-router-dom';
import UpdateOrderForm from './UpdateOrderForm';
import Modal from '../../shared/Modal';


const OrderTable = ({adminOrder,pagination}) => {

  const [updateOpenModal,setUpdateOpenModal] = useState(false);
  const [selectedItem,setSelectedItem] = useState("");
   const [loader,setLoader] = useState(false);
  const navigate = useNavigate();
  const [currentPage,setCurrentPage] = useState(
    pagination?.pageNumber +1 || 1
  );

  const [searchParams] =useSearchParams();
  const params = new URLSearchParams(searchParams);
  const pathname = useLocation().pathname;
 const tableRecords = adminOrder?.map((item) => {
  return {
    id:item.orderId,
    email:item.email,
    // Dacă e null în obiect, punem 0 sau calculăm rapid
    totalAmount: item.totalAmount ?? 0,
    status : item.orderStatus,
    date: item.orderDate,
  }
 });

 const handlePaginationChange = (paginationModel) =>  {

  const page = paginationModel.page + 1;
  setCurrentPage(page);
  params.set("page",page.toString());
  navigate(`${pathname} ?{params}`)
 }

 const handleEdit = (order) => {
  setSelectedItem(order);
  setUpdateOpenModal(true);
}

  return (
    <div>
      <h1 className='text-slate-800 text-3xl text-center font-bold pb-6'>
        All Orders</h1>
    <div>
        <DataGrid
        className='w-full'
        rows={tableRecords}
        columns={adminOrderTableColumn(handleEdit)}
        paginationMode='server'
        rowCount={pagination?.totalElements || 0}
        initialState={{
          pagination: {
            paginationModel: {
              pageSize: pagination?.pageSize || 10,
              page: currentPage -1,
            },
          },
        }}
        onPaginationMetaChange={handlePaginationChange}
        disableRowSelectionOnClick
        pageSizeOptions={[pagination?.pageSize || 10]}
        pagination
        paginationOptions={{
          showFirstButton:true,
          showLastButton:true,
          hideNextButton: currentPage  === pagination?.totalPages,
        }}
      />
    </div>
          <Modal
        open={updateOpenModal}
        setOpen={setUpdateOpenModal}
        title='Update Order Status'>
          <UpdateOrderForm
          setOpen={setUpdateOpenModal}
          open= {updateOpenModal}
          loader={loader}
          setLoader={setLoader}
          selectedId={selectedItem.id}
          selectedItem={selectedItem}
          />
      </Modal>
    </div>
  )
}

export default OrderTable
