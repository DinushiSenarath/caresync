import { useEffect, useState } from 'react'
import type { FormEvent, ReactNode } from 'react'
import { Link, useParams } from 'react-router-dom'
import { api, ApiError } from '../api'
import { useAuth } from '../auth'
import type { Role } from '../auth'
import type { Appointment, Page, Patient } from '../types'
import { ResultsTable } from '../components/LabResults'
import { LabStatusBadge, PriorityBadge } from '../components/LabResults'
import type { LabRequest, LabTest } from '../labTypes'
import {
  ageOf, btnGhost, btnPrimary, btnSmall, ErrorBox, errMsg, Field, fmtDate, fmtTime,
  inputCls, Modal, Spinner, StatusBadge, useDebounced,
} from '../ui'

interface Vital {
  id: number
  recordedBy: string
  temperature: number | null
  pulse: number | null
  bpSystolic: number | null
  bpDiastolic: number | null
  respRate: number | null
  spo2: number | null
  weight: number | null
  height: number | null
  recordedAt: string
  flags: string[]
}

interface NursingNote {
  id: number
  nurseName: string
  note: string
  createdAt: string
}

interface Note {
  id: number
  authorName: string
  symptoms: string | null
  findings: string | null
  diagnosis: string | null
  treatmentPlan: string | null
  status: 'DRAFT' | 'FINAL'
  finalisedAt: string | null
  updatedAt: string
}

interface RxItem {
  id: number
  medicineId: number
  medicineName: string
  strength: string | null
  form: string
  unit: string
  dose: string
  route: string | null
  frequency: string
  duration: string | null
  instructions: string | null
  qtyPrescribed: number
  qtyDispensed: number
  qtyRemaining: number
}

type RxStatus = 'DRAFT' | 'ISSUED' | 'PARTIALLY_DISPENSED' | 'DISPENSED' | 'CANCELLED'

interface Rx {
  id: number
  status: RxStatus
  notes: string | null
  issuedAt: string | null
  items: RxItem[]
  allergyWarnings: string[]
}

interface MedicineRef {
  id: number
  name: string
  strength: string | null
  unit: string
  availableStock: number | null
}

const FLAG_LABEL: Record<string, string> = {
  TEMPERATURE_HIGH: 'Temperature high',
  TEMPERATURE_LOW: 'Temperature low',
  PULSE_HIGH: 'Pulse high',
  PULSE_LOW: 'Pulse low',
  SYSTOLIC_HIGH: 'Systolic BP high',
  SYSTOLIC_LOW: 'Systolic BP low',
  DIASTOLIC_HIGH: 'Diastolic BP high',
  DIASTOLIC_LOW: 'Diastolic BP low',
  RESP_RATE_HIGH: 'Breathing rate high',
  RESP_RATE_LOW: 'Breathing rate low',
  SPO2_LOW: 'Oxygen low',
}

const RX_LABEL: Record<RxStatus, string> = {
  DRAFT: 'Draft',
  ISSUED: 'Issued to pharmacy',
  PARTIALLY_DISPENSED: 'Partly dispensed',
  DISPENSED: 'Dispensed',
  CANCELLED: 'Cancelled',
}

const RX_STYLE: Record<RxStatus, string> = {
  DRAFT: 'bg-slate-200 text-slate-700',
  ISSUED: 'bg-sky-100 text-sky-800',
  PARTIALLY_DISPENSED: 'bg-amber-100 text-amber-800',
  DISPENSED: 'bg-green-100 text-green-800',
  CANCELLED: 'bg-slate-200 text-slate-500',
}

const VITAL_FIELDS = [
  ['temperature', 'Temperature (°C)', '36.8'],
  ['pulse', 'Pulse (per minute)', '72'],
  ['bpSystolic', 'Blood pressure, upper', '120'],
  ['bpDiastolic', 'Blood pressure, lower', '80'],
  ['respRate', 'Breathing rate (per minute)', '16'],
  ['spo2', 'Oxygen saturation (%)', '98'],
  ['weight', 'Weight (kg)', '65'],
  ['height', 'Height (cm)', '165'],
] as const

const EMPTY_VITALS: Record<string, string> = {
  temperature: '', pulse: '', bpSystolic: '', bpDiastolic: '', respRate: '', spo2: '', weight: '', height: '',
}

function detailMsg(e: unknown): string {
  if (e instanceof ApiError && e.fieldErrors) {
    const parts = Object.values(e.fieldErrors)
    if (parts.length > 0) return `${e.message}: ${parts.join('; ')}`
  }
  return errMsg(e)
}

function Section({ title, children }: { title: string; children: ReactNode }) {
  return (
    <section className="mb-5 rounded-xl border bg-white p-4">
      <h2 className="mb-3 text-lg font-medium">{title}</h2>
      {children}
    </section>
  )
}

