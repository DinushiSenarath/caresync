import { useEffect, useState } from 'react'
import type { FormEvent } from 'react'
import { api } from '../api'
import type { Page } from '../types'
import type { Attendance, AttStatus, Employee, Leave, SummaryRow, UnlinkedUser } from '../hrTypes'
import { LEAVE_STYLE, LEAVE_TYPES, leaveLabel, nice } from '../hrTypes'
import {
  btnGhost, btnPrimary, btnSmall, detailMsg, downloadCsv, ErrorBox, Field, fmtClock, fmtDate, inputCls,
  Modal, Pagination, Spinner, Tabs, todayStr, useDebounced,
} from '../ui'

interface Dept { id: number; name: string }

const EMP_TYPES = ['PERMANENT', 'CONTRACT', 'PART_TIME', 'INTERN']

/* ---------------- employees ---------------- */

function EmployeeForm({ employee, onClose, onSaved }: { employee: Employee | null; onClose: () => void; onSaved: () => void }) {
  const [depts, setDepts] = useState<Dept[]>([])
  const [unlinked, setUnlinked] = useState<UnlinkedUser[]>([])
  const [f, setF] = useState({
    fullName: employee?.fullName ?? '',
    nic: employee?.nic ?? '',
    phone: employee?.phone ?? '',
    email: employee?.email ?? '',
    address: employee?.address ?? '',
    jobTitle: employee?.jobTitle ?? '',
    departmentId: employee ? String(employee.departmentId) : '',
    employmentType: employee?.employmentType ?? 'PERMANENT',
    hireDate: employee?.hireDate ?? todayStr(),
    userId: employee?.userId ? String(employee.userId) : '',
    emergencyContactName: employee?.emergencyContactName ?? '',
    emergencyContactPhone: employee?.emergencyContactPhone ?? '',
    active: employee?.active ?? true,
  })
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  useEffect(() => {
    let active = true
    Promise.all([api<Dept[]>('/api/departments', { query: { activeOnly: true } }), api<UnlinkedUser[]>('/api/hr/employees/unlinked-users')])
      .then(([d, u]) => { if (active) { setDepts(d); setUnlinked(u) } })
      .catch((e) => { if (active) setError(detailMsg(e)) })
    return () => { active = false }
  }, [])

  const text = (k: 'fullName' | 'nic' | 'phone' | 'email' | 'address' | 'jobTitle' | 'hireDate' | 'emergencyContactName' | 'emergencyContactPhone') =>
    (e: { target: { value: string } }) => setF((p) => ({ ...p, [k]: e.target.value }))

  async function submit(e: FormEvent) {
    e.preventDefault()
    setError(null)
    const body = {
      fullName: f.fullName.trim(),
      nic: f.nic.trim() || null,
      phone: f.phone.trim(),
      email: f.email.trim() || null,
      address: f.address.trim() || null,
      jobTitle: f.jobTitle.trim(),
      departmentId: Number(f.departmentId),
      employmentType: f.employmentType,
      hireDate: f.hireDate,
      userId: f.userId ? Number(f.userId) : null,
      emergencyContactName: f.emergencyContactName.trim() || null,
      emergencyContactPhone: f.emergencyContactPhone.trim() || null,
      active: f.active,
    }
    setBusy(true)
    try {
      if (employee) await api(`/api/hr/employees/${employee.id}`, { method: 'PUT', body })
      else await api('/api/hr/employees', { method: 'POST', body })
      onSaved()
    } catch (err) {
      setError(detailMsg(err))
    } finally {
      setBusy(false)
    }
  }

  const currentLink = employee?.userId ? { id: employee.userId, username: employee.username ?? '', fullName: '', role: employee.userRole ?? '' } : null
  const linkOptions = currentLink ? [currentLink, ...unlinked.filter((u) => u.id !== currentLink.id)] : unlinked

  return (
    <Modal title={employee ? `Edit ${employee.employeeNo}` : 'Register employee'} onClose={onClose} wide>
      <form onSubmit={submit}>
        <ErrorBox message={error} />
        <div className="grid gap-3 sm:grid-cols-2">
          <Field label="Full name" className="sm:col-span-2"><input className={inputCls} value={f.fullName} onChange={text('fullName')} required maxLength={120} /></Field>
          <Field label="Job title"><input className={inputCls} value={f.jobTitle} onChange={text('jobTitle')} required maxLength={100} /></Field>
          <Field label="Department">
            <select className={inputCls} value={f.departmentId} onChange={(e) => setF((p) => ({ ...p, departmentId: e.target.value }))} required>
              <option value="">Select a department</option>
              {depts.map((d) => <option key={d.id} value={d.id}>{d.name}</option>)}
            </select>
          </Field>
          <Field label="Employment type">
            <select className={inputCls} value={f.employmentType} onChange={(e) => setF((p) => ({ ...p, employmentType: e.target.value }))}>
              {EMP_TYPES.map((t) => <option key={t} value={t}>{nice(t)}</option>)}
            </select>
          </Field>
          <Field label="Hire date"><input type="date" max={todayStr()} className={inputCls} value={f.hireDate} onChange={text('hireDate')} required /></Field>
          <Field label="Phone"><input className={inputCls} placeholder="0771234567" value={f.phone} onChange={text('phone')} required /></Field>
          <Field label="NIC (optional)"><input className={inputCls} value={f.nic} onChange={text('nic')} /></Field>
          <Field label="Email (optional)"><input type="email" className={inputCls} value={f.email} onChange={text('email')} maxLength={120} /></Field>
          <Field label="Address"><input className={inputCls} value={f.address} onChange={text('address')} maxLength={255} /></Field>
          <Field label="Emergency contact name"><input className={inputCls} value={f.emergencyContactName} onChange={text('emergencyContactName')} maxLength={120} /></Field>
          <Field label="Emergency contact phone"><input className={inputCls} value={f.emergencyContactPhone} onChange={text('emergencyContactPhone')} /></Field>
          <Field label="Login account (so they can check in and request leave)" className="sm:col-span-2">
            <select className={inputCls} value={f.userId} onChange={(e) => setF((p) => ({ ...p, userId: e.target.value }))}>
              <option value="">No login account</option>
              {linkOptions.map((u) => <option key={u.id} value={u.id}>{u.username}{u.fullName && ` (${u.fullName})`}{u.role && `, ${nice(u.role)}`}</option>)}
            </select>
          </Field>
          {employee && (
            <label className="flex items-center gap-2 text-sm sm:col-span-2">
              <input type="checkbox" checked={f.active} onChange={(e) => setF((p) => ({ ...p, active: e.target.checked }))} />
              Currently employed
            </label>
          )}
        </div>
        {employee && !f.active && employee.userId && (
          <p className="mt-3 rounded bg-amber-50 p-2 text-xs text-amber-900">
            Remember to deactivate their login account too, in Staff accounts, so they can no longer sign in.
          </p>
        )}
        <div className="mt-5 flex justify-end gap-2">
          <button type="button" className={btnGhost} onClick={onClose}>Cancel</button>
          <button type="submit" className={btnPrimary} disabled={busy}>{busy ? 'Saving...' : 'Save'}</button>
        </div>
      </form>
    </Modal>
  )
}

