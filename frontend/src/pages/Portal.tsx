import { useEffect, useState } from 'react'
import type { FormEvent } from 'react'
import { Link } from 'react-router-dom'
import { api, ApiError } from '../api'
import { ResultsTable } from '../components/LabResults'
import type { LabRequest } from '../labTypes'
import type { Appointment, Page, Slot } from '../types'
import {
  ageOf, btnGhost, btnPrimary, btnSmall, detailMsg, ErrorBox, Field, fmtDate, fmtDateTime, fmtMoney, fmtTime,
  inputCls, Modal, Pagination, Spinner, StatusBadge, Tabs, todayStr,
} from '../ui'

/* ---------------- shared types ---------------- */

type RxStatus = 'DRAFT' | 'ISSUED' | 'PARTIALLY_DISPENSED' | 'DISPENSED' | 'CANCELLED'

interface RxItem {
  id: number
  medicineName: string
  strength: string | null
  dose: string
  route: string | null
  frequency: string
  duration: string | null
  instructions: string | null
  qtyPrescribed: number
  qtyDispensed: number
}

interface Rx {
  id: number
  status: RxStatus
  doctorName: string
  notes: string | null
  createdAt: string
  issuedAt: string | null
  items: RxItem[]
}

interface Home {
  fullName: string
  nextAppointment: Appointment | null
  recentPrescriptions: Rx[]
  outstandingBalance: number
}

interface Profile {
  id: number
  patientNo: string
  fullName: string
  dateOfBirth: string | null
  gender: string | null
  nic: string | null
  phone: string | null
  address: string | null
  emergencyContactName: string | null
  emergencyContactPhone: string | null
  allergies: string | null
}

interface DoctorOption {
  id: number
  name: string
  department: string
  specialty: string | null
  workDays: string
}

type InvStatus = 'UNPAID' | 'PARTIALLY_PAID' | 'PAID' | 'VOIDED'

interface InvoiceSummary {
  id: number
  invoiceNo: string
  total: number
  amountPaid: number
  balance: number
  status: InvStatus
  createdAt: string
}

interface InvoiceItem {
  id: number
  description: string
  quantity: number
  unitPrice: number
  lineTotal: number
}

interface PaymentRow {
  id: number
  receiptNo: string
  amount: number
  method: string
  paidAt: string
  refund: boolean
}

interface InvoiceDetail {
  id: number
  invoiceNo: string
  subtotal: number
  discount: number
  total: number
  amountPaid: number
  balance: number
  status: InvStatus
  createdAt: string
  items: InvoiceItem[]
  payments: PaymentRow[]
}

interface Receipt {
  receiptNo: string
  invoiceNo: string
  patientNo: string
  patientName: string
  amount: number
  method: string
  reference: string | null
  balanceAfter: number
  cashierName: string
  paidAt: string
  refund: boolean
}

const RX_LABEL: Record<RxStatus, string> = {
  DRAFT: 'Draft',
  ISSUED: 'Sent to the pharmacy',
  PARTIALLY_DISPENSED: 'Partly given',
  DISPENSED: 'Given',
  CANCELLED: 'Cancelled',
}

const RX_STYLE: Record<RxStatus, string> = {
  DRAFT: 'bg-slate-200 text-slate-700',
  ISSUED: 'bg-sky-100 text-sky-800',
  PARTIALLY_DISPENSED: 'bg-amber-100 text-amber-800',
  DISPENSED: 'bg-green-100 text-green-800',
  CANCELLED: 'bg-slate-200 text-slate-500',
}

const INV_STYLE: Record<InvStatus, string> = {
  UNPAID: 'bg-red-100 text-red-700',
  PARTIALLY_PAID: 'bg-amber-100 text-amber-800',
  PAID: 'bg-green-100 text-green-800',
  VOIDED: 'bg-slate-200 text-slate-600',
}

const INV_LABEL: Record<InvStatus, string> = {
  UNPAID: 'Unpaid',
  PARTIALLY_PAID: 'Partly paid',
  PAID: 'Paid',
  VOIDED: 'Cancelled',
}

const METHOD_LABEL: Record<string, string> = {
  CASH: 'Cash',
  CARD: 'Card',
  BANK_TRANSFER: 'Bank transfer',
  OTHER: 'Other',
}

function InvBadge({ status }: { status: InvStatus }) {
  return <span className={`rounded-full px-2 py-0.5 text-xs font-medium ${INV_STYLE[status]}`}>{INV_LABEL[status]}</span>
}

/* ---------------- prescriptions ---------------- */

