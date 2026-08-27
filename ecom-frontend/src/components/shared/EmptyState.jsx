import { Link } from "react-router-dom";

const EmptyState = ({ icon: Icon, title, message, action, iconSize = 64, className = "" }) => {
    const ActionIcon = action?.icon;

    return (
        <div className={`min-h-[500px] flex flex-col items-center justify-center px-4 ${className}`}>
            {Icon && <Icon size={iconSize} className="mb-4 text-slate-400 dark:text-gray-500" />}
            <h2 className="text-2xl font-bold text-slate-700 dark:text-white text-center">{title}</h2>
            {message && <p className="text-lg text-slate-500 dark:text-gray-400 mt-2 text-center">{message}</p>}
            {action && (
                <Link
                    to={action.path}
                    className="mt-6 flex gap-2 items-center px-5 py-2.5 rounded-md bg-blue-500 hover:bg-blue-600 focus:ring-2 focus:ring-blue-400 focus:ring-offset-2 focus:ring-offset-white dark:focus:ring-offset-slate-900 text-white transition font-medium"
                >
                    {ActionIcon && <ActionIcon size={20} />}
                    {action.label}
                </Link>
            )}
        </div>
    );
};

export default EmptyState;
