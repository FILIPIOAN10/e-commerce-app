# 🔍 BACKEND CLASSES & METHODS - VERIFICATION CHECKLIST

**Document pentru a verifica dacă ai implementat 2FA corect pe backend**

---

## 📦 CLASE BACKEND NECESARE

### 1️⃣ **USER ENTITY/MODEL**
**Fișier:** `User.java` (in `model/` folder)

**Trebuie să conțină:**
```java
@Column(name = "two_factor_secret")
private String twoFactorSecret;

@Column(name = "two_factor_enabled")
private boolean twoFactorEnabled = false;
```

**Verificare:**
```
✓ User entity are field-ul "twoFactorSecret"
✓ User entity are field-ul "twoFactorEnabled"
✓ Ambele sunt mapate la DB (JPA annotations)
✓ Getter și setter exist
```

---

### 2️⃣ **AUTH SERVICE INTERFACE**
**Fișier:** `AuthService.java` (in `service/` folder)

**Trebuie să conțină aceste metode:**
```java
public interface AuthService {
    
    // Existing methods...
    AuthenticationResult login(LoginRequest loginRequest);
    ResponseEntity register(SignupRequest signupRequest);
    
    // NEW 2FA METHODS
    GoogleAuthenticatorKey generate2FASecret(Long userId);
    boolean validate2FACode(Long userId, int code);
    void enable2FA(Long userId);
    void disable2FA(Long userId);
    boolean verify2FALogin(String jwtToken, int code);
}
```

**Verificare:**
```
✓ Interface are toate 5 metodele 2FA
✓ Metode au semnătura corectă (parameter types, return types)
✓ Service implementation ar trebui să existe
```

---

### 3️⃣ **TOTP SERVICE INTERFACE & IMPLEMENTATION**
**Fișier:** `TotpService.java` + `TotpServiceImpl.java`

**Interface:**
```java
public interface TotpService {
    GoogleAuthenticatorKey generateSecret();
    String getQrCodeUrl(GoogleAuthenticatorKey secret, String username);
    boolean verifyCode(String secret, int code);
}
```

**Implementation:**
```java
@Service
public class TotpServiceImpl implements TotpService {
    private final GoogleAuthenticator gAuth;
    
    public TotpServiceImpl() {
        this.gAuth = new GoogleAuthenticator();
    }
    
    @Override
    public GoogleAuthenticatorKey generateSecret() {
        return gAuth.createCredentials();
    }
    
    @Override
    public String getQrCodeUrl(GoogleAuthenticatorKey secret, String username) {
        return GoogleAuthenticatorQRGenerator.getOtpAuthTotpURL(
            "E-commerce Application",
            username,
            secret
        );
    }
    
    @Override
    public boolean verifyCode(String secret, int code) {
        return gAuth.authorize(secret, code);
    }
}
```

**Verificare:**
```
✓ TotpService interface exists
✓ TotpServiceImpl implement interface
✓ GoogleAuthenticator dependency injected
✓ generateSecret() uses gAuth.createCredentials()
✓ getQrCodeUrl() generates valid TOTP URL
✓ verifyCode() validates code
```

---

### 4️⃣ **AUTH IMPLEMENTATION**
**Fișier:** `AuthServiceImpl.java` (implementare AuthService)

**Trebuie să conțină:**
```java
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {
    
    private final UserRepository userRepository;
    private final TotpService totpService;
    private final JwtUtils jwtUtils;
    private final AuthUtil authUtil;
    
    // ===== EXISTING METHODS =====
    @Override
    public AuthenticationResult login(LoginRequest loginRequest) {
        // Existing login logic...
        // IMPORTANT: Trebuie să returneze needs2FA flag
    }
    
    // ===== NEW 2FA METHODS =====
    
    @Override
    public GoogleAuthenticatorKey generate2FASecret(Long userId) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new RuntimeException("User not found"));
        GoogleAuthenticatorKey key = totpService.generateSecret();
        user.setTwoFactorSecret(key.getKey());
        userRepository.save(user);
        return key;
    }
    
    @Override
    public boolean validate2FACode(Long userId, int code) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new RuntimeException("User not found"));
        return totpService.verifyCode(user.getTwoFactorSecret(), code);
    }
    
    @Override
    public void enable2FA(Long userId) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new RuntimeException("User not found"));
        user.setTwoFactorEnabled(true);
        userRepository.save(user);
    }
    
    @Override
    public void disable2FA(Long userId) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new RuntimeException("User not found"));
        user.setTwoFactorEnabled(false);
        user.setTwoFactorSecret(null);
        userRepository.save(user);
    }
    
    @Override
    public boolean verify2FALogin(String jwtToken, int code) {
        String username = jwtUtils.getUserNameFromJWTToken(jwtToken);
        User user = userRepository.findByUserName(username)
            .orElseThrow(() -> new RuntimeException("User not found"));
        return validate2FACode(user.getUserId(), code);
    }
}
```

