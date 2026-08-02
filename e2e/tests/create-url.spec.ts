import { test, expect } from '@playwright/test';
import { uniqueAlias, uniqueDestination } from './helpers';

test.describe('Create short URL', () => {
  test('creates a link with a generated code', async ({ page }) => {
    const destination = uniqueDestination('generated');

    await page.goto('/');
    await page.getByLabel('Destination URL').fill(destination);
    await page.getByRole('button', { name: 'Shorten link' }).click();

    const token = page.locator('.token');
    await expect(token).toBeVisible();
    await expect(token.locator('.token-code .code')).not.toBeEmpty();
    await expect(token.locator('.token-meta .dest')).toHaveText(destination);
  });

  test('creates a link with a custom alias', async ({ page }) => {
    const alias = uniqueAlias('e2ealias');
    const destination = uniqueDestination('alias');

    await page.goto('/');
    await page.getByLabel('Destination URL').fill(destination);
    await page.getByLabel('Custom code').fill(alias);
    await page.getByRole('button', { name: 'Shorten link' }).click();

    const token = page.locator('.token');
    await expect(token).toBeVisible();
    await expect(token.locator('.token-code .code')).toHaveText(alias);
  });

  test('rejects a duplicate custom alias', async ({ page }) => {
    const alias = uniqueAlias('e2edupe');

    await page.goto('/');
    await page.getByLabel('Destination URL').fill(uniqueDestination('first'));
    await page.getByLabel('Custom code').fill(alias);
    await page.getByRole('button', { name: 'Shorten link' }).click();
    await expect(page.locator('.token')).toBeVisible();

    // Reload to a clean form, then try the same alias again.
    await page.goto('/');
    await page.getByLabel('Destination URL').fill(uniqueDestination('second'));
    await page.getByLabel('Custom code').fill(alias);
    await page.getByRole('button', { name: 'Shorten link' }).click();

    await expect(page.locator('.error')).toBeVisible();
    await expect(page.locator('.token')).not.toBeVisible();
  });

  test('blocks submission for an invalid destination URL', async ({ page }) => {
    await page.goto('/');
    const urlField = page.getByLabel('Destination URL');
    await urlField.fill('not-a-url');
    await urlField.blur();

    await expect(page.locator('.field-error')).toBeVisible();
    await expect(page.getByRole('button', { name: 'Shorten link' })).toBeDisabled();
  });

  test('blocks submission for an unsafe internal URL', async ({ page }) => {
    await page.goto('/');
    await page.getByLabel('Destination URL').fill('http://127.0.0.1/secret');
    await page.getByRole('button', { name: 'Shorten link' }).click();

    await expect(page.locator('.error')).toBeVisible();
    await expect(page.locator('.token')).not.toBeVisible();
  });
});
