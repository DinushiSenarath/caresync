import { useEffect, useState } from 'react'
import type { ChangeEvent, FormEvent } from 'react'
import { api, ApiError } from '../api'
import { useAuth } from '../auth'
import type { Page, Patient } from '../types'
import {
  ageOf, btnGhost, btnPrimary, btnSmall, ErrorBox, errMsg, Field, fmtDate, fmtTime,
  inputCls, Modal, Pagination, Spinner, todayStr, useDebounced,
} from '../ui'

interface DuplicateMatch {
  id: number
  patientNo: string
  fullName: string
  dateOfBirth: string
  phone: string
}

function dupesFrom(data: unknown): DuplicateMatch[] | null {
  const d = data as { possibleDuplicates?: DuplicateMatch[] } | null
  return d && Array.isArray(d.possibleDuplicates) ? d.possibleDuplicates : null
}

type FormState = {
  fullName: string
  dateOfBirth: string
  gender: string
  nic: string
  phone: string
  address: string
  emergencyContactName: string
  emergencyContactPhone: string
  allergies: string
}

function PatientForm({
  patient, readOnly, onClose, onSaved,
}: { patient: Patient | null; readOnly: boolean; onClose: () => void; onSaved: () => void }) {
  const [f, setF] = useState<FormState>({
    fullName: patient?.fullName ?? '',
    dateOfBirth: patient?.dateOfBirth ?? '',
    gender: patient?.gender ?? 'MALE',
    nic: patient?.nic ?? '',
    phone: patient?.phone ?? '',
    address: patient?.address ?? '',
    emergencyContactName: patient?.emergencyContactName ?? '',
    emergencyContactPhone: patient?.emergencyContactPhone ?? '',
    allergies: patient?.allergies ?? '',
  })
  const [error, setError] = useState<string | null>(null)
  const [fe, setFe] = useState<Record<string, string>>({})
  const [dupes, setDupes] = useState<DuplicateMatch[] | null>(null)
  const [busy, setBusy] = useState(false)

  const set = (k: keyof FormState) => (e: ChangeEvent<HTMLInputElement | HTMLSelectElement | HTMLTextAreaElement>) =>
    setF((prev) => ({ ...prev, [k]: e.target.value }))

  async function save(confirmDuplicate: boolean) {
    setBusy(true)
    setError(null)
    setFe({})
    try {
      if (patient) {
        await api(`/api/patients/${patient.id}`, { method: 'PUT', body: f, query: { confirmDuplicate } })
      } else {
        await api('/api/patients', { method: 'POST', body: f, query: { confirmDuplicate } })
      }
      onSaved()
    } catch (e) {
      const list = e instanceof ApiError && e.status === 409 ? dupesFrom(e.data) : null
      if (list) {
        setDupes(list)
      } else {
        setError(errMsg(e))
        setFe(e instanceof ApiError && e.fieldErrors ? e.fieldErrors : {})
      }
    } finally {
      setBusy(false)
    }
  }

  return (
    <Modal title={patient ? (readOnly ? 'Patient details' : 'Edit patient') : 'Register patient'} onClose={onClose} wide>
      <form onSubmit={(e: FormEvent) => { e.preventDefault(); void save(false) }}>
        <ErrorBox message={error} />
        {dupes && (
          <div className="mb-4 rounded-md border border-amber-300 bg-amber-50 p-3 text-sm">
            <p className="mb-2 font-medium text-amber-900">A patient with the same NIC, or the same name and date of birth, already exists:</p>
            <ul className="mb-3 space-y-1">
              {dupes.map((d) => (
                <li key={d.id}>
                  <span className="font-medium">{d.patientNo}</span> {d.fullName}, born {fmtDate(d.dateOfBirth)}, {d.phone}
                </li>
              ))}
            </ul>
            <div className="flex flex-wrap gap-2">
              <button type="button" className={btnPrimary} disabled={busy} onClick={() => void save(true)}>
                This is a different person: save anyway
              </button>
              <button type="button" className={btnGhost} onClick={() => setDupes(null)}>Go back and edit</button>
            </div>
          </div>
        )}
        <div className="grid gap-3 sm:grid-cols-2">
          <Field label="Full name" error={fe.fullName} className="sm:col-span-2">
            <input className={inputCls} value={f.fullName} onChange={set('fullName')} required disabled={readOnly} />
          </Field>
          <Field label="Date of birth" error={fe.dateOfBirth}>
            <input type="date" max={todayStr()} className={inputCls} value={f.dateOfBirth} onChange={set('dateOfBirth')} required disabled={readOnly} />
          </Field>
          <Field label="Gender" error={fe.gender}>
            <select className={inputCls} value={f.gender} onChange={set('gender')} disabled={readOnly}>
              <option value="MALE">Male</option>
              <option value="FEMALE">Female</option>
              <option value="OTHER">Other</option>
            </select>
          </Field>
          <Field label="Phone" error={fe.phone}>
            <input className={inputCls} placeholder="0771234567" value={f.phone} onChange={set('phone')} required disabled={readOnly} />
          </Field>
          <Field label="NIC (optional)" error={fe.nic}>
            <input className={inputCls} value={f.nic} onChange={set('nic')} disabled={readOnly} />
          </Field>
          <Field label="Address" error={fe.address} className="sm:col-span-2">
            <input className={inputCls} value={f.address} onChange={set('address')} disabled={readOnly} />
          </Field>
          <Field label="Emergency contact name" error={fe.emergencyContactName}>
            <input className={inputCls} value={f.emergencyContactName} onChange={set('emergencyContactName')} disabled={readOnly} />
          </Field>
          <Field label="Emergency contact phone" error={fe.emergencyContactPhone}>
            <input className={inputCls} value={f.emergencyContactPhone} onChange={set('emergencyContactPhone')} disabled={readOnly} />
          </Field>
          <Field label="Allergies" error={fe.allergies} className="sm:col-span-2">
            <textarea rows={2} className={inputCls} value={f.allergies} onChange={set('allergies')} disabled={readOnly} />
          </Field>
        </div>
        <div className="mt-5 flex justify-end gap-2">
          <button type="button" className={btnGhost} onClick={onClose}>{readOnly ? 'Close' : 'Cancel'}</button>
          {!readOnly && <button type="submit" className={btnPrimary} disabled={busy}>{busy ? 'Saving...' : 'Save'}</button>}
        </div>
      </form>
    </Modal>
  )
}

