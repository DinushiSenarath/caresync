import { useEffect, useState } from 'react'
import type { FormEvent } from 'react'
import { api } from '../api'
import { btnGhost, btnPrimary, btnSmall, detailMsg, ErrorBox, Field, inputCls, Modal, Spinner, Tabs } from '../ui'

interface Dept {
  id: number
  name: string
  description: string | null
  active: boolean
}

interface DoctorRow {
  id: number
  userId: number
  fullName: string
  departmentId: number
  departmentName: string
  specialty: string | null
  workDays: string
  startTime: string
  endTime: string
  slotMinutes: number
  active: boolean
}

interface AppUser {
  id: number
  username: string
  fullName: string
  role: string
  active: boolean
}

const DAYS = ['MON', 'TUE', 'WED', 'THU', 'FRI', 'SAT', 'SUN']
const nice = (s: string) => s.charAt(0) + s.slice(1).toLowerCase()

/* ---------------- departments ---------------- */

function DeptForm({ dept, onClose, onSaved }: { dept: Dept | null; onClose: () => void; onSaved: () => void }) {
  const [name, setName] = useState(dept?.name ?? '')
  const [description, setDescription] = useState(dept?.description ?? '')
  const [active, setActive] = useState(dept?.active ?? true)
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  async function submit(e: FormEvent) {
    e.preventDefault()
    setError(null)
    setBusy(true)
    try {
      const body = { name: name.trim(), description: description.trim() || null, active }
      if (dept) await api(`/api/departments/${dept.id}`, { method: 'PUT', body })
      else await api('/api/departments', { method: 'POST', body })
      onSaved()
    } catch (err) {
      setError(detailMsg(err))
    } finally {
      setBusy(false)
    }
  }

  return (
    <Modal title={dept ? 'Edit department' : 'Add department'} onClose={onClose}>
      <form onSubmit={submit}>
        <ErrorBox message={error} />
        <div className="grid gap-3">
          <Field label="Name"><input className={inputCls} value={name} onChange={(e) => setName(e.target.value)} required maxLength={100} /></Field>
          <Field label="Description"><input className={inputCls} value={description} onChange={(e) => setDescription(e.target.value)} maxLength={255} /></Field>
          {dept && (
            <label className="flex items-center gap-2 text-sm">
              <input type="checkbox" checked={active} onChange={(e) => setActive(e.target.checked)} />
              Active (an inactive department cannot take new doctors or staff)
            </label>
          )}
        </div>
        <div className="mt-5 flex justify-end gap-2">
          <button type="button" className={btnGhost} onClick={onClose}>Cancel</button>
          <button type="submit" className={btnPrimary} disabled={busy}>{busy ? 'Saving...' : 'Save'}</button>
        </div>
      </form>
    </Modal>
  )
}

