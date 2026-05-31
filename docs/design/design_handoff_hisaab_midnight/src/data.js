/* ============================================================
   Hisaab — mock data + formatting helpers (window-scoped)
   ============================================================ */
(function () {
  // ---- money formatting ----
  function group(n) {
    return Math.abs(Math.round(n)).toLocaleString('en-US');
  }
  // ৳ = Bengali taka mark (U+09F3)
  function taka(n, opts) {
    opts = opts || {};
    var v = group(n);
    var s = '৳' + v;
    if (opts.sign) {
      if (n > 0) return '+' + s;
      if (n < 0) return '−' + s;
    }
    if (n < 0 && !opts.sign) return '−' + s;
    return s;
  }

  // ---- accounts ----
  var accounts = [
    { id: 'a_cash', name: 'Cash',          kind: 'CASH', currency: 'BDT' },
    { id: 'a_brac', name: 'BRAC Bank',     kind: 'BANK', currency: 'BDT' },
    { id: 'a_visa', name: 'City Bank Visa', kind: 'CARD', currency: 'BDT',
      creditLimit: 150000, outstanding: 1250, available: 148750, dueDate: '15 Jun 2026', statementDay: 5, dueDay: 15 },
    { id: 'a_bkash', name: 'bKash',        kind: 'MFS', currency: 'BDT' },
  ];

  // ---- categories ----
  var categories = [
    { id: 'c_bills',    name: 'Bills',         glyph: 'receipt',  color: '#7A5BB8', isDefault: true },
    { id: 'c_borrowed', name: 'Borrowed',      glyph: 'in',       color: '#8a7d68', isDefault: true },
    { id: 'c_education', name: 'Education',     glyph: 'book',     color: '#3F7CC2', isDefault: true },
    { id: 'c_ent',      name: 'Entertainment', glyph: 'film',     color: '#C24A8f', isDefault: true },
    { id: 'c_food',     name: 'Food & dining', glyph: 'food',     color: '#C24A3C', isDefault: true },
    { id: 'c_groc',     name: 'Groceries',     glyph: 'cart',     color: '#2F7D52', isDefault: false },
    { id: 'c_health',   name: 'Health',        glyph: 'health',   color: '#3FA0A0', isDefault: true },
    { id: 'c_lent',     name: 'Lent',          glyph: 'out',      color: '#8a7d68', isDefault: true },
    { id: 'c_other',    name: 'Other',         glyph: 'dot',      color: '#9C9384', isDefault: true },
    { id: 'c_salary',   name: 'Salary',        glyph: 'case',     color: '#2F7D52', isDefault: true },
    { id: 'c_shop',     name: 'Shopping',      glyph: 'bag',      color: '#C68A3C', isDefault: true },
    { id: 'c_transfer', name: 'Transfer',      glyph: 'swap',     color: '#6C6356', isDefault: true },
    { id: 'c_transport', name: 'Transport',    glyph: 'car',      color: '#3F7CC2', isDefault: true },
  ];

  // ---- today's transactions ----
  var transactions = [
    { id: 't1', merchant: 'Shwapno Supermarket', accountId: 'a_visa',  categoryId: 'c_groc',     amount: -1250, kind: 'EXPENSE', when: '2026-05-30 07:15', notes: 'Weekly groceries', auto: false },
    { id: 't2', merchant: 'Pathao Rides',        accountId: 'a_cash',  categoryId: 'c_transport', amount: -320,  kind: 'EXPENSE', when: '2026-05-30 09:40', notes: '', auto: false },
    { id: 't3', merchant: 'DESCO',               accountId: 'a_brac',  categoryId: 'c_bills',     amount: -1180, kind: 'EXPENSE', when: '2026-05-30 11:02', notes: 'Electricity', auto: true },
    { id: 't4', merchant: 'Acme Corp Ltd',       accountId: 'a_brac',  categoryId: 'c_salary',    amount: 65000, kind: 'INCOME',  when: '2026-05-29 10:00', notes: 'May salary', auto: true },
    { id: 't5', merchant: 'Transfer',            accountId: 'a_brac',  categoryId: 'c_transfer',  amount: -5000, kind: 'TRANSFER', when: '2026-05-29 18:20', notes: 'To bKash', auto: false },
    { id: 't6', merchant: 'Transfer',            accountId: 'a_bkash', categoryId: 'c_transfer',  amount: -5000, kind: 'TRANSFER', when: '2026-05-29 18:21', notes: '', auto: false },
    { id: 't7', merchant: 'Lent · Rafi Ahmed',   accountId: 'a_cash',  categoryId: 'c_lent',      amount: 3000,  kind: 'LEND',    when: '2026-05-28 13:30', notes: 'Lunch + cab fare', auto: false },
  ];

  // ---- month insight ----
  var month = {
    label: 'May 2026',
    income: 65000, expense: 2750, net: 62250, deltaNet: 62250,
    categories: [
      { name: 'Groceries', amount: 1250, pct: 45, color: '#2F7D52' },
      { name: 'Bills',     amount: 1180, pct: 42, color: '#7A5BB8' },
      { name: 'Transport', amount: 320,  pct: 11, color: '#3F7CC2' },
    ],
    perDay: [
      { d: 1, v: 1250 }, { d: 2, v: 0 }, { d: 3, v: 0 }, { d: 4, v: 0 },
      { d: 5, v: 0 }, { d: 6, v: 0 }, { d: 7, v: 320 }, { d: 8, v: 0 },
      { d: 9, v: 1180 }, { d: 10, v: 0 },
    ],
    budgets: [
      { name: 'Groceries', spent: 1250, cap: 8000, pct: 15 },
      { name: 'Transport', spent: 320,  cap: 4500, pct: 7 },
    ],
    recurring: [],
  };

  // richer demo month for the isolated chart screens
  var monthRich = {
    categories: [
      { name: 'Food',      amount: 4200, pct: 42, color: '#C24A3C' },
      { name: 'Transport', amount: 2500, pct: 25, color: '#3F7CC2' },
      { name: 'Bills',     amount: 1800, pct: 18, color: '#B5692A' },
      { name: 'Shopping',  amount: 1500, pct: 15, color: '#2F7D52' },
    ],
    perDay: [ {d:1,v:400},{d:2,v:1300},{d:3,v:800},{d:4,v:2100},{d:5,v:1100},{d:6,v:1600} ],
    budgets: [
      { name: 'Food',      spent: 3200, cap: 6000, pct: 53 },
      { name: 'Transport', spent: 2100, cap: 2500, pct: 84 },
      { name: 'Shopping',  spent: 1800, cap: 1500, pct: 120 },
    ],
    recurring: [
      { merchant: 'Netflix',      times: 6, avg: 1100, last: '05-28' },
      { merchant: 'Grameenphone', times: 4, avg: 450,  last: '05-25' },
      { merchant: 'Daraz',        times: 3, avg: 2300, last: '05-18' },
    ],
  };

  // ---- people ----
  var people = [
    { id: 'p_nadia', name: 'Nadia Karim', contact: '+8801712345678', balance: -10000,
      history: [ { dir: 'BORROWED', amount: 10000, note: 'Emergency loan', status: 'OPEN' } ] },
    { id: 'p_rafi', name: 'Rafi Ahmed', contact: null, balance: 3000,
      history: [ { dir: 'LENT', amount: 3000, note: 'Lunch + cab fare', status: 'OPEN' } ] },
  ];

  // ---- recovery phrase (BIP39-ish sample) ----
  var phrase = ['abandon','ability','able','about','above','absent','absorb','abstract','absurd','abuse','access','accident','account','accuse','achieve','acid','acoustic','acquire','across','act','action','actor','actress','actual'];

  // ---- review inbox candidates ----
  var candidates = [
    { id: 'cand1', amount: 1200, dir: 'CREDIT', merchant: 'Rahim', category: 'Income', sender: 'bKash', account: 'bKash', confidence: 92, raw: 'You have received Tk 1,200.00 from RAHIM (017XXXXXX78). Ref: 8H2K. Balance: Tk 14,500.00.' },
    { id: 'cand2', amount: 850, dir: 'DEBIT', merchant: 'Shwapno', category: 'Groceries', sender: 'BRAC BANK', account: 'BRAC Bank', confidence: 64, raw: 'Your A/C ****4521 debited BDT 850.00 at SHWAPNO on 30-MAY. Avl Bal BDT 1,48,750.00.' },
  ];

  // ---- assistant proposed writes ----
  var assistantWrites = [
    { id: 'w1', label: 'Expense', detail: 'Cash · Food', amount: 500, included: true },
    { id: 'w2', label: 'Transfer', detail: 'Bank → Cash', amount: 1000, included: true },
    { id: 'w3', label: 'Budget', detail: 'Food', amount: 8000, included: false },
  ];

  // ---- auto-capture senders ----
  var senders = [
    { id: 's1', name: 'BRAC Bank', mapped: true,  account: 'BRAC Bank' },
    { id: 's2', name: 'City Bank', mapped: true,  account: 'City Bank Visa' },
    { id: 's3', name: 'bKash',     mapped: true,  account: 'bKash' },
    { id: 's4', name: 'Nagad',     mapped: false, account: null },
    { id: 's5', name: 'DBBL',      mapped: false, account: null },
    { id: 's6', name: 'Rocket',    mapped: false, account: null },
  ];

  function acc(id) { return accounts.find(function (a) { return a.id === id; }); }
  function cat(id) { return categories.find(function (c) { return c.id === id; }); }

  window.HISAAB = {
    taka: taka, group: group,
    accounts: accounts, categories: categories, transactions: transactions,
    month: month, monthRich: monthRich, people: people, phrase: phrase,
    candidates: candidates, assistantWrites: assistantWrites, senders: senders,
    acc: acc, cat: cat,
  };
})();
