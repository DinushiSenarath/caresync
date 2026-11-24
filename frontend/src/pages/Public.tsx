import { useState } from 'react'
import type { FormEvent, ReactNode } from 'react'
import { Link } from 'react-router-dom'
import { api, ApiError } from '../api'
import { btnPrimary, ErrorBox, errMsg, Field, inputCls, todayStr } from '../ui'

function Shell({ title, subtitle, wide, children }: { title: string; subtitle?: string; wide?: boolean; children: ReactNode }) {
  return (
    <div className="flex min-h-screen items-center justify-center bg-slate-50 p-4">
      <div className={`w-full ${wide ? 'max-w-xl' : 'max-w-md'} rounded-xl border bg-white p-6 shadow-sm`}>
        <h1 className="text-2xl font-semibold text-teal-800">CareSync</h1>
        <p className="text-lg font-medium">{title}</p>
        {subtitle && <p className="mb-4 text-sm text-slate-500">{subtitle}</p>}
        {children}
        <p className="mt-5 border-t pt-4 text-sm">
          <Link to="/login" className="text-teal-700 hover:underline">Back to sign in</Link>
        </p>
      </div>
    </div>
  )
}

function fieldErrors(e: unknown): Record<string, string> {
  return e instanceof ApiError && e.fieldErrors ? e.fieldErrors : {}
}

/* ---------------- register online ---------------- */

export function Register() {
  const [f, setF] = useState({
    fullName: '', dateOfBirth: '', gender: 'MALE', nic: '', phone: '', address: '', consent: false, website: '',
  })
  const [error, setError] = useState<string | null>(null)
  const [fe, setFe] = useState<Record<string, string>>({})
  const [busy, setBusy] = useState(false)
  const [done, setDone] = useState<string | null>(null)

  const text = (k: 'fullName' | 'dateOfBirth' | 'nic' | 'phone' | 'address' | 'website') =>
    (e: { target: { value: string } }) => setF((p) => ({ ...p, [k]: e.target.value }))

  async function submit(e: FormEvent) {
    e.preventDefault()
    setError(null)
    setFe({})
    setBusy(true)
    try {
      const r = await api<{ message: string }>('/api/public/registration-requests', {
        method: 'POST',
        body: {
          fullName: f.fullName.trim(),
          dateOfBirth: f.dateOfBirth,
          gender: f.gender,
          nic: f.nic.trim() || null,
          phone: f.phone.trim(),
          address: f.address.trim() || null,
          consent: f.consent,
          website: f.website,
        },
      })
      setDone(r.message)
    } catch (err) {
      setError(errMsg(err))
      setFe(fieldErrors(err))
    } finally {
      setBusy(false)
    }
  }

  if (done) {
    return (
      <Shell title="Request received" wide>
        <p className="rounded-md bg-green-50 p-3 text-sm text-green-800">{done}</p>
      </Shell>
    )
  }

  return (
    <Shell title="Register as a patient" subtitle="Fill in your details. The hospital will contact you to confirm who you are. This does not create an account yet." wide>
      <form onSubmit={submit}>
        <ErrorBox message={error} />
        <div className="grid gap-3 sm:grid-cols-2">
          <Field label="Full name" error={fe.fullName} className="sm:col-span-2"><input className={inputCls} value={f.fullName} onChange={text('fullName')} required maxLength={120} /></Field>
          <Field label="Date of birth" error={fe.dateOfBirth}><input type="date" max={todayStr()} className={inputCls} value={f.dateOfBirth} onChange={text('dateOfBirth')} required /></Field>
          <Field label="Gender" error={fe.gender}>
            <select className={inputCls} value={f.gender} onChange={(e) => setF((p) => ({ ...p, gender: e.target.value }))}>
              <option value="MALE">Male</option>
              <option value="FEMALE">Female</option>
              <option value="OTHER">Other</option>
            </select>
          </Field>
          <Field label="Phone" error={fe.phone}><input className={inputCls} placeholder="0771234567" value={f.phone} onChange={text('phone')} required /></Field>
          <Field label="NIC (optional)" error={fe.nic}><input className={inputCls} value={f.nic} onChange={text('nic')} /></Field>
          <Field label="Address (optional)" error={fe.address} className="sm:col-span-2"><input className={inputCls} value={f.address} onChange={text('address')} maxLength={255} /></Field>
          <div aria-hidden="true" className="absolute -left-[9999px]">
            <label>Leave this empty<input tabIndex={-1} autoComplete="off" value={f.website} onChange={text('website')} /></label>
          </div>
          <label className="flex items-start gap-2 text-sm sm:col-span-2">
            <input type="checkbox" className="mt-1" checked={f.consent} onChange={(e) => setF((p) => ({ ...p, consent: e.target.checked }))} />
            <span>I agree that the hospital may store these details to register me as a patient and contact me on this phone number.</span>
          </label>
          {fe.consent && <p className="text-xs text-red-600 sm:col-span-2">{fe.consent}</p>}
        </div>
        <div className="mt-5"><button type="submit" className={`${btnPrimary} w-full`} disabled={busy || !f.consent}>{busy ? 'Sending...' : 'Send request'}</button></div>
      </form>
    </Shell>
  )
}

/* ---------------- activate account ---------------- */

