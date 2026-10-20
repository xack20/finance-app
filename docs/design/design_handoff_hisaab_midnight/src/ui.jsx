/* ============================================================
   Hisaab — shared UI primitives, icons, phone frame
   Exports to window. Plain inline styles + CSS vars.
   ============================================================ */
const { useState, useRef, useEffect, useLayoutEffect } = React;

/* ---------------- Icons (minimal line set) ---------------- */
const ICON_PATHS = {
  'chevron-right': 'M9 5l7 7-7 7',
  'chevron-left':  'M15 5l-7 7 7 7',
  'chevron-down':  'M5 9l7 7 7-7',
  'plus':          'M12 5v14M5 12h14',
  'check':         'M4 12.5l5 5 11-11',
  'close':         'M5 5l14 14M19 5L5 19',
  'arrow-up':      'M12 19V5M5 12l7-7 7 7',
  'arrow-right':   'M5 12h14M13 6l6 6-6 6',
  'lend':          'M6 18L18 6M10 6h8v8',          /* up-right */
  'borrow':        'M18 6L6 18M6 10v8h8',          /* down-left */
  'swap':          'M7 7h11l-3-3M17 17H6l3 3',
  'lock':          'M6 10V8a6 6 0 0112 0v2M5 10h14v10H5z',
  'finger':        'M8 11a4 4 0 018 0v3M8 14v2a4 4 0 004 4M12 11v5M16 13v2',
  'mic':           'M12 3a3 3 0 00-3 3v5a3 3 0 006 0V6a3 3 0 00-3-3zM5 11a7 7 0 0014 0M12 18v3',
  'gear':          'M12 9a3 3 0 100 6 3 3 0 000-6zM19.4 13a1 1 0 00.2 1.1l.1.1a2 2 0 11-2.8 2.8l-.1-.1a1 1 0 00-1.1-.2 1 1 0 00-.6.9V19a2 2 0 11-4 0v-.1a1 1 0 00-.6-.9 1 1 0 00-1.1.2l-.1.1a2 2 0 11-2.8-2.8l.1-.1a1 1 0 00.2-1.1 1 1 0 00-.9-.6H5a2 2 0 110-4h.1a1 1 0 00.9-.6 1 1 0 00-.2-1.1l-.1-.1a2 2 0 112.8-2.8l.1.1a1 1 0 001.1.2H12a1 1 0 00.6-.9V5a2 2 0 114 0v.1a1 1 0 00.6.9 1 1 0 001.1-.2l.1-.1a2 2 0 112.8 2.8l-.1.1a1 1 0 00-.2 1.1V12a1 1 0 00.9.6H19a2 2 0 110 4h-.1a1 1 0 00-.9.6z',
  'today':         'M12 12m-2 0a2 2 0 104 0a2 2 0 10-4 0',
  'month':         'M4 6h7v5H4zM13 6h7v5h-7zM4 13h7v5H4zM13 13h7v5h-7z',
  'people':        'M9 11a3.5 3.5 0 100-7 3.5 3.5 0 000 7zM3 20a6 6 0 0112 0M17 11a3 3 0 100-6M21 20a6 6 0 00-4-5.6',
  'search':        'M11 11m-7 0a7 7 0 1014 0a7 7 0 10-14 0M20 20l-4-4',
  'camera':        'M3 8h3l1.5-2h9L17 8h4v11H3zM12 16a3.5 3.5 0 100-7 3.5 3.5 0 000 7z',
  'warn':          'M12 4l9 16H3zM12 10v4M12 17.5v.5',
  'shield':        'M12 3l8 3v6c0 5-3.5 8-8 9-4.5-1-8-4-8-9V6z',
  'eye':           'M2 12s3.5-7 10-7 10 7 10 7-3.5 7-10 7-10-7-10-7zM12 9a3 3 0 100 6 3 3 0 000-6z',
  'trash':         'M5 7h14M10 7V5h4v2M6 7l1 13h10l1-13',
  'edit':          'M4 20h4L19 9l-4-4L4 16zM14 6l4 4',
  'tag':           'M3 12V4h8l9 9-8 8zM7.5 7.5h.01',
  'calendar':      'M5 7h14v13H5zM5 11h14M8 4v4M16 4v4',
  'receipt':       'M6 3h12v18l-2.5-1.5L13 21l-2.5-1.5L8 21 6 19.5V3zM9 8h6M9 12h6',
  'book':          'M5 5a2 2 0 012-2h11v15H7a2 2 0 00-2 2zM5 19a2 2 0 012-2h11',
  'film':          'M4 4h16v16H4zM4 8h16M4 16h16M8 4v16M16 4v16',
  'food':          'M5 3v7a2 2 0 004 0V3M7 12v9M17 3c-1.5 0-2.5 2-2.5 5s1 4 2.5 4v9',
  'cart':          'M4 5h2l2 11h10l2-8H7M9 20a1 1 0 100-2 1 1 0 000 2zM18 20a1 1 0 100-2 1 1 0 000 2z',
  'health':        'M12 8v8M8 12h8M12 3a9 9 0 100 18 9 9 0 000-18z',
  'case':          'M3 8h18v11H3zM8 8V6a2 2 0 012-2h4a2 2 0 012 2v2',
  'bag':           'M5 8h14l-1 12H6zM9 8V6a3 3 0 016 0v2',
  'car':           'M4 13l1.5-5h13L20 13M4 13h16v5H4zM7 18v1M17 18v1M7 15.5h.01M17 15.5h.01',
  'dot':           'M12 12m-3 0a3 3 0 106 0a3 3 0 10-6 0',
  'sparkle':       'M12 3l1.8 5.2L19 10l-5.2 1.8L12 17l-1.8-5.2L5 10l5.2-1.8zM18 16l.7 2 2 .7-2 .7-.7 2-.7-2-2-.7 2-.7z',
  'sms':           'M4 5h16v11H8l-4 4zM8 9h8M8 12h5',
  'in':            'M12 5v9M7 11l5 4 5-4M5 19h14',
  'cloud':         'M7 18a4 4 0 010-8 5 5 0 019.6-1.3A3.5 3.5 0 0117 18z',
  'key':           'M14 7a3 3 0 11-2 5l-7 7H3v-2l7-7a3 3 0 014-3zM15.5 8.5h.01',
  'wallet':        'M4 7h13a2 2 0 012 2v8a2 2 0 01-2 2H5a2 2 0 01-2-2V6a2 2 0 012-2h11M17 13h.01',
};