function RxCard({ rx }: { rx: Rx }) {
  return (
    <div className="rounded-xl border bg-white p-4 text-sm">
      <div className="mb-2 flex flex-wrap items-center justify-between gap-2">
        <span className="font-medium">Prescribed by {rx.doctorName}</span>
        <span className="flex items-center gap-2">
          <span className="text-slate-500">{fmtDateTime(rx.createdAt)}</span>
          <span className={`rounded-full px-2 py-0.5 text-xs font-medium ${RX_STYLE[rx.status]}`}>{RX_LABEL[rx.status]}</span>
        </span>
      </div>
      <div className="overflow-x-auto">
        <table className="w-full text-left">
          <thead className="border-b text-xs text-slate-500">
            <tr>
              <th className="py-1 pr-3">Medicine</th>
              <th className="py-1 pr-3">How to take it</th>
              <th className="py-1">Quantity (given)</th>
            </tr>
          </thead>
          <tbody>
            {rx.items.map((i) => (
              <tr key={i.id} className="border-b align-top">
                <td className="py-1 pr-3">{i.medicineName}{i.strength && ` ${i.strength}`}</td>
                <td className="py-1 pr-3">
                  {i.dose}{i.route && ` (${i.route})`}, {i.frequency}{i.duration && `, for ${i.duration}`}
                  {i.instructions && <div className="text-xs text-slate-500">{i.instructions}</div>}
                </td>
                <td className="py-1">{i.qtyPrescribed} ({i.qtyDispensed})</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      {rx.notes && <p className="mt-2 text-slate-600">Note: {rx.notes}</p>}
    </div>
  )
}

export function PortalPrescriptions() {
  const [list, setList] = useState<Rx[] | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    let active = true
    api<Rx[]>('/api/portal/prescriptions')
      .then((r) => { if (active) setList(r) })
      .catch((e) => { if (active) setError(detailMsg(e)) })
    return () => { active = false }
  }, [])

  return (
    <div>
      <h1 className="mb-1 text-2xl font-semibold">My prescriptions</h1>
      <p className="mb-4 text-sm text-slate-500">Collect your medicines from the hospital pharmacy. Always follow your doctor's instructions.</p>
      <ErrorBox message={error} />
      {!list && !error && <Spinner />}
      {list && list.length === 0 && <p className="text-slate-500">You have no prescriptions yet.</p>}
      <div className="space-y-3">{list?.map((r) => <RxCard key={r.id} rx={r} />)}</div>
    </div>
  )
}

/* ---------------- home ---------------- */

export function PortalHome() {
  const [data, setData] = useState<Home | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    let active = true
    api<Home>('/api/portal/home')
      .then((r) => { if (active) setData(r) })
      .catch((e) => { if (active) setError(detailMsg(e)) })
    return () => { active = false }
  }, [])

  if (error) return <ErrorBox message={error} />
  if (!data) return <Spinner />
  const next = data.nextAppointment

  return (
    <div>
      <h1 className="mb-4 text-2xl font-semibold">Welcome, {data.fullName}</h1>
      <div className="grid gap-4 sm:grid-cols-2">
        <section className="rounded-xl border bg-white p-4">
          <h2 className="mb-2 text-sm font-medium text-slate-500">Your next appointment</h2>
          {next ? (
            <div className="text-sm">
              <div className="text-lg font-semibold">{fmtDate(next.date)} at {fmtTime(next.startTime)}</div>
              <div>{next.doctorName} · {next.departmentName}</div>
              {next.reason && <div className="text-slate-600">Reason: {next.reason}</div>}
              <div className="mt-2 flex items-center gap-2">
                <StatusBadge status={next.status} />
                {next.status === 'REQUESTED' && <span className="text-slate-500">Waiting for the hospital to confirm</span>}
              </div>
            </div>
          ) : (
            <p className="text-sm text-slate-500">You have no upcoming appointments.</p>
          )}
          <div className="mt-3">
            <Link to="/portal/appointments" className={`${btnPrimary} inline-block`}>{next ? 'My appointments' : 'Request an appointment'}</Link>
          </div>
        </section>

        <section className="rounded-xl border bg-white p-4">
          <h2 className="mb-2 text-sm font-medium text-slate-500">Amount to pay</h2>
          <div className="text-3xl font-semibold">{fmtMoney(data.outstandingBalance)}</div>
          <p className="mt-1 text-sm text-slate-500">
            {data.outstandingBalance > 0 ? 'You can pay at the hospital accounts counter.' : 'You have nothing to pay.'}
          </p>
          <div className="mt-3"><Link to="/portal/bills" className={`${btnGhost} inline-block`}>My bills</Link></div>
        </section>
      </div>

      <section className="mt-6">
        <div className="mb-2 flex items-center justify-between">
          <h2 className="text-lg font-medium">Recent prescriptions</h2>
          <Link to="/portal/prescriptions" className="text-sm text-teal-700 hover:underline">See all</Link>
        </div>
        {data.recentPrescriptions.length === 0 && <p className="text-sm text-slate-500">No prescriptions yet.</p>}
        <div className="space-y-3">{data.recentPrescriptions.map((r) => <RxCard key={r.id} rx={r} />)}</div>
      </section>
    </div>
  )
}