interface IssuedCode {
  patientNo: string
  code: string
  expiresAt: string
}

function CodeModal({
  patient, kind, onClose,
}: { patient: Patient; kind: 'activation' | 'reset'; onClose: () => void }) {
  const [verification, setVerification] = useState('')
  const [result, setResult] = useState<IssuedCode | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)
  const isReset = kind === 'reset'

  async function issue() {
    setBusy(true)
    setError(null)
    try {
      const r = isReset
        ? await api<IssuedCode>(`/api/patients/${patient.id}/password-reset-code`, { method: 'POST', body: { verification } })
        : await api<IssuedCode>(`/api/patients/${patient.id}/activation-code`, { method: 'POST' })
      setResult(r)
    } catch (e) {
      setError(errMsg(e))
    } finally {
      setBusy(false)
    }
  }

  return (
    <Modal title={isReset ? 'Password reset code' : 'Portal activation code'} onClose={onClose}>
      <p className="mb-3 text-sm text-slate-600">
        Patient: <span className="font-medium">{patient.fullName}</span> ({patient.patientNo})
      </p>
      <ErrorBox message={error} />
      {!result ? (
        <>
          {isReset ? (
            <Field label="How was the patient's identity checked? (do not write ID numbers)">
              <input
                className={inputCls}
                placeholder="e.g. Date of birth and NIC checked at the counter"
                value={verification}
                onChange={(e) => setVerification(e.target.value)}
              />
            </Field>
          ) : (
            <p className="text-sm text-slate-600">
              This creates a one-time code, valid for 7 days, that lets the patient create their own portal login.
              Any earlier code stops working.
            </p>
          )}
          <div className="mt-5 flex justify-end gap-2">
            <button className={btnGhost} onClick={onClose}>Cancel</button>
            <button className={btnPrimary} disabled={busy || (isReset && !verification.trim())} onClick={() => void issue()}>
              {busy ? 'Working...' : 'Issue code'}
            </button>
          </div>
        </>
      ) : (
        <>
          <p className="mb-2 text-sm text-slate-600">Give this code to the patient. It is shown only once.</p>
          <div className="mb-2 rounded-lg bg-slate-100 py-4 text-center font-mono text-3xl tracking-widest">{result.code}</div>
          <p className="text-sm text-slate-600">
            Patient number: <span className="font-medium">{result.patientNo}</span>
            <br />
            Expires: {new Date(result.expiresAt).toLocaleString()}
          </p>
          <div className="mt-5 flex justify-end gap-2">
            <button className={btnGhost} onClick={() => void navigator.clipboard?.writeText(result.code)}>Copy code</button>
            <button className={btnPrimary} onClick={onClose}>Done</button>
          </div>
        </>
      )}
    </Modal>
  )
}

interface HistoryVisit {
  appointmentId: number
  date: string
  startTime: string
  status: string
  doctorName: string
  finalNote: {
    symptoms: string | null
    findings: string | null
    diagnosis: string | null
    treatmentPlan: string | null
  } | null
  latestVitals: {
    temperature: number | null
    pulse: number | null
    bpSystolic: number | null
    bpDiastolic: number | null
    respRate: number | null
    spo2: number | null
    flags: string[]
  } | null
}

