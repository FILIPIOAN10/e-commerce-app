import { useState, useEffect, useCallback } from 'react';
import { use2FA } from '../../hooks/use2FA';
import Setup2FA from '../auth/Setup2FA';
import './TwoFactorSettings.css';

const TwoFactorSettings = () => {
  const { get2FAStatus, disable2FA, loading, error, is2FAEnabled, setError } = use2FA();
  const [showSetupModal, setShowSetupModal] = useState(false);
  const [statusLoading, setStatusLoading] = useState(true);
  const [successMessage, setSuccessMessage] = useState('');
  const [disableConfirm, setDisableConfirm] = useState(false);

  const fetchStatus = useCallback(async () => {
    try {
      setStatusLoading(true);
      setError(null);
      await get2FAStatus();
    } catch (err) {
      console.error('Error fetching 2FA status:', err);
    } finally {
      setStatusLoading(false);
    }
  }, [get2FAStatus, setError]);

  // Fetch 2FA status on component mount
  useEffect(() => {
    fetchStatus();
  }, [fetchStatus]);

  const handleDisable = async () => {
    try {
      await disable2FA();
      setSuccessMessage('✓ Two-Factor Authentication has been disabled');
      setDisableConfirm(false);
      await fetchStatus();
      
      // Clear success message after 3 seconds
      setTimeout(() => setSuccessMessage(''), 3000);
    } catch (err) {
      console.error('Error disabling 2FA:', err);
    }
  };

  const handleSetupSuccess = () => {
    setShowSetupModal(false);
    fetchStatus();
    setSuccessMessage('✓ Two-Factor Authentication has been enabled');
    setTimeout(() => setSuccessMessage(''), 3000);
  };

  return (
    <div className="two-factor-settings">
      <div className="settings-header">
        <div className="header-content">
          <h3>Two-Factor Authentication (2FA)</h3>
          <p className="header-description">
            Add an extra layer of security to your account
          </p>
        </div>
      </div>

      {error && (
        <div className="alert alert-error">
          <span className="alert-icon">⚠</span>
          {error}
        </div>
      )}

      {successMessage && (
        <div className="alert alert-success">
          <span className="alert-icon">✓</span>
          {successMessage}
        </div>
      )}

      {statusLoading ? (
        <div className="loading-state">
          <div className="spinner"></div>
          <p>Loading 2FA status...</p>
        </div>
      ) : (
        <>
          <div className="status-card">
            <div className="status-indicator">
              <div className={`status-badge ${is2FAEnabled ? 'enabled' : 'disabled'}`}>
                {is2FAEnabled ? '✓ Enabled' : '○ Disabled'}
              </div>
              <div className="status-text">
                <p className="status-label">Current Status</p>
                <p className="status-value">
                  {is2FAEnabled
                    ? 'Your account is protected with two-factor authentication'
                    : 'Your account is not protected with two-factor authentication'}
                </p>
              </div>
            </div>
          </div>

          <div className="features-list">
            <h4>Benefits of Two-Factor Authentication:</h4>
            <ul>
              <li>
                <span className="feature-icon">🔐</span>
                <span>Extra security layer beyond passwords</span>
              </li>
              <li>
                <span className="feature-icon">📱</span>
                <span>Works with Google Authenticator, Microsoft Authenticator, Authy</span>
              </li>
              <li>
                <span className="feature-icon">🛡️</span>
                <span>Protects against unauthorized account access</span>
              </li>
              <li>
                <span className="feature-icon">⚡</span>
                <span>Quick and easy to set up</span>
              </li>
            </ul>
          </div>

          {is2FAEnabled ? (
            <div className="action-section">
              <div className="enabled-info">
                <div className="info-icon">ℹ</div>
                <div className="info-content">
                  <p className="info-title">Two-Factor Authentication is Active</p>
                  <p className="info-text">
                    When you log in, you'll need to enter a code from your authenticator app 
                    in addition to your password.
                  </p>
                </div>
              </div>

              {!disableConfirm ? (
                <button
                  className="btn btn-danger"
                  onClick={() => setDisableConfirm(true)}
                  disabled={loading}
                >
                  Disable Two-Factor Authentication
                </button>
              ) : (
                <div className="confirm-section">
                  <p className="confirm-text">
                    ⚠ Are you sure you want to disable 2FA? Your account will be less secure.
                  </p>
                  <div className="button-group">
                    <button
                      className="btn btn-danger btn-danger-confirm"
                      onClick={handleDisable}
                      disabled={loading}
                    >
                      {loading ? 'Disabling...' : 'Yes, Disable 2FA'}
                    </button>
                    <button
                      className="btn btn-secondary"
                      onClick={() => setDisableConfirm(false)}
                      disabled={loading}
                    >
                      Keep 2FA Enabled
                    </button>
                  </div>
                </div>
              )}
            </div>
          ) : (
            <div className="action-section">
              <div className="setup-info">
                <div className="info-icon">🚀</div>
                <div className="info-content">
                  <p className="info-title">Get Started with 2FA</p>
                  <p className="info-text">
                    Set up two-factor authentication now to protect your account from unauthorized access.
                  </p>
                </div>
              </div>

              <button
                className="btn btn-primary"
                onClick={() => setShowSetupModal(true)}
                disabled={loading}
              >
                {loading ? 'Setting up...' : 'Enable Two-Factor Authentication'}
              </button>
            </div>
          )}

          <div className="info-box">
            <p className="info-box-title">📝 How it works:</p>
            <ol className="info-box-list">
              <li>Download an authenticator app (Google Authenticator, Microsoft Authenticator, Authy, etc.)</li>
              <li>Click the button to set up 2FA</li>
              <li>Scan the QR code with your authenticator app</li>
              <li>Enter the 6-digit code to verify</li>
              <li>You're all set! Use your authenticator app to log in</li>
            </ol>
          </div>
        </>
      )}

      {showSetupModal && (
        <Setup2FA
          onClose={() => setShowSetupModal(false)}
          onSuccess={handleSetupSuccess}
        />
      )}
    </div>
  );
};

export default TwoFactorSettings;
