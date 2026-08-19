import React from 'react'
import { FaBuilding, FaCheckCircle, FaEdit, FaStreetView, FaTrash } from 'react-icons/fa';
import { MdLocationCity, MdPinDrop, MdPublic } from 'react-icons/md';
import { useDispatch, useSelector } from 'react-redux'
import { selectedUserCheckoutAddress  as selectCheckoutAddress} from '../../store/actions';

const AddressList = ({ addresses, setSelectedAddress, setOpenAddressModal,setOpenDeleteModal }) => {

    const dispatch = useDispatch();
    const { selectedUserCheckoutAddress } = useSelector((state) => state.auth);

    const onEditButtonHandler = (addresses) => {
        setSelectedAddress(addresses);
        setOpenAddressModal(true);

    };
    const onDeleteButtonHandler = (addresses) => {
        setSelectedAddress(addresses);
        setOpenDeleteModal(true);
    };
    const handleAddressSelection = (addresses) => {
        dispatch(selectCheckoutAddress(addresses));
    };

  return (
    <div className='space-y-4'>
      {addresses.map((address) => (
        <div
            key={address.addressId}
            onClick={() => handleAddressSelection(address)}
            className={`p-4 border dark:border-gray-700 rounded-md cursor-pointer relative ${
                selectedUserCheckoutAddress?.addressId === address.addressId
                ? "bg-green-100 dark:bg-green-900/30":"bg-white dark:bg-gray-800"
            }`}>
                <div className="flex items-start">
                    <div className="space-y-1">
                        <div className="flex items-center">
                            <FaBuilding size={17} className='mr-2 text-gray-600 dark:text-gray-400'/> 
                            <p className='font-semibold'>{address.buildingName}</p>

                            {selectedUserCheckoutAddress?.addressId === address.addressId && (
                                <FaCheckCircle className='text-green-500 ml-2'/>
                            )}
                        </div>       


                        <div className="flex items-center">
                            <FaStreetView size={17} className='mr-2 text-gray-600 dark:text-gray-400'/> 
                            <p>{address.street}</p>
                        </div>     

                        <div className="flex items-center">
                            <MdLocationCity size={17} className='mr-2 text-gray-600 dark:text-gray-400'/> 
                            <p>{address.city}, {address.state}</p>
                        </div>  


                        <div className="flex items-center">
                            <MdPinDrop size={17} className='mr-2 text-gray-600 dark:text-gray-400'/> 
                            <p>{address.pincode}</p>
                        </div>  

                        <div className="flex items-center">
                            <MdPublic size={17} className='mr-2 text-gray-600 dark:text-gray-400'/> 
                            <p>{address.country}</p>
                        </div>  
                     </div>   
                 </div>

                 <div className="flex gap-3 absolute top-4 right-2">
                    <button
                        onClick={() => onEditButtonHandler(address)}
                        aria-label="Edit address"
                        className="p-1 rounded focus:ring-2 focus:ring-teal-500 focus:ring-offset-2 focus:ring-offset-white dark:focus:ring-offset-slate-900"
                        type="button"
                    >
                        <FaEdit size={18} className="text-teal-700 dark:text-teal-300" />
                    </button>
                    <button
                        onClick={() => onDeleteButtonHandler(address)}
                        aria-label="Delete address"
                        className="p-1 rounded focus:ring-2 focus:ring-rose-500 focus:ring-offset-2 focus:ring-offset-white dark:focus:ring-offset-slate-900"
                        type="button"
                    >
                        <FaTrash size={17} className="text-rose-600" />
                    </button>
                    </div>
            </div>
  
      ))}
    </div>
  )
}

export default AddressList
