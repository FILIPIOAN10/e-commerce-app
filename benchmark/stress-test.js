/**
 * ============================================================
 *  STRESS TEST — ecom-backend
 *  Împinge aplicația până găsești punctul de rupere
 *  Rulare: k6 run stress-test.js
 * ============================================================
 *
 *  Scenarii incluse:
 *  1. Stress Test  — crește progresiv până la 200 useri
 *  2. Spike Test   — salt brusc de la 5 la 150 useri
 *  3. Soak Test    — 30 useri timp de 10 minute (memory leaks)
 *
 *  Schimbă SCENARIO de mai jos pentru a alege ce rulezi.
 */

import http from 'k6/http';
import { check, sleep, group } from 'k6';
import { Rate, Trend } from 'k6/metrics';

const errorRate    = new Rate('errors');
const p99Duration  = new Trend('p99_duration');

// ── Alege scenariul: 'stress' | 'spike' | 'soak' ───────────
const SCENARIO = __ENV.SCENARIO || 'stress';

const SCENARIOS = {
  stress: {
    stages: [
      { duration: '1m',  target: 50  }, // nivel normal
      { duration: '2m',  target: 100 }, // începi să strângi
      { duration: '2m',  target: 150 }, // stres ridicat
      { duration: '2m',  target: 200 }, // limita maximă
      { duration: '1m',  target: 0   }, // recuperare
    ],
  },
  spike: {
    stages: [
      { duration: '10s', target: 5   }, // baseline minimal
      { duration: '10s', target: 150 }, // spike brusc — flash sale!
      { duration: '2m',  target: 150 }, // menții spike-ul
      { duration: '10s', target: 5   }, // revenire
      { duration: '30s', target: 0   },
    ],
  },
  soak: {
    stages: [
      { duration: '1m',  target: 30  }, // ramp-up
      { duration: '8m',  target: 30  }, // 8 minute la 30 useri — detectezi memory leaks
      { duration: '1m',  target: 0   },
    ],
  },
};

export const options = {
  stages: SCENARIOS[SCENARIO].stages,
  thresholds: {
    http_req_duration: ['p(99)<2000'], // sub stress, 99% sub 2s
    errors:            ['rate<0.05'],  // max 5% erori sub stress
  },
};

const BASE_URL = 'http://localhost:8080/api';
const TEST_USER = { username: 'testuser', password: 'Test1234!' };
const PRODUCT_IDS = [1, 2, 3, 4, 5];

export function setup() {
  const res = http.post(
    `${BASE_URL}/auth/signin`,
    JSON.stringify(TEST_USER),
    { headers: { 'Content-Type': 'application/json' } }
  );
  const body = res.json();
  return { token: body.jwtToken };
}

export default function (data) {
  const headers = {
    'Content-Type': 'application/json',
    Authorization: `Bearer ${data.token}`,
  };

  // ── Flux rapid: produse + coș (cele mai frecvente operații) ─
  group('Flux rapid', () => {

    // Produse — lovește Redis cache-ul
    const p1 = http.get(`${BASE_URL}/public/products?pageNumber=0&pageSize=10`);
    p99Duration.add(p1.timings.duration);
    const ok1 = check(p1, { 'products ok': (r) => r.status === 200 });
    errorRate.add(!ok1);

    sleep(0.2);

    // Caută cu keyword diferit la fiecare iterație
    const queries = ['phone', 'laptop', 'shirt', 'bag', 'watch'];
    const q = queries[Math.floor(Math.random() * queries.length)];
    const p2 = http.get(`${BASE_URL}/public/products?keyword=${q}&pageNumber=0&pageSize=5`);
    const ok2 = check(p2, { 'search ok': (r) => r.status === 200 });
    errorRate.add(!ok2);

    sleep(0.2);

    // Adaugă în coș — operație cu scriere în DB
    const pid = PRODUCT_IDS[Math.floor(Math.random() * PRODUCT_IDS.length)];
    const p3 = http.post(
      `${BASE_URL}/carts/products/${pid}/quantity/1`,
      null,
      { headers }
    );
    // 201 = adăugat, 400 = deja în coș, ambele sunt ok
    const ok3 = check(p3, { 'cart ok': (r) => r.status === 201 || r.status === 400 });
    errorRate.add(p3.status >= 500);

    sleep(0.3);
  });

  sleep(0.5);
}