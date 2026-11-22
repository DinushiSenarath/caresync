import { useEffect, useState } from 'react'
import type { FormEvent } from 'react'
import { useLocation, useParams } from 'react-router-dom'
import { api } from '../api'
import { useAuth } from '../auth'
import type { Role } from '../auth'
import type { Page } from '../types'
import {
  btnGhost, btnPrimary, btnSmall, detailMsg, ErrorBox, Field, fmtDate, fmtDateTime, fmtMoney,
  fmtTime, inputCls, Modal, Pagination, Spinner, Tabs,
} from '../ui'

type InvStatus = 'UNPAID' | 'PARTIALLY_PAID' | 'PAID' | 'VOIDED'
type AdjType = 'DISCOUNT' | 'VOID' | 'REFUND'

interface InvoiceSummary {
  id: number
  invoiceNo: string
  patientNo: string
  patientName: string
  total: number
  amountPaid: number
  balance: number
  status: InvStatus
  createdAt: string
}

interface InvoiceItem {
  id: number
  itemType: string
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
  reference: string | null
  balanceAfter: number
  cashierName: string
  paidAt: string
  refund: boolean
}

interface InvoiceDetail {
  id: number
  invoiceNo: string
  appointmentId: number
  patientNo: string
  patientName: string
  subtotal: number
  discount: number
  total: number
  amountPaid: number
  balance: number
  status: InvStatus
  voidReason: string | null
  createdAt: string
  items: InvoiceItem[]
  payments: PaymentRow[]
}

interface Adjustment {
  id: number
  invoiceId: number
  invoiceNo: string
  patientName: string
  type: AdjType
  amount: number
  refundMethod: string | null
  reason: string
  status: 'PENDING' | 'APPROVED' | 'REJECTED'
  requestedByName: string
  decidedByName: string | null
  decisionNote: string | null
  createdAt: string
}

interface ServiceCharge {
  id: number
  name: string
  category: string
  price: number
  active: boolean
}

