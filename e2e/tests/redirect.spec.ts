import { test, expect } from '@playwright/test';
import { createShortUrl, uniqueDestination } from './helpers';
import { API_URL } from '../env';

// Checked at the HTTP level (302 + Location), not via page.goto() — the
// destination is a synthetic example.com URL that isn't guaranteed to
// actually resolve/load, and what's under test is *our* redirect response,
// not whether the destination page renders.
test.describe('Redirect', () => {
  test('a short code redirects to its destination', async ({ request }) => {
    const destination = uniqueDestination('redirect-target');
    const link = await createShortUrl(request, { longUrl: destination });

    const res = await request.get(link.shortUrl, { maxRedirects: 0 });
    expect(res.status()).toBe(302);
    expect(res.headers()['location']).toBe(destination);
  });

  test('an unknown code returns 404', async ({ request }) => {
    const res = await request.get(`${API_URL}/does-not-exist-e2e`, { maxRedirects: 0 });
    expect(res.status()).toBe(404);
  });
});