function vitalLine(v: Vital): string {
  const parts: string[] = []
  if (v.temperature != null) parts.push(`Temp ${v.temperature}°C`)
  if (v.pulse != null) parts.push(`Pulse ${v.pulse}`)
  if (v.bpSystolic != null && v.bpDiastolic != null) parts.push(`BP ${v.bpSystolic}/${v.bpDiastolic}`)
  if (v.respRate != null) parts.push(`Resp ${v.respRate}`)
  if (v.spo2 != null) parts.push(`SpO2 ${v.spo2}%`)
  if (v.weight != null) parts.push(`${v.weight} kg`)
  if (v.height != null) parts.push(`${v.height} cm`)
  return parts.join(' · ')
}

/* ---------------- vital signs ---------------- */

function VitalsSection({ appt, role }: { appt: Appointment; role: Role | undefined }) {
  const [list, setList] = useState<Vital[] | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [reload, setReload] = useState(0)
  const [f, setF] = useState<Record<string, string>>(EMPTY_VITALS)
  const [fe, setFe] = useState<Record<string, string>>({})
  const [formError, setFormError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)
  const canRecord = role === 'NURSE' && (appt.status === 'CHECKED_IN' || appt.status === 'IN_PROGRESS')

  useEffect(() => {
    let active = true
    api<Vital[]>(`/api/appointments/${appt.id}/vitals`)
      .then((r) => { if (active) setList(r) })
      .catch((e) => { if (active) setError(errMsg(e)) })
    return () => { active = false }
  }, [appt.id, reload])

  async function submit(e: FormEvent) {
    e.preventDefault()
    setFormError(null)
    setFe({})
    const body: Record<string, number> = {}
    for (const [key, value] of Object.entries(f)) {
      if (value.trim() === '') continue
      const n = Number(value)
      if (Number.isNaN(n)) {
        setFormError(`"${value}" is not a number.`)
        return
      }
      body[key] = n
    }
    setBusy(true)
    try {
      await api(`/api/appointments/${appt.id}/vitals`, { method: 'POST', body })
      setF(EMPTY_VITALS)
      setReload((n) => n + 1)
    } catch (err) {
      setFormError(errMsg(err))
      setFe(err instanceof ApiError && err.fieldErrors ? err.fieldErrors : {})
    } finally {
      setBusy(false)
    }
  }

  return (
    <Section title="Vital signs">
      <ErrorBox message={error} />
      {canRecord && (
        <form onSubmit={submit} className="mb-4 rounded-lg bg-slate-50 p-3">
          <ErrorBox message={formError} />
          <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
            {VITAL_FIELDS.map(([key, label, hint]) => (
              <Field key={key} label={label} error={fe[key]}>
                <input
                  className={inputCls}
                  inputMode="decimal"
                  placeholder={hint}
                  value={f[key]}
                  onChange={(e) => setF((prev) => ({ ...prev, [key]: e.target.value }))}
                />
              </Field>
            ))}
          </div>
          <p className="mt-2 text-xs text-slate-500">Fill in only what was measured. Blood pressure needs both values.</p>
          <div className="mt-3">
            <button type="submit" className={btnPrimary} disabled={busy}>{busy ? 'Saving...' : 'Record vital signs'}</button>
          </div>
        </form>
      )}
      {!list && !error && <Spinner />}
      {list && list.length === 0 && <p className="text-sm text-slate-500">No vital signs recorded yet.</p>}
      <ul className="space-y-2">
        {list?.map((v) => (
          <li key={v.id} className="rounded-lg border p-3 text-sm">
            <div className="font-medium">{vitalLine(v)}</div>
            <div className="mt-1 flex flex-wrap items-center gap-2 text-xs text-slate-500">
              <span>{new Date(v.recordedAt).toLocaleString()} · {v.recordedBy}</span>
              {v.flags.map((fl) => (
                <span key={fl} className="rounded bg-amber-100 px-1.5 py-0.5 text-amber-800">{FLAG_LABEL[fl] ?? fl}</span>
              ))}
            </div>
          </li>
        ))}
      </ul>
      {list && list.some((v) => v.flags.length > 0) && (
        <p className="mt-2 text-xs text-slate-500">Flags are prompts based on general adult ranges. They are not a diagnosis.</p>
      )}
    </Section>
  )
}

/* ---------------- nursing notes ---------------- */

