import { useEffect, useState } from 'react'
import type { ChangeEvent, FormEvent, ReactNode } from 'react'
import { useLocation } from 'react-router-dom'
import { api } from '../api'
import { useAuth } from '../auth'
import type { Page } from '../types'
import {
  btnGhost, btnPrimary, btnSmall, detailMsg, ErrorBox, Field, fmtDate, fmtDateTime, fmtMoney,
  inputCls, Modal, Pagination, Spinner, Tabs, todayStr, useDebounced,
} from '../ui'

interface Medicine {
  id: number
  name: string
  strength: string | null
  form: string
  unit: string
  category: string | null
  reorderLevel: number
  active: boolean
  availableStock: number
  lowStock: boolean
  unitPrice: number
}

interface Batch {
  id: number
  medicineId: number
  medicineName: string
  batchNo: string
  expiryDate: string
  quantity: number
  expired: boolean
}

interface Alerts {
  lowStock: Medicine[]
  nearExpiry: Batch[]
  expired: Batch[]
}

interface Movement {
  id: number
  medicineName: string
  batchNo: string
  type: string
  quantity: number
  reference: string | null
  userName: string
  createdAt: string
}

interface QueueEntry {
  id: number
  patientNo: string
  patientName: string
  doctorName: string
  status: string
  issuedAt: string | null
  itemCount: number
}

