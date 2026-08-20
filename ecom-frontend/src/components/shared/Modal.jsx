// Importă componentele necesare pentru logica de modal din Headless UI
import { Description, Dialog, DialogBackdrop, DialogPanel, DialogTitle } from '@headlessui/react'
// Importă iconița "X" pentru închiderea modalului
import { RxCross1 } from 'react-icons/rx';

function Modal({ open, setOpen, children, title = ""}) {
  return (
    <>
      {/* Componenta principală care gestionează starea deschis/închis și accesibilitatea */}
      <Dialog open={open} onClose={() => setOpen(false)} className="relative z-10">
        
        {/* Fundalul gri semitransparent din spatele modalului */}
        <DialogBackdrop className="fixed inset-0 bg-gray-500/75 transition-opacity duration-500 ease-in-out data-closed:opacity-0" />
        
        {/* Container pentru poziționarea modalului pe tot ecranul */}
        <div className="fixed inset-0 overflow-hidden">
            <div className='absolute inset-0 overflow-hidden'>
                {/* Alinierea panelului în partea dreaptă a ecranului */}
                <div className='pointer-events-none fixed inset-y-0 right-0 flex max-w-full pl-10'>
                    {/* Panoul alb care glisează din dreapta (Slide-over) */}
                    <DialogPanel transition
                        className='pointer-events-auto relative w-screen max-w-[800px] transform transition duration-500 ease-in-out data-closed:translate-x-full sm:duration-700'>
                            
                            {/* Structura internă a modalului cu scroll pentru conținut lung */}
                            <div className='card flex h-full flex-col overflow-y-scroll rounded-none shadow-xl'>
                                
                                {/* Header-ul pentru accesibilitate (Title cerut de Headless UI) */}
                                <div className='px-4 sm:px-6'>
                                    <DialogTitle className='text-base font-semibold leading-6 text-gray-900 dark:text-gray-100'>
                                        Panel Title
                                    </DialogTitle>
                                </div>

                                {/* Zona principală de conținut */}
                                <div className='relative mt-6 flex-1 p-8'>
                                    {/* Header vizual cu Titlu și Buton de Închidere */}
                                    <div className='border-b pb-8 flex justify-between'>
                                        <h1 className='font-montserrat font-bold text-slate-800 dark:text-white text-2xl pt-4'>{title}</h1>
                                        {/* Buton care apelează setOpen(false) la click */}
                                        <button onClick={() => setOpen(false)}>
                                            <RxCross1 className='text-slate-800 dark:text-white text-2xl'/>
                                        </button>
                                    </div>    
                                    
                                    {/* Injectarea elementelor primite prin props */}
                                    {children}                                
                                </div>
                            </div>
                    </DialogPanel>
                </div>
            </div>
        </div>
      </Dialog>
    </>
  )
}

export default Modal;