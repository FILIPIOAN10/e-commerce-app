import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import api from '../../api/api';
import { FaDesktop, FaMobileAlt, FaTabletAlt, FaTimes, FaSignOutAlt, FaLaptop, FaArrowLeft } from 'react-icons/fa';
import toast from 'react-hot-toast';

const getDeviceIcon = (deviceInfo) => {
  const info = (deviceInfo || '').toLowerCase();
  if (info.includes('mobile')) return <FaMobileAlt className="text-2xl" />;
  if (info.includes('tablet')) return <FaTabletAlt className="text-2xl" />;
  if (info.includes('desktop') || info.includes('laptop')) return <FaDesktop className="text-2xl" />;
  return <FaLaptop className="text-2xl" />;
};

const formatDate = (timestamp) => {
  if (!timestamp) return '—';
  return new Date(Number(timestamp)).toLocaleString();
};

const Devices = () => {
  const [sessions, setSessions] = useState([]);
  const [loading, setLoading] = useState(true);

  const fetchSessions = async () => {
    try {
      setLoading(true);
      const { data } = await api.get('/auth/devices');
      setSessions(Array.isArray(data) ? data : []);
    } catch (err) {
      toast.error(err?.response?.data?.message || 'Could not load active sessions');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchSessions();
  }, []);

  const handleRevoke = async (token) => {
    try {
      await api.delete(`/auth/devices/${token}`);
      toast.success('Session revoked');
      await fetchSessions();
    } catch (err) {
      toast.error(err?.response?.data?.message || 'Failed to revoke session');
    }
  };

  const handleRevokeOthers = async () => {
    try {
      await api.delete('/auth/devices');
      toast.success('All other devices signed out');
      await fetchSessions();
    } catch (err) {
      toast.error(err?.response?.data?.message || 'Failed to sign out other devices');
    }
  };

  const currentSession = sessions.find((s) => s.current);
  const otherSessions = sessions.filter((s) => !s.current);

  return (
    <div className="max-w-3xl mx-auto p-6 mt-10 dark:text-white">
      <div className="flex items-center justify-between mb-6">
        <div className="flex items-center gap-3">
          <Link to="/profile" className="text-slate-500 hover:text-slate-700 dark:text-slate-400 dark:hover:text-slate-200 transition">
            <FaArrowLeft />
          </Link>
          <h1 className="text-2xl font-bold dark:text-white">Active Devices</h1>
        </div>
        {otherSessions.length > 0 && (
          <button
            onClick={handleRevokeOthers}
            className="flex items-center gap-2 bg-red-600 text-white px-4 py-2 rounded-md hover:bg-red-700 transition"
          >
            <FaSignOutAlt /> Sign out all other devices
          </button>
        )}
      </div>

      <div className="bg-white dark:bg-gray-800 shadow rounded p-6 space-y-4">
        {loading ? (
          <p className="text-slate-500 dark:text-slate-400">Loading sessions…</p>
        ) : sessions.length === 0 ? (
          <p className="text-slate-500 dark:text-slate-400">No active sessions found.</p>
        ) : (
          <>
            {currentSession && (
              <div className="mb-4">
                <h2 className="text-sm font-semibold text-green-600 dark:text-green-400 mb-2 uppercase tracking-wide">Current device</h2>
                <DeviceCard session={currentSession} isCurrent />
              </div>
            )}

            {otherSessions.length > 0 && (
              <div>
                <h2 className="text-sm font-semibold text-slate-500 dark:text-slate-400 mb-2 uppercase tracking-wide">
                  Other devices
                </h2>
                <div className="space-y-3">
                  {otherSessions.map((session) => (
                    <DeviceCard key={session.token} session={session} onRevoke={() => handleRevoke(session.token)} />
                  ))}
                </div>
              </div>
            )}
          </>
        )}
      </div>
    </div>
  );
};

const DeviceCard = ({ session, isCurrent = false, onRevoke }) => {
  return (
    <div className="flex items-center justify-between p-4 border rounded-lg dark:border-gray-700 bg-slate-50 dark:bg-gray-900">
      <div className="flex items-center gap-4">
        <div className="text-slate-600 dark:text-slate-300">{getDeviceIcon(session.deviceInfo)}</div>
        <div>
          <p className="font-medium dark:text-white">{session.deviceInfo || 'Unknown device'}</p>
          <p className="text-sm text-slate-500 dark:text-slate-400">IP: {session.ipAddress || 'unknown'}</p>
          <p className="text-xs text-slate-400 dark:text-slate-500 mt-1">
            Created: {formatDate(session.createdAt)} · Last used: {formatDate(session.lastUsedAt)}
          </p>
        </div>
      </div>
      <div className="flex items-center gap-3">
        {isCurrent && (
          <span className="text-xs font-semibold text-green-600 dark:text-green-400 bg-green-100 dark:bg-green-900/30 px-2 py-1 rounded">
            This device
          </span>
        )}
        {onRevoke && (
          <button
            onClick={onRevoke}
            title="Sign out this device"
            className="text-red-600 hover:text-red-800 dark:text-red-400 dark:hover:text-red-300 transition p-2"
          >
            <FaTimes />
          </button>
        )}
      </div>
    </div>
  );
};

export default Devices;
