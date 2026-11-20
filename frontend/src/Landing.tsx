import { Link } from 'react-router-dom';
import './Landing.css';

const features = [
  ['📁', 'Manage Projects', 'Plan, track and report on every program in one place.'],
  ['📈', 'Track Impact', 'Monitor indicators and beneficiaries against your targets.'],
  ['💰', 'Manage Finances', 'Keep budgets, donors and expenses under control.'],
  ['📄', 'Generate Reports', 'Produce monthly and donor reports in a few clicks.'],
  ['✨', 'AI-Powered Insights', 'Spot risks and trends before they become problems.'],
];

export default function Landing() {
  return (
    <div className="lp">
      <header className="lp-head">
        <img className="lp-logo" src="/Images/CiviCore.png" alt="CiviCore" />
        <nav>
          <a href="#features">Features</a>
          <Link to="/signin">Sign In</Link>
          <Link to="/signup" className="lp-btn">Get Started</Link>
        </nav>
      </header>

      <section className="lp-hero">
        <div>
          
          <h1>Run your NGO with <em>clarity</em> and impact.</h1>
          <p>CiviCore is a complete solution to manage programs, people, finances, operations and impact, powered by AI.</p>
          <div className="lp-cta">
            <Link to="/signup" className="lp-btn">Get Started</Link>
            <Link to="/signin" className="lp-btn ghost">Sign In</Link>
          </div>
        </div>
        <div className="lp-mock">
          <div className="lp-stats">
            <div><small>Active Projects</small><b>24</b></div>
            <div><small>Beneficiaries</small><b>5,340</b></div>
            <div><small>Budget Used</small><b>68%</b></div>
          </div>
          <div className="lp-bars">
            {[40, 65, 50, 80, 60, 90, 72].map((h, i) => <span key={i} style={{ height: h + '%' }} />)}
          </div>
          <div className="lp-insight">✨ 2 projects are at risk of delay</div>
        </div>
      </section>

      <section id="features" className="lp-feats">
        {features.map(([icon, title, text]) => (
          <div key={title} className="lp-card">
            <div className="lp-icon">{icon}</div>
            <h4>{title}</h4>
            <p>{text}</p>
          </div>
        ))}
      </section>

      <section className="lp-band">
        <h2>Ready to put purpose and performance together?</h2>
        <Link to="/signup" className="lp-btn">Create your account</Link>
      </section>

      <footer>CiviCore · Where Purpose Meets Performance · All Rights Reserved © Zaekodes-2026</footer>
    </div>
  );
}