# Two-Factor Authentication (2FA) Frontend Implementation

## Overview
Implementare completă a autentificării cu doi factori pe frontend-ul React. Include setup 2FA, verificare la login, și management în profil.

## Componente Implementate

### 1. **Hook: `use2FA.js`** 
**Locație:** `src/hooks/use2FA.js`

Hook custom care gestionează toată logica 2FA. Include:
- `get2FAStatus()` - Verifică dacă 2FA este activat
- `enable2FA()` - Generează secret și QR code
- `verify2FA(code)` - Verifică codul și activează 2FA
- `disable2FA()` - Dezactivează 2FA
- `verify2FALogin(code, jwtToken)` - Verifică codul la login

**Utilizare:**
```jsx
const { loading, error, verify2FA, is2FAEnabled } = use2FA();
```

---

### 2. **Componentă: `Setup2FA.jsx`**
**Locație:** `src/components/auth/Setup2FA.jsx`

Modal pentru setarea inițială a 2FA. Afișează:
- QR code de scanat cu aplicația Authenticator
- Input pentru codul de verificare 6 cifre
- Mesaje de succes/eroare

**Props:**
- `onClose` - Callback când se închide modalul
- `onSuccess` - Callback după verificare cu succes

**CSS:** `Setup2FA.css`

---

### 3. **Componentă: `Verify2FALogin.jsx`**
**Locație:** `src/components/auth/Verify2FALogin.jsx`

Modal pentru verificarea 2FA la login. Afișează:
- Email-ul utilizatorului
- Input pentru codul de verificare
- Status icon și mesaje de securitate

**Props:**
- `jwtToken` - Token temporar pentru 2FA
- `email` - Email-ul utilizatorului
- `onVerifySuccess` - Callback după verificare cu succes
- `onCancel` - Callback la anulare

**CSS:** `Verify2FALogin.css`

---

### 4. **Componentă: `TwoFactorSettings.jsx`**
**Locație:** `src/components/profile/TwoFactorSettings.jsx`

Componentă de management 2FA în profil. Include:
- Status indicator (enabled/disabled)
- Opțiuni enable/disable
- Beneficii și instrucțiuni
- Confirmare la dezactivare

**CSS:** `TwoFactorSettings.css`

---

## Fluxul de Autentificare cu 2FA

### 1. **Login Normal**
```
User → LogIn Form → authenticateSignInUser Action
                          ↓
                   API /auth/signin
                          ↓
                   Răspuns cu needs2FA: true?
                   ├─ Nu → Standard login → Dashboard
                   └─ Da → Show Verify2FALogin Modal
```

### 2. **Verificare 2FA**
```
Verify2FALogin Modal → Input cod → use2FA.verify2FALogin()
                            ↓
                   API /public/verify-2fa-login
                            ↓
                   Succes → Complete Login → Dashboard
                   Eroare → Affichează mesaj
```

### 3. **Setup 2FA**
```
Profile → TwoFactorSettings → Enable Button → Setup2FA Modal
                                   ↓
                          use2FA.enable2FA()
                                   ↓
                          Afișează QR Code
                                   ↓
                        User scaneaza QR
                                   ↓
                        User introdu cod
                                   ↓
                   use2FA.verify2FA(code)
                                   ↓
                          Succes → 2FA Enabled
```

---

## Integrare în Componentele Existente

### LogIn.jsx - Changes
```jsx
// Import Verify2FALogin
import Verify2FALogin from "./Verify2FALogin";

// State pentru 2FA
const [needs2FA, setNeeds2FA] = useState(false);
const [temp2FAToken, setTemp2FAToken] = useState(null);
const [loginEmail, setLoginEmail] = useState(null);

// Dispatch cu parametrii 2FA
dispatch(authenticateSignInUser(
    data, toast, reset, navigate, setLoader, fetchHint,
    setNeeds2FA,        // NEW
    setTemp2FAToken,    // NEW
    setLoginEmail       // NEW
));

// Handle 2FA success
const handle2FASuccess = async (token) => {
    // Complete login...
};

// Render modal
{needs2FA && temp2FAToken && <Verify2FALogin ... />}
```

### Profile.jsx - Changes
```jsx
import TwoFactorSettings from './TwoFactorSettings';

// În JSX
<TwoFactorSettings />
```

### Action: store/actions/index.js
```jsx
export const authenticateSignInUser = (
    sendData, toast, reset, navigate, setLoader, fetchHint,
    setNeeds2FA, setTemp2FAToken, setLoginEmail  // NEW
) => async (dispatch) => {
    // ... login logic
    if (data.needs2FA && data.temp2FAToken) {
        setNeeds2FA(true);
        setTemp2FAToken(data.temp2FAToken);
        setLoginEmail(sendData.username);
        return;
    }
    // ... standard login
};
```

---

## Fluxuri de Date

### State Management
```
LogIn.jsx State:
├─ needs2FA (boolean)
├─ temp2FAToken (string)
└─ loginEmail (string)

use2FA Hook State:
├─ loading (boolean)
├─ error (string)
├─ qrCode (string - URL)
└─ is2FAEnabled (boolean)
```

