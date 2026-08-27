import { useState, useEffect } from "react";
import { useDispatch, useSelector } from "react-redux";
import toast from "react-hot-toast";
import { fetchProductQuestions, askQuestion, answerQuestion } from "../../store/actions";

const QuestionsSection = ({ productId }) => {
    const dispatch = useDispatch();
    const { user } = useSelector((state) => state.auth);
    const { questions, totalQuestions } = useSelector((state) => state.question);
    const [question, setQuestion] = useState("");
    const [answer, setAnswer] = useState("");
    const [answering, setAnswering] = useState(null);

    useEffect(() => {
        if (productId) {
            dispatch(fetchProductQuestions(productId));
        }
    }, [productId, dispatch]);

    const handleAsk = (e) => {
        e.preventDefault();
        if (!question.trim()) {
            toast.error("Please write a question");
            return;
        }
        dispatch(askQuestion(productId, question, toast));
        setQuestion("");
    };

    const handleAnswer = (e, questionId) => {
        e.preventDefault();
        if (!answer.trim()) {
            toast.error("Please write an answer");
            return;
        }
        dispatch(answerQuestion(productId, questionId, answer, toast));
        setAnswer("");
        setAnswering(null);
    };

    const canAnswer = (q) => {
        if (!user) return false;
        return (
            user.roles?.includes("ROLE_ADMIN") ||
            (q.productId === productId && user.roles?.includes("ROLE_SELLER"))
        );
    };

    return (
        <div className="mt-6 border-t pt-4 dark:text-gray-200">
            <h3 className="text-lg font-semibold mb-3">
                Q&A {totalQuestions > 0 && `(${totalQuestions})`}
            </h3>

            {user && (
                <form onSubmit={handleAsk} className="mb-6 bg-gray-50 dark:bg-gray-900 p-4 rounded-lg">
                    <textarea
                        value={question}
                        onChange={(e) => setQuestion(e.target.value)}
                        placeholder="Ask a question about this product..."
                        className="w-full border dark:border-gray-600 dark:bg-gray-700 dark:text-gray-200 rounded-lg p-2 text-sm mb-3 min-h-20"
                        maxLength={500}
                    />
                    <button
                        type="submit"
                        className="bg-blue-500 hover:bg-blue-600 text-white px-4 py-2 rounded-lg text-sm font-medium"
                    >
                        Ask Question
                    </button>
                </form>
            )}

            {questions.length === 0 ? (
                <p className="text-gray-500 dark:text-gray-400 text-sm">No questions yet</p>
            ) : (
                <div className="space-y-4 max-h-80 overflow-y-auto">
                    {questions.map((q) => (
                        <div key={q.questionId} className="border-b dark:border-gray-700 pb-3">
                            <div className="flex items-center justify-between mb-1">
                                <span className="font-medium text-sm">{q.username}</span>
                                <span className="text-xs text-gray-400 dark:text-gray-500">{q.createdAt}</span>
                            </div>
                            <p className="text-sm text-gray-700 dark:text-gray-300 font-medium">{q.question}</p>
                            {q.answer ? (
                                <p className="text-sm text-gray-600 dark:text-gray-400 mt-1 bg-gray-50 dark:bg-gray-900 p-2 rounded">
                                    <span className="font-medium text-teal-600 dark:text-teal-400">Answer: </span>
                                    {q.answer}
                                    <span className="block text-xs text-gray-400 dark:text-gray-500 mt-1">{q.answeredAt}</span>
                                </p>
                            ) : (
                                <>
                                    {answering === q.questionId ? (
                                        <form onSubmit={(e) => handleAnswer(e, q.questionId)} className="mt-2">
                                            <textarea
                                                value={answer}
                                                onChange={(e) => setAnswer(e.target.value)}
                                                placeholder="Write an answer..."
                                                className="w-full border dark:border-gray-600 dark:bg-gray-700 dark:text-gray-200 rounded-lg p-2 text-sm mb-2 min-h-16"
                                                maxLength={500}
                                            />
                                            <div className="flex gap-2">
                                                <button
                                                    type="submit"
                                                    className="bg-teal-500 hover:bg-teal-600 text-white px-3 py-1.5 rounded-lg text-sm font-medium"
                                                >
                                                    Answer
                                                </button>
                                                <button
                                                    type="button"
                                                    onClick={() => setAnswering(null)}
                                                    className="border dark:border-gray-600 dark:text-gray-200 px-3 py-1.5 rounded-lg text-sm font-medium"
                                                >
                                                    Cancel
                                                </button>
                                            </div>
                                        </form>
                                    ) : (
                                        canAnswer(q) && (
                                            <button
                                                onClick={() => setAnswering(q.questionId)}
                                                className="text-sm text-teal-600 dark:text-teal-400 hover:underline mt-1"
                                            >
                                                Answer this question
                                            </button>
                                        )
                                    )}
                                </>
                            )}
                        </div>
                    ))}
                </div>
            )}
        </div>
    );
};

export default QuestionsSection;
