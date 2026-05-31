/* ============================================================
   Hisaab Midnight — full router, catalog, persistence
   ============================================================ */

function NRender({ name, params, ctx }) {
  const p = params || {};
  switch (name) {
    case 'splash': return <NSplash ctx={ctx} />;
    case 'welcome': return <NWelcome ctx={ctx} demoError={p.demoError} />;
    case 'otp': return <NOtp ctx={ctx} params={p} demoError={p.demoError} />;
    case 'biometric': return <NBiometric ctx={ctx} unavailable={p.unavailable} />;
    case 'recovery': return <NRecovery ctx={ctx} />;
    case 'profile': return <NProfile ctx={ctx} />;
    case 'capture-optin': return <NCaptureOptIn ctx={ctx} />;
    case 'lock': return <NLock ctx={ctx} noEnroll={p.noEnroll} />;
    case 'recovery-entry': return <NRecoveryEntry ctx={ctx} />;
    case 'today': return <TodayNeo go={ctx.go} />;
    case 'month': return <MonthNeo />;
    case 'people': return <NPeople ctx={ctx} />;
    case 'settings': return <NSettings ctx={ctx} />;
    case 'entry': return <EntryNeo close={ctx.back} />;
    case 'agent': return <AssistantNeo ctx={ctx} variant={p.variant} />;
    case 'agent-consent': return <NConsent ctx={ctx} />;
    case 'txn': return <NTxnDetail ctx={ctx} params={p} />;
    case 'review': return <NReviewInbox ctx={ctx} />;
    case 'review-card': return <NReviewCard ctx={ctx} />;
    case 'person': return <NPersonDetail ctx={ctx} params={p} />;
    case 'settings/accounts': return <NAccounts ctx={ctx} />;
    case 'settings/categories': return <NCategories ctx={ctx} />;
    case 'settings/budgets': return <NBudgets ctx={ctx} />;
    case 'settings/auto-capture': return <NAutoCapture ctx={ctx} ios={p.ios} permDenied={p.permissionDenied} />;
    case 'settings/auto-capture/consent': return <NCloudConsent ctx={ctx} />;
    case 'settings/recovery': return <NRecoveryReveal ctx={ctx} error={p.error} />;
    default: return <div style={{ padding: 40, color: 'var(--muted)' }}>?</div>;
  }
}

function FabChooser({ open, onClose, ctx }) {
  return <NSheet open={open} onClose={onClose}>
    <button className="tap" onClick={() => { onClose(); ctx.go('entry'); }} style={{ display: 'flex', alignItems: 'center', gap: 16, padding: '16px 6px', background: 'none', border: 'none', cursor: 'pointer', textAlign: 'left', width: '100%' }}>
      <span style={{ width: 46, height: 46, borderRadius: 14, background: 'var(--lime-soft)', display: 'flex', alignItems: 'center', justifyContent: 'center' }}><Icon name="edit" size={22} color="var(--lime)" /></span>
      <div><div style={{ fontSize: 16.5, fontWeight: 600 }}>Add manually</div><div style={{ fontSize: 13, color: 'var(--muted)', marginTop: 2 }}>Tactile keypad entry</div></div>
    </button>
    <button className="tap" onClick={() => { onClose(); ctx.go('agent'); }} style={{ display: 'flex', alignItems: 'center', gap: 16, padding: '16px 6px', background: 'none', border: 'none', cursor: 'pointer', textAlign: 'left', width: '100%' }}>
      <span style={{ width: 46, height: 46, borderRadius: 14, background: 'var(--lime-soft)', display: 'flex', alignItems: 'center', justifyContent: 'center' }}><Icon name="sparkle" size={22} color="var(--lime)" /></span>
      <div><div style={{ fontSize: 16.5, fontWeight: 600 }}>Ask the assistant</div><div style={{ fontSize: 13, color: 'var(--muted)', marginTop: 2 }}>Describe it in words</div></div>
    </button>
  </NSheet>;
}

