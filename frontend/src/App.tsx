import { useCallback, useEffect, useState, type FormEvent } from 'react';
import { api, type CreateOnboarding, type Onboarding, type OnboardingStatus, type VerificationMode } from './api';

const STORAGE_KEY = 'onboarding-demo-id';
const MODE_KEY = 'onboarding-demo-mode';
const INITIAL: CreateOnboarding = {
  adminName: 'Alex Smith',
  adminEmail: 'delay@acme.com',
  companyName: 'Acme Ltd',
  companyDomain: 'acme.com'
};
const IS_PROCESSING = (status: OnboardingStatus) => status === 'PENDING' || status === 'VERIFYING';

const SCENARIOS = [
  { label: 'Fast timeout', email: 'slow@acme.com', note: 'sync → async fallback' },
  { label: 'Instant success', email: 'success@acme.com', note: 'valid' },
  { label: 'Delayed (202)', email: 'delay@acme.com', note: 'pending → success' },
  { label: 'Transient error', email: 'flaky@acme.com', note: '503 → retry' },
  { label: 'Retry limit (5)', email: 'exhaust@acme.com', note: '5 failures → review' },
  { label: 'Invalid contact', email: 'invalid@acme.com', note: 'action required' },
  { label: 'Inconclusive', email: 'risky@acme.com', note: 'manual review' },
  { label: 'Quota exhausted', email: 'quota@acme.com', note: 'unavailable' }
];

const STATUS_LABELS: Record<OnboardingStatus, string> = {
  PENDING: 'Queued',
  VERIFYING: 'Verification in progress',
  VERIFIED: 'Email check passed',
  ACTION_REQUIRED: 'Action required',
  REVIEW_REQUIRED: 'Needs further review',
  TEMPORARILY_UNAVAILABLE: 'Provider unavailable'
};

function statusTone(status: OnboardingStatus) {
  return status === 'VERIFIED' ? 'success' : IS_PROCESSING(status) ? 'working' : 'attention';
}

function formatDate(value: string) {
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? value : date.toLocaleString();
}

type Screen = 'history' | 'verify';

