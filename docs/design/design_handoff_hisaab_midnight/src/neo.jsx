/* ============================================================
   Hisaab — "MIDNIGHT" neo-bank · hero screens
   Reuses window.Icon / Spinner / HISAAB / React hooks
   ============================================================ */
const T = window.HISAAB;

/* category styling for this theme */
const NCAT = {
  c_groc: ['cart', 'var(--c-teal)'], c_bills: ['receipt', 'var(--c-violet)'], c_transport: ['car', 'var(--c-blue)'],
  c_salary: ['case', 'var(--pos)'], c_transfer: ['swap', 'var(--c-slate)'], c_lent: ['lend', 'var(--c-lime)'],
  c_food: ['food', 'var(--c-rose)'], c_shop: ['bag', 'var(--c-amber)'], c_health: ['health', 'var(--c-teal)'],
  c_ent: ['film', 'var(--c-pink)'], c_education: ['book', 'var(--c-blue)'], c_borrowed: ['borrow', 'var(--c-amber)'], c_other: ['dot', 'var(--c-slate)'],
};
function ncat(id) { const c = T.cat(id) || {}; const m = NCAT[id] || ['dot', 'var(--c-slate)']; return { name: c.name, glyph: m[0], color: m[1] }; }

/* ---------- primitives ---------- */
function NGlyph({ glyph, color, size = 44, r = 14 }) {
  return <span style={{ width: size, height: size, borderRadius: r, flexShrink: 0, display: 'flex', alignItems: 'center', justifyContent: 'center', background: 'color-mix(in srgb, ' + color + ' 16%, transparent)' }}>
    <Icon name={glyph} size={size * 0.46} color={color} stroke={1.9} />
  </span>;
}
function NBtn({ children, onClick, kind = 'lime', size = 'lg', icon, disabled, style }) {
  const base = { display: 'inline-flex', alignItems: 'center', justifyContent: 'center', gap: 9, width: '100%', border: 'none', cursor: disabled ? 'default' : 'pointer', fontFamily: 'var(--sans)', fontWeight: 700, borderRadius: 'var(--r-pill)', height: size === 'lg' ? 56 : 46, fontSize: size === 'lg' ? 16.5 : 15, whiteSpace: 'nowrap' };
  const kinds = {
    lime: { background: disabled ? 'var(--surface-2)' : 'var(--lime)', color: disabled ? 'var(--faint)' : 'var(--on-lime)' },
    glass: { background: 'var(--glass)', color: 'var(--text)', border: '1px solid var(--hair)' },
    dark: { background: 'var(--bg-2)', color: 'var(--text)', border: '1px solid var(--hair)' },
  };
  return <button className="tap" disabled={disabled} onClick={onClick} style={{ ...base, ...kinds[kind], ...style }}>{icon && <Icon name={icon} size={19} />}{children}</button>;
}
function Card({ children, style, glow }) {
  return <div style={{ background: 'var(--surface)', border: '1px solid var(--hair)', borderRadius: 'var(--r-card)', boxShadow: glow ? 'var(--glow-lime)' : 'var(--shadow-card)', ...style }}>{children}</div>;
}
function Money({ v, size = 56, sign, color }) {
  const c = color || (v >= 0 ? 'var(--text)' : 'var(--text)');
  return <span className="disp mono" style={{ fontSize: size, fontWeight: 600, color: c, letterSpacing: '-0.03em', whiteSpace: 'nowrap' }}>{T.taka(v, sign ? { sign: true } : {})}</span>;
}

/* ---------- phone frame (dark) ---------- */
function NeoPhone({ children }) {
  return <div style={{ width: 392, height: 832, borderRadius: 48, padding: 9, background: '#000', boxShadow: '0 50px 120px -24px rgba(0,0,0,.85), 0 0 0 1px rgba(255,255,255,.05)' }}>
    <div style={{ width: '100%', height: '100%', borderRadius: 40, overflow: 'hidden', background: 'var(--bg)', display: 'flex', flexDirection: 'column', position: 'relative' }}>
      <div style={{ flexShrink: 0 }}><AndroidStatusBar dark /></div>
      <div style={{ flex: 1, minHeight: 0, position: 'relative', display: 'flex', flexDirection: 'column' }}>{children}</div>
      <div style={{ flexShrink: 0, paddingBottom: 2 }}><AndroidNavBar dark /></div>
    </div>
  </div>;
}

/* ---------- floating dock ---------- */
function Dock({ tab, onTab, onAdd }) {
  const items = [{ id: 'today', icon: 'today' }, { id: 'month', icon: 'month' }, { id: 'people', icon: 'people' }, { id: 'settings', icon: 'gear' }];
  return <div style={{ position: 'absolute', left: 0, right: 0, bottom: 14, display: 'flex', justifyContent: 'center', zIndex: 40, pointerEvents: 'none' }}>
    <div style={{ display: 'flex', alignItems: 'center', gap: 6, background: 'rgba(18,20,26,.82)', backdropFilter: 'blur(20px)', WebkitBackdropFilter: 'blur(20px)', border: '1px solid var(--hair)', borderRadius: 999, padding: '8px 10px', boxShadow: '0 16px 40px -10px rgba(0,0,0,.7)', pointerEvents: 'auto' }}>
      {items.slice(0, 2).map(it => <DockBtn key={it.id} it={it} on={tab === it.id} onClick={() => onTab(it.id)} />)}
      <button className="tap" onClick={onAdd} style={{ width: 52, height: 52, borderRadius: '50%', background: 'var(--lime)', border: 'none', cursor: 'pointer', display: 'flex', alignItems: 'center', justifyContent: 'center', margin: '0 2px', boxShadow: 'var(--glow-lime)' }}><Icon name="plus" size={26} color="var(--on-lime)" stroke={2.4} /></button>
      {items.slice(2).map(it => <DockBtn key={it.id} it={it} on={tab === it.id} onClick={() => onTab(it.id)} />)}
    </div>
  </div>;
}
function DockBtn({ it, on, onClick }) {
  return <button className="tap" onClick={onClick} style={{ width: 48, height: 48, borderRadius: 999, border: 'none', cursor: 'pointer', display: 'flex', alignItems: 'center', justifyContent: 'center', background: on ? 'var(--lime-soft)' : 'transparent' }}>
    <Icon name={it.icon} size={23} color={on ? 'var(--lime)' : 'var(--faint)'} stroke={on ? 2.1 : 1.8} fill={on && it.icon === 'today' ? 'var(--lime)' : 'none'} />
  </button>;
}

