import { test, expect } from '@playwright/test';
import { createShortUrl, uniqueAlias, uniqueDestination } from './helpers';

test.describe('Dashboard', () => {
  test('a created link shows up in the table', async ({ page, request }) => {
    const alias = uniqueAlias('e2edash');
    const destination = uniqueDestination('dashboard');
    await createShortUrl(request, { longUrl: destination, customAlias: alias });

    await page.goto('/#/admin');
    const row = page.locator('.admin-row', { has: page.locator('.admin-code', { hasText: alias }) });
    await expect(row).toBeVisible();
    await expect(row.locator('.admin-dest')).toHaveAttribute('title', destination);
  });

  test('the analytics overview renders when links exist', async ({ page, request }) => {
    await createShortUrl(request);

    await page.goto('/#/admin');
    await expect(page.getByText('Analytics overview')).toBeVisible();
    await expect(page.locator('.kpi-tile')).toHaveCount(3);
    await expect(page.getByText('Clicks by code')).toBeVisible();
  });

  test('clicking a sortable column header toggles direction', async ({ page, request }) => {
    await createShortUrl(request);
    await page.goto('/#/admin');

    const codeHeader = page.getByRole('button', { name: 'Code' });
    await codeHeader.click();
    await expect(codeHeader).toHaveClass(/active/);
    const firstArrow = await codeHeader.locator('.admin-sort-arrow').textContent();

    await codeHeader.click();
    const secondArrow = await codeHeader.locator('.admin-sort-arrow').textContent();
    expect(secondArrow).not.toBe(firstArrow);
  });

  test('expanding a row shows its analytics detail', async ({ page, request }) => {
    const alias = uniqueAlias('e2eexpand');
    await createShortUrl(request, { customAlias: alias });

    await page.goto('/#/admin');
    const row = page.locator('.admin-row', { has: page.locator('.admin-code', { hasText: alias }) });
    await row.click();

    const detail = page.locator('.admin-detail');
    await expect(detail).toBeVisible();
    await expect(detail.locator('.stats-total')).toBeVisible();
  });
});
