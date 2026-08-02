import { useState } from "react";
import { useDispatch, useSelector } from "react-redux";
import { FaTag, FaTimes } from "react-icons/fa";
import toast from "react-hot-toast";
import { validateCoupon, clearCoupon } from "../../store/actions";

const CouponInput = ({ orderAmount }) => {
    const dispatch = useDispatch();
    const { appliedCoupon, discountAmount } = useSelector((state) => state.coupon);
    const [code, setCode] = useState("");

    const handleApply = (e) => {
        e.preventDefault();
        if (!code.trim()) {
            toast.error("Enter a coupon code");
            return;
        }
        dispatch(validateCoupon(code.trim(), orderAmount, toast));
    };

    const handleRemove = () => {
        dispatch(clearCoupon());
        setCode("");
    };

    return (
        <div className="border-t pt-4 mt-4">
            <div className="flex items-center gap-2 mb-2">
                <FaTag className="text-blue-500" />
                <span className="font-medium text-sm">Have a coupon code?</span>
            </div>

            {appliedCoupon ? (
                <div className="flex items-center justify-between bg-green-50 border border-green-200 rounded-lg p-3">
                    <div>
                        <span className="font-semibold text-green-700">{appliedCoupon.code}</span>
                        <span className="text-sm text-green-600 ml-2">
                            -{appliedCoupon.discountPercent}% (${discountAmount})
                        </span>
                    </div>
                    <button
                        onClick={handleRemove}
                        className="text-red-400 hover:text-red-600"
                    >
                        <FaTimes />
                    </button>
                </div>
            ) : (
                <form onSubmit={handleApply} className="flex gap-2">
                    <input
                        type="text"
                        value={code}
                        onChange={(e) => setCode(e.target.value)}
                        placeholder="Enter coupon code"
                        className="flex-1 border rounded-lg px-3 py-2 text-sm"
                    />
                    <button
                        type="submit"
                        className="bg-blue-500 hover:bg-blue-600 text-white px-4 py-2 rounded-lg text-sm font-medium"
                    >
                        Apply
                    </button>
                </form>
            )}
        </div>
    );
};

export default CouponInput;
