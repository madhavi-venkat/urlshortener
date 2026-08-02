import { test, expect } from '@playwright/test';
import { createShortUrl, uniqueAlias, uniqueDestination } from './helpers';

test.describe('Edit', () => {
  test('editing a link updates its destination on the dashboard', async ({ page, request }) => {
    const alias = uniqueAlias('e2eedit');
    const originalDest = uniqueDestination('edit-original');
    const updatedDest = uniqueDestination('edit-updated');
    await createShortUrl(request, { longUrl: originalDest, customAlias: alias });

    await page.goto('/#/admin');
    const row = page.locator('.admin-row', { has: page.locator('.admin-code', { hasText: alias }) });
    await row.getByRole('link', { name: 'Edit' }).click();

    await expect(page).toHaveURL(new RegExp(`#/edit/${alias}$`));
    await expect(page.getByText(`Editing`)).toContainText(alias);

    const urlField = page.getByLabel('Destination URL');
    await expect(urlField).toHaveValue(originalDest);
    await urlField.fill(updatedDest);
    await page.getByRole('button', { name: 'Save changes' }).click();

    await expect(page).toHaveURL(/#\/admin$/);
    const updatedRow = page.locator('.admin-row', {
      has: page.locator('.admin-code', { hasText: alias }),
    });
    await expect(updatedRow.locator('.admin-dest')).toHaveAttribute('title', updatedDest);
  });

  test('the edit page has no custom-code field — the code is immutable', async ({
    page,
    request,
  }) => {
    const link = await createShortUrl(request);
    await page.goto(`/#/edit/${link.code}`);

    await expect(page.getByLabel('Destination URL')).toBeVisible();
    await expect(page.getByLabel('Custom code')).toHaveCount(0);
  });
});
