import React from "react";

const Skeleton = ({ variant = "text", count = 6, className = "" }) => {
  const textSkeleton = (
    <div
      role="status"
      className={`space-y-2.5 animate-pulse w-full ${className}`}
    >
      {Array.from({ length: count }).map((_, index) => (
        <div key={index} className="flex items-center w-full">
          <div className="h-2.5 bg-gray-200 rounded-full dark:bg-gray-700 w-32" />
          <div className="h-2.5 ms-2 bg-gray-300 rounded-full dark:bg-gray-600 w-24" />
          <div className="h-2.5 ms-2 bg-gray-300 rounded-full dark:bg-gray-600 flex-1" />
        </div>
      ))}
    </div>
  );

  const tableSkeleton = (
    <div
      role="status"
      className={`animate-pulse w-full space-y-3 ${className}`}
    >
      <div className="h-10 bg-gray-200 dark:bg-gray-700 rounded w-full" />
      {Array.from({ length: count }).map((_, index) => (
        <div
          key={index}
          className="flex items-center w-full gap-4 h-12"
        >
          <div className="h-4 bg-gray-200 dark:bg-gray-700 rounded flex-1" />
          <div className="h-4 bg-gray-300 dark:bg-gray-600 rounded w-32" />
          <div className="h-4 bg-gray-300 dark:bg-gray-600 rounded w-24" />
          <div className="h-4 bg-gray-200 dark:bg-gray-700 rounded w-20" />
        </div>
      ))}
    </div>
  );

  const detailSkeleton = (
    <div
      role="status"
      className={`animate-pulse w-full grid grid-cols-1 lg:grid-cols-2 gap-10 ${className}`}
    >
      <div className="aspect-[4/3] bg-gray-200 dark:bg-gray-700 rounded-2xl" />
      <div className="space-y-4">
        <div className="h-6 bg-gray-300 dark:bg-gray-600 rounded w-1/4" />
        <div className="h-10 bg-gray-200 dark:bg-gray-700 rounded w-3/4" />
        <div className="h-4 bg-gray-300 dark:bg-gray-600 rounded w-1/2" />
        <div className="h-4 bg-gray-300 dark:bg-gray-600 rounded w-2/3" />
        <div className="h-12 bg-gray-200 dark:bg-gray-700 rounded w-1/3" />
        <div className="h-4 bg-gray-300 dark:bg-gray-600 rounded w-full" />
        <div className="h-4 bg-gray-300 dark:bg-gray-600 rounded w-5/6" />
        <div className="flex gap-3">
          <div className="h-12 bg-gray-200 dark:bg-gray-700 rounded w-32" />
          <div className="h-12 bg-gray-300 dark:bg-gray-600 rounded w-32" />
        </div>
      </div>
    </div>
  );

  const gridSkeleton = (
    <div
      role="status"
      className={`min-h-175 pb-6 pt-14 grid 2xl:grid-cols-4 lg:grid-cols-3 sm:grid-cols-2 gap-y-6 gap-x-6 px-4 ${className}`}
    >
      {Array.from({ length: count }).map((_, index) => (
        <div
          key={index}
          className="border rounded-lg shadow-xl overflow-hidden dark:bg-gray-800 dark:border-gray-700 p-4"
        >
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

  switch (variant) {
    case "table":
      return tableSkeleton;
    case "detail":
      return detailSkeleton;
    case "grid":
      return gridSkeleton;
    case "text":
    default:
      return textSkeleton;
  }
};

export default Skeleton;