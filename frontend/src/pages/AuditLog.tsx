import { useEffect, useState } from 'react'
import { api } from '../api'
import type { Page } from '../types'
import { btnGhost, detailMsg, downloadCsv, ErrorBox, Field, fmtDateTime, inputCls, Pagination, Spinner } from '../ui'

interface Entry {
  id: number
  userId: number | null
  userName: string | null
  action: string
  entityType: string | null
  entityId: number | null
  details: string | null
  createdAt: string
}

interface AppUser {
  id: number
  fullName: string
  username: string
}

const TYPES = [
  'USER', 'PATIENT', 'APPOINTMENT', 'CONSULTATION_NOTE', 'VITAL_SIGN', 'NURSING_NOTE', 'PRESCRIPTION', 'MEDICINE',
  'MEDICINE_BATCH', 'INVOICE', 'DEPARTMENT', 'DOCTOR', 'SERVICE_CHARGE', 'LAB_REQUEST', 'LAB_TEST', 'ADMISSION',
  'WARD', 'BED', 'EMPLOYEE', 'LEAVE', 'REGISTRATION_REQUEST','REPORT',
]

export default function AuditLog() {
  const [action, setAction] = useState('')
  const [entityType, setEntityType] = useState('')
  const [userId, setUserId] = useState('')
  const [entityId, setEntityId] = useState('')
  const [from, setFrom] = useState('')
  const [to, setTo] = useState('')
  const [page, setPage] = useState(0)
  const [data, setData] = useState<Page<Entry> | null>(null)
  const [actions, setActions] = useState<string[]>([])
  const [users, setUsers] = useState<AppUser[]>([])
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    Promise.all([api<string[]>('/api/audit-logs/actions'), api<AppUser[]>('/api/users')])
      .then(([a, u]) => { setActions(a); setUsers(u) })
      .catch(() => { /* filters are optional */ })
  }, [])

  useEffect(() => { setPage(0) }, [action, entityType, userId, entityId, from, to])

  useEffect(() => {
    let active = true
    api<Page<Entry>>('/api/audit-logs', { query: { action, entityType, userId, entityId, from, to, page, size: 25 } })
      .then((r) => { if (active) { setData(r); setError(null) } })
      .catch((e) => { if (active) setError(detailMsg(e)) })
    return () => { active = false }
  }, [action, entityType, userId, entityId, from, to, page])

  function exportCsv() {
    if (!data) return
    downloadCsv(`audit-log-page-${data.page + 1}.csv`, [
      ['When', 'User', 'Action', 'Record type', 'Record id', 'Details'],
      ...data.content.map((e) => [fmtDateTime(e.createdAt), e.userName ?? 'System or visitor', e.action, e.entityType, e.entityId, e.details]),
    ])
  }

  return (
    <div>
      <div className="mb-4 flex flex-wrap items-center justify-between gap-3">
        <h1 className="text-2xl font-semibold">Audit log</h1>
        <button className={btnGhost} disabled={!data || data.content.length === 0} onClick={exportCsv}>Export this page (CSV)</button>
      </div>
      <p className="mb-3 text-sm text-slate-500">A permanent record of sensitive actions. Entries cannot be edited or deleted.</p>
      <div className="mb-3 grid gap-3 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-6">
        <Field label="Action">
          <select className={inputCls} value={action} onChange={(e) => setAction(e.target.value)}>
            <option value="">All actions</option>
            {actions.map((a) => <option key={a} value={a}>{a}</option>)}
          </select>
        </Field>
        <Field label="Record type">
          <select className={inputCls} value={entityType} onChange={(e) => setEntityType(e.target.value)}>
            <option value="">All types</option>
            {TYPES.map((t) => <option key={t} value={t}>{t}</option>)}
          </select>
        </Field>
        <Field label="User">
          <select className={inputCls} value={userId} onChange={(e) => setUserId(e.target.value)}>
            <option value="">Everyone</option>
            {users.map((u) => <option key={u.id} value={u.id}>{u.fullName} ({u.username})</option>)}
          </select>
        </Field>
        <Field label="Record id"><input className={inputCls} inputMode="numeric" value={entityId} onChange={(e) => setEntityId(e.target.value.replace(/\D/g, ''))} /></Field>
        <Field label="From"><input type="date" className={inputCls} value={from} onChange={(e) => setFrom(e.target.value)} /></Field>
        <Field label="To"><input type="date" className={inputCls} value={to} onChange={(e) => setTo(e.target.value)} /></Field>
      </div>
      <ErrorBox message={error} />
      {!data && !error ? (
        <Spinner />
      ) : (
        <div className="overflow-x-auto rounded-xl border bg-white">
          <table className="w-full text-left text-sm">
            <thead className="bg-slate-50 text-slate-500">
              <tr>
                <th className="px-3 py-2">When</th>
                <th className="px-3 py-2">User</th>
                <th className="px-3 py-2">Action</th>
                <th className="px-3 py-2">Record</th>
                <th className="px-3 py-2">Details</th>
              </tr>
            </thead>
            <tbody>
              {data?.content.length === 0 && <tr><td colSpan={5} className="px-3 py-6 text-center text-slate-500">No entries match.</td></tr>}
              {data?.content.map((e) => (
                <tr key={e.id} className="border-t align-top">
                  <td className="px-3 py-2 whitespace-nowrap">{fmtDateTime(e.createdAt)}</td>
                  <td className="px-3 py-2">{e.userName ?? <span className="text-slate-400">System or visitor</span>}</td>
                  <td className="px-3 py-2 font-medium">{e.action}</td>
                  <td className="px-3 py-2 whitespace-nowrap">{e.entityType}{e.entityId != null && ` #${e.entityId}`}</td>
                  <td className="px-3 py-2 break-words">{e.details}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
      {data && <Pagination page={data.page} totalPages={data.totalPages} onPage={setPage} />}
    </div>
  )
}