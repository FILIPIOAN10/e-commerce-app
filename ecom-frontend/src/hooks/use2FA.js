import { useCallback, useState } from 'react';
import api from '../api/api';

export const use2FA = () => {
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);
  const [qrCode, setQrCode] = useState(null);
  const [is2FAEnabled, setIs2FAEnabled] = useState(false);

// Every function below is memoised. Callers put them in useCallback/useEffect
// dependency lists (TwoFactorSettings fetches the status on mount, Setup2FA
// generates the QR code on mount); a new function on every render re-ran
// those effects on every render - an endless stream of 2fa-status calls that
// kept the profile on "Loading", and a fresh TOTP secret per render that made
// the QR code impossible to verify. They only touch state setters, which React
// keeps stable, so the dependency lists are empty.

const get2FAStatus = useCallback(async () => {
    try {
        setLoading(true);
        const response = await api.get('/auth/user/2fa-status');
        setIs2FAEnabled(response.data.is2faEnabled);
        return response.data.is2faEnabled;
    } catch (err) {
        setError(err.response?.data?.message || 'Error fetching 2FA status');
        return false;
    } finally {
        setLoading(false);
    }
}, []);

const enable2FA = useCallback(async () => {
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
}, []);

const verify2FA = useCallback(async (code) => {
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
}, []);

const disable2FA = useCallback(async () => {
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
}, []);

const verify2FALogin = useCallback(async (code, jwtToken) => {
    try {
        setLoading(true);
        setError(null);
        const response = await api.post(
            '/auth/public/verify-2fa-login',
            { code: parseInt(code), jwtToken: jwtToken }
        );
        return response.data;
    } catch (err) {
        const errorMsg = err.response?.data?.message || 'Invalid 2FA code';
        setError(errorMsg);
        throw err;
    } finally {
        setLoading(false);
    }
}, []);
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
