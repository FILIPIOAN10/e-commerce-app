# 🚀 CUM SĂ FOLOSEȘTI 2FA - GHID PRACTIC

## PART 1: SETUP LA PRIMĂ DATĂ

### Step 1: Verifică dacă fișierele sunt în loc
```
✓ src/hooks/use2FA.js
✓ src/components/auth/Setup2FA.jsx
✓ src/components/auth/Verify2FALogin.jsx
✓ src/components/profile/TwoFactorSettings.jsx
```

### Step 2: Ca utilizator, unde găsești 2FA?

#### Locul 1: La LOGIN
```
/login → Intri username + password
      → Dacă 2FA este ENABLED pentru userul ăsta
      → Apare MODAL: "Enter your 6-digit code"
      → Intri codul din Authenticator
      → Success! Logat
```

#### Locul 2: În PROFILE
```
/profile → Mergi la sectiunea "Two-Factor Authentication"
        → Buton "Enable Two-Factor Authentication"
        → Apare MODAL cu QR Code
        → Scannezi QR cu Authenticator app
        → Intri codul de 6 cifre din app
        → Success! 2FA activat
```

---

## PART 2: FLUXURI PRACTICE

### SCENARIO 1: Utilizator fără 2FA LOGIN
```
User visits /login
     ↓
Intră: username="john", password="secret123"
     ↓
Click LOGIN button
     ↓
Backend validates → Success (no 2FA)
     ↓
App: localStorage.auth = { jwtToken, user }
     ↓
Auto redirect to / (Dashboard)
     ↓
✓ LOGAT NORMAL
```

---

### SCENARIO 2: Utilizator cu 2FA LOGIN
```
User visits /login
     ↓
Intră: username="john", password="secret123"
     ↓
Click LOGIN button
     ↓
Backend validates → Success BUT twoFactorEnabled=true
     ↓
Backend Response:
{
  "jwtToken": "eyJhbGc...",
  "needs2FA": true,           ← KEY!
  "temp2FAToken": "temp_xxx", ← TEMP TOKEN
  "user": { ... }
}
     ↓
Frontend: AHA! needs2FA is TRUE
     ↓
SHOW MODAL: Verify2FALogin
     ↓
User vede: "Enter your 6-digit code"
     ↓
User intră codul din Authenticator: "123456"
     ↓
Click VERIFY
     ↓
POST /public/verify-2fa-login?code=123456&jwtToken=temp_xxx
     ↓
Backend validates codul ✓
     ↓
Response: "2FA Verified"
     ↓
Frontend: Save full JWT token în localStorage
     ↓
navigate("/") → Dashboard
     ↓
✓ LOGAT CU 2FA
```

---

### SCENARIO 3: Setup 2FA (first time)
```
User in Profile → TwoFactorSettings component
     ↓
Status: "2FA Disabled"
     ↓
Click: "Enable Two-Factor Authentication"
     ↓
MODAL OPENS: Setup2FA
     ↓
Step 1 apare:
- Text: "Scan this QR code"
- QR CODE display
- Button: "Next: Verify Code"
     ↓
Backend call: POST /enable-2fa
Response: QR Code URL (otpauth://...)
     ↓
Frontend renders QR code
     ↓
User: Opens Authenticator app
        → Click "+"
        → "Scan QR Code"
        → Scans QR
        → Authenticator generates 6-digit codes
     ↓
User Click "Next: Verify Code"
     ↓
Step 2 apare:
- Text: "Enter verification code"
- Input field
- Button: "Verify & Enable 2FA"
     ↓
User intră codul din Authenticator: "123456"
     ↓
Click "Verify & Enable 2FA"
     ↓
POST /verify-2fa?code=123456
     ↓
Backend validates ✓
     ↓
Backend DB: SET twoFactorEnabled = true
     ↓
Frontend: Success message
     ↓
Modal closes auto
     ↓
TwoFactorSettings refreshes
     ↓
Status now: "2FA Enabled"
     ↓
✓ 2FA ACTIVE
```

---

### SCENARIO 4: Disable 2FA
```
User in Profile → TwoFactorSettings
     ↓
Status: "2FA Enabled"
     ↓
Click: "Disable Two-Factor Authentication"
     ↓
Confirmation dialog:
"Are you sure? Your account will be less secure."
     ↓
Click: "Yes, Disable 2FA"
     ↓
POST /disable-2fa
     ↓
Backend: SET twoFactorEnabled = false, twoFactorSecret = null
     ↓
Response: "2FA disabled successfully"
     ↓
Frontend: Success message
     ↓
TwoFactorSettings refreshes
     ↓
Status now: "2FA Disabled"
     ↓
✓ 2FA DISABLED (next login will NOT ask for code)
```

