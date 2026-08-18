import { FaEdit, FaEye, FaImage, FaImages, FaTrashAlt, FaFilePdf, FaUndo } from "react-icons/fa";
import { MdOutlineEmail } from "react-icons/md";

export const adminProductTableColumn = (
  handleEdit,
  handleDelete,
  handleImageUpload,
  handleProductView,
  handleGalleryUpload
) => [
  {
    disableColumnMenu: true,
    sortable: false,
    field: "id",
    headerName: "ID",
    minWidth: 200,
    headerAlign: "center",
    align: "center",
    editable: false,
    headerClassName: "text-black font-semibold border",
    cellClassName: "text-slate-700 font-normal border",
    renderHeader: (_params) => <span className="text-center">ProductID</span>,
  },
  {
    disableColumnMenu: true,
    field: "productName",
    headerName: "Product Name",
    align: "center",
    width: 260,
    editable: false,
    sortable: false,
    headerAlign: "center",
    headerClassName: "text-black font-semibold text-center border ",
    cellClassName: "text-slate-700 font-normal border text-center",
    renderHeader: (_params) => <span>Product Name</span>,
  },

  {
    disableColumnMenu: true,
    field: "price",
    headerName: "Price",
    minWidth: 200,
    headerAlign: "center",
    align: "center",
    editable: false,
    headerClassName: "text-black font-semibold border",
    cellClassName: "text-slate-700 font-normal border",
    renderHeader: (_params) => <span className="text-center">Price</span>,
  },
  {
    disableColumnMenu: true,
    field: "quantity",
    headerName: "Quantity",
    minWidth: 200,
    headerAlign: "center",
    align: "center",
    editable: false,
    headerClassName: "text-black font-semibold border",
    cellClassName: "text-slate-700 font-normal border",
    renderHeader: (_params) => <span className="text-center">Quantity</span>,
  },
  {
    disableColumnMenu: true,
    field: "specialPrice",
    headerName: "Price",
    minWidth: 200,
    headerAlign: "center",
    align: "center",
    editable: false,
    headerClassName: "text-black font-semibold border",
    cellClassName: "text-slate-700 font-normal border",
    renderHeader: (_params) => (
      <span className="text-center">Special Price</span>
    ),
  },
  {
    sortable: false,
    field: "description",
    headerName: "Image",
    headerAlign: "center",
    align: "center",
    width: 200,
    editable: false,
    disableColumnMenu: true,
    headerClassName: "text-black font-semibold border ",
    cellClassName: "text-slate-700 font-normal border",
    renderHeader: (_params) => <span className="ps-10">Description</span>,
  },
  {
    sortable: false,
    field: "tags",
    headerName: "Tags",
    headerAlign: "center",
    align: "center",
    width: 220,
    editable: false,
    disableColumnMenu: true,
    headerClassName: "text-black font-semibold border ",
    cellClassName: "text-slate-700 font-normal border",
    renderHeader: (_params) => <span>Tags</span>,
  },
  {
    sortable: false,
    field: "image",
    headerName: "Image",
    headerAlign: "center",
    align: "center",
    width: 200,
    editable: false,
    disableColumnMenu: true,
    headerClassName: "text-black font-semibold border ",
    cellClassName: "text-slate-700 font-normal border",
    renderHeader: (_params) => <span className="ps-10">Image</span>,
  },

  {
    field: "action",
    headerName: "Action",
    headerAlign: "center",
    editable: false,
    headerClassName: "text-black font-semibold text-center",
    cellClassName: "text-slate-700 font-normal",
    sortable: false,
    width: 560,
    renderHeader: (_params) => <span>Action</span>,
    renderCell: (_params) => {
      return (
        <div className="flex flex-wrap justify-center items-center gap-1 h-full py-1">
          <button
            onClick={() => handleImageUpload(_params.row)}
            className="flex items-center bg-green-500 hover:bg-green-600 text-white px-2 h-8 rounded-md text-xs"
          >
            <FaImage className="mr-1" />
            Image
          </button>
          <button
            onClick={() => handleGalleryUpload(_params.row)}
            className="flex items-center bg-teal-500 hover:bg-teal-600 text-white px-2 h-8 rounded-md text-xs"
          >
            <FaImages className="mr-1" />
            Gallery
          </button>
          <button
            onClick={() => handleEdit(_params.row)}
            className="flex items-center bg-blue-500 text-white px-2 h-8 rounded-md text-xs"
          >
            <FaEdit className="mr-1" />
            Edit
          </button>

          <button
            onClick={() => handleDelete(_params.row)}
            className="flex items-center bg-red-500 text-white px-2 h-8 rounded-md text-xs"
          >
            <FaTrashAlt className="mr-1" />
            Delete
          </button>
          <button
            onClick={() => handleProductView(_params.row)}
            className="flex items-center bg-slate-800 text-white px-2 h-8 rounded-md text-xs"
          >
            <FaEye className="mr-1" />
            View
          </button>
        </div>
      );
    },
  },
];




