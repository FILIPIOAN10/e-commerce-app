const initialState = {
    questions: [],
    totalQuestions: 0,
    loading: false,
    error: null,
};

const questionReducer = (state = initialState, action) => {
    switch (action.type) {
        case "fetchQuestionsSuccess":
            return {
                ...state,
                questions: action.payload.content,
                totalQuestions: action.payload.totalQuestions,
                error: null,
            };
        case "questionError":
            return {
                ...state,
                error: action.payload,
            };
        case "clearQuestions":
            return initialState;
        default:
            return state;
    }
};

export default questionReducer;
