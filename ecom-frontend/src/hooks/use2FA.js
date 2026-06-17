import { useState } from 'react';
import api from '../api/api';

export const use2FA = () => {
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);
  const [qrCode, setQrCode] = useState(null);
  const [is2FAEnabled, setIs2FAEnabled] = useState(false);

const get2FAStatus = async () => {
    try {
        setLoading(true);
        const response = await api.post('/auth/user/2fa-status'); // <- fix
        setIs2FAEnabled(response.data.is2faEnabled);
        return response.data.is2faEnabled;
    } catch (err) {
        setError(err.response?.data?.message || 'Error fetching 2FA status');
        return false;
    } finally {
        setLoading(false);
    }
};

const enable2FA = async () => {
    try {
        setLoading(true);
        setError(null);
        const response = await api.post('/auth/enable-2fa'); // <- fix
        setQrCode(response.data);
        return response.data;
    } catch (err) {
        const errorMsg = err.response?.data?.message || 'Error enabling 2FA';
        setError(errorMsg);
        throw err;
    } finally {
        setLoading(false);
    }
};

const verify2FA = async (code) => {
    try {
        setLoading(true);
        setError(null);
        const response = await api.post(`/auth/verify-2fa?code=${code}`); // <- fix
        setIs2FAEnabled(true);
        setQrCode(null);
        return response.data;
    } catch (err) {
        const errorMsg = err.response?.data || 'Invalid 2FA code';
        setError(errorMsg);
        throw err;
    } finally {
        setLoading(false);
    }
};

const disable2FA = async () => {
    try {
        setLoading(true);
        setError(null);
        const response = await api.post('/auth/disable-2fa'); // <- fix
        setIs2FAEnabled(false);
        return response.data;
    } catch (err) {
        const errorMsg = err.response?.data?.message || 'Error disabling 2FA';
        setError(errorMsg);
        throw err;
    } finally {
        setLoading(false);
    }
};

const verify2FALogin = async (code, jwtToken) => {
    try {
        setLoading(true);
        setError(null);
        const response = await api.post(
            `/auth/public/verify-2fa-login?code=${code}&jwtToken=${jwtToken}` // <- fix
        );
        return response.data;
    } catch (err) {
        const errorMsg = err.response?.data || 'Invalid 2FA code';
        setError(errorMsg);
        throw err;
    } finally {
        setLoading(false);
    }
};
  return {
    loading,
    error,
    qrCode,
    is2FAEnabled,
    get2FAStatus,
    enable2FA,
    verify2FA,
    disable2FA,
    verify2FALogin,
    setError,
    setQrCode,
  };
};