function NursingNotesSection({ appt, role }: { appt: Appointment; role: Role | undefined }) {
  const [list, setList] = useState<NursingNote[] | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [reload, setReload] = useState(0)
  const [text, setText] = useState('')
  const [formError, setFormError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)
  const stageOk = appt.status === 'CHECKED_IN' || appt.status === 'IN_PROGRESS' || appt.status === 'COMPLETED'
  const canAdd = role === 'NURSE' && stageOk

  useEffect(() => {
    let active = true
    api<NursingNote[]>(`/api/appointments/${appt.id}/nursing-notes`)
      .then((r) => { if (active) setList(r) })
      .catch((e) => { if (active) setError(errMsg(e)) })
    return () => { active = false }
  }, [appt.id, reload])

  async function submit(e: FormEvent) {
    e.preventDefault()
    setFormError(null)
    setBusy(true)
    try {
      await api(`/api/appointments/${appt.id}/nursing-notes`, { method: 'POST', body: { note: text } })
      setText('')
      setReload((n) => n + 1)
    } catch (err) {
      setFormError(detailMsg(err))
    } finally {
      setBusy(false)
    }
  }

  return (
    <Section title="Nursing notes">
      <ErrorBox message={error} />
      {canAdd && (
        <form onSubmit={submit} className="mb-4">
          <ErrorBox message={formError} />
          <textarea
            className={inputCls}
            rows={2}
            maxLength={2000}
            placeholder="Add a nursing note"
            value={text}
            onChange={(e) => setText(e.target.value)}
          />
          <div className="mt-2">
            <button type="submit" className={btnPrimary} disabled={busy || !text.trim()}>{busy ? 'Saving...' : 'Add note'}</button>
          </div>
        </form>
      )}
      {!list && !error && <Spinner />}
      {list && list.length === 0 && <p className="text-sm text-slate-500">No nursing notes.</p>}
      <ul className="space-y-2">
        {list?.map((n) => (
          <li key={n.id} className="rounded-lg border p-3 text-sm">
            <div>{n.note}</div>
            <div className="mt-1 text-xs text-slate-500">{new Date(n.createdAt).toLocaleString()} · {n.nurseName}</div>
          </li>
        ))}
      </ul>
    </Section>
  )
}

/* ---------------- consultation note ---------------- */

function NoteView({ note }: { note: Note }) {
  const rows: [string, string | null][] = [
    ['Symptoms', note.symptoms],
    ['Findings', note.findings],
    ['Diagnosis', note.diagnosis],
    ['Treatment plan', note.treatmentPlan],
  ]
  return (
    <div>
      <p className="mb-3 rounded-md bg-green-50 p-2 text-sm text-green-800">
        Final note by {note.authorName}
        {note.finalisedAt && `, ${new Date(note.finalisedAt).toLocaleString()}`}. It is locked and cannot be edited.
      </p>
      <dl className="grid gap-x-4 gap-y-2 text-sm sm:grid-cols-[10rem_1fr]">
        {rows.map(([label, value]) => (
          <div key={label} className="contents">
            <dt className="text-slate-500">{label}</dt>
            <dd className="whitespace-pre-wrap">{value || '-'}</dd>
          </div>
        ))}
      </dl>
    </div>
  )
}

function NoteForm({ appt, note, onSaved }: { appt: Appointment; note: Note | null; onSaved: () => void }) {
  const [f, setF] = useState({
    symptoms: note?.symptoms ?? '',
    findings: note?.findings ?? '',
    diagnosis: note?.diagnosis ?? '',
    treatmentPlan: note?.treatmentPlan ?? '',
  })
  const [error, setError] = useState<string | null>(null)
  const [message, setMessage] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  async function save() {
    setBusy(true)
    setError(null)
    setMessage(null)
    try {
      await api(`/api/appointments/${appt.id}/note`, { method: 'PUT', body: f })
      setMessage('Draft saved.')
      onSaved()
    } catch (e) {
      setError(detailMsg(e))
    } finally {
      setBusy(false)
    }
  }

  async function finalise() {
    if (!window.confirm('Finalise this note? It cannot be edited afterwards.')) return
    setBusy(true)
    setError(null)
    setMessage(null)
    try {
      await api(`/api/appointments/${appt.id}/note`, { method: 'PUT', body: f })
      await api(`/api/appointments/${appt.id}/note/finalize`, { method: 'POST' })
      onSaved()
    } catch (e) {
      setError(detailMsg(e))
    } finally {
      setBusy(false)
    }
  }

  const set = (k: keyof typeof f) => (e: { target: { value: string } }) =>
    setF((prev) => ({ ...prev, [k]: e.target.value }))

  return (
    <div>
      <ErrorBox message={error} />
      {message && <p className="mb-3 rounded-md bg-green-50 p-2 text-sm text-green-800">{message}</p>}
      <div className="grid gap-3">
        <Field label="Symptoms"><textarea rows={2} className={inputCls} value={f.symptoms} onChange={set('symptoms')} maxLength={4000} /></Field>
        <Field label="Examination findings"><textarea rows={2} className={inputCls} value={f.findings} onChange={set('findings')} maxLength={4000} /></Field>
        <Field label="Diagnosis (needed to finalise)"><textarea rows={2} className={inputCls} value={f.diagnosis} onChange={set('diagnosis')} maxLength={4000} /></Field>
        <Field label="Treatment plan and follow-up advice">
          <textarea rows={3} className={inputCls} value={f.treatmentPlan} onChange={set('treatmentPlan')} maxLength={4000} />
          <span className="mt-1 block text-xs text-slate-500">
            If the patient should come again, write it here, for example "Review in 1 week". Reception will book it.
          </span>
        </Field>
      </div>
      <div className="mt-4 flex flex-wrap gap-2">
        <button className={btnGhost} disabled={busy} onClick={() => void save()}>Save draft</button>
        <button className={btnPrimary} disabled={busy} onClick={() => void finalise()}>Finalise note</button>
      </div>
    </div>
  )
}

