/* ============================================================
   Hisaab Midnight — settings suite
   ============================================================ */

function NSettings({ ctx }) {
  const [bio, setBio] = useState(true);
  const [lock, setLock] = useState('30 seconds');
  const [lockSheet, setLockSheet] = useState(false);
  const [signOut, setSignOut] = useState(false);
  const LOCKS = ['Immediate', '30 seconds', '5 minutes', 'Never'];
  return <NScreen style={{ paddingBottom: 104 }}>
    <h1 className="disp" style={{ fontSize: 30, fontWeight: 600, padding: '10px 0 6px' }}>Settings</h1>
    <NSection label="Privacy">
      <Card style={{ padding: '0 18px' }}>
        <NRow label="Lock timeout" value={lock} chevron onClick={() => setLockSheet(true)} />{nhair()}
        <NRow label="Biometric unlock" trailing={<NToggle on={bio} onChange={setBio} />} />{nhair()}
        <NRow label="Recovery phrase" value="Reveal" valueColor="var(--lime)" chevron onClick={() => ctx.go('settings/recovery')} />
      </Card>
    </NSection>
    <NSection label="Data">
      <Card style={{ padding: '0 18px' }}>
        <NRow label="Accounts" chevron onClick={() => ctx.go('settings/accounts')} />{nhair()}
        <NRow label="Categories" chevron onClick={() => ctx.go('settings/categories')} />{nhair()}
        <NRow label="Budgets" chevron onClick={() => ctx.go('settings/budgets')} />
      </Card>
    </NSection>
    <NSection label="Capture">
      <Card style={{ padding: '0 18px' }}><NRow label="Auto-capture" sub="On-device · private" chevron onClick={() => ctx.go('settings/auto-capture')} /></Card>
    </NSection>
    <NSection label="About">
      <Card style={{ padding: '0 18px' }}><NRow label="Version" value="0.1.0-p0c" /></Card>
    </NSection>
    <div style={{ textAlign: 'center', marginTop: 30 }}><button className="tap" onClick={() => setSignOut(true)} style={{ background: 'none', border: 'none', color: 'var(--neg)', fontWeight: 700, fontSize: 16, cursor: 'pointer', fontFamily: 'var(--sans)' }}>Sign out</button></div>

    <NSheet open={lockSheet} onClose={() => setLockSheet(false)} title="Lock timeout">
      {LOCKS.map((l, i) => <button key={l} className="tap" onClick={() => { setLock(l); setLockSheet(false); }} style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', padding: '16px 4px', background: 'none', border: 'none', borderTop: i ? '1px solid var(--hair-2)' : 'none', cursor: 'pointer', width: '100%', fontFamily: 'var(--sans)', fontSize: 16.5, fontWeight: 500, color: 'var(--text)' }}>{l} {lock === l && <Icon name="check" size={20} color="var(--lime)" stroke={2.4} />}</button>)}
    </NSheet>
    <NDialog open={signOut} onClose={() => setSignOut(false)}>
      <h3 className="disp" style={{ fontSize: 21, fontWeight: 600, marginBottom: 10 }}>Sign out of Hisaab?</h3>
      <p style={{ color: 'var(--muted)', fontSize: 15, lineHeight: 1.5, margin: '0 0 22px' }}>This clears your encrypted data on this device. You'll need your recovery phrase to restore.</p>
      <div style={{ display: 'flex', gap: 12 }}><NBtn kind="glass" size="md" onClick={() => setSignOut(false)}>Cancel</NBtn><NBtn size="md" onClick={() => { setSignOut(false); ctx.setPhase('onboarding'); }} style={{ background: 'var(--neg)', color: '#fff' }}>Sign out</NBtn></div>
    </NDialog>
  </NScreen>;
}

/* ---------- accounts ---------- */
const NABAL = { a_cash: 8400, a_brac: 148750, a_visa: -1250, a_bkash: 14500 };
function NAccounts({ ctx }) {
  const [add, setAdd] = useState(false);
  return <NSub ctx={ctx} title="Accounts" rightLabel="+ Add" onRight={() => setAdd(true)}>
    <div style={{ display: 'flex', flexDirection: 'column', gap: 10, marginTop: 8 }}>
      {T.accounts.map(a => <div key={a.id} style={{ background: 'var(--surface)', border: '1px solid var(--hair)', borderRadius: 18, padding: 18 }}>
        <div style={{ display: 'flex', alignItems: 'center' }}>
          <div style={{ flex: 1 }}><div style={{ fontSize: 17, fontWeight: 600 }}>{a.name}</div><div className="eyebrow" style={{ marginTop: 4 }}>{a.kind} · {a.currency}</div></div>
          <span className="mono" style={{ fontSize: 16, fontWeight: 700, color: NABAL[a.id] < 0 ? 'var(--neg)' : 'var(--text)' }}>{T.taka(NABAL[a.id])}</span>
        </div>
        {a.kind === 'CARD' && <div style={{ display: 'flex', gap: 22, marginTop: 14, paddingTop: 14, borderTop: '1px solid var(--hair-2)' }}>
          {[['Outstanding', T.taka(a.outstanding), 'var(--neg)'], ['Available', T.taka(a.available), 'var(--pos)'], ['Due', a.dueDate, 'var(--muted)']].map(([k, v, c]) => <div key={k}><div className="eyebrow" style={{ marginBottom: 4 }}>{k}</div><div className="mono" style={{ fontSize: 13.5, fontWeight: 700, color: c }}>{v}</div></div>)}
        </div>}
      </div>)}
    </div>
    <NSheet open={add} onClose={() => setAdd(false)} title="New account"><NAddAccount onAdd={() => setAdd(false)} /></NSheet>
  </NSub>;
}
function NAddAccount({ onAdd }) {
  const [name, setName] = useState(''); const [kind, setKind] = useState('CASH');
  return <div>
    <NField label="Name" value={name} onChange={setName} placeholder="e.g. Dutch-Bangla" autoFocus /><div style={{ height: 18 }} />
    <div className="eyebrow" style={{ marginBottom: 10 }}>Kind</div>
    <div style={{ display: 'flex', flexWrap: 'wrap', gap: 8 }}>{['CASH', 'BANK', 'MFS', 'CARD', 'GOAL'].map(k => <button key={k} className="tap" onClick={() => setKind(k)} style={{ padding: '10px 18px', borderRadius: 999, cursor: 'pointer', fontFamily: 'var(--sans)', fontWeight: 700, fontSize: 13.5, border: '1px solid ' + (kind === k ? 'transparent' : 'var(--hair)'), background: kind === k ? 'var(--lime)' : 'transparent', color: kind === k ? 'var(--on-lime)' : 'var(--muted)' }}>{k}</button>)}</div>
    {kind === 'CARD' && <div style={{ marginTop: 18, display: 'flex', flexDirection: 'column', gap: 14 }}>
      <NField label="Credit limit" prefix="৳" value="" onChange={() => {}} placeholder="150000" mono />
      <div style={{ display: 'flex', gap: 12 }}><div style={{ flex: 1 }}><NField label="Statement day" value="" onChange={() => {}} placeholder="5" mono /></div><div style={{ flex: 1 }}><NField label="Due day" value="" onChange={() => {}} placeholder="15" mono /></div></div>
    </div>}
    <div style={{ height: 20 }} /><NBtn onClick={onAdd} disabled={!name.trim()}>Add account</NBtn>
  </div>;
}

/* ---------- categories ---------- */
function NCategories({ ctx }) {
  const [add, setAdd] = useState(false); const [name, setName] = useState(''); const [g, setG] = useState('food');
  return <NSub ctx={ctx} title="Categories" rightLabel="+ Add" onRight={() => setAdd(true)}>
    <Card style={{ padding: '0 16px', marginTop: 8 }}>
      {T.categories.map((c, i) => { const m = ncat(c.id); return <div key={c.id}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 14, padding: '14px 0' }}><NGlyph glyph={m.glyph} color={m.color} size={36} /><span style={{ flex: 1, fontSize: 16, fontWeight: 600 }}>{c.name}</span>{c.isDefault && <span className="eyebrow">Default</span>}</div>
        {i < T.categories.length - 1 && <div style={{ height: 1, background: 'var(--hair-2)', marginLeft: 50 }} />}
      </div>; })}
    </Card>
    <NSheet open={add} onClose={() => setAdd(false)} title="New category">
      <NField label="Name" value={name} onChange={setName} placeholder="e.g. Gym" autoFocus /><div style={{ height: 16 }} />
      <div className="eyebrow" style={{ marginBottom: 10 }}>Icon</div>
      <div style={{ display: 'flex', flexWrap: 'wrap', gap: 10 }}>{['food', 'cart', 'car', 'bag', 'health', 'book', 'film', 'case', 'receipt'].map(x => <button key={x} className="tap" onClick={() => setG(x)} style={{ border: g === x ? '2px solid var(--lime)' : '2px solid transparent', background: 'none', borderRadius: 16, padding: 0, cursor: 'pointer' }}><NGlyph glyph={x} color="var(--lime)" size={42} /></button>)}</div>
      <div style={{ height: 22 }} /><NBtn onClick={() => { setAdd(false); setName(''); }} disabled={!name.trim()}>Add category</NBtn>
    </NSheet>
  </NSub>;
}