interface History {
  patient: { id: number; patientNo: string; fullName: string; dateOfBirth: string | null; allergies: string | null }
  visits: HistoryVisit[]
}

function HistoryModal({ patient, onClose }: { patient: Patient; onClose: () => void }) {
  const [data, setData] = useState<History | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    let active = true
    api<History>(`/api/patients/${patient.id}/history`)
      .then((r) => { if (active) setData(r) })
      .catch((e) => { if (active) setError(errMsg(e)) })
    return () => { active = false }
  }, [patient.id])

  return (
    <Modal title={`History: ${patient.fullName}`} onClose={onClose} wide>
      <ErrorBox message={error} />
      {!data && !error && <Spinner />}
      {data && (
        <>
          {data.patient.allergies && (
            <p className="mb-3 rounded-md bg-red-50 p-3 text-sm text-red-800">
              <span className="font-semibold">Allergies:</span> {data.patient.allergies}
            </p>
          )}
          {data.visits.length === 0 && <p className="text-slate-500">No previous visits.</p>}
          <ul className="space-y-3">
            {data.visits.map((v) => (
              <li key={v.appointmentId} className="rounded-lg border p-3 text-sm">
                <div className="mb-1 font-medium">
                  {fmtDate(v.date)} {fmtTime(v.startTime)} · {v.doctorName}
                  <span className="ml-2 text-xs font-normal text-slate-500">{v.status.replace('_', ' ').toLowerCase()}</span>
                </div>
                {v.latestVitals && (
                  <p className="text-slate-600">
                    Vitals:
                    {v.latestVitals.temperature != null && ` temp ${v.latestVitals.temperature}°C`}
                    {v.latestVitals.pulse != null && `, pulse ${v.latestVitals.pulse}`}
                    {v.latestVitals.bpSystolic != null && `, BP ${v.latestVitals.bpSystolic}/${v.latestVitals.bpDiastolic}`}
                    {v.latestVitals.respRate != null && `, resp ${v.latestVitals.respRate}`}
                    {v.latestVitals.spo2 != null && `, SpO2 ${v.latestVitals.spo2}%`}
                    {v.latestVitals.flags.length > 0 && (
                      <span className="ml-2 rounded bg-amber-100 px-1.5 text-xs text-amber-800">
                        {v.latestVitals.flags.length} outside usual range
                      </span>
                    )}
                  </p>
                )}
                {v.finalNote ? (
                  <dl className="mt-1 grid gap-x-3 sm:grid-cols-[auto_1fr]">
                    {v.finalNote.symptoms && (<><dt className="text-slate-500">Symptoms</dt><dd>{v.finalNote.symptoms}</dd></>)}
                    {v.finalNote.findings && (<><dt className="text-slate-500">Findings</dt><dd>{v.finalNote.findings}</dd></>)}
                    {v.finalNote.diagnosis && (<><dt className="text-slate-500">Diagnosis</dt><dd>{v.finalNote.diagnosis}</dd></>)}
                    {v.finalNote.treatmentPlan && (<><dt className="text-slate-500">Plan</dt><dd>{v.finalNote.treatmentPlan}</dd></>)}
                  </dl>
                ) : (
                  <p className="mt-1 text-slate-400">No final note.</p>
                )}
              </li>
            ))}
          </ul>
        </>
      )}
      <div className="mt-4 flex justify-end">
        <button className={btnPrimary} onClick={onClose}>Close</button>
      </div>
    </Modal>
  )
}

type Dialog =
  | { type: 'form'; patient: Patient | null; readOnly: boolean }
  | { type: 'code'; patient: Patient; kind: 'activation' | 'reset' }
  | { type: 'history'; patient: Patient }
  | null