/* ---------------- appointments ---------------- */

function RequestModal({ onClose, onSaved }: { onClose: () => void; onSaved: () => void }) {
  const [doctors, setDoctors] = useState<DoctorOption[]>([])
  const [doctorId, setDoctorId] = useState('')
  const [date, setDate] = useState(todayStr())
  const [time, setTime] = useState('')
  const [reason, setReason] = useState('')
  const [slots, setSlots] = useState<Slot[] | null>(null)
  const [slotError, setSlotError] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  useEffect(() => {
    let active = true
    api<DoctorOption[]>('/api/portal/doctors')
      .then((r) => { if (active) setDoctors(r) })
      .catch((e) => { if (active) setError(detailMsg(e)) })
    return () => { active = false }
  }, [])

  useEffect(() => {
    let active = true
    if (!doctorId || !date) {
      setSlots(null)
      return
    }
    setSlotError(null)
    api<Slot[]>(`/api/portal/doctors/${doctorId}/slots`, { query: { date } })
      .then((r) => { if (active) setSlots(r) })
      .catch((e) => { if (active) { setSlots(null); setSlotError(detailMsg(e)) } })
    return () => { active = false }
  }, [doctorId, date])

  async function submit(e: FormEvent) {
    e.preventDefault()
    setError(null)
    if (!time) { setError('Choose a time.'); return }
    setBusy(true)
    try {
      await api('/api/portal/appointment-requests', {
        method: 'POST',
        body: { doctorId: Number(doctorId), date, time, reason: reason.trim() || null },
      })
      onSaved()
    } catch (err) {
      setError(detailMsg(err))
    } finally {
      setBusy(false)
    }
  }

  const chosen = doctors.find((d) => String(d.id) === doctorId)

  return (
    <Modal title="Request an appointment" onClose={onClose} wide>
      <form onSubmit={submit}>
        <ErrorBox message={error} />
        <div className="grid gap-3 sm:grid-cols-2">
          <Field label="Doctor">
            <select className={inputCls} value={doctorId} onChange={(e) => { setDoctorId(e.target.value); setTime('') }} required>
              <option value="">Select a doctor</option>
              {doctors.map((d) => <option key={d.id} value={d.id}>{d.name} ({d.department})</option>)}
            </select>
          </Field>
          <Field label="Date">
            <input type="date" min={todayStr()} className={inputCls} value={date} onChange={(e) => { setDate(e.target.value); setTime('') }} required />
          </Field>
          <Field label="Reason (optional)" className="sm:col-span-2">
            <input className={inputCls} maxLength={255} value={reason} onChange={(e) => setReason(e.target.value)} />
          </Field>
        </div>
        {chosen && (
          <p className="mt-2 text-xs text-slate-500">
            {chosen.specialty ? `${chosen.specialty}. ` : ''}Works on {chosen.workDays.split(',').map((d) => d.charAt(0) + d.slice(1).toLowerCase()).join(', ')}.
          </p>
        )}

        <div className="mt-4">
          <div className="mb-1 text-sm font-medium">Time</div>
          <ErrorBox message={slotError} />
          {!doctorId && <p className="text-sm text-slate-500">Choose a doctor and a date to see the free times.</p>}
          {slots && slots.length === 0 && <p className="text-sm text-slate-500">The doctor does not work on this day. Please try another date.</p>}
          {slots && slots.length > 0 && slots.every((s) => !s.available) && <p className="mb-2 text-sm text-amber-700">Every time on this day is taken or has passed.</p>}
          {slots && slots.length > 0 && (
            <div className="flex flex-wrap gap-2">
              {slots.map((s) => (
                <button
                  type="button"
                  key={s.time}
                  disabled={!s.available}
                  onClick={() => setTime(s.time)}
                  className={`rounded border px-2 py-1 text-sm ${
                    time === s.time ? 'border-teal-700 bg-teal-700 text-white' : s.available ? 'hover:bg-teal-50' : 'bg-slate-100 text-slate-400 line-through'
                  }`}
                >
                  {fmtTime(s.time)}
                </button>
              ))}
            </div>
          )}
        </div>

        <p className="mt-4 rounded-md bg-slate-50 p-2 text-xs text-slate-600">
          This is a request. The hospital will confirm it, and you will see the answer here and in your notifications.
        </p>
        <div className="mt-5 flex justify-end gap-2">
          <button type="button" className={btnGhost} onClick={onClose}>Cancel</button>
          <button type="submit" className={btnPrimary} disabled={busy || !doctorId || !time}>{busy ? 'Sending...' : 'Send request'}</button>
        </div>
      </form>
    </Modal>
  )
}

