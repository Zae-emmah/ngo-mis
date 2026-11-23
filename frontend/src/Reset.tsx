import { useState } from 'react'; import { Link, useNavigate, useSearchParams } from 'react-router-dom'; import { api } from './api'; import './Auth.css';
export default function Reset() {
  const [sp] = useSearchParams(); const nav = useNavigate(); const email = sp.get('email') || '', token = sp.get('token') || '';
  const [pw, setPw] = useState(''); const [pw2, setPw2] = useState(''); const [msg, setMsg] = useState(''); const [ok, setOk] = useState(false); const [busy, setBusy] = useState(false);
  const submit = async () => {
    if (pw.length < 8) return setMsg('Password must be at least 8 characters');
    if (pw !== pw2) return setMsg('Passwords do not match');
    setBusy(true); setMsg('');
    try { await api.reset({ email, token, password: pw }); setOk(true); setMsg('Password changed. Taking you to sign in...'); setTimeout(() => nav('/signin'), 1500); }
    catch { setOk(false); setMsg('This reset link is invalid or has expired. Request a new one.'); }
    finally { setBusy(false); }
  };
  return (<div className="au"><aside className="au-side"><h2>Choose a new <span>password</span> for your account.</h2></aside>
    <main className="au-main"><div className="au-card">
      <h1>Set a new password</h1><p className="au-sub">{email}</p>
      <label>New password<div className="au-f"><input type="password" value={pw} onChange={e => setPw(e.target.value)} placeholder="At least 8 characters" /></div></label>
      <label>Confirm password<div className="au-f"><input type="password" value={pw2} onChange={e => setPw2(e.target.value)} placeholder="Repeat your password" /></div></label>
      <button className="au-btn" onClick={submit} disabled={busy}>{busy ? 'Please wait...' : 'Change password'}</button>
      {msg && <div className={ok ? 'au-msg good' : 'au-msg bad'}>{msg}</div>}
      <p className="au-foot"><Link to="/forgot">Request a new link</Link> · <Link to="/signin">Back to sign in</Link></p>
    </div></main></div>);
}
