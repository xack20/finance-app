/* ============================================================
   Hisaab Midnight — onboarding & lock
   ============================================================ */

function NFlow({ children, footer, top }) {
  return <div className="no-scrollbar" style={{ height: '100%', overflowY: 'auto', display: 'flex', flexDirection: 'column', padding: '0 var(--gut)', animation: 'neo-fade .28s ease' }}>
    {top}
    <div style={{ flex: 1, display: 'flex', flexDirection: 'column', justifyContent: 'center', minHeight: 0, padding: '24px 0' }}>{children}</div>
    {footer && <div style={{ paddingBottom: 24 }}>{footer}</div>}
  </div>;
}

function NSplash({ ctx }) {
  useEffect(() => { const t = setTimeout(() => ctx.setPhase('onboarding'), 1400); return () => clearTimeout(t); }, []);
  return <div style={{ height: '100%', display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', gap: 26 }}>
    <div className="disp" style={{ fontSize: 58, color: 'var(--lime)', fontWeight: 700, animation: 'neo-fade .6s ease' }}>হিসাব</div>
    <div style={{ display: 'flex', gap: 6 }}>{[0, 1, 2].map(i => <span key={i} style={{ width: 7, height: 7, borderRadius: 99, background: 'var(--lime)', animation: 'neo-pulse 1.1s ease-in-out infinite', animationDelay: i * .18 + 's' }} />)}</div>
  </div>;
}

function NWelcome({ ctx, demoError }) {
  const [phone, setPhone] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(demoError ? "Couldn't send code. Try again." : null);
  const valid = phone.replace(/\D/g, '').length >= 9;
  const submit = () => { if (!valid) return; setError(null); setLoading(true); setTimeout(() => { setLoading(false); ctx.go('otp', { phone: '+880' + phone }); }, 950); };
  return <NFlow footer={<div>
    {error && <div style={{ color: 'var(--neg)', fontWeight: 600, fontSize: 14, marginBottom: 12 }}>{error}</div>}
    <NField label="Phone number" prefix="+880" value={phone} onChange={v => setPhone(v.replace(/[^\d ]/g, ''))} placeholder="1X XXXX XXXX" mono big />
    <div style={{ height: 14 }} />
    <NBtn onClick={submit} disabled={!valid}>{loading ? <Spinner color="var(--on-lime)" /> : 'Continue'}</NBtn>
  </div>}>
    <div style={{ display: 'flex', alignItems: 'baseline', gap: 10, marginBottom: 30 }}>
      <span className="disp" style={{ fontSize: 26, color: 'var(--lime)', fontWeight: 700 }}>হিসাব</span>
      <span style={{ width: 4, height: 4, borderRadius: 99, background: 'var(--faint)' }} />
      <span className="eyebrow" style={{ letterSpacing: '.28em' }}>Hisaab</span>
    </div>
    <h1 className="disp" style={{ fontSize: 46, fontWeight: 600, margin: 0, lineHeight: 1.04 }}>Your money,<br />only yours.</h1>
    <p style={{ color: 'var(--muted)', fontSize: 17, lineHeight: 1.5, margin: '18px 0 0', maxWidth: 300 }}>Privacy-first finance for Bangladesh. Everything stays on your device.</p>
    <div style={{ display: 'inline-flex', alignItems: 'center', gap: 8, marginTop: 22, padding: '9px 14px', borderRadius: 999, background: 'var(--lime-soft)', alignSelf: 'flex-start' }}>
      <Icon name="shield" size={15} color="var(--lime)" /><span style={{ fontSize: 13, fontWeight: 700, color: 'var(--lime)' }}>End-to-end private, on-device</span>
    </div>
  </NFlow>;
}

function NOtp({ ctx, params, demoError }) {
  const [code, setCode] = useState('');
  const [error] = useState(demoError ? 'Invalid code' : null);
  const phone = (params && params.phone) || '+8801712345678';
  const keys = ['1', '2', '3', '4', '5', '6', '7', '8', '9', '', '0', '⌫'];
  const valid = code.length === 6;
  return <NFlow top={<NTopBar left={<Icon name="chevron-left" size={20} />} onLeft={() => ctx.back()} />} footer={<div>
    <NBtn onClick={() => valid && ctx.go('biometric')} disabled={!valid}>Verify</NBtn>
    <div style={{ textAlign: 'center', marginTop: 12 }}><button className="tap" style={{ background: 'none', border: 'none', color: 'var(--muted)', fontWeight: 600, fontSize: 15, cursor: 'pointer', fontFamily: 'var(--sans)' }}>Resend code</button></div>
  </div>}>
    <div className="eyebrow" style={{ color: 'var(--lime)' }}>Verify</div>
    <h1 className="disp" style={{ fontSize: 32, fontWeight: 600, marginTop: 12 }}>Enter the code<br />we sent you</h1>
    <p className="mono" style={{ color: 'var(--muted)', fontSize: 14, marginTop: 12 }}>Sent to {phone}</p>
    <div style={{ marginTop: 28, display: 'flex', gap: 9 }}>
      {[0, 1, 2, 3, 4, 5].map(i => <div key={i} style={{ flex: 1, height: 58, borderRadius: 14, display: 'flex', alignItems: 'center', justifyContent: 'center', background: 'var(--surface)', border: '1.5px solid ' + (code.length === i ? 'var(--lime)' : error ? 'var(--neg)' : 'var(--hair)'), boxShadow: code.length === i ? '0 0 0 4px var(--lime-soft)' : 'none', fontFamily: 'var(--mono)', fontSize: 24, fontWeight: 700 }}>{code[i] || ''}</div>)}
    </div>
    {error && <div style={{ color: 'var(--neg)', fontWeight: 600, fontSize: 14, marginTop: 14 }}>{error}</div>}
    <div style={{ display: 'grid', gridTemplateColumns: 'repeat(3,1fr)', gap: 8, marginTop: 26 }}>
      {keys.map((k, i) => k === '' ? <div key={i} /> : <button key={i} className="tap" onClick={() => { if (k === '⌫') setCode(c => c.slice(0, -1)); else if (code.length < 6) setCode(c => c + k); }} style={{ height: 50, borderRadius: 14, border: '1px solid var(--hair)', background: 'var(--surface)', fontFamily: 'var(--display)', fontSize: 20, fontWeight: 600, color: 'var(--text)', cursor: 'pointer' }}>{k}</button>)}
    </div>
  </NFlow>;
}

function NBiometric({ ctx, unavailable }) {
  return <NFlow footer={<div>
    <NBtn onClick={() => !unavailable && ctx.go('recovery')} disabled={unavailable}>{unavailable ? 'Not available on this device' : 'Enable biometric'}</NBtn>
    <div style={{ textAlign: 'center', marginTop: 12 }}><button className="tap" onClick={() => ctx.go('recovery')} style={{ background: 'none', border: 'none', color: 'var(--muted)', fontWeight: 600, fontSize: 15, cursor: 'pointer', fontFamily: 'var(--sans)' }}>Skip (not recommended)</button></div>
  </div>}>
    <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', textAlign: 'center' }}>
      <div style={{ width: 96, height: 96, borderRadius: 28, background: 'var(--lime-soft)', display: 'flex', alignItems: 'center', justifyContent: 'center', marginBottom: 28, animation: 'neo-glow 3s ease-in-out infinite' }}><Icon name="finger" size={48} color="var(--lime)" stroke={1.7} /></div>
      <div className="eyebrow" style={{ color: 'var(--lime)' }}>Secure</div>
      <h1 className="disp" style={{ fontSize: 30, fontWeight: 600, marginTop: 12 }}>Unlock with your<br />face or finger</h1>
      <p style={{ color: 'var(--muted)', fontSize: 16, lineHeight: 1.5, marginTop: 14, maxWidth: 280 }}>Your biometric unlocks Hisaab. Your data never leaves this device.</p>
    </div>
  </NFlow>;
}

function NRecovery({ ctx }) {
  const [ack, setAck] = useState(false);
  return <div className="no-scrollbar" style={{ height: '100%', overflowY: 'auto', padding: '8px var(--gut) 0', display: 'flex', flexDirection: 'column', animation: 'neo-fade .28s' }}>
    <div className="eyebrow" style={{ color: 'var(--lime)' }}>Recovery</div>
    <h1 className="disp" style={{ fontSize: 28, fontWeight: 600, marginTop: 10 }}>Write these 24 words down</h1>
    <div style={{ display: 'inline-flex', alignItems: 'center', gap: 7, marginTop: 12, color: 'var(--neg)', fontWeight: 600, fontSize: 14 }}><Icon name="warn" size={16} color="var(--neg)" /> Lose these = lose your data</div>
    <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 9, marginTop: 18 }}>
      {T.phrase.map((w, i) => <div key={i} style={{ display: 'flex', alignItems: 'center', gap: 10, background: 'var(--surface)', border: '1px solid var(--hair)', borderRadius: 12, padding: '12px 13px' }}><span className="mono" style={{ color: 'var(--lime)', fontSize: 12, fontWeight: 700, minWidth: 16 }}>{i + 1}</span><span style={{ fontSize: 14.5, fontWeight: 500 }}>{w}</span></div>)}
    </div>
    <div style={{ position: 'sticky', bottom: 0, paddingBottom: 22, paddingTop: 16, marginTop: 18, background: 'linear-gradient(to top, var(--bg) 72%, transparent)' }}>
      <div role="button" className="tap" onClick={() => setAck(a => !a)} style={{ display: 'flex', alignItems: 'center', gap: 14, width: '100%', background: 'var(--surface)', border: '1px solid var(--hair)', borderRadius: 14, padding: 16, cursor: 'pointer', textAlign: 'left', marginBottom: 12, boxSizing: 'border-box' }}>
        <NCheck on={ack} onChange={setAck} /><span style={{ fontSize: 15, fontWeight: 500 }}>I've written down all 24 words in a safe place</span>
      </div>
      <NBtn onClick={() => ack && ctx.go('profile')} disabled={!ack}>I've saved them</NBtn>
    </div>
  </div>;
}