**Verificare:**
```
✓ AuthServiceImpl implements AuthService
✓ TotpService injected
✓ generate2FASecret() saves secret în DB
✓ validate2FACode() verifies code using TotpService
✓ enable2FA() sets twoFactorEnabled = true
✓ disable2FA() sets twoFactorEnabled = false AND clears secret
✓ verify2FALogin() validates using JWT token
```

---

### 5️⃣ **AUTH CONTROLLER**
**Fișier:** `AuthController.java`

**Trebuie să conțină:**
```java
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {
    
    private final AuthService authService;
    private final AuthUtil authUtil;
    
    // EXISTING ENDPOINTS
    @PostMapping("/signin")
    public ResponseEntity<?> login(@RequestBody LoginRequest loginRequest) {
        // Login logic...
    }
    
    @PostMapping("/signup")
    public ResponseEntity<?> register(@RequestBody SignupRequest signupRequest) {
        // Register logic...
    }
    
    // ===== NEW 2FA ENDPOINTS =====
    
    @PostMapping("/enable-2fa")
    public ResponseEntity<?> enable2FA() {
        Long userId = authUtil.loggedInUserId();
        GoogleAuthenticatorKey secret = authService.generate2FASecret(userId);
        String qrCodeUrl = totpService.getQrCodeUrl(secret, authUtil.loggedInEmail());
        return ResponseEntity.ok(qrCodeUrl);
    }
    
    @PostMapping("/disable-2fa")
    public ResponseEntity<?> disable2FA() {
        Long userId = authUtil.loggedInUserId();
        authService.disable2FA(userId);
        return ResponseEntity.ok("2FA disabled successfully");
    }
    
    @PostMapping("/verify-2fa")
    public ResponseEntity<?> verify2FA(@RequestParam int code) {
        Long userId = authUtil.loggedInUserId();
        boolean isValid = authService.validate2FACode(userId, code);
        if (!isValid) {
            return ResponseEntity.badRequest().body("Invalid 2FA code");
        }
        authService.enable2FA(userId);
        return ResponseEntity.ok("2FA enabled successfully");
    }
    
    @PostMapping("/user/2fa-status")
    public ResponseEntity<?> get2FAStatus() {
        User user = authUtil.loggedInUser();
        if (user != null) {
            return ResponseEntity.ok(Map.of("is2faEnabled", user.isTwoFactorEnabled()));
        } else {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("User not found");
        }
    }
    
    @PostMapping("/public/verify-2fa-login")
    public ResponseEntity<?> verify2FALogin(
        @RequestParam int code,
        @RequestParam String jwtToken
    ) {
        boolean isValid = authService.verify2FALogin(jwtToken, code);
        if (isValid) {
            return ResponseEntity.ok("2FA Verified");
        } else {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body("Invalid 2FA Code");
        }
    }
}
```

**Verificare:**
```
✓ Toți 5 endpoints sunt prezenti
✓ Endpoints sunt POST
✓ /enable-2fa returnează QR code URL (string)
✓ /verify-2fa acceptă code param și returnează success msg
✓ /user/2fa-status returnează JSON cu is2faEnabled
✓ /public/verify-2fa-login acceptă code și jwtToken params
✓ Endpoints sunt protected cu @Secured/@PreAuthorize (except /public/*)
```

---

### 6️⃣ **LOGIN REQUEST/RESPONSE DTOs**
**Fișier:** `LoginRequest.java`, `LoginResponse.java` (or custom response)

**LoginRequest:**
```java
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LoginRequest {
    private String username;
    private String password;
}
```

**Login Response (MUST include 2FA fields):**
```java
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LoginResponse {
    private String jwtToken;
    private User user;
    
    // NEW 2FA FIELDS
    private boolean needs2FA;
    private String temp2FAToken;  // Temporary token for 2FA verification
}
```

**Verificare:**
```
✓ LoginResponse are needs2FA boolean
✓ LoginResponse are temp2FAToken string
✓ Ces sunt populated când user has 2FA enabled
```

---

### 7️⃣ **JWT UTILS**
**Fișier:** `JwtUtils.java`

