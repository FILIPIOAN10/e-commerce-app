import { FaTruck, FaLock, FaUndo } from "react-icons/fa";
import trustBadges from "../../config/trustBadges.json";

const iconMap = {
    truck: FaTruck,
    lock: FaLock,
    return: FaUndo,
};

export const TrustBadges = () => {
    return (
        <div className="flex flex-wrap items-center gap-3 mt-4 text-sm text-gray-600 dark:text-gray-300">
            {trustBadges.map((badge) => {
                const Icon = iconMap[badge.icon] || FaLock;
                return (
                    <div key={badge.id} className="flex items-center gap-2">
                        <Icon className="w-5 h-5 text-blue-500" />
                        <span>{badge.label}</span>
                    </div>
                );
            })}
        </div>
    );
};

export default TrustBadges;
