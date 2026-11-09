import { Link } from 'react-router-dom'
import { DonutChart } from '../components/charts'
import { Icon } from '../components/Icon'

const STEPS = [
  {
    title: 'Upload your CV',
    text: 'We read it and pick out your skills automatically. No typing them in one by one.',
  },
  {
    title: 'Add a job',
    text: 'Paste the job description of a role you want. We find every skill it asks for.',
  },
  {
    title: 'See your match',
    text: 'Charts show what your CV covers, what it hides and what it is missing.',
  },
  {
    title: 'Tailor with AI',
    text: 'Get a version of your CV written for that job, using only things that are true.',
  },
]

const FEATURES = [
  { title: 'Beat the keyword filters', text: 'Most companies scan CVs for keywords before a person reads them. We show you the ones you need.' },
  { title: 'Never invent experience', text: 'The AI only uses facts from your CV, skills and courses, and flags anything that is not backed up.' },
  { title: 'Your details stay private', text: 'Your name, email, phone and links are removed before any text goes to the AI.' },
  { title: 'Track every application', text: 'Saved, applied, interview, offer. Keep notes and the exact CV you sent for each job.' },
]

const PREVIEW_JOBS = [
  { title: 'Backend Engineer', company: 'Northwind', score: 72, active: true },
  { title: 'QA Automation Engineer', company: 'Fabrikam', score: 86, active: false },
  { title: 'Junior Developer', company: 'Contoso', score: 64, active: false },
]

export function LandingPage() {
  return (
    <div className="landing">
      <header className="landing-nav">
        <div className="landing-wrap landing-nav-row">
          <span className="brand">
            <span className="brand-mark">A</span> Auto ATS
          </span>
          <nav className="landing-links" aria-label="Account">
            <Link to="/login">Sign in</Link>
            <Link to="/register" className="btn btn-primary">
              Get started
            </Link>
          </nav>
        </div>
      </header>

      <section className="hero">
        <div className="landing-wrap hero-grid">
          <div className="hero-text">
            <h1>Get your CV past the ATS</h1>
            <p className="lead">
              Upload your CV once. For every job you apply to, see exactly which skills the job wants, which ones your CV is
              missing, and get a tailored version ready to send.
            </p>
            <div className="hero-actions">
              <Link to="/register" className="btn btn-primary btn-large">
                Get started free <Icon name="arrow" />
              </Link>
              <Link to="/login" className="btn btn-large">
                Sign in
              </Link>
            </div>
          </div>

          <div className="hero-preview" aria-hidden="true">
            <div className="window">
              <div className="window-bar">
                <span />
                <span />
                <span />
              </div>
              <div className="window-body">
                <div className="window-side">
                  {PREVIEW_JOBS.map((job) => (
                    <div key={job.title} className={`window-job${job.active ? ' is-active' : ''}`}>
                      <div>
                        <strong>{job.title}</strong>
                        <small>{job.company}</small>
                      </div>
                      <span>{job.score}%</span>
                    </div>
                  ))}
                </div>
                <div className="window-main">
                  <p className="window-title">Backend Engineer</p>
                  <div className="window-score">
                    <DonutChart
                      size={132}
                      thickness={14}
                      max={100}
                      segments={[{ label: 'match', value: 72, color: 'var(--primary)' }]}
                      centerValue="72%"
                      centerLabel="CV match"
                      ariaLabel="Example match 72 percent"
                    />
                    <div className="window-terms">
                      <span className="chip chip-good">Java</span>
                      <span className="chip chip-good">Docker</span>
                      <span className="chip chip-warn">Kubernetes</span>
                      <span className="chip chip-bad">Terraform</span>
                      <p className="muted">Add Kubernetes to your CV. Terraform is missing.</p>
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>
      </section>

      <section className="section">
        <div className="landing-wrap">
          <h2 className="section-title">How it works</h2>
          <ol className="steps">
            {STEPS.map((step, index) => (
              <li key={step.title} className="step">
                <span className="step-number">{index + 1}</span>
                <h3>{step.title}</h3>
                <p>{step.text}</p>
              </li>
            ))}
          </ol>
        </div>
      </section>

      <section className="section section-tint">
        <div className="landing-wrap">
          <h2 className="section-title">Built to be trusted</h2>
          <div className="features">
            {FEATURES.map((feature) => (
              <div key={feature.title} className="feature">
                <Icon name="check" size={20} />
                <div>
                  <h3>{feature.title}</h3>
                  <p>{feature.text}</p>
                </div>
              </div>
            ))}
          </div>
        </div>
      </section>

      <section className="cta">
        <div className="landing-wrap cta-row">
          <div>
            <h2>Ready to see how your CV scores?</h2>
            <p>It takes a minute to upload your CV and add your first job.</p>
          </div>
          <Link to="/register" className="btn btn-large btn-on-dark">
            Create your free account
          </Link>
        </div>
      </section>

      <footer className="landing-footer">
        <div className="landing-wrap">Auto ATS</div>
      </footer>
    </div>
  )
}
