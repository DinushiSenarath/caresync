import { useEffect, useState } from 'react'
import type { FormEvent } from 'react'
import { api } from '../api'
import { useAuth } from '../auth'
import type { Role } from '../auth'
import type { Appointment, AppointmentStatus, Doctor, Page, Patient, Slot } from '../types'
import { Link } from 'react-router-dom'
import {
  btnGhost, btnPrimary, btnSmall, ErrorBox, errMsg, Field, fmtDate, fmtTime, inputCls,
  Modal, Spinner, StatusBadge, STATUS_LABEL, todayStr, useDebounced,
} from '../ui'

const pad = (n: number) => String(n).padStart(2, '0')

function shiftDate(s: string, days: number): string {
  const [y, m, d] = s.split('-').map(Number)
  return new Date(Date.UTC(y, m - 1, d + days)).toISOString().slice(0, 10)
}

interface Act {
  label: string
  status?: AppointmentStatus
  needsReason?: boolean
  reschedule?: boolean
  danger?: boolean
}

function actionsFor(a: Appointment, role: Role | undefined, today: string): Act[] {
  const out: Act[] = []
  if (role === 'ADMIN' || role === 'RECEPTIONIST') {
    if (a.status === 'REQUESTED') {
      out.push({ label: 'Confirm', status: 'SCHEDULED' })
      out.push({ label: 'Reschedule', reschedule: true })
      out.push({ label: 'Reject', status: 'CANCELLED', needsReason: true, danger: true })
    }
    if (a.status === 'SCHEDULED') {
      if (a.date === today) out.push({ label: 'Check in', status: 'CHECKED_IN' })
      out.push({ label: 'Reschedule', reschedule: true })
      out.push({ label: 'Cancel', status: 'CANCELLED', needsReason: true, danger: true })
      if (a.date <= today) out.push({ label: 'Mark missed', status: 'MISSED', danger: true })
    }
  }
  if (role === 'DOCTOR') {
    if (a.status === 'CHECKED_IN') out.push({ label: 'Start consultation', status: 'IN_PROGRESS' })
    if (a.status === 'IN_PROGRESS') out.push({ label: 'Complete', status: 'COMPLETED' })
  }
  return out
}

