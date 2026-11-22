import { useEffect, useState } from 'react'
import type { FormEvent } from 'react'
import { api } from '../api'
import { useAuth } from '../auth'
import type { Role } from '../auth'
import PatientPicker from '../components/PatientPicker'
import type { Doctor, Page, Patient } from '../types'
import type { Admission, AdmissionSummary, Bed, Ward } from '../wardTypes'
import {
  btnGhost, btnPrimary, btnSmall, detailMsg, ErrorBox, Field, fmtDateTime, fmtMoney, inputCls,
  Modal, Pagination, Spinner, Tabs, useDebounced,
} from '../ui'

const WARD_TYPES = ['GENERAL', 'PRIVATE', 'ICU', 'MATERNITY', 'PAEDIATRIC', 'OTHER']
const nice = (s: string) => (s === 'ICU' ? 'ICU' : s.charAt(0) + s.slice(1).toLowerCase())

const STATUS_STYLE: Record<string, string> = {
  ADMITTED: 'bg-sky-100 text-sky-800',
  DISCHARGED: 'bg-green-100 text-green-800',
  CANCELLED: 'bg-slate-200 text-slate-600',
}

function StatusBadge({ status }: { status: string }) {
  return <span className={`rounded-full px-2 py-0.5 text-xs font-medium ${STATUS_STYLE[status] ?? ''}`}>{nice(status)}</span>
}

function freeBedOptions(wards: Ward[]): { id: number; label: string }[] {
  return wards
    .filter((w) => w.active)
    .flatMap((w) => w.beds.filter((b) => b.active && !b.occupied).map((b) => ({ id: b.id, label: `${w.name}, bed ${b.bedNo}` })))
}

/* ---------------- admit ---------------- */

function AdmitModal({ presetBedId, onClose, onSaved }: { presetBedId: number | null; onClose: () => void; onSaved: () => void }) {
  const [patient, setPatient] = useState<Patient | null>(null)
  const [doctors, setDoctors] = useState<Doctor[]>([])
  const [wards, setWards] = useState<Ward[]>([])
  const [doctorId, setDoctorId] = useState('')
  const [bedId, setBedId] = useState(presetBedId ? String(presetBedId) : '')
  const [reason, setReason] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  useEffect(() => {
    let active = true
    Promise.all([api<Doctor[]>('/api/doctors', { query: { activeOnly: true } }), api<Ward[]>('/api/wards')])
      .then(([d, w]) => { if (active) { setDoctors(d); setWards(w) } })
      .catch((e) => { if (active) setError(detailMsg(e)) })
    return () => { active = false }
  }, [])

  async function submit(e: FormEvent) {
    e.preventDefault()
    setError(null)
    if (!patient) { setError('Choose a patient.'); return }
    setBusy(true)
    try {
      await api('/api/admissions', {
        method: 'POST',
        body: { patientId: patient.id, responsibleDoctorId: Number(doctorId), bedId: Number(bedId), reason: reason.trim() },
      })
      onSaved()
    } catch (err) {
      setError(detailMsg(err))
    } finally {
      setBusy(false)
    }
  }

  return (
    <Modal title="Admit patient" onClose={onClose}>
      <form onSubmit={submit}>
        <ErrorBox message={error} />
        <div className="grid gap-3">
          <Field label="Patient"><PatientPicker value={patient} onChange={setPatient} /></Field>
          <Field label="Responsible doctor">
            <select className={inputCls} value={doctorId} onChange={(e) => setDoctorId(e.target.value)} required>
              <option value="">Select a doctor</option>
              {doctors.map((d) => <option key={d.id} value={d.id}>{d.fullName} ({d.departmentName})</option>)}
            </select>
          </Field>
          <Field label="Bed">
            <select className={inputCls} value={bedId} onChange={(e) => setBedId(e.target.value)} required>
              <option value="">Select a free bed</option>
              {freeBedOptions(wards).map((b) => <option key={b.id} value={b.id}>{b.label}</option>)}
            </select>
          </Field>
          <Field label="Reason for admission">
            <textarea className={inputCls} rows={2} maxLength={500} value={reason} onChange={(e) => setReason(e.target.value)} required />
          </Field>
        </div>
        <div className="mt-5 flex justify-end gap-2">
          <button type="button" className={btnGhost} onClick={onClose}>Cancel</button>
          <button type="submit" className={btnPrimary} disabled={busy || !patient || !doctorId || !bedId}>{busy ? 'Admitting...' : 'Admit'}</button>
        </div>
      </form>
    </Modal>
  )
}

