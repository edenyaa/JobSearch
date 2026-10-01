import { Link, useParams } from 'react-router-dom'
import { jobsApi } from '../api/endpoints'
import type { AnalysisCheck, AnalysisTerm, CheckResult, TermStatus } from '../api/types'
import { useUserId } from '../auth/AuthContext'
import { CATEGORY_COLORS, ChartLegend, DonutChart, StackedBar } from '../components/charts'
import type { Segment } from '../components/charts'
import { ErrorMessage, Loading } from '../components/ErrorMessage'
import { Icon } from '../components/Icon'
import { useLoad } from '../hooks/useLoad'

const STATUS_COLOR: Record<TermStatus, string> = {
  MATCHED: 'var(--good)',
  IN_PROFILE: 'var(--warn)',
  MISSING: 'var(--bad)',
}

const STATUS_TEXT: Record<TermStatus, string> = {
  MATCHED: 'In your CV',
  IN_PROFILE: 'In your profile only',
  MISSING: 'Missing',
}

const STATUS_CHIP: Record<TermStatus, string> = {
  MATCHED: 'chip-good',
  IN_PROFILE: 'chip-warn',
  MISSING: 'chip-bad',
}

const CHECK_SYMBOL: Record<CheckResult, string> = { PASS: '✓', FAIL: '✗', UNKNOWN: '?' }
const CHECK_TEXT: Record<CheckResult, string> = { PASS: 'Passed', FAIL: 'Failed', UNKNOWN: 'Unknown' }

function scoreTone(percent: number): string {
  if (percent >= 75) return 'var(--good)'
  if (percent >= 40) return 'var(--warn)'
  return 'var(--bad)'
}

function ScoreCard({ title, percent, caption }: { title: string; percent: number; caption: string }) {
  return (
    <div className="card score-card">
      <DonutChart
        size={150}
        thickness={16}
        max={100}
        segments={[{ label: title, value: percent, color: scoreTone(percent) }]}
        centerValue={`${percent}%`}
        ariaLabel={`${title}: ${percent} percent`}
      />
      <div>
        <h2>{title}</h2>
        <p className="muted">{caption}</p>
      </div>
    </div>
  )
}

function CheckList({ checks }: { checks: AnalysisCheck[] }) {
  return (
    <ul className="check-list">
      {checks.map((check) => (
        <li key={check.id} className={`check-${check.result.toLowerCase()}`}>
          <span className="check-symbol" aria-hidden="true">
            {CHECK_SYMBOL[check.result]}
          </span>
          <div>
            <strong>{check.label}</strong>
            <span className="sr-only"> ({CHECK_TEXT[check.result]})</span>
            <div className="muted">{check.detail}</div>
          </div>
        </li>
      ))}
    </ul>
  )
}

