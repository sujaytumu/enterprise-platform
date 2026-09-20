const API_URL = import.meta.env.VITE_API_URL || '';
const TOKEN_KEY = 'ep_token';

export function getToken() { return localStorage.getItem(TOKEN_KEY); }
export function setToken(token) { localStorage.setItem(TOKEN_KEY, token); }
export function clearToken() { localStorage.removeItem(TOKEN_KEY); }

async function request(path, options = {}) {
  const token = getToken();
  const res = await fetch(`${API_URL}${path}`, {
    headers: {
      'Content-Type': 'application/json',
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
    },
    ...options,
  });
  if (res.status === 401) {
    clearToken();
    window.dispatchEvent(new Event('ep-unauthorized'));
  }
  if (!res.ok) {
    const body = await res.json().catch(() => ({}));
    throw new Error(body.error || `Request failed (${res.status})`);
  }
  if (res.status === 204) return null;
  return res.json();
}

export const api = {
  signup: (data) => request('/api/auth/signup', { method: 'POST', body: JSON.stringify(data) }),
  login: (data) => request('/api/auth/login', { method: 'POST', body: JSON.stringify(data) }),
  listAccounts: () => request('/api/accounts'),
  createAccount: (data) => request('/api/accounts', { method: 'POST', body: JSON.stringify(data) }),
  listCards: (accountId) => request(`/api/cards${accountId ? `?accountId=${accountId}` : ''}`),
  issueCard: (accountId) => request('/api/cards', { method: 'POST', body: JSON.stringify({ accountId }) }),
  blockCard: (id) => request(`/api/cards/${id}/block`, { method: 'POST' }),
  listTransactions: (accountId) => request(`/api/transactions${accountId ? `?accountId=${accountId}` : ''}`),
  authorize: (data) => request('/api/transactions/authorize', { method: 'POST', body: JSON.stringify(data) }),
  transfer: (data) => request('/api/transactions/transfer', { method: 'POST', body: JSON.stringify(data) }),
  listLedgerEntries: (accountId) => request(`/api/ledger/entries${accountId ? `?accountId=${accountId}` : ''}`),
  createPaymentOrder: (amount, accountId) => request('/api/payments/order', { method: 'POST', body: JSON.stringify({ amount, accountId }) }),
  verifyPayment: (data) => request('/api/payments/verify', { method: 'POST', body: JSON.stringify(data) }),
};