interface RxItem {
  id: number
  medicineName: string
  strength: string | null
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

interface Rx {
  id: number
  patientNo: string
  patientName: string
  doctorName: string
  status: string
  notes: string | null
  items: RxItem[]
  allergyWarnings: string[]
}

interface DispenseRecord {
  id: number
  medicineName: string
  batchNo: string
  expiryDate: string
  quantity: number
  dispensedBy: string
  dispensedAt: string
}

const FORMS = ['TABLET', 'CAPSULE', 'SYRUP', 'INJECTION', 'CREAM', 'DROPS', 'INHALER', 'OTHER']
const nice = (s: string) => s.charAt(0) + s.slice(1).toLowerCase().replace('_', ' ')

/* ---------------- queue and dispensing ---------------- */

function QueueTab({ reloadKey, onOpen }: { reloadKey: number; onOpen: (id: number) => void }) {
  const [page, setPage] = useState(0)
  const [data, setData] = useState<Page<QueueEntry> | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [tick, setTick] = useState(0)

  useEffect(() => {
    const t = setInterval(() => setTick((n) => n + 1), 30000)
    return () => clearInterval(t)
  }, [])

  useEffect(() => {
    let active = true
    api<Page<QueueEntry>>('/api/pharmacy/queue', { query: { page, size: 15 } })
      .then((r) => { if (active) { setData(r); setError(null) } })
      .catch((e) => { if (active) setError(detailMsg(e)) })
    return () => { active = false }
  }, [page, reloadKey, tick])

  return (
    <div>
      <ErrorBox message={error} />
      {!data && !error ? (
        <Spinner />
      ) : (
        <div className="overflow-x-auto rounded-xl border bg-white">
          <table className="w-full text-left text-sm">
            <thead className="bg-slate-50 text-slate-500">
              <tr>
                <th className="px-3 py-2">Patient</th>
                <th className="px-3 py-2">Prescribed by</th>
                <th className="px-3 py-2">Issued</th>
                <th className="px-3 py-2">Items</th>
                <th className="px-3 py-2">Status</th>
                <th className="px-3 py-2">Action</th>
              </tr>
            </thead>
            <tbody>
              {data?.content.length === 0 && (
                <tr><td colSpan={6} className="px-3 py-6 text-center text-slate-500">No prescriptions are waiting.</td></tr>
              )}
              {data?.content.map((q) => (
                <tr key={q.id} className="border-t">
                  <td className="px-3 py-2">{q.patientName} <span className="text-slate-400">({q.patientNo})</span></td>
                  <td className="px-3 py-2">{q.doctorName}</td>
                  <td className="px-3 py-2">{fmtDateTime(q.issuedAt)}</td>
                  <td className="px-3 py-2">{q.itemCount}</td>
                  <td className="px-3 py-2">{nice(q.status)}</td>
                  <td className="px-3 py-2"><button className={btnSmall} onClick={() => onOpen(q.id)}>Open</button></td>
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

function DispenseModal({ id, onClose, onChanged }: { id: number; onClose: () => void; onChanged: () => void }) {
  const [rx, setRx] = useState<Rx | null>(null)
  const [history, setHistory] = useState<DispenseRecord[]>([])
  const [qty, setQty] = useState<Record<number, string>>({})
  const [error, setError] = useState<string | null>(null)
  const [message, setMessage] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)
  const [reload, setReload] = useState(0)

  useEffect(() => {
    let active = true
    Promise.all([api<Rx>(`/api/prescriptions/${id}`), api<DispenseRecord[]>(`/api/prescriptions/${id}/dispensing`)])
      .then(([r, h]) => {
        if (!active) return
        setRx(r)
        setHistory(h)
        const q: Record<number, string> = {}
        r.items.forEach((i) => { q[i.id] = String(i.qtyRemaining) })
        setQty(q)
      })
      .catch((e) => { if (active) setError(detailMsg(e)) })
    return () => { active = false }
  }, [id, reload])

  async function dispense() {
    if (!rx) return
    setError(null)
    setMessage(null)
    const lines: { itemId: number; quantity: number }[] = []
    for (const i of rx.items) {
      const raw = (qty[i.id] ?? '').trim()
      if (raw === '') continue
      const n = Number(raw)
      if (!Number.isInteger(n) || n < 0 || n > i.qtyRemaining) {
        setError(`${i.medicineName}: enter a whole number from 0 to ${i.qtyRemaining}.`)
        return
      }
      if (n > 0) lines.push({ itemId: i.id, quantity: n })
    }
    if (lines.length === 0) {
      setError('Enter a quantity for at least one medicine.')
      return
    }
    setBusy(true)
    try {
      await api(`/api/prescriptions/${rx.id}/dispense`, { method: 'POST', body: { lines } })
      setMessage('Dispensed. Stock has been updated.')
      onChanged()
      setReload((n) => n + 1)
    } catch (e) {
      setError(detailMsg(e))
    } finally {
      setBusy(false)
    }
  }

  const canDispense = rx?.status === 'ISSUED' || rx?.status === 'PARTIALLY_DISPENSED'

  return (
    <Modal title="Dispense prescription" onClose={onClose} wide>
      <ErrorBox message={error} />
      {message && <p className="mb-3 rounded-md bg-green-50 p-2 text-sm text-green-800">{message}</p>}
      {!rx && !error && <Spinner />}
      {rx && (
        <>
          <p className="mb-2 text-sm">
            <span className="font-medium">{rx.patientName}</span> ({rx.patientNo}) · prescribed by {rx.doctorName} · {nice(rx.status)}
          </p>
          {rx.allergyWarnings.map((w) => (
            <p key={w} className="mb-2 rounded-md bg-amber-50 p-2 text-sm text-amber-900">⚠ {w}</p>
          ))}
          {rx.notes && <p className="mb-2 text-sm text-slate-600">Note from the doctor: {rx.notes}</p>}
          <div className="overflow-x-auto rounded-lg border">
            <table className="w-full text-left text-sm">
              <thead className="bg-slate-50 text-slate-500">
                <tr>
                  <th className="px-3 py-2">Medicine</th>
                  <th className="px-3 py-2">Directions</th>
                  <th className="px-3 py-2">Prescribed</th>
                  <th className="px-3 py-2">Given</th>
                  <th className="px-3 py-2">Remaining</th>
                  {canDispense && <th className="px-3 py-2">Dispense now</th>}
                </tr>
              </thead>
              <tbody>
                {rx.items.map((i) => (
                  <tr key={i.id} className="border-t align-top">
                    <td className="px-3 py-2">{i.medicineName}{i.strength && ` ${i.strength}`}</td>
                    <td className="px-3 py-2">
                      {i.dose}{i.route && ` (${i.route})`}, {i.frequency}{i.duration && `, ${i.duration}`}
                      {i.instructions && <div className="text-xs text-slate-500">{i.instructions}</div>}
                    </td>
                    <td className="px-3 py-2">{i.qtyPrescribed}</td>
                    <td className="px-3 py-2">{i.qtyDispensed}</td>
                    <td className="px-3 py-2">{i.qtyRemaining}</td>
                    {canDispense && (
                      <td className="px-3 py-2">
                        <input
                          className={`${inputCls} w-24`}
                          inputMode="numeric"
                          disabled={i.qtyRemaining === 0}
                          value={qty[i.id] ?? ''}
                          onChange={(e) => setQty((p) => ({ ...p, [i.id]: e.target.value }))}
                          aria-label={`Quantity to dispense for ${i.medicineName}`}
                        />
                      </td>
                    )}
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          {canDispense && (
            <p className="mt-2 text-xs text-slate-500">
              Stock is taken from the batch that expires first. Expired batches are never used. If any medicine is short, nothing is dispensed.
            </p>
          )}
          {history.length > 0 && (
            <div className="mt-4">
              <h3 className="mb-1 text-sm font-medium">Dispensing history</h3>
              <ul className="space-y-1 text-sm">
                {history.map((h) => (
                  <li key={h.id} className="rounded border px-3 py-1.5">
                    {h.quantity} × {h.medicineName} from batch {h.batchNo} (expires {fmtDate(h.expiryDate)})
                    <span className="text-slate-500"> · {h.dispensedBy}, {fmtDateTime(h.dispensedAt)}</span>
                  </li>
                ))}
              </ul>
            </div>
          )}
        </>
      )}
      <div className="mt-5 flex justify-end gap-2">
        <button className={btnGhost} onClick={onClose}>Close</button>
        {canDispense && <button className={btnPrimary} disabled={busy} onClick={() => void dispense()}>{busy ? 'Dispensing...' : 'Dispense'}</button>}
      </div>
    </Modal>
  )
}

/* ---------------- medicines and batches ---------------- */

type FormVals = {
  name: string; strength: string; form: string; unit: string; category: string
  reorderLevel: string; unitPrice: string; active: boolean
}

function MedicineForm({ medicine, onClose, onSaved }: { medicine: Medicine | null; onClose: () => void; onSaved: () => void }) {
  const [f, setF] = useState<FormVals>({
    name: medicine?.name ?? '',
    strength: medicine?.strength ?? '',
    form: medicine?.form ?? 'TABLET',
    unit: medicine?.unit ?? 'tablet',
    category: medicine?.category ?? '',
    reorderLevel: String(medicine?.reorderLevel ?? 0),
    unitPrice: String(medicine?.unitPrice ?? 0),
    active: medicine?.active ?? true,
  })
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  const text = (k: Exclude<keyof FormVals, 'active'>) => (e: ChangeEvent<HTMLInputElement | HTMLSelectElement>) =>
    setF((p) => ({ ...p, [k]: e.target.value }))

  async function submit(e: FormEvent) {
    e.preventDefault()
    setError(null)
    const reorder = Number(f.reorderLevel)
    const price = Number(f.unitPrice)
    if (!Number.isInteger(reorder) || reorder < 0) { setError('Reorder level must be a whole number, 0 or more.'); return }
    if (Number.isNaN(price) || price < 0) { setError('Price must be a number, 0 or more.'); return }
    const body = {
      name: f.name.trim(),
      strength: f.strength.trim() || null,
      form: f.form,
      unit: f.unit.trim(),
      category: f.category.trim() || null,
      reorderLevel: reorder,
      unitPrice: price,
      active: f.active,
    }
    setBusy(true)
    try {
      if (medicine) await api(`/api/medicines/${medicine.id}`, { method: 'PUT', body })
      else await api('/api/medicines', { method: 'POST', body })
      onSaved()
    } catch (err) {
      setError(detailMsg(err))
    } finally {
      setBusy(false)
    }
  }

  return (
    <Modal title={medicine ? 'Edit medicine' : 'Add medicine'} onClose={onClose}>
      <form onSubmit={submit}>
        <ErrorBox message={error} />
        <div className="grid gap-3 sm:grid-cols-2">
          <Field label="Name" className="sm:col-span-2"><input className={inputCls} value={f.name} onChange={text('name')} required maxLength={120} /></Field>
          <Field label="Strength"><input className={inputCls} placeholder="500 mg" value={f.strength} onChange={text('strength')} maxLength={50} /></Field>
          <Field label="Form">
            <select className={inputCls} value={f.form} onChange={text('form')}>
              {FORMS.map((x) => <option key={x} value={x}>{nice(x)}</option>)}
            </select>
          </Field>
          <Field label="Unit"><input className={inputCls} placeholder="tablet" value={f.unit} onChange={text('unit')} required maxLength={20} /></Field>
          <Field label="Category"><input className={inputCls} value={f.category} onChange={text('category')} maxLength={60} /></Field>
          <Field label="Reorder level"><input className={inputCls} inputMode="numeric" value={f.reorderLevel} onChange={text('reorderLevel')} /></Field>
          <Field label="Price per unit (LKR)"><input className={inputCls} inputMode="decimal" value={f.unitPrice} onChange={text('unitPrice')} /></Field>
          {medicine && (
            <label className="flex items-center gap-2 text-sm sm:col-span-2">
              <input type="checkbox" checked={f.active} onChange={(e) => setF((p) => ({ ...p, active: e.target.checked }))} />
              Active (inactive medicines cannot be prescribed)
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

function BatchesModal({
  medicine, canEdit, onClose, onChanged,
}: { medicine: Medicine; canEdit: boolean; onClose: () => void; onChanged: () => void }) {
  const [list, setList] = useState<Batch[] | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [reload, setReload] = useState(0)
  const [add, setAdd] = useState({ batchNo: '', expiryDate: '', quantity: '' })
  const [adjusting, setAdjusting] = useState<Batch | null>(null)
  const [adj, setAdj] = useState({ change: '', reason: '' })
  const [busy, setBusy] = useState(false)

  useEffect(() => {
    let active = true
    api<Batch[]>(`/api/medicines/${medicine.id}/batches`)
      .then((r) => { if (active) setList(r) })
      .catch((e) => { if (active) setError(detailMsg(e)) })
    return () => { active = false }
  }, [medicine.id, reload])

  async function addBatch(e: FormEvent) {
    e.preventDefault()
    setError(null)
    const q = Number(add.quantity)
    if (!Number.isInteger(q) || q < 1) { setError('Quantity must be a whole number, 1 or more.'); return }
    setBusy(true)
    try {
      await api(`/api/medicines/${medicine.id}/batches`, {
        method: 'POST',
        body: { batchNo: add.batchNo.trim(), expiryDate: add.expiryDate, quantity: q },
      })
      setAdd({ batchNo: '', expiryDate: '', quantity: '' })
      setReload((n) => n + 1)
      onChanged()
    } catch (err) {
      setError(detailMsg(err))
    } finally {
      setBusy(false)
    }
  }

  async function adjust(e: FormEvent) {
    e.preventDefault()
    if (!adjusting) return
    setError(null)
    const c = Number(adj.change)
    if (!Number.isInteger(c) || c === 0) { setError('Enter a whole number that is not zero, such as -5 or 20.'); return }
    setBusy(true)
    try {
      await api(`/api/batches/${adjusting.id}/adjust`, { method: 'POST', body: { quantityChange: c, reason: adj.reason.trim() } })
      setAdjusting(null)
      setAdj({ change: '', reason: '' })
      setReload((n) => n + 1)
      onChanged()
    } catch (err) {
      setError(detailMsg(err))
    } finally {
      setBusy(false)
    }
  }

  return (
    <Modal title={`Batches: ${medicine.name}${medicine.strength ? ` ${medicine.strength}` : ''}`} onClose={onClose} wide>
      <ErrorBox message={error} />
      {!list && !error && <Spinner />}
      {list && (
        <div className="overflow-x-auto rounded-lg border">
          <table className="w-full text-left text-sm">
            <thead className="bg-slate-50 text-slate-500">
              <tr>
                <th className="px-3 py-2">Batch</th>
                <th className="px-3 py-2">Expiry</th>
                <th className="px-3 py-2">Quantity</th>
                <th className="px-3 py-2">Status</th>
                {canEdit && <th className="px-3 py-2">Action</th>}
              </tr>
            </thead>
            <tbody>
              {list.length === 0 && <tr><td colSpan={5} className="px-3 py-5 text-center text-slate-500">No batches yet.</td></tr>}
              {list.map((b) => (
                <tr key={b.id} className="border-t">
                  <td className="px-3 py-2">{b.batchNo}</td>
                  <td className="px-3 py-2">{fmtDate(b.expiryDate)}</td>
                  <td className="px-3 py-2">{b.quantity}</td>
                  <td className="px-3 py-2">{b.expired ? <span className="rounded bg-red-100 px-1.5 py-0.5 text-xs text-red-700">Expired</span> : 'In date'}</td>
                  {canEdit && <td className="px-3 py-2"><button className={btnSmall} onClick={() => setAdjusting(b)}>Adjust</button></td>}
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {canEdit && adjusting && (
        <form onSubmit={adjust} className="mt-4 rounded-lg border border-amber-300 bg-amber-50 p-3">
          <p className="mb-2 text-sm font-medium">Correct stock for batch {adjusting.batchNo} (now {adjusting.quantity})</p>
          <div className="grid gap-3 sm:grid-cols-3">
            <Field label="Change (+ or -)"><input className={inputCls} placeholder="-5" value={adj.change} onChange={(e) => setAdj((p) => ({ ...p, change: e.target.value }))} /></Field>
            <Field label="Reason (required)" className="sm:col-span-2">
              <input className={inputCls} value={adj.reason} onChange={(e) => setAdj((p) => ({ ...p, reason: e.target.value }))} maxLength={100} required />
            </Field>
          </div>
          <div className="mt-3 flex gap-2">
            <button type="submit" className={btnPrimary} disabled={busy || !adj.reason.trim()}>Save correction</button>
            <button type="button" className={btnGhost} onClick={() => setAdjusting(null)}>Cancel</button>
          </div>
        </form>
      )}

      {canEdit && (
        <form onSubmit={addBatch} className="mt-4 rounded-lg bg-slate-50 p-3">
          <p className="mb-2 text-sm font-medium">Receive a new batch</p>
          <div className="grid gap-3 sm:grid-cols-3">
            <Field label="Batch number"><input className={inputCls} value={add.batchNo} onChange={(e) => setAdd((p) => ({ ...p, batchNo: e.target.value }))} required maxLength={50} /></Field>
            <Field label="Expiry date"><input type="date" min={todayStr()} className={inputCls} value={add.expiryDate} onChange={(e) => setAdd((p) => ({ ...p, expiryDate: e.target.value }))} required /></Field>
            <Field label="Quantity"><input className={inputCls} inputMode="numeric" value={add.quantity} onChange={(e) => setAdd((p) => ({ ...p, quantity: e.target.value }))} required /></Field>
          </div>
          <div className="mt-3"><button type="submit" className={btnPrimary} disabled={busy}>Add batch</button></div>
        </form>
      )}
      <div className="mt-4 flex justify-end"><button className={btnGhost} onClick={onClose}>Close</button></div>
    </Modal>
  )
}

function MedicinesTab({ canEdit, reloadKey }: { canEdit: boolean; reloadKey: number }) {
  const [q, setQ] = useState('')
  const dq = useDebounced(q)
  const [page, setPage] = useState(0)
  const [includeInactive, setIncludeInactive] = useState(false)
  const [data, setData] = useState<Page<Medicine> | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [reload, setReload] = useState(0)
  const [form, setForm] = useState<{ medicine: Medicine | null } | null>(null)
  const [batches, setBatches] = useState<Medicine | null>(null)

  useEffect(() => { setPage(0) }, [dq, includeInactive])

  useEffect(() => {
    let active = true
    api<Page<Medicine>>('/api/medicines', { query: { q: dq, page, size: 15, includeInactive } })
      .then((r) => { if (active) { setData(r); setError(null) } })
      .catch((e) => { if (active) setError(detailMsg(e)) })
    return () => { active = false }
  }, [dq, page, includeInactive, reload, reloadKey])

  return (
    <div>
      <div className="mb-3 flex flex-wrap items-center gap-3">
        <input className={`${inputCls} max-w-sm`} placeholder="Search medicines" value={q} onChange={(e) => setQ(e.target.value)} aria-label="Search medicines" />
        <label className="flex items-center gap-2 text-sm">
          <input type="checkbox" checked={includeInactive} onChange={(e) => setIncludeInactive(e.target.checked)} />
          Show inactive
        </label>
        {canEdit && <button className={`${btnPrimary} ml-auto`} onClick={() => setForm({ medicine: null })}>Add medicine</button>}
      </div>
      <ErrorBox message={error} />
      {!data && !error ? (
        <Spinner />
      ) : (
        <div className="overflow-x-auto rounded-xl border bg-white">
          <table className="w-full text-left text-sm">
            <thead className="bg-slate-50 text-slate-500">
              <tr>
                <th className="px-3 py-2">Medicine</th>
                <th className="px-3 py-2">Form</th>
                <th className="px-3 py-2">In stock</th>
                <th className="px-3 py-2">Reorder at</th>
                <th className="px-3 py-2">Price</th>
                <th className="px-3 py-2">Actions</th>
              </tr>
            </thead>
            <tbody>
              {data?.content.length === 0 && <tr><td colSpan={6} className="px-3 py-6 text-center text-slate-500">No medicines found.</td></tr>}
              {data?.content.map((m) => (
                <tr key={m.id} className={`border-t ${m.active ? '' : 'text-slate-400'}`}>
                  <td className="px-3 py-2">
                    <span className="font-medium">{m.name}</span>{m.strength && ` ${m.strength}`}
                    {m.category && <div className="text-xs text-slate-400">{m.category}</div>}
                    {!m.active && <span className="ml-2 rounded bg-slate-200 px-1.5 py-0.5 text-xs text-slate-600">Inactive</span>}
                  </td>
                  <td className="px-3 py-2">{nice(m.form)}</td>
                  <td className="px-3 py-2">
                    {m.availableStock} {m.unit}
                    {m.lowStock && <span className="ml-2 rounded bg-amber-100 px-1.5 py-0.5 text-xs text-amber-800">Low</span>}
                  </td>
                  <td className="px-3 py-2">{m.reorderLevel}</td>
                  <td className="px-3 py-2">{fmtMoney(m.unitPrice)}</td>
                  <td className="px-3 py-2">
                    <div className="flex gap-1">
                      <button className={btnSmall} onClick={() => setBatches(m)}>Batches</button>
                      {canEdit && <button className={btnSmall} onClick={() => setForm({ medicine: m })}>Edit</button>}
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
      {data && <Pagination page={data.page} totalPages={data.totalPages} onPage={setPage} />}
      {form && <MedicineForm medicine={form.medicine} onClose={() => setForm(null)} onSaved={() => { setForm(null); setReload((n) => n + 1) }} />}
      {batches && <BatchesModal medicine={batches} canEdit={canEdit} onClose={() => setBatches(null)} onChanged={() => setReload((n) => n + 1)} />}
    </div>
  )
}

/* ---------------- alerts and ledger ---------------- */

function AlertBlock({ title, count, children }: { title: string; count: number; children: ReactNode }) {
  return (
    <section className="mb-5 rounded-xl border bg-white p-4">
      <h2 className="mb-2 text-lg font-medium">{title} <span className="text-sm font-normal text-slate-500">({count})</span></h2>
      {count === 0 ? <p className="text-sm text-slate-500">Nothing to show.</p> : children}
    </section>
  )
}

function AlertsTab() {
  const [data, setData] = useState<Alerts | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    let active = true
    api<Alerts>('/api/pharmacy/alerts')
      .then((r) => { if (active) setData(r) })
      .catch((e) => { if (active) setError(detailMsg(e)) })
    return () => { active = false }
  }, [])

  if (error) return <ErrorBox message={error} />
  if (!data) return <Spinner />

  return (
    <div>
      <AlertBlock title="Low stock" count={data.lowStock.length}>
        <ul className="space-y-1 text-sm">
          {data.lowStock.map((m) => (
            <li key={m.id} className="rounded border px-3 py-1.5">
              <span className="font-medium">{m.name}</span>{m.strength && ` ${m.strength}`}: {m.availableStock} {m.unit} left (reorder at {m.reorderLevel})
            </li>
          ))}
        </ul>
      </AlertBlock>
      <AlertBlock title="Expiring within 30 days" count={data.nearExpiry.length}>
        <ul className="space-y-1 text-sm">
          {data.nearExpiry.map((b) => (
            <li key={b.id} className="rounded border px-3 py-1.5">
              <span className="font-medium">{b.medicineName}</span> batch {b.batchNo}: {b.quantity} left, expires {fmtDate(b.expiryDate)}
            </li>
          ))}
        </ul>
      </AlertBlock>
      <AlertBlock title="Expired (remove from stock)" count={data.expired.length}>
        <ul className="space-y-1 text-sm">
          {data.expired.map((b) => (
            <li key={b.id} className="rounded border border-red-200 bg-red-50 px-3 py-1.5">
              <span className="font-medium">{b.medicineName}</span> batch {b.batchNo}: {b.quantity} left, expired {fmtDate(b.expiryDate)}
            </li>
          ))}
        </ul>
      </AlertBlock>
    </div>
  )
}

const TYPE_LABEL: Record<string, string> = {
  RECEIPT: 'Received', DISPENSE: 'Dispensed', ADJUSTMENT: 'Adjusted', RETURN: 'Returned',
}

function LedgerTab({ reloadKey }: { reloadKey: number }) {
  const [page, setPage] = useState(0)
  const [data, setData] = useState<Page<Movement> | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    let active = true
    api<Page<Movement>>('/api/stock-movements', { query: { page, size: 20 } })
      .then((r) => { if (active) { setData(r); setError(null) } })
      .catch((e) => { if (active) setError(detailMsg(e)) })
    return () => { active = false }
  }, [page, reloadKey])

  return (
    <div>
      <ErrorBox message={error} />
      {!data && !error ? (
        <Spinner />
      ) : (
        <div className="overflow-x-auto rounded-xl border bg-white">
          <table className="w-full text-left text-sm">
            <thead className="bg-slate-50 text-slate-500">
              <tr>
                <th className="px-3 py-2">When</th>
                <th className="px-3 py-2">Medicine</th>
                <th className="px-3 py-2">Batch</th>
                <th className="px-3 py-2">Type</th>
                <th className="px-3 py-2">Quantity</th>
                <th className="px-3 py-2">Reference</th>
                <th className="px-3 py-2">By</th>
              </tr>
            </thead>
            <tbody>
              {data?.content.map((m) => (
                <tr key={m.id} className="border-t">
                  <td className="px-3 py-2 whitespace-nowrap">{fmtDateTime(m.createdAt)}</td>
                  <td className="px-3 py-2">{m.medicineName}</td>
                  <td className="px-3 py-2">{m.batchNo}</td>
                  <td className="px-3 py-2">{TYPE_LABEL[m.type] ?? m.type}</td>
                  <td className={`px-3 py-2 font-medium ${m.quantity < 0 ? 'text-red-700' : 'text-green-700'}`}>{m.quantity > 0 ? `+${m.quantity}` : m.quantity}</td>
                  <td className="px-3 py-2">{m.reference}</td>
                  <td className="px-3 py-2">{m.userName}</td>
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

/* ---------------- the page ---------------- */

type PTab = 'queue' | 'medicines' | 'alerts' | 'ledger'

export default function Pharmacy() {
  const { user } = useAuth()
  const isPharmacist = user?.role === 'PHARMACIST'
  const { pathname } = useLocation()
  const [tab, setTab] = useState<PTab>(isPharmacist ? 'queue' : 'medicines')
  const [reload, setReload] = useState(0)
  const [openRx, setOpenRx] = useState<number | null>(null)

  useEffect(() => {
    if (pathname.endsWith('/alerts')) setTab('alerts')
    else if (pathname.endsWith('/queue') && isPharmacist) setTab('queue')
  }, [pathname, isPharmacist])

  const tabs: { key: PTab; label: string }[] = [
    ...(isPharmacist ? [{ key: 'queue' as const, label: 'Prescription queue' }] : []),
    { key: 'medicines', label: 'Medicines and stock' },
    { key: 'alerts', label: 'Alerts' },
    { key: 'ledger', label: 'Stock ledger' },
  ]

  return (
    <div>
      <h1 className="mb-4 text-2xl font-semibold">Pharmacy</h1>
      <Tabs tabs={tabs} value={tab} onChange={setTab} />
      {tab === 'queue' && isPharmacist && <QueueTab reloadKey={reload} onOpen={setOpenRx} />}
      {tab === 'medicines' && <MedicinesTab canEdit={isPharmacist} reloadKey={reload} />}
      {tab === 'alerts' && <AlertsTab />}
      {tab === 'ledger' && <LedgerTab reloadKey={reload} />}
      {openRx !== null && <DispenseModal id={openRx} onClose={() => setOpenRx(null)} onChanged={() => setReload((n) => n + 1)} />}
    </div>
  )
}