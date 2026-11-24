import { useState } from 'react'
import type { FormEvent } from 'react'
import { api } from '../api'
import { btnPrimary, detailMsg, ErrorBox, Field, inputCls } from '../ui'

export default function ChangePassword() {
  const [current, setCurrent] = useState('')
  const [next, setNext] = useState('')
  const [again, setAgain] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [done, setDone] = useState(false)
  const [busy, setBusy] = useState(false)

  async function submit(e: FormEvent) {
    e.preventDefault()
    setError(null)
    setDone(false)
    if (next !== again) { setError('The two new passwords are not the same.'); return }
    if (next === current) { setError('The new password must be different from the current one.'); return }
    setBusy(true)
    try {
      await api('/api/auth/change-password', { method: 'POST', body: { currentPassword: current, newPassword: next } })
      setDone(true)
      setCurrent('')
      setNext('')
      setAgain('')
    } catch (err) {
      setError(detailMsg(err))
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="max-w-md">
      <h1 className="mb-4 text-2xl font-semibold">Change password</h1>
      <form onSubmit={submit} className="rounded-xl border bg-white p-5">
        <ErrorBox message={error} />
        {done && <p className="mb-3 rounded-md bg-green-50 p-2 text-sm text-green-800">Your password has been changed.</p>}
        <div className="grid gap-3">
          <Field label="Current password"><input type="password" autoComplete="current-password" className={inputCls} value={current} onChange={(e) => setCurrent(e.target.value)} required /></Field>
          <Field label="New password (8 to 72 characters)"><input type="password" autoComplete="new-password" className={inputCls} value={next} onChange={(e) => setNext(e.target.value)} required minLength={8} maxLength={72} /></Field>
          <Field label="Repeat the new password"><input type="password" autoComplete="new-password" className={inputCls} value={again} onChange={(e) => setAgain(e.target.value)} required minLength={8} maxLength={72} /></Field>
        </div>
        <div className="mt-5"><button type="submit" className={btnPrimary} disabled={busy}>{busy ? 'Saving...' : 'Change password'}</button></div>
      </form>
    </div>
  )
}