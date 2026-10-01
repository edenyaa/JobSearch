import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom'
import { AuthProvider, useAuth } from './auth/AuthContext'
import { RequireAuth } from './auth/RequireAuth'
import { Layout } from './components/Layout'
import { AnalysisPage } from './pages/AnalysisPage'
import { CoursesPage } from './pages/CoursesPage'
import { DashboardPage } from './pages/DashboardPage'
import { JobDetailPage } from './pages/JobDetailPage'
import { JobFormPage } from './pages/JobFormPage'
import { JobsPage } from './pages/JobsPage'
import { LandingPage } from './pages/LandingPage'
import { LoginPage } from './pages/LoginPage'
import { NotFoundPage } from './pages/NotFoundPage'
import { ProfilePage } from './pages/ProfilePage'
import { RegisterPage } from './pages/RegisterPage'
import { SkillsPage } from './pages/SkillsPage'

export const ROUTER_FUTURE = { v7_startTransition: true, v7_relativeSplatPath: true } as const

function Home() {
  const { user } = useAuth()
  return user ? <Navigate to="/dashboard" replace /> : <LandingPage />
}

export function AppRoutes() {
  return (
    <Routes>
      <Route path="/" element={<Home />} />
      <Route path="/login" element={<LoginPage />} />
      <Route path="/register" element={<RegisterPage />} />
      <Route element={<RequireAuth />}>
        <Route element={<Layout />}>
          <Route path="/dashboard" element={<DashboardPage />} />
          <Route path="/jobs" element={<JobsPage />} />
          <Route path="/jobs/new" element={<JobFormPage />} />
          <Route path="/jobs/:jobId" element={<JobDetailPage />} />
          <Route path="/jobs/:jobId/edit" element={<JobFormPage />} />
          <Route path="/jobs/:jobId/analysis" element={<AnalysisPage />} />
          <Route path="/skills" element={<SkillsPage />} />
          <Route path="/courses" element={<CoursesPage />} />
          <Route path="/profile" element={<ProfilePage />} />
        </Route>
      </Route>
      <Route path="*" element={<NotFoundPage />} />
    </Routes>
  )
}

export function App() {
  return (
    <BrowserRouter future={ROUTER_FUTURE}>
      <AuthProvider>
        <AppRoutes />
      </AuthProvider>
    </BrowserRouter>
  )
}