function CancelModal({ appointment, onClose, onDone }: { appointment: Appointment; onClose: () => void; onDone: () => void }) {
  const [reason, setReason] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  async function submit(e: FormEvent) {
    e.preventDefault()
    setBusy(true)
    setError(null)
    try {
      await api(`/api/portal/appointments/${appointment.id}/cancel`, { method: 'POST', body: { reason: reason.trim() || null } })
      onDone()
    } catch (err) {
      setError(detailMsg(err))
    } finally {
      setBusy(false)
    }
  }

  return (
    <Modal title="Cancel appointment" onClose={onClose}>
      <form onSubmit={submit}>
        <p className="mb-3 text-sm text-slate-600">
          {fmtDate(appointment.date)} at {fmtTime(appointment.startTime)} with {appointment.doctorName}
        </p>
        <ErrorBox message={error} />
        <Field label="Reason (optional)"><input className={inputCls} maxLength={255} value={reason} onChange={(e) => setReason(e.target.value)} /></Field>
        <div className="mt-5 flex justify-end gap-2">
          <button type="button" className={btnGhost} onClick={onClose}>Keep appointment</button>
          <button type="submit" className={btnPrimary} disabled={busy}>{busy ? 'Cancelling...' : 'Cancel appointment'}</button>
        </div>
      </form>
    </Modal>
  )
}

export function PortalAppointments() {
  const [scope, setScope] = useState<'upcoming' | 'past'>('upcoming')
  const [page, setPage] = useState(0)
  const [data, setData] = useState<Page<Appointment> | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [notice, setNotice] = useState<string | null>(null)
  const [reload, setReload] = useState(0)
  const [requesting, setRequesting] = useState(false)
  const [cancelling, setCancelling] = useState<Appointment | null>(null)
  const today = todayStr()

  useEffect(() => {
    let active = true
    api<Page<Appointment>>('/api/portal/appointments', { query: { scope, page, size: 10 } })
      .then((r) => { if (active) { setData(r); setError(null) } })
      .catch((e) => { if (active) setError(detailMsg(e)) })
    return () => { active = false }
  }, [scope, page, reload])

  return (
    <div>
      <div className="mb-4 flex flex-wrap items-center justify-between gap-3">
        <h1 className="text-2xl font-semibold">My appointments</h1>
        <button className={btnPrimary} onClick={() => { setNotice(null); setRequesting(true) }}>Request an appointment</button>
      </div>
      {notice && <p className="mb-3 rounded-md bg-green-50 p-3 text-sm text-green-800">{notice}</p>}
      <Tabs
        tabs={[{ key: 'upcoming', label: 'Upcoming' }, { key: 'past', label: 'Past' }]}
        value={scope}
        onChange={(k) => { setScope(k); setPage(0) }}
      />
      <ErrorBox message={error} />
      {!data && !error ? (
        <Spinner />
      ) : (
        <div className="overflow-x-auto rounded-xl border bg-white">
          <table className="w-full text-left text-sm">
            <thead className="bg-slate-50 text-slate-500">
              <tr>
                <th className="px-3 py-2">When</th>
                <th className="px-3 py-2">Doctor</th>
                <th className="px-3 py-2">Reason</th>
                <th className="px-3 py-2">Status</th>
                <th className="px-3 py-2"> </th>
              </tr>
            </thead>
            <tbody>
              {data?.content.length === 0 && (
                <tr><td colSpan={5} className="px-3 py-6 text-center text-slate-500">{scope === 'upcoming' ? 'You have no upcoming appointments.' : 'No past appointments.'}</td></tr>
              )}
              {data?.content.map((a) => {
                const cancellable = (a.status === 'REQUESTED' || a.status === 'SCHEDULED') && a.date > today
                const sameDay = (a.status === 'REQUESTED' || a.status === 'SCHEDULED') && a.date === today
                return (
                  <tr key={a.id} className="border-t align-top">
                    <td className="px-3 py-2 whitespace-nowrap">{fmtDate(a.date)}<div className="text-slate-500">{fmtTime(a.startTime)}</div></td>
                    <td className="px-3 py-2">{a.doctorName}<div className="text-xs text-slate-400">{a.departmentName}</div></td>
                    <td className="px-3 py-2">{a.reason}{a.cancelReason && <div className="text-xs text-slate-400">Cancelled: {a.cancelReason}</div>}</td>
                    <td className="px-3 py-2">
                      <StatusBadge status={a.status} />
                      {a.status === 'REQUESTED' && <div className="mt-1 text-xs text-slate-500">Waiting for confirmation</div>}
                    </td>
                    <td className="px-3 py-2">
                      {cancellable && <button className={btnSmall} onClick={() => setCancelling(a)}>Cancel</button>}
                      {sameDay && <span className="text-xs text-slate-500">To cancel today, please call the hospital</span>}
                    </td>
                  </tr>
                )
              })}
            </tbody>
          </table>
        </div>
      )}
      {data && <Pagination page={data.page} totalPages={data.totalPages} onPage={setPage} />}

      {requesting && (
        <RequestModal
          onClose={() => setRequesting(false)}
          onSaved={() => {
            setRequesting(false)
            setNotice('Your request has been sent. The hospital will confirm it, and you will be notified.')
            setScope('upcoming')
            setPage(0)
            setReload((n) => n + 1)
          }}
        />
      )}
      {cancelling && (
        <CancelModal
          appointment={cancelling}
          onClose={() => setCancelling(null)}
          onDone={() => { setCancelling(null); setNotice('Your appointment has been cancelled.'); setReload((n) => n + 1) }}
        />
      )}
    </div>
  )
}