function ConsultationSection({ appt, role }: { appt: Appointment; role: Role | undefined }) {
  const [state, setState] = useState<{ loaded: boolean; note: Note | null }>({ loaded: false, note: null })
  const [error, setError] = useState<string | null>(null)
  const [reload, setReload] = useState(0)

  useEffect(() => {
    let active = true
    api<Note>(`/api/appointments/${appt.id}/note`)
      .then((n) => { if (active) setState({ loaded: true, note: n }) })
      .catch((e) => {
        if (!active) return
        if (e instanceof ApiError && e.status === 404) setState({ loaded: true, note: null })
        else setError(errMsg(e))
      })
    return () => { active = false }
  }, [appt.id, reload])

  const note = state.note
  const stageOk = appt.status === 'IN_PROGRESS' || appt.status === 'COMPLETED'

  let body: ReactNode
  if (error) body = <ErrorBox message={error} />
  else if (!state.loaded) body = <Spinner />
  else if (role === 'NURSE') body = note ? <NoteView note={note} /> : <p className="text-sm text-slate-500">No final note yet.</p>
  else if (note?.status === 'FINAL') body = <NoteView note={note} />
  else if (!stageOk) body = <p className="text-sm text-slate-500">Start the consultation to write the note.</p>
  else body = <NoteForm key={note?.id ?? 'new'} appt={appt} note={note} onSaved={() => setReload((n) => n + 1)} />

  return <Section title="Consultation note">{body}</Section>
}

/* ---------------- prescriptions ---------------- */

interface MedicineOption {
  id: number
  name: string
  strength: string | null
  unit: string
  availableStock: number
}

