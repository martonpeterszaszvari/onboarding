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

export default function App() {
  const [form, setForm] = useState<CreateOnboarding>(INITIAL);
  const [mode, setMode] = useState<VerificationMode>(() =>
      localStorage.getItem(MODE_KEY) === 'ASYNC' ? 'ASYNC' : 'SYNC');
  const [onboardingId, setOnboardingId] = useState<string | null>(() => localStorage.getItem(STORAGE_KEY));
  const [current, setCurrent] = useState<Onboarding | null>(null);
  const [contactEmail, setContactEmail] = useState('');
  const [busy, setBusy] = useState(false);
  const [loading, setLoading] = useState(Boolean(localStorage.getItem(STORAGE_KEY)));
  const [error, setError] = useState<string | null>(null);
  const [pollError, setPollError] = useState<string | null>(null);

  const refresh = useCallback(async (id: string) => {
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

  useEffect(() => {
    if (!onboardingId) return;
    void refresh(onboardingId);
    // Polling is intentionally simple; the backend never pushes verification updates.
    if (current && !IS_PROCESSING(current.status)) return;
    const timer = window.setInterval(() => { void refresh(onboardingId); }, 2500);
    return () => window.clearInterval(timer);
  }, [onboardingId, current?.status, refresh]);

  async function start(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setBusy(true);
    setError(null);
    try {
      const record = await api.create(form, mode);
      localStorage.setItem(STORAGE_KEY, record.id);
      localStorage.setItem(MODE_KEY, mode);
      setCurrent(record);
      setContactEmail(record.adminEmail);
      setOnboardingId(record.id);
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
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Unable to update contact');
    } finally {
      setBusy(false);
    }
  }

  function reset() {
    localStorage.removeItem(STORAGE_KEY);
    setOnboardingId(null);
    setCurrent(null);
    setError(null);
    setPollError(null);
    setForm(INITIAL);
  }

  const active = current && IS_PROCESSING(current.status);
  const success = current?.status === 'VERIFIED';
  return (
      <div className="shell">
        <header className="topbar">
          <div className="brand"><span className="brand-icon">◈</span> <span>Northstar</span> <small>/ onboarding lab</small></div>
          <span className="environment"><span className="status-dot" /> MOCK PROVIDER ENABLED BY DEFAULT</span>
        </header>
        <main className="main-grid">
          <aside className="sidebar">
            <span className="eyebrow">A BACKEND ENGINEERING EXERCISE</span>
            <h1>Onboard your business.<br /><em>Verify with confidence.</em></h1>
            <p>Choose a quick verification attempt or queue it immediately. Both paths keep unfinished checks durable in PostgreSQL.</p>
            <ol className="step-list">
              <li className="step active-step"><span className="step-number">01</span><span><strong>Company details</strong><small>Collect admin and business information</small></span></li>
              <li className={`step ${current ? 'active-step' : ''}`}><span className="step-number">02</span><span><strong>Contact verification</strong><small>Synchronous-first or queued processing</small></span></li>
              <li className={`step ${success ? 'active-step' : ''}`}><span className="step-number">03</span><span><strong>Continue onboarding</strong><small>Not in scope for this prototype</small></span></li>
            </ol>
            <div className="tech-note"><span>TWO EXECUTION MODES</span><p>Sync first: call Hunter on the request thread, then fall back if unfinished. Async: return as soon as the database job is committed. Both use the same worker for retries.</p></div>
          </aside>
          <section className="panel" aria-live="polite">
            {loading && !current ? (
                <div className="loading"><span className="spinner" /> Loading saved onboarding…</div>
            ) : !current ? (
                <>
                  <div className="panel-heading"><span className="eyebrow">STEP 01 / 02</span><h2>Set up your company</h2><p>Everything starts with a company admin and their business email.</p></div>
                  <form onSubmit={start} className="entry-form">
                    <label>Admin full name<input required maxLength={120} value={form.adminName} onChange={e => setForm({ ...form, adminName: e.target.value })} /></label>
                    <label>Admin business email<input required type="email" maxLength={255} value={form.adminEmail} onChange={e => setForm({ ...form, adminEmail: e.target.value })} /></label>
                    <label>Company name<input required maxLength={160} value={form.companyName} onChange={e => setForm({ ...form, companyName: e.target.value })} /></label>
                    <label>Company domain<input required placeholder="acme.com" maxLength={255} value={form.companyDomain} onChange={e => setForm({ ...form, companyDomain: e.target.value })} /></label>
                    <fieldset className="mode-selector" disabled={busy}>
                      <legend>How should verification run?</legend>
                      <label className={`mode-option ${mode === 'SYNC' ? 'selected' : ''}`}>
                        <input type="radio" name="verification-mode" checked={mode === 'SYNC'} onChange={() => setMode('SYNC')} />
                        <span><strong>Sync first</strong><small>Try Hunter now (short timeout). Unfinished checks move to the background.</small></span>
                      </label>
                      <label className={`mode-option ${mode === 'ASYNC' ? 'selected' : ''}`}>
                        <input type="radio" name="verification-mode" checked={mode === 'ASYNC'} onChange={() => setMode('ASYNC')} />
                        <span><strong>Async</strong><small>Save a job and return immediately; the worker calls Hunter later.</small></span>
                      </label>
                    </fieldset>
                    {error && <p className="error" role="alert">{error}</p>}
                    <button className="primary" disabled={busy}>{busy ? (mode === 'SYNC' ? 'Checking Hunter…' : 'Queuing…') : mode === 'SYNC' ? 'Register & verify now →' : 'Register & queue verification →'}</button>
                  </form>
                  <div className="scenario-box"><span className="eyebrow">MOCK INTEGRATION · CHOOSE A TEST CASE</span><div className="scenario-grid">
                    {SCENARIOS.map(item => <button key={item.email} type="button" className={`scenario ${form.adminEmail === item.email ? 'selected' : ''}`} onClick={() => setForm({ ...form, adminEmail: item.email })}><strong>{item.label}</strong><small>{item.note}</small></button>)}
                  </div></div>
                </>
            ) : (
                <>
                  <div className="panel-heading"><span className="eyebrow">STEP 02 / 02</span><h2>Contact verification</h2><p>{mode === 'SYNC' ? 'Sync first was selected: completed checks returned immediately; unfinished ones continue as jobs.' : 'Async was selected: the background worker handles verification.'} You can return later without losing progress.</p></div>
                  <div className={`outcome ${success ? 'success' : active ? 'working' : 'attention'}`}>
                    <div className="outcome-header"><span className="outcome-icon">{success ? '✓' : active ? '↻' : '!'}</span><div><span className="eyebrow">CURRENT STATUS</span><h3>{STATUS_LABELS[current.status]}</h3></div></div>
                    <p>{current.statusMessage}</p>
                    {active && <div className="activity"><span className="spinner" /> Polling status every 2.5 seconds</div>}
                  </div>
                  <dl className="details">
                    <div><dt>Requested mode</dt><dd>{mode === 'SYNC' ? 'Sync first (with fallback)' : 'Async (queued)'}</dd></div>
                    <div><dt>Company</dt><dd>{current.companyName}</dd></div>
                    <div><dt>Business domain</dt><dd>{current.companyDomain}</dd></div>
                    <div><dt>Email</dt><dd>{current.adminEmail}</dd></div>
                    <div><dt>Hunter status</dt><dd>{current.providerStatus || 'Not available yet'}</dd></div>
                    <div><dt>Verification revision</dt><dd>{current.revision}</dd></div>
                    <div><dt>Onboarding ID</dt><dd className="code">{current.id}</dd></div>
                  </dl>
                  {pollError && <p className="error" role="alert">Could not refresh: {pollError}. Your data is still stored on the server.</p>}
                  {error && <p className="error" role="alert">{error}</p>}
                  {!active && !success && <form className="correction" onSubmit={correctContact}><label>Update contact email / retry<input type="email" required value={contactEmail} onChange={e => setContactEmail(e.target.value)} /></label><label className="correction-mode">Retry mode<select value={mode} onChange={e => { const next = e.target.value as VerificationMode; setMode(next); localStorage.setItem(MODE_KEY, next); }}><option value="SYNC">Sync first (fallback to async)</option><option value="ASYNC">Async only</option></select></label><button className="primary" disabled={busy}>{busy ? 'Updating…' : 'Submit new check →'}</button></form>}
                  {success && <p className="safe-note">✓ Contact deliverability check passed. This is <strong>not</strong> identity, email ownership, or business-authority verification.</p>}
                  <div className="bottom-actions"><button className="text-button" onClick={() => void refresh(current.id)}>Refresh now</button><button className="text-button" onClick={reset}>Start a new onboarding ↗</button></div>
                </>
            )}
          </section>
        </main>
        <footer>SPRING BOOT <span>+</span> REACT <span>+</span> POSTGRESQL <span>+</span> HUNTER EMAIL VERIFIER</footer>
      </div>
  );
}