function NProfile({ ctx }) {
  const [name, setName] = useState('');
  const [lang, setLang] = useState('en');
  return <NFlow footer={<NBtn onClick={() => name.trim() && ctx.enterApp()} disabled={!name.trim()}>Start Hisaab &nbsp;→</NBtn>}>
    <div className="eyebrow" style={{ color: 'var(--lime)' }}>Almost done</div>
    <h1 className="disp" style={{ fontSize: 32, fontWeight: 600, marginTop: 12 }}>What should<br />we call you?</h1>
    <div style={{ marginTop: 30 }}><NField label="Your name" value={name} onChange={setName} placeholder="Name" big autoFocus /></div>
    <div style={{ marginTop: 24 }}>
      <div className="eyebrow" style={{ marginBottom: 10 }}>Language</div>
      <div style={{ display: 'flex', gap: 10 }}>
        {[['en', 'English'], ['bn', 'বাংলা']].map(([v, l]) => <button key={v} className="tap" onClick={() => setLang(v)} style={{ flex: 1, height: 54, borderRadius: 999, cursor: 'pointer', fontFamily: 'var(--sans)', fontWeight: 700, fontSize: 16, border: '1.5px solid ' + (lang === v ? 'transparent' : 'var(--hair)'), background: lang === v ? 'var(--lime)' : 'transparent', color: lang === v ? 'var(--on-lime)' : 'var(--text)' }}>{l}</button>)}
      </div>
    </div>
  </NFlow>;
}

