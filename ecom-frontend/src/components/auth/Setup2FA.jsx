import { QRCodeSVG } from 'qrcode.react';
import { useState, useEffect } from 'react';
import { use2FA } from '../../hooks/use2FA';
import './Setup2FA.css';
const Setup2FA = ({ onClose, onSuccess }) => {
  const { enable2FA, verify2FA, loading, error, qrCode, setError } = use2FA();
  const [verificationCode, setVerificationCode] = useState('');
  const [step, setStep] = useState('qrcode'); // 'qrcode' or 'verify'
  const [successMessage, setSuccessMessage] = useState('');

  // Generate QR code on component mount
  useEffect(() => {
    generateQRCode();
  }, []);

  const generateQRCode = async () => {
    try {
      setSuccessMessage('');
      setError(null);
      await enable2FA();
      setStep('qrcode');
    } catch (err) {
      console.error('Error generating QR code:', err);
    }
  };

  const handleVerify = async (e) => {
    e.preventDefault();
    if (!verificationCode.trim() || verificationCode.length !== 6) {
      setError('Please enter a valid 6-digit code');
      return;
    }

    try {
      await verify2FA(parseInt(verificationCode));
      setSuccessMessage('✓ 2FA has been enabled successfully!');
      setVerificationCode('');
      
      // Call onSuccess callback after 1 second
      setTimeout(() => {
        if (onSuccess) onSuccess();
        if (onClose) onClose();
      }, 1500);
    } catch (err) {
      console.error('Verification error:', err);
    }
  };

  return (
    <div className="setup-2fa-container">
      <div className="setup-2fa-card">
        <div className="setup-2fa-header">
          <h2>Set Up Two-Factor Authentication</h2>
          <button className="close-btn" onClick={onClose} disabled={loading}>
            ✕
          </button>
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

        {step === 'qrcode' && (
          <div className="qrcode-section">
            <div className="step-indicator">
              <div className="step active">1</div>
              <div className="step">2</div>
            </div>

            <h3>Step 1: Scan the QR Code</h3>
            <p className="instruction-text">
              Scan this QR code with your authenticator app (Google Authenticator, 
              Microsoft Authenticator, Authy, etc.)
            </p>

            {qrCode ? (
              <div className="qrcode-display">
                  <QRCodeSVG 
            value={qrCode} 
            size={200}
            level="M"
              />
              </div>
            ) : (
              <div className="qrcode-loading">
                <div className="spinner"></div>
                <p>Generating QR code...</p>
              </div>
            )}

            <div className="backup-info">
              <p className="important-note">
                ⚠ Save this QR code in a safe place. You'll need it to recover your account 
                if you lose access to your authenticator app.
              </p>
            </div>

            <button
              className="btn btn-primary btn-block"
              onClick={() => setStep('verify')}
              disabled={loading || !qrCode}
            >
              Next: Verify Code
            </button>
          </div>
        )}

        {step === 'verify' && (
          <div className="verify-section">
            <div className="step-indicator">
              <div className="step completed">✓</div>
              <div className="step active">2</div>
            </div>

            <h3>Step 2: Enter Verification Code</h3>
            <p className="instruction-text">
              Enter the 6-digit code from your authenticator app to confirm setup.
            </p>

            <form onSubmit={handleVerify}>
              <div className="form-group">
                <label htmlFor="verificationCode" className="form-label">
                  6-Digit Code
                </label>
                <input
                  type="text"
                  id="verificationCode"
                  value={verificationCode}
                  onChange={(e) => {
                    const value = e.target.value.replace(/\D/g, '').slice(0, 6);
                    setVerificationCode(value);
                  }}
                  placeholder="000000"
                  maxLength="6"
                  className="form-control code-input"
                  disabled={loading}
                  autoFocus
                />
              </div>

              <button
                type="submit"
                className="btn btn-primary btn-block"
                disabled={loading || verificationCode.length !== 6}
              >
                {loading ? 'Verifying...' : 'Verify & Enable 2FA'}
              </button>

              <button
                type="button"
                className="btn btn-secondary btn-block"
                onClick={() => setStep('qrcode')}
                disabled={loading}
              >
                Back to QR Code
              </button>
            </form>
          </div>
        )}
      </div>
    </div>
  );
};

export default Setup2FA;