/* ---------------- lab results ---------------- */

function ReportModal({ req, onClose }: { req: LabRequest; onClose: () => void }) {
  return (
    <Modal title={`Lab report ${req.requestNo}`} onClose={onClose} wide>
      <div className="print-area text-sm">
        <div className="mb-3">
          <div className="text-lg font-semibold">CareSync Hospital: Laboratory report</div>
          <div>{req.patientName} ({req.patientNo})</div>
          <div className="text-slate-600">Requested by {req.requestedByName}</div>
          {req.collectedAt && <div className="text-slate-600">Sample collected {fmtDateTime(req.collectedAt)}</div>}
          {req.verifiedAt && <div className="text-slate-600">Released {fmtDateTime(req.verifiedAt)}</div>}
        </div>
        <ResultsTable items={req.items} showResults />
        <p className="mt-3 text-xs text-slate-500">Results should be explained by your doctor. A mark of Low or High only compares the result with the usual range.</p>
      </div>
      <div className="no-print mt-5 flex justify-end gap-2">
        <button className={btnGhost} onClick={onClose}>Close</button>
        <button className={btnPrimary} onClick={() => window.print()}>Print</button>
      </div>
    </Modal>
  )
}

export function PortalLab() {
  const [list, setList] = useState<LabRequest[] | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [open, setOpen] = useState<LabRequest | null>(null)

  useEffect(() => {
    let active = true
    api<LabRequest[]>('/api/portal/lab-results')
      .then((r) => { if (active) setList(r) })
      .catch((e) => { if (active) setError(detailMsg(e)) })
    return () => { active = false }
  }, [])

  return (
    <div>
      <h1 className="mb-1 text-2xl font-semibold">My lab results</h1>
      <p className="mb-4 text-sm text-slate-500">Only results that the laboratory has checked and released appear here. Please discuss them with your doctor.</p>
      <ErrorBox message={error} />
      {!list && !error && <Spinner />}
      {list && list.length === 0 && <p className="text-slate-500">You have no released results yet.</p>}
      <div className="space-y-3">
        {list?.map((r) => (
          <div key={r.id} className="rounded-xl border bg-white p-4 text-sm">
            <div className="mb-2 flex flex-wrap items-center justify-between gap-2">
              <span className="font-medium">{r.requestNo} · requested by {r.requestedByName}</span>
              <span className="flex items-center gap-2">
                <span className="text-slate-500">Released {fmtDateTime(r.verifiedAt)}</span>
                <button className={btnSmall} onClick={() => setOpen(r)}>View and print</button>
              </span>
            </div>
            <ResultsTable items={r.items} showResults />
          </div>
        ))}
      </div>
      {open && <ReportModal req={open} onClose={() => setOpen(null)} />}
    </div>
  )
}

/* ---------------- bills ---------------- */

