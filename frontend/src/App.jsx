import { useEffect, useMemo, useState } from 'react';

const API = 'http://localhost:8080';

// Split "http://localhost:8080/abc123" into host and code for the token display.
function splitShort(shortUrl) {
  const idx = shortUrl.lastIndexOf('/');
  return { host: shortUrl.slice(0, idx + 1), code: shortUrl.slice(idx + 1) };
}

const ALIAS_PATTERN = /^[A-Za-z0-9_-]{3,16}$/;

// Mirrors the server-side checks in UrlSafetyValidator/CreateShortUrlRequest
// closely enough to catch obvious mistakes before a round trip.
function longUrlError(value) {
  const trimmed = value.trim();
  if (!trimmed) return 'Destination URL is required.';
  let uri;
  try {
    uri = new URL(trimmed);
  } catch {
    return 'Enter a valid URL, including https://';
  }
  if (uri.protocol !== 'http:' && uri.protocol !== 'https:') {
    return 'Only http and https URLs are allowed.';
  }
  if (!uri.hostname) return 'URL must include a host.';
  return '';
}

function aliasError(value) {
  const trimmed = value.trim();
  if (!trimmed) return '';
  if (!ALIAS_PATTERN.test(trimmed)) {
    return 'Custom code must be 3-16 letters, digits, _ or -.';
  }
  return '';
}

function formatExpiry(expiresAt) {
  return expiresAt ? `expires ${new Date(expiresAt).toLocaleString()}` : 'never expires';
}

const RANK_BAR_LIMIT = 10;
const DAY_MS = 24 * 60 * 60 * 1000;

// Derives the Dashboard's analytics overview (top codes, unused, expiring-soon)
// entirely from the admin listing already fetched — no extra API calls.
function computeOverview(rows) {
  const byClicks = [...rows].sort((a, b) => b.totalClicks - a.totalClicks);
  const mostClicked = byClicks[0].totalClicks > 0 ? byClicks[0] : null;
  const unused = rows.filter((r) => r.totalClicks === 0);

  const now = Date.now();
  const expiringSoon = rows
    .filter((r) => r.expiresAt && new Date(r.expiresAt).getTime() > now
      && new Date(r.expiresAt).getTime() - now <= DAY_MS)
    .sort((a, b) => new Date(a.expiresAt) - new Date(b.expiresAt));

  return {
    topByClicks: byClicks.slice(0, RANK_BAR_LIMIT),
    maxClicks: Math.max(1, byClicks[0].totalClicks),
    mostClicked,
    unused,
    expiringSoon,
  };
}

async function readProblem(res) {
  try {
    const body = await res.json();
    return body.detail || body.title || `Request failed (${res.status})`;
  } catch {
    return `Request failed (${res.status})`;
  }
}

function Token({ result }) {
  const [copied, setCopied] = useState(false);
  const { host, code } = splitShort(result.shortUrl);

  const copy = async () => {
    await navigator.clipboard.writeText(result.shortUrl);
    setCopied(true);
    setTimeout(() => setCopied(false), 1600);
  };

  return (
    <div className="token">
      <div className="token-main">
        <div className="token-label">Your short link</div>
        <div className="token-code">
          <span className="host">{host}</span>
          <span className="code">{code}</span>
        </div>
        <div className="token-actions">
          <button className={copied ? 'copied' : ''} onClick={copy}>
            {copied ? 'Copied' : 'Copy link'}
          </button>
          <button onClick={() => window.open(result.shortUrl, '_blank')}>Open</button>
        </div>
      </div>
      <div className="tear" />
      <div className="token-meta">
        <div className="dest">{result.longUrl}</div>
        {result.expiresAt && (
          <div className="expiry">Expires {new Date(result.expiresAt).toLocaleString()}</div>
        )}
      </div>
    </div>
  );
}

