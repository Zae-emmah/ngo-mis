import { useEffect, useRef, useState } from 'react'; import { Link, useNavigate, useSearchParams } from 'react-router-dom'; import { api } from './api';
import './Auth.css';
const EMPTY = ['', '', '', '', '', ''];
export default function Verify() {
  const [sp] = useSearchParams(); const email = sp.get('email') || ''; const nav = useNavigate();
  const [d, setD] = useState<string[]>(EMPTY); const [msg, setMsg] = useState(''); const [ok, setOk] = useState(false);
  const [wait, setWait] = useState(60); const [busy, setBusy] = useState(false); const refs = useRef<(HTMLInputElement | null)[]>([]);
  useEffect(() => { if (wait <= 0) return; const t = setTimeout(() => setWait(wait - 1), 1000); return () => clearTimeout(t); }, [wait]);
  const submit = async (digits: string[]) => {
    const code = digits.join(''); if (code.length < 6 || busy) return; setBusy(true); setMsg('');
    try { await api.verify({ email, code }); setOk(true); setMsg('Email verified! Taking you to sign in...'); setTimeout(() => nav('/signin'), 1500); }
    catch (e: any) { setOk(false); setMsg(e.message === '429' ? 'Too many wrong attempts. Request a new code.' : 'Invalid or expired code'); setD(EMPTY); refs.current[0]?.focus(); }
    finally { setBusy(false); }
  };
  const change = (i: number, v: string) => {
    const c = v.replace(/\D/g, '').slice(-1); const n = [...d]; n[i] = c; setD(n);
    if (c && i < 5) refs.current[i + 1]?.focus(); if (n.every(x => x)) submit(n);
  };
  const key = (i: number, e: React.KeyboardEvent) => { if (e.key === 'Backspace' && !d[i] && i > 0) refs.current[i - 1]?.focus(); };
  const paste = (e: React.ClipboardEvent) => { const t = e.clipboardData.getData('text').replace(/\D/g, '').slice(0, 6); if (t.length === 6) { e.preventDefault(); const n = t.split(''); setD(n); submit(n); } };
  const resend = async () => {
    try { await api.resend({ email }); setWait(60); setOk(true); setMsg('A new code has been sent'); }
    catch { setOk(false); setMsg('Please wait a moment before requesting another code'); }
  };
  return (<div className="au">
    <aside className="au-side"><h2>Almost there. One quick step to <span>secure</span> your account.</h2></aside>
    <main className="au-main"><div className="au-card">
      <h1>Verify your email</h1>
      <p className="au-sub">We sent a 6-digit code to <b>{email || 'your email'}</b>. It expires in 10 minutes.</p>
      <div className="au-otp" onPaste={paste}>{d.map((v, i) => <input key={i} ref={el => { refs.current[i] = el; }} inputMode="numeric" maxLength={1} value={v} autoFocus={i === 0}
        onChange={e => change(i, e.target.value)} onKeyDown={e => key(i, e)} />)}</div>
      <button className="au-btn" onClick={() => submit(d)} disabled={busy || d.some(x => !x)}>{busy ? 'Verifying...' : 'Verify'}</button>
      {msg && <div className={ok ? 'au-msg good' : 'au-msg bad'}>{msg}</div>}
      <p className="au-foot">Didn't get it? {wait > 0 ? <span>Resend in {wait}s</span> : <button className="au-link" onClick={resend}>Resend code</button>}<br /><Link to="/signin">Back to sign in</Link></p>
    </div></main></div>);
}