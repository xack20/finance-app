/* ============================================================
   Hisaab Midnight — txn detail · review · people · sheets
   ============================================================ */

function NTxnDetail({ ctx, params }) {
  const t = T.transactions.find(x => x.id === (params && params.id)) || T.transactions[0];
  const c = ncat(t.categoryId); const a = T.acc(t.accountId) || {};
  const pos = t.amount >= 0;
  const [confirm, setConfirm] = useState(false);
  const [sms, setSms] = useState(false);
  const Row2 = (k, v, last) => <div style={{ display: 'flex', justifyContent: 'space-between', padding: '16px 0', borderBottom: last ? 'none' : '1px solid var(--hair-2)' }}><span style={{ fontSize: 15.5, color: 'var(--muted)' }}>{k}</span><span style={{ fontSize: 15.5, fontWeight: 600, textAlign: 'right' }}>{v}</span></div>;
  return <div style={{ height: '100%', display: 'flex', flexDirection: 'column' }}>
    <NTopBar left={<Icon name="chevron-left" size={20} />} onLeft={() => ctx.back()} title="Transaction" rightLabel="Delete" rightColor="var(--neg)" onRight={() => setConfirm(true)} />
    <NScreen style={{ padding: '8px var(--gut) 36px' }}>
      <div style={{ display: 'flex', alignItems: 'center', gap: 14, marginBottom: 6 }}>
        <NGlyph glyph={c.glyph} color={c.color} size={48} />
        <Money v={t.amount} size={44} sign color={pos ? 'var(--pos)' : 'var(--text)'} />
      </div>
      <div style={{ color: 'var(--muted)', fontSize: 14.5, textTransform: 'capitalize', marginBottom: 24 }}>{t.kind.toLowerCase()}</div>
      <Card style={{ padding: '4px 18px' }}>
        {Row2('Merchant', t.merchant)}{Row2('Category', c.name)}{Row2('Account', a.name)}{Row2('When', <span className="mono" style={{ fontWeight: 500 }}>{t.when}</span>)}{t.notes && Row2('Notes', t.notes, true)}
      </Card>
      {t.auto && <Card style={{ padding: 18, marginTop: 16 }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 14 }}><Icon name="sparkle" size={16} color="var(--lime)" /><span className="eyebrow" style={{ color: 'var(--lime)' }}>Captured</span></div>
        {[['Parsed by', 'On-device'], ['Model', 'gemma-2b'], ['Confidence', '94%'], ['From', a.name]].map(([k, v]) => <div key={k} style={{ display: 'flex', justifyContent: 'space-between', padding: '6px 0' }}><span style={{ fontSize: 14, color: 'var(--muted)' }}>{k}</span><span className="mono" style={{ fontSize: 14, fontWeight: 700 }}>{v}</span></div>)}
        <button className="tap" onClick={() => setSms(s => !s)} style={{ background: 'none', border: 'none', color: 'var(--lime)', fontWeight: 600, fontSize: 14, cursor: 'pointer', marginTop: 8, display: 'flex', alignItems: 'center', gap: 5, fontFamily: 'var(--sans)', whiteSpace: 'nowrap' }}>Show original SMS <Icon name="chevron-down" size={14} color="var(--lime)" style={{ transform: sms ? 'rotate(180deg)' : 'none' }} /></button>
        {sms && <div className="mono" style={{ marginTop: 10, background: 'var(--bg)', borderRadius: 10, padding: 12, fontSize: 12.5, lineHeight: 1.55, color: 'var(--muted)' }}>Your A/C ****4521 debited BDT {T.group(t.amount)}.00 at {t.merchant.toUpperCase()} on 30-MAY. Avl Bal BDT 1,48,750.00.</div>}
      </Card>}
    </NScreen>
    <NDialog open={confirm} onClose={() => setConfirm(false)}>
      <h3 className="disp" style={{ fontSize: 21, fontWeight: 600, marginBottom: 10 }}>Delete this transaction?</h3>
      <p style={{ color: 'var(--muted)', fontSize: 15, margin: '0 0 22px' }}>This can't be undone.</p>
      <div style={{ display: 'flex', gap: 12 }}><NBtn kind="glass" size="md" onClick={() => setConfirm(false)}>Cancel</NBtn><NBtn size="md" onClick={() => ctx.back()} style={{ background: 'var(--neg)', color: '#fff' }}>Delete</NBtn></div>
    </NDialog>
  </div>;
}

