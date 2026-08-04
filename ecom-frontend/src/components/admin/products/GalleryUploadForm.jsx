import React, { useRef, useState } from 'react'
import { FaCloudUploadAlt, FaTrashAlt } from 'react-icons/fa'
import Spinners from '../../shared/Spinners';
import { Button } from '@mui/material';
import toast from 'react-hot-toast';
import { useDispatch, useSelector } from 'react-redux';
import api from '../../../api/api';

const GalleryUploadForm = ({setOpen, product}) => {

    const fileInputRef = useRef();
    const [selectedFiles, setSelectedFiles] = useState([]);
    const [previewImages, setPreviewImages] = useState([]);
    const [loader, setLoader] = useState(false);
    const dispatch = useDispatch();

    const { user } = useSelector((state) => state.auth);
    const isAdmin = user && user?.roles?.includes("ROLE_ADMIN");

    const onHandleImageChange = (e) => {
        const files = Array.from(e.target.files);
        const validFiles = files.filter(file =>
            ["image/jpeg", "image/jpg", "image/png"].includes(file.type)
        );

        if (validFiles.length === 0) {
            toast.error("Please select valid image files (.jpeg, .jpg, .png)");
            return;
        }

        const newPreviews = validFiles.map(file => {
            const reader = new FileReader();
            reader.onloadend = () => {
                setPreviewImages(prev => [...prev, reader.result]);
            };
            reader.readAsDataURL(file);
            return file;
        });

        setSelectedFiles(prev => [...prev, ...validFiles]);
    };

    const handleRemoveImage = (index) => {
        setPreviewImages(prev => prev.filter((_, i) => i !== index));
        setSelectedFiles(prev => prev.filter((_, i) => i !== index));
    };

    const uploadGalleryHandler = async (event) => {
        event.preventDefault();
        if (selectedFiles.length === 0) {
            toast.error("Please select at least one image");
            return;
        }

        const formData = new FormData();
        selectedFiles.forEach(file => formData.append("images", file));

        setLoader(true);
        try {
            const endpoint = isAdmin
                ? `/admin/products/${product.id}/gallery`
                : `/seller/products/${product.id}/gallery`;
            await api.post(endpoint, formData, {
                headers: { "Content-Type": "multipart/form-data" }
            });
            toast.success(`${selectedFiles.length} image(s) uploaded successfully`);
            setOpen(false);
        } catch (error) {
            toast.error(error?.response?.data?.message || "Failed to upload images");
        } finally {
            setLoader(false);
        }
    };

    return (
    <div className='py-5 relative h-full'>
       <form className='space-y-4' onSubmit={uploadGalleryHandler}>
            <div className='flex flex-col gap-4 w-full'>
                <label className='flex items-center gap-2 cursor-pointer text-custom-blue border-dashed border-custom-blue rounded-md p-3 w-full justify-center'>
                    <FaCloudUploadAlt size={24} />
                    <span>Upload Gallery Images (multiple)</span>
                    <input
                        type='file'
                        ref={fileInputRef}
                        onChange={onHandleImageChange}
                        className='hidden'
                        accept='.jpeg, .jpg, .png'
                        multiple
                    />
                </label>

                {previewImages.length > 0 && (
                    <div className='flex flex-wrap gap-3'>
                        {previewImages.map((img, index) => (
                            <div key={index} className='relative'>
                                <img
                                    src={img}
                                    alt={`Preview ${index + 1}`}
                                    className='h-32 w-32 object-cover rounded-md'
                                />
                                <button
                                    type='button'
                                    onClick={() => handleRemoveImage(index)}
                                    className='absolute -top-2 -right-2 bg-rose-600 text-white rounded-full p-1 shadow-md'
                                >
                                    <FaTrashAlt size={12} />
                                </button>
                            </div>
                        ))}
                    </div>
                )}
            </div>

            <div className='flex w-full justify-between items-center pt-4'>
                <Button
                    disabled={loader}
                    onClick={() => setOpen(false)}
                    variant='outlined'
                    className='text-white py-[10px] px-4 text-sm font-medium'
                >
                    Cancel
                </Button>
                <Button
                    disabled={loader}
                    type='submit'
                    variant='contained'
                    color='primary'
                    className='bg-custom-blue text-white py-[10px] px-4 text-sm font-medium'
                >
                    {loader ? (
                        <div className='flex gap-2 items-center'>
                            <Spinners /> Loading...
                        </div>
                    ) : (
                        `Upload ${selectedFiles.length > 0 ? `(${selectedFiles.length})` : ""}`
                    )}
                </Button>
            </div>
         </form>
    </div>
  )
}

export default GalleryUploadForm;
