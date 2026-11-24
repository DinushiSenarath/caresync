import { useEffect, useState } from 'react'
import type { FormEvent } from 'react'
import { api } from '../api'
import { useAuth } from '../auth'
import type { Role } from '../auth'
import { ROLE_LABEL } from '../menu'
import { btnGhost, btnPrimary, btnSmall, detailMsg, ErrorBox, Field, fmtDateTime, inputCls, Modal, Spinner } from '../ui'

interface AppUser {
  id: number
  username: string
  fullName: string
  email: string | null
  role: Role
  active: boolean
  lastLogin: string | null
}

const STAFF_ROLES: Role[] = ['ADMIN', 'RECEPTIONIST', 'DOCTOR', 'NURSE', 'PHARMACIST', 'CASHIER', 'LAB_TECH', 'WARD_STAFF']

function generatePassword(): string {
  const chars = 'ABCDEFGHJKMNPQRSTUVWXYZabcdefghjkmnpqrstuvwxyz23456789'
  const bytes = new Uint32Array(12)
  crypto.getRandomValues(bytes)
  return Array.from(bytes, (b) => chars[b % chars.length]).join('')
}

function Credentials({ username, password, onDone }: { username?: string; password: string; onDone: () => void }) {
  return (
    <div>
      <p className="mb-3 text-sm text-slate-600">
        Give these details to the person. The password is shown only now and cannot be looked up later. They should change it after their first sign-in.
      </p>
      <div className="rounded-lg bg-slate-100 p-4 text-center font-mono">
        {username && <div className="mb-1 text-sm text-slate-600">Username: <span className="font-semibold">{username}</span></div>}
        <div className="text-2xl tracking-wide">{password}</div>
      </div>
      <div className="mt-5 flex justify-end gap-2">
        <button className={btnGhost} onClick={() => void navigator.clipboard?.writeText(password)}>Copy password</button>
        <button className={btnPrimary} onClick={onDone}>Done</button>
      </div>
    </div>
  )
}

function UserForm({ user, onClose, onSaved }: { user: AppUser | null; onClose: () => void; onSaved: () => void }) {
  const [f, setF] = useState({
    username: '',
    password: user ? '' : generatePassword(),
    fullName: user?.fullName ?? '',
    email: user?.email ?? '',
    role: (user?.role ?? 'RECEPTIONIST') as Role,
  })
  const [error, setError] = useState<string | null>(null)
  const [fe, setFe] = useState<Record<string, string>>({})
  const [busy, setBusy] = useState(false)
  const [created, setCreated] = useState<{ username: string; password: string } | null>(null)

  async function submit(e: FormEvent) {
    e.preventDefault()
    setError(null)
    setFe({})
    setBusy(true)
    try {
      if (user) {
        await api(`/api/users/${user.id}`, {
          method: 'PUT',
          body: { fullName: f.fullName.trim(), email: f.email.trim() || null, role: f.role },
        })
        onSaved()
      } else {
        await api('/api/users', {
          method: 'POST',
          body: {
            username: f.username.trim(),
            password: f.password,
            fullName: f.fullName.trim(),
            email: f.email.trim() || null,
            role: f.role,
          },
        })
        setCreated({ username: f.username.trim().toLowerCase(), password: f.password })
      }
    } catch (err) {
      setError(detailMsg(err))
      const fields = (err as { fieldErrors?: Record<string, string> | null }).fieldErrors
      if (fields) setFe(fields)
    } finally {
      setBusy(false)
    }
  }

  if (created) {
    return (
      <Modal title="Account created" onClose={onSaved}>
        <Credentials username={created.username} password={created.password} onDone={onSaved} />
      </Modal>
    )
  }

  return (
    <Modal title={user ? `Edit ${user.username}` : 'Create staff account'} onClose={onClose}>
      <form onSubmit={submit}>
        <ErrorBox message={error} />
        <div className="grid gap-3">
          {!user && (
            <Field label="Username (3 to 50 letters, digits, dot, dash or underscore)" error={fe.username}>
              <input className={inputCls} value={f.username} onChange={(e) => setF((p) => ({ ...p, username: e.target.value }))} required maxLength={50} autoComplete="off" />
            </Field>
          )}
          <Field label="Full name" error={fe.fullName}>
            <input className={inputCls} value={f.fullName} onChange={(e) => setF((p) => ({ ...p, fullName: e.target.value }))} required maxLength={120} />
          </Field>
          <Field label="Email (optional)" error={fe.email}>
            <input type="email" className={inputCls} value={f.email} onChange={(e) => setF((p) => ({ ...p, email: e.target.value }))} maxLength={120} />
          </Field>
          <Field label="Role" error={fe.role}>
            <select className={inputCls} value={f.role} onChange={(e) => setF((p) => ({ ...p, role: e.target.value as Role }))}>
              {STAFF_ROLES.map((r) => <option key={r} value={r}>{ROLE_LABEL[r]}</option>)}
            </select>
          </Field>
          {!user && (
            <Field label="First password (8 to 72 characters)" error={fe.password}>
              <div className="flex gap-2">
                <input className={`${inputCls} font-mono`} value={f.password} onChange={(e) => setF((p) => ({ ...p, password: e.target.value }))} required minLength={8} maxLength={72} autoComplete="off" />
                <button type="button" className={btnGhost} onClick={() => setF((p) => ({ ...p, password: generatePassword() }))}>Generate</button>
              </div>
            </Field>
          )}
        </div>
        {user && <p className="mt-3 text-xs text-slate-500">A change of role takes effect the next time the person signs in. A doctor also needs a doctor profile under Departments and doctors.</p>}
        <div className="mt-5 flex justify-end gap-2">
          <button type="button" className={btnGhost} onClick={onClose}>Cancel</button>
          <button type="submit" className={btnPrimary} disabled={busy}>{busy ? 'Saving...' : user ? 'Save' : 'Create account'}</button>
        </div>
      </form>
    </Modal>
  )
}

