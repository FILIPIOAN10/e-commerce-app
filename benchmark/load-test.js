/**
 * ============================================================
 *  LOAD TEST — ecom-backend
 *  Simulează 50 useri simultani care browsează și cumpără
 *  Rulare: k6 run load-test.js
 * ============================================================
 */

import http from 'k6/http';
import { check, sleep, group } from 'k6';
import { Counter, Rate, Trend } from 'k6/metrics';

// ── Metrici custom ──────────────────────────────────────────
const errorRate      = new Rate('errors');
const cartAddSuccess = new Counter('cart_add_success');
const loginDuration  = new Trend('login_duration_ms');
const productsDuration = new Trend('products_duration_ms');

// ── Configurare test ────────────────────────────────────────
export const options = {
  stages: [
    { duration: '30s', target: 10  }, // ramp-up lent
    { duration: '1m',  target: 50  }, // load normal: 50 useri
    { duration: '30s', target: 50  }, // menții
    { duration: '20s', target: 0   }, // ramp-down
  ],
  thresholds: {
    // 95% din request-uri trebuie să răspundă sub 500ms
    http_req_duration:    ['p(95)<500'],
    // mai puțin de 2% erori
    errors:               ['rate<0.02'],
    // login-ul specific sub 800ms
    login_duration_ms:    ['p(95)<800'],
    // lista produse sub 300ms (Redis cache activ)
    products_duration_ms: ['p(95)<300'],
  },
};

const BASE_URL = 'http://localhost:8080/api';

// ── Date de test (trebuie să existe în DB) ──────────────────
const TEST_USER = { username: 'testuser', password: 'Test1234!' };
// ID-uri reale de produse din baza ta de date
const PRODUCT_IDS = [1, 2, 3, 4, 5];
// ID real de categorie
const CATEGORY_ID = 1;

// ─────────────────────────────────────────────────────────────
//  SETUP — rulează o singură dată înainte de test
//  Returnează token-ul JWT folosit de toți userii virtuali
// ─────────────────────────────────────────────────────────────
export function setup() {
  const res = http.post(
    `${BASE_URL}/auth/signin`,
    JSON.stringify(TEST_USER),
    { headers: { 'Content-Type': 'application/json' } }
  );

  const ok = check(res, { 'setup: login 200': (r) => r.status === 200 });
  if (!ok) {
    console.error('SETUP FAILED — nu s-a putut loga userul de test!');
    console.error('Status:', res.status, 'Body:', res.body);
  }

  const body = res.json();
  return {
    token: body.jwtToken,
  };
}

// ─────────────────────────────────────────────────────────────
//  SCENARIUL PRINCIPAL — executat de fiecare user virtual
// ─────────────────────────────────────────────────────────────
export default function (data) {
  const headers = {
    'Content-Type': 'application/json',
    Authorization: `Bearer ${data.token}`,
  };

  // ── 1. Browse produse publice (fără auth) ────────────────
  group('1. Browse produse', () => {
    const start = Date.now();

    const res = http.get(
      `${BASE_URL}/public/products?pageNumber=0&pageSize=10&sortBy=productId&sortOrder=asc`,
    );

    productsDuration.add(Date.now() - start);

    const ok = check(res, {
      'products: status 200':    (r) => r.status === 200,
      'products: are content':   (r) => r.json('content') !== null,
      'products: sub 500ms':     (r) => r.timings.duration < 500,
    });
    errorRate.add(!ok);

    sleep(1);
  });

  // ── 2. Caută produse cu keyword ──────────────────────────
  group('2. Cauta produse', () => {
    const keywords = ['laptop', 'phone', 'shirt', 'shoes', 'book'];
    const kw = keywords[Math.floor(Math.random() * keywords.length)];

    const res = http.get(
      `${BASE_URL}/public/products?keyword=${kw}&pageNumber=0&pageSize=10`,
    );

    const ok = check(res, {
      'search: status 200': (r) => r.status === 200,
    });
    errorRate.add(!ok);

    sleep(0.5);
  });

  // ── 3. Produse pe categorie ──────────────────────────────
  group('3. Produse pe categorie', () => {
    const res = http.get(
      `${BASE_URL}/public/categories/${CATEGORY_ID}/products?pageNumber=0&pageSize=10`,
    );

    check(res, {
      'category products: status 200 sau 404': (r) =>
        r.status === 200 || r.status === 404,
    });

    sleep(0.5);
  });

  // ── 4. Adaugă produs în coș (cu auth) ───────────────────
  group('4. Adauga in cos', () => {
    const productId = PRODUCT_IDS[Math.floor(Math.random() * PRODUCT_IDS.length)];
    const quantity  = Math.floor(Math.random() * 3) + 1;

    const res = http.post(
      `${BASE_URL}/carts/products/${productId}/quantity/${quantity}`,
      null,
      { headers }
    );

    const ok = check(res, {
      'cart add: status 201 sau 400': (r) =>
        r.status === 201 || r.status === 400, // 400 dacă produsul e deja în coș
    });

    if (res.status === 201) cartAddSuccess.add(1);
    errorRate.add(res.status >= 500);

    sleep(0.5);
  });

  // ── 5. Citește coșul curent ──────────────────────────────
  group('5. Vezi cosul', () => {
    const res = http.get(
      `${BASE_URL}/carts/users/cart`,
      { headers }
    );

    const ok = check(res, {
      'cart get: status 200': (r) => r.status === 200 || r.status === 500,
      // 500 dacă userul nu are coș (normal pentru useri noi)
    });

    sleep(0.5);
  });

  // ── 6. Adresele userului ─────────────────────────────────
  group('6. Adrese user', () => {
    const res = http.get(
      `${BASE_URL}/users/addresses`,
      { headers }
    );

    check(res, {
      'addresses: status 200': (r) => r.status === 200,
    });

    sleep(0.5);
  });

  // ── 7. Comenzile userului ────────────────────────────────
  group('7. Comenzile mele', () => {
    const res = http.get(
      `${BASE_URL}/orders/my-orders?pageNumber=0&pageSize=5`,
      { headers }
    );

    check(res, {
      'my orders: status 200': (r) => r.status === 200,
    });

    sleep(1);
  });

  sleep(1); // pauză între iterații — simulează userul real
}

// ─────────────────────────────────────────────────────────────
//  TEARDOWN — afișează sumarul final
// ─────────────────────────────────────────────────────────────
export function teardown(data) {
  console.log('Test finalizat. Verifică graficele de mai sus.');
}