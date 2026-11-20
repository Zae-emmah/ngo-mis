import { useEffect, useState } from 'react'; import { api, Row } from './api';
export type Col = { key: string; label: string; render?: (r: Row) => React.ReactNode };
const pill = (v: string) => <span className={'pill ' + (['On Track', 'Active', 'In Use'].includes(v) ? 'ok' : ['At Risk', 'Maintenance'].includes(v) ? 'bad' : '')}>{v}</span>;
export default function Crud({ title, type, cols, addLabel }: { title: string; type: string; cols: Col[]; addLabel: string }) {
  const [rows, setRows] = useState<Row[]>([]); const [q, setQ] = useState(''); const [form, setForm] = useState<Row | null>(null);
  const load = () => api.list(type).then(setRows).catch(() => setRows([]));
  useEffect(() => { load(); setForm(null); }, [type]);
  const shown = rows.filter(r => JSON.stringify(r).toLowerCase().includes(q.toLowerCase()));
  const editing = form?.id !== undefined;
  const save = async () => { editing ? await api.upd(type, form!.id!, form!) : await api.add(type, form!); setForm(null); load(); };
  return (<div>
    <div className="bar"><h2>{title}</h2><button className="btn" onClick={() => setForm({})}>+ {addLabel}</button></div>
    <input className="in search" placeholder={'🔍  Search ' + title.toLowerCase() + '...'} value={q} onChange={e => setQ(e.target.value)} />
    {form && <div className="panel formc">
      <h3 style={{ gridColumn: '1 / -1', margin: 0 }}>{editing ? 'Edit record' : addLabel}</h3>
      {cols.filter(c => !c.render).map(c => <input key={c.key} className="in" placeholder={c.label} value={form[c.key] ?? ''} onChange={e => setForm({ ...form, [c.key]: e.target.value })} />)}
      <div><button className="btn" onClick={save}>{editing ? 'Update' : 'Save'}</button> <button className="btn ghost" onClick={() => setForm(null)}>Cancel</button></div></div>}
    <div className="tw"><table><thead><tr>{cols.map(c => <th key={c.key}>{c.label}</th>)}<th /></tr></thead>
      <tbody>{shown.map(r => <tr key={r.id}>{cols.map(c => <td key={c.key}>{c.render ? c.render(r) : c.key === 'status' ? pill(r[c.key]) : r[c.key]}</td>)}
        <td style={{ whiteSpace: 'nowrap' }}>
          <button className="btn ghost edit" onClick={() => { setForm({ ...r }); window.scrollTo({ top: 0, behavior: 'smooth' }); }}>Edit</button>{' '}
          <button className="btn ghost" onClick={async () => { if (window.confirm('Delete this record?')) { await api.del(type, r.id!); load(); } }}>Delete</button></td></tr>)}</tbody></table></div>
    <p className="cnt">Showing {shown.length} of {rows.length}</p></div>);
}