//table column for categories in admin panel
export const categoryTableColumns = (handleEdit, handleDelete) => [
  {
    sortable: false,
    disableColumnMenu: true,
    field: "id",
    headerName: "CategoryId",
    minWidth: 300,
    headerAlign: "center",
    align: "center",
    editable: false,
    headerClassName: "text-black font-semibold border",
    cellClassName: "text-slate-700 font-normal border",
    renderHeader: (_params) => <span className="text-center">CategoryId</span>,
  },
  {
    disableColumnMenu: true,
    field: "categoryName",
    headerName: "Category Name",
    align: "center",
    width: 400,
    editable: false,
    sortable: false,
    headerAlign: "center",
    headerClassName: "text-black font-semibold text-center border ",
    cellClassName: "text-slate-700 font-normal border text-center",
    renderHeader: (_params) => <span>Category Name</span>,
  },

  {
    field: "action",
    headerName: "Action",
    headerAlign: "center",
    editable: false,
    headerClassName: "text-black font-semibold text-center",
    cellClassName: "text-slate-700 font-normal",
    sortable: false,
    width: 400,
    renderHeader: (_params) => <span>Action</span>,
    renderCell: (_params) => {
      return (
        <div className="flex justify-center space-x-2 h-full pt-2">
          <button
            onClick={() => handleEdit(_params.row)}
            className="flex items-center bg-blue-500 text-white px-4 h-9 rounded-md "
          >
            <FaEdit className="mr-2" />
            Edit
          </button>

          {/* Delete Button */}
          <button
            onClick={() => handleDelete(_params.row)}
            className="flex items-center bg-red-500 text-white px-4   h-9 rounded-md"
          >
            <FaTrashAlt className="mr-2" />
            Delete
          </button>
        </div>
      );
    },
  },
];


//table column for seller in admin panel
export const sellerTableColumns = [
  {
    disableColumnMenu: true,
    field: "id",
    headerName: "ID",
    minWidth: 400,
    headerAlign: "center",
    align: "center",
    editable: false,

    headerClassName: "text-black font-semibold border",
    cellClassName: "text-slate-700 font-normal border",
    renderHeader: (_params) => <span className="text-center">SellerID</span>,
  },
  {
    disableColumnMenu: true,
    field: "username",
    headerName: "UserName",
    minWidth: 400,
    headerAlign: "center",
    align: "center",
    editable: false,
    sortable: false,
    headerClassName: "text-black font-semibold border",
    cellClassName: "text-slate-700 font-normal border",
    renderHeader: (_params) => <span className="text-center">UserName</span>,
  },
  {
    disableColumnMenu: true,
    field: "email",
    headerName: "Email",
    align: "center",
    width: 400,
    editable: false,
    sortable: false,
    headerAlign: "center",
    headerClassName: "text-black font-semibold text-center border ",
    cellClassName: "text-slate-700 font-normal border text-center",
    renderHeader: (_params) => <span>Email</span>,
    renderCell: (_params) => {
      return (
        <div className="flex items-center justify-center gap-1">
          <span>
            <MdOutlineEmail className="text-slate-700 text-lg" />
          </span>
          <span>{_params?.row?.email}</span>
        </div>
      );
    },
  },
];