function Icon({ name, size = 22, color = 'currentColor', stroke = 1.7, fill = 'none', style }) {
  const d = ICON_PATHS[name] || ICON_PATHS['dot'];
  const solid = fill !== 'none';
  return (
    <svg width={size} height={size} viewBox="0 0 24 24" fill={solid ? color : 'none'}
      stroke={solid ? 'none' : color} strokeWidth={stroke} strokeLinecap="round"
      strokeLinejoin="round" style={{ display: 'block', flexShrink: 0, ...style }}>
      <path d={d} />
    </svg>
  );
}

/* ---------------- Spinner ---------------- */
function Spinner({ size = 20, color = 'var(--on-accent)', stroke = 2.2 }) {
  return (
    <svg width={size} height={size} viewBox="0 0 24 24" style={{ animation: 'hisaab-spin .8s linear infinite' }}>
      <circle cx="12" cy="12" r="9" fill="none" stroke={color} strokeOpacity="0.25" strokeWidth={stroke} />
      <path d="M12 3a9 9 0 019 9" fill="none" stroke={color} strokeWidth={stroke} strokeLinecap="round" />
    </svg>
  );
}

/* ---------------- Buttons ---------------- */
function Btn({ children, onClick, kind = 'primary', disabled, loading, size = 'lg', icon, style }) {
  const base = {
    display: 'inline-flex', alignItems: 'center', justifyContent: 'center', gap: 9,
    width: '100%', border: 'none', cursor: disabled ? 'default' : 'pointer',
    fontFamily: 'var(--sans)', fontWeight: 600, borderRadius: 'var(--r-pill)',
    height: size === 'lg' ? 56 : size === 'md' ? 46 : 38, whiteSpace: 'nowrap',
    fontSize: size === 'lg' ? 16.5 : 15, letterSpacing: '.005em', padding: '0 16px',
    transition: 'transform .12s ease, background .15s, opacity .15s', position: 'relative',
  };
  const kinds = {
    primary: { background: disabled ? 'var(--paper-2)' : 'var(--accent)', color: disabled ? 'var(--faint)' : 'var(--on-accent)' },
    ghost:   { background: 'transparent', color: 'var(--ink)', border: '1px solid var(--rule)' },
    soft:    { background: 'var(--accent-soft)', color: 'var(--accent)' },
    danger:  { background: 'var(--negative-soft)', color: 'var(--negative)' },
    plain:   { background: 'transparent', color: 'var(--muted)' },
  };
  return (
    <button className="tap" disabled={disabled || loading} onClick={onClick}
      style={{ ...base, ...kinds[kind], ...style }}>
      {loading ? <Spinner color={kind === 'primary' ? 'var(--on-accent)' : 'var(--accent)'} />
        : <>{icon && <Icon name={icon} size={19} />}{children}</>}
    </button>
  );
}