/* ---------- review inbox ---------- */
function NCandidate({ c, onDone, onEdit, onDismiss }) {
  const [sms, setSms] = useState(false); const pos = c.dir === 'CREDIT';
  return <Card style={{ padding: 20, animation: 'neo-fade .25s' }}>
    <Money v={pos ? c.amount : -c.amount} size={40} sign color={pos ? 'var(--pos)' : 'var(--text)'} />
    <div style={{ fontSize: 16, fontWeight: 600, marginTop: 8 }}>{c.merchant} · {c.category}</div>
    <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginTop: 6 }}>
      <span style={{ fontSize: 13, color: 'var(--muted)', whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>{c.sender} · {c.account}</span>
      <span style={{ fontSize: 11.5, fontWeight: 700, whiteSpace: 'nowrap', flexShrink: 0, color: c.confidence >= 85 ? 'var(--pos)' : 'var(--c-amber)', background: c.confidence >= 85 ? 'var(--pos-soft)' : 'rgba(255,177,60,.14)', padding: '3px 8px', borderRadius: 6 }}>{c.confidence}% sure</span>
    </div>
    <button className="tap" onClick={() => setSms(s => !s)} style={{ background: 'none', border: 'none', color: 'var(--lime)', fontWeight: 600, fontSize: 13.5, cursor: 'pointer', marginTop: 12, display: 'flex', alignItems: 'center', gap: 5, fontFamily: 'var(--sans)', whiteSpace: 'nowrap' }}>Show original SMS <Icon name="chevron-down" size={13} color="var(--lime)" style={{ transform: sms ? 'rotate(180deg)' : 'none' }} /></button>
    {sms && <div className="mono" style={{ marginTop: 10, background: 'var(--bg)', borderRadius: 10, padding: 12, fontSize: 12, lineHeight: 1.55, color: 'var(--muted)' }}>{c.raw}</div>}
    <div style={{ display: 'flex', gap: 12, marginTop: 16 }}><NBtn size="md" onClick={onDone}>Confirm</NBtn><NBtn kind="glass" size="md" onClick={onEdit}>Edit</NBtn></div>
    <button className="tap" onClick={onDismiss} style={{ width: '100%', marginTop: 10, background: 'none', border: 'none', color: 'var(--neg)', fontWeight: 600, fontSize: 14, cursor: 'pointer', fontFamily: 'var(--sans)' }}>Dismiss</button>
  </Card>;
}

function NReviewInbox({ ctx }) {
  const [list, setList] = useState(T.candidates);
  const anyHigh = list.some(c => c.confidence >= 85);
  return <div style={{ height: '100%', display: 'flex', flexDirection: 'column' }}>
    <NTopBar leftLabel="Close" onLeft={() => ctx.back()} title="Review" />
    <NScreen>
      {list.length === 0 ? <div style={{ height: '60%', display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', textAlign: 'center', gap: 14 }}>
        <div style={{ width: 60, height: 60, borderRadius: 18, background: 'var(--pos-soft)', display: 'flex', alignItems: 'center', justifyContent: 'center' }}><Icon name="check" size={30} color="var(--pos)" stroke={2.2} /></div>
        <div style={{ fontSize: 17, fontWeight: 600 }}>Nothing to review</div><div style={{ color: 'var(--muted)', fontSize: 14.5 }}>Captured transactions appear here to confirm.</div>
      </div> : <>
        {anyHigh && <div style={{ marginBottom: 16 }}><NBtn onClick={() => setList(list.filter(c => c.confidence < 85))}>Confirm all high-confidence</NBtn></div>}
        <div style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>{list.map(c => <NCandidate key={c.id} c={c} onDone={() => setList(list.filter(x => x.id !== c.id))} onDismiss={() => setList(list.filter(x => x.id !== c.id))} onEdit={() => ctx.go('entry')} />)}</div>
      </>}
    </NScreen>
  </div>;
}

/* ---------- review card (isolated) ---------- */
function NReviewCard({ ctx }) {
  const [items, setItems] = useState(T.assistantWrites);
  const [applied, setApplied] = useState(false);
  const anyOn = items.some(i => i.included);
  return <div style={{ height: '100%', display: 'flex', flexDirection: 'column' }}>
    <NTopBar leftLabel="Close" onLeft={() => ctx.back()} title="Proposed writes" />
    <NScreen>
      <Card style={{ padding: 18 }}>
        <div style={{ fontSize: 16, fontWeight: 700, marginBottom: 16 }}>Review &amp; apply</div>
        {applied ? <div style={{ display: 'flex', alignItems: 'center', gap: 8, color: 'var(--pos)', fontWeight: 600 }}><Icon name="check" size={18} color="var(--pos)" stroke={2.4} /> Saved.</div> : <>
          <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
            {items.map((w, i) => <div key={w.id}>
              <div role="button" className="tap" onClick={() => setItems(items.map((x, j) => j === i ? { ...x, included: !x.included } : x))} style={{ display: 'flex', alignItems: 'center', gap: 12, width: '100%', cursor: 'pointer', textAlign: 'left', padding: 0 }}>
                <NCheck on={w.included} /><span style={{ fontSize: 15, fontWeight: 500 }}>{w.label} · {w.detail}</span>
              </div>
              <div className="disp mono" style={{ fontSize: 34, fontWeight: 600, marginTop: 6, marginLeft: 38, opacity: w.included ? 1 : .4 }}>৳ {T.group(w.amount)}</div>
            </div>)}
          </div>
          <div style={{ marginTop: 18 }}><NBtn onClick={() => setApplied(true)} disabled={!anyOn}>Apply</NBtn></div>
        </>}
      </Card>
    </NScreen>
  </div>;
}

/* ---------- agent consent ---------- */
function NConsent({ ctx }) {
  return <div style={{ height: '100%', display: 'flex', flexDirection: 'column' }}>
    <NTopBar leftLabel="Close" onLeft={() => ctx.back()} title="Assistant" />
    <div style={{ flex: 1, position: 'relative' }}>
      <NDialog open onClose={() => ctx.back()}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 10, marginBottom: 12 }}><Icon name="sparkle" size={22} color="var(--lime)" /><h3 className="disp" style={{ fontSize: 21, fontWeight: 600 }}>Turn on the assistant</h3></div>
        <p style={{ color: 'var(--muted)', fontSize: 15, lineHeight: 1.5, margin: '0 0 16px' }}>A cloud feature that uses your own API key.</p>
        {[['What leaves your device', ['Account & category names', 'Named people', 'Amounts', 'Your conversation'], 'arrow-right', 'var(--lime)'], ['What never leaves', ['Raw SMS text', 'Your full ledger', 'Audio'], 'shield', 'var(--pos)']].map(([title, items, ic, col]) => <div key={title} style={{ marginBottom: 14 }}>
          <div className="eyebrow" style={{ marginBottom: 9 }}>{title}</div>
          {items.map(it => <div key={it} style={{ display: 'flex', alignItems: 'center', gap: 10, fontSize: 14.5, marginBottom: 7 }}><Icon name={ic} size={15} color={col} /> {it}</div>)}
        </div>)}
        <div style={{ display: 'flex', flexDirection: 'column', gap: 10, marginTop: 20 }}><NBtn onClick={() => ctx.replace('agent')}>Turn on assistant</NBtn><NBtn kind="glass" onClick={() => ctx.back()}>Not now</NBtn></div>
      </NDialog>
    </div>
  </div>;
}

/* ---------- people ---------- */
function inits(n) { return n.split(' ').map(w => w[0]).slice(0, 2).join('').toUpperCase(); }
function NPeople({ ctx }) {
  const [add, setAdd] = useState(false); const [name, setName] = useState('');
  const people = T.people;
  return <NScreen style={{ paddingBottom: 104 }}>
    <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', padding: '10px 0 18px' }}>
      <h1 className="disp" style={{ fontSize: 30, fontWeight: 600 }}>People</h1>
      <button className="tap" onClick={() => setAdd(true)} style={{ background: 'none', border: 'none', color: 'var(--lime)', fontWeight: 700, fontSize: 15.5, cursor: 'pointer', fontFamily: 'var(--sans)' }}>+ Add</button>
    </div>
    <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
      {people.map(p => { const owed = p.balance > 0; return <button key={p.id} className="tap" onClick={() => ctx.go('person', { id: p.id })} style={{ display: 'flex', alignItems: 'center', gap: 14, width: '100%', padding: 16, background: 'var(--surface)', border: '1px solid var(--hair)', borderRadius: 18, cursor: 'pointer', textAlign: 'left' }}>
        <span style={{ width: 44, height: 44, borderRadius: '50%', background: 'linear-gradient(135deg,var(--c-violet),var(--c-blue))', color: '#fff', display: 'flex', alignItems: 'center', justifyContent: 'center', fontWeight: 700, fontSize: 15, flexShrink: 0 }}>{inits(p.name)}</span>
        <div style={{ flex: 1 }}><div style={{ fontSize: 16.5, fontWeight: 600 }}>{p.name}</div><div style={{ fontSize: 13, color: 'var(--muted)', marginTop: 2 }}>{p.balance === 0 ? 'Settled' : owed ? 'Owes you' : 'You owe'}</div></div>
        <span className="mono" style={{ fontSize: 16, fontWeight: 700, color: p.balance === 0 ? 'var(--muted)' : owed ? 'var(--pos)' : 'var(--neg)' }}>{p.balance === 0 ? 'Settled' : T.taka(p.balance, { sign: true })}</span>
      </button>; })}
    </div>
    <NSheet open={add} onClose={() => setAdd(false)} title="Add person">
      <NField label="Name" value={name} onChange={setName} placeholder="Their name" autoFocus /><div style={{ height: 16 }} />
      <NBtn onClick={() => { setAdd(false); setName(''); }} disabled={!name.trim()}>Add</NBtn>
      <button className="tap" style={{ width: '100%', marginTop: 12, background: 'none', border: 'none', color: 'var(--muted)', fontWeight: 600, fontSize: 14.5, cursor: 'pointer', fontFamily: 'var(--sans)' }}>Or pick from contacts</button>
    </NSheet>
  </NScreen>;
}

function NPersonDetail({ ctx, params }) {
  const p = T.people.find(x => x.id === (params && params.id)) || T.people[1];
  const [settle, setSettle] = useState(false); const [amount, setAmount] = useState(String(Math.abs(p.balance))); const [acct, setAcct] = useState(T.accounts[0].id);
  const owed = p.balance > 0;
  return <div style={{ height: '100%', display: 'flex', flexDirection: 'column' }}>
    <NTopBar left={<Icon name="chevron-left" size={20} />} onLeft={() => ctx.back()} title={p.name} />
    <NScreen style={{ padding: '4px var(--gut) 36px' }}>
      <Card style={{ padding: 22, display: 'flex', alignItems: 'center', gap: 16 }}>
        <span style={{ width: 54, height: 54, borderRadius: '50%', background: 'linear-gradient(135deg,var(--c-violet),var(--c-blue))', color: '#fff', display: 'flex', alignItems: 'center', justifyContent: 'center', fontWeight: 700, fontSize: 18, flexShrink: 0 }}>{inits(p.name)}</span>
        <div><div className="eyebrow" style={{ marginBottom: 5 }}>{p.balance === 0 ? 'Settled' : owed ? 'They owe you' : 'You owe them'}</div><Money v={Math.abs(p.balance)} size={40} color={p.balance === 0 ? 'var(--muted)' : owed ? 'var(--pos)' : 'var(--neg)'} /></div>
      </Card>
      <div className="eyebrow" style={{ margin: '26px 0 12px' }}>History</div>
      <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
        {p.history.map((h, i) => <div key={i} style={{ display: 'flex', alignItems: 'center', gap: 14, padding: 16, background: 'var(--surface)', border: '1px solid var(--hair)', borderRadius: 16 }}>
          <span style={{ width: 38, height: 38, borderRadius: 11, background: h.dir === 'LENT' ? 'var(--pos-soft)' : 'var(--neg-soft)', display: 'flex', alignItems: 'center', justifyContent: 'center', flexShrink: 0 }}><Icon name={h.dir === 'LENT' ? 'lend' : 'borrow'} size={18} color={h.dir === 'LENT' ? 'var(--pos)' : 'var(--neg)'} /></span>
          <div style={{ flex: 1 }}><div style={{ fontSize: 15.5, fontWeight: 600 }}>{h.dir === 'LENT' ? 'Lent' : 'Borrowed'} {T.taka(h.amount)}</div><div style={{ fontSize: 13, color: 'var(--muted)', marginTop: 2 }}>{h.note} · {h.status}</div></div>
          {h.status !== 'SETTLED' && <button className="tap" onClick={() => setSettle(true)} style={{ background: 'var(--lime-soft)', border: 'none', color: 'var(--lime)', fontWeight: 700, fontSize: 13.5, cursor: 'pointer', padding: '8px 16px', borderRadius: 999, fontFamily: 'var(--sans)' }}>Settle</button>}
        </div>)}
      </div>
    </NScreen>
    <NSheet open={settle} onClose={() => setSettle(false)} title="Settle up">
      <NField label="Amount" prefix="৳" value={amount} onChange={v => setAmount(v.replace(/[^\d.]/g, ''))} mono big /><div style={{ height: 18 }} />
      <div className="eyebrow" style={{ marginBottom: 12 }}>Into account</div>
      <div style={{ display: 'flex', flexDirection: 'column' }}>{T.accounts.map((a, i) => <button key={a.id} className="tap" onClick={() => setAcct(a.id)} style={{ display: 'flex', alignItems: 'center', gap: 12, padding: '13px 4px', background: 'none', border: 'none', borderTop: i ? '1px solid var(--hair-2)' : 'none', cursor: 'pointer', width: '100%', textAlign: 'left' }}><NRadio on={acct === a.id} /><span style={{ fontSize: 16, fontWeight: 500 }}>{a.name}</span></button>)}</div>
      <div style={{ display: 'flex', gap: 12, marginTop: 22 }}><NBtn kind="glass" size="md" onClick={() => setSettle(false)}>Cancel</NBtn><NBtn size="md" onClick={() => setSettle(false)}>Settle</NBtn></div>
    </NSheet>
  </div>;
}

Object.assign(window, { NTxnDetail, NReviewInbox, NReviewCard, NConsent, NPeople, NPersonDetail });
