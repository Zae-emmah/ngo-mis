import Account from './Account';
import { useEffect, useState } from 'react'; import { api, Row } from './api';
const ROLES = ['Administrator', 'Program Manager', 'Program Officer', 'M&E Officer', 'Finance Officer', 'HR Officer', 'Auditor'];
const getU = () => JSON.parse(localStorage.getItem('user') || '{}');

function Profile() {
  const u = getU(); const [f, setF] = useState({ name: u.name || '', phone: u.phone || '' });
  const [p, setP] = useState({ current: '', next: '', again: '' }); const [m, setM] = useState(''); const [m2, setM2] = useState('');
  const score = [p.next.length >= 8, /[A-Z]/.test(p.next), /[0-9]/.test(p.next), /[^A-Za-z0-9]/.test(p.next)].filter(Boolean).length;
  const save = async () => { try { await api.profile({ email: u.email, ...f }); localStorage.setItem('user', JSON.stringify({ ...u, ...f })); window.dispatchEvent(new Event('user-updated')); setM('Profile updated'); } catch { setM('Could not save'); } };
  const change = async () => {
    if (p.next !== p.again) return setM2('New passwords do not match'); if (score < 3) return setM2('Password is too weak');
    try { await api.password({ email: u.email, current: p.current, next: p.next }); setM2('Password changed'); setP({ current: '', next: '', again: '' }); } catch { setM2('Current password is wrong'); } };
  return (<div className="grid2s">
    <div className="panel"><h3>Profile</h3>
      <label className="fl">Full name<input className="in" value={f.name} onChange={e => setF({ ...f, name: e.target.value })} /></label>
      <label className="fl">Email<input className="in" readOnly value={u.email || ''} /></label>
      <label className="fl">Phone<input className="in" value={f.phone} onChange={e => setF({ ...f, phone: e.target.value })} /></label>
      <label className="fl">Role<input className="in" readOnly value={u.role || ''} /></label>
      <button className="btn" onClick={save}>Update Profile</button><p className="cnt">{m}</p></div>
    <div className="panel"><h3>Change password</h3>
      <label className="fl">Current password<input className="in" type="password" value={p.current} onChange={e => setP({ ...p, current: e.target.value })} /></label>
      <label className="fl">New password<input className="in" type="password" value={p.next} onChange={e => setP({ ...p, next: e.target.value })} /></label>
      <div className="track" style={{ marginBottom: 14 }}><div className="fill" style={{ width: score * 25 + '%', background: score < 3 ? '#ff6b6b' : '#5aad1e' }} /></div>
      <label className="fl">Confirm new password<input className="in" type="password" value={p.again} onChange={e => setP({ ...p, again: e.target.value })} /></label>
      <button className="btn" onClick={change}>Change Password</button><p className="cnt">{m2}</p></div></div>);
}

function Org() {
  const [o, setO] = useState<Row>({}); const [m, setM] = useState('');
  useEffect(() => { api.list('org').then(r => r[0] && setO(r[0])).catch(() => {}); }, []);
  const save = async () => { try { const r = o.id ? await api.upd('org', o.id, o) : await api.add('org', o); setO(r); setM('Saved'); } catch { setM('Could not save'); } };
  const fields: [string, string][] = [['name', 'Organization name'], ['address', 'Address'], ['country', 'Country'], ['currency', 'Currency (e.g. USD)'], ['fyStart', 'Financial year starts (month)']];
  return (<div className="panel" style={{ maxWidth: 520 }}><h3>Organization</h3>
    {fields.map(([k, l]) => <label key={k} className="fl">{l}<input className="in" value={o[k] ?? ''} onChange={e => setO({ ...o, [k]: e.target.value })} /></label>)}
    <button className="btn" onClick={save}>Save</button><p className="cnt">{m}</p></div>);
}

function Users() {
  const [us, setUs] = useState<Row[]>([]); const [n, setN] = useState({ name: '', email: '', role: 'Program Officer' }); const [tmp, setTmp] = useState('');
  const load = () => api.users().then(setUs).catch(() => {}); useEffect(() => { load(); }, []);
  const invite = async () => { try { setTmp((await api.invite(n)).tempPassword); setN({ name: '', email: '', role: 'Program Officer' }); load(); } catch (e: any) { setTmp(e.message === '402' ? 'User limit reached or subscription expired. Upgrade under Subscription.' : 'Could not invite (email may already exist)'); } };
  return (<div>
    <div className="panel formc"><h3 style={{ gridColumn: '1 / -1', margin: 0 }}>Invite a user</h3>
      <input className="in" placeholder="Full name" value={n.name} onChange={e => setN({ ...n, name: e.target.value })} />
      <input className="in" placeholder="Email" value={n.email} onChange={e => setN({ ...n, email: e.target.value })} />
      <select className="in" value={n.role} onChange={e => setN({ ...n, role: e.target.value })}>{ROLES.map(r => <option key={r}>{r}</option>)}</select>
      <div><button className="btn" onClick={invite}>Invite</button></div>
      {tmp && <p className="cnt" style={{ gridColumn: '1 / -1' }}>Temporary password (share it with the user): <b>{tmp}</b></p>}</div>
    <div className="tw"><table><thead><tr><th>Name</th><th>Email</th><th>Role</th><th>Status</th><th /></tr></thead><tbody>
      {us.map(x => <tr key={x.id}><td>{x.name}</td><td>{x.email}</td>
        <td><select className="in" value={x.role} onChange={async e => { await api.updUser(x.id!, { role: e.target.value }); load(); }}>{ROLES.map(r => <option key={r}>{r}</option>)}</select></td>
        <td><span className={'pill ' + (x.active ? 'ok' : 'bad')}>{x.active ? 'Active' : 'Inactive'}</span></td>
        <td><button className="btn ghost" onClick={async () => { await api.updUser(x.id!, { active: !x.active }); load(); }}>{x.active ? 'Deactivate' : 'Activate'}</button></td></tr>)}</tbody></table></div></div>);
}

function System() {
  const csv = async (t: string) => {
    const r = await api.list(t); if (!r.length) return;
    const k = Object.keys(r[0]).filter(x => x !== 'id'); const txt = [k.join(','), ...r.map(x => k.map(c => JSON.stringify(x[c] ?? '')).join(','))].join('\n');
    const a = document.createElement('a'); a.href = URL.createObjectURL(new Blob([txt], { type: 'text/csv' })); a.download = t + '.csv'; a.click();
  };
  return (<div className="panel" style={{ maxWidth: 520 }}><h3>Export data (CSV)</h3>
    <div style={{ display: 'flex', gap: 10, flexWrap: 'wrap' }}>
      {['projects', 'beneficiaries', 'donors', 'assets', 'employees'].map(t => <button key={t} className="btn ghost edit" onClick={() => csv(t)}>{t}</button>)}</div></div>);
}

export default function Settings() {
  const admin = getU().role === 'Administrator';
  const tabs = ['Account', 'Profile', ...(admin ? ['Organization', 'Users & Roles'] : []), 'System']; const [tab, setTab] = useState('Account');
  return (<div><h2 className="pt">Settings</h2><p className="ps">Manage your account and organization.</p>
    <div className="tabs">{tabs.map(t => <button key={t} className={t === tab ? 'on' : ''} onClick={() => setTab(t)}>{t}</button>)}</div>
    {tab === 'Account' && <Account />}{tab === 'Profile' && <Profile />}{tab === 'Organization' && <Org />}{tab === 'Users & Roles' && <Users />}{tab === 'System' && <System />}</div>);
}