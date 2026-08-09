# 2FA API Documentation & Examples

## Backend Expectations

Acest document descrie ce răspunsuri sunt așteptate de frontend din backend.

---

## 1. LOGIN ENDPOINT: `/auth/signin`

### Request
```json
POST /auth/signin
Content-Type: application/json

{
    "username": "john_doe",
    "password": "securePassword123"
}
```

### Response - WITHOUT 2FA (User Status: 2FA Disabled)
```json
HTTP 200 OK
Content-Type: application/json

{
    "jwtToken": "eyJhbGciOiJIUzUxMiJ9.eyJzdWIiOiJqb2huX2RvZSIsImlhdCI6MTcxODU0...",
    "user": {
        "userId": 1,
        "username": "john_doe",
        "email": "john@example.com",
        "roles": ["ROLE_USER"],
        "twoFactorEnabled": false
    }
}
```

### Response - WITH 2FA (User Status: 2FA Enabled)
```json
HTTP 200 OK
Content-Type: application/json

{
    "jwtToken": "eyJhbGciOiJIUzUxMiJ9.eyJzdWIiOiJqb2huX2RvZSIsImlhdCI6MTcxODU0...",
    "needs2FA": true,
    "temp2FAToken": "eyJhbGciOiJIUzUxMiJ9.eyJzdWIiOiJqb2huX2RvZSIsInR5cGUiOiIyZmFfdGVtcCIs...",
    "user": {
        "userId": 1,
        "username": "john_doe",
        "email": "john@example.com",
        "roles": ["ROLE_USER"],
        "twoFactorEnabled": true
    }
}
```

### Error Response
```json
HTTP 401 UNAUTHORIZED

{
    "message": "Invalid username or password",
    "timestamp": "2024-06-16T10:30:00"
}
```

---

## 2. ENABLE 2FA ENDPOINT: `/enable-2fa`

### Request
```
POST /enable-2fa
Authorization: Bearer <jwtToken>
Content-Type: application/json
```

### Response
```json
HTTP 200 OK
Content-Type: application/json

"otpauth://totp/E-commerce%20Application:john_doe@example.com?secret=JBSWY3DPEBLW64TMMQ======&issuer=E-commerce%20Application"
```

**Notă:** Aceasta este URL-ul QR Code care va fi afișat ca QR code în UI.

### Error Response
```json
HTTP 401 UNAUTHORIZED

{
    "message": "Unauthorized"
}
```

---

## 3. VERIFY 2FA ENDPOINT: `/verify-2fa`

### Request
```
POST /verify-2fa?code=123456
Authorization: Bearer <jwtToken>
Content-Type: application/json
```

### Response - SUCCESS
```json
HTTP 200 OK

"2FA enabled successfully"
```

### Response - ERROR (Invalid Code)
```json
HTTP 400 BAD_REQUEST

"Invalid 2FA code"
```

### Response - ERROR (Unauthorized)
```json
HTTP 401 UNAUTHORIZED

{
    "message": "Unauthorized"
}
```

---

## 4. GET 2FA STATUS ENDPOINT: `/user/2fa-status`

### Request
```
POST /user/2fa-status
Authorization: Bearer <jwtToken>
Content-Type: application/json
```

### Response - 2FA ENABLED
```json
HTTP 200 OK

{
    "is2faEnabled": true
}
```

### Response - 2FA DISABLED
```json
HTTP 200 OK

{
    "is2faEnabled": false
}
```

### Response - USER NOT FOUND
```json
HTTP 404 NOT_FOUND

"User not found"
```

---

## 5. VERIFY 2FA LOGIN ENDPOINT: `/public/verify-2fa-login`

### Request
```
POST /public/verify-2fa-login?code=123456&jwtToken=eyJhbGc...
Content-Type: application/json
```

**Parametri Query:**
- `code` - 6-digit TOTP code din Authenticator
- `jwtToken` - Temporary 2FA token din login response

