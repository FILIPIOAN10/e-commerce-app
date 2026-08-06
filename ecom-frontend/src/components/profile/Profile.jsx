import { useSelector } from 'react-redux';
import { Link } from 'react-router-dom';
import TwoFactorSettings from './TwoFactorSettings';
import { FaUserCircle, FaCog } from 'react-icons/fa';

const Profile = () => {
  const { user } = useSelector((state) => state.auth);
  console.log("User-ul curent din Redux în Profile:", user);
  const isAdmin = user?.roles?.includes("ROLE_ADMIN");
  const isSeller = user?.roles?.includes("ROLE_SELLER");

  return (
    <div className="max-w-2xl mx-auto p-6 mt-10 dark:text-white">
      <div className="flex items-center justify-between mb-6">
        <h1 className="text-2xl font-bold dark:text-white">Profilul meu</h1>
        <Link
          to="/profile/settings"
          className="flex items-center gap-2 bg-button-gradient text-white px-4 py-2 rounded-md hover:opacity-90 transition"
        >
          <FaCog /> Settings
        </Link>
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
          </div>
        </div>
        <p className="dark:text-white"><strong className="dark:text-white">Username:</strong> {user?.username}</p>
        <p className="dark:text-white"><strong className="dark:text-white">Email:</strong> {user?.email}</p>
        {user?.phone && <p className="dark:text-white"><strong className="dark:text-white">Phone:</strong> {user?.phone}</p>}
        <p className="dark:text-white"><strong className="dark:text-white">Rol:</strong> {isAdmin ? "Admin" : isSeller ? "Seller" : "User"}</p>
      </div>

      {/* Two-Factor Authentication Settings */}
      <div className="mt-8">
        <TwoFactorSettings />
      </div>
    </div>
  );
};
export default Profile;