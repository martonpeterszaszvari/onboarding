import { useCallback, useEffect, useState, type FormEvent } from 'react';
import { api, type CreateOnboarding, type Onboarding, type OnboardingStatus } from './api';

const STORAGE_KEY = 'onboarding-demo-id';
const INITIAL: CreateOnboarding = {
  adminName: 'Alex Smith',
  adminEmail: 'delay@acme.com',
  companyName: 'Acme Ltd',
  companyDomain: 'acme.com'
};
const IS_PROCESSING = (status: OnboardingStatus) => status === 'PENDING' || status === 'VERIFYING';

const SCENARIOS = [
  { label: 'Instant success', email: 'success@acme.com', note: 'valid' },
  { label: 'Delayed (202)', email: 'delay@acme.com', note: 'pending → success' },
  { label: 'Transient error', email: 'flaky@acme.com', note: '503 → retry' },
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
      const record = await api.create(form);
      localStorage.setItem(STORAGE_KEY, record.id);
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
      const record = await api.correct(current.id, contactEmail);
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
          <p>Register a company, then follow a durable background job while an external email verifier finishes its work.</p>
          <ol className="step-list">
            <li className="step active-step"><span className="step-number">01</span><span><strong>Company details</strong><small>Collect admin and business information</small></span></li>
            <li className={`step ${current ? 'active-step' : ''}`}><span className="step-number">02</span><span><strong>Contact verification</strong><small>Asynchronous Hunter API integration</small></span></li>
            <li className={`step ${success ? 'active-step' : ''}`}><span className="step-number">03</span><span><strong>Continue onboarding</strong><small>Not in scope for this prototype</small></span></li>
          </ol>
          <div className="tech-note"><span>WHY ASYNC?</span><p>A provider may respond with HTTP 202, time out, or rate limit requests. Verification state is stored in PostgreSQL, not in React or application memory.</p></div>
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
                {error && <p className="error" role="alert">{error}</p>}
                <button className="primary" disabled={busy}>{busy ? 'Submitting…' : 'Register and verify →'}</button>
              </form>
              <div className="scenario-box"><span className="eyebrow">MOCK INTEGRATION · CHOOSE A TEST CASE</span><div className="scenario-grid">
                {SCENARIOS.map(item => <button key={item.email} type="button" className={`scenario ${form.adminEmail === item.email ? 'selected' : ''}`} onClick={() => setForm({ ...form, adminEmail: item.email })}><strong>{item.label}</strong><small>{item.note}</small></button>)}
              </div></div>
            </>
          ) : (
            <>
              <div className="panel-heading"><span className="eyebrow">STEP 02 / 02</span><h2>Contact verification</h2><p>Your application has been accepted. You can refresh or return later without losing progress.</p></div>
              <div className={`outcome ${success ? 'success' : active ? 'working' : 'attention'}`}>
                <div className="outcome-header"><span className="outcome-icon">{success ? '✓' : active ? '↻' : '!'}</span><div><span className="eyebrow">CURRENT STATUS</span><h3>{STATUS_LABELS[current.status]}</h3></div></div>
                <p>{current.statusMessage}</p>
                {active && <div className="activity"><span className="spinner" /> Polling status every 2.5 seconds</div>}
              </div>
              <dl className="details">
                <div><dt>Company</dt><dd>{current.companyName}</dd></div>
                <div><dt>Business domain</dt><dd>{current.companyDomain}</dd></div>
                <div><dt>Email</dt><dd>{current.adminEmail}</dd></div>
                <div><dt>Hunter status</dt><dd>{current.providerStatus || 'Not available yet'}</dd></div>
                <div><dt>Verification revision</dt><dd>{current.revision}</dd></div>
                <div><dt>Onboarding ID</dt><dd className="code">{current.id}</dd></div>
              </dl>
              {pollError && <p className="error" role="alert">Could not refresh: {pollError}. Your data is still stored on the server.</p>}
              {error && <p className="error" role="alert">{error}</p>}
              {!active && !success && <form className="correction" onSubmit={correctContact}><label>Update contact email / retry<input type="email" required value={contactEmail} onChange={e => setContactEmail(e.target.value)} /></label><button className="primary" disabled={busy}>{busy ? 'Updating…' : 'Submit new check →'}</button></form>}
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
