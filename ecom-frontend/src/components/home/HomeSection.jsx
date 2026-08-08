import ProductCard from "../shared/ProductCard";

const HomeSection = ({ title, subtitle, icon, products }) => {
    if (!products || products.length === 0) {
        return null;
    }

    return (
        <div className="py-5">
            <div className="flex flex-col justify-center items-center space-y-2 mb-6">
                <h2 className="text-slate-800 text-3xl font-bold dark:text-white flex items-center gap-2">
                    {icon}
                    {title}
                </h2>
                <span className="text-slate-700 dark:text-gray-300">
                    {subtitle}
                </span>
            </div>
            <div className="pb-6 pt-4 grid 2xl:grid-cols-4 lg:grid-cols-3 sm:grid-cols-2 gap-y-6 gap-x-6">
                {products.map((item) => (
                    <ProductCard key={item.productId} {...item} />
                ))}
            </div>
        </div>
    );
};

export default HomeSection;
