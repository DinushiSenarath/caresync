import { useEffect, useState } from 'react'
import { api, ApiError } from '../api'
import PatientPicker from '../components/PatientPicker'
import type { Page, Patient } from '../types'
import {
  ageOf, btnGhost, btnPrimary, btnSmall, detailMsg, ErrorBox, Field, fmtDate, fmtDateTime, inputCls,
  Modal, Pagination, Spinner,
} from '../ui'

interface Reg {
  id: number
  fullName: string
  dateOfBirth: string
  gender: string
  nic: string | null
  phone: string
  address: string | null
  status: 'PENDING' | 'APPROVED' | 'REJECTED'
  patientId: number | null
  patientNo: string | null
  decisionNote: string | null
  decidedByName: string | null
  createdAt: string
  decidedAt: string | null
}

interface Dup {
  id: number
  patientNo: string
  fullName: string
  dateOfBirth: string
  phone: string
}

interface Approval {
  patientNo: string
  linkedToExistingPatient: boolean
  activationCode: { patientNo: string; code: string; expiresAt: string } | null
  message: string
}

const STATUS_STYLE: Record<string, string> = {
  PENDING: 'bg-violet-100 text-violet-800',
  APPROVED: 'bg-green-100 text-green-800',
  REJECTED: 'bg-slate-200 text-slate-600',
}
const nice = (s: string) => s.charAt(0) + s.slice(1).toLowerCase()

function dupesFrom(data: unknown): Dup[] | null {
  const d = data as { possibleDuplicates?: Dup[] } | null
  return d && Array.isArray(d.possibleDuplicates) ? d.possibleDuplicates : null
}