function NCaptureOptIn({ ctx }) {
  return <NScreen style={{ display: 'flex', flexDirection: 'column', justifyContent: 'center' }}>
    <Card style={{ padding: 22 }}>
      <span style={{ width: 44, height: 44, borderRadius: 13, background: 'var(--lime-soft)', display: 'flex', alignItems: 'center', justifyContent: 'center' }}><Icon name="sms" size={22} color="var(--lime)" /></span>
      <div style={{ fontSize: 18, fontWeight: 700, margin: '16px 0 10px' }}>Log transactions automatically</div>
      <p style={{ color: 'var(--muted)', fontSize: 14.5, lineHeight: 1.55, margin: '0 0 18px' }}>Hisaab reads your bKash, Nagad and bank SMS to log transactions for you — on-device by default, fully private. Turn off anytime.</p>
      <div style={{ display: 'flex', gap: 10 }}>
        <NBtn onClick={() => ctx.enterApp()} size="md">Turn on</NBtn>
        <NBtn kind="glass" size="md" onClick={() => ctx.enterApp()}>Maybe later</NBtn>
      </div>
    </Card>
  </NScreen>;
}

function NLock({ ctx, noEnroll }) {
  return <div style={{ height: '100%', display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', padding: '0 var(--gut)', animation: 'neo-fade .28s' }}>
    <div style={{ width: 84, height: 84, borderRadius: 26, background: 'var(--surface)', border: '1px solid var(--hair)', display: 'flex', alignItems: 'center', justifyContent: 'center', marginBottom: 30 }}><Icon name="lock" size={36} color="var(--lime)" /></div>
    <div className="disp" style={{ fontSize: 30, color: 'var(--lime)', fontWeight: 700, marginBottom: 8 }}>হিসাব</div>
    <h1 className="disp" style={{ fontSize: 24, fontWeight: 600 }}>Hisaab is locked</h1>
    {noEnroll && <div className="eyebrow" style={{ color: 'var(--neg)', marginTop: 14 }}>No fingerprints enrolled</div>}
    <div style={{ height: 28 }} />
    <div style={{ width: 260 }}><NBtn icon="finger" onClick={() => ctx.enterApp()}>Unlock with biometric</NBtn></div>
  </div>;
}

function NRecoveryEntry({ ctx }) {
  const [words, setWords] = useState(Array(24).fill(''));
  const filled = words.filter(w => w.trim()).length;
  return <div className="no-scrollbar" style={{ height: '100%', overflowY: 'auto', padding: '8px var(--gut) 0', display: 'flex', flexDirection: 'column', animation: 'neo-fade .28s' }}>
    <div className="eyebrow" style={{ color: 'var(--lime)' }}>Recover</div>
    <h1 className="disp" style={{ fontSize: 28, fontWeight: 600, marginTop: 10 }}>Enter your 24-word phrase</h1>
    <p className="mono" style={{ color: 'var(--muted)', fontSize: 13, lineHeight: 1.5, marginTop: 12 }}>We never store this. It must match the phrase from setup.</p>
    <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 9, marginTop: 18 }}>
      {words.map((w, i) => <div key={i} style={{ display: 'flex', alignItems: 'center', gap: 8, background: 'var(--surface)', border: '1.5px solid var(--hair)', borderRadius: 12, padding: '0 12px', height: 48 }}><span className="mono" style={{ color: 'var(--lime)', fontSize: 12, fontWeight: 700, minWidth: 15 }}>{i + 1}</span><input value={w} onChange={e => setWords(ws => ws.map((x, j) => j === i ? e.target.value : x))} style={{ flex: 1, minWidth: 0, border: 'none', outline: 'none', background: 'transparent', color: 'var(--text)', fontFamily: 'var(--sans)', fontSize: 14.5, fontWeight: 500 }} /></div>)}
    </div>
    <div style={{ position: 'sticky', bottom: 0, paddingBottom: 22, paddingTop: 14, marginTop: 16, background: 'linear-gradient(to top, var(--bg) 76%, transparent)' }}>
      <NBtn onClick={() => ctx.enterApp()} disabled={filled < 24}>{filled < 24 ? `Enter all 24 words (${filled}/24)` : 'Restore'}</NBtn>
    </div>
  </div>;
}

Object.assign(window, { NSplash, NWelcome, NOtp, NBiometric, NRecovery, NProfile, NCaptureOptIn, NLock, NRecoveryEntry });
