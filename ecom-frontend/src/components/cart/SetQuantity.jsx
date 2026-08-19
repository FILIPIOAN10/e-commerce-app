

const btnStyles="border-[1.2px] border-slate-800 dark:border-gray-300 px-3 rounded focus:ring-2 focus:ring-blue-500 focus:ring-offset-2 focus:ring-offset-white dark:focus:ring-offset-slate-900"
const SetQuantity = ({
    quantity,
    cardCounter,
    handleQtyIncrease,
    handleQtyDecrease,

}) => {

    return (
    <div className="flex gap-8 items-center">
        {cardCounter ? null : <div className="font-semibold"> QUANTITY</div>}
        <div className="flex md:flex-row flex-col gap-4 items-center lg:text-[22px] text-sm">
            <button
                disabled={quantity<=1}
                className={btnStyles}
                onClick={handleQtyDecrease}
                aria-label="Decrease quantity"
                type="button"
            >
                -
            </button>

                <div className="text-red-500" aria-live="polite">{quantity}</div>
            <button
                className={btnStyles}
                onClick={handleQtyIncrease}
                aria-label="Increase quantity"
                type="button"
            >
                +
            </button>

        </div>
    </div>
    );

};

export default SetQuantity;