/* ============================================================
   TODAY
   ============================================================ */
const ACCT_BAL = { a_cash: 8400, a_brac: 148750, a_visa: -1250, a_bkash: 14500 };
const ACCT_HUE = { a_cash: 'var(--c-amber)', a_brac: 'var(--c-blue)', a_visa: 'var(--c-violet)', a_bkash: 'var(--c-rose)' };

function TodayNeo({ go }) {
  const [optIn, setOptIn] = useState(true);
  const total = Object.values(ACCT_BAL).reduce((a, b) => a + b, 0);
  const txns = T.transactions;
  const income = txns.filter(t => t.amount > 0).reduce((s, t) => s + t.amount, 0);
  const expense = txns.filter(t => t.amount < 0).reduce((s, t) => s + Math.abs(t.amount), 0);
  const inPct = Math.round((income / (income + expense)) * 100);
  return <div className="no-scrollbar" style={{ height: '100%', overflowY: 'auto', padding: '6px var(--gut) calc(var(--dock-h) + 30px)', animation: 'neo-fade .3s ease' }}>
    {/* header */}
    <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', padding: '8px 0 18px' }}>
      <div>
        <div style={{ fontSize: 13.5, color: 'var(--muted)' }}>Good evening</div>
        <div className="disp" style={{ fontSize: 20, fontWeight: 600 }}>Arif Hasan</div>
      </div>
      <div style={{ display: 'flex', gap: 10 }}>
        <IconBtn name="search" />
        <span style={{ width: 42, height: 42, borderRadius: '50%', background: 'linear-gradient(135deg,var(--c-violet),var(--c-blue))', display: 'flex', alignItems: 'center', justifyContent: 'center', color: '#fff', fontWeight: 700, fontSize: 15 }}>AH</span>
      </div>
    </div>

    {/* hero balance */}
    <Card style={{ padding: 22, position: 'relative', overflow: 'hidden' }}>
      <div style={{ position: 'absolute', top: -40, right: -30, width: 160, height: 160, borderRadius: '50%', background: 'radial-gradient(circle, var(--lime-soft), transparent 70%)' }} />
      <div className="eyebrow" style={{ marginBottom: 12 }}>Total balance</div>
      <Money v={total} size={50} />
      <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginTop: 14 }}>
        <span style={{ display: 'inline-flex', alignItems: 'center', gap: 5, background: 'var(--pos-soft)', color: 'var(--pos)', padding: '5px 10px', borderRadius: 999, fontSize: 13, fontWeight: 700, whiteSpace: 'nowrap' }}><Icon name="arrow-up" size={13} color="var(--pos)" />{T.taka(62250)} this month</span>
      </div>
      {/* in/out bar */}
      <div style={{ marginTop: 20 }}>
        <div style={{ display: 'flex', height: 8, borderRadius: 999, overflow: 'hidden', background: 'var(--bg-2)' }}>
          <div style={{ width: inPct + '%', background: 'var(--pos)' }} />
          <div style={{ flex: 1, background: 'var(--neg)' }} />
        </div>
        <div style={{ display: 'flex', justifyContent: 'space-between', marginTop: 9 }}>
          <span style={{ fontSize: 12.5, color: 'var(--muted)' }}><b style={{ color: 'var(--pos)' }}>In</b> &nbsp;{T.taka(income)}</span>
          <span style={{ fontSize: 12.5, color: 'var(--muted)' }}>{T.taka(expense)}&nbsp; <b style={{ color: 'var(--neg)' }}>Out</b></span>
        </div>
      </div>
    </Card>

    {/* accounts strip */}
    <div className="no-scrollbar" style={{ display: 'flex', gap: 12, overflowX: 'auto', margin: '16px -20px 0', padding: '2px 20px' }}>
      {T.accounts.map(a => (
        <div key={a.id} style={{ flex: '0 0 auto', width: 150, background: 'var(--surface)', border: '1px solid var(--hair)', borderRadius: 18, padding: 16 }}>
          <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
            <span style={{ width: 30, height: 30, borderRadius: 9, background: 'color-mix(in srgb,' + ACCT_HUE[a.id] + ' 18%, transparent)', display: 'flex', alignItems: 'center', justifyContent: 'center' }}><Icon name={a.kind === 'CASH' ? 'wallet' : a.kind === 'CARD' ? 'month' : 'month'} size={15} color={ACCT_HUE[a.id]} /></span>
            <span style={{ fontSize: 10.5, fontWeight: 700, letterSpacing: '.08em', color: 'var(--faint)' }}>{a.kind}</span>
          </div>
          <div style={{ fontSize: 13.5, color: 'var(--muted)', marginTop: 14 }}>{a.name}</div>
          <div className="mono" style={{ fontSize: 17, fontWeight: 700, marginTop: 3, color: ACCT_BAL[a.id] < 0 ? 'var(--neg)' : 'var(--text)' }}>{T.taka(ACCT_BAL[a.id])}</div>
        </div>
      ))}
    </div>

    {/* review banner */}
    <button className="tap" onClick={() => go('review')} style={{ display: 'flex', alignItems: 'center', gap: 13, width: '100%', marginTop: 16, background: 'var(--lime-soft)', border: '1px solid color-mix(in srgb,var(--lime) 30%,transparent)', borderRadius: 16, padding: '14px 16px', cursor: 'pointer', textAlign: 'left' }}>
      <span style={{ width: 38, height: 38, borderRadius: 11, background: 'var(--lime)', display: 'flex', alignItems: 'center', justifyContent: 'center' }}><Icon name="sparkle" size={20} color="var(--on-lime)" /></span>
      <div style={{ flex: 1 }}><div style={{ fontSize: 15, fontWeight: 700, color: 'var(--text)' }}>1 transaction to review</div><div style={{ fontSize: 13, color: 'var(--muted)' }}>Auto-captured from SMS</div></div>
      <Icon name="chevron-right" size={20} color="var(--lime)" />
    </button>

    {optIn && <Card style={{ padding: 18, marginTop: 14 }}>
      <div style={{ display: 'flex', alignItems: 'center', gap: 12, marginBottom: 10 }}>
        <span style={{ width: 38, height: 38, borderRadius: 11, background: 'var(--lime-soft)', display: 'flex', alignItems: 'center', justifyContent: 'center' }}><Icon name="sms" size={19} color="var(--lime)" /></span>
        <div style={{ fontSize: 16, fontWeight: 700 }}>Log transactions automatically</div>
      </div>
      <p style={{ color: 'var(--muted)', fontSize: 13.5, lineHeight: 1.55, margin: '0 0 14px' }}>Hisaab reads your bKash, Nagad and bank SMS to log for you — on-device, fully private.</p>
      <div style={{ display: 'flex', gap: 10 }}><NBtn size="md" onClick={() => { setOptIn(false); go('settings/auto-capture'); }} style={{ width: 'auto', padding: '0 22px' }}>Turn on</NBtn><button className="tap" onClick={() => setOptIn(false)} style={{ background: 'none', border: 'none', color: 'var(--muted)', fontWeight: 600, fontSize: 14.5, cursor: 'pointer', padding: '0 12px', fontFamily: 'var(--sans)' }}>Maybe later</button></div>
    </Card>}

    {/* feed */}
    <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', margin: '26px 0 6px' }}>
      <div className="disp" style={{ fontSize: 18, fontWeight: 600 }}>Today</div>
      <span style={{ fontSize: 13, color: 'var(--muted)' }}>31 May</span>
    </div>
    <div>{txns.map((t, i) => <NTxn key={t.id} t={t} onClick={() => go('txn', t)} last={i === txns.length - 1} />)}</div>
  </div>;
}