---

## PART 3: CODUL PRACTIC - UNDE ȘI CUM?

### Fișier 1: src/hooks/use2FA.js
**Ce conține:** Logica API calls
**Cum se folosește:**
```jsx
import { use2FA } from '../../hooks/use2FA';

function MyComponent() {
  const { 
    is2FAEnabled,
    enable2FA,
    verify2FA,
    disable2FA,
    loading,
    error
  } = use2FA();

  // Exemplu: Check status
  useEffect(() => {
    get2FAStatus();
  }, []);

  // Exemplu: Enable
  const handleEnable = async () => {
    try {
      const qrCodeUrl = await enable2FA();
      console.log("QR Code URL:", qrCodeUrl);
    } catch (err) {
      console.error(err);
    }
  };

  return (
    <button onClick={handleEnable} disabled={loading}>
      {loading ? 'Loading...' : 'Enable 2FA'}
    </button>
  );
}
```

---

### Fișier 2: src/components/auth/Setup2FA.jsx
**Ce conține:** Modal cu QR code
**Unde se folosește:** În LogIn component
**Cum se foloseșt:**
```jsx
import Setup2FA from "./Setup2FA";

function LogIn() {
  const [showSetupModal, setShowSetupModal] = useState(false);

  return (
    <div>
      {/* Login form... */}

      {/* Show modal when user clicks Enable 2FA */}
      {showSetupModal && (
        <Setup2FA
          onClose={() => setShowSetupModal(false)}
          onSuccess={() => {
            console.log("2FA setup complete!");
            setShowSetupModal(false);
          }}
        />
      )}
    </div>
  );
}
```

**Props explicație:**
- `onClose`: Se apelează când user click X (close button)
- `onSuccess`: Se apelează după verify code success

---

### Fișier 3: src/components/auth/Verify2FALogin.jsx
**Ce conține:** Modal pentru verify la login
**Unde se folosește:** În LogIn component (automatic la login)
**Cum se folosește:**
```jsx
import Verify2FALogin from "./Verify2FALogin";

function LogIn() {
  const [needs2FA, setNeeds2FA] = useState(false);
  const [temp2FAToken, setTemp2FAToken] = useState(null);
  const [loginEmail, setLoginEmail] = useState(null);

  const handle2FASuccess = async (token) => {
    // Token is verified JWT
    localStorage.setItem("auth", JSON.stringify({
      jwtToken: token,
      user: { email: loginEmail }
    }));
    navigate("/");
  };

  return (
    <div>
      {/* Login form... */}

      {/* Show modal when 2FA is required */}
      {needs2FA && temp2FAToken && (
        <Verify2FALogin
          jwtToken={temp2FAToken}
          email={loginEmail}
          onVerifySuccess={handle2FASuccess}
          onCancel={() => {
            setNeeds2FA(false);
            setTemp2FAToken(null);
          }}
        />
      )}
    </div>
  );
}
```

**Props explicație:**
- `jwtToken`: Temporary token din login response
- `email`: User's email (for display)
- `onVerifySuccess`: Callback când code is verified
- `onCancel`: Callback la back/cancel

---

### Fișier 4: src/components/profile/TwoFactorSettings.jsx
**Ce conține:** Componenta settings în profil
**Unde se folosește:** În Profile component
**Cum se folosește:**
```jsx
import TwoFactorSettings from './TwoFactorSettings';

function Profile() {
  const { user } = useSelector(state => state.auth);

  return (
    <div className="profile-container">
      <h1>My Profile</h1>
      {/* User info... */}

      {/* 2FA Settings Component */}
      <TwoFactorSettings />
    </div>
  );
}
```

**Props:** NONE - Se folosește din Redux și hook!

---

## PART 4: INTEGRARE REDUX ACTION

### File: src/store/actions/index.js
**Modificare la authenticateSignInUser:**

