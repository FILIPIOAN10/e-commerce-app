// Sugestie de conținut: afișează datele user-ului din Redux
import { useSelector } from 'react-redux';

const Profile = () => {
  const { user } = useSelector((state) => state.auth);
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
    </div>
  );
};
export default Profile;