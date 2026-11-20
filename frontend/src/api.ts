const B = (import.meta.env.VITE_API_URL || 'http://localhost:8080') + '/api';
const token = () => JSON.parse(localStorage.getItem('user') || '{}').token as string | undefined;
async function call<T = any>(path: string, method = 'GET', body?: unknown): Promise<T> {
  const t = token();
  const r = await fetch(B + path, { method, headers: { 'Content-Type': 'application/json', ...(t ? { Authorization: 'Bearer ' + t } : {}) }, body: body ? JSON.stringify(body) : undefined });
  if (r.status === 401 && !path.startsWith('/auth/')) { localStorage.removeItem('user'); window.location.href = '/signin'; }
  if (!r.ok) throw new Error(String(r.status));
  const txt = await r.text(); return txt ? JSON.parse(txt) : (undefined as T);
}
export type Row = { id?: number; [k: string]: any };
export const api = {
  list: (t: string) => call<Row[]>('/' + t), add: (t: string, b: Row) => call<Row>('/' + t, 'POST', b),
  upd: (t: string, id: number, b: Row) => call<Row>('/' + t + '/' + id, 'PUT', b),
  del: (t: string, id: number) => call('/' + t + '/' + id, 'DELETE'),
  signin: (b: any) => call('/auth/signin', 'POST', b), register: (b: any) => call('/auth/register', 'POST', b),
  verify: (b: any) => call('/auth/verify', 'POST', b), resend: (b: any) => call('/auth/resend', 'POST', b),
  forgot: (email: string) => call('/auth/forgot', 'POST', { email }),
  report: (b: any) => call<{ text: string }>('/ai/report', 'POST', b),
  profile: (b: any) => call('/profile', 'PUT', b), password: (b: any) => call('/auth/password', 'POST', b),
  account: () => call<Row>('/account'), avatar: (b: any) => call('/account/avatar', 'PUT', b), saveOrg: (b: any) => call('/account/org', 'PUT', b),
  users: () => call<Row[]>('/users'), invite: (b: any) => call<{ tempPassword: string }>('/users/invite', 'POST', b),
  updUser: (id: number, b: any) => call('/users/' + id, 'PUT', b),
  plans: () => call<Row[]>('/plans'), subscription: () => call<Row>('/subscription'),
  subscribe: (b: any) => call<Row>('/subscription/subscribe', 'POST', b),
};