### Response - SUCCESS
```json
HTTP 200 OK

"2FA Verified"
```

**Notă:** Frontend trebuie să use temporary token-ul din request pentru a completa login.

### Response - ERROR (Invalid Code)
```json
HTTP 401 UNAUTHORIZED

"Invalid 2FA Code"
```

---

## 6. DISABLE 2FA ENDPOINT: `/disable-2fa`

### Request
```
POST /disable-2fa
Authorization: Bearer <jwtToken>
Content-Type: application/json
```

### Response - SUCCESS
```json
HTTP 200 OK

"2FA disabled successfully"
```

### Response - ERROR
```json
HTTP 401 UNAUTHORIZED

{
    "message": "Unauthorized"
}
```

---

## Testing cu cURL

### Test 1: Login WITHOUT 2FA
```bash
curl -X POST http://localhost:8080/auth/signin \
  -H "Content-Type: application/json" \
  -d '{
    "username": "user_without_2fa",
    "password": "password123"
  }'
```

### Test 2: Login WITH 2FA
```bash
curl -X POST http://localhost:8080/auth/signin \
  -H "Content-Type: application/json" \
  -d '{
    "username": "user_with_2fa",
    "password": "password123"
  }'

# Response va include "needs2FA": true
```

### Test 3: Enable 2FA
```bash
curl -X POST http://localhost:8080/enable-2fa \
  -H "Authorization: Bearer <JWT_TOKEN>" \
  -H "Content-Type: application/json"
```

### Test 4: Verify 2FA Code (after setup)
```bash
curl -X POST "http://localhost:8080/verify-2fa?code=123456" \
  -H "Authorization: Bearer <JWT_TOKEN>" \
  -H "Content-Type: application/json"
```

### Test 5: Get 2FA Status
```bash
curl -X POST http://localhost:8080/user/2fa-status \
  -H "Authorization: Bearer <JWT_TOKEN>" \
  -H "Content-Type: application/json"
```

### Test 6: Verify 2FA at Login
```bash
curl -X POST "http://localhost:8080/public/verify-2fa-login?code=123456&jwtToken=<TEMP_2FA_TOKEN>" \
  -H "Content-Type: application/json"
```

### Test 7: Disable 2FA
```bash
curl -X POST http://localhost:8080/disable-2fa \
  -H "Authorization: Bearer <JWT_TOKEN>" \
  -H "Content-Type: application/json"
```

---

## Testing cu Postman

### Collection Setup

1. **Create Variable:** `baseUrl` = `http://localhost:8080`
2. **Create Variable:** `jwtToken` = (salvezi token din login)
3. **Create Variable:** `temp2FAToken` = (salvezi temp token din login cu 2FA)

### Requests

#### Request 1: Login (2FA Disabled)
```
POST {{baseUrl}}/auth/signin
Body (JSON):
{
    "username": "user_without_2fa",
    "password": "password123"
}

Script (Post-response):
pm.globals.set("jwtToken", pm.response.json().jwtToken);
```

#### Request 2: Login (2FA Enabled)
```
POST {{baseUrl}}/auth/signin
Body (JSON):
{
    "username": "user_with_2fa",
    "password": "password123"
}

Script (Post-response):
pm.globals.set("temp2FAToken", pm.response.json().temp2FAToken);
pm.test("Should return needs2FA: true", function() {
    pm.expect(pm.response.json().needs2FA).to.equal(true);
});
```

#### Request 3: Enable 2FA
```
POST {{baseUrl}}/enable-2fa
Headers:
Authorization: Bearer {{jwtToken}}

Script (Post-response):
pm.globals.set("qrCodeUrl", pm.response.json());
console.log("QR Code URL:", pm.globals.get("qrCodeUrl"));
```

#### Request 4: Verify 2FA Code
```
POST {{baseUrl}}/verify-2fa?code=123456
Headers:
Authorization: Bearer {{jwtToken}}

Note: Înlocuiește 123456 cu codul real din Authenticator
```

