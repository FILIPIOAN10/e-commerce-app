const ProductSkeleton = () => {
    return (
        <div className="min-h-175 pb-6 pt-14 grid 2xl:grid-cols-4 lg:grid-cols-3 sm:grid-cols-2 gap-y-6 gap-x-6 px-4">
            {Array.from({ length: 8 }).map((_, index) => (
                <div key={index} className="border rounded-lg shadow-xl overflow-hidden dark:bg-gray-800 dark:border-gray-700 p-4">
                    <div className="w-full aspect-3/2 bg-gray-200 dark:bg-gray-700 animate-pulse rounded-md" />
                    <div className="mt-4 h-6 bg-gray-200 dark:bg-gray-700 animate-pulse rounded w-3/4" />
                    <div className="mt-2 h-4 bg-gray-200 dark:bg-gray-700 animate-pulse rounded w-full" />
                    <div className="mt-2 h-4 bg-gray-200 dark:bg-gray-700 animate-pulse rounded w-5/6" />
                    <div className="mt-4 flex items-center justify-between">
                        <div className="h-6 w-24 bg-gray-200 dark:bg-gray-700 animate-pulse rounded" />
                        <div className="h-10 w-28 bg-gray-200 dark:bg-gray-700 animate-pulse rounded" />
                    </div>
                </div>
            ))}
        </div>
    );
};

export default ProductSkeleton;