```jsx
export const authenticateSignInUser = 
  (sendData, toast, reset, navigate, setLoader, fetchHint,
   setNeeds2FA,        // NEW - function to show 2FA modal
   setTemp2FAToken,    // NEW - function to save temp token
   setLoginEmail       // NEW - function to save email
  ) => async (dispatch) => {
    
    try {
      setLoader(true);
      const loginData = {
        ...sendData,
        username: String(sendData.username || "").trim(),
      };
      
      // 1. LOGIN API CALL
      const { data } = await api.post("/auth/signin", loginData);
      
      // 2. CHECK IF 2FA NEEDED
      if (data.needs2FA && data.temp2FAToken) {
        // 2FA Required!
        setNeeds2FA(true);           // Show modal
        setTemp2FAToken(data.temp2FAToken); // Save temp token
        setLoginEmail(sendData.username);   // Save email
        toast.success("Please verify with your authenticator");
        return; // Don't complete login yet
      }
      
      // 3. STANDARD LOGIN (no 2FA)
      dispatch({ type: "LOGIN_USER", payload: data });
      localStorage.setItem("auth", JSON.stringify(data));
      reset();
      toast.success("Login Success");
      navigate("/");
      
    } catch (error) {
      toast.error(error?.response?.data?.message || "Login failed");
    } finally {
      setLoader(false);
    }
  };
```

---

## PART 5: FULL EXAMPLE - LOGIN COMPONENT

```jsx
// src/components/auth/LogIn.jsx
import { useState } from "react";
import { useForm } from "react-hook-form";
import { useNavigate } from "react-router-dom";
import { useDispatch } from "react-redux";
import { authenticateSignInUser } from "../../store/actions";
import Verify2FALogin from "./Verify2FALogin";
import toast from "react-hot-toast";

const LogIn = () => {
  const navigate = useNavigate();
  const dispatch = useDispatch();
  const [loader, setLoader] = useState(false);
  
  // NEW: 2FA States
  const [needs2FA, setNeeds2FA] = useState(false);
  const [temp2FAToken, setTemp2FAToken] = useState(null);
  const [loginEmail, setLoginEmail] = useState(null);

  const { register, handleSubmit, reset, formState: { errors } } = useForm();

  const loginHandler = async (data) => {
    // Dispatch with NEW 2FA parameters
    dispatch(authenticateSignInUser(
      data, 
      toast, 
      reset, 
      navigate, 
      setLoader,
      null, // fetchHint
      setNeeds2FA,      // NEW
      setTemp2FAToken,  // NEW
      setLoginEmail     // NEW
    ));
  };

  // NEW: Handle 2FA success
  const handle2FASuccess = async (token) => {
    try {
      localStorage.setItem("auth", JSON.stringify({
        jwtToken: token,
        user: { email: loginEmail }
      }));
      dispatch({ 
        type: "LOGIN_USER", 
        payload: { jwtToken: token, user: { email: loginEmail } } 
      });
      toast.success("Login Success");
      setNeeds2FA(false);
      setTemp2FAToken(null);
      setLoginEmail(null);
      reset();
      navigate("/");
    } catch (error) {
      toast.error("Error completing login");
    }
  };

  return (
    <div className="login-container">
      <form onSubmit={handleSubmit(loginHandler)}>
        <h1>Login</h1>
        
        {/* Username Input */}
        <input
          type="text"
          placeholder="Username"
          {...register("username", { required: true })}
        />
        
        {/* Password Input */}
        <input
          type="password"
          placeholder="Password"
          {...register("password", { required: true })}
        />
        
        {/* Login Button */}
        <button type="submit" disabled={loader}>
          {loader ? "Loading..." : "Login"}
        </button>
      </form>

      {/* NEW: 2FA Verification Modal */}
      {needs2FA && temp2FAToken && (
        <Verify2FALogin
          jwtToken={temp2FAToken}
          email={loginEmail}
          onVerifySuccess={handle2FASuccess}
          onCancel={() => {
            setNeeds2FA(false);
            setTemp2FAToken(null);
            setLoginEmail(null);
          }}
        />
      )}
    </div>
  );
};

export default LogIn;
```

---

## PART 6: FULL EXAMPLE - PROFILE COMPONENT

```jsx
// src/components/profile/Profile.jsx
import { useSelector } from 'react-redux';
import TwoFactorSettings from './TwoFactorSettings';

const Profile = () => {
  const { user } = useSelector((state) => state.auth);

  return (
    <div className="profile-container">
      <div className="profile-header">
        <h1>My Profile</h1>
      </div>

      {/* User Info Section */}
      <div className="profile-section">
        <h2>Personal Information</h2>
        <p><strong>Username:</strong> {user?.username}</p>
        <p><strong>Email:</strong> {user?.email}</p>
        <p><strong>Role:</strong> {user?.roles?.[0] || 'User'}</p>
      </div>

      {/* NEW: 2FA Settings Section */}
      <div className="profile-section">
        <TwoFactorSettings />
      </div>
    </div>
  );
};

export default Profile;
```