#### Request 5: Get 2FA Status
```
POST {{baseUrl}}/user/2fa-status
Headers:
Authorization: Bearer {{jwtToken}}
```

#### Request 6: Verify 2FA at Login
```
POST {{baseUrl}}/public/verify-2fa-login?code=123456&jwtToken={{temp2FAToken}}

Note: Înlocuiește 123456 cu codul real din Authenticator
```

#### Request 7: Disable 2FA
```
POST {{baseUrl}}/disable-2fa
Headers:
Authorization: Bearer {{jwtToken}}
```

---

## Frontend Expected Flow

### Scenario 1: User WITHOUT 2FA
```
User Input (username, password)
         ↓
POST /auth/signin
         ↓
Response: { jwtToken, user }
         ↓
localStorage.setItem("auth", response)
         ↓
navigate("/")
```

### Scenario 2: User WITH 2FA
```
User Input (username, password)
         ↓
POST /auth/signin
         ↓
Response: { jwtToken, temp2FAToken, needs2FA: true }
         ↓
Show Verify2FALogin Modal
         ↓
User Input (6-digit code)
         ↓
POST /public/verify-2fa-login?code=xxx&jwtToken=yyy
         ↓
Response: "2FA Verified"
         ↓
localStorage.setItem("auth", { jwtToken: (original token), user })
         ↓
navigate("/")
```

### Scenario 3: Setup 2FA
```
User Click "Enable 2FA"
         ↓
Show Setup2FA Modal
         ↓
POST /enable-2fa
         ↓
Response: QR Code URL
         ↓
Display QR Code
         ↓
User Scan QR
         ↓
User Input (6-digit code)
         ↓
POST /verify-2fa?code=xxx
         ↓
Response: "2FA enabled successfully"
         ↓
Close Modal & Refresh Status
```

---

## Error Codes to Handle

| Code | Status | Meaning | Action |
|------|--------|---------|--------|
| 400 | BAD_REQUEST | Invalid 2FA code | Show error, allow retry |
| 401 | UNAUTHORIZED | Invalid credentials or expired token | Re-login |
| 403 | FORBIDDEN | Access denied (maybe 2FA required) | Show 2FA modal |
| 404 | NOT_FOUND | User not found | Show error |
| 500 | INTERNAL_SERVER_ERROR | Server error | Show generic error |

---

## QR Code Format

Backend returnează TOTP URL în format:
```
otpauth://totp/[ISSUER]:[USERNAME]?secret=[SECRET]&issuer=[ISSUER]
```

Exemplu:
```
otpauth://totp/E-commerce%20Application:john_doe@example.com?secret=JBSWY3DPEBLW64TMMQ======&issuer=E-commerce%20Application
```

Frontend trebuie să convertească URL-ul în QR code (de obicei via bibliotecă).

---

## Security Notes

1. ✅ **temp2FAToken** trebuie să expire rapid (ex: 5 minuti)
2. ✅ **temp2FAToken** trebuie să fie valid doar pentru o singură verificare
3. ✅ **TOTP codes** expiră după 30 de secunde
4. ✅ **Bearer token** trebuie salvat în localStorage doar după succes complet
5. ✅ HTTPS este obligatoriu în producție
6. ✅ Rate limiting pe endpoints de 2FA (prevent brute force)

---

## Integration Checklist

- [ ] Backend returnează `needs2FA: true` și `temp2FAToken` la login?
- [ ] Backend generează QR code URL corect?
- [ ] Backend verifică 6-digit codes corect?
- [ ] Backend endpoint `/public/verify-2fa-login` funcționează?
- [ ] Frontend decode QR code URL în imagine?
- [ ] Frontend salveaza JWT token permanent după 2FA verificare?
- [ ] Frontend afișează error messages corecte?
- [ ] Test flow complet cu Authenticator real app?
- [ ] Test disable/enable 2FA?
- [ ] Test logout și login din nou?
