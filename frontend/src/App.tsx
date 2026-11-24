import { BrowserRouter, Navigate, Route, Routes, useLocation } from 'react-router-dom'
import { AuthProvider, useAuth } from './auth'
import { homePath } from './menu'
import Layout from './components/Layout'
import Login from './pages/Login'
import Dashboard from './pages/Dashboard'
import Patients from './pages/Patients'
import Appointments from './pages/Appointments'
import Visit from './pages/Visit'
import Pharmacy from './pages/Pharmacy'
import Billing from './pages/Billing'
import Prices from './pages/Prices'
import Laboratory from './pages/Laboratory'
import Wards from './pages/Wards'
import Hr from './pages/Hr'
import MyWork from './pages/MyWork'
import Users from './pages/Users'
import DoctorsAdmin from './pages/DoctorsAdmin'
import Registrations from './pages/Registrations'
import AuditLog from './pages/AuditLog'
import ChangePassword from './pages/ChangePassword'
import { Activate, Register, ResetPassword } from './pages/Public'
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

function AppointmentRoute() {
  const { user } = useAuth()
  return user && (user.role === 'DOCTOR' || user.role === 'NURSE') ? <Visit /> : <Appointments />
}

const PLACEHOLDERS: { path: string; title: string }[] = [
  { path: '/portal', title: 'Welcome' },
  { path: '/portal/appointments', title: 'My appointments' },
  { path: '/portal/prescriptions', title: 'My prescriptions' },
  { path: '/portal/lab', title: 'My lab results' },
  { path: '/portal/bills', title: 'My bills' },
  { path: '/portal/profile', title: 'My details' },
]

export default function App() {
  return (
    <BrowserRouter>
      <AuthProvider>
        <Routes>
          <Route path="/login" element={<Login />} />
          <Route path="/register" element={<Register />} />
          <Route path="/activate" element={<Activate />} />
          <Route path="/reset-password" element={<ResetPassword />} />
          <Route element={<RequireAuth />}>
            <Route path="/" element={<Home />} />
            <Route path="/dashboard" element={<Dashboard />} />
            <Route path="/patients" element={<Patients />} />
            <Route path="/appointments" element={<Appointments />} />
            <Route path="/appointments/:id" element={<AppointmentRoute />} />
            <Route path="/registration-requests" element={<Registrations />} />
            <Route path="/pharmacy" element={<Pharmacy />} />
            <Route path="/pharmacy/queue" element={<Pharmacy />} />
            <Route path="/pharmacy/alerts" element={<Pharmacy />} />
            <Route path="/billing" element={<Billing />} />
            <Route path="/billing/adjustments" element={<Billing />} />
            <Route path="/billing/invoices/:id" element={<Billing />} />
            <Route path="/laboratory" element={<Laboratory />} />
            <Route path="/wards" element={<Wards />} />
            <Route path="/hr" element={<Hr />} />
            <Route path="/my-work" element={<MyWork />} />
            <Route path="/admin/users" element={<Users />} />
            <Route path="/admin/doctors" element={<DoctorsAdmin />} />
            <Route path="/admin/prices" element={<Prices />} />
            <Route path="/audit" element={<AuditLog />} />
            <Route path="/account" element={<ChangePassword />} />
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