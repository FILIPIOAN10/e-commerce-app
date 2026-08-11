/**
 * ============================================================
 *  FULL USER JOURNEY TEST — ecom-backend
 *  Simulează fluxul complet: login → browse → coș → comandă
 *  Rulare: k6 run user-journey-test.js
 * ============================================================
 *
 *  ATENȚIE: Acest test plasează comenzi reale în DB.
 *           Rulează doar pe un environment de test/dev.
 */

import http from 'k6/http';
import { check, sleep, group, fail } from 'k6';
import { Counter, Rate } from 'k6/metrics';

const ordersPlaced   = new Counter('orders_placed');
const loginErrors    = new Counter('login_errors');
const checkoutErrors = new Counter('checkout_errors');
const errorRate      = new Rate('errors');

export const options = {
  // Flux complet: 10 useri, 3 iterații fiecare
  vus:        10,
  iterations: 30,
  thresholds: {
    http_req_duration:  ['p(95)<1500'],
    errors:             ['rate<0.05'],
    orders_placed:      ['count>0'], // cel puțin 1 comandă plasată
  },
};

const BASE_URL   = 'http://localhost:8080/api';
const TEST_USER  = { username: 'testuser', password: 'Test1234!' };
// Trebuie să existe în DB un produs cu acest ID și quantity > 0
const PRODUCT_ID = 1;
// Trebuie să existe o adresă salvată pentru testuser
// Dacă nu există, scriptul o creează automat (vezi step 3)

// ─────────────────────────────────────────────────────────────
//  STEP 1 — Login
// ─────────────────────────────────────────────────────────────
function doLogin() {
  const res = http.post(
    `${BASE_URL}/auth/signin`,
    JSON.stringify(TEST_USER),
    { headers: { 'Content-Type': 'application/json' } }
  );

  const ok = check(res, {
    'login: status 200':    (r) => r.status === 200,
    'login: are jwtToken':  (r) => r.json('jwtToken') !== undefined,
    'login: sub 1s':        (r) => r.timings.duration < 1000,
  });

  if (!ok) {
    loginErrors.add(1);
    errorRate.add(1);
    fail('Login eșuat — opresc iterația');
  }

  return res.json('jwtToken');
}

// ─────────────────────────────────────────────────────────────
//  STEP 2 — Browse produse
// ─────────────────────────────────────────────────────────────
function doBrowse(token) {
  const headers = { Authorization: `Bearer ${token}` };

  // Lista principală de produse
  const res1 = http.get(
    `${BASE_URL}/public/products?pageNumber=0&pageSize=10&sortBy=productId&sortOrder=asc`,
  );
  check(res1, { 'browse: produse 200': (r) => r.status === 200 });

  sleep(0.5);

  // Caută ceva
  const res2 = http.get(`${BASE_URL}/public/products?keyword=phone&pageNumber=0&pageSize=5`);
  check(res2, { 'browse: search 200': (r) => r.status === 200 });

  sleep(0.5);
}

// ─────────────────────────────────────────────────────────────
//  STEP 3 — Asigură că există o adresă (o creează dacă nu)
// ─────────────────────────────────────────────────────────────
function ensureAddress(token) {
  const headers = {
    'Content-Type': 'application/json',
    Authorization: `Bearer ${token}`,
  };

  // Ia adresele existente
  const res = http.get(`${BASE_URL}/users/addresses`, { headers });

  if (res.status === 200) {
    const addresses = res.json();
    if (addresses && addresses.length > 0) {
      return addresses[0].addressId;
    }
  }

  // Nu există nicio adresă — creăm una
  const createRes = http.post(
    `${BASE_URL}/addresses`,
    JSON.stringify({
      street:    'Strada Test 42',
      buildingName: 'Bloc A',
      city:      'Cluj-Napoca',
      state:     'Cluj',
      country:   'Romania',
      pincode:   '400001',
    }),
    { headers }
  );

  const ok = check(createRes, {
    'address: creat 201': (r) => r.status === 201,
  });

  if (!ok) {
    console.error('Nu s-a putut crea adresa:', createRes.body);
    return null;
  }

  return createRes.json('addressId');
}