/* ---------- budgets ---------- */
function NBudgets({ ctx }) {
  const [add, setAdd] = useState(false); const [pick, setPick] = useState(null); const [cap, setCap] = useState('');
  const budgets = [{ name: 'Groceries', cap: 8000, since: '2026-05' }, { name: 'Transport', cap: 4500, since: '2026-05' }];
  const pickable = T.categories.filter(c => !['c_salary', 'c_transfer'].includes(c.id));
  return <NSub ctx={ctx} title="Budgets" rightLabel="+ Add" onRight={() => setAdd(true)}>
    <div style={{ display: 'flex', flexDirection: 'column', gap: 10, marginTop: 8 }}>
      {budgets.map((b, i) => <div key={i} style={{ display: 'flex', alignItems: 'center', background: 'var(--surface)', border: '1px solid var(--hair)', borderRadius: 18, padding: 18 }}>
        <div style={{ flex: 1 }}><div style={{ fontSize: 17, fontWeight: 600 }}>{b.name}</div><div className="mono" style={{ fontSize: 13, color: 'var(--muted)', marginTop: 4 }}>{T.taka(b.cap)}/mo · since {b.since}</div></div>
        <button className="tap" style={{ background: 'none', border: 'none', color: 'var(--neg)', fontWeight: 700, fontSize: 14, cursor: 'pointer', fontFamily: 'var(--sans)' }}>Archive</button>
      </div>)}
    </div>
    <NSheet open={add} onClose={() => setAdd(false)} title="New budget">
      <div className="eyebrow" style={{ marginBottom: 10 }}>Category</div>
      <div className="no-scrollbar" style={{ maxHeight: 230, overflowY: 'auto', marginBottom: 18 }}>{pickable.map(c => { const m = ncat(c.id); return <button key={c.id} className="tap" onClick={() => setPick(c.id)} style={{ display: 'flex', alignItems: 'center', gap: 12, padding: '11px 4px', background: 'none', border: 'none', cursor: 'pointer', width: '100%', textAlign: 'left' }}><NRadio on={pick === c.id} /><NGlyph glyph={m.glyph} color={m.color} size={32} /><span style={{ fontSize: 16, fontWeight: 500 }}>{c.name}</span></button>; })}</div>
      <NField label="Monthly cap" prefix="৳" value={cap} onChange={v => setCap(v.replace(/[^\d]/g, ''))} placeholder="8000" mono /><div style={{ height: 18 }} />
      <NBtn onClick={() => { setAdd(false); setPick(null); setCap(''); }} disabled={!pick || !cap}>Save budget</NBtn>
    </NSheet>
  </NSub>;
}