// table column for orders in admin panel
export const adminOrderTableColumn = (handleEdit, isAdmin, handleTrack, handleInvoice, handleReturn) => {
  const columns = [
    {
      disableColumnMenu: true,
      sortable: false,
      field: "id",
      headerName: "Order ID",
      minWidth: 150,
      headerAlign: "center",
      align: "center",
      editable: false,
      headerClassName: "text-black font-semibold border",
      cellClassName: "text-slate-700 font-normal border",
      renderHeader: (_params) => <span className="text-center">Order ID</span>,
    },
    {
      disableColumnMenu: true,
      field: "email",
      headerName: "Email",
      minWidth: 200,
      headerAlign: "center",
      align: "center",
      editable: false,
      sortable: false,
      headerClassName: "text-black font-semibold border",
      cellClassName: "text-slate-700 font-normal border",
      renderHeader: (_params) => <span className="text-center">Email</span>,
      renderCell: (_params) => {
        return (
          <div className="flex items-center justify-center gap-1">
            <span>
              <MdOutlineEmail className="text-slate-700 text-lg" />
            </span>
            <span>{_params?.row?.email}</span>
          </div>
        );
      },
    },
    {
      disableColumnMenu: true,
      field: "totalAmount",
      headerName: "Total Amount",
      minWidth: 150,
      headerAlign: "center",
      align: "center",
      editable: false,
      headerClassName: "text-black font-semibold border",
      cellClassName: "text-slate-700 font-normal border",
      renderHeader: (_params) => <span className="text-center">Total Amount</span>,
    },
    {
      disableColumnMenu: true,
      field: "status",
      headerName: "Status",
      minWidth: 160,
      headerAlign: "center",
      align: "center",
      editable: false,
      headerClassName: "text-black font-semibold border",
      cellClassName: "text-slate-700 font-normal border",
      renderHeader: (_params) => <span className="text-center">Status</span>,
      renderCell: (_params) => {
        const statusColors = {
          "Placed": "bg-blue-100 text-blue-700",
          "Packed": "bg-purple-100 text-purple-700",
          "Shipped": "bg-orange-100 text-orange-700",
          "Delivered": "bg-green-100 text-green-700",
          "Cancelled": "bg-red-100 text-red-600",
          "Return Requested": "bg-indigo-100 text-indigo-700",
          "Returned": "bg-gray-100 text-gray-700",
          "Refunded": "bg-teal-100 text-teal-700",
        };
        const colorClass = statusColors[_params.value] || "bg-gray-100 text-gray-600";
        return (
          <span className={`px-3 py-1 rounded-full text-xs font-semibold ${colorClass}`}>
            {_params.value}
          </span>
        );
      },
    },
    {
      disableColumnMenu: true,
      field: "date",
      headerName: "Order Date",
      minWidth: 150,
      flex:1,
      headerAlign: "center",
      align: "center",
      editable: false,
      headerClassName: "text-black font-semibold border",
      cellClassName: "text-slate-700 font-normal border",
      renderHeader: (_params) => <span className="text-center">Order Date</span>,
    },
  ];

  // Add track column for all users
  columns.push({
    field: "track",
    headerName: "Track",
    headerAlign: "center",
    editable: false,
    headerClassName: "text-black font-semibold text-center",
    cellClassName: "text-slate-700 font-normal",
    sortable: false,
    width: 120,
    renderHeader: (_params) => <span>Track</span>,
    renderCell: (_params) => {
      return (
        <div className="flex justify-center items-center h-full">
          <button
            onClick={() => handleTrack(_params.row)}
            className="flex items-center bg-slate-700 hover:bg-slate-800 text-white px-3 h-8 rounded-md text-sm"
          >
            <FaEye className="mr-1 text-xs" />
            Track
          </button>
        </div>
      );
    },
  });

  // Add invoice column for all users
  if (handleInvoice) {
    columns.push({
      field: "invoice",
      headerName: "Invoice",
      headerAlign: "center",
      editable: false,
      headerClassName: "text-black font-semibold text-center",
      cellClassName: "text-slate-700 font-normal",
      sortable: false,
      width: 120,
      renderHeader: (_params) => <span>Invoice</span>,
      renderCell: (_params) => {
        return (
          <div className="flex justify-center items-center h-full">
            <button
              onClick={() => handleInvoice(_params.row)}
              className="flex items-center bg-red-500 hover:bg-red-600 text-white px-3 h-8 rounded-md text-sm"
            >
              <FaFilePdf className="mr-1 text-xs" />
              PDF
            </button>
          </div>
        );
      },
    });
  }

  // Add return column for non-admin users (customers)
  if (handleReturn) {
    columns.push({
      field: "return",
      headerName: "Return",
      headerAlign: "center",
      editable: false,
      headerClassName: "text-black font-semibold text-center",
      cellClassName: "text-slate-700 font-normal",
      sortable: false,
      width: 120,
      renderHeader: (_params) => <span>Return</span>,
      renderCell: (_params) => {
        const status = _params.row.status;
        const returnStatus = _params.row.returnStatus;

        if (status === "Delivered" && !returnStatus) {
          return (
            <div className="flex justify-center items-center h-full">
              <button
                onClick={() => handleReturn(_params.row)}
                className="flex items-center bg-orange-500 hover:bg-orange-600 text-white px-3 h-8 rounded-md text-sm"
              >
                <FaUndo className="mr-1 text-xs" />
                Return
              </button>
            </div>
          );
        }

        if (returnStatus === "APPROVED") {
          return (
            <button
              onClick={() => handleReturn(_params.row)}
              className="flex items-center bg-indigo-500 hover:bg-indigo-600 text-white px-2 h-8 rounded-md text-xs"
            >
              Add Tracking
            </button>
          );
        }

        if (returnStatus === "SHIPPED_BACK") {
          return (
            <button
              onClick={() => handleReturn(_params.row)}
              className="flex items-center bg-blue-500 hover:bg-blue-600 text-white px-2 h-8 rounded-md text-xs"
            >
              Track
            </button>
          );
        }

        if (status === "Return Requested" || status === "Returned" || status === "Refunded" || returnStatus) {
          const label = returnStatus || status;
          return (
            <span className="text-xs text-gray-500">{label}</span>
          );
        }
        return <span className="text-xs text-gray-300">—</span>;
      },
    });
  }

  // Add action column only if user is admin
  if (isAdmin) {
    columns.push({
      field: "action",
      headerName: "Action",
      headerAlign: "center",
      editable: false,
      headerClassName: "text-black font-semibold text-center",
      cellClassName: "text-slate-700 font-normal",
      sortable: false,
      width: 200,
      renderHeader: (_params) => <span>Action</span>,
      renderCell: (_params) => {
        return (
          <div className="flex justify-center items-center space-x-2 h-full pt-2">
            <button
              onClick={() => handleEdit(_params.row)}
              className="flex items-center bg-blue-500 hover:bg-blue-600 text-white px-4 h-9 rounded-md"
            >
              <FaEdit className="mr-2" />
              Edit
            </button>
          </div>
        );
      },
    });
  }

  return columns;
};