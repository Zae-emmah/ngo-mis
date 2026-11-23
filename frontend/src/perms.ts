const WRITE: Record<string, string[]> = {
  projects: ['Program Manager', 'Program Officer'],
  beneficiaries: ['Program Manager', 'Program Officer', 'M&E Officer'],
  indicators: ['Program Manager', 'M&E Officer'],
  employees: ['HR Officer'],
  assets: ['Program Manager', 'Finance Officer'],
  donors: ['Program Manager', 'Finance Officer'],
};
export const canWrite = (role: string | undefined, type: string) => role === 'Administrator' || (WRITE[type] || []).includes(role || '');
