import { useEffect, useState } from 'react'
import { api } from '../api'
import type { Doctor } from '../types'
import {
  btnGhost, btnSmall, detailMsg, downloadCsv, ErrorBox, Field, fmtDate, fmtDateTime, fmtMoney, inputCls,
  Spinner, Tabs, todayStr,
} from '../ui'

type Kind = 'number' | 'money' | 'percent' | 'text'
type Cell = string | number | null

interface ReportInfo {
  key: string
  title: string
  description: string
}

interface Metric {
  label: string
  value: Cell
  kind: Kind
}

interface Col {
  label: string
  kind: Kind
}

interface Section {
  title: string
  note: string | null
  columns: Col[]
  rows: Cell[][]
}

interface Report {
  key: string
  title: string
  from: string
  to: string
  generatedAt: string
  metrics: Metric[]
  sections: Section[]
}

const DATE_RE = /^\d{4}-\d{2}-\d{2}$/

function shift(s: string, days: number): string {
  const [y, m, d] = s.split('-').map(Number)
  return new Date(Date.UTC(y, m - 1, d + days)).toISOString().slice(0, 10)
}

function monthRange(offset: number): [string, string] {
  const [y, m] = todayStr().split('-').map(Number)
  const first = new Date(Date.UTC(y, m - 1 + offset, 1))
  const last = new Date(Date.UTC(y, m + offset, 0))
  return [first.toISOString().slice(0, 10), last.toISOString().slice(0, 10)]
}

function fmtValue(v: Cell, kind: Kind): string {
  if (v === null || v === undefined || v === '') return kind === 'text' ? '' : '-'
  if (kind === 'money') return fmtMoney(Number(v))
  if (kind === 'percent') return `${v}%`
  if (kind === 'number') return Number(v).toLocaleString()
  const s = String(v)
  return DATE_RE.test(s) ? fmtDate(s) : s
}

function exportCsv(r: Report) {
  const rows: (string | number | null)[][] = [
    [r.title],
    ['From', r.from],
    ['To', r.to],
    ['Generated', fmtDateTime(r.generatedAt)],
    [],
    ['Summary'],
  ]
  r.metrics.forEach((m) => rows.push([m.label, m.value]))
  r.sections.forEach((s) => {
    rows.push([])
    rows.push([s.title])
    rows.push(s.columns.map((c) => c.label))
    s.rows.forEach((x) => rows.push(x))
  })
  downloadCsv(`${r.key}-report-${r.from}-to-${r.to}.csv`, rows)
}