export default function App() {
  const [screen, setScreen] = useState<Screen>('verify');
  const [menuOpen, setMenuOpen] = useState(false);
  const [form, setForm] = useState<CreateOnboarding>(INITIAL);
  const [mode, setMode] = useState<VerificationMode>(() =>
      localStorage.getItem(MODE_KEY) === 'ASYNC' ? 'ASYNC' : 'SYNC');
  const [onboardingId, setOnboardingId] = useState<string | null>(() => localStorage.getItem(STORAGE_KEY));
  const [current, setCurrent] = useState<Onboarding | null>(null);
  const [queuedRecord, setQueuedRecord] = useState<Onboarding | null>(null);
  const [history, setHistory] = useState<Onboarding[]>([]);
  const [selectedHistoryId, setSelectedHistoryId] = useState<string | null>(null);
  const [historyLoading, setHistoryLoading] = useState(false);
  const [historyError, setHistoryError] = useState<string | null>(null);
  const [contactEmail, setContactEmail] = useState('');
  const [busy, setBusy] = useState(false);
  const [loading, setLoading] = useState(Boolean(localStorage.getItem(STORAGE_KEY)));
  const [error, setError] = useState<string | null>(null);
  const [pollError, setPollError] = useState<string | null>(null);

  const refreshCurrent = useCallback(async (id: string) => {
    try {
      const record = await api.get(id);
      setCurrent(record);
      setContactEmail(record.adminEmail);
      setPollError(null);
    } catch (err) {
      setPollError(err instanceof Error ? err.message : 'Unable to reach backend');
    } finally {
      setLoading(false);
    }
  }, []);

  const refreshHistory = useCallback(async (showLoading = false) => {
    if (showLoading) setHistoryLoading(true);
    try {
      setHistory(await api.list());
      setHistoryError(null);
    } catch (err) {
      setHistoryError(err instanceof Error ? err.message : 'Unable to retrieve records');
    } finally {
      if (showLoading) setHistoryLoading(false);
    }
  }, []);

  useEffect(() => {
    if (!onboardingId) return;
    void refreshCurrent(onboardingId);
    if (current && !IS_PROCESSING(current.status)) return;
    const timer = window.setInterval(() => void refreshCurrent(onboardingId), 2500);
    return () => window.clearInterval(timer);
  }, [onboardingId, current?.status, refreshCurrent]);

  useEffect(() => {
    if (screen !== 'history' && !queuedRecord) return;
    void refreshHistory();
    const timer = window.setInterval(() => void refreshHistory(), 3000);
    return () => window.clearInterval(timer);
  }, [screen, queuedRecord?.id, refreshHistory]);

  useEffect(() => {
    if (!menuOpen) return;
    function onKeyDown(event: KeyboardEvent) {
      if (event.key === 'Escape') setMenuOpen(false);
    }
    window.addEventListener('keydown', onKeyDown);
    return () => window.removeEventListener('keydown', onKeyDown);
  }, [menuOpen]);

  function chooseMode(next: VerificationMode) {
    setMode(next);
    localStorage.setItem(MODE_KEY, next);
  }

  function navigate(next: Screen) {
    setMenuOpen(false);
    setScreen(next);
    setError(null);
    if (next === 'history') void refreshHistory(true);
    if (next === 'verify') {
      // Navigation to New verification always gives a fresh form.
      localStorage.removeItem(STORAGE_KEY);
      setOnboardingId(null);
      setCurrent(null);
      setLoading(false);
    }
  }

  async function start(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setBusy(true);
    setError(null);
    try {
      const record = await api.create(form, mode);
      if (mode === 'ASYNC') {
        // Do NOT lock the form onto a pending record. The next request
        // can be submitted while this one is being processed in PostgreSQL.
        localStorage.removeItem(STORAGE_KEY);
        setOnboardingId(null);
        setCurrent(null);
        setQueuedRecord(record);
        void refreshHistory();
      } else {
        localStorage.setItem(STORAGE_KEY, record.id);
        setCurrent(record);
        setContactEmail(record.adminEmail);
        setOnboardingId(record.id);
        setQueuedRecord(null);
      }
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Unable to create onboarding');
    } finally {
      setBusy(false);
    }
  }

  async function correctContact(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!current) return;
    setBusy(true);
    setError(null);
    try {
      const record = await api.correct(current.id, contactEmail, mode);
      setCurrent(record);
      if (mode === 'ASYNC') {
        setQueuedRecord(record);
        setCurrent(null);
        setOnboardingId(null);
        localStorage.removeItem(STORAGE_KEY);
        void refreshHistory();
      }
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Unable to update contact');
    } finally {
      setBusy(false);
    }
  }

  function startNew() {
    localStorage.removeItem(STORAGE_KEY);
    setOnboardingId(null);
    setCurrent(null);
    setLoading(false);
    setError(null);
    setPollError(null);
    setForm(INITIAL);
  }

  const latestQueued = queuedRecord && (history.find(r => r.id === queuedRecord.id) ?? queuedRecord);
  const processingCount = history.filter(r => IS_PROCESSING(r.status)).length;
  const verifiedCount = history.filter(r => r.status === 'VERIFIED').length;
  const currentIsProcessing = current && IS_PROCESSING(current.status);
  const success = current?.status === 'VERIFIED';

  return (
      <div className="shell">
        <header className="topbar">
          <div className="topbar-left">
            <button type="button" className="hamburger" onClick={() => setMenuOpen(!menuOpen)}
                    aria-label={menuOpen ? 'Close navigation' : 'Open navigation'} aria-controls="app-navigation" aria-expanded={menuOpen}>
              <span /><span /><span />
            </button>
            <div className="brand"><span className="brand-icon">◈</span> <span>Northstar</span> <small>/ onboarding lab</small></div>
          </div>
          <span className="environment"><span className="status-dot" /> HUNTER / MOCK-READY</span>
        </header>

        {menuOpen && <button className="menu-scrim" type="button" onClick={() => setMenuOpen(false)} aria-label="Close navigation" />}
        <nav id="app-navigation" className={`drawer ${menuOpen ? 'drawer-open' : ''}`} aria-label="Main navigation" aria-hidden={!menuOpen}>
          <div className="drawer-heading">NAVIGATION</div>
          <button type="button" tabIndex={menuOpen ? 0 : -1} className={`drawer-item ${screen === 'history' ? 'active' : ''}`} onClick={() => navigate('history')}>
            <span className="drawer-glyph">▤</span><span><strong>Previous records</strong><small>View verification history and outcomes</small></span><span className="drawer-arrow">→</span>
          </button>
          <button type="button" tabIndex={menuOpen ? 0 : -1} className={`drawer-item ${screen === 'verify' ? 'active' : ''}`} onClick={() => navigate('verify')}>
            <span className="drawer-glyph">＋</span><span><strong>New verification</strong><small>Sync first or queue asynchronously</small></span><span className="drawer-arrow">→</span>
          </button>
        </nav>

        <main className="main-grid">
          <aside className="sidebar">
            <span className="eyebrow">A BACKEND ENGINEERING EXERCISE</span>
            {screen === 'history' ? (
                <>
                  <h1>Every request.<br /><em>One clear outcome.</em></h1>
                  <p>Recent registrations and their latest contact-verification statuses are loaded from PostgreSQL.</p>
                  <div className="tech-note"><span>LIVE STATUS</span><p>Open requests keep processing while you browse. This view refreshes every 3 seconds and survives page reloads.</p></div>
                </>
            ) : (
                <>
                  <h1>Onboard your business.<br /><em>Verify with confidence.</em></h1>
                  <p>Choose a quick verification attempt or queue it immediately. Both paths persist unfinished checks in PostgreSQL.</p>
                  <ol className="step-list">
                    <li className="step active-step"><span className="step-number">01</span><span><strong>Company details</strong><small>Collect admin and business information</small></span></li>
                    <li className={`step ${current || latestQueued ? 'active-step' : ''}`}><span className="step-number">02</span><span><strong>Contact verification</strong><small>Sync-first or queued processing</small></span></li>
                    <li className={`step ${success ? 'active-step' : ''}`}><span className="step-number">03</span><span><strong>Continue onboarding</strong><small>Not in scope for this prototype</small></span></li>
                  </ol>
                  <div className="tech-note"><span>TWO EXECUTION MODES</span><p>Sync first: try Hunter before returning, then fall back if unfinished. Async: return once the job is saved, leaving the form ready for another request.</p></div>
                </>
            )}
          </aside>

          <section className={`panel ${screen === 'history' ? 'history-panel' : ''}`}>
            {screen === 'history' ? (
                <>
                  <div className="panel-heading history-heading">
                    <div><span className="eyebrow">HISTORY / LATEST 50</span><h2>Previous records</h2><p>Each row shows the latest outcome for a saved onboarding.</p></div>
                    <button type="button" className="secondary" onClick={() => void refreshHistory(true)} disabled={historyLoading}>{historyLoading ? 'Refreshing…' : '↻ Refresh'}</button>
                  </div>
                  <div className="history-stats">
                    <div><strong>{history.length}</strong><small>Records shown</small></div>
                    <div><strong>{processingCount}</strong><small>In progress</small></div>
                    <div><strong>{verifiedCount}</strong><small>Passed</small></div>
                  </div>
                  {historyError && <div role="alert" className="error">{historyError} <button type="button" className="text-button" onClick={() => void refreshHistory(true)}>Retry</button></div>}
                  {historyLoading && history.length === 0 ? (
                      <p className="loading"><span className="spinner" /> Loading records…</p>
                  ) : history.length === 0 ? (
                      <div className="empty-state"><strong>No records yet</strong><p>Submit a verification to see its outcome here.</p><button className="primary" onClick={() => navigate('verify')}>Create first request →</button></div>
                  ) : (
                      <>
                        <div className="history-list" aria-label="Previous onboardings">
                          {history.map(item => (
                              <div className="history-record-group" key={item.id}>
                                <button type="button" aria-expanded={selectedHistoryId === item.id}
                                        onClick={() => setSelectedHistoryId(selectedHistoryId === item.id ? null : item.id)}
                                        className={`history-record ${selectedHistoryId === item.id ? 'selected' : ''}`}>
                                  <span className="record-main"><strong>{item.companyName}</strong><small>{item.adminEmail}</small></span>
                                  <span className={`status-pill ${statusTone(item.status)}`}>{IS_PROCESSING(item.status) ? '● ' : ''}{STATUS_LABELS[item.status]}</span>
                                  <span className="record-date">{formatDate(item.createdAt)}</span>
                                  <span className="record-chevron">{selectedHistoryId === item.id ? '⌃' : '⌄'}</span>
                                </button>
                                {selectedHistoryId === item.id && (
                                    <div className="record-detail">
                                      <div className="record-detail-head"><h3>{item.companyName}</h3><span className={`status-pill ${statusTone(item.status)}`}>{STATUS_LABELS[item.status]}</span></div>
                                      <p>{item.statusMessage}</p>
                                      <dl className="details">
                                        <div><dt>Admin</dt><dd>{item.adminName}</dd></div>
                                        <div><dt>Email</dt><dd>{item.adminEmail}</dd></div>
                                        <div><dt>Business domain</dt><dd>{item.companyDomain}</dd></div>
                                        <div><dt>Provider result</dt><dd>{item.providerStatus ?? 'Awaiting result'}</dd></div>
                                        <div><dt>Revision</dt><dd>{item.revision}</dd></div>
                                        <div><dt>Last updated</dt><dd>{formatDate(item.updatedAt)}</dd></div>
                                        <div><dt>ID</dt><dd className="code">{item.id}</dd></div>
                                      </dl>
                                    </div>
                                )}
                              </div>
                          ))}
                        </div>
                      </>
                  )}
                  <div className="bottom-actions"><button type="button" className="text-button" onClick={() => navigate('verify')}>+ New verification →</button><small className="history-hint">Showing latest 50 · Auto-refresh every 3s</small></div>
                </>
            ) : loading && !current ? (
                <p className="loading"><span className="spinner" /> Loading saved onboarding…</p>
            ) : !current ? (
                <>
                  <div className="panel-heading"><span className="eyebrow">NEW VERIFICATION</span><h2>Set up your company</h2><p>Choose how Hunter verification runs. Async lets you queue multiple requests without waiting for earlier ones.</p></div>
                  <form onSubmit={start} className="entry-form">
                    <label>Admin full name<input required maxLength={120} value={form.adminName} onChange={e => setForm({ ...form, adminName: e.target.value })} /></label>
                    <label>Admin business email<input required type="email" maxLength={255} value={form.adminEmail} onChange={e => setForm({ ...form, adminEmail: e.target.value })} /></label>
                    <label>Company name<input required maxLength={160} value={form.companyName} onChange={e => setForm({ ...form, companyName: e.target.value })} /></label>
                    <label>Company domain<input required placeholder="acme.com" maxLength={255} value={form.companyDomain} onChange={e => setForm({ ...form, companyDomain: e.target.value })} /></label>
                    <fieldset className="mode-selector" disabled={busy}>
                      <legend>How should verification run?</legend>
                      <label className={`mode-option ${mode === 'SYNC' ? 'selected' : ''}`}>
                        <input type="radio" name="verification-mode" checked={mode === 'SYNC'} onChange={() => chooseMode('SYNC')} />
                        <span><strong>Sync first</strong><small>Try Hunter on the request thread. Unfinished checks continue as background jobs.</small></span>
                      </label>
                      <label className={`mode-option ${mode === 'ASYNC' ? 'selected' : ''}`}>
                        <input type="radio" name="verification-mode" checked={mode === 'ASYNC'} onChange={() => chooseMode('ASYNC')} />
                        <span><strong>Async · keep form open</strong><small>Queue instantly. Submit more companies while earlier requests are running.</small></span>
                      </label>
                    </fieldset>
                    {error && <p className="error" role="alert">{error}</p>}
                    <button className="primary" disabled={busy}>{busy ? (mode === 'SYNC' ? 'Checking Hunter…' : 'Queuing…') : mode === 'SYNC' ? 'Register & verify now →' : 'Queue verification →'}</button>
                  </form>
                  {latestQueued && (
                      <div className="queue-confirmation" role="status">
                        <span className="queue-check">✓</span>
                        <div><strong>{IS_PROCESSING(latestQueued.status) ? 'Request queued. You can submit another.' : `Latest request: ${STATUS_LABELS[latestQueued.status]}`}</strong>
                          <p>{latestQueued.adminEmail} · {STATUS_LABELS[latestQueued.status]}</p>
                          <button type="button" className="text-button" onClick={() => { setSelectedHistoryId(latestQueued.id); navigate('history'); }}>See it in previous records →</button>
                        </div>
                      </div>
                  )}
                  <div className="scenario-box"><span className="eyebrow">MOCK INTEGRATION · CHOOSE A TEST CASE</span><div className="scenario-grid">
                    {SCENARIOS.map(item => <button key={item.email} type="button" className={`scenario ${form.adminEmail === item.email ? 'selected' : ''}`} onClick={() => setForm({ ...form, adminEmail: item.email })}><strong>{item.label}</strong><small>{item.note}</small></button>)}
                  </div></div>
                </>
            ) : (
                <>
                  <div className="panel-heading"><span className="eyebrow">VERIFICATION OUTCOME</span><h2>Contact verification</h2><p>The synchronous attempt finished or handed off to your durable background worker.</p></div>
                  <div className={`outcome ${statusTone(current.status)}`} aria-live="polite">
                    <div className="outcome-header"><span className="outcome-icon">{success ? '✓' : currentIsProcessing ? '↻' : '!'}</span><div><span className="eyebrow">CURRENT STATUS</span><h3>{STATUS_LABELS[current.status]}</h3></div></div>
                    <p>{current.statusMessage}</p>
                    {currentIsProcessing && <div className="activity"><span className="spinner" /> Polling status every 2.5 seconds</div>}
                  </div>
                  <dl className="details">
                    <div><dt>Company</dt><dd>{current.companyName}</dd></div>
                    <div><dt>Domain</dt><dd>{current.companyDomain}</dd></div>
                    <div><dt>Email</dt><dd>{current.adminEmail}</dd></div>
                    <div><dt>Hunter status</dt><dd>{current.providerStatus ?? 'Not available yet'}</dd></div>
                    <div><dt>Revision</dt><dd>{current.revision}</dd></div>
                    <div><dt>Onboarding ID</dt><dd className="code">{current.id}</dd></div>
                  </dl>
                  {pollError && <p role="alert" className="error">Could not refresh: {pollError}. Your data is saved on the backend.</p>}
                  {error && <p role="alert" className="error">{error}</p>}
                  {!currentIsProcessing && !success && <form className="correction" onSubmit={correctContact}><label>Update contact email / retry<input type="email" required value={contactEmail} onChange={e => setContactEmail(e.target.value)} /></label><label className="correction-mode">Retry mode<select value={mode} onChange={e => chooseMode(e.target.value as VerificationMode)}><option value="SYNC">Sync first (fallback to async)</option><option value="ASYNC">Async only</option></select></label><button className="primary" disabled={busy}>{busy ? 'Updating…' : 'Submit new check →'}</button></form>}
                  {success && <p className="safe-note">✓ Email deliverability check passed. This does <strong>not</strong> establish identity or business ownership.</p>}
                  <div className="bottom-actions"><button className="text-button" type="button" onClick={() => void refreshCurrent(current.id)}>Refresh now</button><button className="text-button" type="button" onClick={startNew}>+ New onboarding →</button></div>
                </>
            )}
          </section>
        </main>
        <footer>SPRING BOOT <span>+</span> REACT <span>+</span> POSTGRESQL <span>+</span> HUNTER EMAIL VERIFIER</footer>
      </div>
  );
}
