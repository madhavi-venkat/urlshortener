import { test, expect } from '@playwright/test';
import { createShortUrl, uniqueAlias } from './helpers';
import { API_URL } from '../env';

test.describe('Analytics drill-down', () => {
  test('a click is reflected in the per-code stats', async ({ page, request }) => {
    const alias = uniqueAlias('e2eanalyt');
    const link = await createShortUrl(request, { customAlias: alias });

    // A real click through the redirect endpoint.
    await request.get(link.shortUrl, { maxRedirects: 0 });

    // Click recording is async (fire-and-forget) — poll until it lands
    // instead of a fixed sleep.
    await expect
      .poll(
        async () => {
          const res = await request.get(`${API_URL}/api/v1/admin/urls/${alias}/stats`);
          return (await res.json()).totalClicks;
        },
        { timeout: 5000 }
      )
      .toBeGreaterThan(0);

    await page.goto('/#/admin');
    const row = page.locator('.admin-row', { has: page.locator('.admin-code', { hasText: alias }) });
    await row.click();

    await expect(page.locator('.admin-detail .stats-total')).toHaveText('1');
  });

  test('period tabs switch the time-bucket view', async ({ page, request }) => {
    const alias = uniqueAlias('e2eperiod');
    await createShortUrl(request, { customAlias: alias });

    await page.goto('/#/admin');
    const row = page.locator('.admin-row', { has: page.locator('.admin-code', { hasText: alias }) });
    await row.click();

    const detail = page.locator('.admin-detail');
    await expect(detail).toBeVisible();

    const weekTab = detail.getByRole('button', { name: 'Week' });
    await weekTab.click();
    await expect(weekTab).toHaveClass(/active/);
  });
});