export function AnalysisPage() {
  const userId = useUserId()
  const jobId = Number(useParams().jobId)
  const job = useLoad(() => jobsApi.get(userId, jobId), [userId, jobId])
  const analysis = useLoad(() => jobsApi.analysis(userId, jobId), [userId, jobId])

  if (job.loading || analysis.loading) return <Loading label="Analyzing…" />
  if (job.error || !job.data) return <ErrorMessage message={job.error ?? 'Job not found.'} />

  const title = job.data.title ?? 'Untitled job'
  const header = (
    <div className="page-head">
      <div>
        <Link to={`/jobs/${jobId}`} className="back-link">
          ← Back to job
        </Link>
        <h1>Analysis</h1>
        <p className="muted">
          {title}
          {job.data.company ? ` · ${job.data.company}` : ''}
        </p>
      </div>
      <Link to={`/jobs/${jobId}`} className="btn btn-primary">
        <Icon name="sparkle" /> Tailor CV for this job
      </Link>
    </div>
  )

  if (analysis.error) {
    const needsCv = analysis.error.toLowerCase().includes('main cv')
    return (
      <div>
        {header}
        {needsCv ? (
          <div className="card empty-state">
            <Icon name="upload" size={28} />
            <h2>Upload your main CV first</h2>
            <p className="muted">The analysis compares your CV with this job, so it needs your CV to read.</p>
            <Link to="/profile" className="btn btn-primary">
              Upload your CV
            </Link>
          </div>
        ) : (
          <ErrorMessage message={analysis.error} />
        )}
      </div>
    )
  }

  const data = analysis.data!

  const statusSegments: Segment[] = [
    { label: STATUS_TEXT.MATCHED, value: data.matched, color: STATUS_COLOR.MATCHED },
    { label: STATUS_TEXT.IN_PROFILE, value: data.inProfile, color: STATUS_COLOR.IN_PROFILE },
    { label: STATUS_TEXT.MISSING, value: data.missing, color: STATUS_COLOR.MISSING },
  ]
  const cvSegments: Segment[] = data.cvCategories.map((category, index) => ({
    label: category.label,
    value: category.count,
    color: CATEGORY_COLORS[index % CATEGORY_COLORS.length],
  }))
  const cvTotal = cvSegments.reduce((sum, segment) => sum + segment.value, 0)
  const termsByCategory = data.categories.map((category) => ({
    category,
    terms: data.terms.filter((term: AnalysisTerm) => term.category === category.category),
  }))
  const failedChecks = [...data.requirementChecks, ...data.readinessChecks].filter((check) => check.result === 'FAIL')
  const missingKeywords = data.keywords.filter((keyword) => !keyword.inCv)

  return (
    <div>
      {header}

      <section className="card overall">
        <DonutChart
          size={190}
          thickness={20}
          max={100}
          segments={[{ label: 'ATS readiness', value: data.overallScore, color: scoreTone(data.overallScore) }]}
          centerValue={`${data.overallScore}%`}
          centerLabel="estimate"
          ariaLabel={`ATS readiness estimate: ${data.overallScore} percent`}
        />
        <div className="overall-body">
          <h2>ATS readiness estimate</h2>
          <p className="muted">
            This blends your skills, the keywords the posting repeats, the job&apos;s requirements and how readable your CV is.
            It is an estimate. No tool can predict what an ATS or a recruiter will decide, and matching every skill does not
            guarantee an interview.
          </p>
          <ul className="components">
            {data.components.map((component) => (
              <li key={component.key}>
                <div className="component-head">
                  <strong>{component.label}</strong>
                  <span className="muted">{component.weight}% of the score</span>
                  <span>{component.percent}%</span>
                </div>
                <div
                  className="coverage-track"
                  role="progressbar"
                  aria-label={`${component.label} score`}
                  aria-valuemin={0}
                  aria-valuemax={100}
                  aria-valuenow={component.percent}
                >
                  <div className="coverage-fill" style={{ width: `${component.percent}%`, background: scoreTone(component.percent) }} />
                </div>
                <div className="muted component-detail">{component.detail}</div>
              </li>
            ))}
          </ul>
        </div>
      </section>

      {data.total === 0 ? (
        <div className="card empty-state">
          <Icon name="target" size={28} />
          <h2>No known skills found in this posting</h2>
          <p className="muted">
            We looked for common skills such as languages, frameworks, tools and soft skills. Paste the full job description, or
            add your own skills and keywords so we can look for them.
          </p>
          <Link to={`/jobs/${jobId}/edit`} className="btn">
            Edit the job description
          </Link>
        </div>
      ) : (
        <>
          <div className="score-row">
            <ScoreCard
              title="Skills match"
              percent={data.coveragePercent}
              caption={`${data.matched} of the ${data.total} skills this job asks for are in your CV. Required skills count double.`}
            />
            <ScoreCard
              title="Potential skills match"
              percent={data.potentialPercent}
              caption="What you could reach by adding the skills from your profile to your CV."
            />
          </div>

          <div className="grid-2 charts-row">
            <section className="card">
              <h2>How your CV covers this job</h2>
              <div className="chart-with-legend">
                <DonutChart
                  segments={statusSegments}
                  centerValue={String(data.total)}
                  centerLabel="skills"
                  ariaLabel={`Skills asked by the job: ${data.matched} in your CV, ${data.inProfile} in your profile only, ${data.missing} missing`}
                />
                <ChartLegend segments={statusSegments} />
              </div>
            </section>

            <section className="card">
              <h2>What your CV shows</h2>
              {cvTotal === 0 ? (
                <p className="muted">We did not find known skills in your CV.</p>
              ) : (
                <div className="chart-with-legend">
                  <DonutChart
                    segments={cvSegments}
                    centerValue={String(cvTotal)}
                    centerLabel="skills"
                    ariaLabel={`Skills found in your CV by category: ${cvSegments.map((s) => `${s.label} ${s.value}`).join(', ')}`}
                  />
                  <ChartLegend segments={cvSegments} />
                </div>
              )}
            </section>
          </div>

          <section className="card">
            <h2>Match by category</h2>
            <div className="category-grid">
              {data.categories.map((category) => (
                <div key={category.category} className="category-card">
                  <DonutChart
                    size={84}
                    thickness={10}
                    max={100}
                    segments={[{ label: category.label, value: category.percent, color: scoreTone(category.percent) }]}
                    centerValue={`${category.percent}%`}
                    ariaLabel={`${category.label}: ${category.percent} percent in your CV`}
                  />
                  <div>
                    <strong>{category.label}</strong>
                    <p className="muted">
                      {category.matched} of {category.total} in your CV
                    </p>
                    <StackedBar
                      ariaLabel={`${category.label}: ${category.matched} in CV, ${category.inProfile} in profile only, ${category.missing} missing`}
                      segments={[
                        { label: 'In your CV', value: category.matched, color: STATUS_COLOR.MATCHED },
                        { label: 'In your profile only', value: category.inProfile, color: STATUS_COLOR.IN_PROFILE },
                        { label: 'Missing', value: category.missing, color: STATUS_COLOR.MISSING },
                      ]}
                    />
                  </div>
                </div>
              ))}
            </div>
          </section>

          <section className="card">
            <h2>Skills this job asks for</h2>
            <p className="muted key-row">
              <span className="chip chip-good">In your CV</span>
              <span className="chip chip-warn">In your profile only — add it to your CV</span>
              <span className="chip chip-bad">Missing</span>
              <span className="chip chip-soft">Dashed means nice to have</span>
            </p>
            {termsByCategory.map(({ category, terms }) => (
              <div key={category.category} className="term-group">
                <h3>{category.label}</h3>
                <div className="chips">
                  {terms.map((term) => (
                    <span
                      key={term.name}
                      className={`chip ${STATUS_CHIP[term.status]}${term.importance === 'PREFERRED' ? ' chip-soft' : ''}`}
                    >
                      {term.name}
                      <span className="sr-only">
                        {' '}
                        ({STATUS_TEXT[term.status]}
                        {term.importance === 'PREFERRED' ? ', nice to have' : ''})
                      </span>
                    </span>
                  ))}
                </div>
              </div>
            ))}
          </section>

          {data.extraCvSkills.length > 0 && (
            <section className="card">
              <h2>Other skills in your CV</h2>
              <p className="muted">Your CV shows these, but this job does not ask for them.</p>
              <div className="chips">
                {data.extraCvSkills.map((skill) => (
                  <span key={skill} className="chip">
                    {skill}
                  </span>
                ))}
              </div>
            </section>
          )}
        </>
      )}

      {data.keywords.length > 0 && (
        <section className="card">
          <h2>Other keywords in the posting</h2>
          <p className="muted">
            Words this posting repeats that are not in our skill list. An ATS or recruiter often searches for exactly these.
          </p>
          <div className="chips">
            {data.keywords.map((keyword) => (
              <span key={keyword.term} className={`chip ${keyword.inCv ? 'chip-good' : 'chip-bad'}`}>
                {keyword.term}
                <span className="chip-count">×{keyword.mentions}</span>
                <span className="sr-only"> ({keyword.inCv ? 'in your CV' : 'missing from your CV'})</span>
              </span>
            ))}
          </div>
        </section>
      )}

      {data.requirementChecks.length > 0 && (
        <section className="card">
          <h2>Requirements check</h2>
          <p className="muted">Years of experience are estimated from the dates on your CV.</p>
          <CheckList checks={data.requirementChecks} />
        </section>
      )}

      <section className="card">
        <h2>Is your CV easy for an ATS to read?</h2>
        <CheckList checks={data.readinessChecks} />
      </section>

      <section className="card tips">
        <h2>What to do next</h2>
        <ul>
          {data.inProfile > 0 && (
            <li>
              You already have <strong>{data.inProfile}</strong> of these skills in your profile but your CV does not mention
              them. Use <strong>Tailor CV for this job</strong> to add them where they are true.
            </li>
          )}
          {data.missing > 0 && (
            <li>
              <strong>{data.missing}</strong> skills are missing. If you have them, add them on the{' '}
              <Link to="/skills">Skills page</Link>.
            </li>
          )}
          {missingKeywords.length > 0 && (
            <li>
              Work these words into your CV where they are true: <strong>{missingKeywords.map((k) => k.term).join(', ')}</strong>.
            </li>
          )}
          {failedChecks.map((check) => (
            <li key={check.id}>{check.detail}</li>
          ))}
          {data.inProfile === 0 && data.missing === 0 && missingKeywords.length === 0 && failedChecks.length === 0 && (
            <li>Everything we can check looks good. Remember that a recruiter still reads your CV.</li>
          )}
        </ul>
      </section>
    </div>
  )
}
