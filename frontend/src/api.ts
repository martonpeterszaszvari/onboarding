export type VerificationMode = 'SYNC' | 'ASYNC';

export type OnboardingStatus =
    | 'PENDING'
    | 'VERIFYING'
    | 'VERIFIED'
    | 'ACTION_REQUIRED'
    | 'REVIEW_REQUIRED'
    | 'TEMPORARILY_UNAVAILABLE';

export type Onboarding = {
  id: string;
  adminName: string;
  adminEmail: string;
  companyName: string;
  companyDomain: string;
  status: OnboardingStatus;
  providerStatus: string | null;
  statusMessage: string;
  revision: number;
  createdAt: string;
  updatedAt: string;
};

export type CreateOnboarding = Pick<Onboarding,
    'adminName' | 'adminEmail' | 'companyName' | 'companyDomain'>;

async function request<T>(url: string, init?: RequestInit): Promise<T> {
  const res = await fetch(url, {
    ...init,
    headers: { 'Content-Type': 'application/json', ...init?.headers }
  });
  if (!res.ok) {
    const body: { error?: string; detail?: string; message?: string } =
        await res.json().catch(() => ({}));
    throw new Error(body.error || body.detail || body.message || `Request failed (${res.status})`);
  }
  return res.json() as Promise<T>;
}

export const api = {
  create: (payload: CreateOnboarding, mode: VerificationMode) =>
      request<Onboarding>(`/api/onboardings?mode=${mode}`, { method: 'POST', body: JSON.stringify(payload) }),
  get: (id: string) => request<Onboarding>(`/api/onboardings/${id}`),
  list: () => request<Onboarding[]>('/api/onboardings?limit=50'),
  correct: (id: string, adminEmail: string, mode: VerificationMode) =>
      request<Onboarding>(`/api/onboardings/${id}/contact?mode=${mode}`, {
        method: 'PATCH', body: JSON.stringify({ adminEmail })
      })
};