function ReceiptModal({ paymentId, onClose }: { paymentId: number; onClose: () => void }) {
  const [r, setR] = useState<Receipt | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    let active = true
    api<Receipt>(`/api/portal/payments/${paymentId}/receipt`)
      .then((x) => { if (active) setR(x) })
      .catch((e) => { if (active) setError(detailMsg(e)) })
    return () => { active = false }
  }, [paymentId])

  return (
    <Modal title={r?.refund ? 'Refund receipt' : 'Payment receipt'} onClose={onClose}>
      <ErrorBox message={error} />
      {!r && !error && <Spinner />}
      {r && (
        <>
          <div className="print-area text-sm">
            <div className="mb-3 text-center">
              <div className="text-lg font-semibold">CareSync Hospital</div>
              <div>{r.refund ? 'REFUND RECEIPT' : 'PAYMENT RECEIPT'}</div>
            </div>
            <dl className="grid grid-cols-[9rem_1fr] gap-y-1">
              <dt className="text-slate-500">Receipt no.</dt><dd className="font-medium">{r.receiptNo}</dd>
              <dt className="text-slate-500">Date</dt><dd>{fmtDateTime(r.paidAt)}</dd>
              <dt className="text-slate-500">Patient</dt><dd>{r.patientName} ({r.patientNo})</dd>
              <dt className="text-slate-500">Invoice</dt><dd>{r.invoiceNo}</dd>
              <dt className="text-slate-500">{r.refund ? 'Amount refunded' : 'Amount paid'}</dt>
              <dd className="text-lg font-semibold">{fmtMoney(Math.abs(r.amount))}</dd>
              <dt className="text-slate-500">Method</dt><dd>{METHOD_LABEL[r.method] ?? r.method}</dd>
              {r.reference && (<><dt className="text-slate-500">Reference</dt><dd>{r.reference}</dd></>)}
              <dt className="text-slate-500">Balance after</dt><dd>{fmtMoney(r.balanceAfter)}</dd>
              <dt className="text-slate-500">Received by</dt><dd>{r.cashierName}</dd>
            </dl>
            <p className="mt-4 text-center text-xs text-slate-500">Thank you.</p>
          </div>
          <div className="no-print mt-4 flex justify-end gap-2">
            <button className={btnGhost} onClick={onClose}>Close</button>
            <button className={btnPrimary} onClick={() => window.print()}>Print</button>
          </div>
        </>
      )}
    </Modal>
  )
}

