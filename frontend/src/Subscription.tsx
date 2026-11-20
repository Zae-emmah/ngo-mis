import { useEffect, useState } from 'react'; import { api, Row } from './api';
type Plan = { id: string; price: number; users: number };
export default function Subscription() {
  const [plans, setPlans] = useState<Plan[]>([]); const [s, setS] = useState<Row>({}); const [pick, setPick] = useState<string | null>(null); const [m, setM] = useState('');
  const admin = JSON.parse(localStorage.getItem('user') || '{}').role === 'Administrator';
  const load = () => { api.plans().then(r => setPlans(r as unknown as Plan[])).catch(() => {}); api.subscription().then(setS).catch(() => {}); };
  useEffect(() => { load(); }, []);
  const days = s.expiresAt ? Math.max(0, Math.ceil((s.expiresAt - Date.now()) / 864e5)) : 0;
  const activate = async () => {
    try { await api.subscribe({ plan: pick }); setM('Subscription updated'); setPick(null); load(); }
    catch (e: any) { setM(e.message === '409' ? 'You have more active users than this plan allows. Deactivate some users first.' : 'Could not update the subscription'); }
  };
  return (<div><h2 className="pt">Subscription</h2><p className="ps">Manage your organization's plan.</p>
    <div className="panel" style={{ maxWidth: 560 }}>
      <h3>Current plan: {s.plan}</h3>
      <p>{s.expired ? 'Expired' : days + ' days remaining'} · ends {s.expiresAt ? new Date(s.expiresAt).toLocaleDateString() : ''}</p>
      <div className="track"><div className="fill" style={{ width: (s.userLimit ? Math.min(100, s.userCount / s.userLimit * 100) : 0) + '%' }} /></div>
      <p className="cnt">{s.userCount} of {s.userLimit} users used</p></div>
    <div className="plans">{plans.map(p => <div key={p.id} className={'panel plan' + (p.id === s.plan ? ' cur' : '')}>
      <h3>{p.id[0] + p.id.slice(1).toLowerCase()}</h3>
      <div className="price">${p.price}<small>/month</small></div>
      <ul><li>Up to {p.users} users</li><li>All modules</li><li>AI reports</li></ul>
      {p.id === 'TRIAL' ? <span className="pill">14 days free</span> : admin ? <button className="btn" onClick={() => setPick(p.id)}>{p.id === s.plan ? 'Renew' : 'Choose plan'}</button> : null}
    </div>)}</div>
    {pick && <div className="panel" style={{ marginTop: 16, maxWidth: 560 }}><h3>Confirm {pick} plan</h3>
      <p className="cnt">Demo checkout: no real payment is taken yet.</p>
      <button className="btn" onClick={activate}>Confirm and activate (30 days)</button> <button className="btn ghost" onClick={() => setPick(null)}>Cancel</button></div>}
    {m && <p className="cnt">{m}</p>}</div>);
}