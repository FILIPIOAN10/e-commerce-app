import React, { useState } from 'react'
import { MdAddShoppingCart } from 'react-icons/md';
import { useDispatch, useSelector } from 'react-redux';
import Loader from '../../shared/Loader';
import { FaBoxOpen } from 'react-icons/fa';
import { DataGrid } from '@mui/x-data-grid';
import { adminProductTableColumn } from '../../helper/tableColumn';
import { useDashboardProductFilter } from '../../../hooks/useProductFilter';
import Modal from '../../shared/Modal';
import AddProductForm from './AddProductForm';
import DeleteModal from '../../shared/DeleteModal';
import { deleteProduct } from '../../../store/actions';
import toast from 'react-hot-toast';
import ImageUploadForm from './ImageUploadForm';
import GalleryUploadForm from './GalleryUploadForm';
import ProductViewModal from '../../shared/ProductViewModal';
import { useLocation, useNavigate, useSearchParams } from 'react-router-dom';

const AdminProducts = () => {
  
  // const products =[ { "productId": 1, "productName": "Nikon Z6 II", "image": "http://localhost:8080/images/cal.png", "description": "Aparat foto mirrorless full-frame cu autofocus rapid și filmare 4K. Potrivit pentru fotografi profesioniști și creatori de conținut.", "quantity": 7, "price": 2000.0, "discount": 9.0, "specialPrice": 1820.0 } ];
  // const pagination = { pageNumber:0,pageSize:50,totalElements:11,totalPages:1,lastPage:true};
  

  const {products,pagination} = useSelector((state) => state.products);
  const {isLoading} = useSelector((state) => state.errors);
  // check if order exist or not
  const emptyProduct= !products || products?.length === 0;
    const [currentPage,setCurrentPage] = useState(
    pagination?.pageNumber +1 || 1
  );

  const [selectedProduct,setSelectedProduct] = useState('');

  const [openUpdateModal,setOpenUpdateModal] = useState(false);

  const [openAddModal,setOpenAddModal] = useState(false);
  
  const [openDeleteModal,setOpenDeleteModal] = useState(false);
  const [openProductViewModal,setOpenProductViewModal] = useState(false);
  const [loader,setLoader] = useState(false);
  const [openImageUploadModal, setOpenImageUploadModal] = useState(false);
  const [openGalleryUploadModal, setOpenGalleryUploadModal] = useState(false);
  
  // for pagination
  const navigate = useNavigate();

    const [searchParams] =useSearchParams();
  const params = new URLSearchParams(searchParams);
  const pathname = useLocation().pathname;
  const { user } = useSelector((state) => state.auth);
  const isAdmin = user && user?.roles?.includes("ROLE_ADMIN");

  const dispatch = useDispatch();

  const tableRecords = products?.map((item) => {
  return {
    id:item.productId,
    productName: item.productName,
    description:item.description,
    tags:item.tags || "",
    discount:item.discount,
    image:item.image,
    images:item.images || [],
    price:item.price,
    quantity:item.quantity,
    specialPrice:item.specialPrice,
  }
 });

 const handleEdit = (product) =>{
  setSelectedProduct(product);
  setOpenUpdateModal(true);
 };
  const handleDelete = (product) =>{

    setSelectedProduct(product);
    setOpenDeleteModal(true);

 };


  const handleImageUpload = (product) =>{
  setSelectedProduct(product);
  setOpenImageUploadModal(true);

 };

 const handleGalleryUpload = (product) =>{
  setSelectedProduct(product);
  setOpenGalleryUploadModal(true);

 };

  const handleProductView = (product) =>{
    setSelectedProduct(product);
    setOpenProductViewModal(true);

 };

const handlePaginationChange = (paginationModel) =>{
  const page = paginationModel.page + 1;
  setCurrentPage(page);
  params.set("page",page.toString());
  navigate(`${pathname}?${params}`)
 };

 const onDeleteHandler = () => {
  
  const deleteParams = new URLSearchParams();
  const page = searchParams.get("page") ? Number(searchParams.get("page")) : currentPage;
  deleteParams.set("pageNumber", Math.max(page-1, 0));

  dispatch(deleteProduct(
    setLoader,
    selectedProduct?.id,
    toast,
    setOpenDeleteModal,
    isAdmin,
    deleteParams.toString()
  ));
 };



  useDashboardProductFilter();

  return (
    <div>
      <div className='pt-6 flex justify-end'>
        <button
          onClick={() => setOpenAddModal(true)}
          className='bg-custom-blue hover:bg-blue-800 text-white font-semibold py-2 px-4 flex items-center rounded-md shadow-md transition-colors hover:text-slate-300 duration-300'
            >
          <MdAddShoppingCart className='text-xl'/>
          Add Product
        </button>
      </div>

      {!emptyProduct && (
        <h1 className='text-slate-800 dark:text-white text-3xl text-center font-bold pb-6'>All products</h1>
      )}
      {isLoading ? (

        <Loader/>
      ): (
        <>
        {emptyProduct ? (
          <div className='flex flex-col items-center justify-center text-gray-600 dark:text-gray-400 py-10'>
            <FaBoxOpen size={50} className='mb-3' />
            <h2 className='text-2xl font-semibold dark:text-white'>
              No products created</h2>
            
          </div>
        ) :(
          <div className='max-w-full'>
                  <DataGrid
                  className='w-full'
                  rows={tableRecords}
                  columns={adminProductTableColumn(handleEdit,handleDelete,handleImageUpload,handleProductView,handleGalleryUpload)}
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
                  onPaginationModelChange={handlePaginationChange}
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
        )}
        </>
      )}

      <Modal
        open={openUpdateModal || openAddModal}
        setOpen={ openUpdateModal ? setOpenUpdateModal : setOpenAddModal}
        title={openUpdateModal ? "Update Product": "Add Product"}>
            <AddProductForm
             //  give acces to the fuction which will allow openning and closing the from
              setOpen ={openUpdateModal ? setOpenUpdateModal : setOpenAddModal}
              product= {selectedProduct}
              update={openUpdateModal}

            />
    </Modal>


    <Modal
        open={openImageUploadModal}
        setOpen={ setOpenImageUploadModal}
        title="Add Product Image">
            <ImageUploadForm
             //  give acces to the fuction which will allow openning and closing the from
              setOpen ={setOpenImageUploadModal}
              product= {selectedProduct}
              update={openUpdateModal}

            />
    </Modal>

    <Modal
        open={openGalleryUploadModal}
        setOpen={setOpenGalleryUploadModal}
        title="Upload Gallery Images">
            <GalleryUploadForm
              setOpen={setOpenGalleryUploadModal}
              product={selectedProduct}
            />
    </Modal>



      <DeleteModal
        open={openDeleteModal}
        setOpen={ setOpenDeleteModal}
        loader={loader}
        title="Delete Product?"
        onDeleteHandler = {onDeleteHandler}/>
        <ProductViewModal
        
          open={openProductViewModal}
          setOpen={setOpenProductViewModal}
          product={selectedProduct}
        />

    </div>
    
  )
}

export default AdminProducts
