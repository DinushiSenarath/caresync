import { useEffect, useState } from 'react'
import type { FormEvent } from 'react'
import { api, ApiError } from '../api'
import type { Page } from '../types'
import type { Attendance, HrMe, Leave } from '../hrTypes'
import { LEAVE_STYLE, LEAVE_TYPES, leaveLabel, nice } from '../hrTypes'
import {
  btnGhost, btnPrimary, btnSmall, detailMsg, ErrorBox, Field, fmtClock, fmtDate, inputCls, Spinner, todayStr,
} from '../ui'

export default function MyWork() {
  const [me, setMe] = useState<HrMe | null>(null)
  const [missing, setMissing] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [message, setMessage] = useState<string | null>(null)
  const [reload, setReload] = useState(0)
  const [busy, setBusy] = useState(false)
  const [month, setMonth] = useState(todayStr().slice(0, 7))
  const [attendance, setAttendance] = useState<Attendance[]>([])
  const [leaves, setLeaves] = useState<Leave[]>([])
  const [type, setType] = useState('ANNUAL')
  const [from, setFrom] = useState(todayStr())
  const [to, setTo] = useState(todayStr())
  const [reason, setReason] = useState('')

  useEffect(() => {
    let active = true
    api<HrMe>('/api/hr/me')
      .then((r) => { if (active) { setMe(r); setMissing(null) } })
      .catch((e) => {
        if (!active) return
        if (e instanceof ApiError && e.status === 404) setMissing(e.message)
        else setError(detailMsg(e))
      })
    return () => { active = false }
  }, [reload])

  useEffect(() => {
    if (missing) return
    let active = true
    Promise.all([
      api<Attendance[]>('/api/hr/me/attendance', { query: { month } }),
      api<Page<Leave>>('/api/hr/me/leave', { query: { size: 50 } }),
    ])
      .then(([a, l]) => { if (active) { setAttendance(a); setLeaves(l.content) } })
      .catch(() => { /* the main call above shows any problem */ })
    return () => { active = false }
  }, [month, reload, missing])

  async function act(fn: () => Promise<unknown>, done: string) {
    setBusy(true)
    setError(null)
    setMessage(null)
    try {
      await fn()
      setMessage(done)
      setReload((n) => n + 1)
    } catch (e) {
      setError(detailMsg(e))
    } finally {
      setBusy(false)
    }
  }

  async function requestLeave(e: FormEvent) {
    e.preventDefault()
    await act(() => api('/api/hr/me/leave', {
      method: 'POST',
      body: { leaveType: type, fromDate: from, toDate: to, reason: reason.trim() || null },
    }), 'Leave request sent. The administrator has been told.')
    setReason('')
  }

  async function cancelLeave(l: Leave) {
    if (!window.confirm('Cancel this leave request?')) return
    await act(() => api(`/api/hr/leave/${l.id}/cancel`, { method: 'POST' }), 'Leave cancelled.')
  }

  if (missing) {
    return (
      <div>
        <h1 className="mb-3 text-2xl font-semibold">My attendance and leave</h1>
        <p className="rounded-md bg-amber-50 p-3 text-sm text-amber-900">{missing}</p>
      </div>
    )
  }
  if (!me && !error) return <Spinner />

  const t = me?.today
  const checkedIn = !!t && t.status !== 'ABSENT' && !!t.checkIn
  const checkedOut = !!t && !!t.checkOut

  return (
    <div>
      <h1 className="mb-1 text-2xl font-semibold">My attendance and leave</h1>
      {me && <p className="mb-4 text-sm text-slate-500">{me.fullName} · {me.employeeNo} · {me.jobTitle}, {me.departmentName}</p>}
      <ErrorBox message={error} />
      {message && <p className="mb-3 rounded-md bg-green-50 p-2 text-sm text-green-800">{message}</p>}

      {me && (
        <section className="mb-5 rounded-xl border bg-white p-4">
          <h2 className="mb-2 text-lg font-medium">Today</h2>
          {me.onLeaveToday && <p className="mb-2 rounded bg-sky-50 p-2 text-sm text-sky-800">You are on approved leave today.</p>}
          {t?.status === 'ABSENT' && <p className="mb-2 rounded bg-red-50 p-2 text-sm text-red-700">Marked absent today by an administrator.</p>}
          {checkedIn && <p className="mb-2 text-sm">Checked in at <span className="font-medium">{fmtClock(t?.checkIn)}</span>{checkedOut && <> · checked out at <span className="font-medium">{fmtClock(t?.checkOut)}</span> ({t?.hours} hours)</>}</p>}
          {!checkedIn && !me.onLeaveToday && t?.status !== 'ABSENT' && <p className="mb-2 text-sm text-slate-500">You have not checked in yet.</p>}
          <div className="flex gap-2">
            <button className={btnPrimary} disabled={busy || checkedIn || me.onLeaveToday || t?.status === 'ABSENT'} onClick={() => void act(() => api('/api/hr/me/check-in', { method: 'POST' }), 'Checked in.')}>Check in</button>
            <button className={btnGhost} disabled={busy || !checkedIn || checkedOut} onClick={() => void act(() => api('/api/hr/me/check-out', { method: 'POST' }), 'Checked out.')}>Check out</button>
          </div>
        </section>
      )}

      {me && (
        <section className="mb-5 rounded-xl border bg-white p-4">
          <h2 className="mb-2 text-lg font-medium">Leave balance for {todayStr().slice(0, 4)}</h2>
          <div className="overflow-x-auto">
            <table className="w-full text-left text-sm">
              <thead className="border-b text-xs text-slate-500">
                <tr>
                  <th className="py-1 pr-3">Type</th>
                  <th className="py-1 pr-3 text-right">Entitlement</th>
                  <th className="py-1 pr-3 text-right">Taken</th>
                  <th className="py-1 pr-3 text-right">Waiting</th>
                  <th className="py-1 text-right">Remaining</th>
                </tr>
              </thead>
              <tbody>
                {me.balances.map((b) => (
                  <tr key={b.type} className="border-b">
                    <td className="py-1 pr-3">{leaveLabel(b.type)}</td>
                    <td className="py-1 pr-3 text-right">{b.entitlement ?? 'No limit'}</td>
                    <td className="py-1 pr-3 text-right">{b.used}</td>
                    <td className="py-1 pr-3 text-right">{b.pending}</td>
                    <td className="py-1 text-right font-medium">{b.remaining ?? ''}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </section>
      )}

      <section className="mb-5 rounded-xl border bg-white p-4">
        <h2 className="mb-2 text-lg font-medium">Request leave</h2>
        <form onSubmit={requestLeave}>
          <div className="grid gap-3 sm:grid-cols-4">
            <Field label="Type">
              <select className={inputCls} value={type} onChange={(e) => setType(e.target.value)}>
                {LEAVE_TYPES.map((x) => <option key={x.value} value={x.value}>{x.label}</option>)}
              </select>
            </Field>
            <Field label="From"><input type="date" className={inputCls} value={from} onChange={(e) => setFrom(e.target.value)} required /></Field>
            <Field label="To"><input type="date" className={inputCls} value={to} min={from} onChange={(e) => setTo(e.target.value)} required /></Field>
            <Field label="Reason (optional)"><input className={inputCls} value={reason} onChange={(e) => setReason(e.target.value)} maxLength={255} /></Field>
          </div>
          <p className="mt-2 text-xs text-slate-500">Saturdays and Sundays are not counted. Requests can start up to 7 days ago.</p>
          <div className="mt-3"><button type="submit" className={btnPrimary} disabled={busy}>Send request</button></div>
        </form>
      </section>

      <section className="mb-5 rounded-xl border bg-white p-4">
        <h2 className="mb-2 text-lg font-medium">My leave requests</h2>
        {leaves.length === 0 && <p className="text-sm text-slate-500">No leave requests yet.</p>}
        <ul className="space-y-2">
          {leaves.map((l) => (
            <li key={l.id} className="flex flex-wrap items-center justify-between gap-2 rounded border px-3 py-2 text-sm">
              <span>
                <span className={`mr-2 rounded-full px-2 py-0.5 text-xs font-medium ${LEAVE_STYLE[l.status]}`}>{nice(l.status)}</span>
                {leaveLabel(l.leaveType)}, {fmtDate(l.fromDate)} to {fmtDate(l.toDate)} ({l.days} day{l.days === 1 ? '' : 's'})
                {l.decisionNote && <span className="text-slate-500"> · {l.decisionNote}</span>}
              </span>
              {(l.status === 'PENDING' || (l.status === 'APPROVED' && l.fromDate > todayStr())) && (
                <button className={btnSmall} disabled={busy} onClick={() => void cancelLeave(l)}>Cancel</button>
              )}
            </li>
          ))}
        </ul>
      </section>

      <section className="rounded-xl border bg-white p-4">
        <div className="mb-2 flex flex-wrap items-center justify-between gap-2">
          <h2 className="text-lg font-medium">My attendance</h2>
          <input type="month" className={`${inputCls} w-auto`} value={month} onChange={(e) => e.target.value && setMonth(e.target.value)} aria-label="Month" />
        </div>
        {attendance.length === 0 && <p className="text-sm text-slate-500">No records for this month.</p>}
        {attendance.length > 0 && (
          <div className="overflow-x-auto">
            <table className="w-full text-left text-sm">
              <thead className="border-b text-xs text-slate-500">
                <tr>
                  <th className="py-1 pr-3">Date</th>
                  <th className="py-1 pr-3">Status</th>
                  <th className="py-1 pr-3">In</th>
                  <th className="py-1 pr-3">Out</th>
                  <th className="py-1">Hours</th>
                </tr>
              </thead>
              <tbody>
                {attendance.map((a) => (
                  <tr key={a.id} className="border-b">
                    <td className="py-1 pr-3">{fmtDate(a.workDate)}</td>
                    <td className="py-1 pr-3">{nice(a.status)}</td>
                    <td className="py-1 pr-3">{fmtClock(a.checkIn)}</td>
                    <td className="py-1 pr-3">{fmtClock(a.checkOut)}</td>
                    <td className="py-1">{a.hours ?? ''}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </section>
    </div>
  )
}