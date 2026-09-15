import { useEffect, useState } from 'react';
import { api } from './api.js';

function loadRazorpay() {
  return new Promise((resolve, reject) => {
    if (window.Razorpay) return resolve(true);
    const script = document.createElement('script');
    script.src = 'https://checkout.razorpay.com/v1/checkout.js';
    script.onload = () => resolve(true);
    script.onerror = () => reject(new Error('Could not load Razorpay Checkout'));
    document.body.appendChild(script);
  });
}

export default function App() {
  const [accounts, setAccounts] = useState([]);
  const [cards, setCards] = useState([]);
  const [transactions, setTransactions] = useState([]);
  const [selectedAccountId, setSelectedAccountId] = useState('');
  const [error, setError] = useState('');
  const [paymentStatus, setPaymentStatus] = useState('');
  const [paymentAmount, setPaymentAmount] = useState('100');

  const [newAccount, setNewAccount] = useState({ holderName: '', openingBalance: '' });
  const [authForm, setAuthForm] = useState({ amount: '', merchant: '', cardId: '' });

  async function refreshAll() {
    try {
      const acc = await api.listAccounts();
      setAccounts(acc);
      if (!selectedAccountId && acc.length) setSelectedAccountId(acc[0].id);
    } catch (e) { setError(e.message); }
  }

  async function refreshAccountDetail(accountId) {
    if (!accountId) return;
    try {
      const [c, t] = await Promise.all([api.listCards(accountId), api.listTransactions(accountId)]);
      setCards(c); setTransactions(t);
    } catch (e) { setError(e.message); }
  }

  useEffect(() => { refreshAll(); }, []);
  useEffect(() => { refreshAccountDetail(selectedAccountId); }, [selectedAccountId]);

  async function handleCreateAccount(e) {
    e.preventDefault(); setError('');
    try {
      const acc = await api.createAccount({ holderName: newAccount.holderName, openingBalance: parseFloat(newAccount.openingBalance) });
      setNewAccount({ holderName: '', openingBalance: '' }); await refreshAll(); setSelectedAccountId(acc.id);
    } catch (e) { setError(e.message); }
  }

  async function handleIssueCard() {
    setError('');
    try { await api.issueCard(selectedAccountId); await refreshAccountDetail(selectedAccountId); }
    catch (e) { setError(e.message); }
  }

  async function handleAuthorize(e) {
    e.preventDefault(); setError('');
    try {
      await api.authorize({ accountId: selectedAccountId, cardId: authForm.cardId || null, amount: parseFloat(authForm.amount), merchant: authForm.merchant });
      setAuthForm({ amount: '', merchant: '', cardId: '' }); await refreshAll(); await refreshAccountDetail(selectedAccountId);
    } catch (e) { setError(e.message); }
  }

  async function handleRazorpayPayment() {
    setError(''); setPaymentStatus('Creating Test Mode order...');
    try {
      const amount = Number(paymentAmount);
      if (!Number.isFinite(amount) || amount < 1) throw new Error('Enter an amount of at least ₹1');
      const order = await api.createPaymentOrder(amount);
      await loadRazorpay();
      const checkout = new window.Razorpay({
        key: order.keyId,
        amount: order.amount,
        currency: order.currency,
        name: 'Enterprise Platform',
        description: 'Razorpay Test Mode payment',
        order_id: order.orderId,
        theme: { color: '#2563eb' },
        handler: async (response) => {
          setPaymentStatus('Verifying Test Mode payment...');
          try {
            await api.verifyPayment({
              razorpayOrderId: response.razorpay_order_id,
              razorpayPaymentId: response.razorpay_payment_id,
              razorpaySignature: response.razorpay_signature,
            });
            setPaymentStatus(`✓ Test payment verified: ${response.razorpay_payment_id}`);
          } catch (e) { setError(e.message); setPaymentStatus(''); }
        },
        modal: { ondismiss: () => setPaymentStatus('Payment window closed') },
      });
      checkout.open();
    } catch (e) { setError(e.message); setPaymentStatus(''); }
  }

  const selectedAccount = accounts.find((a) => a.id === selectedAccountId);

  return (
    <div className="app">
      <header>
        <h1>Enterprise Platform</h1>
        <p className="subtitle">Accounts, cards, transaction authorization and payments</p>
      </header>

      {error && <div className="error">{error}</div>}

      <section className="panel">
        <h2>💳 Razorpay Test Mode</h2>
        <p className="subtitle">Sandbox payment only — no real money is charged.</p>
        <div className="inline-form">
          <input type="number" min="1" step="0.01" value={paymentAmount} onChange={(e) => setPaymentAmount(e.target.value)} placeholder="Amount (INR)" />
          <button onClick={handleRazorpayPayment}>Pay with Razorpay Test Mode</button>
        </div>
        {paymentStatus && <p>{paymentStatus}</p>}
      </section>

      <section className="panel">
        <h2>Accounts</h2>
        <div className="account-list">
          {accounts.map((a) => (
            <button key={a.id} className={`account-chip ${a.id === selectedAccountId ? 'selected' : ''}`} onClick={() => setSelectedAccountId(a.id)}>
              {a.holderName} — {a.currency} {Number(a.balance).toFixed(2)}
            </button>
          ))}
        </div>
        <form onSubmit={handleCreateAccount} className="inline-form">
          <input placeholder="Holder name" value={newAccount.holderName} onChange={(e) => setNewAccount({ ...newAccount, holderName: e.target.value })} required />
          <input placeholder="Opening balance" type="number" step="0.01" value={newAccount.openingBalance} onChange={(e) => setNewAccount({ ...newAccount, openingBalance: e.target.value })} required />
          <button type="submit">Create account</button>
        </form>
      </section>

      {selectedAccount && (
        <>
          <section className="panel">
            <h2>Cards</h2>
            <button onClick={handleIssueCard}>Issue new card</button>
            <ul className="card-list">{cards.map((c) => <li key={c.id}>•••• {c.last4} exp {c.expiry} — {c.status}</li>)}</ul>
          </section>

          <section className="panel">
            <h2>Authorize a transaction</h2>
            <form onSubmit={handleAuthorize} className="inline-form">
              <select value={authForm.cardId} onChange={(e) => setAuthForm({ ...authForm, cardId: e.target.value })}>
                <option value="">No card (account-level)</option>
                {cards.map((c) => <option key={c.id} value={c.id}>•••• {c.last4}</option>)}
              </select>
              <input placeholder="Merchant" value={authForm.merchant} onChange={(e) => setAuthForm({ ...authForm, merchant: e.target.value })} />
              <input placeholder="Amount" type="number" step="0.01" value={authForm.amount} onChange={(e) => setAuthForm({ ...authForm, amount: e.target.value })} required />
              <button type="submit">Authorize</button>
            </form>
          </section>

          <section className="panel">
            <h2>Transaction history</h2>
            <table><thead><tr><th>Time</th><th>Merchant</th><th>Amount</th><th>Status</th></tr></thead><tbody>
              {transactions.map((t) => <tr key={t.id} className={t.status === 'DECLINED' ? 'declined' : ''}><td>{new Date(t.createdAt).toLocaleString()}</td><td>{t.merchant || '—'}</td><td>{t.currency} {Number(t.amount).toFixed(2)}</td><td>{t.status}{t.declineReason ? ` (${t.declineReason})` : ''}</td></tr>)}
            </tbody></table>
          </section>
        </>
      )}
    </div>
  );
}
