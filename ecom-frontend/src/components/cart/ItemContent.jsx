import { HiOutlineTrash } from "react-icons/hi";
import SetQuantity from "./SetQuantity";
import { useSelector } from "react-redux";
import toast from "react-hot-toast";
import { formatPrice } from "../../utils/formatPrice";
import truncateText from "../../utils/truncateText";
import {
    useIncreaseCartQuantityMutation,
    useDecreaseCartQuantityMutation,
    useRemoveFromCartMutation,
    useSaveItemForLaterMutation,
    useMoveItemToCartMutation,
} from "../../store/api/cartApi";

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
    const cartId = useSelector((state) => state.carts.cartId);
    const [increaseQuantity] = useIncreaseCartQuantityMutation();
    const [decreaseQuantity] = useDecreaseCartQuantityMutation();
    const [removeFromCart] = useRemoveFromCartMutation();
    const [saveItemForLater] = useSaveItemForLaterMutation();
    const [moveItemToCart] = useMoveItemToCartMutation();

    const handleQtyIncrease = () => {
        increaseQuantity(productId)
            .unwrap()
            .then(() => toast.success("Quantity increased"))
            .catch((error) => toast.error(error?.data?.message || "Failed to increase quantity"));
    };

    const handleQtyDecrease = () => {
        if (quantity > 1) {
            decreaseQuantity(productId)
                .unwrap()
                .then(() => toast.success("Quantity decreased"))
                .catch((error) => toast.error(error?.data?.message || "Failed to decrease quantity"));
        }
    };

    const removeItemFromCart = () => {
        removeFromCart({ cartId, productId })
            .unwrap()
            .then(() => toast.success(`${productName} removed from cart`))
            .catch((error) => toast.error(error?.data?.message || "Failed to remove item"));
    };

    const toggleSaveForLater = () => {
        const action = savedForLater ? moveItemToCart : saveItemForLater;
        action(cartItemId)
            .unwrap()
            .then(() => toast.success(savedForLater ? "Item moved to cart" : "Item saved for later"))
            .catch((error) => toast.error(error?.data?.message || "Failed to update item"));
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
                            onClick={removeItemFromCart}
                            disabled={!cartId}
                            className="flex items-center font-semibold space-x-2 px-4 py-1 text-xs border border-rose-600 text-rose-600 rounded-md hover:bg-red-50 dark:hover:bg-red-900/30 transition-colors  duration-200 disabled:opacity-50"
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
                        handleQtyIncrease={handleQtyIncrease}
                        handleQtyDecrease={handleQtyDecrease}
                    
                    />
                </div>

                <div className="justify-self-center lg:text-[17px] text-sm text-slate-600 dark:text-gray-300 font-semibold">
                    {formatPrice( Number(quantity) * Number(unitPrice))}
                </div>
            </div>
    )
};

export default ItemContent;