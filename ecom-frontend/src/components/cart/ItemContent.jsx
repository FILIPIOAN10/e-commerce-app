import { HiOutlineTrash } from "react-icons/hi";
import SetQuantity from "./SetQuantity";
import { useDispatch } from "react-redux";
import { decreaseCartQuantity, increaseCartQuantity, removeFromCart, saveItemForLater, moveItemToCart } from "../../store/actions";
import toast from "react-hot-toast";
import { formatPrice } from "../../utils/formatPrice";
import truncateText from "../../utils/truncateText";

const ItemContent = ({
    productId,
    productName,
    image,
    description,
    quantity,
    price,
    specialPrice,
    cartItemId,
    savedForLater,
}) => {
    const unitPrice = specialPrice ?? price;
    const dispatch = useDispatch();

    const handleQtyIncrease = (cartItems) => {
        dispatch(increaseCartQuantity(cartItems, toast));
    };

    const handleQtyDecrease = (cartItems) => {
        if(quantity > 1) {
            dispatch(decreaseCartQuantity(cartItems, toast));
        }
    };

    const removeItemFromCart = (cartItems) => {
        dispatch(removeFromCart(cartItems, toast));
    };

    const toggleSaveForLater = () => {
        if (savedForLater) {
            dispatch(moveItemToCart(cartItemId, toast));
        } else {
            dispatch(saveItemForLater(cartItemId, toast));
        }
    };

    return(
            <div className="grid md:grid-cols-5 grid-cols-4 md:text-md text-sm gap-4 items-center border border-slate-200 dark:border-gray-700 dark:bg-gray-800 rounded-md lg:px-4 py-4 p-2">
                <div className="md:col-span-2 justify-self-start flex flex-col gap-2">
                <div className="flex md:flex-row flex-col lg:gap-4 sm:gap-3 gap-0 items-start">

                    <h3 className="lg:text-[17px] text-sm font-semibold text-slate-600 dark:text-gray-300">
                {truncateText(productName)}
                    </h3>
  

                </div>
                    <div className="md:w-36 sm:w-24 w-12">
                        <img
                            src ={image}
                            alt ={productName}
                            className="md:h-36 sm:h-24 h-12 w-full object-cover rounded-md"
                        
                        />
               
                    <div className="flex items-start gap-5 mt-3 ">
                        <button
                            onClick={() =>removeItemFromCart({
                                image,
                                productName,
                                description,
                                specialPrice,
                                price,
                                productId,
                                quantity,

                            })}
                            className="flex items-center font-semibold space-x-2 px-4 py-1 text-xs border border-rose-600 text-rose-600 rounded-md hover:bg-red-50 dark:hover:bg-red-900/30 transition-colors  duration-200"
                        >
                            <HiOutlineTrash size={16} className="text-rose-600" />
                            Remove

                        </button>
                        <button
                            onClick={toggleSaveForLater}
                            className="text-xs text-blue-500 hover:text-blue-700 dark:hover:text-blue-300 underline"
                        >
                            {savedForLater ? "Move to cart" : "Save for later"}
                        </button>
                        </div>
                    </div>
                </div>

                <div className="justify-self-center lg:text-[17px] text-sm text-slate-600 dark:text-gray-300 font-semibold">
                   {formatPrice(Number(unitPrice))}
                </div>
                <div className="justify-self-center">
                    <SetQuantity
                        quantity={quantity}
                        cardCounter={true}
                        handleQtyIncrease={() => handleQtyIncrease({
                            image,
                            productName,
                            description,
                            specialPrice,
                            price,
                            productId,
                            quantity,
                        })}
                        handleQtyDecrease={() => {handleQtyDecrease({
                            image,
                            productName,
                            description,
                            specialPrice,
                            price,
                            productId,
                            quantity,
                        })}}
                    
                    />
                </div>

                <div className="justify-self-center lg:text-[17px] text-sm text-slate-600 dark:text-gray-300 font-semibold">
                    {formatPrice( Number(quantity) * Number(unitPrice))}
                </div>
            </div>
    )
};

export default ItemContent;