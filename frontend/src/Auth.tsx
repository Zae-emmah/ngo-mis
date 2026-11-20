import { useState } from 'react'; import { useNavigate, Link } from 'react-router-dom'; import { api } from './api';
import './Auth.css';

const ic = { width: 18, height: 18, viewBox: '0 0 24 24', fill: 'none', stroke: 'currentColor', strokeWidth: 1.8, strokeLinecap: 'round', strokeLinejoin: 'round' } as const;
const Mail = () => <svg {...ic}><rect x="3" y="5" width="18" height="14" rx="2" /><path d="m3 7 9 6 9-6" /></svg>;
const Lock = () => <svg {...ic}><rect x="5" y="11" width="14" height="9" rx="2" /><path d="M8 11V8a4 4 0 0 1 8 0v3" /></svg>;
const User = () => <svg {...ic}><circle cx="12" cy="8" r="4" /><path d="M4 21c0-4 4-6 8-6s8 2 8 6" /></svg>;
const Building = () => <svg {...ic}><rect x="4" y="3" width="16" height="18" rx="2" /><path d="M9 8h2M13 8h2M9 12h2M13 12h2M10 21v-4h4v4" /></svg>;
const Check = () => <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="#7fd23a" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round"><circle cx="12" cy="12" r="10" /><path d="m8 12 3 3 5-6" /></svg>;

export function Auth({ mode }: { mode: 'in' | 'up' | 'forgot' }) {
  const [f, setF] = useState<any>({}); const [msg, setMsg] = useState(''); const [ok, setOk] = useState(false);
  const [show, setShow] = useState(false); const [busy, setBusy] = useState(false); const nav = useNavigate();
  const set = (k: string) => (e: any) => setF({ ...f, [k]: e.target.value });
  const done = (m: string, good = false) => { setMsg(m); setOk(good); };
  const submit = async () => {
    setBusy(true); setMsg('');
    try {
      if (mode === 'in') { localStorage.setItem('user', JSON.stringify(await api.signin(f))); nav('/'); }
      else if (mode === 'up') {
        if (!f.orgName || !f.name || !f.email || !f.password) return done('Please fill in all fields');
        if (f.password.length < 8) return done('Password must be at least 8 characters');
        if (f.password !== f.confirm) return done('Passwords do not match');
        const r: any = await api.register(f);
        if (r.status === 'ok') nav('/verify?email=' + encodeURIComponent(f.email));
        else done(r.status === 'exists' ? 'That email is already registered' : 'Please check your details');
      } else { await api.forgot(f.email); done('If that email exists, a reset link has been sent.', true); }
    } catch (e: any) {
      if (mode === 'in' && e.message === '403') { api.resend({ email: f.email }).catch(() => {}); nav('/verify?email=' + encodeURIComponent(f.email)); return; }
      done(mode === 'in' ? 'Invalid email or password, or server unreachable' : 'Something went wrong. Is the server running?');
    }
    finally { setBusy(false); }
  };
  const title = mode === 'in' ? 'Welcome back!' : mode === 'up' ? 'Register your organization' : 'Reset your password';
  const sub = mode === 'in' ? 'Sign in to continue' : mode === 'up' ? 'Create your NGO workspace. You will be its administrator.' : "Enter your email and we'll send you a link to reset your password.";
  const onKey = (e: React.KeyboardEvent) => { if (e.key === 'Enter') submit(); };

  return (
    <div className="au">
      <aside className="au-side">
        <h2>Manage programs, people, finances and <span>impact</span> in one place.</h2>
        <ul>
          <li><Check />Track projects and indicators</li>
          <li><Check />Keep budgets and donors organised</li>
          <li><Check />Generate reports with AI</li>
        </ul>
      </aside>

      <main className="au-main">
        <div className="au-card" onKeyDown={onKey}>
          <h1>{title}</h1>
          <p className="au-sub">{sub}</p>

          {mode === 'up' && <label>Organization name<div className="au-f"><Building /><input placeholder="Hope Foundation" onChange={set('orgName')} /></div></label>}
          {mode === 'up' && <label>Your full name<div className="au-f"><User /><input placeholder="Jane Doe" onChange={set('name')} /></div></label>}
          <label>Email address<div className="au-f"><Mail /><input type="email" placeholder="you@organisation.org" onChange={set('email')} /></div></label>
          {mode !== 'forgot' && (
            <label>Password
              <div className="au-f"><Lock />
                <input type={show ? 'text' : 'password'} placeholder="Enter your password" onChange={set('password')} />
                <button type="button" onClick={() => setShow(!show)}>{show ? 'Hide' : 'Show'}</button>
              </div>
            </label>
          )}
          {mode === 'up' && <label>Confirm password<div className="au-f"><Lock /><input type={show ? 'text' : 'password'} placeholder="Repeat your password" onChange={set('confirm')} /></div></label>}

          {mode === 'in' && (
            <div className="au-row">
              <label className="au-check"><input type="checkbox" /> Remember me</label>
              <Link to="/forgot">Forgot password?</Link>
            </div>
          )}

          <button className="au-btn" onClick={submit} disabled={busy}>
            {busy ? 'Please wait...' : mode === 'in' ? 'Sign In' : mode === 'up' ? 'Register Organization' : 'Send Reset Link'}
          </button>

          {msg && <div className={ok ? 'au-msg good' : 'au-msg bad'}>{msg}</div>}

          <p className="au-foot">
            {mode === 'in' && <>New organization? <Link to="/signup">Register your NGO</Link><br />Joining an existing organization? Ask your administrator to add you.</>}
            {mode === 'up' && <>Already registered? <Link to="/signin">Sign in</Link></>}
            {mode === 'forgot' && <Link to="/signin">Back to sign in</Link>}
          </p>
        </div>
      </main>
    </div>
  );
}