function Creator({ editCode }) {
  const isEditing = !!editCode;

  const [longUrl, setLongUrl] = useState('');
  const [alias, setAlias] = useState('');
  const [expiry, setExpiry] = useState(isEditing ? 'keep' : '');
  const [currentExpiresAt, setCurrentExpiresAt] = useState(null);
  const [touched, setTouched] = useState({ longUrl: false, alias: false });
  const [result, setResult] = useState(null);
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  const [loading, setLoading] = useState(isEditing);
  const [loadError, setLoadError] = useState('');

  useEffect(() => {
    if (!isEditing) return;
    let cancelled = false;
    (async () => {
      setLoading(true);
      setLoadError('');
      try {
        const res = await fetch(`${API}/api/v1/admin/urls/${encodeURIComponent(editCode)}`);
        if (!res.ok) {
          if (!cancelled) setLoadError(await readProblem(res));
          return;
        }
        const body = await res.json();
        if (!cancelled) {
          setLongUrl(body.longUrl);
          setCurrentExpiresAt(body.expiresAt);
        }
      } catch {
        if (!cancelled) setLoadError('Could not reach the service. Is the API running on :8080?');
      } finally {
        if (!cancelled) setLoading(false);
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [isEditing, editCode]);

  const urlError = longUrlError(longUrl);
  const codeError = isEditing ? '' : aliasError(alias);
  const canSubmit = !urlError && !codeError;

  const submit = async () => {
    setTouched({ longUrl: true, alias: true });
    if (!canSubmit) return;
    setError('');
    setResult(null);
    setBusy(true);
    try {
      if (isEditing) {
        const payload = {
          longUrl,
          changeExpiry: expiry !== 'keep',
          expiresInSeconds: expiry === 'keep' || expiry === '' ? null : Number(expiry),
        };
        const res = await fetch(`${API}/api/v1/admin/urls/${encodeURIComponent(editCode)}`, {
          method: 'PATCH',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify(payload),
        });
        if (!res.ok) {
          setError(await readProblem(res));
          return;
        }
        window.location.hash = '#/admin';
        return;
      }

      const payload = { longUrl };
      if (alias.trim()) payload.customAlias = alias.trim();
      if (expiry) payload.expiresInSeconds = Number(expiry);

      const res = await fetch(`${API}/api/v1/urls`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload),
      });
      if (!res.ok) {
        setError(await readProblem(res));
        return;
      }
      setResult(await res.json());
    } catch {
      setError('Could not reach the service. Is the API running on :8080?');
    } finally {
      setBusy(false);
    }
  };

  if (loading) {
    return (
      <div className="panel">
        <div className="admin-detail-loading">Loading…</div>
      </div>
    );
  }

  if (loadError) {
    return (
      <div className="panel">
        <div className="error">{loadError}</div>
      </div>
    );
  }

  return (
    <div className="panel">
      {isEditing && (
        <div className="field editing-code">
          Editing <span className="admin-code">/{editCode}</span>
          <span className="hint"> — the code itself can't be changed</span>
        </div>
      )}
      <div className="field">
        <label htmlFor="url">Destination URL</label>
        <input
          id="url"
          placeholder="https://example.com/a/very/long/path"
          value={longUrl}
          aria-invalid={touched.longUrl && !!urlError}
          onChange={(e) => setLongUrl(e.target.value)}
          onBlur={() => setTouched((t) => ({ ...t, longUrl: true }))}
        />
        {touched.longUrl && urlError && <div className="field-error">{urlError}</div>}
      </div>
      <div className="row">
        {!isEditing && (
          <div className="field">
            <label htmlFor="alias">
              Custom code <span className="hint">optional</span>
            </label>
            <input
              id="alias"
              placeholder="launch-2026"
              value={alias}
              aria-invalid={touched.alias && !!codeError}
              onChange={(e) => setAlias(e.target.value)}
              onBlur={() => setTouched((t) => ({ ...t, alias: true }))}
            />
            {touched.alias && codeError && <div className="field-error">{codeError}</div>}
          </div>
        )}
        <div className="field">
          <label htmlFor="expiry">
            Expires <span className="hint">optional</span>
          </label>
          <select id="expiry" value={expiry} onChange={(e) => setExpiry(e.target.value)}>
            {isEditing && (
              <option value="keep">Keep current ({formatExpiry(currentExpiresAt)})</option>
            )}
            <option value="">Never</option>
            <option value="3600">In 1 hour</option>
            <option value="86400">In 1 day</option>
            <option value="604800">In 1 week</option>
          </select>
        </div>
      </div>
      <button className="primary" onClick={submit} disabled={busy || !canSubmit}>
        {isEditing
          ? busy
            ? 'Saving…'
            : 'Save changes'
          : busy
            ? 'Shortening…'
            : 'Shorten link'}
      </button>

      {error && <div className="error">{error}</div>}
      {result && <Token result={result} />}
    </div>
  );
}

const PERIODS = [
  { value: 'DAY', label: 'Day' },
  { value: 'WEEK', label: 'Week' },
  { value: 'MONTH', label: 'Month' },
];

function bucketLabel(bucketStart, period) {
  const d = new Date(bucketStart);
  if (period === 'MONTH') return d.toLocaleDateString(undefined, { year: 'numeric', month: 'short' });
  return d.toLocaleDateString(undefined, { month: 'short', day: 'numeric' });
}

// The per-code detail a row expands into: total clicks, a day/week/month drill-down,
// and a geo breakdown.
function AdminRowDetail({ code }) {
  const [period, setPeriod] = useState('DAY');
  const [stats, setStats] = useState(null);
  const [error, setError] = useState('');

  useEffect(() => {
    let cancelled = false;
    setStats(null);
    setError('');
    (async () => {
      try {
        const res = await fetch(
          `${API}/api/v1/admin/urls/${encodeURIComponent(code)}/stats?period=${period}`
        );
        if (!res.ok) {
          if (!cancelled) setError(await readProblem(res));
          return;
        }
        const body = await res.json();
        if (!cancelled) setStats(body);
      } catch {
        if (!cancelled) setError('Could not reach the service.');
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [code, period]);

  if (error) return <div className="error">{error}</div>;

  const maxBucketClicks = stats
    ? Math.max(1, ...stats.clicksByPeriod.map((b) => b.clicks))
    : 1;

  return (
    <div className="admin-detail">
      {!stats ? (
        <div className="admin-detail-loading">Loading…</div>
      ) : (
        <>
          <div className="stats-total">{stats.totalClicks.toLocaleString()}</div>
          <div className="stats-total-label">total clicks on /{stats.code}</div>

          <div className="period-tabs">
            {PERIODS.map((p) => (
              <button
                key={p.value}
                className={period === p.value ? 'period-tab active' : 'period-tab'}
                onClick={() => setPeriod(p.value)}
              >
                {p.label}
              </button>
            ))}
          </div>

          {stats.clicksByPeriod.length === 0 ? (
            <div className="admin-empty">No clicks in this period yet.</div>
          ) : (
            <div className="time-series">
              {stats.clicksByPeriod.map((b) => (
                <div className="time-bar-row" key={b.bucketStart}>
                  <span className="time-bar-label">{bucketLabel(b.bucketStart, period)}</span>
                  <div className="time-bar-track">
                    <div
                      className="time-bar-fill"
                      style={{ width: `${(b.clicks / maxBucketClicks) * 100}%` }}
                    />
                  </div>
                  <span className="time-bar-count">{b.clicks.toLocaleString()}</span>
                </div>
              ))}
            </div>
          )}

          {Object.keys(stats.clicksByCountry).length > 0 && (
            <div className="geo-section">
              <div className="section-subtitle">By geo</div>
              {Object.entries(stats.clicksByCountry).map(([cc, n]) => (
                <div className="country-row" key={cc}>
                  <span className="cc">{cc}</span>
                  <span className="n">{n}</span>
                </div>
              ))}
            </div>
          )}
        </>
      )}
    </div>
  );
}

const DATE_SORT_KEYS = new Set(['createdAt', 'updatedAt']);

function compareRows(a, b, key) {
  if (key === 'totalClicks') return a.totalClicks - b.totalClicks;
  if (DATE_SORT_KEYS.has(key)) return new Date(a[key]).getTime() - new Date(b[key]).getTime();
  return String(a[key]).localeCompare(String(b[key]));
}

// Sortable admin-table-head cell: click toggles direction, or switches column
// with a sensible default (newest/highest first for dates and clicks).
function SortHeader({ label, sortKey, sort, onSort }) {
  const active = sort.key === sortKey;
  return (
    <button type="button" className={active ? 'admin-sort-btn active' : 'admin-sort-btn'}
      onClick={() => onSort(sortKey)}>
      {label}
      <span className="admin-sort-arrow" aria-hidden="true">
        {active ? (sort.direction === 'asc' ? '↑' : '↓') : ''}
      </span>
    </button>
  );
}

function AdminPage() {
  const [rows, setRows] = useState(null);
  const [error, setError] = useState('');
  const [expanded, setExpanded] = useState(null);
  const [sort, setSort] = useState({ key: 'createdAt', direction: 'desc' });

  useEffect(() => {
    let cancelled = false;
    (async () => {
      try {
        const res = await fetch(`${API}/api/v1/admin/urls`);
        if (!res.ok) {
          if (!cancelled) setError(await readProblem(res));
          return;
        }
        const body = await res.json();
        if (!cancelled) setRows(body);
      } catch {
        if (!cancelled) setError('Could not reach the service. Is the API running on :8080?');
      }
    })();
    return () => {
      cancelled = true;
    };
  }, []);

  const overview = useMemo(() => (rows && rows.length > 0 ? computeOverview(rows) : null), [rows]);

  const sortedRows = useMemo(() => {
    if (!rows) return rows;
    const sorted = [...rows].sort((a, b) => compareRows(a, b, sort.key));
    return sort.direction === 'desc' ? sorted.reverse() : sorted;
  }, [rows, sort]);

  const toggleSort = (key) => {
    setSort((prev) => {
      if (prev.key === key) return { key, direction: prev.direction === 'asc' ? 'desc' : 'asc' };
      const defaultDesc = key === 'createdAt' || key === 'updatedAt' || key === 'totalClicks';
      return { key, direction: defaultDesc ? 'desc' : 'asc' };
    });
  };

  return (
    <div className="wrap">
      <header className="masthead">
        <p className="eyebrow">URL Shortener</p>
        <h1>My Dashboard</h1>
        <div className="masthead-links">
          <a className="nav-link" href="#/">
            + Create URL
          </a>
          <a className="nav-link" href="#/admin">
            Analytics →
          </a>
        </div>
      </header>

      {overview && (
        <div className="panel">
          <h2 className="section-title">Analytics overview</h2>

          <div className="kpi-row">
            <div className="kpi-tile">
              <p className="kpi-label">Most clicked</p>
              <p className="kpi-value">
                {overview.mostClicked ? overview.mostClicked.totalClicks.toLocaleString() : '—'}
              </p>
              <p className="kpi-context">
                {overview.mostClicked ? `/${overview.mostClicked.code}` : 'no clicks yet'}
              </p>
            </div>
            <div className="kpi-tile">
              <p className="kpi-label">Never clicked</p>
              <p className="kpi-value">{overview.unused.length}</p>
              <p className="kpi-context">of {rows.length} link{rows.length === 1 ? '' : 's'}</p>
            </div>
            <div className={`kpi-tile ${overview.expiringSoon.length > 0 ? 'kpi-tile--warning' : ''}`}>
              <p className="kpi-label">
                {overview.expiringSoon.length > 0 && (
                  <span className="kpi-status-dot" aria-hidden="true" />
                )}
                Expiring in 24h
              </p>
              <p className="kpi-value">{overview.expiringSoon.length}</p>
              <p className="kpi-context">
                {overview.expiringSoon.length > 0 ? 'needs attention' : 'nothing due'}
              </p>
            </div>
          </div>

          <p className="section-subtitle">Clicks by code</p>
          <div className="time-series">
            {overview.topByClicks.map((row) => (
              <div className="rank-bar-row" key={row.code}>
                <span className="time-bar-label">/{row.code}</span>
                <div
                  className="time-bar-track"
                  title={`${row.totalClicks.toLocaleString()} click${row.totalClicks === 1 ? '' : 's'}`}
                >
                  <div
                    className="time-bar-fill"
                    style={{ width: `${(row.totalClicks / overview.maxClicks) * 100}%` }}
                  />
                </div>
                <span className="time-bar-count">{row.totalClicks.toLocaleString()}</span>
              </div>
            ))}
          </div>
          {rows.length > overview.topByClicks.length && (
            <p className="rank-more">+{rows.length - overview.topByClicks.length} more not shown</p>
          )}

          {overview.expiringSoon.length > 0 && (
            <div className="expiring-list">
              <p className="section-subtitle">Expiring soon</p>
              {overview.expiringSoon.map((row) => (
                <div className="country-row" key={row.code}>
                  <span className="cc">
                    <span className="kpi-status-dot" aria-hidden="true" />/{row.code}
                  </span>
                  <span className="n">{new Date(row.expiresAt).toLocaleString()}</span>
                </div>
              ))}
            </div>
          )}
        </div>
      )}

      <div className="panel">
        <h2 className="section-title">Your links</h2>
        {error && <div className="error">{error}</div>}
        {rows && rows.length === 0 && <div className="admin-empty">No links created yet.</div>}
        {rows && rows.length > 0 && (
          <div className="admin-table">
            <div className="admin-table-head">
              <SortHeader label="Code" sortKey="code" sort={sort} onSort={toggleSort} />
              <SortHeader label="Destination" sortKey="longUrl" sort={sort} onSort={toggleSort} />
              <SortHeader label="Created" sortKey="createdAt" sort={sort} onSort={toggleSort} />
              <SortHeader label="Updated" sortKey="updatedAt" sort={sort} onSort={toggleSort} />
              <SortHeader label="Clicks" sortKey="totalClicks" sort={sort} onSort={toggleSort} />
              <span>Actions</span>
            </div>
            {sortedRows.map((row) => (
              <div key={row.code} className="admin-row-group">
                <div
                  className="admin-row"
                  role="button"
                  tabIndex={0}
                  onClick={() => setExpanded(expanded === row.code ? null : row.code)}
                  onKeyDown={(e) => {
                    if (e.key === 'Enter' || e.key === ' ') {
                      e.preventDefault();
                      setExpanded(expanded === row.code ? null : row.code);
                    }
                  }}
                >
                  <span className="admin-code">{row.code}</span>
                  <span className="admin-dest" title={row.longUrl}>
                    {row.longUrl}
                  </span>
                  <span className="admin-created">
                    {new Date(row.createdAt).toLocaleDateString()}
                  </span>
                  <span className="admin-created">
                    {new Date(row.updatedAt).toLocaleDateString()}
                  </span>
                  <span className="admin-clicks">{row.totalClicks.toLocaleString()}</span>
                  <span className="admin-actions" onClick={(e) => e.stopPropagation()}>
                    <a className="admin-edit-btn" href={`#/edit/${encodeURIComponent(row.code)}`}>
                      Edit
                    </a>
                  </span>
                </div>
                {expanded === row.code && <AdminRowDetail code={row.code} />}
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  );
}

// Minimal hash routing (#/admin) — no router dependency for a two-page app.
function useRoute() {
  const [hash, setHash] = useState(window.location.hash);
  useEffect(() => {
    const onChange = () => setHash(window.location.hash);
    window.addEventListener('hashchange', onChange);
    return () => window.removeEventListener('hashchange', onChange);
  }, []);
  return hash;
}

export default function App() {
  const hash = useRoute();
  if (hash === '#/admin') return <AdminPage />;

  const editMatch = hash.match(/^#\/edit\/(.+)$/);
  const editCode = editMatch ? decodeURIComponent(editMatch[1]) : null;

  return (
    <div className="wrap">
      <header className="masthead">
        <p className="eyebrow">URL Shortener</p>
        {editCode ? (
          <h1>Edit link</h1>
        ) : (
          <h1>
            <span className="long">Long URL</span> <span className="arrow">→</span> short.
          </h1>
        )}
        <a className="nav-link" href="#/admin">
          {editCode ? '← My Dashboard' : 'My Dashboard →'}
        </a>
      </header>
      <Creator editCode={editCode} />
    </div>
  );
}
