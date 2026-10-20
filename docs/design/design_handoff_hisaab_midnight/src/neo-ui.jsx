/* ============================================================
   Hisaab Midnight — shared form primitives
   ============================================================ */

function NTopBar({ left, leftLabel, onLeft, title, rightLabel, onRight, rightColor }) {
  return <div style={{ display: 'flex', alignItems: 'center', gap: 12, padding: '14px var(--gut) 12px', minHeight: 56 }}>
    <div style={{ minWidth: 70, display: 'flex' }}>
      {(left || leftLabel) && <button className="tap" onClick={onLeft} style={{ display: 'flex', alignItems: 'center', gap: 4, background: left ? 'var(--glass)' : 'none', border: left ? '1px solid var(--hair)' : 'none', borderRadius: 999, padding: left ? 9 : 0, cursor: 'pointer', color: 'var(--muted)', fontFamily: 'var(--sans)', fontWeight: 600, fontSize: 15.5, whiteSpace: 'nowrap' }}>{left}{leftLabel}</button>}
    </div>
    <div className="disp" style={{ flex: 1, textAlign: 'center', fontSize: 17, fontWeight: 600 }}>{title}</div>
    <div style={{ minWidth: 70, display: 'flex', justifyContent: 'flex-end' }}>
      {rightLabel && <button className="tap" onClick={onRight} style={{ background: 'none', border: 'none', cursor: 'pointer', color: rightColor || 'var(--lime)', fontFamily: 'var(--sans)', fontWeight: 700, fontSize: 15.5, whiteSpace: 'nowrap' }}>{rightLabel}</button>}
    </div>
  </div>;
}

function NScreen({ children, pad = true, style }) {
  return <div className="no-scrollbar" style={{ height: '100%', overflowY: 'auto', padding: pad ? '4px var(--gut) 36px' : 0, animation: 'neo-fade .28s ease', ...style }}>{children}</div>;
}

function NField({ label, value, onChange, placeholder, prefix, type = 'text', mono, big, autoFocus }) {
  const [foc, setFoc] = useState(false);
  return <label style={{ display: 'block' }}>
    {label && <div className="eyebrow" style={{ marginBottom: 9 }}>{label}</div>}
    <div style={{ display: 'flex', alignItems: 'center', gap: 8, background: 'var(--surface)', border: '1.5px solid ' + (foc ? 'var(--lime)' : 'var(--hair)'), borderRadius: 'var(--r-field)', padding: '0 16px', height: big ? 62 : 54, boxShadow: foc ? '0 0 0 4px var(--lime-soft)' : 'none', transition: 'border-color .15s, box-shadow .15s' }}>
      {prefix && <span className="mono" style={{ color: 'var(--lime)', fontWeight: 700, fontSize: big ? 20 : 16 }}>{prefix}</span>}
      <input value={value} type={type} placeholder={placeholder} autoFocus={autoFocus} onFocus={() => setFoc(true)} onBlur={() => setFoc(false)} onChange={e => onChange && onChange(e.target.value)}
        style={{ flex: 1, minWidth: 0, border: 'none', outline: 'none', background: 'transparent', color: 'var(--text)', fontFamily: mono ? 'var(--mono)' : 'var(--sans)', fontSize: big ? 22 : 16.5, fontWeight: 500 }} />
    </div>
  </label>;
}

function NTextArea({ label, value, onChange, placeholder, rows = 3 }) {
  const [foc, setFoc] = useState(false);
  return <label style={{ display: 'block' }}>
    {label && <div className="eyebrow" style={{ marginBottom: 9 }}>{label}</div>}
    <textarea value={value} rows={rows} placeholder={placeholder} onFocus={() => setFoc(true)} onBlur={() => setFoc(false)} onChange={e => onChange && onChange(e.target.value)}
      style={{ width: '100%', resize: 'none', borderRadius: 'var(--r-field)', padding: '14px 16px', background: 'var(--surface)', border: '1.5px solid ' + (foc ? 'var(--lime)' : 'var(--hair)'), color: 'var(--text)', fontFamily: 'var(--sans)', fontSize: 16, outline: 'none', lineHeight: 1.5, boxShadow: foc ? '0 0 0 4px var(--lime-soft)' : 'none' }} />
  </label>;
}

function NToggle({ on, onChange, disabled }) {
  return <button className="tap" disabled={disabled} onClick={() => onChange && onChange(!on)} style={{ width: 50, height: 30, borderRadius: 999, border: 'none', cursor: disabled ? 'default' : 'pointer', background: on ? 'var(--lime)' : 'var(--surface-2)', position: 'relative', flexShrink: 0, opacity: disabled ? .5 : 1, transition: 'background .2s' }}>
    <span style={{ position: 'absolute', top: 3, left: on ? 23 : 3, width: 24, height: 24, borderRadius: '50%', background: on ? 'var(--on-lime)' : '#6b7280', transition: 'left .2s cubic-bezier(.3,1.3,.5,1)' }} />
  </button>;
}