function ResetModal({ user, onClose }: { user: AppUser; onClose: () => void }) {
  const [password, setPassword] = useState(generatePassword())
  const [done, setDone] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  async function submit(e: FormEvent) {
    e.preventDefault()
    setError(null)
    setBusy(true)
    try {
      await api(`/api/users/${user.id}/reset-password`, { method: 'POST', body: { newPassword: password } })
      setDone(true)
    } catch (err) {
      setError(detailMsg(err))
    } finally {
      setBusy(false)
    }
  }

  return (
    <Modal title={`Reset password: ${user.username}`} onClose={onClose}>
      {done ? (
        <Credentials password={password} onDone={onClose} />
      ) : (
        <form onSubmit={submit}>
          <ErrorBox message={error} />
          <p className="mb-3 text-sm text-slate-600">
            This replaces the old password straight away and clears any lockout. {user.fullName} should change the new password after signing in.
          </p>
          <Field label="New password (8 to 72 characters)">
            <div className="flex gap-2">
              <input className={`${inputCls} font-mono`} value={password} onChange={(e) => setPassword(e.target.value)} required minLength={8} maxLength={72} autoComplete="off" />
              <button type="button" className={btnGhost} onClick={() => setPassword(generatePassword())}>Generate</button>
            </div>
          </Field>
          <div className="mt-5 flex justify-end gap-2">
            <button type="button" className={btnGhost} onClick={onClose}>Cancel</button>
            <button type="submit" className={btnPrimary} disabled={busy}>{busy ? 'Saving...' : 'Reset password'}</button>
          </div>
        </form>
      )}
    </Modal>
  )
}