function InvoiceModal({ id, onClose }: { id: number; onClose: () => void }) {
  const [inv, setInv] = useState<InvoiceDetail | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [receiptId, setReceiptId] = useState<number | null>(null)

  useEffect(() => {
    let active = true
    api<InvoiceDetail>(`/api/portal/invoices/${id}`)
      .then((r) => { if (active) setInv(r) })
      .catch((e) => { if (active) setError(detailMsg(e)) })
    return () => { active = false }
  }, [id])

  return (
    <>
      <Modal title={`Invoice ${inv?.invoiceNo ?? ''}`} onClose={onClose} wide>
        <ErrorBox message={error} />
        {!inv && !error && <Spinner />}
        {inv && (
          <>
            <div className="print-area text-sm">
              <div className="mb-3 flex flex-wrap items-start justify-between gap-2">
                <div>
                  <div className="text-lg font-semibold">CareSync Hospital</div>
                  <div className="text-slate-600">Invoice {inv.invoiceNo} · {fmtDateTime(inv.createdAt)}</div>
                </div>
                <InvBadge status={inv.status} />
              </div>
              {inv.status === 'VOIDED' && <p className="mb-2 rounded bg-slate-100 p-2">This invoice was cancelled by the hospital. You do not need to pay it.</p>}
              <table className="mb-3 w-full text-left">
                <thead className="border-b text-xs text-slate-500">
                  <tr>
                    <th className="py-1 pr-2">Item</th>
                    <th className="py-1 pr-2">Qty</th>
                    <th className="py-1 pr-2 text-right">Unit price</th>
                    <th className="py-1 text-right">Amount</th>
                  </tr>
                </thead>
                <tbody>
                  {inv.items.map((i) => (
                    <tr key={i.id} className="border-b">
                      <td className="py-1 pr-2">{i.description}</td>
                      <td className="py-1 pr-2">{i.quantity}</td>
                      <td className="py-1 pr-2 text-right">{fmtMoney(i.unitPrice)}</td>
                      <td className="py-1 text-right">{fmtMoney(i.lineTotal)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
              <dl className="ml-auto grid max-w-xs grid-cols-2 gap-y-1">
                <dt className="text-slate-500">Subtotal</dt><dd className="text-right">{fmtMoney(inv.subtotal)}</dd>
                {inv.discount > 0 && (<><dt className="text-slate-500">Discount</dt><dd className="text-right">-{fmtMoney(inv.discount)}</dd></>)}
                <dt className="font-medium">Total</dt><dd className="text-right font-medium">{fmtMoney(inv.total)}</dd>
                <dt className="text-slate-500">Paid</dt><dd className="text-right">{fmtMoney(inv.amountPaid)}</dd>
                <dt className="font-medium">Balance</dt><dd className="text-right font-medium">{fmtMoney(inv.balance)}</dd>
              </dl>
              {inv.payments.length > 0 && (
                <div className="mt-4">
                  <h3 className="mb-1 font-medium">Payments</h3>
                  <table className="w-full text-left">
                    <thead className="border-b text-xs text-slate-500">
                      <tr>
                        <th className="py-1 pr-2">Receipt</th>
                        <th className="py-1 pr-2">Date</th>
                        <th className="py-1 pr-2">Method</th>
                        <th className="py-1 pr-2 text-right">Amount</th>
                        <th className="no-print py-1"> </th>
                      </tr>
                    </thead>
                    <tbody>
                      {inv.payments.map((p) => (
                        <tr key={p.id} className="border-b">
                          <td className="py-1 pr-2">{p.receiptNo}</td>
                          <td className="py-1 pr-2">{fmtDateTime(p.paidAt)}</td>
                          <td className="py-1 pr-2">{METHOD_LABEL[p.method] ?? p.method}</td>
                          <td className={`py-1 pr-2 text-right ${p.refund ? 'text-red-700' : ''}`}>{fmtMoney(p.amount)}</td>
                          <td className="no-print py-1 text-right"><button className={btnSmall} onClick={() => setReceiptId(p.id)}>Receipt</button></td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              )}
            </div>
            {inv.status !== 'VOIDED' && inv.balance > 0 && (
              <p className="no-print mt-3 rounded-md bg-amber-50 p-2 text-sm text-amber-900">
                To pay, please visit the hospital accounts counter. Online payment is not available.
              </p>
            )}
            <div className="no-print mt-5 flex justify-end gap-2">
              <button className={btnGhost} onClick={onClose}>Close</button>
              <button className={btnPrimary} onClick={() => window.print()}>Print invoice</button>
            </div>
          </>
        )}
      </Modal>
      {receiptId !== null && <ReceiptModal paymentId={receiptId} onClose={() => setReceiptId(null)} />}
    </>
  )
}

export function PortalBills() {
  const [page, setPage] = useState(0)
  const [data, setData] = useState<Page<InvoiceSummary> | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [openId, setOpenId] = useState<number | null>(null)

  useEffect(() => {
    let active = true
    api<Page<InvoiceSummary>>('/api/portal/invoices', { query: { page, size: 10 } })
      .then((r) => { if (active) { setData(r); setError(null) } })
      .catch((e) => { if (active) setError(detailMsg(e)) })
    return () => { active = false }
  }, [page])

  return (
    <div>
      <h1 className="mb-1 text-2xl font-semibold">My bills</h1>
      <p className="mb-4 text-sm text-slate-500">Payments are made at the hospital accounts counter.</p>
      <ErrorBox message={error} />
      {!data && !error ? (
        <Spinner />
      ) : (
        <div className="overflow-x-auto rounded-xl border bg-white">
          <table className="w-full text-left text-sm">
            <thead className="bg-slate-50 text-slate-500">
              <tr>
                <th className="px-3 py-2">Invoice</th>
                <th className="px-3 py-2">Date</th>
                <th className="px-3 py-2 text-right">Total</th>
                <th className="px-3 py-2 text-right">Balance</th>
                <th className="px-3 py-2">Status</th>
                <th className="px-3 py-2"> </th>
              </tr>
            </thead>
            <tbody>
              {data?.content.length === 0 && <tr><td colSpan={6} className="px-3 py-6 text-center text-slate-500">You have no bills yet.</td></tr>}
              {data?.content.map((i) => (
                <tr key={i.id} className="border-t">
                  <td className="px-3 py-2 font-medium">{i.invoiceNo}</td>
                  <td className="px-3 py-2 whitespace-nowrap">{fmtDate(i.createdAt.slice(0, 10))}</td>
                  <td className="px-3 py-2 text-right">{fmtMoney(i.total)}</td>
                  <td className="px-3 py-2 text-right">{i.status === 'VOIDED' ? '' : fmtMoney(i.balance)}</td>
                  <td className="px-3 py-2"><InvBadge status={i.status} /></td>
                  <td className="px-3 py-2"><button className={btnSmall} onClick={() => setOpenId(i.id)}>View</button></td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
      {data && <Pagination page={data.page} totalPages={data.totalPages} onPage={setPage} />}
      {openId !== null && <InvoiceModal id={openId} onClose={() => setOpenId(null)} />}
    </div>
  )
}

/* ---------------- my details ---------------- */

export function PortalProfile() {
  const [p, setP] = useState<Profile | null>(null)
  const [f, setF] = useState({ phone: '', address: '', emergencyContactName: '', emergencyContactPhone: '' })
  const [error, setError] = useState<string | null>(null)
  const [fe, setFe] = useState<Record<string, string>>({})
  const [saved, setSaved] = useState(false)
  const [busy, setBusy] = useState(false)

  useEffect(() => {
    let active = true
    api<Profile>('/api/portal/me')
      .then((r) => {
        if (!active) return
        setP(r)
        setF({
          phone: r.phone ?? '',
          address: r.address ?? '',
          emergencyContactName: r.emergencyContactName ?? '',
          emergencyContactPhone: r.emergencyContactPhone ?? '',
        })
      })
      .catch((e) => { if (active) setError(detailMsg(e)) })
    return () => { active = false }
  }, [])

  const text = (k: keyof typeof f) => (e: { target: { value: string } }) => {
    setSaved(false)
    setF((prev) => ({ ...prev, [k]: e.target.value }))
  }

  async function submit(e: FormEvent) {
    e.preventDefault()
    setError(null)
    setFe({})
    setSaved(false)
    setBusy(true)
    try {
      const r = await api<Profile>('/api/portal/me', {
        method: 'PUT',
        body: {
          phone: f.phone.trim(),
          address: f.address.trim() || null,
          emergencyContactName: f.emergencyContactName.trim() || null,
          emergencyContactPhone: f.emergencyContactPhone.trim() || null,
        },
      })
      setP(r)
      setSaved(true)
    } catch (err) {
      setError(detailMsg(err))
      setFe(err instanceof ApiError && err.fieldErrors ? err.fieldErrors : {})
    } finally {
      setBusy(false)
    }
  }

  if (!p && !error) return <Spinner />

  return (
    <div className="max-w-2xl">
      <h1 className="mb-4 text-2xl font-semibold">My details</h1>
      <ErrorBox message={error} />
      {p && (
        <>
          <section className="mb-5 rounded-xl border bg-white p-4">
            <h2 className="mb-2 text-lg font-medium">Your record</h2>
            <dl className="grid gap-x-4 gap-y-1 text-sm sm:grid-cols-[10rem_1fr]">
              <dt className="text-slate-500">Patient number</dt><dd className="font-medium">{p.patientNo}</dd>
              <dt className="text-slate-500">Name</dt><dd>{p.fullName}</dd>
              <dt className="text-slate-500">Date of birth</dt><dd>{p.dateOfBirth ? `${fmtDate(p.dateOfBirth)} (${ageOf(p.dateOfBirth)} years)` : ''}</dd>
              <dt className="text-slate-500">Gender</dt><dd>{p.gender ? p.gender.charAt(0) + p.gender.slice(1).toLowerCase() : ''}</dd>
              <dt className="text-slate-500">NIC</dt><dd>{p.nic}</dd>
              <dt className="text-slate-500">Allergies</dt><dd>{p.allergies || 'None recorded'}</dd>
            </dl>
            <p className="mt-3 text-xs text-slate-500">To change your name, date of birth, NIC or allergies, please contact the hospital reception.</p>
          </section>

          <form onSubmit={submit} className="rounded-xl border bg-white p-4">
            <h2 className="mb-2 text-lg font-medium">Contact details</h2>
            {saved && <p className="mb-3 rounded-md bg-green-50 p-2 text-sm text-green-800">Your details have been saved.</p>}
            <div className="grid gap-3 sm:grid-cols-2">
              <Field label="Phone" error={fe.phone}><input className={inputCls} placeholder="0771234567" value={f.phone} onChange={text('phone')} required /></Field>
              <Field label="Address" error={fe.address}><input className={inputCls} maxLength={255} value={f.address} onChange={text('address')} /></Field>
              <Field label="Emergency contact name" error={fe.emergencyContactName}><input className={inputCls} maxLength={120} value={f.emergencyContactName} onChange={text('emergencyContactName')} /></Field>
              <Field label="Emergency contact phone" error={fe.emergencyContactPhone}><input className={inputCls} value={f.emergencyContactPhone} onChange={text('emergencyContactPhone')} /></Field>
            </div>
            <div className="mt-4"><button type="submit" className={btnPrimary} disabled={busy}>{busy ? 'Saving...' : 'Save'}</button></div>
          </form>
        </>
      )}
    </div>
  )
}