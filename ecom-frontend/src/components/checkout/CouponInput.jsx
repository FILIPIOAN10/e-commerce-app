import { useState } from "react";
import { useDispatch, useSelector } from "react-redux";
import { FaTag, FaTimes, FaPlus } from "react-icons/fa";
import toast from "react-hot-toast";
import { validateCoupon, removeCoupon, clearCoupon } from "../../store/actions";

const CouponInput = ({ orderAmount }) => {
    const dispatch = useDispatch();
    const { appliedCoupons, discountAmount } = useSelector((state) => state.coupon);
    const [code, setCode] = useState("");

    const handleApply = (e) => {
        e.preventDefault();
        if (!code.trim()) {
            toast.error("Enter a coupon code");
            return;
        }
        dispatch(validateCoupon(code.trim(), orderAmount, toast));
        setCode("");
    };

    const handleRemove = (couponCode) => {
        dispatch(removeCoupon(couponCode));
    };

    const handleClear = () => {
        dispatch(clearCoupon());
        setCode("");
    };

    return (
        <div className="border-t pt-4 mt-4">
            <div className="flex items-center gap-2 mb-2">
                <FaTag className="text-blue-500" />
                <span className="font-medium text-sm">Have coupon codes?</span>
            </div>

            <form onSubmit={handleApply} className="flex gap-2 mb-3">
                <input
                    type="text"
                    value={code}
                    onChange={(e) => setCode(e.target.value)}
                    placeholder="Enter coupon code and add more"
                    className="flex-1 border rounded-lg px-3 py-2 text-sm"
                />
                <button
                    type="submit"
                    className="bg-blue-500 hover:bg-blue-600 text-white px-4 py-2 rounded-lg text-sm font-medium flex items-center gap-1"
                >
                    <FaPlus size={12} /> Add
                </button>
            </form>

            {appliedCoupons && appliedCoupons.length > 0 && (
                <div className="space-y-2 mb-3">
                    {appliedCoupons.map((c) => (
                        <div key={c} className="flex items-center justify-between bg-green-50 border border-green-200 rounded-lg p-2">
                            <span className="font-semibold text-green-700 text-sm">{c}</span>
                            <button
                                onClick={() => handleRemove(c)}
                                className="text-red-400 hover:text-red-600"
                            >
                                <FaTimes />
                            </button>
                        </div>
                    ))}
                </div>
            )}

            {appliedCoupons && appliedCoupons.length > 0 && (
                <div className="flex justify-between items-center text-sm">
                    <span className="text-green-600">Total discount: -${discountAmount}</span>
                    <button onClick={handleClear} className="text-slate-500 hover:text-red-500 underline">
                        Clear all
                    </button>
                </div>
            )}
        </div>
    );
};

export default CouponInput;