**Trebuie să conțină:**
```java
public class JwtUtils {
    
    // EXISTING METHODS
    public String generateJwtToken(Authentication authentication) {
        // Generate JWT...
    }
    
    public String getUserNameFromJWTToken(String token) {
        // Extract username from JWT
    }
    
    // NEW - Pentru temp 2FA token
    public String generateTemp2FAToken(String username) {
        // Generate temporary token válid pentru 5 minutes
        // Trebuie diferit de JWT principal!
    }
    
    public String getUserNameFromTemp2FAToken(String token) {
        // Extract username din temp token
    }
}
```

**Verificare:**
```
✓ Metoda generateTemp2FAToken() exists
✓ Temp token diferit de JWT principal
✓ Temp token expire rapid (5-10 minute)
✓ Metoda getUserNameFromJWTToken() works
```

---

### 8️⃣ **AUTH UTIL**
**Fișier:** `AuthUtil.java`

**Trebuie să conțină:**
```java
@Component
public class AuthUtil {
    
    public Long loggedInUserId() {
        // Return current user ID from SecurityContext
    }
    
    public String loggedInEmail() {
        // Return current user email
    }
    
    public User loggedInUser() {
        // Return current User object
    }
    
    public String loggedInUsername() {
        // Return current username
    }
}
```

**Verificare:**
```
✓ loggedInUserId() returnează user ID
✓ loggedInEmail() returnează user email
✓ loggedInUser() returnează User object
✓ Metode extrag din SecurityContext
```

---

## 🔐 MODIFICĂRI LA SIGNIN ENDPOINT

**IMPORTANT:** Login endpoint trebuie modified!

**Before:**
```java
@PostMapping("/signin")
public ResponseEntity<?> login(@RequestBody LoginRequest loginRequest) {
    User user = userRepository.findByUserName(loginRequest.getUsername())
        .orElseThrow(() -> new Exception("Invalid credentials"));
    
    // Validate password...
    
    String jwtToken = jwtUtils.generateJwtToken(authentication);
    return ResponseEntity.ok(new LoginResponse(jwtToken, user));
}
```

**After (WITH 2FA CHECK):**
```java
@PostMapping("/signin")
public ResponseEntity<?> login(@RequestBody LoginRequest loginRequest) {
    User user = userRepository.findByUserName(loginRequest.getUsername())
        .orElseThrow(() -> new Exception("Invalid credentials"));
    
    // Validate password...
    
    // IMPORTANT: Check if 2FA is enabled
    if (user.isTwoFactorEnabled()) {
        // Generate temporary 2FA token
        String temp2FAToken = jwtUtils.generateTemp2FAToken(user.getUserName());
        
        // Return response with needs2FA flag
        return ResponseEntity.ok(new LoginResponse(
            jwtToken,           // Regular JWT (or null)
            user,
            true,              // needs2FA = true
            temp2FAToken       // Temporary token for 2FA verification
        ));
    }
    
    // Normal login (no 2FA)
    String jwtToken = jwtUtils.generateJwtToken(authentication);
    return ResponseEntity.ok(new LoginResponse(jwtToken, user));
}
```

---

## 📋 VERIFICATION CHECKLIST

```
USER ENTITY:
□ Field "twoFactorSecret" exists
□ Field "twoFactorEnabled" exists
□ JPA @Column annotations present
□ Getters/Setters exist

AUTH SERVICE INTERFACE:
□ generate2FASecret(Long userId) method
□ validate2FACode(Long userId, int code) method
□ enable2FA(Long userId) method
□ disable2FA(Long userId) method
□ verify2FALogin(String jwtToken, int code) method

TOTP SERVICE:
□ TotpService interface exists
□ TotpServiceImpl implements interface
□ generateSecret() uses GoogleAuthenticator
□ getQrCodeUrl() generates TOTP URL
□ verifyCode() validates 6-digit codes

AUTH CONTROLLER - ENDPOINTS:
□ POST /enable-2fa (requires auth)
□ POST /disable-2fa (requires auth)
□ POST /verify-2fa (requires auth, param: code)
□ POST /user/2fa-status (requires auth)
□ POST /public/verify-2fa-login (public, params: code, jwtToken)

SIGNIN ENDPOINT:
□ Checks if user.twoFactorEnabled
□ Returns needs2FA: true if enabled
□ Generates temp2FAToken if needed
□ Normal response if 2FA disabled

RESPONSE DTOs:
□ LoginResponse has needs2FA boolean
□ LoginResponse has temp2FAToken string

UTILITIES:
□ JwtUtils has generateTemp2FAToken()
□ JwtUtils has getUserNameFromJWTToken()
□ AuthUtil has loggedInUserId()
□ AuthUtil has loggedInEmail()
□ AuthUtil has loggedInUser()
```

---

## 🧪 TESTING COMMANDS