function MedicinePicker({ value, onChange }: { value: MedicineRef | null; onChange: (m: MedicineRef | null) => void }) {
  const [q, setQ] = useState('')
  const dq = useDebounced(q)
  const [results, setResults] = useState<MedicineOption[]>([])

  useEffect(() => {
    let active = true
    if (dq.trim().length < 2) {
      setResults([])
      return
    }
    api<Page<MedicineOption>>('/api/medicines', { query: { q: dq, size: 8 } })
      .then((r) => { if (active) setResults(r.content) })
      .catch(() => { if (active) setResults([]) })
    return () => { active = false }
  }, [dq])

  if (value) {
    return (
      <div className="flex items-center justify-between rounded-md border bg-slate-50 px-3 py-2 text-sm">
        <span>
          <span className="font-medium">{value.name}</span>
          {value.strength && ` ${value.strength}`}
          {value.availableStock != null && (
            <span className={value.availableStock === 0 ? 'text-red-600' : 'text-slate-500'}> · stock {value.availableStock}</span>
          )}
        </span>
        <button type="button" className={btnSmall} onClick={() => onChange(null)}>Change</button>
      </div>
    )
  }
  return (
    <div>
      <input className={inputCls} placeholder="Type at least 2 letters of the medicine name" value={q} onChange={(e) => setQ(e.target.value)} />
      {results.length > 0 && (
        <ul className="mt-1 rounded-md border bg-white">
          {results.map((m) => (
            <li key={m.id}>
              <button
                type="button"
                className="block w-full px-3 py-2 text-left text-sm hover:bg-teal-50"
                onClick={() => onChange({ id: m.id, name: m.name, strength: m.strength, unit: m.unit, availableStock: m.availableStock })}
              >
                <span className="font-medium">{m.name}</span>{m.strength && ` ${m.strength}`}
                <span className={m.availableStock === 0 ? 'text-red-600' : 'text-slate-400'}> · stock {m.availableStock}</span>
              </button>
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}

interface ItemForm {
  medicine: MedicineRef | null
  dose: string
  route: string
  frequency: string
  duration: string
  instructions: string
  quantity: string
}

const blankItem = (): ItemForm => ({
  medicine: null, dose: '', route: 'Oral', frequency: '', duration: '', instructions: '', quantity: '',
})

function RxEditor({
  appointmentId, rx, onClose, onSaved,
}: { appointmentId: number; rx: Rx | null; onClose: () => void; onSaved: () => void }) {
  const [notes, setNotes] = useState(rx?.notes ?? '')
  const [items, setItems] = useState<ItemForm[]>(
    rx
      ? rx.items.map((i) => ({
          medicine: { id: i.medicineId, name: i.medicineName, strength: i.strength, unit: i.unit, availableStock: null },
          dose: i.dose,
          route: i.route ?? '',
          frequency: i.frequency,
          duration: i.duration ?? '',
          instructions: i.instructions ?? '',
          quantity: String(i.qtyPrescribed),
        }))
      : [blankItem()],
  )
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  const update = (idx: number, patch: Partial<ItemForm>) =>
    setItems((list) => list.map((it, i) => (i === idx ? { ...it, ...patch } : it)))

  async function submit(e: FormEvent) {
    e.preventDefault()
    setError(null)
    const payload = []
    for (const [idx, it] of items.entries()) {
      const qty = Number(it.quantity)
      if (!it.medicine) { setError(`Choose a medicine for line ${idx + 1}.`); return }
      if (!it.dose.trim() || !it.frequency.trim()) { setError(`Line ${idx + 1} needs a dose and how often.`); return }
      if (!Number.isInteger(qty) || qty < 1) { setError(`Line ${idx + 1} needs a quantity of 1 or more.`); return }
      payload.push({
        medicineId: it.medicine.id,
        dose: it.dose.trim(),
        route: it.route.trim() || null,
        frequency: it.frequency.trim(),
        duration: it.duration.trim() || null,
        instructions: it.instructions.trim() || null,
        quantity: qty,
      })
    }
    setBusy(true)
    try {
      const body = { notes: notes.trim() || null, items: payload }
      if (rx) await api(`/api/prescriptions/${rx.id}`, { method: 'PUT', body })
      else await api(`/api/appointments/${appointmentId}/prescriptions`, { method: 'POST', body })
      onSaved()
    } catch (err) {
      setError(detailMsg(err))
    } finally {
      setBusy(false)
    }
  }

  return (
    <Modal title={rx ? 'Edit prescription' : 'New prescription'} onClose={onClose} wide>
      <form onSubmit={submit}>
        <ErrorBox message={error} />
        <div className="space-y-4">
          {items.map((it, idx) => (
            <div key={idx} className="rounded-lg border p-3">
              <div className="mb-2 flex items-center justify-between">
                <span className="text-sm font-medium">Medicine {idx + 1}</span>
                {items.length > 1 && (
                  <button type="button" className={`${btnSmall} text-red-700`} onClick={() => setItems((l) => l.filter((_, i) => i !== idx))}>
                    Remove
                  </button>
                )}
              </div>
              <MedicinePicker value={it.medicine} onChange={(m) => update(idx, { medicine: m })} />
              <div className="mt-3 grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
                <Field label="Dose"><input className={inputCls} placeholder="500 mg" value={it.dose} onChange={(e) => update(idx, { dose: e.target.value })} /></Field>
                <Field label="Route"><input className={inputCls} placeholder="Oral" value={it.route} onChange={(e) => update(idx, { route: e.target.value })} /></Field>
                <Field label="How often"><input className={inputCls} placeholder="Three times a day" value={it.frequency} onChange={(e) => update(idx, { frequency: e.target.value })} /></Field>
                <Field label="For how long"><input className={inputCls} placeholder="5 days" value={it.duration} onChange={(e) => update(idx, { duration: e.target.value })} /></Field>
                <Field label="Total quantity" className="lg:col-span-1">
                  <input className={inputCls} inputMode="numeric" placeholder="20" value={it.quantity} onChange={(e) => update(idx, { quantity: e.target.value })} />
                </Field>
                <Field label="Instructions" className="sm:col-span-2 lg:col-span-3">
                  <input className={inputCls} placeholder="After meals" value={it.instructions} onChange={(e) => update(idx, { instructions: e.target.value })} />
                </Field>
              </div>
            </div>
          ))}
        </div>
        <div className="mt-3">
          <button type="button" className={btnGhost} disabled={items.length >= 20} onClick={() => setItems((l) => [...l, blankItem()])}>
            Add another medicine
          </button>
        </div>
        <Field label="Notes for the pharmacist (optional)" className="mt-4">
          <input className={inputCls} value={notes} onChange={(e) => setNotes(e.target.value)} maxLength={500} />
        </Field>
        <div className="mt-5 flex justify-end gap-2">
          <button type="button" className={btnGhost} onClick={onClose}>Cancel</button>
          <button type="submit" className={btnPrimary} disabled={busy}>{busy ? 'Saving...' : 'Save as draft'}</button>
        </div>
      </form>
    </Modal>
  )
}

function CancelRxModal({ rx, onClose, onDone }: { rx: Rx; onClose: () => void; onDone: () => void }) {
  const [reason, setReason] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  async function submit(e: FormEvent) {
    e.preventDefault()
    setBusy(true)
    setError(null)
    try {
      await api(`/api/prescriptions/${rx.id}/cancel`, { method: 'POST', body: { reason } })
      onDone()
    } catch (err) {
      setError(detailMsg(err))
    } finally {
      setBusy(false)
    }
  }

  return (
    <Modal title="Cancel prescription" onClose={onClose}>
      <form onSubmit={submit}>
        <ErrorBox message={error} />
        <Field label="Reason (required)">
          <input className={inputCls} value={reason} onChange={(e) => setReason(e.target.value)} maxLength={255} required />
        </Field>
        <div className="mt-5 flex justify-end gap-2">
          <button type="button" className={btnGhost} onClick={onClose}>Back</button>
          <button type="submit" className={btnPrimary} disabled={busy || !reason.trim()}>{busy ? 'Saving...' : 'Cancel prescription'}</button>
        </div>
      </form>
    </Modal>
  )
}

function PrescriptionSection({ appt, role }: { appt: Appointment; role: Role | undefined }) {
  const [list, setList] = useState<Rx[] | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [reload, setReload] = useState(0)
  const [editor, setEditor] = useState<{ rx: Rx | null } | null>(null)
  const [cancelling, setCancelling] = useState<Rx | null>(null)
  const [busyId, setBusyId] = useState<number | null>(null)
  const isDoctor = role === 'DOCTOR'
  const canCreate = isDoctor && (appt.status === 'IN_PROGRESS' || appt.status === 'COMPLETED')

  useEffect(() => {
    let active = true
    api<Rx[]>(`/api/appointments/${appt.id}/prescriptions`)
      .then((r) => { if (active) setList(r) })
      .catch((e) => { if (active) setError(errMsg(e)) })
    return () => { active = false }
  }, [appt.id, reload])

  async function issue(rx: Rx) {
    if (!window.confirm('Issue this prescription to the pharmacy? It can no longer be edited.')) return
    setBusyId(rx.id)
    setError(null)
    try {
      await api(`/api/prescriptions/${rx.id}/issue`, { method: 'POST' })
      setReload((n) => n + 1)
    } catch (e) {
      setError(detailMsg(e))
    } finally {
      setBusyId(null)
    }
  }

  function done() {
    setEditor(null)
    setCancelling(null)
    setReload((n) => n + 1)
  }

  return (
    <Section title="Prescriptions">
      <ErrorBox message={error} />
      {canCreate && (
        <div className="mb-3">
          <button className={btnPrimary} onClick={() => setEditor({ rx: null })}>New prescription</button>
        </div>
      )}
      {isDoctor && !canCreate && <p className="mb-3 text-sm text-slate-500">Start the consultation to write a prescription.</p>}
      {!list && !error && <Spinner />}
      {list && list.length === 0 && <p className="text-sm text-slate-500">No prescriptions.</p>}
      <div className="space-y-3">
        {list?.map((rx) => (
          <div key={rx.id} className="rounded-lg border p-3 text-sm">
            <div className="mb-2 flex flex-wrap items-center justify-between gap-2">
              <span className={`rounded-full px-2 py-0.5 text-xs font-medium ${RX_STYLE[rx.status]}`}>{RX_LABEL[rx.status]}</span>
              {isDoctor && (
                <div className="flex flex-wrap gap-1">
                  {rx.status === 'DRAFT' && (
                    <>
                      <button className={btnSmall} onClick={() => setEditor({ rx })}>Edit</button>
                      <button className={btnSmall} disabled={busyId === rx.id} onClick={() => void issue(rx)}>Issue to pharmacy</button>
                    </>
                  )}
                  {(rx.status === 'DRAFT' || rx.status === 'ISSUED') && (
                    <button className={`${btnSmall} text-red-700`} onClick={() => setCancelling(rx)}>Cancel</button>
                  )}
                </div>
              )}
            </div>
            {rx.allergyWarnings.map((w) => (
              <p key={w} className="mb-2 rounded-md bg-amber-50 p-2 text-amber-900">⚠ {w}</p>
            ))}
            <div className="overflow-x-auto">
              <table className="w-full text-left">
                <thead className="text-xs text-slate-500">
                  <tr>
                    <th className="py-1 pr-3">Medicine</th>
                    <th className="py-1 pr-3">Dose</th>
                    <th className="py-1 pr-3">How often</th>
                    <th className="py-1 pr-3">Duration</th>
                    <th className="py-1">Qty (given)</th>
                  </tr>
                </thead>
                <tbody>
                  {rx.items.map((i) => (
                    <tr key={i.id} className="border-t">
                      <td className="py-1 pr-3">
                        {i.medicineName}{i.strength && ` ${i.strength}`}
                        {i.instructions && <div className="text-xs text-slate-500">{i.instructions}</div>}
                      </td>
                      <td className="py-1 pr-3">{i.dose}{i.route && ` (${i.route})`}</td>
                      <td className="py-1 pr-3">{i.frequency}</td>
                      <td className="py-1 pr-3">{i.duration}</td>
                      <td className="py-1">{i.qtyPrescribed} ({i.qtyDispensed})</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
            {rx.notes && <p className="mt-2 text-slate-600">Note: {rx.notes}</p>}
          </div>
        ))}
      </div>
      {editor && <RxEditor appointmentId={appt.id} rx={editor.rx} onClose={() => setEditor(null)} onSaved={done} />}
      {cancelling && <CancelRxModal rx={cancelling} onClose={() => setCancelling(null)} onDone={done} />}
    </Section>
  )
}

/* ---------------- laboratory (doctor) ---------------- */

function LabRequestModal({ appointmentId, onClose, onSaved }: { appointmentId: number; onClose: () => void; onSaved: () => void }) {
  const [tests, setTests] = useState<LabTest[] | null>(null)
  const [picked, setPicked] = useState<number[]>([])
  const [filter, setFilter] = useState('')
  const [priority, setPriority] = useState('ROUTINE')
  const [notes, setNotes] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  useEffect(() => {
    let active = true
    api<LabTest[]>('/api/lab/tests', { query: { activeOnly: true } })
      .then((r) => { if (active) setTests(r) })
      .catch((e) => { if (active) setError(detailMsg(e)) })
    return () => { active = false }
  }, [])

  const shown = (tests ?? []).filter((t) => t.name.toLowerCase().includes(filter.trim().toLowerCase()))

  async function submit(e: FormEvent) {
    e.preventDefault()
    setError(null)
    if (picked.length === 0) {
      setError('Choose at least one test.')
      return
    }
    setBusy(true)
    try {
      await api('/api/lab/requests', {
        method: 'POST',
        body: { appointmentId, testIds: picked, priority, clinicalNotes: notes.trim() || null },
      })
      onSaved()
    } catch (err) {
      setError(detailMsg(err))
    } finally {
      setBusy(false)
    }
  }

  return (
    <Modal title="Request laboratory tests" onClose={onClose} wide>
      <form onSubmit={submit}>
        <ErrorBox message={error} />
        <input className={inputCls} placeholder="Filter tests" value={filter} onChange={(e) => setFilter(e.target.value)} aria-label="Filter tests" />
        {!tests && !error && <Spinner />}
        <ul className="mt-2 grid max-h-64 gap-1 overflow-y-auto sm:grid-cols-2">
          {shown.map((t) => (
            <li key={t.id}>
              <label className="flex items-start gap-2 rounded border px-3 py-2 text-sm hover:bg-slate-50">
                <input
                  type="checkbox"
                  checked={picked.includes(t.id)}
                  onChange={(e) => setPicked((p) => (e.target.checked ? [...p, t.id] : p.filter((x) => x !== t.id)))}
                />
                <span>{t.name}<span className="block text-xs text-slate-500">{t.specimen}</span></span>
              </label>
            </li>
          ))}
        </ul>
        <div className="mt-4 grid gap-3 sm:grid-cols-3">
          <Field label="Priority">
            <select className={inputCls} value={priority} onChange={(e) => setPriority(e.target.value)}>
              <option value="ROUTINE">Routine</option>
              <option value="URGENT">Urgent</option>
            </select>
          </Field>
          <Field label="Note for the laboratory (optional)" className="sm:col-span-2">
            <input className={inputCls} maxLength={500} value={notes} onChange={(e) => setNotes(e.target.value)} />
          </Field>
        </div>
        <div className="mt-5 flex justify-end gap-2">
          <button type="button" className={btnGhost} onClick={onClose}>Cancel</button>
          <button type="submit" className={btnPrimary} disabled={busy}>{busy ? 'Sending...' : `Request ${picked.length || ''} test${picked.length === 1 ? '' : 's'}`}</button>
        </div>
      </form>
    </Modal>
  )
}

function LabSection({ appt, role }: { appt: Appointment; role: Role | undefined }) {
  const [list, setList] = useState<LabRequest[] | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [reload, setReload] = useState(0)
  const [open, setOpen] = useState(false)
  const canRequest = role === 'DOCTOR' && (appt.status === 'IN_PROGRESS' || appt.status === 'COMPLETED')

  useEffect(() => {
    let active = true
    api<LabRequest[]>(`/api/appointments/${appt.id}/lab-requests`)
      .then((r) => { if (active) setList(r) })
      .catch((e) => { if (active) setError(detailMsg(e)) })
    return () => { active = false }
  }, [appt.id, reload])

  async function cancel(r: LabRequest) {
    const reason = window.prompt('Reason for cancelling this lab request:')
    if (!reason || !reason.trim()) return
    setError(null)
    try {
      await api(`/api/lab/requests/${r.id}/cancel`, { method: 'POST', body: { reason: reason.trim() } })
      setReload((n) => n + 1)
    } catch (e) {
      setError(detailMsg(e))
    }
  }

  return (
    <Section title="Laboratory">
      <ErrorBox message={error} />
      {canRequest && <div className="mb-3"><button className={btnPrimary} onClick={() => setOpen(true)}>Request tests</button></div>}
      {role === 'DOCTOR' && !canRequest && <p className="mb-3 text-sm text-slate-500">Start the consultation to request tests.</p>}
      {!list && !error && <Spinner />}
      {list && list.length === 0 && <p className="text-sm text-slate-500">No laboratory requests.</p>}
      <div className="space-y-3">
        {list?.map((r) => (
          <div key={r.id} className="rounded-lg border p-3 text-sm">
            <div className="mb-2 flex flex-wrap items-center justify-between gap-2">
              <div className="flex items-center gap-2">
                <span className="font-medium">{r.requestNo}</span>
                <PriorityBadge priority={r.priority} />
                <LabStatusBadge status={r.status} />
              </div>
              {r.status === 'REQUESTED' && <button className={`${btnSmall} text-red-700`} onClick={() => void cancel(r)}>Cancel</button>}
            </div>
            {r.status === 'CANCELLED' && <p className="mb-2 text-slate-500">Cancelled: {r.cancelReason}</p>}
            {r.status === 'COLLECTED' && <p className="mb-2 text-slate-500">The sample has been collected. Results will appear here once the laboratory releases them.</p>}
            <ResultsTable items={r.items} showResults={r.status === 'VERIFIED'} />
            {r.status === 'VERIFIED' && r.verifiedAt && (
              <p className="mt-2 text-xs text-slate-500">Released {new Date(r.verifiedAt).toLocaleString()} by {r.verifiedByName}</p>
            )}
          </div>
        ))}
      </div>
      {open && <LabRequestModal appointmentId={appt.id} onClose={() => setOpen(false)} onSaved={() => { setOpen(false); setReload((n) => n + 1) }} />}
    </Section>
  )
}

/* ---------------- the page ---------------- */

export default function Visit() {
  const { id } = useParams()
  const { user } = useAuth()
  const role = user?.role
  const apptId = Number(id)

  const [appt, setAppt] = useState<Appointment | null>(null)
  const [patient, setPatient] = useState<Patient | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [actionError, setActionError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)
  const [reload, setReload] = useState(0)

  useEffect(() => {
    let active = true
    api<Appointment>(`/api/appointments/${apptId}`)
      .then(async (a) => {
        const p = await api<Patient>(`/api/patients/${a.patientId}`)
        if (active) {
          setAppt(a)
          setPatient(p)
          setError(null)
        }
      })
      .catch((e) => { if (active) setError(errMsg(e)) })
    return () => { active = false }
  }, [apptId, reload])

  async function changeStatus(status: string) {
    setBusy(true)
    setActionError(null)
    try {
      await api(`/api/appointments/${apptId}/status`, { method: 'PATCH', body: { status } })
      setReload((n) => n + 1)
    } catch (e) {
      setActionError(errMsg(e))
    } finally {
      setBusy(false)
    }
  }

  if (error) {
    return (
      <div>
        <Link to="/appointments" className="text-sm text-teal-700 hover:underline">← Appointments</Link>
        <div className="mt-3"><ErrorBox message={error} /></div>
      </div>
    )
  }
  if (!appt || !patient) return <Spinner />

  let next: { status: string; label: string } | null = null
  if (role === 'DOCTOR' && appt.status === 'CHECKED_IN') next = { status: 'IN_PROGRESS', label: 'Start consultation' }
  if (role === 'DOCTOR' && appt.status === 'IN_PROGRESS') next = { status: 'COMPLETED', label: 'Complete consultation' }

  return (
    <div>
      <Link to="/appointments" className="text-sm text-teal-700 hover:underline">← Appointments</Link>

      <div className="mb-4 mt-2 rounded-xl border bg-white p-4">
        <div className="flex flex-wrap items-start justify-between gap-3">
          <div>
            <h1 className="text-2xl font-semibold">{appt.patientName}</h1>
            <p className="text-sm text-slate-500">
              {appt.patientNo}
              {patient.dateOfBirth && ` · ${ageOf(patient.dateOfBirth)} years`}
              {patient.gender && ` · ${patient.gender.charAt(0) + patient.gender.slice(1).toLowerCase()}`}
            </p>
            <p className="mt-1 text-sm">
              {fmtDate(appt.date)} {fmtTime(appt.startTime)} · {appt.doctorName} · {appt.departmentName}
            </p>
            {appt.reason && <p className="text-sm text-slate-600">Reason: {appt.reason}</p>}
          </div>
          <div className="flex flex-col items-end gap-2">
            <StatusBadge status={appt.status} />
            {next && (
              <button className={btnPrimary} disabled={busy} onClick={() => void changeStatus(next.status)}>
                {busy ? 'Working...' : next.label}
              </button>
            )}
          </div>
        </div>
        <div className="mt-3">
          <ErrorBox message={actionError} />
          {patient.allergies && (
            <p className="rounded-md bg-red-50 p-3 text-sm text-red-800">
              <span className="font-semibold">Allergies:</span> {patient.allergies}
            </p>
          )}
        </div>
      </div>

      <VitalsSection appt={appt} role={role} />
      <ConsultationSection appt={appt} role={role} />
      <PrescriptionSection appt={appt} role={role} />
      {role === 'DOCTOR' && <LabSection appt={appt} role={role} />}
      <NursingNotesSection appt={appt} role={role} />
    </div>
  )
}