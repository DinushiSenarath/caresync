import { BrowserRouter, Navigate, Route, Routes, useLocation } from 'react-router-dom'
import { AuthProvider, useAuth } from './auth'
import { homePath } from './menu'
import Layout from './components/Layout'
import Login from './pages/Login'
import Dashboard from './pages/Dashboard'
import Patients from './pages/Patients'
import Appointments from './pages/Appointments'
import Placeholder from './pages/Placeholder'
import Visit from './pages/Visit'
import Pharmacy from './pages/Pharmacy'
import Billing from './pages/Billing'
import Prices from './pages/Prices'
import Laboratory from './pages/Laboratory'

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

function AppointmentRoute() {
  const { user } = useAuth()
  return user && (user.role === 'DOCTOR' || user.role === 'NURSE') ? <Visit /> : <Appointments />
}

const PLACEHOLDERS: { path: string; title: string }[] = [
  { path: '/registration-requests', title: 'Registration requests' },
  { path: '/admin/users', title: 'Staff accounts' },
  { path: '/admin/doctors', title: 'Departments and doctors' },
  { path: '/audit', title: 'Audit log' },
  { path: '/portal', title: 'Welcome' },
  { path: '/portal/appointments', title: 'My appointments' },
  { path: '/portal/prescriptions', title: 'My prescriptions' },
  { path: '/portal/bills', title: 'My bills' },
  { path: '/portal/profile', title: 'My details' },
  { path: '/portal/lab', title: 'My lab results' },
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
            <Route path="/patients" element={<Patients />} />
            <Route path="/appointments" element={<Appointments />} />
            <Route path="/appointments/:id" element={<AppointmentRoute />} />
            <Route path="/pharmacy" element={<Pharmacy />} />
            <Route path="/pharmacy/queue" element={<Pharmacy />} />
            <Route path="/pharmacy/alerts" element={<Pharmacy />} />
            <Route path="/billing" element={<Billing />} />
            <Route path="/billing/adjustments" element={<Billing />} />
            <Route path="/billing/invoices/:id" element={<Billing />} />
            <Route path="/admin/prices" element={<Prices />} />
            <Route path="/laboratory" element={<Laboratory />} />

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