function IconBtn({ name, onClick }) {
  return <button className="tap" onClick={onClick} style={{ width: 42, height: 42, borderRadius: '50%', background: 'var(--glass)', border: '1px solid var(--hair)', cursor: 'pointer', display: 'flex', alignItems: 'center', justifyContent: 'center' }}><Icon name={name} size={19} color="var(--text)" /></button>;
}

function NTxn({ t, onClick, last }) {
  const c = ncat(t.categoryId); const a = T.acc(t.accountId) || {}; const pos = t.amount >= 0;
  return <button className="tap" onClick={onClick} style={{ display: 'flex', alignItems: 'center', gap: 14, width: '100%', padding: '13px 0', background: 'none', border: 'none', borderBottom: last ? 'none' : '1px solid var(--hair-2)', cursor: 'pointer', textAlign: 'left' }}>
    <NGlyph glyph={c.glyph} color={c.color} />
    <div style={{ flex: 1, minWidth: 0 }}>
      <div style={{ display: 'flex', alignItems: 'center', gap: 7 }}>
        <span style={{ fontSize: 16, fontWeight: 600, color: 'var(--text)', whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>{t.merchant}</span>
        {t.auto && <Icon name="sparkle" size={13} color="var(--lime)" />}
      </div>
      <div style={{ fontSize: 13, color: 'var(--muted)', marginTop: 2 }}>{a.name} · {c.name}</div>
    </div>
    <span className="mono" style={{ fontSize: 16, fontWeight: 700, whiteSpace: 'nowrap', color: pos ? 'var(--pos)' : 'var(--text)' }}>{T.taka(t.amount, { sign: true })}</span>
  </button>;
}

/* ============================================================
   ENTRY — tactile keypad
   ============================================================ */
const QCATS = ['c_food', 'c_groc', 'c_transport', 'c_bills', 'c_shop', 'c_health'];

function DChip({ icon, label, on, onClick }) {
  return <button className="tap" onClick={onClick} style={{ flex: '0 0 auto', display: 'flex', alignItems: 'center', gap: 7, padding: '9px 14px', borderRadius: 999, cursor: 'pointer', whiteSpace: 'nowrap', fontFamily: 'var(--sans)', fontSize: 13.5, fontWeight: 600, border: '1px solid ' + (on ? 'transparent' : 'var(--hair)'), background: on ? 'var(--lime-soft)' : 'var(--surface)', color: on ? 'var(--lime)' : 'var(--muted)' }}>
    <Icon name={icon} size={15} color={on ? 'var(--lime)' : 'var(--muted)'} />{label}
  </button>;
}

function NeoSplitSheet({ open, parent, onClose, onSave, onRemove }) {
  const [rows, setRows] = useState([{ v: '' }, { v: '' }]);
  useEffect(() => { if (open) setRows([{ v: '' }, { v: '' }]); }, [open]);
  const sum = rows.reduce((s, r) => s + (parseFloat(r.v) || 0), 0);
  const matched = Math.abs(sum - parent) < 0.01 && parent > 0;
  const allPos = rows.every(r => parseFloat(r.v) > 0);
  return <NSheet open={open} onClose={onClose} title="Split into parts">
    <div style={{ fontSize: 14, fontWeight: 600, color: matched ? 'var(--pos)' : 'var(--muted)', marginBottom: 16 }}>Parent: {T.taka(parent)} — children: {T.taka(sum)} {matched && '✓'}</div>
    <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
      {rows.map((r, i) => <div key={i} style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
        <span className="mono" style={{ color: 'var(--faint)', fontSize: 14, width: 16 }}>{i + 1}</span>
        <div style={{ flex: 1 }}><NField prefix="৳" value={r.v} onChange={v => setRows(rs => rs.map((x, j) => j === i ? { v: v.replace(/[^\d.]/g, '') } : x))} placeholder="0" mono /></div>
        {rows.length > 1 && <button className="tap" onClick={() => setRows(rs => rs.filter((_, j) => j !== i))} style={{ background: 'none', border: 'none', cursor: 'pointer', color: 'var(--faint)', padding: 6 }}><Icon name="close" size={18} /></button>}
      </div>)}
    </div>
    <button className="tap" onClick={() => setRows(rs => [...rs, { v: '' }])} style={{ background: 'none', border: 'none', color: 'var(--lime)', fontWeight: 600, fontSize: 15, cursor: 'pointer', marginTop: 14, fontFamily: 'var(--sans)' }}>+ Add part</button>
    <div style={{ display: 'flex', gap: 12, marginTop: 22 }}><NBtn kind="glass" size="md" onClick={onRemove}>Remove</NBtn><NBtn size="md" onClick={() => onSave(rows)} disabled={!matched || !allPos}>Save</NBtn></div>
  </NSheet>;
}

function EntryNeo({ close }) {
  const [kind, setKind] = useState('EXPENSE');
  const [amt, setAmt] = useState('0');
  const [cat, setCat] = useState('c_food');
  const [acct, setAcct] = useState('a_bkash');
  const [toAcct, setToAcct] = useState('a_cash');
  const [merchant, setMerchant] = useState('');
  const [note, setNote] = useState('');
  const [when] = useState('2026-05-31 07:15');
  const [tags, setTags] = useState([]);
  const [tagInput, setTagInput] = useState('');
  const [receipt, setReceipt] = useState(false);
  const [split, setSplit] = useState(null);
  const [person, setPerson] = useState('');
  const [sheet, setSheet] = useState(null);
  const press = k => setAmt(a => {
    if (k === 'del') return a.length <= 1 ? '0' : a.slice(0, -1);
    if (k === '.') return a.includes('.') ? a : a + '.';
    if (a === '0') return k;
    return (a + k).slice(0, 9);
  });
  const KINDS = [['EXPENSE', 'Expense'], ['INCOME', 'Income'], ['TRANSFER', 'Transfer'], ['LEND', 'Lend']];
  const keys = ['1', '2', '3', '4', '5', '6', '7', '8', '9', '.', '0', 'del'];
  const accent = kind === 'INCOME' ? 'var(--pos)' : kind === 'EXPENSE' ? 'var(--text)' : 'var(--lime)';
  return <div style={{ height: '100%', display: 'flex', flexDirection: 'column', animation: 'neo-up .3s cubic-bezier(.2,.9,.2,1)', background: 'var(--bg)' }}>
    <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', padding: '14px var(--gut) 4px' }}>
      <button className="tap" onClick={close} style={{ background: 'var(--glass)', border: '1px solid var(--hair)', borderRadius: 999, width: 40, height: 40, cursor: 'pointer', display: 'flex', alignItems: 'center', justifyContent: 'center' }}><Icon name="close" size={19} color="var(--text)" /></button>
      <div className="disp" style={{ fontSize: 17, fontWeight: 600 }}>New entry</div>
      <div style={{ width: 40 }} />
    </div>
    {/* kind */}
    <div className="no-scrollbar" style={{ display: 'flex', gap: 8, overflowX: 'auto', padding: '14px var(--gut) 6px' }}>
      {KINDS.map(([v, l]) => <button key={v} className="tap" onClick={() => setKind(v)} style={{ flex: '0 0 auto', padding: '9px 18px', borderRadius: 999, border: '1px solid ' + (kind === v ? 'transparent' : 'var(--hair)'), background: kind === v ? 'var(--text)' : 'transparent', color: kind === v ? 'var(--bg)' : 'var(--muted)', fontWeight: 700, fontSize: 14, cursor: 'pointer', fontFamily: 'var(--sans)' }}>{l}</button>)}
    </div>
    {/* amount */}
    <div style={{ flex: 1, display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', minHeight: 0 }}>
      <div style={{ display: 'flex', alignItems: 'baseline', gap: 8 }}>
        <span className="disp" style={{ fontSize: 30, color: 'var(--faint)', fontWeight: 500 }}>৳</span>
        <span className="disp mono" style={{ fontSize: 64, fontWeight: 600, color: amt === '0' ? 'var(--faint)' : accent, letterSpacing: '-.03em' }}>{Number(amt).toLocaleString('en-US') + (amt.endsWith('.') ? '.' : amt.includes('.') ? '' : '')}</span>
      </div>
      {/* category quick row */}
      <div className="no-scrollbar" style={{ display: 'flex', gap: 10, overflowX: 'auto', maxWidth: '100%', padding: '24px var(--gut) 0' }}>
        {QCATS.map(id => { const c = ncat(id); const on = cat === id; return <button key={id} className="tap" onClick={() => setCat(id)} style={{ flex: '0 0 auto', display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 6, background: 'none', border: 'none', cursor: 'pointer', opacity: on ? 1 : .55 }}>
          <span style={{ width: 50, height: 50, borderRadius: 15, display: 'flex', alignItems: 'center', justifyContent: 'center', background: 'color-mix(in srgb,' + c.color + ' 16%,transparent)', border: on ? '2px solid ' + c.color : '2px solid transparent' }}><Icon name={c.glyph} size={22} color={c.color} /></span>
          <span style={{ fontSize: 11, color: 'var(--muted)', whiteSpace: 'nowrap' }}>{c.name.split(' ')[0]}</span>
        </button>; })}
      </div>
    </div>
    {/* detail chips */}
    <div className="no-scrollbar" style={{ display: 'flex', gap: 8, overflowX: 'auto', padding: '0 var(--gut) 12px' }}>
      <DChip icon="wallet" label={T.acc(acct).name} on onClick={() => setSheet('account')} />
      {kind === 'TRANSFER' && <DChip icon="arrow-right" label={'To ' + T.acc(toAcct).name} on onClick={() => setSheet('toAcct')} />}
      {(kind === 'LEND' || kind === 'BORROW') && <DChip icon="people" label={person || 'Person'} on={!!person} onClick={() => setSheet('person')} />}
      <DChip icon="calendar" label="Today" onClick={() => setSheet('when')} />
      <DChip icon="edit" label={merchant || 'Merchant'} on={!!merchant} onClick={() => setSheet('merchant')} />
      <DChip icon="sms" label={note ? 'Note ✓' : 'Note'} on={!!note} onClick={() => setSheet('note')} />
      <DChip icon="tag" label={tags.length ? tags.length + ' tags' : 'Tags'} on={!!tags.length} onClick={() => setSheet('tags')} />
      <DChip icon="camera" label={receipt ? 'Receipt ✓' : 'Receipt'} on={receipt} onClick={() => setReceipt(r => !r)} />
      <DChip icon="swap" label={split ? split.length + ' parts' : 'Split'} on={!!split} onClick={() => setSheet('split')} />
    </div>
    {/* keypad */}
    <div style={{ background: 'var(--bg-2)', borderTop: '1px solid var(--hair)', padding: '12px 14px calc(env(safe-area-inset-bottom) + 14px)', borderRadius: '24px 24px 0 0' }}>
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(3,1fr)', gap: 8 }}>
        {keys.map(k => <button key={k} className="tap" onClick={() => press(k)} style={{ height: 56, borderRadius: 16, border: 'none', background: 'var(--surface)', cursor: 'pointer', display: 'flex', alignItems: 'center', justifyContent: 'center', fontFamily: 'var(--display)', fontSize: 24, fontWeight: 600, color: 'var(--text)' }}>{k === 'del' ? <Icon name="chevron-left" size={22} color="var(--muted)" /> : k}</button>)}
      </div>
      <div style={{ marginTop: 10 }}><NBtn onClick={close} disabled={amt === '0'}>Save entry</NBtn></div>
    </div>

    <NSheet open={sheet === 'account' || sheet === 'toAcct'} onClose={() => setSheet(null)} title={sheet === 'toAcct' ? 'To account' : 'Account'}>
      {T.accounts.map((a, i) => <button key={a.id} className="tap" onClick={() => { sheet === 'toAcct' ? setToAcct(a.id) : setAcct(a.id); setSheet(null); }} style={{ display: 'flex', alignItems: 'center', gap: 12, padding: '14px 4px', background: 'none', border: 'none', borderTop: i ? '1px solid var(--hair-2)' : 'none', cursor: 'pointer', width: '100%', textAlign: 'left' }}><span style={{ flex: 1, fontSize: 16, fontWeight: 600 }}>{a.name}</span><span className="eyebrow">{a.kind}</span>{((sheet === 'toAcct' ? toAcct : acct) === a.id) && <Icon name="check" size={18} color="var(--lime)" stroke={2.4} />}</button>)}
    </NSheet>
    <NSheet open={sheet === 'merchant'} onClose={() => setSheet(null)} title="Merchant"><NField value={merchant} onChange={setMerchant} placeholder="Where / who" autoFocus /><div style={{ height: 14 }} /><NBtn onClick={() => setSheet(null)}>Done</NBtn></NSheet>
    <NSheet open={sheet === 'note'} onClose={() => setSheet(null)} title="Note"><NTextArea value={note} onChange={setNote} placeholder="Add a note" /><div style={{ height: 14 }} /><NBtn onClick={() => setSheet(null)}>Done</NBtn></NSheet>
    <NSheet open={sheet === 'when'} onClose={() => setSheet(null)} title="When"><NField value={when} onChange={() => {}} mono /><div style={{ height: 14 }} /><NBtn onClick={() => setSheet(null)}>Done</NBtn></NSheet>
    <NSheet open={sheet === 'tags'} onClose={() => setSheet(null)} title="Tags">
      <div style={{ display: 'flex', flexWrap: 'wrap', gap: 8, alignItems: 'center' }}>
        {tags.map(t => <span key={t} className="tap" onClick={() => setTags(tags.filter(x => x !== t))} style={{ display: 'inline-flex', alignItems: 'center', gap: 6, background: 'var(--lime-soft)', color: 'var(--lime)', padding: '8px 12px', borderRadius: 999, fontSize: 14, fontWeight: 600, cursor: 'pointer' }}>{t} <Icon name="close" size={12} color="var(--lime)" stroke={2.2} /></span>)}
        <input value={tagInput} onChange={e => setTagInput(e.target.value)} onKeyDown={e => { if (e.key === 'Enter' && tagInput.trim()) { setTags([...tags, tagInput.trim()]); setTagInput(''); } }} placeholder="Add tag" style={{ flex: '1 0 100px', border: 'none', outline: 'none', background: 'var(--surface)', borderRadius: 999, padding: '10px 14px', color: 'var(--text)', fontFamily: 'var(--sans)', fontSize: 14.5 }} />
      </div>
      <div style={{ height: 16 }} /><NBtn onClick={() => setSheet(null)}>Done</NBtn>
    </NSheet>
    <NSheet open={sheet === 'person'} onClose={() => setSheet(null)} title="Person"><NField value={person} onChange={setPerson} placeholder="Their name" autoFocus /><div style={{ height: 14 }} /><NBtn onClick={() => setSheet(null)} disabled={!person.trim()}>Add</NBtn><div style={{ textAlign: 'center', color: 'var(--faint)', fontSize: 13, marginTop: 12 }}>Pick from contacts coming soon.</div></NSheet>
    <NeoSplitSheet open={sheet === 'split'} parent={parseFloat(amt) || 0} onClose={() => setSheet(null)} onSave={s => { setSplit(s); setSheet(null); }} onRemove={() => { setSplit(null); setSheet(null); }} />
  </div>;
}

/* ============================================================
   MONTH — insights
   ============================================================ */
function Ring({ pct, color, size = 64, label, value }) {
  const r = (size - 8) / 2, circ = 2 * Math.PI * r, off = circ * (1 - Math.min(pct, 100) / 100);
  return <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 8 }}>
    <div style={{ position: 'relative', width: size, height: size }}>
      <svg width={size} height={size} style={{ transform: 'rotate(-90deg)' }}>
        <circle cx={size / 2} cy={size / 2} r={r} fill="none" stroke="var(--bg-2)" strokeWidth="6" />
        <circle cx={size / 2} cy={size / 2} r={r} fill="none" stroke={color} strokeWidth="6" strokeLinecap="round" strokeDasharray={circ} strokeDashoffset={off} style={{ transition: 'stroke-dashoffset .6s' }} />
      </svg>
      <span className="mono" style={{ position: 'absolute', inset: 0, display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: 13, fontWeight: 700, color: pct >= 100 ? 'var(--neg)' : 'var(--text)' }}>{pct}%</span>
    </div>
    <div style={{ textAlign: 'center' }}><div style={{ fontSize: 12.5, fontWeight: 600 }}>{label}</div><div className="mono" style={{ fontSize: 11, color: 'var(--muted)' }}>{value}</div></div>
  </div>;
}
function MonthNeo() {
  const m = T.monthRich; const net = 66000;
  const max = Math.max(...m.perDay.map(d => d.v));
  return <div className="no-scrollbar" style={{ height: '100%', overflowY: 'auto', padding: '6px var(--gut) calc(var(--dock-h) + 30px)', animation: 'neo-fade .3s ease' }}>
    <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', padding: '10px 0 22px' }}>
      <IconBtn name="chevron-left" />
      <div className="disp" style={{ fontSize: 19, fontWeight: 600 }}>May 2026</div>
      <IconBtn name="chevron-right" />
    </div>
    <Card style={{ padding: 22 }}>
      <div className="eyebrow" style={{ marginBottom: 10 }}>Net this month</div>
      <Money v={net} size={46} />
      <div style={{ display: 'inline-flex', alignItems: 'center', gap: 5, marginTop: 12, background: 'var(--pos-soft)', color: 'var(--pos)', padding: '5px 10px', borderRadius: 999, fontSize: 13, fontWeight: 700, whiteSpace: 'nowrap' }}><Icon name="arrow-up" size={13} color="var(--pos)" />{T.taka(4200)} vs last month</div>
      {/* bar chart */}
      <div style={{ display: 'flex', alignItems: 'flex-end', gap: 7, height: 90, marginTop: 22 }}>
        {m.perDay.map((d, i) => <div key={i} style={{ flex: 1, height: Math.max(6, (d.v / max) * 100) + '%', borderRadius: '6px 6px 3px 3px', background: d.v === max ? 'var(--lime)' : 'color-mix(in srgb,var(--lime) 28%,transparent)' }} />)}
      </div>
      <div style={{ display: 'flex', justifyContent: 'space-between', marginTop: 8 }}><span className="mono" style={{ fontSize: 11, color: 'var(--faint)' }}>৳0</span><span className="mono" style={{ fontSize: 11, color: 'var(--faint)' }}>{T.taka(max)} peak</span></div>
    </Card>

    <div className="eyebrow" style={{ margin: '28px 0 14px' }}>Spending by category</div>
    <Card style={{ padding: 20 }}>
      {m.categories.map((c, i) => { const hue = [`var(--c-rose)`, `var(--c-blue)`, `var(--c-amber)`, `var(--c-teal)`][i]; return <div key={i} style={{ marginBottom: i === m.categories.length - 1 ? 0 : 16 }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 8 }}><span style={{ fontSize: 14.5, fontWeight: 600 }}>{c.name}</span><span style={{ display: 'flex', gap: 8, alignItems: 'baseline' }}><span className="mono" style={{ fontSize: 14, fontWeight: 700 }}>{T.taka(c.amount)}</span><span className="mono" style={{ fontSize: 12, color: 'var(--faint)' }}>{c.pct}%</span></span></div>
        <div style={{ height: 7, borderRadius: 999, background: 'var(--bg-2)', overflow: 'hidden' }}><div style={{ height: '100%', width: c.pct + '%', background: hue, borderRadius: 999 }} /></div>
      </div>; })}
    </Card>

    <div className="eyebrow" style={{ margin: '28px 0 14px' }}>Budgets</div>
    <Card style={{ padding: '22px 16px' }}>
      <div style={{ display: 'flex', justifyContent: 'space-around' }}>
        {m.budgets.map((b, i) => <Ring key={i} pct={b.pct} color={b.pct >= 100 ? 'var(--neg)' : b.pct >= 80 ? 'var(--c-amber)' : 'var(--lime)'} label={b.name} value={T.taka(b.spent) + '/' + T.taka(b.cap)} />)}
      </div>
    </Card>

    <div className="eyebrow" style={{ margin: '28px 0 14px' }}>Recurring</div>
    <Card style={{ padding: '6px 18px' }}>
      {m.recurring.map((r, i) => <div key={i} style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', padding: '15px 0', borderBottom: i === m.recurring.length - 1 ? 'none' : '1px solid var(--hair-2)' }}>
        <div><div style={{ fontSize: 15, fontWeight: 600 }}>{r.merchant}</div><div className="mono" style={{ fontSize: 12.5, color: 'var(--muted)', marginTop: 2 }}>{r.times}× · avg {T.taka(r.avg)}</div></div>
        <span className="mono" style={{ fontSize: 12.5, color: 'var(--faint)' }}>{r.last}</span>
      </div>)}
    </Card>
  </div>;
}

/* ============================================================
   ASSISTANT
   ============================================================ */
function AssistantNeo({ ctx, variant }) {
  const unavailable = variant === 'unavailable';
  const init = (variant === 'empty' || unavailable) ? [] : [{ role: 'user', text: 'How much did I spend on food this month?' }, { role: 'ai', text: "You've spent ৳12,000 on Food — about 38% of your spending so far. Want me to set a monthly budget?" }];
  const [msgs, setMsgs] = useState(init);
  const [input, setInput] = useState('');
  const [think, setThink] = useState(false);
  const [listening, setListening] = useState(false);
  const ref = useRef(null);
  useEffect(() => { if (ref.current) ref.current.scrollTop = ref.current.scrollHeight; }, [msgs, think]);
  const send = (txt) => { if (unavailable) return; const t = (txt || input).trim(); if (!t) return; setMsgs(m => [...m, { role: 'user', text: t }]); setInput(''); setThink(true); setTimeout(() => { setThink(false); setMsgs(m => [...m, { role: 'ai', text: 'Done — I’ve drafted that change. Tap apply when you’re ready.' }]); }, 1200); };
  const micTap = () => { if (unavailable) return; if (listening) { setListening(false); return; } setListening(true); setTimeout(() => { setListening(false); setInput('I paid 500 for lunch'); }, 1800); };
  return <div style={{ height: '100%', display: 'flex', flexDirection: 'column', animation: 'neo-fade .3s ease' }}>
    <div style={{ display: 'flex', alignItems: 'center', gap: 12, padding: '12px var(--gut)' }}>
      <button className="tap" onClick={() => ctx && ctx.back()} style={{ width: 38, height: 38, borderRadius: '50%', background: 'var(--glass)', border: '1px solid var(--hair)', cursor: 'pointer', display: 'flex', alignItems: 'center', justifyContent: 'center', flexShrink: 0 }}><Icon name="chevron-left" size={19} color="var(--text)" /></button>
      <span style={{ width: 38, height: 38, borderRadius: 12, background: 'var(--lime-soft)', display: 'flex', alignItems: 'center', justifyContent: 'center', animation: 'neo-glow 3s ease-in-out infinite' }}><Icon name="sparkle" size={20} color="var(--lime)" /></span>
      <div style={{ flex: 1 }}><div className="disp" style={{ fontSize: 17, fontWeight: 600 }}>Assistant</div><div style={{ fontSize: 12, color: 'var(--muted)' }}>On-device · private</div></div>
      <button className="tap" onClick={() => setMsgs([])} style={{ background: 'none', border: 'none', color: 'var(--lime)', fontWeight: 700, fontSize: 14, cursor: 'pointer', fontFamily: 'var(--sans)' }}>New</button>
    </div>
    <div ref={ref} className="no-scrollbar" style={{ flex: 1, overflowY: 'auto', padding: '8px var(--gut)', display: 'flex', flexDirection: 'column', gap: 12 }}>
      {unavailable && <div style={{ background: 'var(--surface)', border: '1px solid var(--hair)', borderRadius: 16, padding: 18 }}><div style={{ fontSize: 15.5, fontWeight: 600 }}>Add a cloud model + API key to use the assistant.</div><div style={{ fontSize: 13.5, color: 'var(--muted)', marginTop: 6 }}>Enable in Settings → Auto-capture → Cloud.</div></div>}
      {!unavailable && msgs.length === 0 && !think && <div style={{ flex: 1, display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', textAlign: 'center', gap: 16, padding: 24 }}>
        <div style={{ width: 64, height: 64, borderRadius: 20, background: 'var(--lime-soft)', display: 'flex', alignItems: 'center', justifyContent: 'center' }}><Icon name="sparkle" size={32} color="var(--lime)" /></div>
        <h2 className="disp" style={{ fontSize: 23, fontWeight: 600 }}>Ask about your money</h2>
        <p style={{ color: 'var(--muted)', fontSize: 15, lineHeight: 1.5, maxWidth: 260 }}>Try “I lent Karim 2000” or “How much on food this month?” — or tap the mic to speak.</p>
      </div>}
      {msgs.map((m, i) => <div key={i} style={{ display: 'flex', justifyContent: m.role === 'user' ? 'flex-end' : 'flex-start', animation: 'neo-fade .25s ease' }}>
        <div style={{ maxWidth: '82%', padding: '13px 16px', borderRadius: m.role === 'user' ? '20px 20px 6px 20px' : '20px 20px 20px 6px', background: m.role === 'user' ? 'var(--lime)' : 'var(--surface)', color: m.role === 'user' ? 'var(--on-lime)' : 'var(--text)', border: m.role === 'user' ? 'none' : '1px solid var(--hair)', fontSize: 15.5, lineHeight: 1.5, fontWeight: m.role === 'user' ? 600 : 400 }}>{m.text}</div>
      </div>)}
      {think && <div style={{ display: 'flex' }}><div style={{ padding: '14px 16px', borderRadius: '20px 20px 20px 6px', background: 'var(--surface)', border: '1px solid var(--hair)', display: 'flex', gap: 5 }}>{[0, 1, 2].map(i => <span key={i} style={{ width: 7, height: 7, borderRadius: 99, background: 'var(--lime)', animation: 'neo-pulse 1s ease-in-out infinite', animationDelay: i * .18 + 's' }} />)}</div></div>}
    </div>
    {!unavailable && <div className="no-scrollbar" style={{ display: 'flex', gap: 8, overflowX: 'auto', padding: '4px var(--gut) 10px' }}>
      {['Set a food budget', 'I paid 500 for lunch', 'Move 1000 to cash'].map(s => <button key={s} className="tap" onClick={() => send(s)} style={{ flex: '0 0 auto', background: 'var(--glass)', border: '1px solid var(--hair)', borderRadius: 999, padding: '9px 14px', fontSize: 13, fontWeight: 600, color: 'var(--muted)', cursor: 'pointer', whiteSpace: 'nowrap', fontFamily: 'var(--sans)' }}>{s}</button>)}
    </div>}
    {listening && <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 10, padding: '0 var(--gut) 8px' }}><span style={{ display: 'flex', gap: 4, alignItems: 'flex-end', height: 18 }}>{[0, 1, 2, 3, 4].map(i => <span key={i} style={{ width: 3, height: 6 + (i % 3) * 6, borderRadius: 2, background: 'var(--lime)', animation: 'neo-pulse .7s ease-in-out infinite', animationDelay: i * .1 + 's' }} />)}</span><span style={{ fontSize: 13.5, color: 'var(--lime)', fontWeight: 600 }}>Listening…</span></div>}
    <div style={{ padding: '6px var(--gut) 16px', display: 'flex', gap: 10, alignItems: 'center' }}>
      <input value={input} disabled={unavailable} onChange={e => setInput(e.target.value)} onKeyDown={e => e.key === 'Enter' && send()} placeholder={listening ? 'Listening…' : 'Ask anything…'} style={{ flex: 1, height: 50, borderRadius: 999, border: '1px solid ' + (listening ? 'var(--lime)' : 'var(--hair)'), background: 'var(--surface)', padding: '0 18px', outline: 'none', color: 'var(--text)', fontFamily: 'var(--sans)', fontSize: 15.5, opacity: unavailable ? .5 : 1 }} />
      <button className="tap" onClick={() => send()} disabled={unavailable || !input.trim()} style={{ width: 50, height: 50, borderRadius: '50%', border: 'none', background: input.trim() && !unavailable ? 'var(--lime)' : 'var(--surface-2)', cursor: 'pointer', display: 'flex', alignItems: 'center', justifyContent: 'center', flexShrink: 0 }}><Icon name="arrow-up" size={22} color={input.trim() && !unavailable ? 'var(--on-lime)' : 'var(--faint)'} /></button>
      <button className="tap" onClick={micTap} disabled={unavailable} title="Voice input" style={{ width: 50, height: 50, borderRadius: '50%', border: '1px solid ' + (listening ? 'transparent' : 'var(--hair)'), background: listening ? 'var(--lime)' : 'var(--surface)', cursor: 'pointer', display: 'flex', alignItems: 'center', justifyContent: 'center', flexShrink: 0, opacity: unavailable ? .4 : 1, boxShadow: listening ? 'var(--glow-lime)' : 'none' }}><Icon name="mic" size={20} color={listening ? 'var(--on-lime)' : 'var(--muted)'} /></button>
    </div>
  </div>;
}

/* a tiny detail screen so 'go' works */
function PlaceholderDetail({ title, close }) {
  return <div style={{ height: '100%', display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', gap: 16, animation: 'neo-fade .3s' }}>
    <span style={{ width: 60, height: 60, borderRadius: 18, background: 'var(--lime-soft)', display: 'flex', alignItems: 'center', justifyContent: 'center' }}><Icon name="sparkle" size={28} color="var(--lime)" /></span>
    <div className="disp" style={{ fontSize: 20, fontWeight: 600 }}>{title}</div>
    <div style={{ fontSize: 14, color: 'var(--muted)', maxWidth: 240, textAlign: 'center' }}>This screen comes next once you sign off on the direction.</div>
    <div style={{ width: 180 }}><NBtn kind="glass" size="md" onClick={close}>Back</NBtn></div>
  </div>;
}

/* ============================================================
   SHELL
   ============================================================ */
function NeoApp() {
  const [tab, setTab] = useState('today');
  const [overlay, setOverlay] = useState(null); // 'entry' | {detail}
  const [scale, setScale] = useState(1);
  useEffect(() => { const f = () => setScale(Math.min(1, (window.innerHeight - 40) / 832)); f(); window.addEventListener('resize', f); return () => window.removeEventListener('resize', f); }, []);
  const go = (kind, data) => setOverlay({ kind, data });

  let base;
  if (tab === 'today') base = <TodayNeo go={go} />;
  else if (tab === 'month') base = <MonthNeo />;
  else if (tab === 'ask') base = <AssistantNeo />;
  else base = <PlaceholderDetail title="Cards" close={() => setTab('today')} />;

  return <div style={{ minHeight: '100vh', display: 'flex' }}>
    <NeoSidebar tab={tab} setTab={t => { setOverlay(null); setTab(t); }} openEntry={() => setOverlay({ kind: 'entry' })} />
    <div style={{ flex: 1, display: 'flex', alignItems: 'center', justifyContent: 'center', padding: 20, background: 'radial-gradient(120% 90% at 50% -10%, #15171d 0%, #060708 70%)', overflow: 'hidden' }}>
      <div style={{ transform: 'scale(' + scale + ')', transformOrigin: 'center' }}>
        <NeoPhone>
          {base}
          {tab !== 'ask' && !overlay && <Dock tab={tab} onTab={setTab} onAdd={() => setOverlay({ kind: 'entry' })} />}
          {overlay && overlay.kind === 'entry' && <div style={{ position: 'absolute', inset: 0, zIndex: 50 }}><EntryNeo close={() => setOverlay(null)} /></div>}
          {overlay && overlay.kind === 'review' && <div style={{ position: 'absolute', inset: 0, zIndex: 50, background: 'var(--bg)' }}><PlaceholderDetail title="Review inbox" close={() => setOverlay(null)} /></div>}
          {overlay && overlay.kind === 'txn' && <div style={{ position: 'absolute', inset: 0, zIndex: 50, background: 'var(--bg)' }}><PlaceholderDetail title={overlay.data.merchant} close={() => setOverlay(null)} /></div>}
        </NeoPhone>
      </div>
    </div>
  </div>;
}

function NeoSidebar({ tab, setTab, openEntry }) {
  const items = [['today', 'Today'], ['month', 'Insights'], ['ask', 'Assistant'], ['cards', 'Cards']];
  return <div className="no-scrollbar" style={{ width: 236, flexShrink: 0, height: '100vh', overflowY: 'auto', background: '#0c0d11', borderRight: '1px solid var(--hair)', padding: '24px 16px' }}>
    <div style={{ display: 'flex', alignItems: 'baseline', gap: 8 }}><span className="disp" style={{ fontSize: 22, color: 'var(--lime)', fontWeight: 700 }}>হিসাব</span><span style={{ fontSize: 12, fontWeight: 700, letterSpacing: '.2em', color: 'var(--faint)' }}>HISAAB</span></div>
    <div style={{ fontSize: 11.5, color: 'var(--faint)', marginTop: 4, marginBottom: 22 }}>Midnight direction · hero screens</div>
    <div style={{ display: 'flex', flexDirection: 'column', gap: 3 }}>
      {items.map(([id, label]) => <button key={id} onClick={() => setTab(id)} style={{ textAlign: 'left', padding: '11px 14px', borderRadius: 12, border: 'none', cursor: 'pointer', fontFamily: 'var(--sans)', fontSize: 14.5, fontWeight: tab === id ? 700 : 500, background: tab === id ? 'var(--lime-soft)' : 'transparent', color: tab === id ? 'var(--lime)' : 'var(--muted)' }}>{label}</button>)}
      <button onClick={openEntry} style={{ textAlign: 'left', padding: '11px 14px', borderRadius: 12, border: '1px solid var(--hair)', cursor: 'pointer', fontFamily: 'var(--sans)', fontSize: 14.5, fontWeight: 600, background: 'transparent', color: 'var(--text)', marginTop: 6 }}>+ New entry</button>
    </div>
    <div style={{ marginTop: 26, padding: '14px', borderRadius: 14, background: 'var(--lime-soft)', border: '1px solid color-mix(in srgb,var(--lime) 25%,transparent)' }}>
      <div style={{ fontSize: 12.5, color: 'var(--text)', lineHeight: 1.5 }}>New visual language. If this direction lands, I'll roll it across all 30 screens.</div>
    </div>
  </div>;
}

/* Shell + render now live in neo-app.jsx */