/* ---------------- Eyebrow + headline block ---------------- */
function Eyebrow({ children, color }) {
  return <div className="eyebrow" style={color ? { color } : null}>{children}</div>;
}

/* ---------------- Inputs ---------------- */
function Field({ label, value, onChange, placeholder, prefix, type = 'text', mono, autoFocus, big, onFocus, align, style }) {
  const [foc, setFoc] = useState(false);
  return (
    <label style={{ display: 'block', ...style }}>
      {label && <div className="section-label" style={{ marginBottom: 9 }}>{label}</div>}
      <div style={{
        display: 'flex', alignItems: 'center', gap: 8,
        background: 'var(--surface-2)', border: '1.5px solid ' + (foc ? 'var(--accent)' : 'var(--rule)'),
        borderRadius: 'var(--r-field)', padding: big ? '0 18px' : '0 16px', height: big ? 64 : 54,
        boxShadow: foc ? '0 0 0 4px var(--accent-ring)' : 'none', transition: 'box-shadow .15s, border-color .15s',
      }}>
        {prefix && <span className="tnum" style={{ color: 'var(--accent)', fontWeight: 600, fontSize: big ? 20 : 16 }}>{prefix}</span>}
        <input value={value} onChange={e => onChange && onChange(e.target.value)} placeholder={placeholder}
          type={type} autoFocus={autoFocus} onFocus={() => { setFoc(true); onFocus && onFocus(); }} onBlur={() => setFoc(false)}
          style={{
            flex: 1, minWidth: 0, border: 'none', outline: 'none', background: 'transparent',
            fontFamily: mono ? 'var(--mono)' : 'var(--sans)', color: 'var(--ink)',
            fontSize: big ? 22 : 16.5, fontWeight: mono ? 500 : 500,
            textAlign: align || 'left', letterSpacing: mono ? '.02em' : 'normal',
          }} />
      </div>
    </label>
  );
}

function TextArea({ label, value, onChange, placeholder, rows = 3 }) {
  const [foc, setFoc] = useState(false);
  return (
    <label style={{ display: 'block' }}>
      {label && <div className="section-label" style={{ marginBottom: 9 }}>{label}</div>}
      <textarea value={value} rows={rows} placeholder={placeholder}
        onChange={e => onChange && onChange(e.target.value)} onFocus={() => setFoc(true)} onBlur={() => setFoc(false)}
        style={{
          width: '100%', resize: 'none', borderRadius: 'var(--r-field)', padding: '14px 16px',
          background: 'var(--surface-2)', border: '1.5px solid ' + (foc ? 'var(--accent)' : 'var(--rule)'),
          boxShadow: foc ? '0 0 0 4px var(--accent-ring)' : 'none', transition: 'box-shadow .15s, border-color .15s',
          fontFamily: 'var(--sans)', fontSize: 16, color: 'var(--ink)', outline: 'none', lineHeight: 1.5,
        }} />
    </label>
  );
}

/* ---------------- Segmented control ---------------- */
function Segmented({ options, value, onChange, scroll }) {
  return (
    <div className="no-scrollbar" style={{
      display: 'flex', gap: 8, padding: 5, background: 'var(--paper-2)', borderRadius: 'var(--r-pill)',
      overflowX: scroll ? 'auto' : 'visible',
    }}>
      {options.map(o => {
        const on = (o.value ?? o) === value;
        return (
          <button key={o.value ?? o} className="tap" onClick={() => onChange(o.value ?? o)}
            style={{
              flex: scroll ? '0 0 auto' : 1, border: 'none', cursor: 'pointer',
              padding: '11px 18px', borderRadius: 'var(--r-pill)', whiteSpace: 'nowrap',
              background: on ? 'var(--accent)' : 'transparent', color: on ? 'var(--on-accent)' : 'var(--muted)',
              fontFamily: 'var(--sans)', fontWeight: 600, fontSize: 15,
              boxShadow: on ? 'var(--shadow-fab)' : 'none', transition: 'background .18s, color .18s',
            }}>{o.label ?? o}</button>
        );
      })}
    </div>
  );
}