### LocalStorage
```javascript
// Auth data cu 2FA
localStorage.auth = {
    jwtToken: "...",
    user: { email: "...", roles: [...] }
}
```

---

## API Endpoints Așteptate (Backend)

```
POST /auth/signin
Response (dacă 2FA activat):
{
    needs2FA: true,
    temp2FAToken: "eyJhbGc..."
}

POST /enable-2fa
Response:
"data:image/png;base64,..." (QR code)

POST /verify-2fa?code=123456
Response:
"2FA enabled successfully"

POST /disable-2fa
Response:
"2FA disabled successfully"

POST /user/2fa-status
Response:
{ is2faEnabled: true }

POST /public/verify-2fa-login?code=123456&jwtToken=...
Response:
"2FA Verified"
```

---

## Pasul 1: Instalare Aplicații Authenticator

Utilizatorul trebuie să instaleze una din aceste app-uri:
- **Google Authenticator** (iOS/Android)
- **Microsoft Authenticator** (iOS/Android)
- **Authy** (iOS/Android/Desktop)
- **FreeOTP** (iOS/Android)

---

## Pasul 2: Activare 2FA

1. Utilizatorul intră în Profile
2. Click "Enable Two-Factor Authentication"
3. Se deschide Setup2FA modal
4. Utilizatorul scanează QR code cu Authenticator
5. Intră codul de 6 cifre din app
6. 2FA este activat ✓

---

## Pasul 3: Login cu 2FA

1. Utilizatorul intră username + password
2. Backend verifică și returnează `needs2FA: true`
3. Se afișează Verify2FALogin modal
4. Utilizatorul intră codul din Authenticator
5. Backend verifică codul și returnează token final
6. Utilizatorul este logat ✓

---

## Siguranța și Best Practices

✅ **QR Code Storage**
- Utilizatorul trebuie să salveze QR code în loc sigur
- Dacă pierde accesul la Authenticator, va folosi QR code pentru a recupera

✅ **Backup Codes** (Opțional - Backend)
- Backend ar putea genera coduri de backup
- Utilizatorul le salvează în loc sigur
- Pot fi folosite dacă pierde Authenticator

✅ **TOTP Security**
- 6-digit codes care expiră în 30 de secunde
- Sincronizarea între client și server este critică

✅ **Temporary Token**
- Valabil doar pentru o singură verificare 2FA
- Expiră rapid pentru siguranță

---

## Testare

### Test Case 1: Setup 2FA
```
1. Acces Profile
2. Click "Enable Two-Factor Authentication"
3. Scan QR code cu Authenticator (sau simulator)
4. Enter 6-digit code
5. Verify 2FA este "Enabled"
```

### Test Case 2: Login cu 2FA
```
1. Logout
2. Login cu username + password
3. Modal "Two-Factor Authentication" apare
4. Enter 6-digit code din Authenticator
5. Succes → Dashboard
6. Incorect code → Error message
```

### Test Case 3: Disable 2FA
```
1. Acces Profile
2. Click "Disable Two-Factor Authentication"
3. Confirma
4. Verify 2FA este "Disabled"
```

---

## Troubleshooting

### ❌ "Invalid 2FA code"
- Verific dacă codul din Authenticator este corect
- Verific sincronizarea de timp între device și server
- QR code-ul a fost scanat corect?

### ❌ "2FA status not loading"
- Check API endpoint `/user/2fa-status`
- Verify authentication token este valid
- Check Redux auth state

### ❌ Modal nu apare la login
- Verify backend returnează `needs2FA: true`
- Check `setNeeds2FA` și `setTemp2FAToken` sunt pasate corect
- Verify `Verify2FALogin` componenta este importată

### ❌ Authenticator nu poate scana QR
- Backend generează QR URL corect?
- Verific `getQrCodeUrl` din backend
- Incercă manual intrarea cheii (secret key)

---

## Extensii Viitoare

1. **Backup Codes** - Coduri de recuperare
2. **SMS 2FA** - Cod prin SMS
3. **Email 2FA** - Cod prin email
4. **Remember Device** - Opțiune "nu mai cere 2FA pe device"
5. **Force 2FA** - Pentru utilizatori admin
6. **2FA Logs** - History de login-uri cu 2FA

---

## Fișiere Componente

```
src/
├── hooks/
│   └── use2FA.js ✓
├── components/
│   ├── auth/
│   │   ├── LogIn.jsx (UPDATED) ✓
│   │   ├── Setup2FA.jsx ✓
│   │   ├── Setup2FA.css ✓
│   │   ├── Verify2FALogin.jsx ✓
│   │   └── Verify2FALogin.css ✓
│   └── profile/
│       ├── Profile.jsx (UPDATED) ✓
│       ├── TwoFactorSettings.jsx ✓
│       └── TwoFactorSettings.css ✓
└── store/
    └── actions/
        └── index.js (UPDATED) ✓
```

---

## Contact & Support

Pentru ajutor cu integrarea 2FA în backend, consultă backend developer.