### Test 1: Check User Entity
```java
// In test
User user = userRepository.findById(1L).get();
assertTrue(user.isTwoFactorEnabled() == false);  // Default
assertNull(user.getTwoFactorSecret());           // Default null
```

### Test 2: Generate 2FA Secret
```bash
curl -X POST http://localhost:8080/enable-2fa \
  -H "Authorization: Bearer <JWT_TOKEN>" \
  -H "Content-Type: application/json"

# Expected Response: otpauth://totp/...
```

### Test 3: Login without 2FA
```bash
curl -X POST http://localhost:8080/auth/signin \
  -H "Content-Type: application/json" \
  -d '{
    "username": "user_without_2fa",
    "password": "password123"
  }'

# Expected Response:
{
  "jwtToken": "eyJhbGc...",
  "user": { ... },
  "needs2FA": false,
  "temp2FAToken": null
}
```

### Test 4: Login with 2FA (user has 2FA enabled)
```bash
curl -X POST http://localhost:8080/auth/signin \
  -H "Content-Type: application/json" \
  -d '{
    "username": "user_with_2fa",
    "password": "password123"
  }'

# Expected Response:
{
  "jwtToken": "eyJhbGc...",
  "user": { ... },
  "needs2FA": true,
  "temp2FAToken": "eyJhbGc..." // Different token!
}
```

### Test 5: Verify 2FA Code
```bash
# Get current code from Authenticator (example: 123456)

curl -X POST "http://localhost:8080/auth/verify-2fa?code=123456" \
  -H "Authorization: Bearer <JWT_TOKEN>" \
  -H "Content-Type: application/json"

# Expected Response (200):
"2FA enabled successfully"

# Or (400) for invalid code:
"Invalid 2FA code"
```

### Test 6: Verify 2FA at Login
```bash
# Get temp token from login response (example: temp_token_xyz)
# Get code from Authenticator (example: 654321)

curl -X POST "http://localhost:8080/public/verify-2fa-login?code=654321&jwtToken=temp_token_xyz" \
  -H "Content-Type: application/json"

# Expected Response (200):
"2FA Verified"

# Or (401) for invalid code:
"Invalid 2FA Code"
```

---

## 🔍 HOW TO VERIFY IMPLEMENTATION

### Step 1: Check Classes Exist
```
src/main/java/
├── model/User.java (check 2FA fields)
├── service/
│   ├── AuthService.java (check interface)
│   ├── AuthServiceImpl.java (check implementation)
│   ├── TotpService.java (check interface)
│   └── TotpServiceImpl.java (check implementation)
├── controller/AuthController.java (check endpoints)
├── dto/
│   ├── LoginRequest.java
│   └── LoginResponse.java (check 2FA fields)
├── security/
│   ├── JwtUtils.java (check temp token methods)
│   └── AuthUtil.java (check utility methods)
└── repository/UserRepository.java
```

### Step 2: Check Maven Dependencies
```xml
<!-- pom.xml trebuie să conțină -->
<dependency>
    <groupId>com.warrenstrange</groupId>
    <artifactId>jtop</artifactId>
    <version>1.0.0</version> <!-- or latest -->
</dependency>
```

### Step 3: Run Integration Tests
```bash
mvn test -Dtest=AuthControllerTest
mvn test -Dtest=TotpServiceTest
```

### Step 4: Start Application and Test
```bash
mvn spring-boot:run

# Then use Postman or cURL commands above
```

---

## ⚠️ COMMON BACKEND ISSUES

### ❌ Issue 1: QR code not displaying
**Cause:** `/enable-2fa` not returning proper TOTP URL
**Fix:** Check `getQrCodeUrl()` implementation

### ❌ Issue 2: Login endpoint not returning needs2FA
**Cause:** Signin endpoint not modified
**Fix:** Add 2FA check in login logic

### ❌ Issue 3: "Invalid 2FA code" always
**Cause:** Time sync issue or wrong secret storage
**Fix:** Check time on server, verify secret saved correctly

### ❌ Issue 4: Temp token validation fails
**Cause:** Token not properly generated or extracted
**Fix:** Check JwtUtils generateTemp2FAToken() and parsing

---

## 📞 CRITICAL METHODS TO VERIFY

```
TOP 5 Methods to Check:
1. AuthServiceImpl.generate2FASecret() → saves secret to DB
2. AuthServiceImpl.validate2FACode() → validates using TOTP
3. AuthController.login() → checks twoFactorEnabled
4. TotpServiceImpl.verifyCode() → uses GoogleAuthenticator
5. JwtUtils.generateTemp2FAToken() → creates temp token
```

---

Dă-mi feedback dacă lipsește vreun class sau method!