// ─────────────────────────────────────────────────────────────
//  STEP 4 — Adaugă produs în coș
// ─────────────────────────────────────────────────────────────
function doAddToCart(token) {
  const headers = {
    'Content-Type': 'application/json',
    Authorization: `Bearer ${token}`,
  };

  const res = http.post(
    `${BASE_URL}/carts/products/${PRODUCT_ID}/quantity/1`,
    null,
    { headers }
  );

  const ok = check(res, {
    'cart: adaugat 201 sau deja in cos 400': (r) =>
      r.status === 201 || r.status === 400,
  });

  errorRate.add(res.status >= 500);
  sleep(0.5);
}

// ─────────────────────────────────────────────────────────────
//  STEP 5 — Verifică coșul
// ─────────────────────────────────────────────────────────────
function doViewCart(token) {
  const headers = { Authorization: `Bearer ${token}` };

  const res = http.get(`${BASE_URL}/carts/users/cart`, { headers });

  check(res, {
    'cart view: 200': (r) => r.status === 200,
  });

  return res.status === 200 ? res.json() : null;
}

// ─────────────────────────────────────────────────────────────
//  STEP 6 — Plasează comanda (COD - Cash on Delivery)
// ─────────────────────────────────────────────────────────────
function doPlaceOrder(token, addressId) {
  if (!addressId) {
    console.warn('Nu am addressId — skip checkout');
    return;
  }

  const headers = {
    'Content-Type': 'application/json',
    Authorization: `Bearer ${token}`,
  };

  const res = http.post(
    `${BASE_URL}/order/users/payments/COD`,
    JSON.stringify({
      addressId:         addressId,
      pgName:            'COD',
      pgPaymentId:       'test_' + Date.now(),
      pgStatus:          'SUCCESS',
      pgResponseMessage: 'Cash on Delivery',
    }),
    { headers }
  );

  const ok = check(res, {
    'order: plasat 201':    (r) => r.status === 201,
    'order: are orderId':   (r) => r.json('orderId') !== undefined,
    'order: sub 2s':        (r) => r.timings.duration < 2000,
  });

  if (ok) {
    ordersPlaced.add(1);
    console.log('Comandă plasată, orderId:', res.json('orderId'));
  } else {
    checkoutErrors.add(1);
    errorRate.add(1);
    console.error('Checkout eșuat:', res.status, res.body);
  }

  sleep(1);
}

// ─────────────────────────────────────────────────────────────
//  STEP 7 — Verifică istoricul comenzilor
// ─────────────────────────────────────────────────────────────
function doCheckMyOrders(token) {
  const headers = { Authorization: `Bearer ${token}` };

  const res = http.get(
    `${BASE_URL}/orders/my-orders?pageNumber=0&pageSize=5`,
    { headers }
  );

  check(res, {
    'my orders: 200':    (r) => r.status === 200,
    'my orders: are comenzi': (r) => {
      const body = r.json();
      return body && body.content !== undefined;
    },
  });
}

// ─────────────────────────────────────────────────────────────
//  MAIN — fluxul principal
// ─────────────────────────────────────────────────────────────
export default function () {
  group('Full User Journey', () => {

    group('Step 1: Login', () => {
      const token = doLogin();
      if (!token) return;
      sleep(0.5);

      group('Step 2: Browse', () => doBrowse(token));

      group('Step 3: Adresa', () => {
        const addressId = ensureAddress(token);
        sleep(0.5);

        group('Step 4: Adauga in cos', () => doAddToCart(token));

        group('Step 5: Vad cosul', () => doViewCart(token));

        group('Step 6: Plasez comanda', () => doPlaceOrder(token, addressId));

        group('Step 7: Verific comenzile', () => doCheckMyOrders(token));
      });
    });

  });

  sleep(1);
}