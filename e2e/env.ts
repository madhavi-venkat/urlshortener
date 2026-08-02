// Shared endpoints, overridable via env vars if you're pointing the suite at
// a non-default setup (a different port, a deployed environment, etc.).
export const FRONTEND_URL = process.env.E2E_BASE_URL ?? 'http://localhost:5173';
export const API_URL = process.env.E2E_API_URL ?? 'http://localhost:8080';
