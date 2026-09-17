import { useEffect, useMemo, useState } from 'react';
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

const nav = [
  ['overview', 'Overview', '⌂'],
  ['accounts', 'Accounts', '▣'],
  ['cards', 'Cards', '▤'],
  ['transfers', 'Transfers', '↗'],
  ['transactions', 'Transactions', '↕'],
  ['payments', 'Payments', '₨'],
];

export default function App() {
  const [active, setActive] = useState('overview');
  const [accounts, setAccounts] = useState([]);
  const [cards, setCards] = useState([]);
  const [transactions, setTransactions] = useState([]);
  const [selectedAccountId, setSelectedAccountId] = useState('');
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  const [paymentStatus, setPaymentStatus] = useState('');
  const [paymentAmount, setPaymentAmount] = useState('100');
  const [newAccount, setNewAccount] = useState({ holderName: '', openingBalance: '', currency: 'INR' });
  const [authForm, setAuthForm] = useState({ amount: '', merchant: '', cardId: '' });
  const [transferForm, setTransferForm] = useState({ destinationId: '', amount: '' });
  const [search, setSearch] = useState('');

  async function refreshAll() {
    try {
      setError('');
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
    e.preventDefault(); setBusy(true); setError('');
    try {
      const acc = await api.createAccount({ holderName: newAccount.holderName, openingBalance: Number(newAccount.openingBalance), currency: newAccount.currency });
      setNewAccount({ holderName: '', openingBalance: '', currency: 'INR' });
      await refreshAll();
      setSelectedAccountId(acc.id);
      setActive('accounts');
    } catch (e) { setError(e.message); } finally { setBusy(false); }
  }

  async function handleIssueCard() {
    setBusy(true); setError('');
    try { await api.issueCard(selectedAccountId); await refreshAccountDetail(selectedAccountId); setActive('cards'); }
    catch (e) { setError(e.message); } finally { setBusy(false); }
  }

  async function handleAuthorize(e) {
    e.preventDefault(); setBusy(true); setError('');
    try {
      await api.authorize({ accountId: selectedAccountId, cardId: authForm.cardId || null, amount: Number(authForm.amount), merchant: authForm.merchant });
      setAuthForm({ amount: '', merchant: '', cardId: '' });
      await refreshAll(); await refreshAccountDetail(selectedAccountId); setActive('transactions');
    } catch (e) { setError(e.message); } finally { setBusy(false); }
  }

  async function handleTransfer(e) {
    e.preventDefault(); setBusy(true); setError('');
    try {
      if (!transferForm.destinationId) throw new Error('Select a destination account');
      if (transferForm.destinationId === selectedAccountId) throw new Error('Source and destination accounts must differ');
      await api.authorize({ accountId: selectedAccountId, cardId: null, amount: Number(transferForm.amount), merchant: `Transfer to ${transferForm.destinationId.slice(0, 8)}` });
      setTransferForm({ destinationId: '', amount: '' });
      await refreshAll(); await refreshAccountDetail(selectedAccountId); setActive('transactions');
    } catch (e) { setError(e.message); } finally { setBusy(false); }
  }

  async function handleRazorpayPayment() {
    setError(''); setPaymentStatus('Creating Test Mode order...');
    try {
      const amount = Number(paymentAmount);
      if (!Number.isFinite(amount) || amount < 1) throw new Error('Enter an amount of at least ₹1');
      const order = await api.createPaymentOrder(amount);
      await loadRazorpay();
      const checkout = new window.Razorpay({
        key: order.keyId, amount: order.amount, currency: order.currency, name: 'Enterprise Platform', description: 'Razorpay Test Mode payment', order_id: order.orderId,
        theme: { color: '#2563eb' },
        handler: async (response) => {
          setPaymentStatus('Verifying Test Mode payment...');
          try {
            await api.verifyPayment({ razorpayOrderId: response.razorpay_order_id, razorpayPaymentId: response.razorpay_payment_id, razorpaySignature: response.razorpay_signature });
            setPaymentStatus(`✓ Verified test payment ${response.razorpay_payment_id}`);
          } catch (e) { setError(e.message); setPaymentStatus(''); }
        },
        modal: { ondismiss: () => setPaymentStatus('Payment window closed') },
      });
      checkout.open();
    } catch (e) { setError(e.message); setPaymentStatus(''); }
  }

  const selectedAccount = accounts.find((a) => a.id === selectedAccountId);
  const filteredTransactions = useMemo(() => {
    const q = search.trim().toLowerCase();
    if (!q) return transactions;
    return transactions.filter((t) => `${t.merchant || ''} ${t.status || ''}`.toLowerCase().includes(q));
  }, [transactions, search]);
  const totalBalance = accounts.reduce((sum, a) => sum + Number(a.balance || 0), 0);
  const approved = transactions.filter((t) => t.status === 'APPROVED').length;
  const declined = transactions.filter((t) => t.status === 'DECLINED').length;

  return (
    <div className="shell">
      <aside className="sidebar">
        <div className="brand"><div className="brand-mark">EP</div><div><strong>Enterprise</strong><span>Banking Platform</span></div></div>
        <div className="workspace"><span className="dot" /> Demo environment</div>
        <nav>{nav.map(([id, label, icon]) => <button key={id} className={active === id ? 'nav-item active' : 'nav-item'} onClick={() => setActive(id)}><span>{icon}</span>{label}</button>)}</nav>
        <div className="sidebar-bottom"><div className="security-card"><strong>Environment</strong><span>Razorpay Test Mode</span></div><div className="profile"><div className="avatar">SB</div><div><strong>Platform Admin</strong><span>Enterprise demo</span></div></div></div>
      </aside>

      <main className="main">
        <header className="topbar"><div><span className="eyebrow">Enterprise Console</span><h1>{nav.find((n) => n[0] === active)?.[1] || 'Overview'}</h1></div><div className="top-actions"><div className="search"><span>⌕</span><input placeholder="Search transactions" value={search} onChange={(e) => setSearch(e.target.value)} /></div><button className="icon-btn">◔</button><button className="icon-btn">⋮</button></div></header>
        {error && <div className="error"><strong>Action failed</strong><span>{error}</span></div>}

        {active === 'overview' && <Overview accounts={accounts} cards={cards} transactions={transactions} totalBalance={totalBalance} approved={approved} declined={declined} selectedAccount={selectedAccount} onSelect={setSelectedAccountId} onNav={setActive} />}
        {active === 'accounts' && <Accounts accounts={accounts} selectedAccountId={selectedAccountId} setSelectedAccountId={setSelectedAccountId} form={newAccount} setForm={setNewAccount} onCreate={handleCreateAccount} busy={busy} />}
        {active === 'cards' && <Cards cards={cards} selectedAccount={selectedAccount} onIssue={handleIssueCard} busy={busy} />}
        {active === 'transfers' && <Transfers accounts={accounts} selectedAccountId={selectedAccountId} setSelectedAccountId={setSelectedAccountId} form={transferForm} setForm={setTransferForm} onTransfer={handleTransfer} busy={busy} />}
        {active === 'transactions' && <Transactions transactions={filteredTransactions} search={search} setSearch={setSearch} onAuthorize={handleAuthorize} form={authForm} setForm={setAuthForm} cards={cards} busy={busy} />}
        {active === 'payments' && <Payments amount={paymentAmount} setAmount={setPaymentAmount} status={paymentStatus} onPay={handleRazorpayPayment} />}
      </main>
    </div>
  );
}

function Stat({ label, value, meta, tone = '' }) {
  return <div className="stat"><div className="stat-top"><span>{label}</span><i className={tone}>•</i></div><strong>{value}</strong><small>{meta}</small></div>;
}

function Overview({ accounts, cards, transactions, totalBalance, approved, declined, selectedAccount, onSelect, onNav }) {
  const recent = transactions.slice(0, 5);
  return <div className="content-stack">
    <section className="hero"><div><div className="hero-badge">LIVE DEMO · CORE BANKING</div><h2>Financial operations at a glance.</h2><p>Manage accounts, cards, transaction authorization, money movement and sandbox payments from one control center.</p></div><button className="primary" onClick={() => onNav('transfers')}>Make a transfer ↗</button></section>
    <div className="stats-grid"><Stat label="Total balance" value={`₹${totalBalance.toLocaleString('en-IN', { minimumFractionDigits: 2 })}`} meta={`${accounts.length} account${accounts.length === 1 ? '' : 's'}`} tone="blue" /><Stat label="Cards issued" value={cards.length} meta="Synthetic demo cards" tone="purple" /><Stat label="Approved txns" value={approved} meta="Successful authorizations" tone="green" /><Stat label="Declined txns" value={declined} meta="Reviewable outcomes" tone="red" /></div>
    <div className="two-col"><section className="panel"><div className="panel-head"><div><h3>Accounts</h3><span>Balances and account status</span></div><button className="ghost" onClick={() => onNav('accounts')}>View all</button></div><div className="account-table">{accounts.length === 0 ? <Empty title="No accounts yet" text="Open an account from the Accounts section." /> : accounts.map((a) => <button className={`account-row ${a.id === selectedAccount?.id ? 'selected' : ''}`} key={a.id} onClick={() => onSelect(a.id)}><div className="account-icon">{a.currency === 'INR' ? '₹' : '$'}</div><div className="row-main"><strong>{a.holderName}</strong><span>{a.accountNumber || `ACC-${a.id.slice(0, 8).toUpperCase()}`}</span></div><div className="row-value"><strong>{a.currency} {Number(a.balance).toLocaleString('en-IN', { minimumFractionDigits: 2 })}</strong><span className="status active">{a.status}</span></div></button>)}</div></section><section className="panel"><div className="panel-head"><div><h3>Recent activity</h3><span>Latest authorization outcomes</span></div><button className="ghost" onClick={() => onNav('transactions')}>View all</button></div>{recent.length === 0 ? <Empty title="No transactions" text="Authorized transactions will appear here." /> : <div className="activity">{recent.map((t) => <div className="activity-row" key={t.id}><div className="activity-icon">{t.status === 'APPROVED' ? '✓' : '!'}</div><div><strong>{t.merchant || 'Account transaction'}</strong><span>{new Date(t.createdAt).toLocaleString()}</span></div><div className="activity-amount"><strong>{t.currency} {Number(t.amount).toFixed(2)}</strong><span className={t.status === 'APPROVED' ? 'positive' : 'negative'}>{t.status}</span></div></div>)}</div>}</section></div>
  </div>;
}

function Accounts({ accounts, selectedAccountId, setSelectedAccountId, form, setForm, onCreate, busy }) {
  return <div className="content-stack"><section className="panel"><div className="panel-head"><div><h3>Account directory</h3><span>Core banking accounts stored in PostgreSQL</span></div><span className="count-pill">{accounts.length} accounts</span></div><div className="cards-grid">{accounts.map((a) => <div key={a.id} className={`large-account ${a.id === selectedAccountId ? 'selected-card' : ''}`} onClick={() => setSelectedAccountId(a.id)}><div className="large-top"><span className="kicker">{a.currency} · RETAIL</span><span className="status active">{a.status}</span></div><strong className="large-name">{a.holderName}</strong><span className="account-number">{a.accountNumber || `ACC-${a.id.slice(0, 8).toUpperCase()}`}</span><div className="balance-label">Available balance</div><div className="balance">{a.currency} {Number(a.balance).toLocaleString('en-IN', { minimumFractionDigits: 2 })}</div></div>)}{accounts.length === 0 && <Empty title="No accounts" text="Create the first account below." />}</div></section><section className="panel form-panel"><div className="panel-head"><div><h3>Open account</h3><span>Creates a new active customer account</span></div></div><form onSubmit={onCreate} className="form-grid"><label>Holder name<input value={form.holderName} onChange={(e) => setForm({ ...form, holderName: e.target.value })} required placeholder="e.g. Sujay Babu" /></label><label>Opening balance<input type="number" min="0" step="0.01" value={form.openingBalance} onChange={(e) => setForm({ ...form, openingBalance: e.target.value })} required placeholder="10000" /></label><label>Currency<select value={form.currency} onChange={(e) => setForm({ ...form, currency: e.target.value })}><option>INR</option><option>USD</option><option>EUR</option></select></label><div className="form-end"><button className="primary" disabled={busy}>{busy ? 'Creating…' : 'Create account'}</button></div></form></section></div>;
}

function Cards({ cards, selectedAccount, onIssue, busy }) {
  return <div className="content-stack"><section className="panel"><div className="panel-head"><div><h3>Card management</h3><span>{selectedAccount ? `Cards for ${selectedAccount.holderName}` : 'Select an account to manage cards'}</span></div><button className="primary" onClick={onIssue} disabled={!selectedAccount || busy}>{busy ? 'Issuing…' : 'Issue card'}</button></div><div className="card-showcase">{cards.length === 0 ? <Empty title="No cards issued" text="Issue a synthetic card for the selected account." /> : cards.map((c) => <div className="bank-card" key={c.id}><div className="chip" /><span className="card-brand">EP / PAYMENTS</span><strong className="card-number">•••• •••• •••• {c.last4}</strong><div className="card-meta"><div><small>VALID THRU</small><strong>{c.expiry}</strong></div><div><small>STATUS</small><strong>{c.status}</strong></div></div></div>)}</div></section></div>;
}

function Transfers({ accounts, selectedAccountId, setSelectedAccountId, form, setForm, onTransfer, busy }) {
  return <div className="content-stack"><section className="panel transfer-panel"><div className="transfer-head"><div className="transfer-icon">↗</div><div><h2>Internal transfer</h2><p>Move funds between demo accounts. This screen currently uses the platform’s authorization endpoint.</p></div></div><form onSubmit={onTransfer} className="form-grid"><label>Source account<select value={selectedAccountId} onChange={(e) => setSelectedAccountId(e.target.value)} required><option value="">Select source</option>{accounts.map((a) => <option key={a.id} value={a.id}>{a.holderName} · {a.currency} {Number(a.balance).toFixed(2)}</option>)}</select></label><label>Destination account<select value={form.destinationId} onChange={(e) => setForm({ ...form, destinationId: e.target.value })} required><option value="">Select destination</option>{accounts.filter((a) => a.id !== selectedAccountId).map((a) => <option key={a.id} value={a.id}>{a.holderName} · {a.currency} {Number(a.balance).toFixed(2)}</option>)}</select></label><label>Amount<input type="number" min="0.01" step="0.01" value={form.amount} onChange={(e) => setForm({ ...form, amount: e.target.value })} required placeholder="2500.00" /></label><div className="form-end"><button className="primary" disabled={busy || accounts.length < 2}>{busy ? 'Processing…' : 'Continue transfer ↗'}</button></div></form></section></div>;
}

function Transactions({ transactions, search, setSearch, onAuthorize, form, setForm, cards, busy }) {
  return <div className="content-stack"><section className="panel"><div className="panel-head"><div><h3>Transaction monitor</h3><span>Authorization decisions and outcomes</span></div><div className="toolbar"><input placeholder="Filter merchant/status" value={search} onChange={(e) => setSearch(e.target.value)} /></div></div><div className="table-wrap"><table><thead><tr><th>Merchant</th><th>Amount</th><th>Status</th><th>Time</th></tr></thead><tbody>{transactions.length === 0 ? <tr><td colSpan="4"><Empty title="No matching transactions" text="Try a different search." /></td></tr> : transactions.map((t) => <tr key={t.id}><td><strong>{t.merchant || 'Account transaction'}</strong></td><td>{t.currency} {Number(t.amount).toFixed(2)}</td><td><span className={`status ${t.status === 'APPROVED' ? 'active' : 'declined'}`}>{t.status}</span></td><td>{new Date(t.createdAt).toLocaleString()}</td></tr>)}</tbody></table></div></section><section className="panel form-panel"><div className="panel-head"><div><h3>Authorize transaction</h3><span>Run a card or account-level authorization</span></div></div><form onSubmit={onAuthorize} className="form-grid"><label>Card<select value={form.cardId} onChange={(e) => setForm({ ...form, cardId: e.target.value })}><option value="">Account-level</option>{cards.map((c) => <option key={c.id} value={c.id}>•••• {c.last4}</option>)}</select></label><label>Merchant<input value={form.merchant} onChange={(e) => setForm({ ...form, merchant: e.target.value })} placeholder="Merchant name" /></label><label>Amount<input type="number" min="0.01" step="0.01" value={form.amount} onChange={(e) => setForm({ ...form, amount: e.target.value })} required placeholder="500.00" /></label><div className="form-end"><button className="primary" disabled={busy}>{busy ? 'Authorizing…' : 'Authorize transaction'}</button></div></form></section></div>;
}

function Payments({ amount, setAmount, status, onPay }) {
  return <div className="content-stack"><section className="panel payment-panel"><div className="payment-art">₨</div><div><span className="eyebrow">Gateway integration</span><h2>Razorpay Test Mode</h2><p>Sandbox checkout is wired to the Spring Boot backend. No real money is charged.</p></div><div className="payment-form"><label>Amount in INR<input type="number" min="1" step="0.01" value={amount} onChange={(e) => setAmount(e.target.value)} /></label><button className="primary" onClick={onPay}>Open secure checkout ↗</button></div>{status && <div className="success-box">{status}</div>}</section></div>;
}

function Empty({ title, text }) { return <div className="empty"><div className="empty-icon">○</div><h4>{title}</h4><p>{text}</p></div>; }
