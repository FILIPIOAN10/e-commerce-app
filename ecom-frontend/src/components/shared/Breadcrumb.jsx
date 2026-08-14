import { Link } from "react-router-dom";
import { MdChevronRight } from "react-icons/md";

const Breadcrumb = ({ items }) => {
    return (
        <nav className="py-4 text-sm text-slate-600 dark:text-gray-300">
            <ol className="flex items-center gap-2">
                {items.map((item, index) => (
                    <li key={index} className="flex items-center">
                        {index > 0 && <MdChevronRight className="mx-1" />}
                        {item.path ? (
                            <Link to={item.path} className="hover:text-blue-500 hover:underline">
                                {item.label}
                            </Link>
                        ) : (
                            <span className="font-medium text-slate-900 dark:text-white">{item.label}</span>
                        )}
                    </li>
                ))}
            </ol>
        </nav>
    );
};

export default Breadcrumb;
