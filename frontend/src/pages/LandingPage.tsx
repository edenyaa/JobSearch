import { Link } from 'react-router-dom'
import { DonutChart } from '../components/charts'
import { Icon } from '../components/Icon'
import type { IconName } from '../components/Icon'

const STEPS: { icon: IconName; title: string; text: string }[] = [
  {
    icon: 'upload',
    title: 'Upload your CV',
    text: 'We read it and pick out your skills automatically. No typing them in one by one.',
  },
  {
    icon: 'jobs',
    title: 'Add a job',
    text: 'Paste the job description of a role you want. We find every skill it asks for.',
  },
  {
    icon: 'chart',
    title: 'See your match',
    text: 'Charts show what your CV covers, what it hides and what it is missing.',
  },
  {
    icon: 'sparkle',
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

export function LandingPage() {
  return (
    <div className="landing">
      <header className="landing-nav">
        <span className="brand">
          <span className="brand-mark">A</span> Auto ATS
        </span>
        <nav className="landing-links" aria-label="Account">
          <Link to="/login">Sign in</Link>
          <Link to="/register" className="btn btn-primary">
            Get started
          </Link>
        </nav>
      </header>

      <section className="hero">
        <div className="hero-text">
          <p className="eyebrow">Applicant tracking, decoded</p>
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
          <div className="card preview-card">
            <p className="muted">Example match for a Backend Engineer job</p>
            <div className="preview-body">
              <DonutChart
                size={150}
                thickness={16}
                max={100}
                segments={[{ label: 'match', value: 72, color: 'var(--good)' }]}
                centerValue="72%"
                centerLabel="CV match"
                ariaLabel="Example match 72 percent"
              />
              <ul className="preview-list">
                <li>
                  <span className="chip chip-good">Java</span>
                  <span className="chip chip-good">Docker</span>
                </li>
                <li>
                  <span className="chip chip-warn">Kubernetes</span>
                  <span className="muted"> add to CV</span>
                </li>
                <li>
                  <span className="chip chip-bad">Terraform</span>
                  <span className="muted"> missing</span>
                </li>
              </ul>
            </div>
          </div>
        </div>
      </section>

      <section className="section">
        <h2 className="section-title">How it works</h2>
        <ol className="steps">
          {STEPS.map((step, index) => (
            <li key={step.title} className="card step">
              <span className="step-number">{index + 1}</span>
              <Icon name={step.icon} size={22} />
              <h3>{step.title}</h3>
              <p className="muted">{step.text}</p>
            </li>
          ))}
        </ol>
      </section>

      <section className="section">
        <h2 className="section-title">Built to be trusted</h2>
        <div className="features">
          {FEATURES.map((feature) => (
            <div key={feature.title} className="feature">
              <Icon name="check" size={20} />
              <div>
                <h3>{feature.title}</h3>
                <p className="muted">{feature.text}</p>
              </div>
            </div>
          ))}
        </div>
      </section>

      <section className="cta card">
        <h2>Ready to see how your CV scores?</h2>
        <p className="muted">It takes a minute to upload your CV and add your first job.</p>
        <Link to="/register" className="btn btn-primary btn-large">
          Create your free account
        </Link>
      </section>

      <footer className="landing-footer muted">Auto ATS</footer>
    </div>
  )
}
