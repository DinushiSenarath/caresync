import { BrowserRouter, Navigate, Route, Routes, useLocation } from 'react-router-dom'
import { AuthProvider, useAuth } from './auth'
import { homePath } from './menu'
import Layout from './components/Layout'
import Login from './pages/Login'
import Dashboard from './pages/Dashboard'
import Placeholder from './pages/Placeholder'

function RequireAuth() {
  const { user } = useAuth()
  const location = useLocation()
  if (!user) return <Navigate to="/login" replace state={{ from: location.pathname }} />
  return <Layout />
}

function Home() {
  const { user } = useAuth()
  return <Navigate to={user ? homePath(user.role) : '/login'} replace />
}

const PLACEHOLDERS: { path: string; title: string }[] = [
  { path: '/patients', title: 'Patients' },
  { path: '/appointments', title: 'Appointments' },
  { path: '/registration-requests', title: 'Registration requests' },
  { path: '/pharmacy', title: 'Pharmacy' },
  { path: '/billing', title: 'Billing' },
  { path: '/admin/users', title: 'Staff accounts' },
  { path: '/admin/doctors', title: 'Departments and doctors' },
  { path: '/admin/prices', title: 'Service prices' },
  { path: '/audit', title: 'Audit log' },
  { path: '/portal', title: 'Welcome' },
  { path: '/portal/appointments', title: 'My appointments' },
  { path: '/portal/prescriptions', title: 'My prescriptions' },
  { path: '/portal/bills', title: 'My bills' },
  { path: '/portal/profile', title: 'My details' },
]

export default function App() {
  return (
    <BrowserRouter>
      <AuthProvider>
        <Routes>
          <Route path="/login" element={<Login />} />
          <Route element={<RequireAuth />}>
            <Route path="/" element={<Home />} />
            <Route path="/dashboard" element={<Dashboard />} />
            {PLACEHOLDERS.map((p) => (
              <Route key={p.path} path={p.path} element={<Placeholder title={p.title} />} />
            ))}
            <Route path="*" element={<Placeholder title="Page not found" />} />
          </Route>
        </Routes>
      </AuthProvider>
    </BrowserRouter>
  )
}