function NCheck({ on, onChange }) {
  return <button className="tap" onClick={() => onChange && onChange(!on)} style={{ width: 26, height: 26, borderRadius: 8, flexShrink: 0, cursor: 'pointer', border: '2px solid ' + (on ? 'var(--lime)' : 'var(--hair)'), background: on ? 'var(--lime)' : 'transparent', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
    {on && <Icon name="check" size={15} color="var(--on-lime)" stroke={2.6} />}
  </button>;
}

function NRadio({ on }) {
  return <span style={{ width: 22, height: 22, borderRadius: '50%', flexShrink: 0, border: '2px solid ' + (on ? 'var(--lime)' : 'var(--hair)'), display: 'flex', alignItems: 'center', justifyContent: 'center' }}>{on && <span style={{ width: 11, height: 11, borderRadius: '50%', background: 'var(--lime)' }} />}</span>;
}

function NRow({ label, sub, value, valueColor, onClick, danger, chevron, trailing }) {
  return <button className={onClick ? 'tap' : ''} onClick={onClick} disabled={!onClick} style={{ display: 'flex', alignItems: 'center', gap: 14, width: '100%', padding: '16px 0', background: 'none', border: 'none', cursor: onClick ? 'pointer' : 'default', textAlign: 'left' }}>
    <div style={{ flex: 1, minWidth: 0 }}>
      <div style={{ fontSize: 16, fontWeight: 500, color: danger ? 'var(--neg)' : 'var(--text)' }}>{label}</div>
      {sub && <div style={{ fontSize: 13, color: 'var(--muted)', marginTop: 3 }}>{sub}</div>}
    </div>
    {trailing}
    {value != null && <span style={{ fontSize: 15.5, color: valueColor || 'var(--muted)', fontWeight: 500 }}>{value}</span>}
    {chevron && <Icon name="chevron-right" size={19} color="var(--faint)" />}
  </button>;
}

function NSheet({ open, onClose, title, children }) {
  if (!open) return null;
  return <div onClick={onClose} style={{ position: 'absolute', inset: 0, zIndex: 60, background: 'rgba(0,0,0,.6)', display: 'flex', alignItems: 'flex-end', animation: 'neo-fade .2s' }}>
    <div onClick={e => e.stopPropagation()} className="no-scrollbar" style={{ width: '100%', background: 'var(--bg-2)', borderRadius: '26px 26px 0 0', borderTop: '1px solid var(--hair)', padding: '14px 20px 28px', maxHeight: '88%', overflowY: 'auto', animation: 'neo-up .28s cubic-bezier(.2,.9,.2,1)' }}>
      <div style={{ width: 38, height: 4, borderRadius: 99, background: 'var(--hair)', margin: '0 auto 16px' }} />
      {title && <div className="eyebrow" style={{ marginBottom: 16 }}>{title}</div>}
      {children}
    </div>
  </div>;
}

function NDialog({ open, onClose, children }) {
  if (!open) return null;
  return <div onClick={onClose} style={{ position: 'absolute', inset: 0, zIndex: 70, background: 'rgba(0,0,0,.65)', display: 'flex', alignItems: 'center', justifyContent: 'center', padding: 24, animation: 'neo-fade .2s' }}>
    <div onClick={e => e.stopPropagation()} style={{ width: '100%', background: 'var(--bg-2)', border: '1px solid var(--hair)', borderRadius: 24, padding: 24, animation: 'neo-fade .22s' }}>{children}</div>
  </div>;
}

function NSeg({ options, value, onChange, scroll }) {
  return <div className="no-scrollbar" style={{ display: 'flex', gap: 6, padding: 5, background: 'var(--bg-2)', borderRadius: 999, overflowX: scroll ? 'auto' : 'visible' }}>
    {options.map(o => { const v = o.value ?? o; const on = v === value; return <button key={v} className="tap" onClick={() => onChange(v)} style={{ flex: scroll ? '0 0 auto' : 1, border: 'none', cursor: 'pointer', padding: '10px 18px', borderRadius: 999, whiteSpace: 'nowrap', background: on ? 'var(--text)' : 'transparent', color: on ? 'var(--bg)' : 'var(--muted)', fontFamily: 'var(--sans)', fontWeight: 700, fontSize: 14.5 }}>{o.label ?? o}</button>; })}
  </div>;
}

function NSub({ ctx, title, rightLabel, onRight, children }) {
  return <div style={{ height: '100%', display: 'flex', flexDirection: 'column' }}>
    <NTopBar left={<Icon name="chevron-left" size={20} />} leftLabel="" onLeft={() => ctx.back()} title={title} rightLabel={rightLabel} onRight={onRight} />
    <NScreen style={{ padding: '0 var(--gut) 36px' }}>{children}</NScreen>
  </div>;
}

function NSection({ label, children, top = 30 }) {
  return <div style={{ marginTop: top }}><div className="eyebrow" style={{ marginBottom: 12 }}>{label}</div>{children}</div>;
}
function nhair() { return <div style={{ height: 1, background: 'var(--hair-2)' }} />; }

Object.assign(window, { NTopBar, NScreen, NField, NTextArea, NToggle, NCheck, NRadio, NRow, NSheet, NDialog, NSeg, NSub, NSection, nhair });