---

## PART 7: STEP BY STEP - USER JOURNEY

### 🟢 User fără 2FA - Normal Flow
```
1. User accesează /login
2. Intră username + password
3. Click "Login"
4. ✓ LOGAT - Merge la /
```

### 🟡 User activează 2FA - First Time
```
1. User accesează /profile
2. Scorolează la "Two-Factor Authentication"
3. Status: "Disabled"
4. Click: "Enable Two-Factor Authentication"
   → Setup2FA MODAL opens
5. Vede QR Code
6. Deschide Authenticator app
7. Click "+" → "Scan QR Code"
8. Scaneaza QR
9. Authenticator generează cod: "123456"
10. Intră codul în modal
11. Click "Verify & Enable 2FA"
    → POST /verify-2fa?code=123456
12. ✓ Backend sets twoFactorEnabled = true
13. ✓ Modal closes
14. Status now: "Enabled"
```

### 🔵 User cu 2FA - Login Flow
```
1. User accesează /login
2. Intră username + password
3. Click "Login"
   → POST /auth/signin
4. Backend vede: twoFactorEnabled = true
5. Backend Response:
   {
     "needs2FA": true,
     "temp2FAToken": "...",
     "jwtToken": "..."
   }
6. Frontend: AHA! needs2FA = true
7. Verify2FALogin MODAL apare
8. Email shown: "john@example.com"
9. User checks Authenticator
10. Current code: "654321"
11. Intră codul: "654321"
12. Click "Verify & Login"
    → POST /public/verify-2fa-login?code=654321&jwtToken=...
13. Backend validates code ✓
14. Response: "2FA Verified"
15. Frontend saves jwtToken în localStorage
16. navigate("/") → Dashboard
17. ✓ LOGAT CU 2FA
```

---

## PART 8: DEBUGGING - CUM SĂ VERIFIC CĂ MERGE?

### Check 1: In Browser Console
```javascript
// 1. Check localStorage
console.log(JSON.parse(localStorage.auth));
// Should show: { jwtToken: "...", user: {...} }

// 2. Check Redux state
// Open Redux DevTools
// Look for "LOGIN_USER" action
// Check auth reducer state

// 3. Check network requests
// DevTools → Network tab
// Look for: /auth/signin, /enable-2fa, /verify-2fa, /public/verify-2fa-login
```

### Check 2: Test 2FA Setup
```
1. Go to Profile
2. Click "Enable 2FA"
3. Check Network tab:
   - Should see POST /enable-2fa
   - Response should have QR code URL
   - URL should start: "otpauth://totp/..."
4. QR code should display in modal
```

### Check 3: Test 2FA Login
```
1. Enable 2FA for a test account
2. Logout
3. Try to login
4. Should see Verify2FALogin modal
5. Check Network tab:
   - POST /auth/signin → Response has needs2FA: true
   - POST /public/verify-2fa-login → Response "2FA Verified"
5. Should navigate to dashboard
```

---

## ⚠️ COMMON MISTAKES

### ❌ Mistake 1: QR code nu apare
**Problem:** Backend nu returnează valid QR URL
**Fix:** Check `/enable-2fa` endpoint returnează string, not HTML

### ❌ Mistake 2: Modal nu apare la login
**Problem:** `setNeeds2FA` callback nu e pasată
**Fix:** Verify dispatch call include `setNeeds2FA, setTemp2FAToken, setLoginEmail`

### ❌ Mistake 3: Codul e invalid
**Problem:** Time sync issue
**Fix:** Verific timestamp pe server și device sunt sincronizate

### ❌ Mistake 4: User nu poate scana QR
**Problem:** Authenticator app nu open
**Fix:** User trebuie să instaleze: Google Authenticator / Microsoft Authenticator / Authy

---

## ✅ QUICK CHECKLIST

- [ ] Fișierele sunt copiate corect?
- [ ] Imports sunt corecte în componente?
- [ ] Redux action is updated?
- [ ] LogIn component are state pt 2FA?
- [ ] Profile component importă TwoFactorSettings?
- [ ] Backend endpoints implementate?
- [ ] Poti login normal (fără 2FA)?
- [ ] Poti enable 2FA din profil?
- [ ] Poti verifica cod la login?
- [ ] Status in localStorage se saves?

**All done! 🚀**
