import { useState } from 'react';
import { use2FA } from '../../hooks/use2FA';
import './Verify2FALogin.css';

const Verify2FALogin = ({ jwtToken, onVerifySuccess, onCancel, email }) => {
  const { verify2FALogin, loading, error, setError } = use2FA();
  const [verificationCode, setVerificationCode] = useState('');
  const [successMessage, setSuccessMessage] = useState('');

const handleSubmit = async (e) => {
    e.preventDefault();
    setSuccessMessage('');

    if (!verificationCode.trim() || verificationCode.length !== 6) {
      setError('Please enter a valid 6-digit code');
      return;
    }

    try {
      const fullAuthData = await verify2FALogin(parseInt(verificationCode), jwtToken);
      setSuccessMessage('✓ 2FA verified successfully! Logging in...');
      
      setTimeout(() => {
        if (onVerifySuccess) onVerifySuccess(fullAuthData); // <- obiectul complet
      }, 1500);
    } catch (err) {
      console.error('2FA verification error:', err);
    }
};

  return (
    <div className="verify-2fa-login-container">
      <div className="verify-2fa-login-card">
        <div className="verify-2fa-header">
          <h2>Two-Factor Authentication</h2>
        </div>

        {error && (
          <div className="alert alert-error">
            <span className="alert-icon">⚠</span>
            <div>
              <p className="alert-title">Invalid Code</p>
              <p className="alert-message">{error}</p>
            </div>
          </div>
        )}

        {successMessage && (
          <div className="alert alert-success">
            <span className="alert-icon">✓</span>
            {successMessage}
          </div>
        )}

        <div className="verify-2fa-content">
          <div className="email-info">
            <p className="email-label">Verifying for:</p>
            <p className="email-value">{email}</p>
          </div>

          <p className="instruction-text">
            Enter the 6-digit code from your authenticator app to complete login.
          </p>

          <form onSubmit={handleSubmit}>
            <div className="form-group">
              <label htmlFor="twoFACode" className="form-label">
                Authentication Code
              </label>
              <input
                type="text"
                id="twoFACode"
                value={verificationCode}
                onChange={(e) => {
                  const value = e.target.value.replace(/\D/g, '').slice(0, 6);
                  setVerificationCode(value);
                  if (error) setError(null);
                }}
                placeholder="000000"
                maxLength="6"
                className="form-control code-input"
                disabled={loading}
                autoFocus
                required
              />
              <p className="code-hint">Enter 6 digits</p>
            </div>

            <button
              type="submit"
              className="btn btn-primary btn-block"
              disabled={loading || verificationCode.length !== 6}
            >
              {loading ? (
                <>
                  <span className="spinner-small"></span>
                  Verifying...
                </>
              ) : (
                'Verify & Login'
              )}
            </button>

            <button
              type="button"
              className="btn btn-secondary btn-block"
              onClick={onCancel}
              disabled={loading}
            >
              Cancel
            </button>
          </form>

          <div className="security-info">
            <p className="security-icon">🔒</p>
            <p className="security-text">
              Your account is protected by two-factor authentication. 
              Only you can access your account with both your password and authenticator code.
            </p>
          </div>
        </div>
      </div>
    </div>
  );
};

export default Verify2FALogin;