function NeoRoot() {
  const saved = (() => { try { return JSON.parse(localStorage.getItem('hisaab_neo') || 'null'); } catch (e) { return null; } })();
  const [phase, setPhase] = useState(saved ? saved.phase : 'splash');
  const [onb, setOnb] = useState(saved && saved.onb ? saved.onb : [{ screen: 'welcome' }]);
  const [tab, setTab] = useState(saved ? saved.tab : 'today');
  const [stack, setStack] = useState(saved && saved.stack ? saved.stack : []);
  const [chooser, setChooser] = useState(false);
  const [scale, setScale] = useState(1);
  useEffect(() => { const f = () => setScale(Math.min(1, (window.innerHeight - 40) / 832)); f(); window.addEventListener('resize', f); return () => window.removeEventListener('resize', f); }, []);
  useEffect(() => { try { localStorage.setItem('hisaab_neo', JSON.stringify({ phase, onb, tab, stack })); } catch (e) {} }, [phase, onb, tab, stack]);

  const ctx = {
    setPhase: ph => { setPhase(ph); if (ph === 'onboarding') setOnb([{ screen: 'welcome' }]); },
    go: (screen, params) => { if (phase === 'onboarding') setOnb(s => [...s, { screen, params }]); else setStack(s => [...s, { screen, params }]); },
    replace: (screen, params) => { if (phase === 'onboarding') setOnb(s => [...s.slice(0, -1), { screen, params }]); else setStack(s => [...s.slice(0, -1), { screen, params }]); },
    back: () => { if (phase === 'onboarding') setOnb(s => s.length > 1 ? s.slice(0, -1) : s); else setStack(s => s.slice(0, -1)); },
    tab: t => { setTab(t); setStack([]); },
    enterApp: route => { setPhase('auth'); setTab(route && route.startsWith('settings') ? 'settings' : 'today'); setStack(route ? [{ screen: route }] : []); },
  };
  const jump = spec => { setPhase(spec.phase); if (spec.onb) setOnb(spec.onb); if (spec.tab) setTab(spec.tab); setStack(spec.stack || []); };

  let body, showDock = false;
  if (phase === 'splash') body = <NRender name="splash" ctx={ctx} />;
  else if (phase === 'onboarding') { const t = onb[onb.length - 1]; body = <NRender name={t.screen} params={t.params} ctx={ctx} />; }
  else if (phase === 'lock') body = <NRender name="lock" params={stack[0] && stack[0].params} ctx={ctx} />;
  else if (phase === 'recovery') body = <NRender name="recovery-entry" ctx={ctx} />;
  else { if (stack.length) { const t = stack[stack.length - 1]; body = <NRender name={t.screen} params={t.params} ctx={ctx} />; } else { body = <NRender name={tab} ctx={ctx} />; showDock = true; } }

  return <div style={{ minHeight: '100vh', display: 'flex' }}>
    <NeoCatalog jump={jump} />
    <div style={{ flex: 1, display: 'flex', alignItems: 'center', justifyContent: 'center', padding: 20, background: 'radial-gradient(120% 90% at 50% -10%, #15171d 0%, #060708 70%)', overflow: 'hidden' }}>
      <div style={{ transform: 'scale(' + scale + ')', transformOrigin: 'center' }}>
        <NeoPhone>
          <div style={{ flex: 1, minHeight: 0, position: 'relative' }}>{body}</div>
          {showDock && <Dock tab={tab} onTab={ctx.tab} onAdd={() => setChooser(true)} />}
          <FabChooser open={chooser} onClose={() => setChooser(false)} ctx={ctx} />
        </NeoPhone>
      </div>
    </div>
  </div>;
}

