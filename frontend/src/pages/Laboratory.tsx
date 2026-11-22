import { useEffect, useState } from 'react'
import type { FormEvent } from 'react'
import { api } from '../api'
import { useAuth } from '../auth'
import type { Page } from '../types'
import type { LabRequest, LabSummary, LabTest } from '../labTypes'
import { FlagBadge, LabStatusBadge, PriorityBadge, rangeText, ResultsTable } from '../components/LabResults'
import {
  btnGhost, btnPrimary, btnSmall, detailMsg, ErrorBox, Field, fmtDateTime, fmtMoney, inputCls,
  Modal, Pagination, Spinner, Tabs, useDebounced,
} from '../ui'

const CATEGORIES = ['HAEMATOLOGY', 'BIOCHEMISTRY', 'MICROBIOLOGY', 'URINALYSIS', 'IMMUNOLOGY', 'OTHER']
const nice = (s: string) => s.charAt(0) + s.slice(1).toLowerCase()

/* ---------------- one request ---------------- */

function RequestModal({ id, onClose, onChanged }: { id: number; onClose: () => void; onChanged: () => void }) {
  const [req, setReq] = useState<LabRequest | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [message, setMessage] = useState<string | null>(null)
  const [reload, setReload] = useState(0)
  const [sampleNo, setSampleNo] = useState('')
  const [vals, setVals] = useState<Record<number, string>>({})
  const [notes, setNotes] = useState<Record<number, string>>({})
  const [busy, setBusy] = useState(false)
  const [cancelling, setCancelling] = useState(false)
  const [reason, setReason] = useState('')

  useEffect(() => {
    let active = true
    api<LabRequest>(`/api/lab/requests/${id}`)
      .then((r) => {
        if (!active) return
        const v: Record<number, string> = {}
        const n: Record<number, string> = {}
        r.items.forEach((i) => { v[i.id] = i.resultValue ?? ''; n[i.id] = i.resultComment ?? '' })
        setReq(r)
        setVals(v)
        setNotes(n)
        setError(null)
      })
      .catch((e) => { if (active) setError(detailMsg(e)) })
    return () => { active = false }
  }, [id, reload])

  async function run(fn: () => Promise<unknown>, done?: string) {
    setBusy(true)
    setError(null)
    setMessage(null)
    try {
      await fn()
      if (done) setMessage(done)
      setReload((n) => n + 1)
      onChanged()
    } catch (e) {
      setError(detailMsg(e))
    } finally {
      setBusy(false)
    }
  }

  const collect = () =>
    run(() => api(`/api/lab/requests/${id}/collect`, { method: 'POST', body: { sampleNo: sampleNo.trim() || null } }),
      'Sample recorded.')

  const save = () =>
    run(() => api(`/api/lab/requests/${id}/results`, {
      method: 'PUT',
      body: {
        results: (req?.items ?? []).map((i) => ({
          itemId: i.id,
          value: (vals[i.id] ?? '').trim(),
          comment: (notes[i.id] ?? '').trim(),
        })),
      },
    }), 'Results saved.')

  function verify() {
    if (!window.confirm('Release these results? They cannot be edited afterwards and the doctor and patient will be told.')) return
    void run(() => api(`/api/lab/requests/${id}/verify`, { method: 'POST' }), 'Results released.')
  }

  async function submitCancel(e: FormEvent) {
    e.preventDefault()
    await run(() => api(`/api/lab/requests/${id}/cancel`, { method: 'POST', body: { reason: reason.trim() } }))
    setCancelling(false)
  }

  const dirty = !!req && req.items.some(
    (i) => (vals[i.id] ?? '') !== (i.resultValue ?? '') || (notes[i.id] ?? '') !== (i.resultComment ?? ''),
  )
  const allSaved = !!req && req.items.every((i) => i.resultValue != null)

  return (
    <Modal title={req ? `Lab request ${req.requestNo}` : 'Lab request'} onClose={onClose} wide>
      <ErrorBox message={error} />
      {message && <p className="mb-3 rounded-md bg-green-50 p-2 text-sm text-green-800">{message}</p>}
      {!req && !error && <Spinner />}
      {req && (
        <>
          <div className="print-area">
            <div className="mb-3 flex flex-wrap items-start justify-between gap-2 text-sm">
              <div>
                <div className="text-lg font-semibold">{req.patientName} <span className="text-base font-normal text-slate-500">({req.patientNo})</span></div>
                <div className="text-slate-600">Requested by {req.requestedByName} · {fmtDateTime(req.createdAt)}</div>
                {req.clinicalNotes && <div className="text-slate-600">Clinical note: {req.clinicalNotes}</div>}
                {req.sampleNo && <div className="text-slate-600">Sample number: {req.sampleNo}</div>}
                {req.collectedAt && <div className="text-slate-600">Sample collected {fmtDateTime(req.collectedAt)} by {req.collectedByName}</div>}
                {req.verifiedAt && <div className="text-slate-600">Released {fmtDateTime(req.verifiedAt)} by {req.verifiedByName}</div>}
              </div>
              <div className="flex gap-2 no-print"><PriorityBadge priority={req.priority} /><LabStatusBadge status={req.status} /></div>
            </div>

            {req.status === 'CANCELLED' && <p className="mb-3 rounded-md bg-slate-100 p-2 text-sm">Cancelled: {req.cancelReason}</p>}

            {(req.status === 'REQUESTED' || req.status === 'CANCELLED') && (
              <ul className="mb-3 list-disc pl-5 text-sm">
                {req.items.map((i) => <li key={i.id}>{i.testName} <span className="text-slate-500">({i.specimen})</span></li>)}
              </ul>
            )}

            {req.status === 'VERIFIED' && <ResultsTable items={req.items} showResults />}
          </div>

          {req.status === 'COLLECTED' && (
            <div className="overflow-x-auto rounded-lg border">
              <table className="w-full text-left text-sm">
                <thead className="bg-slate-50 text-xs text-slate-500">
                  <tr>
                    <th className="px-3 py-2">Test</th>
                    <th className="px-3 py-2">Result</th>
                    <th className="px-3 py-2">Reference</th>
                    <th className="px-3 py-2">Flag</th>
                    <th className="px-3 py-2">Comment</th>
                  </tr>
                </thead>
                <tbody>
                  {req.items.map((i) => (
                    <tr key={i.id} className="border-t align-top">
                      <td className="px-3 py-2">{i.testName}<div className="text-xs text-slate-400">{i.specimen}</div></td>
                      <td className="px-3 py-2">
                        <div className="flex items-center gap-1">
                          <input
                            className={`${inputCls} ${i.resultType === 'NUMERIC' ? 'w-28' : 'w-44'}`}
                            inputMode={i.resultType === 'NUMERIC' ? 'decimal' : 'text'}
                            value={vals[i.id] ?? ''}
                            onChange={(e) => setVals((p) => ({ ...p, [i.id]: e.target.value }))}
                            aria-label={`Result for ${i.testName}`}
                          />
                          {i.unit && <span className="text-xs text-slate-500">{i.unit}</span>}
                        </div>
                      </td>
                      <td className="px-3 py-2 text-slate-600">{rangeText(i)}</td>
                      <td className="px-3 py-2"><FlagBadge flag={i.flag} /></td>
                      <td className="px-3 py-2">
                        <input
                          className={`${inputCls} w-48`}
                          maxLength={500}
                          value={notes[i.id] ?? ''}
                          onChange={(e) => setNotes((p) => ({ ...p, [i.id]: e.target.value }))}
                          aria-label={`Comment for ${i.testName}`}
                        />
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
          {req.status === 'COLLECTED' && (
            <p className="mt-2 text-xs text-slate-500">
              Flags appear after you save, and compare the result with the reference range. They are prompts only. Release needs a saved result for every test.
            </p>
          )}

          {req.status === 'REQUESTED' && (
            <div className="mt-3 rounded-lg bg-slate-50 p-3 no-print">
              <Field label="Sample number or label (optional)">
                <input className={`${inputCls} max-w-xs`} maxLength={40} value={sampleNo} onChange={(e) => setSampleNo(e.target.value)} />
              </Field>
              <div className="mt-3"><button className={btnPrimary} disabled={busy} onClick={() => void collect()}>Record sample collected</button></div>
            </div>
          )}

          {cancelling && (
            <form onSubmit={(e) => void submitCancel(e)} className="mt-3 rounded-lg border border-amber-300 bg-amber-50 p-3 no-print">
              <Field label="Reason for cancelling (required)">
                <input className={inputCls} value={reason} onChange={(e) => setReason(e.target.value)} maxLength={255} required />
              </Field>
              <div className="mt-3 flex gap-2">
                <button type="submit" className={btnPrimary} disabled={busy || !reason.trim()}>Cancel request</button>
                <button type="button" className={btnGhost} onClick={() => setCancelling(false)}>Back</button>
              </div>
            </form>
          )}

          <div className="mt-5 flex flex-wrap justify-end gap-2 no-print">
            <button className={btnGhost} onClick={onClose}>Close</button>
            {(req.status === 'REQUESTED' || req.status === 'COLLECTED') && !cancelling && (
              <button className={`${btnGhost} text-red-700`} onClick={() => setCancelling(true)}>Cancel request</button>
            )}
            {req.status === 'COLLECTED' && (
              <>
                <button className={btnGhost} disabled={busy || !dirty} onClick={() => void save()}>Save results</button>
                <button
                  className={btnPrimary}
                  disabled={busy || dirty || !allSaved}
                  title={dirty ? 'Save the results first' : !allSaved ? 'Every test needs a saved result' : ''}
                  onClick={verify}
                >
                  Verify and release
                </button>
              </>
            )}
            {req.status === 'VERIFIED' && <button className={btnPrimary} onClick={() => window.print()}>Print report</button>}
          </div>
        </>
      )}
    </Modal>
  )
}

/* ---------------- worklist ---------------- */

function WorklistTab({ reloadKey, onOpen }: { reloadKey: number; onOpen: (id: number) => void }) {
  const [q, setQ] = useState('')
  const dq = useDebounced(q)
  const [status, setStatus] = useState('')
  const [page, setPage] = useState(0)
  const [data, setData] = useState<Page<LabSummary> | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [tick, setTick] = useState(0)

  useEffect(() => {
    const t = setInterval(() => setTick((n) => n + 1), 30000)
    return () => clearInterval(t)
  }, [])

  useEffect(() => { setPage(0) }, [dq, status])

  useEffect(() => {
    let active = true
    api<Page<LabSummary>>('/api/lab/requests', { query: { q: dq, status, page, size: 15 } })
      .then((r) => { if (active) { setData(r); setError(null) } })
      .catch((e) => { if (active) setError(detailMsg(e)) })
    return () => { active = false }
  }, [dq, status, page, reloadKey, tick])

  return (
    <div>
      <div className="mb-3 flex flex-wrap items-end gap-3">
        <Field label="Show">
          <select className={inputCls} value={status} onChange={(e) => setStatus(e.target.value)}>
            <option value="">Waiting (to do)</option>
            <option value="REQUESTED">Waiting for sample</option>
            <option value="COLLECTED">Awaiting results</option>
            <option value="VERIFIED">Released</option>
            <option value="CANCELLED">Cancelled</option>
            <option value="ALL">All</option>
          </select>
        </Field>
        <input className={`${inputCls} max-w-xs`} placeholder="Search patient or request number" value={q} onChange={(e) => setQ(e.target.value)} aria-label="Search requests" />
      </div>
      <ErrorBox message={error} />
      {!data && !error ? (
        <Spinner />
      ) : (
        <div className="overflow-x-auto rounded-xl border bg-white">
          <table className="w-full text-left text-sm">
            <thead className="bg-slate-50 text-slate-500">
              <tr>
                <th className="px-3 py-2">Request</th>
                <th className="px-3 py-2">Patient</th>
                <th className="px-3 py-2">Tests</th>
                <th className="px-3 py-2">Requested by</th>
                <th className="px-3 py-2">Requested</th>
                <th className="px-3 py-2">Status</th>
                <th className="px-3 py-2"> </th>
              </tr>
            </thead>
            <tbody>
              {data?.content.length === 0 && <tr><td colSpan={7} className="px-3 py-6 text-center text-slate-500">No lab requests found.</td></tr>}
              {data?.content.map((r) => (
                <tr key={r.id} className="border-t">
                  <td className="px-3 py-2 font-medium">{r.requestNo} <PriorityBadge priority={r.priority} /></td>
                  <td className="px-3 py-2">{r.patientName} <span className="text-slate-400">({r.patientNo})</span></td>
                  <td className="px-3 py-2">{r.itemCount}</td>
                  <td className="px-3 py-2">{r.requestedByName}</td>
                  <td className="px-3 py-2 whitespace-nowrap">{fmtDateTime(r.createdAt)}</td>
                  <td className="px-3 py-2"><LabStatusBadge status={r.status} /></td>
                  <td className="px-3 py-2"><button className={btnSmall} onClick={() => onOpen(r.id)}>Open</button></td>
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

/* ---------------- test catalogue ---------------- */

function TestForm({ test, onClose, onSaved }: { test: LabTest | null; onClose: () => void; onSaved: () => void }) {
  const [f, setF] = useState({
    name: test?.name ?? '',
    category: test?.category ?? 'BIOCHEMISTRY',
    specimen: test?.specimen ?? 'Blood',
    resultType: test?.resultType ?? 'NUMERIC',
    unit: test?.unit ?? '',
    refLow: test?.refLow != null ? String(test.refLow) : '',
    refHigh: test?.refHigh != null ? String(test.refHigh) : '',
    refText: test?.refText ?? '',
    price: test ? String(test.price) : '',
    active: test?.active ?? true,
  })
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)
  const numeric = f.resultType === 'NUMERIC'

  const text = (k: 'name' | 'specimen' | 'unit' | 'refLow' | 'refHigh' | 'refText' | 'price') =>
    (e: { target: { value: string } }) => setF((p) => ({ ...p, [k]: e.target.value }))

  async function submit(e: FormEvent) {
    e.preventDefault()
    setError(null)
    const price = Number(f.price)
    if (f.price.trim() === '' || Number.isNaN(price) || price < 0) { setError('Enter a price of 0 or more.'); return }
    const low = f.refLow.trim() === '' ? null : Number(f.refLow)
    const high = f.refHigh.trim() === '' ? null : Number(f.refHigh)
    if ((low !== null && Number.isNaN(low)) || (high !== null && Number.isNaN(high))) { setError('Reference limits must be numbers.'); return }
    const body = {
      name: f.name.trim(),
      category: f.category,
      specimen: f.specimen.trim(),
      resultType: f.resultType,
      unit: f.unit.trim() || null,
      refLow: numeric ? low : null,
      refHigh: numeric ? high : null,
      refText: f.refText.trim() || null,
      price,
      active: f.active,
    }
    setBusy(true)
    try {
      if (test) await api(`/api/lab/tests/${test.id}`, { method: 'PUT', body })
      else await api('/api/lab/tests', { method: 'POST', body })
      onSaved()
    } catch (err) {
      setError(detailMsg(err))
    } finally {
      setBusy(false)
    }
  }

  return (
    <Modal title={test ? 'Edit test' : 'Add test'} onClose={onClose}>
      <form onSubmit={submit}>
        <ErrorBox message={error} />
        <div className="grid gap-3 sm:grid-cols-2">
          <Field label="Name" className="sm:col-span-2"><input className={inputCls} value={f.name} onChange={text('name')} required maxLength={120} /></Field>
          <Field label="Category">
            <select className={inputCls} value={f.category} onChange={(e) => setF((p) => ({ ...p, category: e.target.value }))}>
              {CATEGORIES.map((c) => <option key={c} value={c}>{nice(c)}</option>)}
            </select>
          </Field>
          <Field label="Specimen"><input className={inputCls} value={f.specimen} onChange={text('specimen')} required maxLength={40} /></Field>
          <Field label="Result type">
            <select className={inputCls} value={f.resultType} onChange={(e) => setF((p) => ({ ...p, resultType: e.target.value as 'NUMERIC' | 'TEXT' }))}>
              <option value="NUMERIC">Number</option>
              <option value="TEXT">Text</option>
            </select>
          </Field>
          <Field label="Price (LKR)"><input className={inputCls} inputMode="decimal" value={f.price} onChange={text('price')} required /></Field>
          {numeric ? (
            <>
              <Field label="Unit"><input className={inputCls} value={f.unit} onChange={text('unit')} maxLength={30} /></Field>
              <div />
              <Field label="Reference: lower limit"><input className={inputCls} inputMode="decimal" value={f.refLow} onChange={text('refLow')} /></Field>
              <Field label="Reference: upper limit"><input className={inputCls} inputMode="decimal" value={f.refHigh} onChange={text('refHigh')} /></Field>
            </>
          ) : (
            <Field label="Expected result (text)" className="sm:col-span-2">
              <input className={inputCls} placeholder="e.g. Negative" value={f.refText} onChange={text('refText')} maxLength={120} />
            </Field>
          )}
          {test && (
            <label className="flex items-center gap-2 text-sm sm:col-span-2">
              <input type="checkbox" checked={f.active} onChange={(e) => setF((p) => ({ ...p, active: e.target.checked }))} />
              Active (inactive tests cannot be requested)
            </label>
          )}
        </div>
        <p className="mt-3 text-xs text-slate-500">
          Changes never alter requests that were already made, because each request keeps its own copy of the name, range and price.
          Reference ranges should come from your laboratory's own validated values.
        </p>
        <div className="mt-5 flex justify-end gap-2">
          <button type="button" className={btnGhost} onClick={onClose}>Cancel</button>
          <button type="submit" className={btnPrimary} disabled={busy}>{busy ? 'Saving...' : 'Save'}</button>
        </div>
      </form>
    </Modal>
  )
}

function TestsTab() {
  const [list, setList] = useState<LabTest[] | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [reload, setReload] = useState(0)
  const [form, setForm] = useState<{ test: LabTest | null } | null>(null)

  useEffect(() => {
    let active = true
    api<LabTest[]>('/api/lab/tests')
      .then((r) => { if (active) { setList(r); setError(null) } })
      .catch((e) => { if (active) setError(detailMsg(e)) })
    return () => { active = false }
  }, [reload])

  return (
    <div>
      <div className="mb-3 flex justify-end"><button className={btnPrimary} onClick={() => setForm({ test: null })}>Add test</button></div>
      <ErrorBox message={error} />
      {!list && !error ? (
        <Spinner />
      ) : (
        <div className="overflow-x-auto rounded-xl border bg-white">
          <table className="w-full text-left text-sm">
            <thead className="bg-slate-50 text-slate-500">
              <tr>
                <th className="px-3 py-2">Test</th>
                <th className="px-3 py-2">Category</th>
                <th className="px-3 py-2">Specimen</th>
                <th className="px-3 py-2">Reference</th>
                <th className="px-3 py-2 text-right">Price</th>
                <th className="px-3 py-2"> </th>
              </tr>
            </thead>
            <tbody>
              {list?.length === 0 && <tr><td colSpan={6} className="px-3 py-6 text-center text-slate-500">No tests yet.</td></tr>}
              {list?.map((t) => (
                <tr key={t.id} className={`border-t ${t.active ? '' : 'text-slate-400'}`}>
                  <td className="px-3 py-2 font-medium">{t.name}{!t.active && <span className="ml-2 rounded bg-slate-200 px-1.5 py-0.5 text-xs text-slate-600">Inactive</span>}</td>
                  <td className="px-3 py-2">{nice(t.category)}</td>
                  <td className="px-3 py-2">{t.specimen}</td>
                  <td className="px-3 py-2">
                    {t.resultType === 'TEXT'
                      ? t.refText
                      : `${t.refLow ?? ''}${t.refLow != null || t.refHigh != null ? ' - ' : ''}${t.refHigh ?? ''} ${t.unit ?? ''}`}
                  </td>
                  <td className="px-3 py-2 text-right">{fmtMoney(t.price)}</td>
                  <td className="px-3 py-2"><button className={btnSmall} onClick={() => setForm({ test: t })}>Edit</button></td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
      {form && <TestForm test={form.test} onClose={() => setForm(null)} onSaved={() => { setForm(null); setReload((n) => n + 1) }} />}
    </div>
  )
}

/* ---------------- the page ---------------- */

type LTab = 'worklist' | 'tests'

export default function Laboratory() {
  const { user } = useAuth()
  const isLab = user?.role === 'LAB_TECH'
  const [tab, setTab] = useState<LTab>(isLab ? 'worklist' : 'tests')
  const [reload, setReload] = useState(0)
  const [openId, setOpenId] = useState<number | null>(null)

  const tabs: { key: LTab; label: string }[] = [
    ...(isLab ? [{ key: 'worklist' as const, label: 'Worklist' }] : []),
    { key: 'tests', label: 'Test catalogue' },
  ]

  return (
    <div>
      <h1 className="mb-4 text-2xl font-semibold">Laboratory</h1>
      <Tabs tabs={tabs} value={tab} onChange={setTab} />
      {tab === 'worklist' && isLab && <WorklistTab reloadKey={reload} onOpen={setOpenId} />}
      {tab === 'tests' && <TestsTab />}
      {openId !== null && <RequestModal id={openId} onClose={() => setOpenId(null)} onChanged={() => setReload((n) => n + 1)} />}
    </div>
  )
}