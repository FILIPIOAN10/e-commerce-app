/**
 * Shared request status.
 *
 * `isLoading` is derived from a count of in-flight requests rather than being a
 * bare boolean. Nineteen components read this one object while 77 dispatch
 * sites write to it, so with a boolean the *first* response to land cleared the
 * flag for everyone: on /products, `fetchCategories` returning before
 * `fetchProducts` hid the skeleton and rendered an empty grid until the second
 * response arrived. Counting means the spinner clears when the last request
 * finishes, not the first.
 *
 * The exposed shape is unchanged, so no consumer needs to know. `pending` is an
 * implementation detail and is not read by any component.
 *
 * This does not fix the other half of the problem: `errorMessage` is still
 * global, so a failure in one feature is displayed by whichever screen happens
 * to be mounted. That needs per-request state, which is what RTK Query already
 * provides for the endpoints that have been migrated to it.
 */
const initialState = {
    isLoading: false,
    errorMessage: null,
    categoryLoader: false,
    categoryError: null,
    btnLoader: false,
    pending: 0,
};

const decrement = (state) => {
    // Clamped: some flows dispatch IS_SUCCESS without a matching IS_FETCHING
    // (the button-loader paths), which would otherwise drive this negative and
    // leave isLoading stuck on.
    const pending = Math.max(0, state.pending - 1);
    return { pending, isLoading: pending > 0 };
};

export const errorReducer = (state = initialState, action) => {
    switch (action.type) {
        case "IS_FETCHING": {
            const pending = state.pending + 1;
            return {
                ...state,
                pending,
                isLoading: true,
                errorMessage: null,
            };
        }

        case "BUTTON_LOADER":
            return {
                ...state,
                btnLoader: true,
                errorMessage: null,
                categoryError: null,
                categoryLoader: false,
            };

        case "IS_SUCCESS":
            return {
                ...state,
                ...decrement(state),
                errorMessage: null,
                btnLoader: false,
                categoryError: null,
                categoryLoader: false,
            };

        case "IS_ERROR":
            return {
                ...state,
                ...decrement(state),
                errorMessage: action.payload,
                btnLoader: false,
                categoryLoader: false,
            };

        case "CATEGORY_SUCCESS":
            return {
                ...state,
                categoryLoader: false,
                categoryError: null,
            };

        case "CATEGORY_LOADER":
            return {
                ...state,
                categoryLoader: true,
                categoryError: null,
                errorMessage: null,
            };

        default:
            return state;
    }
};