const NEO_CATALOG = [
  ['Onboarding', [
    ['Splash', { phase: 'splash' }], ['Welcome', { phase: 'onboarding', onb: [{ screen: 'welcome' }] }],
    ['Welcome · error', { phase: 'onboarding', onb: [{ screen: 'welcome', params: { demoError: true } }] }],
    ['OTP', { phase: 'onboarding', onb: [{ screen: 'otp' }] }], ['OTP · error', { phase: 'onboarding', onb: [{ screen: 'otp', params: { demoError: true } }] }],
    ['Biometric', { phase: 'onboarding', onb: [{ screen: 'biometric' }] }], ['Biometric · unavailable', { phase: 'onboarding', onb: [{ screen: 'biometric', params: { unavailable: true } }] }],
    ['Recovery phrase', { phase: 'onboarding', onb: [{ screen: 'recovery' }] }], ['Profile', { phase: 'onboarding', onb: [{ screen: 'profile' }] }],
    ['Capture opt-in', { phase: 'onboarding', onb: [{ screen: 'capture-optin' }] }],
  ]],
  ['Lock & recovery', [
    ['Lock', { phase: 'lock', stack: [] }], ['Lock · no biometric', { phase: 'lock', stack: [{ screen: 'lock', params: { noEnroll: true } }] }],
    ['Restore from phrase', { phase: 'recovery' }],
  ]],
  ['Home', [
    ['Today', { phase: 'auth', tab: 'today' }], ['Insights (Month)', { phase: 'auth', tab: 'month' }],
  ]],
  ['Entry & detail', [
    ['New entry (keypad)', { phase: 'auth', tab: 'today', stack: [{ screen: 'entry' }] }],
    ['Transaction detail', { phase: 'auth', tab: 'today', stack: [{ screen: 'txn', params: { id: 't1' } }] }],
    ['Transaction · captured', { phase: 'auth', tab: 'today', stack: [{ screen: 'txn', params: { id: 't3' } }] }],
  ]],
  ['Assistant & review', [
    ['Assistant', { phase: 'auth', tab: 'today', stack: [{ screen: 'agent' }] }],
    ['Assistant · empty', { phase: 'auth', tab: 'today', stack: [{ screen: 'agent', params: { variant: 'empty' } }] }],
    ['Assistant · unavailable', { phase: 'auth', tab: 'today', stack: [{ screen: 'agent', params: { variant: 'unavailable' } }] }],
    ['Assistant · consent', { phase: 'auth', tab: 'today', stack: [{ screen: 'agent-consent' }] }],
    ['Review card', { phase: 'auth', tab: 'today', stack: [{ screen: 'review-card' }] }],
    ['Review inbox', { phase: 'auth', tab: 'today', stack: [{ screen: 'review' }] }],
  ]],
  ['People', [
    ['People', { phase: 'auth', tab: 'people' }], ['Person detail', { phase: 'auth', tab: 'people', stack: [{ screen: 'person', params: { id: 'p_rafi' } }] }],
  ]],
  ['Settings', [
    ['Settings', { phase: 'auth', tab: 'settings' }], ['Accounts', { phase: 'auth', tab: 'settings', stack: [{ screen: 'settings/accounts' }] }],
    ['Categories', { phase: 'auth', tab: 'settings', stack: [{ screen: 'settings/categories' }] }], ['Budgets', { phase: 'auth', tab: 'settings', stack: [{ screen: 'settings/budgets' }] }],
    ['Auto-capture', { phase: 'auth', tab: 'settings', stack: [{ screen: 'settings/auto-capture' }] }],
    ['Auto-capture · permission', { phase: 'auth', tab: 'settings', stack: [{ screen: 'settings/auto-capture', params: { permissionDenied: true } }] }],
    ['Auto-capture · iOS', { phase: 'auth', tab: 'settings', stack: [{ screen: 'settings/auto-capture', params: { ios: true } }] }],
    ['Cloud consent', { phase: 'auth', tab: 'settings', stack: [{ screen: 'settings/auto-capture/consent' }] }],
    ['Recovery reveal', { phase: 'auth', tab: 'settings', stack: [{ screen: 'settings/recovery' }] }],
  ]],
];

function NeoCatalog({ jump }) {
  const [active, setActive] = useState('Today');
  return <div className="no-scrollbar" style={{ width: 244, flexShrink: 0, height: '100vh', overflowY: 'auto', background: '#0c0d11', borderRight: '1px solid var(--hair)', padding: '22px 14px 40px' }}>
    <div style={{ display: 'flex', alignItems: 'baseline', gap: 8, padding: '0 6px' }}><span className="disp" style={{ fontSize: 22, color: 'var(--lime)', fontWeight: 700 }}>হিসাব</span><span style={{ fontSize: 12, fontWeight: 700, letterSpacing: '.2em', color: 'var(--faint)' }}>HISAAB</span></div>
    <div style={{ fontSize: 11.5, color: 'var(--faint)', margin: '4px 6px 20px' }}>Midnight · complete build</div>
    {NEO_CATALOG.map(([group, items]) => <div key={group} style={{ marginBottom: 16 }}>
      <div style={{ fontSize: 10.5, fontWeight: 700, letterSpacing: '.14em', textTransform: 'uppercase', color: '#5a6170', margin: '0 6px 7px' }}>{group}</div>
      <div style={{ display: 'flex', flexDirection: 'column', gap: 1 }}>
        {items.map(([label, spec]) => <button key={label} onClick={() => { setActive(label); jump(spec); }} style={{ textAlign: 'left', padding: '7px 10px', borderRadius: 8, cursor: 'pointer', border: 'none', fontFamily: 'var(--sans)', fontSize: 13.5, fontWeight: active === label ? 600 : 450, background: active === label ? 'var(--lime-soft)' : 'transparent', color: active === label ? 'var(--lime)' : '#aab2bf' }}>{label}</button>)}
      </div>
    </div>)}
  </div>;
}

ReactDOM.createRoot(document.getElementById('neo-root')).render(<NeoRoot />);