interface Billable {
  appointmentId: number
  date: string
  startTime: string
  patientNo: string
  patientName: string
  doctorName: string
  status: string
  pendingLabRequests: number
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

interface BillableAdmission {
  admissionId: number
  admissionNo: string
  patientNo: string
  patientName: string
  doctorName: string
  admittedAt: string
  dischargedAt: string
}

const METHODS: { value: string; label: string }[] = [
  { value: 'CASH', label: 'Cash' },
  { value: 'CARD', label: 'Card' },
  { value: 'BANK_TRANSFER', label: 'Bank transfer' },
  { value: 'OTHER', label: 'Other' },
]
const methodLabel = (m: string) => METHODS.find((x) => x.value === m)?.label ?? m
const nice = (s: string) => s.charAt(0) + s.slice(1).toLowerCase().replace('_', ' ')

const INV_STYLE: Record<InvStatus, string> = {
  UNPAID: 'bg-red-100 text-red-700',
  PARTIALLY_PAID: 'bg-amber-100 text-amber-800',
  PAID: 'bg-green-100 text-green-800',
  VOIDED: 'bg-slate-200 text-slate-600',
}

function InvoiceBadge({ status }: { status: InvStatus }) {
  return <span className={`rounded-full px-2 py-0.5 text-xs font-medium ${INV_STYLE[status]}`}>{nice(status)}</span>
}

const ADJ_STYLE: Record<Adjustment['status'], string> = {
  PENDING: 'bg-violet-100 text-violet-800',
  APPROVED: 'bg-green-100 text-green-800',
  REJECTED: 'bg-slate-200 text-slate-600',
}

/* ---------------- receipt ---------------- */

function ReceiptModal({ paymentId, onClose }: { paymentId: number; onClose: () => void }) {
  const [r, setR] = useState<Receipt | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    let active = true
    api<Receipt>(`/api/payments/${paymentId}/receipt`)
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
              <dt className="text-slate-500">Method</dt><dd>{methodLabel(r.method)}</dd>
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

/* ---------------- payment and adjustment dialogs ---------------- */

function PaymentModal({ inv, onClose, onDone }: { inv: InvoiceDetail; onClose: () => void; onDone: () => void }) {
  const [amount, setAmount] = useState(inv.balance.toFixed(2))
  const [method, setMethod] = useState('CASH')
  const [reference, setReference] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  async function submit(e: FormEvent) {
    e.preventDefault()
    setError(null)
    const n = Number(amount)
    if (Number.isNaN(n) || n <= 0) { setError('Enter an amount above zero.'); return }
    setBusy(true)
    try {
      await api(`/api/invoices/${inv.id}/payments`, {
        method: 'POST',
        body: { amount: n, method, reference: reference.trim() || null },
      })
      onDone()
    } catch (err) {
      setError(detailMsg(err))
    } finally {
      setBusy(false)
    }
  }

  return (
    <Modal title="Record payment" onClose={onClose}>
      <form onSubmit={submit}>
        <p className="mb-3 text-sm text-slate-600">{inv.invoiceNo} · outstanding {fmtMoney(inv.balance)}</p>
        <ErrorBox message={error} />
        <div className="grid gap-3">
          <Field label="Amount (LKR)"><input className={inputCls} inputMode="decimal" value={amount} onChange={(e) => setAmount(e.target.value)} /></Field>
          <Field label="Method">
            <select className={inputCls} value={method} onChange={(e) => setMethod(e.target.value)}>
              {METHODS.map((m) => <option key={m.value} value={m.value}>{m.label}</option>)}
            </select>
          </Field>
          <Field label="Reference (optional)"><input className={inputCls} placeholder="Card slip or transfer number" value={reference} onChange={(e) => setReference(e.target.value)} maxLength={100} /></Field>
        </div>
        <div className="mt-5 flex justify-end gap-2">
          <button type="button" className={btnGhost} onClick={onClose}>Cancel</button>
          <button type="submit" className={btnPrimary} disabled={busy}>{busy ? 'Saving...' : 'Record payment'}</button>
        </div>
      </form>
    </Modal>
  )
}

function AdjustmentRequestModal({ inv, onClose, onDone }: { inv: InvoiceDetail; onClose: () => void; onDone: () => void }) {
  const options: { value: AdjType; label: string }[] = []
  if (inv.balance > 0) options.push({ value: 'DISCOUNT', label: 'Discount' })
  if (inv.amountPaid === 0) options.push({ value: 'VOID', label: 'Void the invoice' })
  if (inv.amountPaid > 0) options.push({ value: 'REFUND', label: 'Refund' })

  const [type, setType] = useState<AdjType>(options[0]?.value ?? 'DISCOUNT')
  const [amount, setAmount] = useState('')
  const [method, setMethod] = useState('CASH')
  const [reason, setReason] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  async function submit(e: FormEvent) {
    e.preventDefault()
    setError(null)
    let n: number | undefined
    if (type !== 'VOID') {
      n = Number(amount)
      if (Number.isNaN(n) || n <= 0) { setError('Enter an amount above zero.'); return }
    }
    setBusy(true)
    try {
      await api(`/api/invoices/${inv.id}/adjustments`, {
        method: 'POST',
        body: { type, amount: n, refundMethod: type === 'REFUND' ? method : undefined, reason: reason.trim() },
      })
      onDone()
    } catch (err) {
      setError(detailMsg(err))
    } finally {
      setBusy(false)
    }
  }

  return (
    <Modal title="Request approval" onClose={onClose}>
      {options.length === 0 ? (
        <p className="text-sm text-slate-600">There is nothing to adjust on this invoice.</p>
      ) : (
        <form onSubmit={submit}>
          <p className="mb-3 text-sm text-slate-600">
            {inv.invoiceNo} · total {fmtMoney(inv.total)} · paid {fmtMoney(inv.amountPaid)}. The Administrator must approve this request.
          </p>
          <ErrorBox message={error} />
          <div className="grid gap-3">
            <Field label="What do you need?">
              <select className={inputCls} value={type} onChange={(e) => setType(e.target.value as AdjType)}>
                {options.map((o) => <option key={o.value} value={o.value}>{o.label}</option>)}
              </select>
            </Field>
            {type !== 'VOID' && (
              <Field label="Amount (LKR)"><input className={inputCls} inputMode="decimal" value={amount} onChange={(e) => setAmount(e.target.value)} /></Field>
            )}
            {type === 'REFUND' && (
              <Field label="Refund method">
                <select className={inputCls} value={method} onChange={(e) => setMethod(e.target.value)}>
                  {METHODS.map((m) => <option key={m.value} value={m.value}>{m.label}</option>)}
                </select>
              </Field>
            )}
            <Field label="Reason (required)"><input className={inputCls} value={reason} onChange={(e) => setReason(e.target.value)} maxLength={255} required /></Field>
          </div>
          <div className="mt-5 flex justify-end gap-2">
            <button type="button" className={btnGhost} onClick={onClose}>Cancel</button>
            <button type="submit" className={btnPrimary} disabled={busy || !reason.trim()}>{busy ? 'Sending...' : 'Send request'}</button>
          </div>
        </form>
      )}
      {options.length === 0 && <div className="mt-5 flex justify-end"><button className={btnGhost} onClick={onClose}>Close</button></div>}
    </Modal>
  )
}

function DecisionModal({
  adj, approve, onClose, onDone,
}: { adj: Adjustment; approve: boolean; onClose: () => void; onDone: () => void }) {
  const [note, setNote] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  async function submit(e: FormEvent) {
    e.preventDefault()
    setBusy(true)
    setError(null)
    try {
      await api(`/api/adjustments/${adj.id}/decision`, { method: 'POST', body: { approve, note: note.trim() || null } })
      onDone()
    } catch (err) {
      setError(detailMsg(err))
    } finally {
      setBusy(false)
    }
  }

  return (
    <Modal title={approve ? 'Approve request' : 'Reject request'} onClose={onClose}>
      <form onSubmit={submit}>
        <p className="mb-3 text-sm text-slate-600">
          {nice(adj.type)} {adj.type !== 'VOID' && fmtMoney(adj.amount)} on {adj.invoiceNo} ({adj.patientName}). Reason given: {adj.reason}
        </p>
        <ErrorBox message={error} />
        <Field label={approve ? 'Note (optional)' : 'Reason for rejecting (required)'}>
          <input className={inputCls} value={note} onChange={(e) => setNote(e.target.value)} maxLength={255} required={!approve} />
        </Field>
        <div className="mt-5 flex justify-end gap-2">
          <button type="button" className={btnGhost} onClick={onClose}>Back</button>
          <button type="submit" className={btnPrimary} disabled={busy || (!approve && !note.trim())}>
            {busy ? 'Saving...' : approve ? 'Approve' : 'Reject'}
          </button>
        </div>
      </form>
    </Modal>
  )
}

/* ---------------- invoice detail ---------------- */

function InvoiceModal({
  id, role, onClose, onChanged,
}: { id: number; role: Role | undefined; onClose: () => void; onChanged: () => void }) {
  const [inv, setInv] = useState<InvoiceDetail | null>(null)
  const [adjs, setAdjs] = useState<Adjustment[]>([])
  const [error, setError] = useState<string | null>(null)
  const [dialog, setDialog] = useState<'pay' | 'adjust' | null>(null)
  const [receiptId, setReceiptId] = useState<number | null>(null)
  const [reload, setReload] = useState(0)
  const isCashier = role === 'CASHIER'

  useEffect(() => {
    let active = true
    Promise.all([api<InvoiceDetail>(`/api/invoices/${id}`), api<Adjustment[]>(`/api/invoices/${id}/adjustments`)])
      .then(([i, a]) => { if (active) { setInv(i); setAdjs(a); setError(null) } })
      .catch((e) => { if (active) setError(detailMsg(e)) })
    return () => { active = false }
  }, [id, reload])

  function changed() {
    setDialog(null)
    setReload((n) => n + 1)
    onChanged()
  }

  const pending = adjs.some((a) => a.status === 'PENDING')
  const open = inv && inv.status !== 'VOIDED'

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
                  <div>{inv.patientName} ({inv.patientNo})</div>
                </div>
                <InvoiceBadge status={inv.status} />
              </div>
              {inv.voidReason && <p className="mb-2 rounded bg-slate-100 p-2">Voided: {inv.voidReason}</p>}

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
                        <th className="py-1 text-right no-print"> </th>
                      </tr>
                    </thead>
                    <tbody>
                      {inv.payments.map((p) => (
                        <tr key={p.id} className="border-b">
                          <td className="py-1 pr-2">{p.receiptNo}</td>
                          <td className="py-1 pr-2">{fmtDateTime(p.paidAt)}</td>
                          <td className="py-1 pr-2">{methodLabel(p.method)}</td>
                          <td className={`py-1 pr-2 text-right ${p.refund ? 'text-red-700' : ''}`}>{fmtMoney(p.amount)}</td>
                          <td className="py-1 text-right no-print"><button className={btnSmall} onClick={() => setReceiptId(p.id)}>Receipt</button></td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              )}
            </div>

            {adjs.length > 0 && (
              <div className="no-print mt-4">
                <h3 className="mb-1 text-sm font-medium">Approval requests</h3>
                <ul className="space-y-1 text-sm">
                  {adjs.map((a) => (
                    <li key={a.id} className="rounded border px-3 py-1.5">
                      <span className={`mr-2 rounded-full px-2 py-0.5 text-xs font-medium ${ADJ_STYLE[a.status]}`}>{nice(a.status)}</span>
                      {nice(a.type)}{a.type !== 'VOID' && ` ${fmtMoney(a.amount)}`}: {a.reason}
                      <span className="text-slate-500"> · {a.requestedByName}{a.decidedByName && `, decided by ${a.decidedByName}`}{a.decisionNote && ` (${a.decisionNote})`}</span>
                    </li>
                  ))}
                </ul>
              </div>
            )}

            <div className="no-print mt-5 flex flex-wrap justify-end gap-2">
              <button className={btnGhost} onClick={onClose}>Close</button>
              <button className={btnGhost} onClick={() => window.print()}>Print invoice</button>
              {isCashier && open && (
                <button className={btnGhost} disabled={pending} title={pending ? 'A request is already waiting for approval' : ''} onClick={() => setDialog('adjust')}>
                  Request discount, void or refund
                </button>
              )}
              {isCashier && open && inv.balance > 0 && <button className={btnPrimary} onClick={() => setDialog('pay')}>Record payment</button>}
            </div>
          </>
        )}
      </Modal>
      {dialog === 'pay' && inv && <PaymentModal inv={inv} onClose={() => setDialog(null)} onDone={changed} />}
      {dialog === 'adjust' && inv && <AdjustmentRequestModal inv={inv} onClose={() => setDialog(null)} onDone={changed} />}
      {receiptId !== null && <ReceiptModal paymentId={receiptId} onClose={() => setReceiptId(null)} />}
    </>
  )
}