export default function Users() {
  const { user: me } = useAuth()
  const [list, setList] = useState<AppUser[] | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [reload, setReload] = useState(0)
  const [q, setQ] = useState('')
  const [role, setRole] = useState('')
  const [status, setStatus] = useState('ACTIVE')
  const [form, setForm] = useState<{ user: AppUser | null } | null>(null)
  const [reset, setReset] = useState<AppUser | null>(null)

  useEffect(() => {
    let active = true
    api<AppUser[]>('/api/users')
      .then((r) => { if (active) { setList(r); setError(null) } })
      .catch((e) => { if (active) setError(detailMsg(e)) })
    return () => { active = false }
  }, [reload])

  async function toggle(u: AppUser) {
    if (!window.confirm(`${u.active ? 'Deactivate' : 'Reactivate'} ${u.fullName}?${u.active ? ' They will not be able to sign in.' : ''}`)) return
    setError(null)
    try {
      await api(`/api/users/${u.id}/active`, { method: 'PATCH', body: { active: !u.active } })
      setReload((n) => n + 1)
    } catch (e) {
      setError(detailMsg(e))
    }
  }

  const shown = (list ?? []).filter((u) => {
    const text = `${u.username} ${u.fullName} ${u.email ?? ''}`.toLowerCase()
    return text.includes(q.trim().toLowerCase())
      && (role === '' || u.role === role)
      && (status === 'ALL' || (status === 'ACTIVE' ? u.active : !u.active))
  })

  return (
    <div>
      <div className="mb-4 flex flex-wrap items-center justify-between gap-3">
        <h1 className="text-2xl font-semibold">Staff accounts</h1>
        <button className={btnPrimary} onClick={() => setForm({ user: null })}>Create staff account</button>
      </div>
      <div className="mb-3 flex flex-wrap items-end gap-3">
        <input className={`${inputCls} max-w-xs`} placeholder="Search name, username or email" value={q} onChange={(e) => setQ(e.target.value)} aria-label="Search accounts" />
        <Field label="Role">
          <select className={inputCls} value={role} onChange={(e) => setRole(e.target.value)}>
            <option value="">All roles</option>
            {(Object.keys(ROLE_LABEL) as Role[]).map((r) => <option key={r} value={r}>{ROLE_LABEL[r]}</option>)}
          </select>
        </Field>
        <Field label="Status">
          <select className={inputCls} value={status} onChange={(e) => setStatus(e.target.value)}>
            <option value="ACTIVE">Active</option>
            <option value="INACTIVE">Deactivated</option>
            <option value="ALL">All</option>
          </select>
        </Field>
      </div>
      <ErrorBox message={error} />
      {!list && !error ? (
        <Spinner />
      ) : (
        <div className="overflow-x-auto rounded-xl border bg-white">
          <table className="w-full text-left text-sm">
            <thead className="bg-slate-50 text-slate-500">
              <tr>
                <th className="px-3 py-2">Username</th>
                <th className="px-3 py-2">Name</th>
                <th className="px-3 py-2">Role</th>
                <th className="px-3 py-2">Last sign-in</th>
                <th className="px-3 py-2">Actions</th>
              </tr>
            </thead>
            <tbody>
              {shown.length === 0 && <tr><td colSpan={5} className="px-3 py-6 text-center text-slate-500">No accounts match.</td></tr>}
              {shown.map((u) => (
                <tr key={u.id} className={`border-t ${u.active ? '' : 'text-slate-400'}`}>
                  <td className="px-3 py-2 font-medium">{u.username}{!u.active && <span className="ml-2 rounded bg-slate-200 px-1.5 py-0.5 text-xs text-slate-600">Deactivated</span>}</td>
                  <td className="px-3 py-2">{u.fullName}{u.email && <div className="text-xs text-slate-400">{u.email}</div>}</td>
                  <td className="px-3 py-2">{ROLE_LABEL[u.role]}</td>
                  <td className="px-3 py-2">{u.lastLogin ? fmtDateTime(u.lastLogin) : 'Never'}</td>
                  <td className="px-3 py-2">
                    <div className="flex flex-wrap gap-1">
                      {u.role !== 'PATIENT' && <button className={btnSmall} onClick={() => setForm({ user: u })}>Edit</button>}
                      {u.role !== 'PATIENT' && <button className={btnSmall} onClick={() => setReset(u)}>Reset password</button>}
                      {u.username !== me?.username && <button className={btnSmall} onClick={() => void toggle(u)}>{u.active ? 'Deactivate' : 'Reactivate'}</button>}
                    </div>
                    {u.role === 'PATIENT' && <div className="mt-1 text-xs text-slate-400">Patient passwords are reset from the patient record.</div>}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
      {form && <UserForm user={form.user} onClose={() => setForm(null)} onSaved={() => { setForm(null); setReload((n) => n + 1) }} />}
      {reset && <ResetModal user={reset} onClose={() => setReset(null)} />}
    </div>
  )
}