function ReviewModal({ req, onClose, onChanged }: { req: Reg; onClose: () => void; onChanged: () => void }) {
  const pending = req.status === 'PENDING'
  const [mode, setMode] = useState<'new' | 'link'>('new')
  const [linked, setLinked] = useState<Patient | null>(null)
  const [note, setNote] = useState('')
  const [issueCode, setIssueCode] = useState(true)
  const [dupes, setDupes] = useState<Dup[] | null>(null)
  const [result, setResult] = useState<Approval | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  async function approve(existingPatientId: number | null, confirmDuplicate: boolean) {
    setBusy(true)
    setError(null)
    try {
      const r = await api<Approval>(`/api/registration-requests/${req.id}/approve`, {
        method: 'POST',
        query: { confirmDuplicate: confirmDuplicate ? true : undefined },
        body: { existingPatientId, issueActivationCode: issueCode, note: note.trim() },
      })
      setDupes(null)
      setResult(r)
      onChanged()
    } catch (e) {
      const list = e instanceof ApiError && e.status === 409 ? dupesFrom(e.data) : null
      if (list) setDupes(list)
      else setError(detailMsg(e))
    } finally {
      setBusy(false)
    }
  }

  async function reject() {
    setBusy(true)
    setError(null)
    try {
      await api(`/api/registration-requests/${req.id}/reject`, { method: 'POST', body: { note: note.trim() } })
      onChanged()
      onClose()
    } catch (e) {
      setError(detailMsg(e))
    } finally {
      setBusy(false)
    }
  }

  if (result) {
    return (
      <Modal title="Request approved" onClose={onClose}>
        <p className="mb-2 text-sm">
          {result.linkedToExistingPatient ? 'Linked to the existing patient' : 'New patient record created'}: <span className="font-semibold">{result.patientNo}</span>
        </p>
        <p className="mb-3 text-sm text-slate-600">{result.message}</p>
        {result.activationCode && (
          <>
            <div className="mb-2 rounded-lg bg-slate-100 py-4 text-center font-mono text-3xl tracking-widest">{result.activationCode.code}</div>
            <p className="text-sm text-slate-600">
              Patient number: <span className="font-medium">{result.activationCode.patientNo}</span><br />
              Expires: {new Date(result.activationCode.expiresAt).toLocaleString()}<br />
              Tell the patient to open the sign-in page and choose "I have an activation code".
            </p>
          </>
        )}
        <div className="mt-5 flex justify-end gap-2">
          {result.activationCode && <button className={btnGhost} onClick={() => void navigator.clipboard?.writeText(result.activationCode?.code ?? '')}>Copy code</button>}
          <button className={btnPrimary} onClick={onClose}>Done</button>
        </div>
      </Modal>
    )
  }

  return (
    <Modal title="Registration request" onClose={onClose} wide>
      <ErrorBox message={error} />
      <dl className="mb-4 grid gap-x-4 gap-y-1 text-sm sm:grid-cols-[9rem_1fr]">
        <dt className="text-slate-500">Name</dt><dd className="font-medium">{req.fullName}</dd>
        <dt className="text-slate-500">Date of birth</dt><dd>{fmtDate(req.dateOfBirth)} ({ageOf(req.dateOfBirth)} years)</dd>
        <dt className="text-slate-500">Gender</dt><dd>{nice(req.gender)}</dd>
        <dt className="text-slate-500">Phone</dt><dd>{req.phone}</dd>
        <dt className="text-slate-500">NIC</dt><dd>{req.nic ?? ''}</dd>
        <dt className="text-slate-500">Address</dt><dd>{req.address ?? ''}</dd>
        <dt className="text-slate-500">Submitted</dt><dd>{fmtDateTime(req.createdAt)}</dd>
      </dl>

      {!pending && (
        <p className="rounded-md bg-slate-50 p-3 text-sm">
          {nice(req.status)}{req.decidedByName && ` by ${req.decidedByName}`}{req.decidedAt && `, ${fmtDateTime(req.decidedAt)}`}
          {req.patientNo && <> · patient {req.patientNo}</>}
          {req.decisionNote && <div className="mt-1 text-slate-600">Note: {req.decisionNote}</div>}
        </p>
      )}

      {pending && (
        <>
          <p className="mb-3 rounded-md bg-amber-50 p-2 text-sm text-amber-900">
            Contact the person on the phone number above and check who they are before approving.
          </p>

          {dupes && (
            <div className="mb-4 rounded-md border border-amber-300 bg-amber-50 p-3 text-sm">
              <p className="mb-2 font-medium text-amber-900">This person may already be registered:</p>
              <ul className="mb-3 space-y-2">
                {dupes.map((d) => (
                  <li key={d.id} className="flex flex-wrap items-center justify-between gap-2">
                    <span><span className="font-medium">{d.patientNo}</span> {d.fullName}, born {fmtDate(d.dateOfBirth)}, {d.phone}</span>
                    <button className={btnSmall} disabled={busy || !note.trim()} onClick={() => void approve(d.id, false)}>Link to this patient</button>
                  </li>
                ))}
              </ul>
              <div className="flex flex-wrap gap-2">
                <button className={btnPrimary} disabled={busy || !note.trim()} onClick={() => void approve(null, true)}>A different person: create a new record</button>
                <button className={btnGhost} onClick={() => setDupes(null)}>Back</button>
              </div>
            </div>
          )}

          {!dupes && (
            <>
              <div className="mb-3 flex gap-4 text-sm">
                <label className="flex items-center gap-2"><input type="radio" checked={mode === 'new'} onChange={() => setMode('new')} />Create a new patient record</label>
                <label className="flex items-center gap-2"><input type="radio" checked={mode === 'link'} onChange={() => setMode('link')} />This person is already a patient</label>
              </div>
              {mode === 'link' && <div className="mb-3"><PatientPicker value={linked} onChange={setLinked} /></div>}
              <label className="mb-3 flex items-center gap-2 text-sm">
                <input type="checkbox" checked={issueCode} onChange={(e) => setIssueCode(e.target.checked)} />
                Give them a portal activation code
              </label>
              <Field label="Note (required): how was the identity checked? Do not write ID numbers.">
                <input className={inputCls} value={note} onChange={(e) => setNote(e.target.value)} maxLength={255} placeholder="e.g. Called back, confirmed date of birth and address" />
              </Field>
              <div className="mt-5 flex flex-wrap justify-end gap-2">
                <button className={btnGhost} onClick={onClose}>Close</button>
                <button className={`${btnGhost} text-red-700`} disabled={busy || !note.trim()} onClick={() => void reject()}>Reject</button>
                <button
                  className={btnPrimary}
                  disabled={busy || !note.trim() || (mode === 'link' && !linked)}
                  onClick={() => void approve(mode === 'link' && linked ? linked.id : null, false)}
                >
                  {busy ? 'Working...' : 'Approve'}
                </button>
              </div>
            </>
          )}
        </>
      )}
      {!pending && <div className="mt-5 flex justify-end"><button className={btnGhost} onClick={onClose}>Close</button></div>}
    </Modal>
  )
}