export default function Patients() {
  const { user } = useAuth()
  const role = user?.role
  const canWrite = role === 'ADMIN' || role === 'RECEPTIONIST'
  const isAdmin = role === 'ADMIN'
  const isDoctor = role === 'DOCTOR'

  const [q, setQ] = useState('')
  const dq = useDebounced(q)
  const [page, setPage] = useState(0)
  const [includeInactive, setIncludeInactive] = useState(false)
  const [data, setData] = useState<Page<Patient> | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [reload, setReload] = useState(0)
  const [dialog, setDialog] = useState<Dialog>(null)
  const [actionError, setActionError] = useState<string | null>(null)

  useEffect(() => {
    setPage(0)
  }, [dq, includeInactive])

  useEffect(() => {
    let active = true
    setLoading(true)
    setError(null)
    api<Page<Patient>>('/api/patients', { query: { q: dq, page, size: 15, includeInactive } })
      .then((r) => { if (active) setData(r) })
      .catch((e) => { if (active) setError(errMsg(e)) })
      .finally(() => { if (active) setLoading(false) })
    return () => { active = false }
  }, [dq, page, includeInactive, reload])

  async function toggleActive(p: Patient) {
    const verb = p.active ? 'deactivate' : 'reactivate'
    if (!window.confirm(`Do you want to ${verb} ${p.fullName}?`)) return
    setActionError(null)
    try {
      await api(`/api/patients/${p.id}/active`, { method: 'PATCH', body: { active: !p.active } })
      setReload((n) => n + 1)
    } catch (e) {
      setActionError(errMsg(e))
    }
  }

  return (
    <div>
      <div className="mb-4 flex flex-wrap items-center justify-between gap-3">
        <h1 className="text-2xl font-semibold">Patients</h1>
        {canWrite && (
          <button className={btnPrimary} onClick={() => setDialog({ type: 'form', patient: null, readOnly: false })}>
            Register patient
          </button>
        )}
      </div>

      <div className="mb-3 flex flex-wrap items-center gap-3">
        <input
          className={`${inputCls} max-w-sm`}
          placeholder="Search by name, phone, NIC or patient number"
          value={q}
          onChange={(e) => setQ(e.target.value)}
          aria-label="Search patients"
        />
        {isAdmin && (
          <label className="flex items-center gap-2 text-sm">
            <input type="checkbox" checked={includeInactive} onChange={(e) => setIncludeInactive(e.target.checked)} />
            Show inactive
          </label>
        )}
      </div>

      <ErrorBox message={error ?? actionError} />
      {loading && !data ? (
        <Spinner />
      ) : (
        <div className="overflow-x-auto rounded-xl border bg-white">
          <table className="w-full text-left text-sm">
            <thead className="bg-slate-50 text-slate-500">
              <tr>
                <th className="px-3 py-2">Number</th>
                <th className="px-3 py-2">Name</th>
                <th className="px-3 py-2">Age</th>
                <th className="px-3 py-2">Gender</th>
                <th className="px-3 py-2">Phone</th>
                <th className="px-3 py-2">Actions</th>
              </tr>
            </thead>
            <tbody>
              {data?.content.length === 0 && (
                <tr><td colSpan={6} className="px-3 py-6 text-center text-slate-500">No patients found.</td></tr>
              )}
              {data?.content.map((p) => (
                <tr key={p.id} className={`border-t ${p.active ? '' : 'text-slate-400'}`}>
                  <td className="px-3 py-2 font-medium">{p.patientNo}</td>
                  <td className="px-3 py-2">
                    {p.fullName}
                    {p.allergies && <span title={p.allergies} className="ml-2 rounded bg-red-100 px-1.5 py-0.5 text-xs text-red-700">Allergy</span>}
                    {!p.active && <span className="ml-2 rounded bg-slate-200 px-1.5 py-0.5 text-xs text-slate-600">Inactive</span>}
                  </td>
                  <td className="px-3 py-2">{ageOf(p.dateOfBirth)}</td>
                  <td className="px-3 py-2">{p.gender ? p.gender.charAt(0) + p.gender.slice(1).toLowerCase() : ''}</td>
                  <td className="px-3 py-2">{p.phone}</td>
                  <td className="px-3 py-2">
                    <div className="flex flex-wrap gap-1">
                      <button className={btnSmall} onClick={() => setDialog({ type: 'form', patient: p, readOnly: !canWrite })}>
                        {canWrite ? 'Edit' : 'View'}
                      </button>
                      {isDoctor && <button className={btnSmall} onClick={() => setDialog({ type: 'history', patient: p })}>History</button>}
                      {canWrite && p.active && (
                        <>
                          <button className={btnSmall} onClick={() => setDialog({ type: 'code', patient: p, kind: 'activation' })}>Activation code</button>
                          <button className={btnSmall} onClick={() => setDialog({ type: 'code', patient: p, kind: 'reset' })}>Reset code</button>
                        </>
                      )}
                      {isAdmin && (
                        <button className={btnSmall} onClick={() => void toggleActive(p)}>
                          {p.active ? 'Deactivate' : 'Reactivate'}
                        </button>
                      )}
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
      {data && <Pagination page={data.page} totalPages={data.totalPages} onPage={setPage} />}

      {dialog?.type === 'form' && (
        <PatientForm
          patient={dialog.patient}
          readOnly={dialog.readOnly}
          onClose={() => setDialog(null)}
          onSaved={() => { setDialog(null); setReload((n) => n + 1) }}
        />
      )}
      {dialog?.type === 'code' && <CodeModal patient={dialog.patient} kind={dialog.kind} onClose={() => setDialog(null)} />}
      {dialog?.type === 'history' && <HistoryModal patient={dialog.patient} onClose={() => setDialog(null)} />}
    </div>
  )
}