export function Activate() {
  const [f, setF] = useState({ patientNo: '', code: '', username: '', password: '', again: '' })
  const [error, setError] = useState<string | null>(null)
  const [fe, setFe] = useState<Record<string, string>>({})
  const [busy, setBusy] = useState(false)
  const [done, setDone] = useState(false)

  const text = (k: keyof typeof f) => (e: { target: { value: string } }) => setF((p) => ({ ...p, [k]: e.target.value }))

  async function submit(e: FormEvent) {
    e.preventDefault()
    setError(null)
    setFe({})
    if (f.password !== f.again) { setError('The two passwords are not the same.'); return }
    setBusy(true)
    try {
      await api('/api/auth/activate', {
        method: 'POST',
        body: { patientNo: f.patientNo.trim(), code: f.code.trim(), username: f.username.trim(), password: f.password },
      })
      setDone(true)
    } catch (err) {
      setError(errMsg(err))
      setFe(fieldErrors(err))
    } finally {
      setBusy(false)
    }
  }

  if (done) {
    return (
      <Shell title="Account created">
        <p className="mb-3 rounded-md bg-green-50 p-3 text-sm text-green-800">Your account is ready. You can now sign in with your username and password.</p>
        <Link to="/login" className={`${btnPrimary} inline-block`}>Go to sign in</Link>
      </Shell>
    )
  }

  return (
    <Shell title="Activate your account" subtitle="Use the activation code the hospital gave you. You choose your own username and password.">
      <form onSubmit={submit}>
        <ErrorBox message={error} />
        <div className="grid gap-3">
          <Field label="Patient number" error={fe.patientNo}><input className={inputCls} placeholder="CS-000001" value={f.patientNo} onChange={text('patientNo')} required /></Field>
          <Field label="Activation code" error={fe.code}><input className={`${inputCls} font-mono`} placeholder="XXXXX-XXXXX" value={f.code} onChange={text('code')} required autoComplete="off" /></Field>
          <Field label="Choose a username (letters, digits, dot, dash, underscore)" error={fe.username}><input className={inputCls} value={f.username} onChange={text('username')} required minLength={3} maxLength={50} autoComplete="username" /></Field>
          <Field label="Choose a password (8 to 72 characters)" error={fe.password}><input type="password" className={inputCls} value={f.password} onChange={text('password')} required minLength={8} maxLength={72} autoComplete="new-password" /></Field>
          <Field label="Repeat the password"><input type="password" className={inputCls} value={f.again} onChange={text('again')} required minLength={8} maxLength={72} autoComplete="new-password" /></Field>
        </div>
        <div className="mt-5"><button type="submit" className={`${btnPrimary} w-full`} disabled={busy}>{busy ? 'Working...' : 'Create my account'}</button></div>
      </form>
    </Shell>
  )
}

/* ---------------- reset password ---------------- */

export function ResetPassword() {
  const [f, setF] = useState({ patientNo: '', code: '', password: '', again: '' })
  const [error, setError] = useState<string | null>(null)
  const [fe, setFe] = useState<Record<string, string>>({})
  const [busy, setBusy] = useState(false)
  const [done, setDone] = useState(false)

  const text = (k: keyof typeof f) => (e: { target: { value: string } }) => setF((p) => ({ ...p, [k]: e.target.value }))

  async function submit(e: FormEvent) {
    e.preventDefault()
    setError(null)
    setFe({})
    if (f.password !== f.again) { setError('The two passwords are not the same.'); return }
    setBusy(true)
    try {
      await api('/api/auth/reset-password', {
        method: 'POST',
        body: { patientNo: f.patientNo.trim(), code: f.code.trim(), newPassword: f.password },
      })
      setDone(true)
    } catch (err) {
      setError(errMsg(err))
      setFe(fieldErrors(err))
    } finally {
      setBusy(false)
    }
  }

  if (done) {
    return (
      <Shell title="Password changed">
        <p className="mb-3 rounded-md bg-green-50 p-3 text-sm text-green-800">Your password has been changed. You can now sign in with the new password.</p>
        <Link to="/login" className={`${btnPrimary} inline-block`}>Go to sign in</Link>
      </Shell>
    )
  }

  return (
    <Shell title="Reset your password" subtitle="Contact the hospital first. After they check who you are, they give you a reset code.">
      <form onSubmit={submit}>
        <ErrorBox message={error} />
        <div className="grid gap-3">
          <Field label="Patient number" error={fe.patientNo}><input className={inputCls} placeholder="CS-000001" value={f.patientNo} onChange={text('patientNo')} required /></Field>
          <Field label="Reset code" error={fe.code}><input className={`${inputCls} font-mono`} placeholder="XXXXX-XXXXX" value={f.code} onChange={text('code')} required autoComplete="off" /></Field>
          <Field label="New password (8 to 72 characters)" error={fe.newPassword}><input type="password" className={inputCls} value={f.password} onChange={text('password')} required minLength={8} maxLength={72} autoComplete="new-password" /></Field>
          <Field label="Repeat the new password"><input type="password" className={inputCls} value={f.again} onChange={text('again')} required minLength={8} maxLength={72} autoComplete="new-password" /></Field>
        </div>
        <div className="mt-5"><button type="submit" className={`${btnPrimary} w-full`} disabled={busy}>{busy ? 'Working...' : 'Change my password'}</button></div>
      </form>
    </Shell>
  )
}