/* ---------------- tabs ---------------- */

function InvoicesTab({ reloadKey, onOpen }: { reloadKey: number; onOpen: (id: number) => void }) {
  const [status, setStatus] = useState('')
  const [page, setPage] = useState(0)
  const [data, setData] = useState<Page<InvoiceSummary> | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => { setPage(0) }, [status])

  useEffect(() => {
    let active = true
    api<Page<InvoiceSummary>>('/api/invoices', { query: { status, page, size: 15 } })
      .then((r) => { if (active) { setData(r); setError(null) } })
      .catch((e) => { if (active) setError(detailMsg(e)) })
    return () => { active = false }
  }, [status, page, reloadKey])

  return (
    <div>
      <div className="mb-3 max-w-xs">
        <Field label="Status">
          <select className={inputCls} value={status} onChange={(e) => setStatus(e.target.value)}>
            <option value="">All invoices</option>
            <option value="UNPAID">Unpaid</option>
            <option value="PARTIALLY_PAID">Partially paid</option>
            <option value="PAID">Paid</option>
            <option value="VOIDED">Voided</option>
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
                <th className="px-3 py-2">Invoice</th>
                <th className="px-3 py-2">Patient</th>
                <th className="px-3 py-2">Date</th>
                <th className="px-3 py-2 text-right">Total</th>
                <th className="px-3 py-2 text-right">Balance</th>
                <th className="px-3 py-2">Status</th>
                <th className="px-3 py-2"> </th>
              </tr>
            </thead>
            <tbody>
              {data?.content.length === 0 && <tr><td colSpan={7} className="px-3 py-6 text-center text-slate-500">No invoices found.</td></tr>}
              {data?.content.map((i) => (
                <tr key={i.id} className="border-t">
                  <td className="px-3 py-2 font-medium">{i.invoiceNo}</td>
                  <td className="px-3 py-2">{i.patientName} <span className="text-slate-400">({i.patientNo})</span></td>
                  <td className="px-3 py-2">{fmtDate(i.createdAt.slice(0, 10))}</td>
                  <td className="px-3 py-2 text-right">{fmtMoney(i.total)}</td>
                  <td className="px-3 py-2 text-right">{fmtMoney(i.balance)}</td>
                  <td className="px-3 py-2"><InvoiceBadge status={i.status} /></td>
                  <td className="px-3 py-2"><button className={btnSmall} onClick={() => onOpen(i.id)}>View</button></td>
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

function CreateInvoiceModal({
  appt, onClose, onCreated,
}: { appt: Billable; onClose: () => void; onCreated: (id: number) => void }) {
  const [services, setServices] = useState<ServiceCharge[] | null>(null)
  const [qty, setQty] = useState<Record<number, string>>({})
  const [includeMeds, setIncludeMeds] = useState(true)
  const [includeLabs, setIncludeLabs] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  useEffect(() => {
    let active = true
    api<ServiceCharge[]>('/api/service-charges', { query: { activeOnly: true } })
      .then((r) => { if (active) setServices(r) })
      .catch((e) => { if (active) setError(detailMsg(e)) })
    return () => { active = false }
  }, [])

  function toggle(id: number, on: boolean) {
    setQty((p) => {
      const next = { ...p }
      if (on) next[id] = '1'
      else delete next[id]
      return next
    })
  }

  async function submit(e: FormEvent) {
    e.preventDefault()
    setError(null)
    const lines: { serviceChargeId: number; quantity: number }[] = []
    for (const [key, value] of Object.entries(qty)) {
      const n = Number(value)
      if (!Number.isInteger(n) || n < 1 || n > 100) { setError('Quantities must be whole numbers from 1 to 100.'); return }
      lines.push({ serviceChargeId: Number(key), quantity: n })
    }
    setBusy(true)
    try {
      const r = await api<InvoiceDetail>('/api/invoices', {
        method: 'POST',
        body: { appointmentId: appt.appointmentId, services: lines, includeDispensedMedicines: includeMeds, includeLabTests: includeLabs },
      })
      onCreated(r.id)
    } catch (err) {
      setError(detailMsg(err))
    } finally {
      setBusy(false)
    }
  }

  return (
    <Modal title="Create invoice" onClose={onClose}>
      <form onSubmit={submit}>
        <p className="mb-3 text-sm text-slate-600">
          {appt.patientName} ({appt.patientNo}) · {fmtDate(appt.date)} {fmtTime(appt.startTime)} · {appt.doctorName}
        </p>
        <ErrorBox message={error} />
        {!services && !error && <Spinner />}
        {services && (
          <ul className="space-y-2">
            {services.map((s) => (
              <li key={s.id} className="flex items-center justify-between gap-2 rounded border px-3 py-2 text-sm">
                <label className="flex items-center gap-2">
                  <input type="checkbox" checked={qty[s.id] !== undefined} onChange={(e) => toggle(s.id, e.target.checked)} />
                  {s.name} <span className="text-slate-400">{fmtMoney(s.price)}</span>
                </label>
                {qty[s.id] !== undefined && (
                  <input
                    className={`${inputCls} w-20`}
                    inputMode="numeric"
                    aria-label={`Quantity for ${s.name}`}
                    value={qty[s.id]}
                    onChange={(e) => setQty((p) => ({ ...p, [s.id]: e.target.value }))}
                  />
                )}
              </li>
            ))}
          </ul>
        )}
        <label className="mt-3 flex items-center gap-2 text-sm">
          <input type="checkbox" checked={includeMeds} onChange={(e) => setIncludeMeds(e.target.checked)} />
          Include medicines dispensed for this visit
        </label>
        <label className="mt-2 flex items-center gap-2 text-sm">
          <input type="checkbox" checked={includeLabs} onChange={(e) => setIncludeLabs(e.target.checked)} />
          Include laboratory tests (collected or released)
        </label>
        {appt.pendingLabRequests > 0 && (
          <p className="mt-1 rounded bg-amber-50 p-2 text-xs text-amber-900">
            {appt.pendingLabRequests} lab request(s) have not had a sample collected yet. Tests are only charged once the sample is collected, so creating the invoice now will leave them out.
          </p>
        )}
        <p className="mt-1 text-xs text-slate-500">Prices come from the price list. They cannot be changed here.</p>
        <div className="mt-5 flex justify-end gap-2">
          <button type="button" className={btnGhost} onClick={onClose}>Cancel</button>
          <button type="submit" className={btnPrimary} disabled={busy}>{busy ? 'Creating...' : 'Create invoice'}</button>
        </div>
      </form>
    </Modal>
  )
}

function CreateAdmissionInvoiceModal({
  adm, onClose, onCreated,
}: { adm: BillableAdmission; onClose: () => void; onCreated: (id: number) => void }) {
  const [services, setServices] = useState<ServiceCharge[] | null>(null)
  const [qty, setQty] = useState<Record<number, string>>({})
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  useEffect(() => {
    let active = true
    api<ServiceCharge[]>('/api/service-charges', { query: { activeOnly: true } })
      .then((r) => { if (active) setServices(r) })
      .catch((e) => { if (active) setError(detailMsg(e)) })
    return () => { active = false }
  }, [])

  function toggle(id: number, on: boolean) {
    setQty((p) => {
      const next = { ...p }
      if (on) next[id] = '1'
      else delete next[id]
      return next
    })
  }

  async function submit(e: FormEvent) {
    e.preventDefault()
    setError(null)
    const lines: { serviceChargeId: number; quantity: number }[] = []
    for (const [key, value] of Object.entries(qty)) {
      const n = Number(value)
      if (!Number.isInteger(n) || n < 1 || n > 100) { setError('Quantities must be whole numbers from 1 to 100.'); return }
      lines.push({ serviceChargeId: Number(key), quantity: n })
    }
    setBusy(true)
    try {
      const r = await api<InvoiceDetail>('/api/invoices', { method: 'POST', body: { admissionId: adm.admissionId, services: lines } })
      onCreated(r.id)
    } catch (err) {
      setError(detailMsg(err))
    } finally {
      setBusy(false)
    }
  }

  return (
    <Modal title="Create admission invoice" onClose={onClose}>
      <form onSubmit={submit}>
        <p className="mb-1 text-sm text-slate-600">{adm.patientName} ({adm.patientNo}) · {adm.admissionNo} · {adm.doctorName}</p>
        <p className="mb-3 text-sm text-slate-600">Admitted {fmtDateTime(adm.admittedAt)}, discharged {fmtDateTime(adm.dischargedAt)}.</p>
        <p className="mb-3 rounded bg-slate-50 p-2 text-xs text-slate-600">
          Bed charges are added automatically from the ward and bed history: one day for each night, at the rate of the bed the patient was in, with a minimum of one day. You can add service lines below.
        </p>
        <ErrorBox message={error} />
        {!services && !error && <Spinner />}
        {services && (
          <ul className="space-y-2">
            {services.map((s) => (
              <li key={s.id} className="flex items-center justify-between gap-2 rounded border px-3 py-2 text-sm">
                <label className="flex items-center gap-2">
                  <input type="checkbox" checked={qty[s.id] !== undefined} onChange={(e) => toggle(s.id, e.target.checked)} />
                  {s.name} <span className="text-slate-400">{fmtMoney(s.price)}</span>
                </label>
                {qty[s.id] !== undefined && (
                  <input className={`${inputCls} w-20`} inputMode="numeric" aria-label={`Quantity for ${s.name}`} value={qty[s.id]} onChange={(e) => setQty((p) => ({ ...p, [s.id]: e.target.value }))} />
                )}
              </li>
            ))}
          </ul>
        )}
        <div className="mt-5 flex justify-end gap-2">
          <button type="button" className={btnGhost} onClick={onClose}>Cancel</button>
          <button type="submit" className={btnPrimary} disabled={busy}>{busy ? 'Creating...' : 'Create invoice'}</button>
        </div>
      </form>
    </Modal>
  )
}

function NewInvoiceTab({ onCreated }: { onCreated: (id: number) => void }) {
  const [list, setList] = useState<Billable[] | null>(null)
  const [admissions, setAdmissions] = useState<BillableAdmission[] | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [picked, setPicked] = useState<Billable | null>(null)
  const [pickedAdm, setPickedAdm] = useState<BillableAdmission | null>(null)
  const [reload, setReload] = useState(0)

  useEffect(() => {
    let active = true
    Promise.all([
      api<Billable[]>('/api/billing/billable-appointments'),
      api<BillableAdmission[]>('/api/billing/billable-admissions'),
    ])
      .then(([a, b]) => { if (active) { setList(a); setAdmissions(b); setError(null) } })
      .catch((e) => { if (active) setError(detailMsg(e)) })
    return () => { active = false }
  }, [reload])

  return (
    <div>
      <ErrorBox message={error} />
      <h2 className="mb-1 text-lg font-medium">Visits</h2>
      <p className="mb-3 text-sm text-slate-500">Visits from the last 7 days that have started and are not yet invoiced.</p>
      {!list && !error ? (
        <Spinner />
      ) : (
        <div className="overflow-x-auto rounded-xl border bg-white">
          <table className="w-full text-left text-sm">
            <thead className="bg-slate-50 text-slate-500">
              <tr>
                <th className="px-3 py-2">When</th>
                <th className="px-3 py-2">Patient</th>
                <th className="px-3 py-2">Doctor</th>
                <th className="px-3 py-2">Visit status</th>
                <th className="px-3 py-2"> </th>
              </tr>
            </thead>
            <tbody>
              {list?.length === 0 && <tr><td colSpan={5} className="px-3 py-6 text-center text-slate-500">Nothing is waiting to be invoiced.</td></tr>}
              {list?.map((a) => (
                <tr key={a.appointmentId} className="border-t">
                  <td className="px-3 py-2 whitespace-nowrap">{fmtDate(a.date)} {fmtTime(a.startTime)}</td>
                  <td className="px-3 py-2">{a.patientName} <span className="text-slate-400">({a.patientNo})</span>{a.pendingLabRequests > 0 && <span className="ml-2 rounded bg-amber-100 px-1.5 py-0.5 text-xs text-amber-800">Lab pending</span>}</td>
                  <td className="px-3 py-2">{a.doctorName}</td>
                  <td className="px-3 py-2">{nice(a.status)}</td>
                  <td className="px-3 py-2"><button className={btnPrimary} onClick={() => setPicked(a)}>Create invoice</button></td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      <h2 className="mb-1 mt-8 text-lg font-medium">Discharged patients</h2>
      <p className="mb-3 text-sm text-slate-500">Hospital stays finished in the last 30 days that are not yet invoiced.</p>
      {admissions && (
        <div className="overflow-x-auto rounded-xl border bg-white">
          <table className="w-full text-left text-sm">
            <thead className="bg-slate-50 text-slate-500">
              <tr>
                <th className="px-3 py-2">Admission</th>
                <th className="px-3 py-2">Patient</th>
                <th className="px-3 py-2">Admitted</th>
                <th className="px-3 py-2">Discharged</th>
                <th className="px-3 py-2"> </th>
              </tr>
            </thead>
            <tbody>
              {admissions.length === 0 && <tr><td colSpan={5} className="px-3 py-6 text-center text-slate-500">No discharged patients are waiting to be invoiced.</td></tr>}
              {admissions.map((a) => (
                <tr key={a.admissionId} className="border-t">
                  <td className="px-3 py-2 font-medium">{a.admissionNo}</td>
                  <td className="px-3 py-2">{a.patientName} <span className="text-slate-400">({a.patientNo})</span></td>
                  <td className="px-3 py-2 whitespace-nowrap">{fmtDateTime(a.admittedAt)}</td>
                  <td className="px-3 py-2 whitespace-nowrap">{fmtDateTime(a.dischargedAt)}</td>
                  <td className="px-3 py-2"><button className={btnPrimary} onClick={() => setPickedAdm(a)}>Create invoice</button></td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {picked && (
        <CreateInvoiceModal
          appt={picked}
          onClose={() => setPicked(null)}
          onCreated={(id) => { setPicked(null); setReload((n) => n + 1); onCreated(id) }}
        />
      )}
      {pickedAdm && (
        <CreateAdmissionInvoiceModal
          adm={pickedAdm}
          onClose={() => setPickedAdm(null)}
          onCreated={(id) => { setPickedAdm(null); setReload((n) => n + 1); onCreated(id) }}
        />
      )}
    </div>
  )
}

function AdjustmentsTab({
  isAdmin, reloadKey, onOpenInvoice, onChanged,
}: { isAdmin: boolean; reloadKey: number; onOpenInvoice: (id: number) => void; onChanged: () => void }) {
  const [status, setStatus] = useState(isAdmin ? 'PENDING' : '')
  const [page, setPage] = useState(0)
  const [data, setData] = useState<Page<Adjustment> | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [reload, setReload] = useState(0)
  const [deciding, setDeciding] = useState<{ adj: Adjustment; approve: boolean } | null>(null)

  useEffect(() => { setPage(0) }, [status])

  useEffect(() => {
    let active = true
    api<Page<Adjustment>>('/api/adjustments', { query: { status, page, size: 15 } })
      .then((r) => { if (active) { setData(r); setError(null) } })
      .catch((e) => { if (active) setError(detailMsg(e)) })
    return () => { active = false }
  }, [status, page, reload, reloadKey])

  return (
    <div>
      <div className="mb-3 max-w-xs">
        <Field label="Status">
          <select className={inputCls} value={status} onChange={(e) => setStatus(e.target.value)}>
            <option value="">All requests</option>
            <option value="PENDING">Waiting</option>
            <option value="APPROVED">Approved</option>
            <option value="REJECTED">Rejected</option>
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
                <th className="px-3 py-2">Invoice</th>
                <th className="px-3 py-2">Type</th>
                <th className="px-3 py-2 text-right">Amount</th>
                <th className="px-3 py-2">Reason</th>
                <th className="px-3 py-2">Requested by</th>
                <th className="px-3 py-2">Status</th>
                <th className="px-3 py-2"> </th>
              </tr>
            </thead>
            <tbody>
              {data?.content.length === 0 && <tr><td colSpan={7} className="px-3 py-6 text-center text-slate-500">No requests found.</td></tr>}
              {data?.content.map((a) => (
                <tr key={a.id} className="border-t align-top">
                  <td className="px-3 py-2">
                    <button className="font-medium text-teal-700 hover:underline" onClick={() => onOpenInvoice(a.invoiceId)}>{a.invoiceNo}</button>
                    <div className="text-xs text-slate-400">{a.patientName}</div>
                  </td>
                  <td className="px-3 py-2">{nice(a.type)}</td>
                  <td className="px-3 py-2 text-right">{a.type === 'VOID' ? '' : fmtMoney(a.amount)}</td>
                  <td className="px-3 py-2">{a.reason}{a.decisionNote && <div className="text-xs text-slate-400">Decision note: {a.decisionNote}</div>}</td>
                  <td className="px-3 py-2">{a.requestedByName}</td>
                  <td className="px-3 py-2"><span className={`rounded-full px-2 py-0.5 text-xs font-medium ${ADJ_STYLE[a.status]}`}>{nice(a.status)}</span></td>
                  <td className="px-3 py-2">
                    {isAdmin && a.status === 'PENDING' && (
                      <div className="flex gap-1">
                        <button className={btnSmall} onClick={() => setDeciding({ adj: a, approve: true })}>Approve</button>
                        <button className={`${btnSmall} text-red-700`} onClick={() => setDeciding({ adj: a, approve: false })}>Reject</button>
                      </div>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
      {data && <Pagination page={data.page} totalPages={data.totalPages} onPage={setPage} />}
      {deciding && (
        <DecisionModal
          adj={deciding.adj}
          approve={deciding.approve}
          onClose={() => setDeciding(null)}
          onDone={() => { setDeciding(null); setReload((n) => n + 1); onChanged() }}
        />
      )}
    </div>
  )
}

/* ---------------- the page ---------------- */

type BTab = 'invoices' | 'new' | 'adjustments'

export default function Billing() {
  const { user } = useAuth()
  const role = user?.role
  const isCashier = role === 'CASHIER'
  const isAdmin = role === 'ADMIN'
  const { id } = useParams()
  const { pathname } = useLocation()
  const [tab, setTab] = useState<BTab>(isCashier ? 'new' : isAdmin ? 'adjustments' : 'invoices')
  const [reload, setReload] = useState(0)
  const [openId, setOpenId] = useState<number | null>(null)

  useEffect(() => {
    if (pathname.endsWith('/adjustments')) setTab('adjustments')
    const n = Number(id)
    if (id && Number.isInteger(n)) {
      setTab('invoices')
      setOpenId(n)
    }
  }, [id, pathname])

  const tabs: { key: BTab; label: string }[] = [
    { key: 'invoices', label: 'Invoices' },
    ...(isCashier ? [{ key: 'new' as const, label: 'New invoice' }] : []),
    { key: 'adjustments', label: isAdmin ? 'Approvals' : 'Requests' },
  ]

  return (
    <div>
      <h1 className="mb-4 text-2xl font-semibold">Billing</h1>
      <Tabs tabs={tabs} value={tab} onChange={setTab} />
      {tab === 'invoices' && <InvoicesTab reloadKey={reload} onOpen={setOpenId} />}
      {tab === 'new' && isCashier && (
        <NewInvoiceTab onCreated={(newId) => { setReload((n) => n + 1); setOpenId(newId) }} />
      )}
      {tab === 'adjustments' && (
        <AdjustmentsTab isAdmin={isAdmin} reloadKey={reload} onOpenInvoice={setOpenId} onChanged={() => setReload((n) => n + 1)} />
      )}
      {openId !== null && (
        <InvoiceModal id={openId} role={role} onClose={() => setOpenId(null)} onChanged={() => setReload((n) => n + 1)} />
      )}
    </div>
  )
}