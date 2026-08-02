import { defineConfig, devices } from '@playwright/test';
import { FRONTEND_URL } from './env';

export default defineConfig({
  testDir: './tests',
  fullyParallel: true,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 2 : 0,
  workers: process.env.CI ? 2 : undefined,
  reporter: [['html', { open: 'never' }], ['list']],

  use: {
    baseURL: FRONTEND_URL,
    trace: 'on-first-retry',
    screenshot: 'only-on-failure',
  },

  projects: [
    { name: 'chromium', use: { ...devices['Desktop Chrome'] } },
  ],

  // Starts the Vite dev server for you. The backend (Spring Boot + Postgres +
  // Redis) is NOT started here — bring it up yourself first:
  //   docker compose up -d && mvn spring-boot:run
  // (see e2e/README.md).
  webServer: {
    command: 'npm run dev',
    cwd: '../frontend',
    url: FRONTEND_URL,
    reuseExistingServer: !process.env.CI,
    timeout: 30_000,
  },
});
