export type JobStatus = 'SAVED' | 'APPLIED' | 'INTERVIEW' | 'OFFER' | 'REJECTED' | 'WITHDRAWN'

export const JOB_STATUSES: JobStatus[] = ['SAVED', 'APPLIED', 'INTERVIEW', 'OFFER', 'REJECTED', 'WITHDRAWN']

export const STATUS_LABELS: Record<JobStatus, string> = {
  SAVED: 'Saved',
  APPLIED: 'Applied',
  INTERVIEW: 'Interview',
  OFFER: 'Offer',
  REJECTED: 'Rejected',
  WITHDRAWN: 'Withdrawn',
}

export interface User {
  id: number
  name: string
  email: string
  mainCvFileName: string | null
  hasMainCv: boolean
}

export interface LoginResponse {
  token: string
  user: User
}

export type SkillSource = 'MANUAL' | 'CV'

export interface Skill {
  id: number
  name: string
  explanation: string | null
  keywords: string[]
  source: SkillSource
}

export interface SkillInput {
  name: string
  explanation: string
  keywords: string[]
}

export interface Course {
  id: number
  courseNumber: string
  institution: string | null
  description: string | null
  assignments: string[]
  skillIds: number[]
}

export interface CourseInput {
  courseNumber: string
  institution: string
  description: string
  assignments: string[]
  skillIds: number[]
}

export interface Job {
  id: number
  jobNumber: string
  title: string | null
  company: string | null
  description: string
  link: string | null
  appliedDate: string
  status: JobStatus
  notes: string | null
  tailoredResumeFileName: string | null
  hasTailoredResume: boolean
}

export interface JobInput {
  jobNumber?: string
  title?: string
  company?: string
  description: string
  link?: string
  appliedDate?: string
  status?: JobStatus
  notes?: string
}

export interface MatchResult {
  coveragePercent: number
  matchedTerms: string[]
  missingTerms: string[]
}

export interface TailorResult {
  job: Job
  coverageBefore: number
  coverageAfter: number
  matchedTerms: string[]
  missingTerms: string[]
  unsupportedSkills: string[]
}

export type TermStatus = 'MATCHED' | 'IN_PROFILE' | 'MISSING'

export type Importance = 'REQUIRED' | 'PREFERRED'

export type CheckResult = 'PASS' | 'FAIL' | 'UNKNOWN'

export interface AnalysisTerm {
  name: string
  category: string
  status: TermStatus
  importance: Importance
}

export interface KeywordGap {
  term: string
  inCv: boolean
  mentions: number
}

export interface AnalysisCheck {
  id: string
  label: string
  result: CheckResult
  detail: string
}

export interface ScoreComponent {
  key: string
  label: string
  percent: number
  weight: number
  detail: string
}

export interface CategoryScore {
  category: string
  label: string
  total: number
  matched: number
  inProfile: number
  missing: number
  percent: number
}

export interface CategoryCount {
  category: string
  label: string
  count: number
}

export interface Analysis {
  overallScore: number
  coveragePercent: number
  potentialPercent: number
  total: number
  matched: number
  inProfile: number
  missing: number
  terms: AnalysisTerm[]
  categories: CategoryScore[]
  cvCategories: CategoryCount[]
  extraCvSkills: string[]
  keywords: KeywordGap[]
  requirementChecks: AnalysisCheck[]
  readinessChecks: AnalysisCheck[]
  components: ScoreComponent[]
}

export interface DetectedSkill {
  name: string
  category: string
  categoryLabel: string
  keywords: string[]
}

export type CvUpload = User & { importedSkills: number }
