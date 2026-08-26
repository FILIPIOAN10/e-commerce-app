import { useSelector } from 'react-redux';
import { Link } from 'react-router-dom';
import TwoFactorSettings from './TwoFactorSettings';
import { FaUserCircle, FaCog, FaLaptop } from 'react-icons/fa';
import { useLanguage } from '../../context/LanguageContext';

const Profile = () => {
  const { user } = useSelector((state) => state.auth);
  const isAdmin = user?.roles?.includes("ROLE_ADMIN");
  const isSeller = user?.roles?.includes("ROLE_SELLER");
  const lang = useLanguage();

  return (
    <div className="max-w-2xl mx-auto p-6 mt-10 dark:text-white">
      <div className="flex items-center justify-between mb-6">
        <h1 className="text-2xl font-bold dark:text-white">Profilul meu</h1>
        <div className="flex items-center gap-2">
          <Link
            to={`/${lang}/profile/devices`}
            className="flex items-center gap-2 bg-button-gradient text-white px-4 py-2 rounded-md hover:opacity-90 transition"
          >
            <FaLaptop /> Devices
          </Link>
          <Link
            to={`/${lang}/profile/settings`}
            className="flex items-center gap-2 bg-button-gradient text-white px-4 py-2 rounded-md hover:opacity-90 transition"
          >
            <FaCog /> Settings
          </Link>
        </div>
      </div>
      <div className="bg-white dark:bg-gray-800 shadow rounded p-6 space-y-3 dark:text-white">
        <div className="flex items-center gap-4 mb-4">
          {user?.avatarUrl ? (
            <img
              src={user.avatarUrl}
              alt="Avatar"
              className="w-16 h-16 rounded-full object-cover border-2 border-slate-200 dark:border-gray-600"
            />
          ) : (
            <FaUserCircle className="w-16 h-16 text-slate-300 dark:text-gray-500" />
          )}
          <div>
            <p className="text-lg font-semibold dark:text-white">{user?.username}</p>
            <p className="text-sm text-slate-500 dark:text-slate-400">{user?.email}</p>
            {user?.phone && <p className="text-sm text-slate-500 dark:text-slate-400">{user?.phone}</p>}
            <p className="text-sm text-slate-500 dark:text-slate-400 mt-1">
              <span className="font-medium">{isAdmin ? "Admin" : isSeller ? "Seller" : "User"}</span>
            </p>
          </div>
        </div>
      </div>

      {/* Two-Factor Authentication Settings */}
      <div className="mt-8">
        <TwoFactorSettings />
      </div>
    </div>
  );
};
export default Profile;