function EmployeesTab() {
  const [q, setQ] = useState('')
  const dq = useDebounced(q)
  const [status, setStatus] = useState('ACTIVE')
  const [dept, setDept] = useState('')
  const [depts, setDepts] = useState<Dept[]>([])
  const [page, setPage] = useState(0)
  const [data, setData] = useState<Page<Employee> | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [reload, setReload] = useState(0)
  const [form, setForm] = useState<{ employee: Employee | null } | null>(null)

  useEffect(() => {
    api<Dept[]>('/api/departments').then(setDepts).catch(() => setDepts([]))
  }, [])

  useEffect(() => { setPage(0) }, [dq, status, dept])

  useEffect(() => {
    let active = true
    api<Page<Employee>>('/api/hr/employees', { query: { q: dq, status, departmentId: dept, page, size: 15 } })
      .then((r) => { if (active) { setData(r); setError(null) } })
      .catch((e) => { if (active) setError(detailMsg(e)) })
    return () => { active = false }
  }, [dq, status, dept, page, reload])

  return (
    <div>
      <div className="mb-3 flex flex-wrap items-end gap-3">
        <input className={`${inputCls} max-w-xs`} placeholder="Search name, number, NIC, phone or job" value={q} onChange={(e) => setQ(e.target.value)} aria-label="Search employees" />
        <Field label="Status">
          <select className={inputCls} value={status} onChange={(e) => setStatus(e.target.value)}>
            <option value="ACTIVE">Current</option>
            <option value="INACTIVE">Former</option>
            <option value="ALL">All</option>
          </select>
        </Field>
        <Field label="Department">
          <select className={inputCls} value={dept} onChange={(e) => setDept(e.target.value)}>
            <option value="">All departments</option>
            {depts.map((d) => <option key={d.id} value={d.id}>{d.name}</option>)}
          </select>
        </Field>
        <button className={`${btnPrimary} ml-auto`} onClick={() => setForm({ employee: null })}>Register employee</button>
      </div>
      <ErrorBox message={error} />
      {!data && !error ? (
        <Spinner />
      ) : (
        <div className="overflow-x-auto rounded-xl border bg-white">
          <table className="w-full text-left text-sm">
            <thead className="bg-slate-50 text-slate-500">
              <tr>
                <th className="px-3 py-2">Number</th>
                <th className="px-3 py-2">Name</th>
                <th className="px-3 py-2">Job title</th>
                <th className="px-3 py-2">Department</th>
                <th className="px-3 py-2">Type</th>
                <th className="px-3 py-2">Login</th>
                <th className="px-3 py-2"> </th>
              </tr>
            </thead>
            <tbody>
              {data?.content.length === 0 && <tr><td colSpan={7} className="px-3 py-6 text-center text-slate-500">No employees found.</td></tr>}
              {data?.content.map((e) => (
                <tr key={e.id} className={`border-t ${e.active ? '' : 'text-slate-400'}`}>
                  <td className="px-3 py-2 font-medium">{e.employeeNo}</td>
                  <td className="px-3 py-2">{e.fullName}<div className="text-xs text-slate-400">{e.phone}</div></td>
                  <td className="px-3 py-2">{e.jobTitle}</td>
                  <td className="px-3 py-2">{e.departmentName}</td>
                  <td className="px-3 py-2">{nice(e.employmentType)}</td>
                  <td className="px-3 py-2">{e.username ?? <span className="text-slate-400">None</span>}</td>
                  <td className="px-3 py-2"><button className={btnSmall} onClick={() => setForm({ employee: e })}>Edit</button></td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
      {data && <Pagination page={data.page} totalPages={data.totalPages} onPage={setPage} />}
      {form && <EmployeeForm employee={form.employee} onClose={() => setForm(null)} onSaved={() => { setForm(null); setReload((n) => n + 1) }} />}
    </div>
  )
}

/* ---------------- attendance ---------------- */

function AttendanceForm({
  record, employees, onClose, onSaved,
}: { record: Attendance | null; employees: Employee[]; onClose: () => void; onSaved: () => void }) {
  const [employeeId, setEmployeeId] = useState(record ? String(record.employeeId) : '')
  const [workDate, setWorkDate] = useState(record?.workDate ?? todayStr())
  const [status, setStatus] = useState<AttStatus>(record?.status ?? 'PRESENT')
  const [inTime, setInTime] = useState(record?.checkIn ? fmtClock(record.checkIn) : '08:00')
  const [outTime, setOutTime] = useState(record?.checkOut ? fmtClock(record.checkOut) : '')
  const [note, setNote] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  async function submit(e: FormEvent) {
    e.preventDefault()
    setError(null)
    const absent = status === 'ABSENT'
    const body = {
      employeeId: record ? record.employeeId : Number(employeeId),
      workDate,
      status,
      checkInTime: absent ? null : inTime || null,
      checkOutTime: absent ? null : outTime || null,
      note: note.trim(),
    }
    setBusy(true)
    try {
      if (record) await api(`/api/hr/attendance/${record.id}`, { method: 'PUT', body })
      else await api('/api/hr/attendance', { method: 'POST', body })
      onSaved()
    } catch (err) {
      setError(detailMsg(err))
    } finally {
      setBusy(false)
    }
  }

  return (
    <Modal title={record ? `Correct record: ${record.employeeName}` : 'Add attendance record'} onClose={onClose}>
      <form onSubmit={submit}>
        <ErrorBox message={error} />
        <div className="grid gap-3 sm:grid-cols-2">
          {!record && (
            <Field label="Employee" className="sm:col-span-2">
              <select className={inputCls} value={employeeId} onChange={(e) => setEmployeeId(e.target.value)} required>
                <option value="">Select an employee</option>
                {employees.map((x) => <option key={x.id} value={x.id}>{x.employeeNo} {x.fullName}</option>)}
              </select>
            </Field>
          )}
          <Field label="Date"><input type="date" max={todayStr()} className={inputCls} value={workDate} onChange={(e) => setWorkDate(e.target.value)} disabled={!!record} required /></Field>
          <Field label="Status">
            <select className={inputCls} value={status} onChange={(e) => setStatus(e.target.value as AttStatus)}>
              <option value="PRESENT">Present</option>
              <option value="HALF_DAY">Half day</option>
              <option value="ABSENT">Absent</option>
            </select>
          </Field>
          {status !== 'ABSENT' && (
            <>
              <Field label="Check-in"><input type="time" className={inputCls} value={inTime} onChange={(e) => setInTime(e.target.value)} required /></Field>
              <Field label="Check-out (optional)"><input type="time" className={inputCls} value={outTime} onChange={(e) => setOutTime(e.target.value)} /></Field>
            </>
          )}
          <Field label="Reason for this entry (required)" className="sm:col-span-2">
            <input className={inputCls} value={note} onChange={(e) => setNote(e.target.value)} maxLength={255} required placeholder="e.g. Forgot to check out" />
          </Field>
        </div>
        <div className="mt-5 flex justify-end gap-2">
          <button type="button" className={btnGhost} onClick={onClose}>Cancel</button>
          <button type="submit" className={btnPrimary} disabled={busy || !note.trim() || (!record && !employeeId)}>{busy ? 'Saving...' : 'Save'}</button>
        </div>
      </form>
    </Modal>
  )
}

function RecordsView({ employees }: { employees: Employee[] }) {
  const [empId, setEmpId] = useState('')
  const [from, setFrom] = useState(todayStr().slice(0, 8) + '01')
  const [to, setTo] = useState(todayStr())
  const [page, setPage] = useState(0)
  const [data, setData] = useState<Page<Attendance> | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [reload, setReload] = useState(0)
  const [form, setForm] = useState<{ record: Attendance | null } | null>(null)

  useEffect(() => { setPage(0) }, [empId, from, to])

  useEffect(() => {
    let active = true
    api<Page<Attendance>>('/api/hr/attendance', { query: { employeeId: empId, from, to, page, size: 20 } })
      .then((r) => { if (active) { setData(r); setError(null) } })
      .catch((e) => { if (active) setError(detailMsg(e)) })
    return () => { active = false }
  }, [empId, from, to, page, reload])

  return (
    <div>
      <div className="mb-3 flex flex-wrap items-end gap-3">
        <Field label="Employee">
          <select className={inputCls} value={empId} onChange={(e) => setEmpId(e.target.value)}>
            <option value="">Everyone</option>
            {employees.map((x) => <option key={x.id} value={x.id}>{x.fullName}</option>)}
          </select>
        </Field>
        <Field label="From"><input type="date" className={inputCls} value={from} onChange={(e) => e.target.value && setFrom(e.target.value)} /></Field>
        <Field label="To"><input type="date" className={inputCls} value={to} onChange={(e) => e.target.value && setTo(e.target.value)} /></Field>
        <button className={`${btnPrimary} ml-auto`} onClick={() => setForm({ record: null })}>Add record</button>
      </div>
      <ErrorBox message={error} />
      {!data && !error ? (
        <Spinner />
      ) : (
        <div className="overflow-x-auto rounded-xl border bg-white">
          <table className="w-full text-left text-sm">
            <thead className="bg-slate-50 text-slate-500">
              <tr>
                <th className="px-3 py-2">Date</th>
                <th className="px-3 py-2">Employee</th>
                <th className="px-3 py-2">Status</th>
                <th className="px-3 py-2">In</th>
                <th className="px-3 py-2">Out</th>
                <th className="px-3 py-2">Hours</th>
                <th className="px-3 py-2">Source</th>
                <th className="px-3 py-2"> </th>
              </tr>
            </thead>
            <tbody>
              {data?.content.length === 0 && <tr><td colSpan={8} className="px-3 py-6 text-center text-slate-500">No records in this range.</td></tr>}
              {data?.content.map((r) => (
                <tr key={r.id} className="border-t">
                  <td className="px-3 py-2 whitespace-nowrap">{fmtDate(r.workDate)}</td>
                  <td className="px-3 py-2">{r.employeeName} <span className="text-slate-400">({r.employeeNo})</span></td>
                  <td className="px-3 py-2">{nice(r.status)}</td>
                  <td className="px-3 py-2">{fmtClock(r.checkIn)}</td>
                  <td className="px-3 py-2">{r.checkIn && !r.checkOut && r.status !== 'ABSENT' ? <span className="text-amber-700">Not checked out</span> : fmtClock(r.checkOut)}</td>
                  <td className="px-3 py-2">{r.hours ?? ''}</td>
                  <td className="px-3 py-2">{r.source === 'SELF' ? 'Self' : 'Manual'}{r.note && <div className="text-xs text-slate-400">{r.note}</div>}</td>
                  <td className="px-3 py-2"><button className={btnSmall} onClick={() => setForm({ record: r })}>Correct</button></td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
      {data && <Pagination page={data.page} totalPages={data.totalPages} onPage={setPage} />}
      {form && <AttendanceForm record={form.record} employees={employees} onClose={() => setForm(null)} onSaved={() => { setForm(null); setReload((n) => n + 1) }} />}
    </div>
  )
}

function SummaryView() {
  const [month, setMonth] = useState(todayStr().slice(0, 7))
  const [rows, setRows] = useState<SummaryRow[] | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    let active = true
    api<SummaryRow[]>('/api/hr/attendance/summary', { query: { month } })
      .then((r) => { if (active) { setRows(r); setError(null) } })
      .catch((e) => { if (active) setError(detailMsg(e)) })
    return () => { active = false }
  }, [month])

  function exportCsv() {
    if (!rows) return
    downloadCsv(`attendance-summary-${month}.csv`, [
      ['Employee no', 'Name', 'Department', 'Days present', 'Days absent', 'Leave days', 'Days without record', 'Hours worked'],
      ...rows.map((r) => [r.employeeNo, r.fullName, r.departmentName, r.presentDays, r.absentDays, r.leaveDays, r.daysWithoutRecord, r.hoursWorked]),
    ])
  }

  return (
    <div>
      <div className="mb-3 flex flex-wrap items-end gap-3">
        <Field label="Month"><input type="month" className={inputCls} value={month} onChange={(e) => e.target.value && setMonth(e.target.value)} /></Field>
        <button className={btnGhost} disabled={!rows || rows.length === 0} onClick={exportCsv}>Export CSV</button>
      </div>
      <p className="mb-3 text-xs text-slate-500">
        Working days are Monday to Friday. "Days without record" counts weekdays up to today that have neither an attendance record nor approved leave. Staff on weekend shifts are not modelled.
      </p>
      <ErrorBox message={error} />
      {!rows && !error ? (
        <Spinner />
      ) : (
        <div className="overflow-x-auto rounded-xl border bg-white">
          <table className="w-full text-left text-sm">
            <thead className="bg-slate-50 text-slate-500">
              <tr>
                <th className="px-3 py-2">Employee</th>
                <th className="px-3 py-2">Department</th>
                <th className="px-3 py-2 text-right">Present</th>
                <th className="px-3 py-2 text-right">Absent</th>
                <th className="px-3 py-2 text-right">Leave</th>
                <th className="px-3 py-2 text-right">No record</th>
                <th className="px-3 py-2 text-right">Hours</th>
              </tr>
            </thead>
            <tbody>
              {rows?.length === 0 && <tr><td colSpan={7} className="px-3 py-6 text-center text-slate-500">No employees.</td></tr>}
              {rows?.map((r) => (
                <tr key={r.employeeId} className="border-t">
                  <td className="px-3 py-2">{r.fullName} <span className="text-slate-400">({r.employeeNo})</span></td>
                  <td className="px-3 py-2">{r.departmentName}</td>
                  <td className="px-3 py-2 text-right">{r.presentDays}</td>
                  <td className="px-3 py-2 text-right">{r.absentDays}</td>
                  <td className="px-3 py-2 text-right">{r.leaveDays}</td>
                  <td className="px-3 py-2 text-right">{r.daysWithoutRecord}</td>
                  <td className="px-3 py-2 text-right">{r.hoursWorked}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  )
}

function AttendanceTab() {
  const [view, setView] = useState<'records' | 'summary'>('records')
  const [employees, setEmployees] = useState<Employee[]>([])

  useEffect(() => {
    api<Page<Employee>>('/api/hr/employees', { query: { status: 'ALL', size: 100 } })
      .then((r) => setEmployees(r.content))
      .catch(() => setEmployees([]))
  }, [])

  return (
    <div>
      <div className="mb-4 flex overflow-hidden rounded-md border text-sm w-fit">
        <button className={`px-3 py-2 ${view === 'records' ? 'bg-teal-700 text-white' : 'hover:bg-slate-100'}`} onClick={() => setView('records')}>Daily records</button>
        <button className={`px-3 py-2 ${view === 'summary' ? 'bg-teal-700 text-white' : 'hover:bg-slate-100'}`} onClick={() => setView('summary')}>Monthly summary</button>
      </div>
      {view === 'records' ? <RecordsView employees={employees.filter((e) => e.active)} /> : <SummaryView />}
    </div>
  )
}

/* ---------------- leave ---------------- */

function DecisionModal({ leave, approve, onClose, onDone }: { leave: Leave; approve: boolean; onClose: () => void; onDone: () => void }) {
  const [note, setNote] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  async function submit(e: FormEvent) {
    e.preventDefault()
    setBusy(true)
    setError(null)
    try {
      await api(`/api/hr/leave/${leave.id}/decision`, { method: 'POST', body: { approve, note: note.trim() || null } })
      onDone()
    } catch (err) {
      setError(detailMsg(err))
    } finally {
      setBusy(false)
    }
  }

  return (
    <Modal title={approve ? 'Approve leave' : 'Reject leave'} onClose={onClose}>
      <form onSubmit={submit}>
        <p className="mb-3 text-sm text-slate-600">
          {leave.employeeName}: {leaveLabel(leave.leaveType)} leave, {fmtDate(leave.fromDate)} to {fmtDate(leave.toDate)} ({leave.days} working day{leave.days === 1 ? '' : 's'}).
          {leave.reason && ` Reason: ${leave.reason}`}
        </p>
        <ErrorBox message={error} />
        <Field label={approve ? 'Note (optional)' : 'Reason for rejecting (required)'}>
          <input className={inputCls} value={note} onChange={(e) => setNote(e.target.value)} maxLength={255} required={!approve} />
        </Field>
        <div className="mt-5 flex justify-end gap-2">
          <button type="button" className={btnGhost} onClick={onClose}>Back</button>
          <button type="submit" className={btnPrimary} disabled={busy || (!approve && !note.trim())}>{busy ? 'Saving...' : approve ? 'Approve' : 'Reject'}</button>
        </div>
      </form>
    </Modal>
  )
}

function RecordLeaveModal({ employees, onClose, onSaved }: { employees: Employee[]; onClose: () => void; onSaved: () => void }) {
  const [employeeId, setEmployeeId] = useState('')
  const [type, setType] = useState('ANNUAL')
  const [from, setFrom] = useState(todayStr())
  const [to, setTo] = useState(todayStr())
  const [reason, setReason] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  async function submit(e: FormEvent) {
    e.preventDefault()
    setError(null)
    setBusy(true)
    try {
      await api('/api/hr/leave/record', {
        method: 'POST',
        body: { employeeId: Number(employeeId), leaveType: type, fromDate: from, toDate: to, reason: reason.trim() },
      })
      onSaved()
    } catch (err) {
      setError(detailMsg(err))
    } finally {
      setBusy(false)
    }
  }

  return (
    <Modal title="Record leave for an employee" onClose={onClose}>
      <form onSubmit={submit}>
        <ErrorBox message={error} />
        <div className="grid gap-3 sm:grid-cols-2">
          <Field label="Employee" className="sm:col-span-2">
            <select className={inputCls} value={employeeId} onChange={(e) => setEmployeeId(e.target.value)} required>
              <option value="">Select an employee</option>
              {employees.map((x) => <option key={x.id} value={x.id}>{x.employeeNo} {x.fullName}</option>)}
            </select>
          </Field>
          <Field label="Leave type">
            <select className={inputCls} value={type} onChange={(e) => setType(e.target.value)}>
              {LEAVE_TYPES.map((t) => <option key={t.value} value={t.value}>{t.label}</option>)}
            </select>
          </Field>
          <div />
          <Field label="From"><input type="date" className={inputCls} value={from} onChange={(e) => setFrom(e.target.value)} required /></Field>
          <Field label="To"><input type="date" className={inputCls} value={to} min={from} onChange={(e) => setTo(e.target.value)} required /></Field>
          <Field label="Reason (required)" className="sm:col-span-2"><input className={inputCls} value={reason} onChange={(e) => setReason(e.target.value)} maxLength={255} required /></Field>
        </div>
        <p className="mt-3 text-xs text-slate-500">Recorded leave is approved straight away. Saturdays and Sundays are not counted.</p>
        <div className="mt-5 flex justify-end gap-2">
          <button type="button" className={btnGhost} onClick={onClose}>Cancel</button>
          <button type="submit" className={btnPrimary} disabled={busy || !employeeId || !reason.trim()}>{busy ? 'Saving...' : 'Record leave'}</button>
        </div>
      </form>
    </Modal>
  )
}

function LeaveTab() {
  const [status, setStatus] = useState('PENDING')
  const [page, setPage] = useState(0)
  const [data, setData] = useState<Page<Leave> | null>(null)
  const [employees, setEmployees] = useState<Employee[]>([])
  const [error, setError] = useState<string | null>(null)
  const [reload, setReload] = useState(0)
  const [deciding, setDeciding] = useState<{ leave: Leave; approve: boolean } | null>(null)
  const [recording, setRecording] = useState(false)
  const today = todayStr()

  useEffect(() => {
    api<Page<Employee>>('/api/hr/employees', { query: { status: 'ACTIVE', size: 100 } })
      .then((r) => setEmployees(r.content))
      .catch(() => setEmployees([]))
  }, [])

  useEffect(() => { setPage(0) }, [status])

  useEffect(() => {
    let active = true
    api<Page<Leave>>('/api/hr/leave', { query: { status, page, size: 15 } })
      .then((r) => { if (active) { setData(r); setError(null) } })
      .catch((e) => { if (active) setError(detailMsg(e)) })
    return () => { active = false }
  }, [status, page, reload])

  async function cancel(l: Leave) {
    const reason = window.prompt('Reason for cancelling this leave (optional):')
    if (reason === null) return
    setError(null)
    try {
      await api(`/api/hr/leave/${l.id}/cancel`, { method: 'POST', body: { reason: reason.trim() || null } })
      setReload((n) => n + 1)
    } catch (e) {
      setError(detailMsg(e))
    }
  }

  return (
    <div>
      <div className="mb-3 flex flex-wrap items-end gap-3">
        <Field label="Show">
          <select className={inputCls} value={status} onChange={(e) => setStatus(e.target.value)}>
            <option value="PENDING">Waiting for a decision</option>
            <option value="APPROVED">Approved</option>
            <option value="REJECTED">Rejected</option>
            <option value="CANCELLED">Cancelled</option>
            <option value="ALL">All</option>
          </select>
        </Field>
        <button className={`${btnPrimary} ml-auto`} onClick={() => setRecording(true)}>Record leave</button>
      </div>
      <ErrorBox message={error} />
      {!data && !error ? (
        <Spinner />
      ) : (
        <div className="overflow-x-auto rounded-xl border bg-white">
          <table className="w-full text-left text-sm">
            <thead className="bg-slate-50 text-slate-500">
              <tr>
                <th className="px-3 py-2">Employee</th>
                <th className="px-3 py-2">Type</th>
                <th className="px-3 py-2">Dates</th>
                <th className="px-3 py-2">Days</th>
                <th className="px-3 py-2">Reason</th>
                <th className="px-3 py-2">Status</th>
                <th className="px-3 py-2"> </th>
              </tr>
            </thead>
            <tbody>
              {data?.content.length === 0 && <tr><td colSpan={7} className="px-3 py-6 text-center text-slate-500">No leave requests found.</td></tr>}
              {data?.content.map((l) => (
                <tr key={l.id} className="border-t align-top">
                  <td className="px-3 py-2">{l.employeeName} <span className="text-slate-400">({l.employeeNo})</span></td>
                  <td className="px-3 py-2">{leaveLabel(l.leaveType)}</td>
                  <td className="px-3 py-2 whitespace-nowrap">{fmtDate(l.fromDate)} to {fmtDate(l.toDate)}</td>
                  <td className="px-3 py-2">{l.days}</td>
                  <td className="px-3 py-2">{l.reason}{l.decisionNote && <div className="text-xs text-slate-400">{l.decisionNote}{l.decidedByName && ` (${l.decidedByName})`}</div>}</td>
                  <td className="px-3 py-2"><span className={`rounded-full px-2 py-0.5 text-xs font-medium ${LEAVE_STYLE[l.status]}`}>{nice(l.status)}</span></td>
                  <td className="px-3 py-2">
                    <div className="flex gap-1">
                      {l.status === 'PENDING' && (
                        <>
                          <button className={btnSmall} onClick={() => setDeciding({ leave: l, approve: true })}>Approve</button>
                          <button className={`${btnSmall} text-red-700`} onClick={() => setDeciding({ leave: l, approve: false })}>Reject</button>
                        </>
                      )}
                      {l.status === 'APPROVED' && l.fromDate > today && <button className={btnSmall} onClick={() => void cancel(l)}>Cancel</button>}
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
      {data && <Pagination page={data.page} totalPages={data.totalPages} onPage={setPage} />}
      {deciding && <DecisionModal leave={deciding.leave} approve={deciding.approve} onClose={() => setDeciding(null)} onDone={() => { setDeciding(null); setReload((n) => n + 1) }} />}
      {recording && <RecordLeaveModal employees={employees} onClose={() => setRecording(false)} onSaved={() => { setRecording(false); setReload((n) => n + 1) }} />}
    </div>
  )
}

/* ---------------- the page ---------------- */

type HTab = 'employees' | 'attendance' | 'leave'

export default function Hr() {
  const [tab, setTab] = useState<HTab>('employees')
  return (
    <div>
      <h1 className="mb-4 text-2xl font-semibold">Staff management</h1>
      <Tabs
        tabs={[
          { key: 'employees', label: 'Employees' },
          { key: 'attendance', label: 'Attendance' },
          { key: 'leave', label: 'Leave' },
        ]}
        value={tab}
        onChange={setTab}
      />
      {tab === 'employees' && <EmployeesTab />}
      {tab === 'attendance' && <AttendanceTab />}
      {tab === 'leave' && <LeaveTab />}
    </div>
  )
}