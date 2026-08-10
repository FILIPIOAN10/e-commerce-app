# E2E Tests — Playwright

End-to-end tests for the full purchase flow: register → verify email → login → 2FA → add to cart → checkout → order confirmation.

## Prerequisites

1. **Docker Desktop** running
2. **Backend + infrastructure** started:
   ```bash
   docker compose up --build
   ```
3. **Frontend** started:
   ```bash
   cd ecom-frontend
   npm install
   npm run dev
   ```

## Running the tests

```bash
cd e2e
npm install
npx playwright install chromium

# Headless (CI)
npm run test:e2e

# Headed (local debugging)
npm run test:e2e:headed

# UI mode (interactive)
npm run test:e2e:ui

# View HTML report
npm run test:e2e:report
```

## What the tests cover

| Test file | Flow |
|---|---|
| `auth.spec.js` | Login with seed admin, wrong password, non-existent user |
| `purchase.spec.js` | Login → browse products → add to cart → view cart → checkout |
| `register.spec.js` | Register a new user with form validation |

## Video evidence

Playwright records videos on failure. To record a video of a successful full flow for the README:

```bash
npx playwright test --video=on --headed
```

The video files are saved in `test-results/`.
