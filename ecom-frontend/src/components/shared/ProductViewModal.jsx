import { Button, Dialog, DialogBackdrop, DialogPanel, DialogTitle } from '@headlessui/react'
import { Divider } from '@mui/material';
import { useState } from 'react'
import Status from './Status';
import { MdClose, MdDone } from 'react-icons/md';
import { FaChevronLeft, FaChevronRight } from 'react-icons/fa';
import ReviewsSection from './ReviewsSection';

function ProductViewModal({open, setOpen, product, isAvailable}) {
  
  const {id, productName, image, description,tags,quantity, price, discount, specialPrice, images} = product;
  const [selectedImage, setSelectedImage] = useState(0);

  const galleryImages = images && images.length > 0 ? images : (image ? [image] : []);
  const hasGallery = galleryImages.length > 1;

  const handlePrev = () => {
    setSelectedImage((prev) => (prev === 0 ? galleryImages.length - 1 : prev - 1));
  };

  const handleNext = () => {
    setSelectedImage((prev) => (prev === galleryImages.length - 1 ? 0 : prev + 1));
  };

  return (
    <>
      <Dialog open={open} as="div" className="relative z-10" onClose={close}>
      <DialogBackdrop className="fixed inset-0 bg-gray-500/75  transition-opacity" />
        <div className="fixed inset-0 z-10 w-screen overflow-y-auto">
          <div className="flex min-h-full items-center justify-center p-4">
            <DialogPanel
              transition
              className="relative transform overflow-hidden rounded-lg bg-white shadow-xl transition-all md:max-w-155 md:min-w-155 w-full"
            >
                {galleryImages.length > 0 && (
                    <div className='relative flex justify-center aspect-3/2'>
                    <img 
                    src={galleryImages[selectedImage]}
                    alt={productName} 
                    className="w-full h-full object-contain"
                    />
                    {hasGallery && (
                      <>
                        <button
                          onClick={handlePrev}
                          className="absolute left-2 top-1/2 -translate-y-1/2 bg-white/80 hover:bg-white p-2 rounded-full shadow-md transition"
                        >
                          <FaChevronLeft className="text-gray-700" />
                        </button>
                        <button
                          onClick={handleNext}
                          className="absolute right-2 top-1/2 -translate-y-1/2 bg-white/80 hover:bg-white p-2 rounded-full shadow-md transition"
                        >
                          <FaChevronRight className="text-gray-700" />
                        </button>
                      </>
                    )}
                    </div>
                )}

                {hasGallery && (
                  <div className="flex justify-center gap-2 px-4 py-3 bg-gray-50">
                    {galleryImages.map((img, index) => (
                      <img
                        key={index}
                        src={img}
                        alt={`${productName} ${index + 1}`}
                        onClick={() => setSelectedImage(index)}
                        className={`w-16 h-16 object-cover rounded-md cursor-pointer border-2 transition ${
                          selectedImage === index
                            ? 'border-blue-500 ring-1 ring-blue-300'
                            : 'border-transparent opacity-60 hover:opacity-100'
                        }`}
                      />
                    ))}
                  </div>
                )}


                <div className='px-6 pt-10 pb-2'>
                <DialogTitle as="h1" className="lg:text-3xl sm:text-2xl text-xl font-semibold leading-6 text-gray-800 mb-4">
                {productName}
              </DialogTitle>


              <div className="space-y-2 text-gray-700 pb-4">
                <div className="flex items-center justify-between gap-2">
                  {specialPrice ? (
                    <div className="flex items-center gap-2">
                      <span className="text-gray-400 line-through">
                        ${Number(price).toFixed(2)}
                      </span>
                      <span className="sm:text-xl font-semibold text-slate-700">
                        ${Number(specialPrice).toFixed(2)}
                      </span>
                    </div>
                  ) : (
                    <span className="text-xl font-bold">
                      {" "}
                      ${Number(price).toFixed(2)}
                    </span>
                  )}

                  {isAvailable ? (
                    <Status
                      text="In Stock"
                      icon={MdDone}
                      bg="bg-teal-200"
                      color="text-teal-900"
                    />
                  ) : (
                    <Status
                      text="Out-Of-Stock"
                      icon={MdClose}
                      bg="bg-rose-200"
                      color="text-rose-700"
                    />
                  )}
                </div>

                <Divider />

                <p>{description}</p>
              </div>
                </div>

                {id && <ReviewsSection productId={id} />}


            <div className="px-6 py-4 flex justify-end gap-4">
              <button
                onClick={() => setOpen(false)}
                type="button"
                className="px-4 py-2 text-sm font-semibold text-slate-700 border border-slate-700 hover:text-slate-800 hover:border-slate-800 rounded-md "
              >
                Close
              </button>
            </div>
            </DialogPanel>
          </div>
        </div>
      </Dialog>
    </>
  )
}

export default ProductViewModal;