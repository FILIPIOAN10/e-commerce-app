// Sugestie de conținut: afișează datele user-ului din Redux
import { useSelector } from 'react-redux';
import TwoFactorSettings from './TwoFactorSettings';

const Profile = () => {
  const { user } = useSelector((state) => state.auth);
  console.log("User-ul curent din Redux în Profile:", user);
  const isAdmin = user?.roles?.includes("ROLE_ADMIN");
  const isSeller = user?.roles?.includes("ROLE_SELLER");

  return (
    <div className="max-w-2xl mx-auto p-6 mt-10">
      <h1 className="text-2xl font-bold mb-4">Profilul meu</h1>
      <div className="bg-white shadow rounded p-6 space-y-3">
        <p><strong>Username:</strong> {user?.username}</p>
        <p><strong>Rol:</strong> {isAdmin ? "Admin" : isSeller ? "Seller" : "User"}</p>
        {/* Aici poți adăuga: email, telefon, avatar, buton editare etc. */}
      </div>

      {/* Two-Factor Authentication Settings */}
      <div className="mt-8">
        <TwoFactorSettings />
      </div>
    </div>
  );
};
export default Profile;