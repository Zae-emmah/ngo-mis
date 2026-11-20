import { useEffect, useRef, useState } from 'react'; import { api, Row } from './api';
const getU = () => JSON.parse(localStorage.getItem('user') || '{}');
const shrink = (file: File): Promise<string> => new Promise((res, rej) => {
  const img = new Image(); const url = URL.createObjectURL(file);
  img.onload = () => { const s = 256, c = document.createElement('canvas'); c.width = c.height = s; const m = Math.min(img.width, img.height);
    c.getContext('2d')!.drawImage(img, (img.width - m) / 2, (img.height - m) / 2, m, m, 0, 0, s, s); URL.revokeObjectURL(url); res(c.toDataURL('image/jpeg', 0.85)); };
  img.onerror = () => rej(new Error('bad image')); img.src = url;
});
export default function Account() {
  const u = getU(); const admin = u.role === 'Administrator'; const fileRef = useRef<HTMLInputElement>(null);
  const [avatar, setAvatar] = useState<string>(u.avatar || ''); const [o, setO] = useState<Row>({ name: '', bio: '', website: '', address: '', country: '' });
  const [m, setM] = useState(''); const [m2, setM2] = useState('');
  useEffect(() => { api.account().then(r => { setAvatar(r.avatar || ''); setO(r.org); }).catch(() => {}); }, []);
  const store = (a: string) => { localStorage.setItem('user', JSON.stringify({ ...getU(), avatar: a })); window.dispatchEvent(new Event('user-updated')); };
  const pick = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const f = e.target.files?.[0]; e.target.value = ''; if (!f) return;
    if (!f.type.startsWith('image/')) return setM('Please choose an image file');
    try { const d = await shrink(f); await api.avatar({ avatar: d }); setAvatar(d); store(d); setM('Photo updated'); } catch { setM('Could not upload photo'); }
  };
  const remove = async () => { try { await api.avatar({ avatar: '' }); setAvatar(''); store(''); setM('Photo removed'); } catch { setM('Could not remove photo'); } };
  const save = async () => { try { await api.saveOrg(o); const x = getU(); localStorage.setItem('user', JSON.stringify({ ...x, orgName: o.name })); window.dispatchEvent(new Event('user-updated')); setM2('Organization profile saved'); } catch { setM2('Could not save'); } };
  const set = (k: string) => (e: React.ChangeEvent<HTMLInputElement | HTMLTextAreaElement>) => setO({ ...o, [k]: e.target.value });
  return (<div className="grid2s">
    <div className="panel"><h3>Profile picture</h3>
      <div className="avrow"><div className="bigav">{avatar ? <img src={avatar} alt="" /> : (u.name || '?')[0]}</div>
        <div><input ref={fileRef} type="file" accept="image/*" hidden onChange={pick} />
          <button className="btn" onClick={() => fileRef.current?.click()}>Upload photo</button>{' '}
          {avatar && <button className="btn ghost" onClick={remove}>Remove</button>}
          <p className="cnt">JPG or PNG. It is cropped to a square automatically.</p></div></div>
      <p className="cnt">{m}</p></div>
    <div className="panel"><h3>Organization profile</h3>
      {!admin && <p className="cnt">Only administrators can edit this.</p>}
      <label className="fl">Organization name<input className="in" disabled={!admin} value={o.name ?? ''} onChange={set('name')} /></label>
      <label className="fl">About your organization<textarea className="in" disabled={!admin} maxLength={1000} placeholder="Tell people about your mission, programs and the communities you serve." value={o.bio ?? ''} onChange={set('bio')} /></label>
      <p className="cnt" style={{ marginTop: -8 }}>{(o.bio || '').length}/1000</p>
      <label className="fl">Website<input className="in" disabled={!admin} placeholder="https://" value={o.website ?? ''} onChange={set('website')} /></label>
      <label className="fl">Address<input className="in" disabled={!admin} value={o.address ?? ''} onChange={set('address')} /></label>
      <label className="fl">Country<input className="in" disabled={!admin} value={o.country ?? ''} onChange={set('country')} /></label>
      {admin && <button className="btn" onClick={save}>Save organization profile</button>}<p className="cnt">{m2}</p></div></div>);
}