/* ---------- auto-capture ---------- */
function NSlider({ value, onChange, min = 50, max = 99 }) {
  const ref = useRef(null); const pct = ((value - min) / (max - min)) * 100;
  const set = x => { const r = ref.current.getBoundingClientRect(); let p = (x - r.left) / r.width; p = Math.max(0, Math.min(1, p)); onChange(Math.round(min + p * (max - min))); };
  const down = e => { set((e.touches ? e.touches[0] : e).clientX); const mv = ev => set((ev.touches ? ev.touches[0] : ev).clientX); const up = () => { window.removeEventListener('pointermove', mv); window.removeEventListener('pointerup', up); }; window.addEventListener('pointermove', mv); window.addEventListener('pointerup', up); };
  return <div ref={ref} onPointerDown={down} style={{ position: 'relative', height: 30, display: 'flex', alignItems: 'center', cursor: 'pointer', touchAction: 'none' }}>
    <div style={{ height: 7, borderRadius: 999, background: 'var(--surface-2)', width: '100%' }} />
    <div style={{ position: 'absolute', height: 7, borderRadius: 999, background: 'var(--lime)', width: pct + '%' }} />
    <div style={{ position: 'absolute', left: 'calc(' + pct + '% - 13px)', width: 26, height: 26, borderRadius: '50%', background: 'var(--text)', border: '3px solid var(--lime)' }} />
  </div>;
}
function NEngine({ engine, onSelect }) {
  return <Card style={{ padding: '0 18px' }}>{[['device', 'On-device', 'Private, offline', 'shield'], ['cloud', 'Cloud', 'Your own API key', 'cloud']].map(([v, t, s, ic], i) => <div key={v}>
    <button className="tap" onClick={() => onSelect(v)} style={{ display: 'flex', alignItems: 'center', gap: 14, padding: '16px 0', background: 'none', border: 'none', cursor: 'pointer', width: '100%', textAlign: 'left' }}>
      <Icon name={ic} size={22} color={engine === v ? 'var(--lime)' : 'var(--faint)'} />
      <div style={{ flex: 1 }}><div style={{ fontSize: 16, fontWeight: 600 }}>{t}</div><div style={{ fontSize: 13, color: 'var(--muted)', marginTop: 2 }}>{s}</div></div>
      {engine === v && <Icon name="check" size={20} color="var(--lime)" stroke={2.4} />}
    </button>{i === 0 && nhair()}
  </div>)}</Card>;
}
function NAutoCapture({ ctx, ios, permDenied }) {
  const [on, setOn] = useState(permDenied || false); const [engine, setEngine] = useState('device'); const [redact, setRedact] = useState(true); const [review, setReview] = useState(false); const [conf, setConf] = useState(85);
  const [provider, setProvider] = useState('claude'); const [apiKey, setApiKey] = useState(''); const [keyState, setKeyState] = useState('idle');
  const [addSender, setAddSender] = useState(false); const [sid, setSid] = useState(''); const [sname, setSname] = useState('');
  const [senders, setSenders] = useState(T.senders); const unmapped = senders.filter(s => !s.mapped).length;
  if (ios) return <NSub ctx={ctx} title="Auto-capture"><Card style={{ padding: 20, marginTop: 8 }}><Icon name="sms" size={26} color="var(--lime)" /><div style={{ fontSize: 17, fontWeight: 600, margin: '14px 0 8px' }}>SMS capture isn't available on iOS</div><p style={{ color: 'var(--muted)', fontSize: 14.5, lineHeight: 1.55, margin: 0 }}>Apple restricts reading SMS. Paste or share a bank message instead — coming soon.</p></Card></NSub>;
  return <NSub ctx={ctx} title="Auto-capture">
    <Card style={{ padding: '0 18px', marginTop: 8 }}><NRow label="Capture transactions from SMS" trailing={<NToggle on={on} onChange={setOn} />} /></Card>
    {on && permDenied && <Card style={{ padding: 16, marginTop: 10, border: '1px solid var(--neg)' }}><div style={{ display: 'flex', alignItems: 'center', gap: 10 }}><Icon name="warn" size={18} color="var(--neg)" /><div style={{ flex: 1, fontSize: 14, fontWeight: 600, color: 'var(--neg)' }}>SMS permission denied</div><button className="tap" style={{ background: 'var(--lime)', border: 'none', color: 'var(--on-lime)', fontWeight: 700, fontSize: 13.5, padding: '8px 14px', borderRadius: 999, cursor: 'pointer', fontFamily: 'var(--sans)' }}>Grant</button></div></Card>}
    <NSection label="Engine"><NEngine engine={engine} onSelect={setEngine} /></NSection>
    {engine === 'cloud' && <NSection label="Cloud">
      <div className="eyebrow" style={{ marginBottom: 10 }}>Provider</div>
      <Card style={{ padding: '0 18px', marginBottom: 14 }}>{[['claude', 'Claude'], ['openai', 'OpenAI'], ['gemini', 'Gemini']].map(([v, l], i) => <div key={v}><button className="tap" onClick={() => setProvider(v)} style={{ display: 'flex', alignItems: 'center', gap: 12, padding: '14px 0', background: 'none', border: 'none', cursor: 'pointer', width: '100%', textAlign: 'left' }}><NRadio on={provider === v} /><span style={{ fontSize: 16, fontWeight: 500 }}>{l}</span></button>{i < 2 && nhair()}</div>)}</Card>
      <NField label="API key" type="password" value={apiKey} onChange={v => { setApiKey(v); setKeyState('idle'); }} placeholder="sk-ant-…" mono />
      <div style={{ display: 'flex', alignItems: 'center', gap: 12, marginTop: 12 }}>
        <div style={{ width: 130 }}><NBtn kind="glass" size="md" onClick={() => { setKeyState('checking'); setTimeout(() => setKeyState(apiKey.trim().length > 6 ? 'valid' : 'invalid'), 900); }} disabled={!apiKey.trim()}>Validate</NBtn></div>
        {keyState === 'checking' && <span style={{ fontSize: 13.5, color: 'var(--muted)' }}>Checking…</span>}
        {keyState === 'valid' && <span style={{ fontSize: 13.5, color: 'var(--pos)', fontWeight: 600 }}>✓ Valid key</span>}
        {keyState === 'invalid' && <span style={{ fontSize: 13.5, color: 'var(--neg)', fontWeight: 600 }}>Invalid key</span>}
      </div>
      <Card style={{ padding: '0 18px', marginTop: 14 }}>
        <NRow label="Model" value={provider === 'claude' ? 'claude-3-5-haiku' : provider === 'openai' ? 'gpt-4o-mini' : 'gemini-1.5-flash'} />{nhair()}
        <NRow label="Cloud consent" value="Review" valueColor="var(--lime)" chevron onClick={() => ctx.go('settings/auto-capture/consent')} />
      </Card>
    </NSection>}
    <NSection label="Privacy">
      <Card style={{ padding: 18 }}>
        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}><span style={{ fontSize: 16, fontWeight: 500 }}>Redact PII before cloud calls</span><NToggle on={redact} onChange={setRedact} /></div>
        <p style={{ color: 'var(--muted)', fontSize: 13, lineHeight: 1.55, margin: '12px 0 0' }}>Masks account and phone numbers. Amount and merchant still leave the device when using cloud.</p>
      </Card>
    </NSection>
    <NSection label="Trust">
      <Card style={{ padding: 18 }}>
        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}><span style={{ fontSize: 16, fontWeight: 500 }}>Always review before posting</span><NToggle on={review} onChange={setReview} /></div>
        {!review && <div style={{ marginTop: 14, paddingTop: 14, borderTop: '1px solid var(--hair-2)' }}><div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 6 }}><span style={{ fontSize: 14, color: 'var(--muted)' }}>Auto-post confidence</span><span className="mono" style={{ fontSize: 14, fontWeight: 700, color: 'var(--lime)' }}>{conf}%</span></div><NSlider value={conf} onChange={setConf} /></div>}
      </Card>
    </NSection>
    <NSection label="Senders">
      {unmapped > 0 && <Card style={{ padding: 16, marginBottom: 10 }}><div style={{ fontSize: 15, fontWeight: 600 }}>{unmapped} new senders detected</div><div style={{ fontSize: 13, color: 'var(--muted)', marginTop: 3 }}>Map them to an account so Hisaab can auto-log them.</div></Card>}
      <Card style={{ padding: '0 18px' }}>{senders.map((s, i) => <div key={s.id}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 12, padding: '14px 0' }}><div style={{ flex: 1 }}><div style={{ fontSize: 16, fontWeight: 600 }}>{s.name}</div><div style={{ fontSize: 12.5, color: s.mapped ? 'var(--lime)' : 'var(--faint)', marginTop: 2 }}>{s.mapped ? '→ ' + s.account : 'Tap to map'}</div></div><NToggle on={s.mapped} onChange={v => setSenders(senders.map((x, j) => j === i ? { ...x, mapped: v, account: v ? T.accounts[0].name : null } : x))} /></div>
        {i < senders.length - 1 && nhair()}
      </div>)}</Card>
      {addSender ? <Card style={{ padding: 16, marginTop: 10 }}>
        <NField label="Sender ID" value={sid} onChange={setSid} placeholder="e.g. BRAC BANK" /><div style={{ height: 12 }} />
        <NField label="Display name" value={sname} onChange={setSname} placeholder="BRAC Bank" />
        <div style={{ display: 'flex', gap: 12, marginTop: 14 }}><NBtn kind="glass" size="md" onClick={() => setAddSender(false)}>Cancel</NBtn><NBtn size="md" disabled={!sid.trim()} onClick={() => { setSenders([...senders, { id: 's' + Date.now(), name: sname.trim() || sid.trim(), mapped: false, account: null }]); setSid(''); setSname(''); setAddSender(false); }}>Add</NBtn></div>
      </Card> : <button className="tap" onClick={() => setAddSender(true)} style={{ width: '100%', marginTop: 10, padding: '13px', background: 'var(--glass)', border: '1px solid var(--hair)', borderRadius: 14, color: 'var(--lime)', fontWeight: 600, fontSize: 14.5, cursor: 'pointer', fontFamily: 'var(--sans)' }}>+ Add a sender</button>}
    </NSection>
    <div style={{ marginTop: 22 }}><NBtn kind="glass" disabled={!on}>Import last 90 days</NBtn></div>
  </NSub>;
}