export default function Reports() {
  const today = todayStr()
  const [list, setList] = useState<ReportInfo[] | null>(null)
  const [key, setKey] = useState('')
  const [from, setFrom] = useState(today.slice(0, 8) + '01')
  const [to, setTo] = useState(today)
  const [doctorId, setDoctorId] = useState('')
  const [doctors, setDoctors] = useState<Doctor[]>([])
  const [data, setData] = useState<Report | null>(null)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    let active = true
    api<ReportInfo[]>('/api/reports')
      .then((r) => { if (active) { setList(r); if (r.length > 0) setKey(r[0].key) } })
      .catch((e) => { if (active) setError(detailMsg(e)) })
    api<Doctor[]>('/api/doctors').then((d) => { if (active) setDoctors(d) }).catch(() => { /* optional filter */ })
    return () => { active = false }
  }, [])

  useEffect(() => {
    if (!key) return
    if (from > to) {
      setError('The end date is before the start date.')
      setData(null)
      return
    }
    let active = true
    setLoading(true)
    setError(null)
    api<Report>(`/api/reports/${key}`, { query: { from, to, doctorId: key === 'appointments' ? doctorId : '' } })
      .then((r) => { if (active) setData(r) })
      .catch((e) => { if (active) { setData(null); setError(detailMsg(e)) } })
      .finally(() => { if (active) setLoading(false) })
    return () => { active = false }
  }, [key, from, to, doctorId])

  function preset(a: string, b: string) {
    setFrom(a)
    setTo(b)
  }

  if (!list && !error) return <Spinner />
  if (list && list.length === 0) return <p className="text-slate-500">There are no reports available for your role.</p>

  const info = list?.find((r) => r.key === key)
  const [lastFrom, lastTo] = monthRange(-1)

  return (
    <div>
      <h1 className="mb-1 text-2xl font-semibold">Reports</h1>
      <p className="mb-4 text-sm text-slate-500">{info?.description}</p>

      {list && list.length > 1 && (
        <Tabs tabs={list.map((r) => ({ key: r.key, label: r.title.replace(' report', '') }))} value={key} onChange={(k) => { setKey(k); setData(null) }} />
      )}

      <div className="no-print mb-4 rounded-xl border bg-white p-4">
        <div className="flex flex-wrap items-end gap-3">
          <Field label="From"><input type="date" className={inputCls} value={from} max={to} onChange={(e) => e.target.value && setFrom(e.target.value)} /></Field>
          <Field label="To"><input type="date" className={inputCls} value={to} min={from} max={today} onChange={(e) => e.target.value && setTo(e.target.value)} /></Field>
          {key === 'appointments' && (
            <Field label="Doctor">
              <select className={inputCls} value={doctorId} onChange={(e) => setDoctorId(e.target.value)}>
                <option value="">All doctors</option>
                {doctors.map((d) => <option key={d.id} value={d.id}>{d.fullName}</option>)}
              </select>
            </Field>
          )}
          <div className="ml-auto flex gap-2">
            <button className={btnGhost} disabled={!data} onClick={() => data && exportCsv(data)}>Export CSV</button>
            <button className={btnGhost} disabled={!data} onClick={() => window.print()}>Print</button>
          </div>
        </div>
        <div className="mt-3 flex flex-wrap gap-2">
          <button className={btnSmall} onClick={() => preset(today, today)}>Today</button>
          <button className={btnSmall} onClick={() => preset(shift(today, -6), today)}>Last 7 days</button>
          <button className={btnSmall} onClick={() => preset(shift(today, -29), today)}>Last 30 days</button>
          <button className={btnSmall} onClick={() => preset(today.slice(0, 8) + '01', today)}>This month</button>
          <button className={btnSmall} onClick={() => preset(lastFrom, lastTo)}>Last month</button>
          <button className={btnSmall} onClick={() => preset(today.slice(0, 4) + '-01-01', today)}>This year</button>
        </div>
      </div>

      <ErrorBox message={error} />
      {loading && !data && <Spinner text="Preparing the report..." />}

      {data && (
        <div className={`print-area ${loading ? 'opacity-60' : ''}`}>
          <div className="mb-3">
            <div className="text-lg font-semibold">CareSync Hospital: {data.title}</div>
            <div className="text-sm text-slate-600">
              {fmtDate(data.from)} to {fmtDate(data.to)} · prepared {fmtDateTime(data.generatedAt)}
            </div>
          </div>

          <div className="mb-6 grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
            {data.metrics.map((m) => (
              <div key={m.label} className="rounded-xl border bg-white p-3">
                <div className="text-xs text-slate-500">{m.label}</div>
                <div className="mt-1 text-xl font-semibold">{fmtValue(m.value, m.kind)}</div>
              </div>
            ))}
          </div>

          {data.sections.map((s) => (
            <section key={s.title} className="mb-6">
              <h2 className="mb-1 text-lg font-medium">{s.title}</h2>
              {s.note && <p className="mb-2 text-xs text-slate-500">{s.note}</p>}
              <div className="overflow-x-auto rounded-xl border bg-white">
                <table className="w-full text-left text-sm">
                  <thead className="bg-slate-50 text-slate-500">
                    <tr>
                      {s.columns.map((c) => (
                        <th key={c.label} className={`px-3 py-2 ${c.kind === 'text' ? '' : 'text-right'}`}>{c.label}</th>
                      ))}
                    </tr>
                  </thead>
                  <tbody>
                    {s.rows.length === 0 && (
                      <tr><td colSpan={s.columns.length} className="px-3 py-4 text-center text-slate-500">No data for this period.</td></tr>
                    )}
                    {s.rows.map((row, i) => (
                      <tr key={i} className="border-t">
                        {row.map((v, j) => (
                          <td key={j} className={`px-3 py-2 ${s.columns[j]?.kind === 'text' ? '' : 'text-right'}`}>
                            {fmtValue(v, s.columns[j]?.kind ?? 'text')}
                          </td>
                        ))}
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </section>
          ))}
        </div>
      )}
    </div>
  )
}