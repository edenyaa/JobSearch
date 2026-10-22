import { render } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { vi } from 'vitest'
import type { Analysis, Company, Course, Job, Skill, User } from '../api/types'
import { AppRoutes, ROUTER_FUTURE } from '../App'
import { AuthProvider } from '../auth/AuthContext'

export interface Call {
  method: string
  path: string
  body: unknown
  headers: Headers
}

export interface Reply {
  status?: number
  body?: unknown
  text?: string
  headers?: Record<string, string>
  delay?: number
}

export type Route = Reply | ((call: Call) => Reply)

export function mockApi(routes: Record<string, Route>) {
  const calls: Call[] = []
  const fetchMock = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
    const path = String(input)
    const method = init?.method ?? 'GET'
    let body: unknown = undefined
    if (init?.body instanceof FormData) {
      body = init.body
    } else if (typeof init?.body === 'string') {
      body = JSON.parse(init.body)
    }
    const call: Call = { method, path, body, headers: new Headers(init?.headers) }
    calls.push(call)

    const key = `${method} ${path}`
    const route = routes[key]
    if (!route) {
      return new Response(JSON.stringify({ message: `No mock for ${key}` }), {
        status: 404,
        headers: { 'Content-Type': 'application/json' },
      })
    }
    const reply = typeof route === 'function' ? route(call) : route
    if (reply.delay) {
      await new Promise((resolve) => setTimeout(resolve, reply.delay))
    }
    const status = reply.status ?? 200
    if (status === 204) {
      return new Response(null, { status })
    }
    if (reply.text !== undefined) {
      return new Response(reply.text, { status, headers: reply.headers })
    }
    return new Response(JSON.stringify(reply.body ?? {}), {
      status,
      headers: { 'Content-Type': 'application/json', ...reply.headers },
    })
  })
  vi.stubGlobal('fetch', fetchMock)
  return {
    calls,
    fetchMock,
    to: (key: string) => calls.filter((call) => `${call.method} ${call.path}` === key),
  }
}

export const USER: User = {
  id: 1,
  name: 'Dana Levi',
  email: 'dana@example.com',
  mainCvFileName: 'cv.pdf',
  hasMainCv: true,
}

export function makeJob(overrides: Partial<Job> = {}): Job {
  return {
    id: 5,
    jobNumber: 'REQ-5',
    title: 'Backend Engineer',
    company: 'Acme',
    description: 'Build APIs with Java',
    link: null,
    appliedDate: '2026-03-01',
    status: 'APPLIED',
    notes: null,
    tailoredResumeFileName: null,
    hasTailoredResume: false,
    ...overrides,
  }
}

export function makeSkill(overrides: Partial<Skill> = {}): Skill {
  return { id: 1, name: 'Java', explanation: 'Backend language', keywords: ['Spring'], source: 'MANUAL', ...overrides }
}

export function makeCourse(overrides: Partial<Course> = {}): Course {
  return {
    id: 1,
    courseNumber: 'CS101',
    institution: 'TAU',
    description: 'Intro course',
    assignments: ['Built a compiler'],
    skillIds: [],
    ...overrides,
  }
}

export function makeCompany(overrides: Partial<Company> = {}): Company {
  return {
    id: 1,
    name: 'Acme',
    contacts: [],
    jobs: [],
    ...overrides,
  }
}

export function makeAnalysis(overrides: Partial<Analysis> = {}): Analysis {
  return {
    overallScore: 72,
    coveragePercent: 67,
    potentialPercent: 100,
    total: 3,
    matched: 2,
    inProfile: 1,
    missing: 0,
    terms: [
      { name: 'Java', category: 'LANGUAGES', status: 'MATCHED', importance: 'REQUIRED' },
      { name: 'Docker', category: 'CLOUD_DEVOPS', status: 'MATCHED', importance: 'REQUIRED' },
      { name: 'Kubernetes', category: 'CLOUD_DEVOPS', status: 'IN_PROFILE', importance: 'PREFERRED' },
    ],
    categories: [
      { category: 'LANGUAGES', label: 'Languages', total: 1, matched: 1, inProfile: 0, missing: 0, percent: 100 },
      { category: 'CLOUD_DEVOPS', label: 'Cloud & DevOps', total: 2, matched: 1, inProfile: 1, missing: 0, percent: 50 },
    ],
    cvCategories: [
      { category: 'LANGUAGES', label: 'Languages', count: 2 },
      { category: 'CLOUD_DEVOPS', label: 'Cloud & DevOps', count: 1 },
    ],
    extraCvSkills: ['Git'],
    keywords: [
      { term: 'payment systems', inCv: false, mentions: 2 },
      { term: 'ledger', inCv: true, mentions: 3 },
    ],
    requirementChecks: [
      {
        id: 'years',
        label: 'Years of experience',
        result: 'FAIL',
        detail: 'The job asks for 8+ years; your CV shows about 6 years of dated experience.',
      },
      { id: 'title', label: 'Job title words appear in your CV', result: 'PASS', detail: 'Your CV uses the words in the job title.' },
    ],
    readinessChecks: [
      { id: 'email', label: 'Email address is present', result: 'PASS', detail: 'An ATS needs a contact email.' },
      {
        id: 'skills',
        label: 'Has a Skills section',
        result: 'FAIL',
        detail: 'Add a "Skills" section. It is the first place an ATS looks for keywords.',
      },
    ],
    components: [
      { key: 'skills', label: 'Skills', percent: 67, weight: 50, detail: '2 required and 1 nice-to-have skills found in the posting.' },
      { key: 'keywords', label: 'Other keywords', percent: 50, weight: 20, detail: '1 of the 2 words this posting repeats appear in your CV.' },
      { key: 'requirements', label: 'Requirements', percent: 50, weight: 20, detail: '1 of 2 checks passed (job title, years of experience, education).' },
      { key: 'readiness', label: 'CV readiness', percent: 50, weight: 10, detail: '1 of 2 checks that help an ATS read your CV.' },
    ],
    ...overrides,
  }
}

interface RenderOptions {
  signedIn?: boolean
  user?: User
}

type Entry = string | { pathname: string; state?: unknown }

export function renderApp(entry: Entry | Entry[] = '/', options: RenderOptions = {}) {
  if (options.signedIn !== false) {
    localStorage.setItem('ats.session', JSON.stringify({ token: 't0k', user: options.user ?? USER }))
  }
  const entries = Array.isArray(entry) ? entry : [entry]
  return render(
    <MemoryRouter initialEntries={entries} initialIndex={entries.length - 1} future={ROUTER_FUTURE}>
      <AuthProvider>
        <AppRoutes />
      </AuthProvider>
    </MemoryRouter>,
  )
}

export function stubDownloads() {
  const createObjectURL = vi.fn(() => 'blob:test')
  const revokeObjectURL = vi.fn()
  vi.stubGlobal('URL', Object.assign(URL, { createObjectURL, revokeObjectURL }))
  const click = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => undefined)
  return { createObjectURL, revokeObjectURL, click }
}
