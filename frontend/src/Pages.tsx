import { useEffect, useState } from 'react'; import { api, Row } from './api';

export function Dashboard() {
  const [p, setP] = useState<Row[]>([]); const [b, setB] = useState<Row[]>([]);
  useEffect(() => { api.list('projects').then(setP).catch(() => {}); api.list('beneficiaries').then(setB).catch(() => {}); }, []);
  const total = p.reduce((s, x) => s + Number(x.budget), 0); const max = Math.max(1, ...p.map(x => Number(x.budget)));
  const on = p.filter(x => x.status === 'On Track').length; const risk = p.filter(x => x.status === 'At Risk').length;
  const other = p.length - on - risk; const n = Math.max(1, p.length);
  const a = on / n * 100, c = a + risk / n * 100;
  const name = JSON.parse(localStorage.getItem('user') || '{}').name?.split(' ')[0] || '';
  return (<div>
    <h2 className="pt">Welcome back{name ? ', ' + name : ''} 👋</h2><p className="ps">Here is what is happening across your programs.</p>
    <div className="grid4">
      <div className="panel stat"><small>Active Projects</small><b>{p.length}</b></div>
      <div className="panel stat b"><small>Beneficiaries Reached</small><b>{b.length}</b></div>
      <div className="panel stat g"><small>Total Budget</small><b>${total.toLocaleString()}</b></div>
      <div className="panel stat r"><small>Projects At Risk</small><b>{risk}</b></div></div>
    <div className="grid2">
      <div className="panel"><h3>Budget by Project</h3>{p.map(x => <div key={x.id} className="brow"><span>{x.name}</span><div className="track"><div className="fill" style={{ width: Number(x.budget) / max * 100 + '%' }} /></div><b>${Number(x.budget).toLocaleString()}</b></div>)}</div>
      <div className="panel"><h3>Project Status</h3>
        <div className="donut" style={{ background: `conic-gradient(#5aad1e 0 ${a}%, #ff6b6b ${a}% ${c}%, #4da3ff ${c}% 100%)` }}><div>{p.length}</div></div>
        <div className="legend"><span><i className="dot" style={{ background: '#5aad1e' }} />On Track {on}</span><span><i className="dot" style={{ background: '#ff6b6b' }} />At Risk {risk}</span><span><i className="dot" style={{ background: '#4da3ff' }} />Other {other}</span></div></div></div>
    <div className="grid2">
      <div className="panel"><h3>Recent Projects</h3>{p.slice(-5).reverse().map(x => <div key={x.id} className="li"><span>{x.name}</span><span className={'pill ' + (x.status === 'On Track' ? 'ok' : x.status === 'At Risk' ? 'bad' : '')}>{x.status}</span></div>)}</div>
      <div className="panel ai"><h3>✨ AI Insight</h3><p>{risk ? `${risk} project(s) are at risk of delay. Open Projects to review them and plan next steps.` : 'All projects are on track. Keep it up!'}</p></div></div>
  </div>);
}

export function AIStudio() {
  const all = ['Executive Summary', 'Project Progress', 'Financial Summary', 'M&E Indicators', 'Challenges & Risks'];
  const [sel, setSel] = useState<string[]>(all); const [period, setPeriod] = useState('April 2024'); const [out, setOut] = useState('');
  const toggle = (s: string) => setSel(sel.includes(s) ? sel.filter(x => x !== s) : [...sel, s]);
  const dl = () => { const a = document.createElement('a'); a.href = URL.createObjectURL(new Blob([out])); a.download = 'report.txt'; a.click(); };
  return (<div><h2 className="pt">AI Studio</h2><p className="ps">Generate a monthly report from your live data.</p>
    <div className="panel" style={{ maxWidth: 560 }}>
      <label className="fl">Report period<input className="in" value={period} onChange={e => setPeriod(e.target.value)} /></label>
      <h3>Data to include</h3>
      {all.map(s => <label key={s} className="chk"><input type="checkbox" checked={sel.includes(s)} onChange={() => toggle(s)} />{s}</label>)}
      <button className="btn" style={{ marginTop: 10 }} onClick={async () => setOut((await api.report({ period, sections: sel })).text)}>Generate Report</button></div>
    {out && <div className="panel" style={{ marginTop: 16 }}><pre>{out}</pre><button className="btn" onClick={dl}>Download</button></div>}</div>);
}

export function Settings() {
  const u = JSON.parse(localStorage.getItem('user') || '{}');
  return (<div><h2 className="pt">Settings</h2><p className="ps">Your profile details.</p>
    <div className="panel" style={{ maxWidth: 480 }}>
      <label className="fl">Full name<input className="in" readOnly value={u.name || ''} /></label>
      <label className="fl">Email<input className="in" readOnly value={u.email || ''} /></label>
      <label className="fl">Role<input className="in" readOnly value={u.role || ''} /></label></div></div>);
}