/* ---------------- one admission ---------------- */

function AdmissionModal({
  id, role, onClose, onChanged,
}: { id: number; role: Role | undefined; onClose: () => void; onChanged: () => void }) {
  const [adm, setAdm] = useState<Admission | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [message, setMessage] = useState<string | null>(null)
  const [reload, setReload] = useState(0)
  const [summary, setSummary] = useState('')
  const [instructions, setInstructions] = useState('')
  const [mode, setMode] = useState<'transfer' | 'cancel' | null>(null)
  const [wards, setWards] = useState<Ward[]>([])
  const [bedId, setBedId] = useState('')
  const [text, setText] = useState('')
  const [busy, setBusy] = useState(false)
  const isWard = role === 'WARD_STAFF'
  const isDoctor = role === 'DOCTOR'

  useEffect(() => {
    let active = true
    api<Admission>(`/api/admissions/${id}`)
      .then((a) => {
        if (!active) return
        setAdm(a)
        setSummary(a.dischargeSummary ?? '')
        setInstructions(a.dischargeInstructions ?? '')
        setError(null)
      })
      .catch((e) => { if (active) setError(detailMsg(e)) })
    return () => { active = false }
  }, [id, reload])

  useEffect(() => {
    if (mode !== 'transfer') return
    let active = true
    api<Ward[]>('/api/wards')
      .then((w) => { if (active) setWards(w) })
      .catch((e) => { if (active) setError(detailMsg(e)) })
    return () => { active = false }
  }, [mode])

  async function run(fn: () => Promise<unknown>, done: string) {
    setBusy(true)
    setError(null)
    setMessage(null)
    try {
      await fn()
      setMessage(done)
      setMode(null)
      setText('')
      setBedId('')
      setReload((n) => n + 1)
      onChanged()
    } catch (e) {
      setError(detailMsg(e))
    } finally {
      setBusy(false)
    }
  }

  const saveSummary = () =>
    run(() => api(`/api/admissions/${id}/discharge-summary`, {
      method: 'PUT',
      body: { summary: summary.trim(), instructions: instructions.trim() || null },
    }), 'Discharge summary saved.')

  function discharge() {
    if (!window.confirm('Discharge this patient? The bed will be freed.')) return
    void run(() => api(`/api/admissions/${id}/discharge`, { method: 'POST' }), 'Patient discharged.')
  }

  const transfer = (e: FormEvent) => {
    e.preventDefault()
    void run(() => api(`/api/admissions/${id}/transfer`, { method: 'POST', body: { bedId: Number(bedId), reason: text.trim() } }),
      'Patient moved.')
  }

  const cancel = (e: FormEvent) => {
    e.preventDefault()
    void run(() => api(`/api/admissions/${id}/cancel`, { method: 'POST', body: { reason: text.trim() } }), 'Admission cancelled.')
  }

  const active = adm?.status === 'ADMITTED'
  const canDischarge = !!adm && active && adm.summaryWritten && (isWard || isDoctor)

  return (
    <Modal title={adm ? `Admission ${adm.admissionNo}` : 'Admission'} onClose={onClose} wide>
      <ErrorBox message={error} />
      {message && <p className="mb-3 rounded-md bg-green-50 p-2 text-sm text-green-800">{message}</p>}
      {!adm && !error && <Spinner />}
      {adm && (
        <>
          <div className="mb-4 flex flex-wrap items-start justify-between gap-2 text-sm">
            <div>
              <div className="text-lg font-semibold">{adm.patientName} <span className="text-base font-normal text-slate-500">({adm.patientNo})</span></div>
              <div className="text-slate-600">Responsible doctor: {adm.responsibleDoctorName}</div>
              <div className="text-slate-600">Admitted {fmtDateTime(adm.admittedAt)} by {adm.admittedByName}</div>
              {adm.currentWard && <div className="text-slate-600">{active ? 'Now in' : 'Last bed'}: {adm.currentWard}, bed {adm.currentBed}</div>}
              {adm.dischargedAt && <div className="text-slate-600">Discharged {fmtDateTime(adm.dischargedAt)} by {adm.dischargedByName}</div>}
              {adm.reason && <div className="mt-1">Reason: {adm.reason}</div>}
              {adm.cancelReason && <div className="mt-1 text-slate-600">Cancelled: {adm.cancelReason}</div>}
            </div>
            <StatusBadge status={adm.status} />
          </div>

          <h3 className="mb-1 text-sm font-medium">Bed history</h3>
          <div className="mb-4 overflow-x-auto rounded-lg border">
            <table className="w-full text-left text-sm">
              <thead className="bg-slate-50 text-xs text-slate-500">
                <tr>
                  <th className="px-3 py-2">Ward and bed</th>
                  <th className="px-3 py-2">From</th>
                  <th className="px-3 py-2">To</th>
                  <th className="px-3 py-2">Reason for move</th>
                </tr>
              </thead>
              <tbody>
                {adm.stays.map((s) => (
                  <tr key={s.id} className="border-t">
                    <td className="px-3 py-2">{s.wardName}, bed {s.bedNo}</td>
                    <td className="px-3 py-2">{fmtDateTime(s.fromAt)}</td>
                    <td className="px-3 py-2">{s.toAt ? fmtDateTime(s.toAt) : 'Current'}</td>
                    <td className="px-3 py-2">{s.transferReason}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          <h3 className="mb-1 text-sm font-medium">Discharge summary</h3>
          {isDoctor && active ? (
            <div className="mb-4 rounded-lg bg-slate-50 p-3">
              <Field label="Summary of the stay (diagnosis, treatment, condition at discharge)">
                <textarea className={inputCls} rows={5} maxLength={8000} value={summary} onChange={(e) => setSummary(e.target.value)} />
              </Field>
              <Field label="Instructions for the patient (medicines, follow-up, warning signs)" className="mt-3">
                <textarea className={inputCls} rows={3} maxLength={4000} value={instructions} onChange={(e) => setInstructions(e.target.value)} />
              </Field>
              <div className="mt-3">
                <button className={btnPrimary} disabled={busy || !summary.trim()} onClick={() => void saveSummary()}>Save discharge summary</button>
              </div>
            </div>
          ) : (
            <div className="mb-4 text-sm">
              {adm.dischargeSummary && <p className="mb-2 whitespace-pre-wrap rounded-md bg-slate-50 p-3">{adm.dischargeSummary}</p>}
              {adm.dischargeInstructions && (
                <p className="mb-2 whitespace-pre-wrap rounded-md bg-slate-50 p-3"><span className="font-medium">Instructions: </span>{adm.dischargeInstructions}</p>
              )}
              {!adm.dischargeSummary && adm.summaryWritten && <p className="text-slate-600">The doctor has written the discharge summary.</p>}
              {!adm.summaryWritten && <p className="text-slate-500">No discharge summary yet.</p>}
            </div>
          )}

          {mode === 'transfer' && (
            <form onSubmit={transfer} className="mb-4 rounded-lg border border-amber-300 bg-amber-50 p-3">
              <div className="grid gap-3 sm:grid-cols-2">
                <Field label="Move to">
                  <select className={inputCls} value={bedId} onChange={(e) => setBedId(e.target.value)} required>
                    <option value="">Select a free bed</option>
                    {freeBedOptions(wards).map((b) => <option key={b.id} value={b.id}>{b.label}</option>)}
                  </select>
                </Field>
                <Field label="Reason (required)"><input className={inputCls} value={text} onChange={(e) => setText(e.target.value)} maxLength={255} required /></Field>
              </div>
              <div className="mt-3 flex gap-2">
                <button type="submit" className={btnPrimary} disabled={busy || !bedId || !text.trim()}>Move patient</button>
                <button type="button" className={btnGhost} onClick={() => setMode(null)}>Back</button>
              </div>
            </form>
          )}

          {mode === 'cancel' && (
            <form onSubmit={cancel} className="mb-4 rounded-lg border border-amber-300 bg-amber-50 p-3">
              <Field label="Reason for cancelling this admission (required)">
                <input className={inputCls} value={text} onChange={(e) => setText(e.target.value)} maxLength={255} required />
              </Field>
              <div className="mt-3 flex gap-2">
                <button type="submit" className={btnPrimary} disabled={busy || !text.trim()}>Cancel admission</button>
                <button type="button" className={btnGhost} onClick={() => setMode(null)}>Back</button>
              </div>
            </form>
          )}

          <div className="flex flex-wrap justify-end gap-2">
            <button className={btnGhost} onClick={onClose}>Close</button>
            {isWard && active && !adm.summaryWritten && mode === null && (
              <button className={`${btnGhost} text-red-700`} onClick={() => { setText(''); setMode('cancel') }}>Cancel admission</button>
            )}
            {isWard && active && mode === null && (
              <button className={btnGhost} onClick={() => { setText(''); setBedId(''); setMode('transfer') }}>Transfer to another bed</button>
            )}
            {(isWard || isDoctor) && active && (
              <button
                className={btnPrimary}
                disabled={busy || !canDischarge}
                title={!adm.summaryWritten ? 'The doctor must write the discharge summary first' : ''}
                onClick={discharge}
              >
                Discharge patient
              </button>
            )}
          </div>
        </>
      )}
    </Modal>
  )
}

/* ---------------- bed map ---------------- */

function BedsTab({
  reloadKey, canAdmit, onAdmit, onOpen,
}: { reloadKey: number; canAdmit: boolean; onAdmit: (bedId: number) => void; onOpen: (id: number) => void }) {
  const [wards, setWards] = useState<Ward[] | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [tick, setTick] = useState(0)

  useEffect(() => {
    const t = setInterval(() => setTick((n) => n + 1), 30000)
    return () => clearInterval(t)
  }, [])

  useEffect(() => {
    let active = true
    api<Ward[]>('/api/wards')
      .then((r) => { if (active) { setWards(r); setError(null) } })
      .catch((e) => { if (active) setError(detailMsg(e)) })
    return () => { active = false }
  }, [reloadKey, tick])

  if (error) return <ErrorBox message={error} />
  if (!wards) return <Spinner />

  const usable = wards.reduce((n, w) => n + w.usableBeds, 0)
  const occupied = wards.reduce((n, w) => n + w.occupied, 0)

  return (
    <div>
      <p className="mb-3 text-sm text-slate-600">
        {occupied} of {usable} beds occupied ({usable === 0 ? 0 : Math.round((occupied * 100) / usable)}%).
        <span className="ml-3 inline-block h-3 w-3 rounded border border-green-600 bg-white align-middle" /> free
        <span className="ml-3 inline-block h-3 w-3 rounded bg-sky-200 align-middle" /> occupied
        <span className="ml-3 inline-block h-3 w-3 rounded bg-slate-200 align-middle" /> out of service
      </p>
      {wards.length === 0 && <p className="text-slate-500">No wards have been set up yet.</p>}
      {wards.map((w) => (
        <section key={w.id} className="mb-5 rounded-xl border bg-white p-4">
          <div className="mb-3 flex flex-wrap items-baseline justify-between gap-2">
            <h2 className="text-lg font-medium">{w.name} <span className="text-sm font-normal text-slate-500">{nice(w.wardType)}</span></h2>
            <span className="text-sm text-slate-500">{w.occupied} occupied · {w.free} free{w.outOfService > 0 && ` · ${w.outOfService} out of service`}</span>
          </div>
          <div className="grid gap-2 sm:grid-cols-3 lg:grid-cols-4 xl:grid-cols-6">
            {w.beds.map((b: Bed) => {
              if (!b.active) {
                return <div key={b.id} className="rounded-lg bg-slate-200 p-2 text-sm text-slate-500">{b.bedNo}<div className="text-xs">Out of service</div></div>
              }
              if (b.occupied && b.admissionId !== null) {
                return (
                  <button key={b.id} onClick={() => onOpen(b.admissionId as number)} className="rounded-lg bg-sky-100 p-2 text-left text-sm hover:bg-sky-200">
                    <span className="font-medium">{b.bedNo}</span>
                    <div className="truncate">{b.patientName}</div>
                    <div className="text-xs text-slate-500">{b.patientNo}</div>
                  </button>
                )
              }
              return (
                <button
                  key={b.id}
                  disabled={!canAdmit}
                  onClick={() => onAdmit(b.id)}
                  className="rounded-lg border border-green-600 bg-white p-2 text-left text-sm enabled:hover:bg-green-50"
                >
                  <span className="font-medium">{b.bedNo}</span>
                  <div className="text-xs text-green-700">{canAdmit ? 'Free: click to admit' : 'Free'}</div>
                </button>
              )
            })}
          </div>
        </section>
      ))}
    </div>
  )
}

/* ---------------- inpatient list ---------------- */

function InpatientsTab({ reloadKey, onOpen }: { reloadKey: number; onOpen: (id: number) => void }) {
  const [q, setQ] = useState('')
  const dq = useDebounced(q)
  const [status, setStatus] = useState('')
  const [page, setPage] = useState(0)
  const [data, setData] = useState<Page<AdmissionSummary> | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => { setPage(0) }, [dq, status])

  useEffect(() => {
    let active = true
    api<Page<AdmissionSummary>>('/api/admissions', { query: { q: dq, status, page, size: 15 } })
      .then((r) => { if (active) { setData(r); setError(null) } })
      .catch((e) => { if (active) setError(detailMsg(e)) })
    return () => { active = false }
  }, [dq, status, page, reloadKey])

  return (
    <div>
      <div className="mb-3 flex flex-wrap items-end gap-3">
        <Field label="Show">
          <select className={inputCls} value={status} onChange={(e) => setStatus(e.target.value)}>
            <option value="">Currently admitted</option>
            <option value="DISCHARGED">Discharged</option>
            <option value="CANCELLED">Cancelled</option>
            <option value="ALL">All</option>
          </select>
        </Field>
        <input className={`${inputCls} max-w-xs`} placeholder="Search patient or admission number" value={q} onChange={(e) => setQ(e.target.value)} aria-label="Search admissions" />
      </div>
      <ErrorBox message={error} />
      {!data && !error ? (
        <Spinner />
      ) : (
        <div className="overflow-x-auto rounded-xl border bg-white">
          <table className="w-full text-left text-sm">
            <thead className="bg-slate-50 text-slate-500">
              <tr>
                <th className="px-3 py-2">Admission</th>
                <th className="px-3 py-2">Patient</th>
                <th className="px-3 py-2">Doctor</th>
                <th className="px-3 py-2">Ward and bed</th>
                <th className="px-3 py-2">Admitted</th>
                <th className="px-3 py-2">Status</th>
                <th className="px-3 py-2"> </th>
              </tr>
            </thead>
            <tbody>
              {data?.content.length === 0 && <tr><td colSpan={7} className="px-3 py-6 text-center text-slate-500">No admissions found.</td></tr>}
              {data?.content.map((a) => (
                <tr key={a.id} className="border-t">
                  <td className="px-3 py-2 font-medium">{a.admissionNo}</td>
                  <td className="px-3 py-2">{a.patientName} <span className="text-slate-400">({a.patientNo})</span></td>
                  <td className="px-3 py-2">{a.doctorName}</td>
                  <td className="px-3 py-2">{a.ward ? `${a.ward}, ${a.bed}` : ''}</td>
                  <td className="px-3 py-2 whitespace-nowrap">{fmtDateTime(a.admittedAt)}</td>
                  <td className="px-3 py-2">
                    <StatusBadge status={a.status} />
                    {a.status === 'ADMITTED' && a.summaryWritten && <span className="ml-1 rounded bg-amber-100 px-1.5 py-0.5 text-xs text-amber-800">Ready to discharge</span>}
                  </td>
                  <td className="px-3 py-2"><button className={btnSmall} onClick={() => onOpen(a.id)}>Open</button></td>
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

/* ---------------- wards and beds setup (admin) ---------------- */

function WardForm({ ward, onClose, onSaved }: { ward: Ward | null; onClose: () => void; onSaved: () => void }) {
  const [name, setName] = useState(ward?.name ?? '')
  const [type, setType] = useState(ward?.wardType ?? 'GENERAL')
  const [description, setDescription] = useState(ward?.description ?? '')
  const [rate, setRate] = useState(ward ? String(ward.dailyRate) : '')
  const [active, setActive] = useState(ward?.active ?? true)
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  async function submit(e: FormEvent) {
    e.preventDefault()
    setError(null)
    const price = Number(rate)
    if (rate.trim() === '' || Number.isNaN(price) || price < 0) { setError('Enter a daily rate of 0 or more.'); return }
    const body = { name: name.trim(), wardType: type, description: description.trim() || null, dailyRate: price, active }
    setBusy(true)
    try {
      if (ward) await api(`/api/wards/${ward.id}`, { method: 'PUT', body })
      else await api('/api/wards', { method: 'POST', body })
      onSaved()
    } catch (err) {
      setError(detailMsg(err))
    } finally {
      setBusy(false)
    }
  }

  return (
    <Modal title={ward ? 'Edit ward' : 'Add ward'} onClose={onClose}>
      <form onSubmit={submit}>
        <ErrorBox message={error} />
        <div className="grid gap-3">
          <Field label="Name"><input className={inputCls} value={name} onChange={(e) => setName(e.target.value)} required maxLength={100} /></Field>
          <Field label="Type">
            <select className={inputCls} value={type} onChange={(e) => setType(e.target.value)}>
              {WARD_TYPES.map((t) => <option key={t} value={t}>{nice(t)}</option>)}
            </select>
          </Field>
          <Field label="Description"><input className={inputCls} value={description} onChange={(e) => setDescription(e.target.value)} maxLength={255} /></Field>
          <Field label="Daily bed rate (LKR)"><input className={inputCls} inputMode="decimal" value={rate} onChange={(e) => setRate(e.target.value)} required /></Field>
          {ward && (
            <label className="flex items-center gap-2 text-sm">
              <input type="checkbox" checked={active} onChange={(e) => setActive(e.target.checked)} />
              Active (an inactive ward cannot take new patients)
            </label>
          )}
        </div>
        <p className="mt-3 text-xs text-slate-500">Changing the rate never changes bills for stays that already happened.</p>
        <div className="mt-5 flex justify-end gap-2">
          <button type="button" className={btnGhost} onClick={onClose}>Cancel</button>
          <button type="submit" className={btnPrimary} disabled={busy}>{busy ? 'Saving...' : 'Save'}</button>
        </div>
      </form>
    </Modal>
  )
}

function BedForm({ wardId, bed, onClose, onSaved }: { wardId: number; bed: Bed | null; onClose: () => void; onSaved: () => void }) {
  const [bedNo, setBedNo] = useState(bed?.bedNo ?? '')
  const [active, setActive] = useState(bed?.active ?? true)
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  async function submit(e: FormEvent) {
    e.preventDefault()
    setError(null)
    setBusy(true)
    try {
      const body = { bedNo: bedNo.trim(), active }
      if (bed) await api(`/api/beds/${bed.id}`, { method: 'PUT', body })
      else await api(`/api/wards/${wardId}/beds`, { method: 'POST', body })
      onSaved()
    } catch (err) {
      setError(detailMsg(err))
    } finally {
      setBusy(false)
    }
  }

  return (
    <Modal title={bed ? `Edit bed ${bed.bedNo}` : 'Add bed'} onClose={onClose}>
      <form onSubmit={submit}>
        <ErrorBox message={error} />
        <Field label="Bed number"><input className={inputCls} value={bedNo} onChange={(e) => setBedNo(e.target.value)} required maxLength={20} /></Field>
        {bed && (
          <label className="mt-3 flex items-center gap-2 text-sm">
            <input type="checkbox" checked={active} onChange={(e) => setActive(e.target.checked)} />
            In service (untick for repairs or cleaning)
          </label>
        )}
        <div className="mt-5 flex justify-end gap-2">
          <button type="button" className={btnGhost} onClick={onClose}>Cancel</button>
          <button type="submit" className={btnPrimary} disabled={busy}>{busy ? 'Saving...' : 'Save'}</button>
        </div>
      </form>
    </Modal>
  )
}

function ManageTab({ reloadKey, onChanged }: { reloadKey: number; onChanged: () => void }) {
  const [wards, setWards] = useState<Ward[] | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [reload, setReload] = useState(0)
  const [wardForm, setWardForm] = useState<{ ward: Ward | null } | null>(null)
  const [bedForm, setBedForm] = useState<{ wardId: number; bed: Bed | null } | null>(null)

  useEffect(() => {
    let active = true
    api<Ward[]>('/api/wards')
      .then((r) => { if (active) { setWards(r); setError(null) } })
      .catch((e) => { if (active) setError(detailMsg(e)) })
    return () => { active = false }
  }, [reload, reloadKey])

  function saved() {
    setWardForm(null)
    setBedForm(null)
    setReload((n) => n + 1)
    onChanged()
  }

  return (
    <div>
      <div className="mb-3 flex justify-end"><button className={btnPrimary} onClick={() => setWardForm({ ward: null })}>Add ward</button></div>
      <ErrorBox message={error} />
      {!wards && !error && <Spinner />}
      {wards?.map((w) => (
        <section key={w.id} className={`mb-4 rounded-xl border bg-white p-4 ${w.active ? '' : 'opacity-70'}`}>
          <div className="mb-3 flex flex-wrap items-center justify-between gap-2">
            <div>
              <h2 className="text-lg font-medium">
                {w.name} <span className="text-sm font-normal text-slate-500">{nice(w.wardType)} · {fmtMoney(w.dailyRate)} per day</span>
                {!w.active && <span className="ml-2 rounded bg-slate-200 px-1.5 py-0.5 text-xs text-slate-600">Inactive</span>}
              </h2>
              {w.description && <p className="text-sm text-slate-500">{w.description}</p>}
            </div>
            <div className="flex gap-2">
              <button className={btnSmall} onClick={() => setWardForm({ ward: w })}>Edit ward</button>
              <button className={btnSmall} onClick={() => setBedForm({ wardId: w.id, bed: null })}>Add bed</button>
            </div>
          </div>
          <div className="flex flex-wrap gap-2">
            {w.beds.length === 0 && <p className="text-sm text-slate-500">No beds yet.</p>}
            {w.beds.map((b) => (
              <button
                key={b.id}
                onClick={() => setBedForm({ wardId: w.id, bed: b })}
                className={`rounded border px-2 py-1 text-sm hover:bg-slate-50 ${b.active ? '' : 'bg-slate-200 text-slate-500'}`}
                title={b.occupied ? `Occupied by ${b.patientName}` : b.active ? 'Free' : 'Out of service'}
              >
                {b.bedNo}{b.occupied && ' •'}
              </button>
            ))}
          </div>
        </section>
      ))}
      {wardForm && <WardForm ward={wardForm.ward} onClose={() => setWardForm(null)} onSaved={saved} />}
      {bedForm && <BedForm wardId={bedForm.wardId} bed={bedForm.bed} onClose={() => setBedForm(null)} onSaved={saved} />}
    </div>
  )
}

/* ---------------- the page ---------------- */

type WTab = 'beds' | 'inpatients' | 'manage'

export default function Wards() {
  const { user } = useAuth()
  const role = user?.role
  const isWard = role === 'WARD_STAFF'
  const isAdmin = role === 'ADMIN'
  const [tab, setTab] = useState<WTab>('beds')
  const [reload, setReload] = useState(0)
  const [openId, setOpenId] = useState<number | null>(null)
  const [admit, setAdmit] = useState<{ bedId: number | null } | null>(null)

  const tabs: { key: WTab; label: string }[] = [
    { key: 'beds', label: 'Bed map' },
    { key: 'inpatients', label: 'Inpatients' },
    ...(isAdmin ? [{ key: 'manage' as const, label: 'Wards and beds' }] : []),
  ]
  const bump = () => setReload((n) => n + 1)

  return (
    <div>
      <div className="mb-4 flex flex-wrap items-center justify-between gap-3">
        <h1 className="text-2xl font-semibold">Wards</h1>
        {isWard && <button className={btnPrimary} onClick={() => setAdmit({ bedId: null })}>Admit patient</button>}
      </div>
      <Tabs tabs={tabs} value={tab} onChange={setTab} />
      {tab === 'beds' && <BedsTab reloadKey={reload} canAdmit={isWard} onAdmit={(bedId) => setAdmit({ bedId })} onOpen={setOpenId} />}
      {tab === 'inpatients' && <InpatientsTab reloadKey={reload} onOpen={setOpenId} />}
      {tab === 'manage' && isAdmin && <ManageTab reloadKey={reload} onChanged={bump} />}
      {admit && <AdmitModal presetBedId={admit.bedId} onClose={() => setAdmit(null)} onSaved={() => { setAdmit(null); bump() }} />}
      {openId !== null && <AdmissionModal id={openId} role={role} onClose={() => setOpenId(null)} onChanged={bump} />}
    </div>
  )
}