/* ---------- cloud consent ---------- */
function NCloudConsent({ ctx }) {
  const [granted, setGranted] = useState(false);
  return <NSub ctx={ctx} title="Cloud consent">
    <h2 style={{ fontSize: 19, fontWeight: 700, lineHeight: 1.4, margin: '12px 0 14px' }}>Your bank SMS text will be sent to Claude for parsing.</h2>
    <p style={{ color: 'var(--muted)', fontSize: 15, lineHeight: 1.6, margin: '0 0 26px' }}>Hisaab's own servers never see this data — it goes directly from your device to Claude using your API key. Redaction is on by default and masks account and phone numbers before sending.</p>
    {granted ? <><div style={{ display: 'flex', alignItems: 'center', gap: 8, color: 'var(--pos)', fontWeight: 600, fontSize: 16, marginBottom: 18 }}><Icon name="check" size={18} color="var(--pos)" stroke={2.4} /> Consent granted.</div><NBtn kind="glass" onClick={() => setGranted(false)} style={{ color: 'var(--neg)' }}>Revoke consent</NBtn></> : <NBtn onClick={() => setGranted(true)}>I agree — use cloud parsing</NBtn>}
  </NSub>;
}

/* ---------- recovery reveal ---------- */
function NRecoveryReveal({ ctx, error }) {
  return <NSub ctx={ctx} title="Recovery phrase">
    <div style={{ display: 'inline-flex', alignItems: 'flex-start', gap: 8, color: 'var(--neg)', fontWeight: 500, fontSize: 14, lineHeight: 1.5, marginTop: 8 }}><Icon name="warn" size={17} color="var(--neg)" style={{ flexShrink: 0, marginTop: 1 }} /> Anyone with these 24 words can restore your data on another device. Keep them private.</div>
    {error ? <div className="eyebrow" style={{ color: 'var(--neg)', marginTop: 26 }}>No fingerprints enrolled</div> : <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 9, marginTop: 22 }}>{T.phrase.map((w, i) => <div key={i} style={{ display: 'flex', alignItems: 'center', gap: 10, background: 'var(--surface)', border: '1px solid var(--hair)', borderRadius: 12, padding: '12px 13px' }}><span className="mono" style={{ color: 'var(--lime)', fontSize: 12.5, fontWeight: 700, minWidth: 16 }}>{i + 1}</span><span style={{ fontSize: 14.5, fontWeight: 500 }}>{w}</span></div>)}</div>}
  </NSub>;
}

Object.assign(window, { NSettings, NAccounts, NCategories, NBudgets, NAutoCapture, NCloudConsent, NRecoveryReveal });