function DepartmentsTab() {
  const [list, setList] = useState<Dept[] | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [reload, setReload] = useState(0)
  const [form, setForm] = useState<{ dept: Dept | null } | null>(null)

  useEffect(() => {
    let active = true
    api<Dept[]>('/api/departments')
      .then((r) => { if (active) { setList(r); setError(null) } })
      .catch((e) => { if (active) setError(detailMsg(e)) })
    return () => { active = false }
  }, [reload])

  return (
    <div>
      <div className="mb-3 flex justify-end"><button className={btnPrimary} onClick={() => setForm({ dept: null })}>Add department</button></div>
      <ErrorBox message={error} />
      {!list && !error ? (
        <Spinner />
      ) : (
        <div className="overflow-x-auto rounded-xl border bg-white">
          <table className="w-full text-left text-sm">
            <thead className="bg-slate-50 text-slate-500">
              <tr>
                <th className="px-3 py-2">Department</th>
                <th className="px-3 py-2">Description</th>
                <th className="px-3 py-2">Status</th>
                <th className="px-3 py-2"> </th>
              </tr>
            </thead>
            <tbody>
              {list?.length === 0 && <tr><td colSpan={4} className="px-3 py-6 text-center text-slate-500">No departments yet.</td></tr>}
              {list?.map((d) => (
                <tr key={d.id} className={`border-t ${d.active ? '' : 'text-slate-400'}`}>
                  <td className="px-3 py-2 font-medium">{d.name}</td>
                  <td className="px-3 py-2">{d.description}</td>
                  <td className="px-3 py-2">{d.active ? 'Active' : 'Inactive'}</td>
                  <td className="px-3 py-2"><button className={btnSmall} onClick={() => setForm({ dept: d })}>Edit</button></td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
      {form && <DeptForm dept={form.dept} onClose={() => setForm(null)} onSaved={() => { setForm(null); setReload((n) => n + 1) }} />}
    </div>
  )
}

/* ---------------- doctors ---------------- */

function DoctorForm({
  doctor, depts, available, onClose, onSaved,
}: { doctor: DoctorRow | null; depts: Dept[]; available: AppUser[]; onClose: () => void; onSaved: () => void }) {
  const [userId, setUserId] = useState('')
  const [departmentId, setDepartmentId] = useState(doctor ? String(doctor.departmentId) : '')
  const [specialty, setSpecialty] = useState(doctor?.specialty ?? '')
  const [days, setDays] = useState<string[]>(doctor ? doctor.workDays.split(',') : ['MON', 'TUE', 'WED', 'THU', 'FRI'])
  const [start, setStart] = useState(doctor ? doctor.startTime.slice(0, 5) : '09:00')
  const [end, setEnd] = useState(doctor ? doctor.endTime.slice(0, 5) : '17:00')
  const [slot, setSlot] = useState(String(doctor?.slotMinutes ?? 15))
  const [active, setActive] = useState(doctor?.active ?? true)
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  function toggleDay(d: string) {
    setDays((cur) => (cur.includes(d) ? cur.filter((x) => x !== d) : [...cur, d]))
  }

  async function submit(e: FormEvent) {
    e.preventDefault()
    setError(null)
    const workDays = DAYS.filter((d) => days.includes(d)).join(',')
    if (!workDays) { setError('Choose at least one working day.'); return }
    const slotMinutes = Number(slot)
    if (!Number.isInteger(slotMinutes) || slotMinutes < 5 || slotMinutes > 120) { setError('Slot length must be a whole number from 5 to 120 minutes.'); return }
    const common = {
      departmentId: Number(departmentId),
      specialty: specialty.trim() || null,
      workDays,
      startTime: start,
      endTime: end,
      slotMinutes,
    }
    setBusy(true)
    try {
      if (doctor) await api(`/api/doctors/${doctor.id}`, { method: 'PUT', body: { ...common, active } })
      else await api('/api/doctors', { method: 'POST', body: { ...common, userId: Number(userId) } })
      onSaved()
    } catch (err) {
      setError(detailMsg(err))
    } finally {
      setBusy(false)
    }
  }

  return (
    <Modal title={doctor ? `Edit ${doctor.fullName}` : 'Add doctor profile'} onClose={onClose}>
      <form onSubmit={submit}>
        <ErrorBox message={error} />
        <div className="grid gap-3 sm:grid-cols-2">
          {!doctor && (
            <Field label="Doctor's login account" className="sm:col-span-2">
              <select className={inputCls} value={userId} onChange={(e) => setUserId(e.target.value)} required>
                <option value="">Select an account</option>
                {available.map((u) => <option key={u.id} value={u.id}>{u.fullName} ({u.username})</option>)}
              </select>
              {available.length === 0 && (
                <span className="mt-1 block text-xs text-amber-700">
                  Every doctor account already has a profile. Create a staff account with the Doctor role first.
                </span>
              )}
            </Field>
          )}
          <Field label="Department">
            <select className={inputCls} value={departmentId} onChange={(e) => setDepartmentId(e.target.value)} required>
              <option value="">Select a department</option>
              {depts.map((d) => <option key={d.id} value={d.id}>{d.name}</option>)}
            </select>
          </Field>
          <Field label="Specialty"><input className={inputCls} value={specialty} onChange={(e) => setSpecialty(e.target.value)} maxLength={100} /></Field>
          <div className="sm:col-span-2">
            <div className="mb-1 text-sm font-medium">Working days</div>
            <div className="flex flex-wrap gap-2">
              {DAYS.map((d) => (
                <label key={d} className={`cursor-pointer rounded border px-3 py-1 text-sm ${days.includes(d) ? 'border-teal-700 bg-teal-700 text-white' : 'hover:bg-slate-50'}`}>
                  <input type="checkbox" className="sr-only" checked={days.includes(d)} onChange={() => toggleDay(d)} />
                  {nice(d)}
                </label>
              ))}
            </div>
          </div>
          <Field label="Starts"><input type="time" className={inputCls} value={start} onChange={(e) => setStart(e.target.value)} required /></Field>
          <Field label="Ends"><input type="time" className={inputCls} value={end} onChange={(e) => setEnd(e.target.value)} required /></Field>
          <Field label="Appointment length (minutes)"><input className={inputCls} inputMode="numeric" value={slot} onChange={(e) => setSlot(e.target.value)} required /></Field>
          {doctor && (
            <label className="flex items-center gap-2 self-end text-sm">
              <input type="checkbox" checked={active} onChange={(e) => setActive(e.target.checked)} />
              On duty (untick to stop new bookings)
            </label>
          )}
        </div>
        <p className="mt-3 text-xs text-slate-500">Appointments are offered in slots of this length between the start and end times, on the chosen days only.</p>
        <div className="mt-5 flex justify-end gap-2">
          <button type="button" className={btnGhost} onClick={onClose}>Cancel</button>
          <button type="submit" className={btnPrimary} disabled={busy || (!doctor && !userId)}>{busy ? 'Saving...' : 'Save'}</button>
        </div>
      </form>
    </Modal>
  )
}

function DoctorsTab() {
  const [doctors, setDoctors] = useState<DoctorRow[] | null>(null)
  const [depts, setDepts] = useState<Dept[]>([])
  const [users, setUsers] = useState<AppUser[]>([])
  const [error, setError] = useState<string | null>(null)
  const [reload, setReload] = useState(0)
  const [form, setForm] = useState<{ doctor: DoctorRow | null } | null>(null)

  useEffect(() => {
    let active = true
    Promise.all([api<DoctorRow[]>('/api/doctors'), api<Dept[]>('/api/departments'), api<AppUser[]>('/api/users')])
      .then(([d, dep, u]) => { if (active) { setDoctors(d); setDepts(dep); setUsers(u); setError(null) } })
      .catch((e) => { if (active) setError(detailMsg(e)) })
    return () => { active = false }
  }, [reload])

  const withProfile = new Set((doctors ?? []).map((d) => d.userId))
  const available = users.filter((u) => u.role === 'DOCTOR' && u.active && !withProfile.has(u.id))
  const formDepts = (doc: DoctorRow | null) => depts.filter((d) => d.active || (doc && d.id === doc.departmentId))

  return (
    <div>
      <div className="mb-3 flex justify-end"><button className={btnPrimary} onClick={() => setForm({ doctor: null })}>Add doctor profile</button></div>
      <ErrorBox message={error} />
      {!doctors && !error ? (
        <Spinner />
      ) : (
        <div className="overflow-x-auto rounded-xl border bg-white">
          <table className="w-full text-left text-sm">
            <thead className="bg-slate-50 text-slate-500">
              <tr>
                <th className="px-3 py-2">Doctor</th>
                <th className="px-3 py-2">Department</th>
                <th className="px-3 py-2">Working days</th>
                <th className="px-3 py-2">Hours</th>
                <th className="px-3 py-2">Slot</th>
                <th className="px-3 py-2">Status</th>
                <th className="px-3 py-2"> </th>
              </tr>
            </thead>
            <tbody>
              {doctors?.length === 0 && <tr><td colSpan={7} className="px-3 py-6 text-center text-slate-500">No doctor profiles yet.</td></tr>}
              {doctors?.map((d) => (
                <tr key={d.id} className={`border-t ${d.active ? '' : 'text-slate-400'}`}>
                  <td className="px-3 py-2 font-medium">{d.fullName}{d.specialty && <div className="text-xs font-normal text-slate-400">{d.specialty}</div>}</td>
                  <td className="px-3 py-2">{d.departmentName}</td>
                  <td className="px-3 py-2">{d.workDays.split(',').map(nice).join(', ')}</td>
                  <td className="px-3 py-2 whitespace-nowrap">{d.startTime.slice(0, 5)} to {d.endTime.slice(0, 5)}</td>
                  <td className="px-3 py-2">{d.slotMinutes} min</td>
                  <td className="px-3 py-2">{d.active ? 'On duty' : 'Off duty'}</td>
                  <td className="px-3 py-2"><button className={btnSmall} onClick={() => setForm({ doctor: d })}>Edit</button></td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
      {form && (
        <DoctorForm
          doctor={form.doctor}
          depts={formDepts(form.doctor)}
          available={available}
          onClose={() => setForm(null)}
          onSaved={() => { setForm(null); setReload((n) => n + 1) }}
        />
      )}
    </div>
  )
}

/* ---------------- the page ---------------- */

type DTab = 'doctors' | 'departments'

export default function DoctorsAdmin() {
  const [tab, setTab] = useState<DTab>('doctors')
  return (
    <div>
      <h1 className="mb-4 text-2xl font-semibold">Departments and doctors</h1>
      <Tabs tabs={[{ key: 'doctors', label: 'Doctors' }, { key: 'departments', label: 'Departments' }]} value={tab} onChange={setTab} />
      {tab === 'doctors' ? <DoctorsTab /> : <DepartmentsTab />}
    </div>
  )
}