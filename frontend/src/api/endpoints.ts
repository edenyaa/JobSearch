import { download, request, upload } from './client'
import type {
  Analysis,
  Company,
  CompanyInput,
  Course,
  CourseInput,
  CvUpload,
  DetectedSkill,
  Job,
  JobInput,
  JobStatus,
  LoginResponse,
  MatchResult,
  Skill,
  SkillInput,
  TailorResult,
  User,
} from './types'

export const usersApi = {
  register: (name: string, email: string, password: string) =>
    request<User>('POST', '/users', { name, email, password }),
  login: (email: string, password: string) => request<LoginResponse>('POST', '/users/login', { email, password }),
  get: (id: number) => request<User>('GET', `/users/${id}`),
  logout: (id: number) => request<void>('POST', `/users/${id}/logout`),
  changeName: (id: number, oldValue: string, newValue: string) =>
    request<User>('PUT', `/users/${id}/name`, { oldValue, newValue }),
  changeEmail: (id: number, oldValue: string, newValue: string) =>
    request<User>('PUT', `/users/${id}/email`, { oldValue, newValue }),
  changePassword: (id: number, oldValue: string, newValue: string) =>
    request<User>('PUT', `/users/${id}/password`, { oldValue, newValue }),
  remove: (id: number) => request<void>('DELETE', `/users/${id}`),
  uploadCv: (id: number, file: File) => upload<CvUpload>(`/users/${id}/cv`, file),
  cvSkills: (id: number) => request<DetectedSkill[]>('GET', `/users/${id}/cv/skills`),
  downloadCv: (id: number) => download(`/users/${id}/cv`, 'main-cv'),
  cvText: (id: number) => request<{ text: string }>('GET', `/users/${id}/cv/text`),
}

export const skillsApi = {
  list: (userId: number) => request<Skill[]>('GET', `/users/${userId}/skills`),
  create: (userId: number, input: SkillInput) => request<Skill>('POST', `/users/${userId}/skills`, input),
  update: (userId: number, id: number, input: SkillInput) =>
    request<Skill>('PUT', `/users/${userId}/skills/${id}`, input),
  remove: (userId: number, id: number) => request<void>('DELETE', `/users/${userId}/skills/${id}`),
}

export const coursesApi = {
  list: (userId: number) => request<Course[]>('GET', `/users/${userId}/courses`),
  create: (userId: number, input: CourseInput) => request<Course>('POST', `/users/${userId}/courses`, input),
  update: (userId: number, id: number, input: CourseInput) =>
    request<Course>('PUT', `/users/${userId}/courses/${id}`, input),
  remove: (userId: number, id: number) => request<void>('DELETE', `/users/${userId}/courses/${id}`),
}

export const companiesApi = {
  list: (userId: number) => request<Company[]>('GET', `/users/${userId}/companies`),
  create: (userId: number, input: CompanyInput) => request<Company>('POST', `/users/${userId}/companies`, input),
  update: (userId: number, id: number, input: CompanyInput) =>
    request<Company>('PUT', `/users/${userId}/companies/${id}`, input),
  remove: (userId: number, id: number) => request<void>('DELETE', `/users/${userId}/companies/${id}`),
}

export const jobsApi = {
  list: (userId: number, status?: JobStatus) =>
    request<Job[]>('GET', `/users/${userId}/jobs${status ? `?status=${status}` : ''}`),
  get: (userId: number, id: number) => request<Job>('GET', `/users/${userId}/jobs/${id}`),
  create: (userId: number, input: JobInput) => request<Job>('POST', `/users/${userId}/jobs`, input),
  update: (userId: number, id: number, input: JobInput) => request<Job>('PUT', `/users/${userId}/jobs/${id}`, input),
  changeStatus: (userId: number, id: number, status: JobStatus) =>
    request<Job>('PUT', `/users/${userId}/jobs/${id}/status`, { status }),
  remove: (userId: number, id: number) => request<void>('DELETE', `/users/${userId}/jobs/${id}`),
  match: (userId: number, id: number) => request<MatchResult>('GET', `/users/${userId}/jobs/${id}/match`),
  analysis: (userId: number, id: number) => request<Analysis>('GET', `/users/${userId}/jobs/${id}/analysis`),
  tailor: (userId: number, id: number) => request<TailorResult>('POST', `/users/${userId}/jobs/${id}/tailor`),
  uploadResume: (userId: number, id: number, file: File) => upload<Job>(`/users/${userId}/jobs/${id}/resume`, file),
  downloadResume: (userId: number, id: number) => download(`/users/${userId}/jobs/${id}/resume`, 'tailored-cv'),
}
