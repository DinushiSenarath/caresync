import { useEffect, useState } from 'react'
import type { FormEvent } from 'react'
import { api } from '../api'
import { btnGhost, btnPrimary, btnSmall, detailMsg, ErrorBox, Field, fmtMoney, inputCls, Modal, Spinner } from '../ui'

interface Charge {
  id: number
  name: string
  category: string
  price: number
  active: boolean
}

const CATEGORIES = ['CONSULTATION', 'PROCEDURE', 'INVESTIGATION', 'OTHER']
const nice = (s: string) => s.charAt(0) + s.slice(1).toLowerCase()

function ChargeForm({ charge, onClose, onSaved }: { charge: Charge | null; onClose: () => void; onSaved: () => void }) {
  const [name, setName] = useState(charge?.name ?? '')
  const [category, setCategory] = useState(charge?.category ?? 'CONSULTATION')
  const [price, setPrice] = useState(String(charge?.price ?? ''))
  const [active, setActive] = useState(charge?.active ?? true)
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  async function submit(e: FormEvent) {
    e.preventDefault()
    setError(null)
    const p = Number(price)
    if (Number.isNaN(p) || p < 0) { setError('Enter a price of 0 or more.'); return }
    const body = { name: name.trim(), category, price: p, active }
    setBusy(true)
    try {
      if (charge) await api(`/api/service-charges/${charge.id}`, { method: 'PUT', body })
      else await api('/api/service-charges', { method: 'POST', body })
      onSaved()
    } catch (err) {
      setError(detailMsg(err))
    } finally {
      setBusy(false)
    }
  }

  return (
    <Modal title={charge ? 'Edit service' : 'Add service'} onClose={onClose}>
      <form onSubmit={submit}>
        <ErrorBox message={error} />
        <div className="grid gap-3">
          <Field label="Name"><input className={inputCls} value={name} onChange={(e) => setName(e.target.value)} required maxLength={120} /></Field>
          <Field label="Category">
            <select className={inputCls} value={category} onChange={(e) => setCategory(e.target.value)}>
              {CATEGORIES.map((c) => <option key={c} value={c}>{nice(c)}</option>)}
            </select>
          </Field>
          <Field label="Price (LKR)"><input className={inputCls} inputMode="decimal" value={price} onChange={(e) => setPrice(e.target.value)} required /></Field>
          {charge && (
            <label className="flex items-center gap-2 text-sm">
              <input type="checkbox" checked={active} onChange={(e) => setActive(e.target.checked)} />
              Active (inactive services cannot be added to new invoices)
            </label>
          )}
        </div>
        <p className="mt-3 text-xs text-slate-500">Changing a price never changes invoices that were already created.</p>
        <div className="mt-5 flex justify-end gap-2">
          <button type="button" className={btnGhost} onClick={onClose}>Cancel</button>
          <button type="submit" className={btnPrimary} disabled={busy}>{busy ? 'Saving...' : 'Save'}</button>
        </div>
      </form>
    </Modal>
  )
}

export default function Prices() {
  const [list, setList] = useState<Charge[] | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [reload, setReload] = useState(0)
  const [form, setForm] = useState<{ charge: Charge | null } | null>(null)

  useEffect(() => {
    let active = true
    api<Charge[]>('/api/service-charges')
      .then((r) => { if (active) { setList(r); setError(null) } })
      .catch((e) => { if (active) setError(detailMsg(e)) })
    return () => { active = false }
  }, [reload])

  return (
    <div>
      <div className="mb-4 flex flex-wrap items-center justify-between gap-3">
        <h1 className="text-2xl font-semibold">Service prices</h1>
        <button className={btnPrimary} onClick={() => setForm({ charge: null })}>Add service</button>
      </div>
      <p className="mb-3 text-sm text-slate-500">Medicine prices are set by the pharmacist in the Pharmacy screen.</p>
      <ErrorBox message={error} />
      {!list && !error ? (
        <Spinner />
      ) : (
        <div className="overflow-x-auto rounded-xl border bg-white">
          <table className="w-full text-left text-sm">
            <thead className="bg-slate-50 text-slate-500">
              <tr>
                <th className="px-3 py-2">Service</th>
                <th className="px-3 py-2">Category</th>
                <th className="px-3 py-2 text-right">Price</th>
                <th className="px-3 py-2">Status</th>
                <th className="px-3 py-2"> </th>
              </tr>
            </thead>
            <tbody>
              {list?.length === 0 && <tr><td colSpan={5} className="px-3 py-6 text-center text-slate-500">No services yet.</td></tr>}
              {list?.map((c) => (
                <tr key={c.id} className={`border-t ${c.active ? '' : 'text-slate-400'}`}>
                  <td className="px-3 py-2 font-medium">{c.name}</td>
                  <td className="px-3 py-2">{nice(c.category)}</td>
                  <td className="px-3 py-2 text-right">{fmtMoney(c.price)}</td>
                  <td className="px-3 py-2">{c.active ? 'Active' : 'Inactive'}</td>
                  <td className="px-3 py-2"><button className={btnSmall} onClick={() => setForm({ charge: c })}>Edit</button></td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
      {form && <ChargeForm charge={form.charge} onClose={() => setForm(null)} onSaved={() => { setForm(null); setReload((n) => n + 1) }} />}
    </div>
  )
}