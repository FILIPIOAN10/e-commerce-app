import React from "react";

/**
 * Stops one component's render error from blanking the whole application.
 *
 * Without this, a single throw during render — an API response missing a field
 * some component dereferences — unmounts the entire tree and leaves a white
 * page with no navigation, recoverable only by a manual reload.
 *
 * Placed inside Suspense and around the router, so a failed lazy chunk is
 * caught too. Resetting clears the error and remounts the subtree; `resetKey`
 * lets a route change clear it automatically, so navigating away from a broken
 * page is enough to recover.
 */
export default class ErrorBoundary extends React.Component {
    state = { error: null };

    static getDerivedStateFromError(error) {
        return { error };
    }

    componentDidCatch(error, info) {
        console.error("Render failed", error, info?.componentStack);
    }

    componentDidUpdate(prevProps) {
        if (this.state.error && prevProps.resetKey !== this.props.resetKey) {
            this.setState({ error: null });
        }
    }

    handleRetry = () => this.setState({ error: null });

    render() {
        if (!this.state.error) return this.props.children;

        return (
            <div role="alert" className="mx-auto max-w-lg px-6 py-24 text-center">
                <h2 className="text-xl font-semibold text-gray-900 dark:text-white">
                    This page didn&apos;t load
                </h2>
                <p className="mt-2 text-gray-600 dark:text-gray-400">
                    Something went wrong while rendering it. Your cart and account are unaffected.
                </p>
                <div className="mt-6 flex justify-center gap-3">
                    <button
                        type="button"
                        onClick={this.handleRetry}
                        className="rounded-md bg-blue-600 px-4 py-2 text-white transition hover:bg-blue-700"
                    >
                        Try again
                    </button>
                    <button
                        type="button"
                        onClick={() => window.location.reload()}
                        className="rounded-md border border-gray-300 px-4 py-2 text-gray-700 transition hover:bg-gray-50 dark:border-gray-600 dark:text-gray-200 dark:hover:bg-gray-800"
                    >
                        Reload
                    </button>
                </div>
            </div>
        );
    }
}
