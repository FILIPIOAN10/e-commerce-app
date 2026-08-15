import { useState, useEffect } from "react";
import { FaStar, FaTrashAlt, FaEdit, FaThumbsUp, FaThumbsDown } from "react-icons/fa";
import { MdVerified } from "react-icons/md";
import { useDispatch, useSelector } from "react-redux";
import toast from "react-hot-toast";
import { fetchProductReviews, addReview, updateReview, deleteReview, markReviewHelpful, markReviewUnhelpful } from "../../store/actions";

const ReviewsSection = ({ productId }) => {
    const dispatch = useDispatch();
    const { user } = useSelector((state) => state.auth);
    const { reviews, averageRating, totalReviews } = useSelector((state) => state.review);
    const [rating, setRating] = useState(5);
    const [comment, setComment] = useState("");
    const [hoverRating, setHoverRating] = useState(0);
    const [editing, setEditing] = useState(false);

    useEffect(() => {
        if (productId) {
            dispatch(fetchProductReviews(productId));
        }
    }, [productId, dispatch]);

    const handleSubmit = (e) => {
        e.preventDefault();
        if (!comment.trim()) {
            toast.error("Please write a comment");
            return;
        }
        if (editing) {
            dispatch(updateReview(productId, rating, comment, toast));
            setEditing(false);
        } else {
            dispatch(addReview(productId, rating, comment, toast));
        }
        setComment("");
        setRating(5);
    };

    const handleEdit = (review) => {
        setRating(review.rating);
        setComment(review.comment);
        setEditing(true);
    };

    const handleDelete = () => {
        dispatch(deleteReview(productId, toast));
        setEditing(false);
        setComment("");
        setRating(5);
    };

    const myReview = reviews.find((r) => r.username === user?.username);

    return (
        <div className="mt-6 border-t pt-4 dark:text-gray-200">
            <h3 className="text-lg font-semibold mb-3">
                Reviews {totalReviews > 0 && `(${totalReviews})`}
            </h3>

            {totalReviews > 0 && (
                <div className="flex items-center gap-2 mb-4">
                    <div className="flex">
                        {[1, 2, 3, 4, 5].map((star) => (
                            <FaStar
                                key={star}
                                className={star <= Math.round(averageRating) ? "text-yellow-400" : "text-gray-300"}
                            />
                        ))}
                    </div>
                    <span className="text-sm font-medium text-gray-600 dark:text-gray-400">
                        {Number(averageRating).toFixed(1)} / 5
                    </span>
                </div>
            )}

            {user && !user.roles?.includes("ROLE_ADMIN") && (
                <form onSubmit={handleSubmit} className="mb-6 bg-gray-50 dark:bg-gray-900 p-4 rounded-lg">
                    <div className="flex items-center gap-2 mb-3">
                        <span className="text-sm font-medium">Your rating:</span>
                        <div className="flex">
                            {[1, 2, 3, 4, 5].map((star) => (
                                <FaStar
                                    key={star}
                                    className={`cursor-pointer text-xl transition ${
                                        star <= (hoverRating || rating)
                                            ? "text-yellow-400"
                                            : "text-gray-300 hover:text-yellow-300"
                                    }`}
                                    onClick={() => setRating(star)}
                                    onMouseEnter={() => setHoverRating(star)}
                                    onMouseLeave={() => setHoverRating(0)}
                                />
                            ))}
                        </div>
                    </div>
                    <textarea
                        value={comment}
                        onChange={(e) => setComment(e.target.value)}
                        placeholder="Write your review..."
                        className="w-full border dark:border-gray-600 dark:bg-gray-700 dark:text-gray-200 rounded-lg p-2 text-sm mb-3 min-h-20"
                        maxLength={500}
                    />
                    <div className="flex gap-2">
                        <button
                            type="submit"
                            className="bg-blue-500 hover:bg-blue-600 text-white px-4 py-2 rounded-lg text-sm font-medium"
                        >
                            {editing ? "Update Review" : "Submit Review"}
                        </button>
                        {editing && (
                            <button
                                type="button"
                                onClick={() => {
                                    setEditing(false);
                                    setComment("");
                                    setRating(5);
                                }}
                                className="border dark:border-gray-600 dark:text-gray-200 px-4 py-2 rounded-lg text-sm font-medium"
                            >
                                Cancel
                            </button>
                        )}
                        {myReview && !editing && (
                            <>
                                <button
                                    type="button"
                                    onClick={() => handleEdit(myReview)}
                                    className="border dark:border-gray-600 dark:text-gray-200 px-4 py-2 rounded-lg text-sm font-medium flex items-center gap-1"
                                >
                                    <FaEdit /> Edit
                                </button>
                                <button
                                    type="button"
                                    onClick={handleDelete}
                                    className="border border-red-400 text-red-500 px-4 py-2 rounded-lg text-sm font-medium flex items-center gap-1"
                                >
                                    <FaTrashAlt /> Delete
                                </button>
                            </>
                        )}
                    </div>
                </form>
            )}

            {reviews.length === 0 ? (
                <p className="text-gray-500 dark:text-gray-400 text-sm">No reviews yet. Be the first to review!</p>
            ) : (
                <div className="space-y-4 max-h-80 overflow-y-auto">
                    {reviews.map((review) => (
                        <div key={review.reviewId} className="border-b dark:border-gray-700 pb-3">
                            <div className="flex items-center justify-between mb-1">
                                <div className="flex items-center gap-2">
                                    <span className="font-medium text-sm">{review.username}</span>
                                    {review.verifiedPurchase && (
                                        <span className="flex items-center gap-0.5 text-xs text-teal-600 dark:text-teal-400">
                                            <MdVerified /> Verified purchase
                                        </span>
                                    )}
                                </div>
                                <span className="text-xs text-gray-400 dark:text-gray-500">{review.createdAt}</span>
                            </div>
                            <div className="flex mb-1">
                                {[1, 2, 3, 4, 5].map((star) => (
                                    <FaStar
                                        key={star}
                                        className={star <= review.rating ? "text-yellow-400 text-sm" : "text-gray-300 text-sm"}
                                    />
                                ))}
                            </div>
                            <p className="text-sm text-gray-600 dark:text-gray-400">{review.comment}</p>
                            <div className="flex items-center gap-3 mt-2">
                                <button
                                    onClick={() => dispatch(markReviewHelpful(productId, review.reviewId, toast))}
                                    className="flex items-center gap-1 text-xs text-gray-500 hover:text-blue-500 dark:text-gray-400 dark:hover:text-blue-400"
                                >
                                    <FaThumbsUp /> Yes ({review.helpfulCount || 0})
                                </button>
                                <button
                                    onClick={() => dispatch(markReviewUnhelpful(productId, review.reviewId, toast))}
                                    className="flex items-center gap-1 text-xs text-gray-500 hover:text-red-500 dark:text-gray-400 dark:hover:text-red-400"
                                >
                                    <FaThumbsDown /> No ({review.unhelpfulCount || 0})
                                </button>
                            </div>
                        </div>
                    ))}
                </div>
            )}
        </div>
    );
};

export default ReviewsSection;