export default function Registrations() {
  const [status, setStatus] = useState('PENDING')
  const [page, setPage] = useState(0)
  const [data, setData] = useState<Page<Reg> | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [reload, setReload] = useState(0)
  const [open, setOpen] = useState<Reg | null>(null)

  useEffect(() => { setPage(0) }, [status])

  useEffect(() => {
    let active = true
    api<Page<Reg>>('/api/registration-requests', { query: { status, page, size: 15 } })
      .then((r) => { if (active) { setData(r); setError(null) } })
      .catch((e) => { if (active) setError(detailMsg(e)) })
    return () => { active = false }
  }, [status, page, reload])

  return (
    <div>
      <h1 className="mb-4 text-2xl font-semibold">Registration requests</h1>
      <p className="mb-3 text-sm text-slate-500">People who asked to register online. Nothing is created until you approve a request.</p>
      <div className="mb-3 max-w-xs">
        <Field label="Show">
          <select className={inputCls} value={status} onChange={(e) => setStatus(e.target.value)}>
            <option value="PENDING">Waiting for review</option>
            <option value="APPROVED">Approved</option>
            <option value="REJECTED">Rejected</option>
            <option value="">All</option>
          </select>
        </Field>
      </div>
      <ErrorBox message={error} />
      {!data && !error ? (
        <Spinner />
      ) : (
        <div className="overflow-x-auto rounded-xl border bg-white">
          <table className="w-full text-left text-sm">
            <thead className="bg-slate-50 text-slate-500">
              <tr>
                <th className="px-3 py-2">Name</th>
                <th className="px-3 py-2">Age</th>
                <th className="px-3 py-2">Phone</th>
                <th className="px-3 py-2">Submitted</th>
                <th className="px-3 py-2">Status</th>
                <th className="px-3 py-2"> </th>
              </tr>
            </thead>
            <tbody>
              {data?.content.length === 0 && <tr><td colSpan={6} className="px-3 py-6 text-center text-slate-500">No requests found.</td></tr>}
              {data?.content.map((r) => (
                <tr key={r.id} className="border-t">
                  <td className="px-3 py-2 font-medium">{r.fullName}</td>
                  <td className="px-3 py-2">{ageOf(r.dateOfBirth)}</td>
                  <td className="px-3 py-2">{r.phone}</td>
                  <td className="px-3 py-2 whitespace-nowrap">{fmtDateTime(r.createdAt)}</td>
                  <td className="px-3 py-2"><span className={`rounded-full px-2 py-0.5 text-xs font-medium ${STATUS_STYLE[r.status]}`}>{nice(r.status)}</span></td>
                  <td className="px-3 py-2"><button className={btnSmall} onClick={() => setOpen(r)}>{r.status === 'PENDING' ? 'Review' : 'View'}</button></td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
      {data && <Pagination page={data.page} totalPages={data.totalPages} onPage={setPage} />}
      {open && <ReviewModal req={open} onClose={() => setOpen(null)} onChanged={() => setReload((n) => n + 1)} />}
    </div>
  )
}