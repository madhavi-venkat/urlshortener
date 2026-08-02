import type { APIRequestContext } from '@playwright/test';
import { API_URL } from '../env';

// Shared test data helpers. There's no test-DB reset between runs, so every
// spec that creates a link uses a unique alias/destination to avoid colliding
// with data left over from a previous run (or from manual testing).

/** Alias-safe unique string: matches ^[A-Za-z0-9_-]{3,16}$, stays well under 16 chars. */
export function uniqueAlias(prefix: string): string {
  const tail = Date.now().toString(36).slice(-6) + Math.floor(Math.random() * 36).toString(36);
  return `${prefix}-${tail}`.slice(0, 16);
}

export function uniqueDestination(label: string): string {
  return `https://example.com/e2e/${label}/${Date.now()}-${Math.floor(Math.random() * 1e6)}`;
}

export interface CreatedLink {
  code: string;
  shortUrl: string;
  longUrl: string;
  expiresAt: string | null;
}

/**
 * Seeds a short URL directly via the API — for specs where creation isn't the
 * behavior under test (redirect, dashboard, edit, analytics), so each spec
 * only exercises the one flow it's actually named for.
 */
export async function createShortUrl(
  request: APIRequestContext,
  opts: { longUrl?: string; customAlias?: string; expiresInSeconds?: number } = {}
): Promise<CreatedLink> {
  const res = await request.post(`${API_URL}/api/v1/urls`, {
    data: {
      longUrl: opts.longUrl ?? uniqueDestination('seed'),
      ...(opts.customAlias ? { customAlias: opts.customAlias } : {}),
      ...(opts.expiresInSeconds ? { expiresInSeconds: opts.expiresInSeconds } : {}),
    },
  });
  if (!res.ok()) {
    throw new Error(`Failed to seed short URL: ${res.status()} ${await res.text()}`);
  }
  return res.json();
}
