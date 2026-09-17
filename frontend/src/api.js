const API_URL = import.meta.env.VITE_API_URL || '';

async function request(path, options = {}) {
  const res = await fetch(`${API_URL}${path}`, {
    headers: { 'Content-Type': 'application/json' },
    ...options,
  });
  if (!res.ok) {
    const body = await res.json().catch(() => ({}));
    throw new Error(body.error || `Request failed (${res.status})`);
  }
  return res.json();
}

export const api = {
  listAccounts: () => request('/api/accounts'),
  createAccount: (data) => request('/api/accounts', { method: 'POST', body: JSON.stringify(data) }),
  listCards: (accountId) => request(`/api/cards${accountId ? `?accountId=${accountId}` : ''}`),
  issueCard: (accountId) => request('/api/cards', { method: 'POST', body: JSON.stringify({ accountId }) }),
  blockCard: (id) => request(`/api/cards/${id}/block`, { method: 'POST' }),
  listTransactions: (accountId) => request(`/api/transactions${accountId ? `?accountId=${accountId}` : ''}`),
  authorize: (data) => request('/api/transactions/authorize', { method: 'POST', body: JSON.stringify(data) }),
  createPaymentOrder: (amount) => request('/api/payments/order', { method: 'POST', body: JSON.stringify({ amount }) }),
  verifyPayment: (data) => request('/api/payments/verify', { method: 'POST', body: JSON.stringify(data) }),
};
