import { useState } from 'react'
import type { FormEvent } from 'react'
import { Link, Navigate, useLocation, useNavigate } from 'react-router-dom'
import { ApiError } from '../api'
import { useAuth } from '../auth'
import { homePath } from '../menu'

export default function Login() {
  const { user, login, notice } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  if (user) return <Navigate to={homePath(user.role)} replace />

  async function submit(e: FormEvent) {
    e.preventDefault()
    setError(null)
    setBusy(true)
    try {
      await login(username.trim(), password)
      const from = (location.state as { from?: string } | null)?.from
      navigate(from && from !== '/login' ? from : '/', { replace: true })
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Sign in failed. Please try again.')
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="flex min-h-screen items-center justify-center bg-slate-50 p-4">
      <form onSubmit={submit} className="w-full max-w-sm rounded-xl border bg-white p-6 shadow-sm">
        <h1 className="text-2xl font-semibold text-teal-800">CareSync</h1>
        <p className="mb-5 text-sm text-slate-500">Hospital Management System</p>

        {notice && <p className="mb-4 rounded-md bg-amber-50 p-3 text-sm text-amber-800">{notice}</p>}
        {error && <p role="alert" className="mb-4 rounded-md bg-red-50 p-3 text-sm text-red-700">{error}</p>}

        <label className="mb-1 block text-sm font-medium" htmlFor="username">Username</label>
        <input
          id="username"
          autoComplete="username"
          required
          value={username}
          onChange={(e) => setUsername(e.target.value)}
          className="mb-4 w-full rounded-md border px-3 py-2 focus:outline-2 focus:outline-teal-600"
        />

        <label className="mb-1 block text-sm font-medium" htmlFor="password">Password</label>
        <input
          id="password"
          type="password"
          autoComplete="current-password"
          required
          value={password}
          onChange={(e) => setPassword(e.target.value)}
          className="mb-5 w-full rounded-md border px-3 py-2 focus:outline-2 focus:outline-teal-600"
        />

        <button
          type="submit"
          disabled={busy}
          className="w-full rounded-md bg-teal-700 py-2 font-medium text-white hover:bg-teal-800 disabled:opacity-60"
        >
          {busy ? 'Signing in...' : 'Sign in'}
        </button>
        {busy && (
          <p className="mt-3 text-xs text-slate-500">
            The first sign-in after a quiet period can take up to a minute while the server wakes up.
          </p>
        )}

        <div className="mt-5 space-y-1 border-t pt-4 text-sm">
          <p className="font-medium">Patients</p>
          <p><Link to="/register" className="text-teal-700 hover:underline">Register online</Link></p>
          <p><Link to="/activate" className="text-teal-700 hover:underline">I have an activation code</Link></p>
          <p><Link to="/reset-password" className="text-teal-700 hover:underline">I forgot my password</Link></p>
        </div>
      </form>
    </div>
  )
}