/* ---------------- Switch / Checkbox / Radio ---------------- */
function Toggle({ on, onChange, disabled }) {
  return (
    <button className="tap" disabled={disabled} onClick={() => onChange && onChange(!on)}
      style={{
        width: 50, height: 30, borderRadius: 999, border: 'none', cursor: disabled ? 'default' : 'pointer',
        background: on ? 'var(--accent)' : 'var(--paper-2)', position: 'relative', flexShrink: 0,
        opacity: disabled ? 0.5 : 1, transition: 'background .2s',
      }}>
      <span style={{
        position: 'absolute', top: 3, left: on ? 23 : 3, width: 24, height: 24, borderRadius: '50%',
        background: on ? 'var(--on-accent)' : 'var(--surface)', boxShadow: '0 1px 3px rgba(0,0,0,.25)',
        transition: 'left .2s cubic-bezier(.3,1.3,.5,1)',
      }} />
    </button>
  );
}

function Check({ on, onChange }) {
  return (
    <button className="tap" onClick={() => onChange && onChange(!on)}
      style={{
        width: 26, height: 26, borderRadius: 8, flexShrink: 0, cursor: 'pointer',
        border: '2px solid ' + (on ? 'var(--accent)' : 'var(--rule)'),
        background: on ? 'var(--accent)' : 'transparent',
        display: 'flex', alignItems: 'center', justifyContent: 'center', transition: 'all .15s',
      }}>
      {on && <Icon name="check" size={15} color="var(--on-accent)" stroke={2.6} />}
    </button>
  );
}

function Radio({ on }) {
  return (
    <span style={{
      width: 22, height: 22, borderRadius: '50%', flexShrink: 0,
      border: '2px solid ' + (on ? 'var(--accent)' : 'var(--rule)'),
      display: 'flex', alignItems: 'center', justifyContent: 'center',
    }}>
      {on && <span style={{ width: 11, height: 11, borderRadius: '50%', background: 'var(--accent)' }} />}
    </span>
  );
}

/* ---------------- Field row (settings / pickers) ---------------- */
function Row({ label, value, onClick, danger, chevron, trailing, sub, valueColor }) {
  return (
    <button className={onClick ? 'tap' : ''} onClick={onClick} disabled={!onClick}
      style={{
        display: 'flex', alignItems: 'center', width: '100%', gap: 14, padding: '17px 0',
        background: 'transparent', border: 'none', cursor: onClick ? 'pointer' : 'default', textAlign: 'left',
      }}>
      <div style={{ flex: 1, minWidth: 0 }}>
        <div style={{ fontSize: 16.5, fontWeight: 500, color: danger ? 'var(--negative)' : 'var(--ink)' }}>{label}</div>
        {sub && <div style={{ fontSize: 13.5, color: 'var(--muted)', marginTop: 3 }}>{sub}</div>}
      </div>
      {trailing}
      {value != null && <span style={{ fontSize: 16, color: valueColor || 'var(--muted)', fontWeight: 500 }}>{value}</span>}
      {chevron && <Icon name="chevron-right" size={19} color="var(--faint)" />}
    </button>
  );
}

/* ---------------- Progress bar ---------------- */
function Bar({ pct, color, track = 'var(--paper-2)', h = 7 }) {
  return (
    <div style={{ height: h, borderRadius: 999, background: track, overflow: 'hidden' }}>
      <div style={{ height: '100%', width: Math.min(100, pct) + '%', background: color, borderRadius: 999, transition: 'width .5s cubic-bezier(.2,.8,.2,1)' }} />
    </div>
  );
}

/* ---------------- Category glyph chip ---------------- */
function Glyph({ name, color, size = 38 }) {
  return (
    <span style={{
      width: size, height: size, borderRadius: 11, flexShrink: 0,
      background: (color || 'var(--accent)') + '1f',
      display: 'flex', alignItems: 'center', justifyContent: 'center',
    }}>
      <Icon name={name} size={size * 0.5} color={color || 'var(--accent)'} stroke={1.8} />
    </span>
  );
}