function PatientPicker({ value, onChange }: { value: Patient | null; onChange: (p: Patient | null) => void }) {
  const [q, setQ] = useState('')
  const dq = useDebounced(q)
  const [results, setResults] = useState<Patient[]>([])
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    let active = true
    if (dq.trim().length < 2) {
      setResults([])
      return
    }
    api<Page<Patient>>('/api/patients', { query: { q: dq, size: 6 } })
      .then((r) => { if (active) setResults(r.content) })
      .catch((e) => { if (active) setError(errMsg(e)) })
    return () => { active = false }
  }, [dq])

  if (value) {
    return (
      <div className="flex items-center justify-between rounded-md border bg-slate-50 px-3 py-2 text-sm">
        <span><span className="font-medium">{value.patientNo}</span> {value.fullName}</span>
        <button type="button" className={btnSmall} onClick={() => onChange(null)}>Change</button>
      </div>
    )
  }
  return (
    <div>
      <input
        className={inputCls}
        placeholder="Type at least 2 letters of the name, phone or number"
        value={q}
        onChange={(e) => setQ(e.target.value)}
      />
      <ErrorBox message={error} />
      {results.length > 0 && (
        <ul className="mt-1 rounded-md border bg-white">
          {results.map((p) => (
            <li key={p.id}>
              <button
                type="button"
                className="block w-full px-3 py-2 text-left text-sm hover:bg-teal-50"
                onClick={() => onChange(p)}
              >
                <span className="font-medium">{p.patientNo}</span> {p.fullName}
                <span className="text-slate-400"> · {p.phone}</span>
              </button>
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}

function BookingModal({
  appointment, doctors, onClose, onSaved,
}: { appointment: Appointment | null; doctors: Doctor[]; onClose: () => void; onSaved: () => void }) {
  const [patient, setPatient] = useState<Patient | null>(null)
  const [doctorId, setDoctorId] = useState(appointment ? String(appointment.doctorId) : '')
  const [date, setDate] = useState(appointment ? appointment.date : todayStr())
  const [time, setTime] = useState('')
  const [reason, setReason] = useState('')
  const [slots, setSlots] = useState<Slot[] | null>(null)
  const [slotError, setSlotError] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  useEffect(() => {
    let active = true
    if (!doctorId || !date) {
      setSlots(null)
      return
    }
    setSlotError(null)
    api<Slot[]>('/api/appointments/slots', { query: { doctorId, date } })
      .then((r) => { if (active) setSlots(r) })
      .catch((e) => { if (active) { setSlots(null); setSlotError(errMsg(e)) } })
    return () => { active = false }
  }, [doctorId, date])

  async function submit(e: FormEvent) {
    e.preventDefault()
    setError(null)
    if (!time) {
      setError('Choose a time slot.')
      return
    }
    setBusy(true)
    try {
      if (appointment) {
        await api(`/api/appointments/${appointment.id}`, {
          method: 'PUT',
          body: { doctorId: Number(doctorId), date, time },
        })
      } else {
        if (!patient) {
          setError('Choose a patient.')
          setBusy(false)
          return
        }
        await api('/api/appointments', {
          method: 'POST',
          body: { patientId: patient.id, doctorId: Number(doctorId), date, time, reason },
        })
      }
      onSaved()
    } catch (err) {
      setError(errMsg(err))
    } finally {
      setBusy(false)
    }
  }

  return (
    <Modal title={appointment ? `Reschedule: ${appointment.patientName}` : 'Book appointment'} onClose={onClose} wide>
      <form onSubmit={submit}>
        <ErrorBox message={error} />
        <div className="grid gap-3 sm:grid-cols-2">
          {!appointment && (
            <Field label="Patient" className="sm:col-span-2">
              <PatientPicker value={patient} onChange={setPatient} />
            </Field>
          )}
          <Field label="Doctor">
            <select
              className={inputCls}
              value={doctorId}
              onChange={(e) => { setDoctorId(e.target.value); setTime('') }}
              required
            >
              <option value="">Select a doctor</option>
              {doctors.map((d) => (
                <option key={d.id} value={d.id}>{d.fullName} ({d.departmentName})</option>
              ))}
            </select>
          </Field>
          <Field label="Date">
            <input
              type="date"
              min={todayStr()}
              className={inputCls}
              value={date}
              onChange={(e) => { setDate(e.target.value); setTime('') }}
              required
            />
          </Field>
          {!appointment && (
            <Field label="Reason (optional)" className="sm:col-span-2">
              <input className={inputCls} value={reason} onChange={(e) => setReason(e.target.value)} maxLength={255} />
            </Field>
          )}
        </div>

        <div className="mt-4">
          <div className="mb-1 text-sm font-medium">Time slot</div>
          <ErrorBox message={slotError} />
          {!doctorId && <p className="text-sm text-slate-500">Choose a doctor and date to see the free slots.</p>}
          {slots && slots.length === 0 && (
            <p className="text-sm text-slate-500">The doctor does not work on this day, or is not available.</p>
          )}
          {slots && slots.length > 0 && slots.every((s) => !s.available) && (
            <p className="mb-2 text-sm text-amber-700">All slots on this day are taken or have passed.</p>
          )}
          {slots && slots.length > 0 && (
            <div className="flex flex-wrap gap-2">
              {slots.map((s) => (
                <button
                  type="button"
                  key={s.time}
                  disabled={!s.available}
                  onClick={() => setTime(s.time)}
                  className={`rounded border px-2 py-1 text-sm ${
                    time === s.time
                      ? 'border-teal-700 bg-teal-700 text-white'
                      : s.available
                        ? 'hover:bg-teal-50'
                        : 'bg-slate-100 text-slate-400 line-through'
                  }`}
                >
                  {fmtTime(s.time)}
                </button>
              ))}
            </div>
          )}
        </div>

        <div className="mt-5 flex justify-end gap-2">
          <button type="button" className={btnGhost} onClick={onClose}>Cancel</button>
          <button type="submit" className={btnPrimary} disabled={busy}>
            {busy ? 'Saving...' : appointment ? 'Reschedule' : 'Book appointment'}
          </button>
        </div>
      </form>
    </Modal>
  )
}

function ReasonModal({
  appointment, status, title, onClose, onDone,
}: { appointment: Appointment; status: AppointmentStatus; title: string; onClose: () => void; onDone: () => void }) {
  const [reason, setReason] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  async function submit(e: FormEvent) {
    e.preventDefault()
    setBusy(true)
    setError(null)
    try {
      await api(`/api/appointments/${appointment.id}/status`, { method: 'PATCH', body: { status, reason } })
      onDone()
    } catch (err) {
      setError(errMsg(err))
    } finally {
      setBusy(false)
    }
  }

  return (
    <Modal title={title} onClose={onClose}>
      <form onSubmit={submit}>
        <p className="mb-3 text-sm text-slate-600">
          {appointment.patientName}, {fmtDate(appointment.date)} {fmtTime(appointment.startTime)}
        </p>
        <ErrorBox message={error} />
        <Field label="Reason (required)">
          <input className={inputCls} value={reason} onChange={(e) => setReason(e.target.value)} required maxLength={255} />
        </Field>
        <div className="mt-5 flex justify-end gap-2">
          <button type="button" className={btnGhost} onClick={onClose}>Back</button>
          <button type="submit" className={btnPrimary} disabled={busy || !reason.trim()}>{busy ? 'Saving...' : title}</button>
        </div>
      </form>
    </Modal>
  )
}

function CalendarView({
  doctorId, reloadKey, onPickDay,
}: { doctorId: string; reloadKey: number; onPickDay: (d: string) => void }) {
  const today = todayStr()
  const [ym, setYm] = useState(() => {
    const [y, m] = today.split('-').map(Number)
    return { y, m: m - 1 }
  })
  const [items, setItems] = useState<Appointment[]>([])
  const [total, setTotal] = useState(0)
  const [error, setError] = useState<string | null>(null)
  const [loading, setLoading] = useState(true)

  const days = new Date(ym.y, ym.m + 1, 0).getDate()
  const from = `${ym.y}-${pad(ym.m + 1)}-01`
  const to = `${ym.y}-${pad(ym.m + 1)}-${pad(days)}`

  useEffect(() => {
    let active = true
    setLoading(true)
    setError(null)
    api<Page<Appointment>>('/api/appointments', { query: { from, to, doctorId, size: 100 } })
      .then((r) => { if (active) { setItems(r.content); setTotal(r.totalElements) } })
      .catch((e) => { if (active) setError(errMsg(e)) })
      .finally(() => { if (active) setLoading(false) })
    return () => { active = false }
  }, [from, to, doctorId, reloadKey])

  const counts = new Map<string, number>()
  items
    .filter((a) => a.status !== 'CANCELLED')
    .forEach((a) => counts.set(a.date, (counts.get(a.date) ?? 0) + 1))

  const lead = (new Date(ym.y, ym.m, 1).getDay() + 6) % 7
  const title = new Date(ym.y, ym.m, 1).toLocaleDateString(undefined, { month: 'long', year: 'numeric' })

  function move(delta: number) {
    setYm((cur) => {
      const d = new Date(cur.y, cur.m + delta, 1)
      return { y: d.getFullYear(), m: d.getMonth() }
    })
  }

  return (
    <div>
      <div className="mb-3 flex items-center justify-between">
        <button className={btnSmall} onClick={() => move(-1)} aria-label="Previous month">‹ Previous</button>
        <h2 className="text-lg font-medium">{title}</h2>
        <button className={btnSmall} onClick={() => move(1)} aria-label="Next month">Next ›</button>
      </div>
      <ErrorBox message={error} />
      {loading && <Spinner />}
      <div className="grid grid-cols-7 gap-1 text-center text-xs text-slate-500">
        {['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun'].map((d) => <div key={d}>{d}</div>)}
      </div>
      <div className="mt-1 grid grid-cols-7 gap-1">
        {Array.from({ length: lead }, (_, i) => <div key={`blank-${i}`} />)}
        {Array.from({ length: days }, (_, i) => i + 1).map((day) => {
          const ds = `${ym.y}-${pad(ym.m + 1)}-${pad(day)}`
          const n = counts.get(ds) ?? 0
          return (
            <button
              key={ds}
              onClick={() => onPickDay(ds)}
              className={`min-h-16 rounded-lg border bg-white p-1 text-left hover:bg-teal-50 ${ds === today ? 'ring-2 ring-teal-600' : ''}`}
            >
              <div className="text-xs text-slate-500">{day}</div>
              {n > 0 && <div className="mt-1 rounded bg-teal-100 px-1 text-xs font-medium text-teal-900">{n} appt{n > 1 ? 's' : ''}</div>}
            </button>
          )
        })}
      </div>
      {total > 100 && <p className="mt-2 text-xs text-slate-500">Showing the first 100 appointments of this month.</p>}
    </div>
  )
}

export default function Appointments() {
  const { user } = useAuth()
  const role = user?.role
  const isStaff = role === 'ADMIN' || role === 'RECEPTIONIST'
  const isNurse = role === 'NURSE'
  const today = todayStr()

  const [view, setView] = useState<'list' | 'calendar'>('list')
  const [date, setDate] = useState(today)
  const [doctorId, setDoctorId] = useState('')
  const [status, setStatus] = useState('')
  const [doctors, setDoctors] = useState<Doctor[]>([])
  const [data, setData] = useState<Page<Appointment> | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [reload, setReload] = useState(0)
  const [booking, setBooking] = useState<{ appointment: Appointment | null } | null>(null)
  const [reasonFor, setReasonFor] = useState<{ appointment: Appointment; status: AppointmentStatus; title: string } | null>(null)
  const [busyId, setBusyId] = useState<number | null>(null)
  const [actionError, setActionError] = useState<string | null>(null)

  useEffect(() => {
    api<Doctor[]>('/api/doctors', { query: { activeOnly: true } })
      .then(setDoctors)
      .catch(() => setDoctors([]))
  }, [])

  useEffect(() => {
    if (view !== 'list') return
    let active = true
    setLoading(true)
    setError(null)
    api<Page<Appointment>>('/api/appointments', { query: { date, doctorId, status, size: 100 } })
      .then((r) => { if (active) setData(r) })
      .catch((e) => { if (active) setError(errMsg(e)) })
      .finally(() => { if (active) setLoading(false) })
    return () => { active = false }
  }, [view, date, doctorId, status, reload])

  async function run(a: Appointment, act: Act) {
    setActionError(null)
    if (act.reschedule) {
      setBooking({ appointment: a })
      return
    }
    if (!act.status) return
    if (act.needsReason) {
      setReasonFor({ appointment: a, status: act.status, title: act.label })
      return
    }
    setBusyId(a.id)
    try {
      await api(`/api/appointments/${a.id}/status`, { method: 'PATCH', body: { status: act.status } })
      setReload((n) => n + 1)
    } catch (e) {
      setActionError(errMsg(e))
    } finally {
      setBusyId(null)
    }
  }

  function afterChange() {
    setBooking(null)
    setReasonFor(null)
    setReload((n) => n + 1)
  }

  return (
    <div>
      <div className="mb-4 flex flex-wrap items-center justify-between gap-3">
        <h1 className="text-2xl font-semibold">Appointments</h1>
        <div className="flex gap-2">
          {!isNurse && (
            <div className="flex overflow-hidden rounded-md border text-sm">
              <button className={`px-3 py-2 ${view === 'list' ? 'bg-teal-700 text-white' : 'hover:bg-slate-100'}`} onClick={() => setView('list')}>List</button>
              <button className={`px-3 py-2 ${view === 'calendar' ? 'bg-teal-700 text-white' : 'hover:bg-slate-100'}`} onClick={() => setView('calendar')}>Calendar</button>
            </div>
          )}
          {isStaff && <button className={btnPrimary} onClick={() => setBooking({ appointment: null })}>Book appointment</button>}
        </div>
      </div>

      <div className="mb-3 flex flex-wrap items-end gap-3">
        {view === 'list' && !isNurse && (
          <div className="flex items-end gap-1">
            <button className={btnGhost} aria-label="Previous day" onClick={() => setDate(shiftDate(date, -1))}>‹</button>
            <Field label="Date">
              <input type="date" className={inputCls} value={date} onChange={(e) => e.target.value && setDate(e.target.value)} />
            </Field>
            <button className={btnGhost} aria-label="Next day" onClick={() => setDate(shiftDate(date, 1))}>›</button>
            <button className={btnGhost} onClick={() => setDate(today)}>Today</button>
          </div>
        )}
        {isStaff && (
          <Field label="Doctor">
            <select className={inputCls} value={doctorId} onChange={(e) => setDoctorId(e.target.value)}>
              <option value="">All doctors</option>
              {doctors.map((d) => <option key={d.id} value={d.id}>{d.fullName}</option>)}
            </select>
          </Field>
        )}
        {view === 'list' && (
          <Field label="Status">
            <select className={inputCls} value={status} onChange={(e) => setStatus(e.target.value)}>
              <option value="">All statuses</option>
              {(Object.keys(STATUS_LABEL) as AppointmentStatus[]).map((s) => (
                <option key={s} value={s}>{STATUS_LABEL[s]}</option>
              ))}
            </select>
          </Field>
        )}
      </div>

      <ErrorBox message={actionError} />

      {view === 'calendar' ? (
        <CalendarView
          doctorId={doctorId}
          reloadKey={reload}
          onPickDay={(d) => { setDate(d); setView('list') }}
        />
      ) : (
        <>
          <ErrorBox message={error} />
          {isNurse && <p className="mb-2 text-sm text-slate-500">Showing today's appointments.</p>}
          {loading && !data ? (
            <Spinner />
          ) : (
            <div className="overflow-x-auto rounded-xl border bg-white">
              <table className="w-full text-left text-sm">
                <thead className="bg-slate-50 text-slate-500">
                  <tr>
                    <th className="px-3 py-2">Time</th>
                    <th className="px-3 py-2">Patient</th>
                    <th className="px-3 py-2">Doctor</th>
                    <th className="px-3 py-2">Reason</th>
                    <th className="px-3 py-2">Status</th>
                    <th className="px-3 py-2">Actions</th>
                  </tr>
                </thead>
                <tbody>
                  {data?.content.length === 0 && (
                    <tr><td colSpan={6} className="px-3 py-6 text-center text-slate-500">No appointments found for this selection.</td></tr>
                  )}
                  {data?.content.map((a) => (
                    <tr key={a.id} className="border-t align-top">
                      <td className="px-3 py-2 whitespace-nowrap">
                        {fmtTime(a.startTime)}
                        {a.date !== today && <div className="text-xs text-slate-400">{fmtDate(a.date)}</div>}
                      </td>
                      <td className="px-3 py-2">{a.patientName} <span className="text-slate-400">({a.patientNo})</span></td>
                      <td className="px-3 py-2">{a.doctorName}<div className="text-xs text-slate-400">{a.departmentName}</div></td>
                      <td className="px-3 py-2">
                        {a.reason}
                        {a.cancelReason && <div className="text-xs text-slate-400">Cancelled: {a.cancelReason}</div>}
                      </td>
                      <td className="px-3 py-2"><StatusBadge status={a.status} /></td>
                      <td className="px-3 py-2">
                        <div className="flex flex-wrap gap-1">
                          {actionsFor(a, role, today).map((act) => (
                            <button
                              key={act.label}
                              disabled={busyId === a.id}
                              className={`${btnSmall} ${act.danger ? 'text-red-700' : ''}`}
                              onClick={() => void run(a, act)}
                            >
                              {act.label}
                            </button>
                          ))}                          
                          {(role === 'DOCTOR' || role === 'NURSE') &&
                            (a.status === 'CHECKED_IN' || a.status === 'IN_PROGRESS' || a.status === 'COMPLETED') && (
                              <Link to={`/appointments/${a.id}`} className={btnSmall}>Open visit</Link>
                            )}
                        </div>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
          {data && data.totalElements > 100 && (
            <p className="mt-2 text-xs text-slate-500">Showing the first 100 appointments. Use the filters to narrow down.</p>
          )}
        </>
      )}

      {booking && (
        <BookingModal
          appointment={booking.appointment}
          doctors={doctors}
          onClose={() => setBooking(null)}
          onSaved={afterChange}
        />
      )}
      {reasonFor && (
        <ReasonModal
          appointment={reasonFor.appointment}
          status={reasonFor.status}
          title={reasonFor.title}
          onClose={() => setReasonFor(null)}
          onDone={afterChange}
        />
      )}
    </div>
  )
}