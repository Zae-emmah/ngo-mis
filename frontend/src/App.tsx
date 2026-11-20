
import { useEffect, useState } from 'react';
import {
  NavLink,
  Navigate,
  Route,
  Routes,
  useLocation,
  useNavigate
} from 'react-router-dom';

import Crud, { Col } from './Crud';
import { Auth } from './Auth';
import { Dashboard, AIStudio } from './Pages';
import Settings from './Settings';
import Subscription from './Subscription';
import Landing from './Landing';
import Verify from './Verify';
import { api } from './api';

import './Theme.css';

const pct = (r: any) => {
  const v = Math.round(
    ((r.current - r.baseline) / (r.target - r.baseline)) * 100
  );

  return v + '%';
};

const pages: {
  path: string;
  label: string;
  icon: string;
  type: string;
  add: string;
  cols: Col[];
}[] = [
  {
    path: 'projects',
    label: 'Projects',
    icon: '📁',
    type: 'projects',
    add: 'New Project',
    cols: [
      { key: 'name', label: 'Project Name' },
      { key: 'status', label: 'Status' },
      { key: 'start', label: 'Start Date' },
      { key: 'end', label: 'End Date' },
      { key: 'budget', label: 'Budget' }
    ]
  },
  {
    path: 'beneficiaries',
    label: 'Beneficiaries',
    icon: '👥',
    type: 'beneficiaries',
    add: 'Add Beneficiary',
    cols: [
      { key: 'name', label: 'Name' },
      { key: 'gender', label: 'Gender' },
      { key: 'age', label: 'Age' },
      { key: 'program', label: 'Program' },
      { key: 'location', label: 'Location' }
    ]
  },
  {
    path: 'me',
    label: 'M&E',
    icon: '📈',
    type: 'indicators',
    add: 'Add Data',
    cols: [
      { key: 'name', label: 'Indicator' },
      { key: 'baseline', label: 'Baseline' },
      { key: 'target', label: 'Target' },
      { key: 'current', label: 'Current' },
      { key: 'p', label: 'Progress', render: pct }
    ]
  },
  {
    path: 'hr',
    label: 'HR',
    icon: '🧑‍💼',
    type: 'employees',
    add: 'Add Employee',
    cols: [
      { key: 'name', label: 'Name' },
      { key: 'position', label: 'Position' },
      { key: 'department', label: 'Department' },
      { key: 'status', label: 'Status' }
    ]
  },
  {
    path: 'assets',
    label: 'Assets',
    icon: '🧰',
    type: 'assets',
    add: 'Add Asset',
    cols: [
      { key: 'name', label: 'Asset Name' },
      { key: 'category', label: 'Category' },
      { key: 'serial', label: 'Serial ID' },
      { key: 'location', label: 'Location' },
      { key: 'status', label: 'Status' }
    ]
  },
  {
    path: 'donors',
    label: 'Donors',
    icon: '🤝',
    type: 'donors',
    add: 'Add Donor',
    cols: [
      { key: 'name', label: 'Donor Name' },
      { key: 'type', label: 'Type' },
      { key: 'funding', label: 'Total Funding' },
      { key: 'contact', label: 'Contact Person' }
    ]
  }
];

function Shell() {
  const nav = useNavigate();

  const [u, setU] = useState(() =>
    JSON.parse(localStorage.getItem('user') || '{}')
  );

  const admin = u.role === 'Administrator';

  useEffect(() => {
    const f = () => {
      setU(JSON.parse(localStorage.getItem('user') || '{}'));
    };

    window.addEventListener('user-updated', f);

    return () => {
      window.removeEventListener('user-updated', f);
    };
  }, []);

  const [sub, setSub] = useState<any>(null);

  useEffect(() => {
    api.subscription()
      .then(setSub)
      .catch(() => {});
  }, []);

  const left = sub
    ? Math.ceil((sub.expiresAt - Date.now()) / 864e5)
    : 99;

  return (
    <div className="app">
      <nav className="side">
        <img src="/Images/civic.jpeg" alt="CiviCore" />

        <div className="org">{u.orgName}</div>

        <NavLink to="/" end>
          🏠 Dashboard
        </NavLink>

        {pages.map(p => (
          <NavLink key={p.path} to={'/' + p.path}>
            {p.icon} {p.label}
          </NavLink>
        ))}

        <NavLink to="/ai">
          ✨ AI Studio
        </NavLink>

        {admin && (
          <NavLink to="/subscription">
            💳 Subscription
          </NavLink>
        )}

        <NavLink to="/settings">
          ⚙️ Settings
        </NavLink>

        <div className="sp" />

        <a
          onClick={() => {
            localStorage.removeItem('user');
            nav('/welcome');
          }}
        >
          🚪 Sign out
        </a>

        <div className="me">
          <div className="av">
            {u.avatar ? (
              <img src={u.avatar} alt="" />
            ) : (
              (u.name || '?')[0]
            )}
          </div>

          <div>
            <b>{u.name}</b>
            <small>{u.role}</small>
          </div>
        </div>
      </nav>

      <div className="content">
        {sub?.expired && (
          <div className="banner bad">
            <span>
              Your subscription has expired. Your data is read-only until you renew.
            </span>

            {admin && (
              <NavLink to="/subscription">
                Renew now
              </NavLink>
            )}
          </div>
        )}

        {sub && !sub.expired && left <= 7 && (
          <div className="banner warn">
            <span>
              {left} day(s) left on your {sub.plan} plan.
            </span>

            {admin && (
              <NavLink to="/subscription">
                Subscribe
              </NavLink>
            )}
          </div>
        )}

        <Routes>
          <Route path="/" element={<Dashboard />} />

          {pages.map(p => (
            <Route
              key={p.path}
              path={p.path}
              element={
                <Crud
                  title={p.label}
                  type={p.type}
                  cols={p.cols}
                  addLabel={p.add}
                />
              }
            />
          ))}

          <Route path="/ai" element={<AIStudio />} />

          <Route
            path="/subscription"
            element={<Subscription />}
          />

          <Route
            path="/settings"
            element={<Settings />}
          />
        </Routes>
      </div>
    </div>
  );
}

export default function App() {
  useLocation();

  const authed = !!JSON.parse(
    localStorage.getItem('user') || '{}'
  ).token;

  return (
    <Routes>
      <Route path="/welcome" element={<Landing />} />

      <Route path="/verify" element={<Verify />} />

      <Route path="/signin" element={<Auth mode="in" />} />

      <Route path="/signup" element={<Auth mode="up" />} />

      <Route path="/forgot" element={<Auth mode="forgot" />} />

      <Route
        path="/*"
        element={
          authed ? <Shell /> : <Navigate to="/welcome" />
        }
      />
    </Routes>
  );
}