/* ---------------- Bottom sheet ---------------- */
function Sheet({ open, onClose, children, title }) {
  if (!open) return null;
  return (
    <div onClick={onClose} style={{
      position: 'absolute', inset: 0, zIndex: 60, background: 'var(--scrim)',
      display: 'flex', alignItems: 'flex-end', animation: 'hisaab-fade .2s ease',
    }}>
      <div onClick={e => e.stopPropagation()} style={{
        width: '100%', background: 'var(--surface)', borderRadius: 'var(--r-sheet) var(--r-sheet) 0 0',
        padding: '14px 22px 28px', boxShadow: '0 -10px 40px -10px rgba(0,0,0,.4)',
        animation: 'hisaab-sheet-up .28s cubic-bezier(.2,.9,.2,1)', maxHeight: '88%', overflowY: 'auto',
      }} className="no-scrollbar">
        <div style={{ width: 38, height: 4, borderRadius: 99, background: 'var(--rule)', margin: '0 auto 16px' }} />
        {title && <div className="eyebrow" style={{ marginBottom: 16 }}>{title}</div>}
        {children}
      </div>
    </div>
  );
}

/* ---------------- Dialog ---------------- */
function Dialog({ open, onClose, children }) {
  if (!open) return null;
  return (
    <div onClick={onClose} style={{
      position: 'absolute', inset: 0, zIndex: 70, background: 'var(--scrim)',
      display: 'flex', alignItems: 'center', justifyContent: 'center', padding: 28,
      animation: 'hisaab-fade .2s ease',
    }}>
      <div onClick={e => e.stopPropagation()} style={{
        width: '100%', background: 'var(--surface)', borderRadius: 22, padding: 24,
        boxShadow: 'var(--shadow-pop)', animation: 'hisaab-fade-up .22s ease',
      }}>{children}</div>
    </div>
  );
}

/* ---------------- Screen scaffold + top bar ---------------- */
function TopBar({ left, title, right, onLeft, onRight, leftLabel, rightLabel, rightDanger }) {
  return (
    <div style={{ display: 'flex', alignItems: 'center', gap: 12, padding: '6px 0 18px', minHeight: 40 }}>
      <div style={{ minWidth: 64, display: 'flex', justifyContent: 'flex-start' }}>
        {(leftLabel || left) && (
          <button className="tap" onClick={onLeft} style={{ display: 'flex', alignItems: 'center', gap: 3, background: 'none', border: 'none', cursor: 'pointer', color: 'var(--muted)', fontFamily: 'var(--sans)', fontWeight: 600, fontSize: 16, padding: 0, whiteSpace: 'nowrap' }}>
            {left}{leftLabel}
          </button>
        )}
      </div>
      <div style={{ flex: 1, textAlign: 'center', fontSize: 18, fontWeight: 600, color: 'var(--ink)', fontFamily: 'var(--sans)' }}>{title}</div>
      <div style={{ minWidth: 64, display: 'flex', justifyContent: 'flex-end' }}>
        {rightLabel && (
          <button className="tap" onClick={onRight} style={{ background: 'none', border: 'none', cursor: 'pointer', color: rightDanger ? 'var(--negative)' : 'var(--accent)', fontFamily: 'var(--sans)', fontWeight: 600, fontSize: 16, padding: 0, whiteSpace: 'nowrap' }}>
            {rightLabel}
          </button>
        )}
        {right}
      </div>
    </div>
  );
}

/* fills the scrollable screen body with gutter padding */
function Screen({ children, pad = true, style, scrollRef }) {
  return (
    <div ref={scrollRef} className="no-scrollbar" style={{
      height: '100%', overflowY: 'auto', padding: pad ? '8px var(--gutter) 32px' : 0,
      animation: 'hisaab-fade .22s ease', ...style,
    }}>{children}</div>
  );
}

Object.assign(window, {
  useState, useRef, useEffect, useLayoutEffect,
  Icon, Spinner, Btn, Eyebrow, Field, TextArea, Segmented, Toggle, Check, Radio,
  Row, Bar, Glyph, Sheet, Dialog, TopBar, Screen,
});
