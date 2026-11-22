import { useEffect, useState } from 'react'
import { api } from '../api'
import type { Page, Patient } from '../types'
import { btnSmall, detailMsg, ErrorBox, inputCls, useDebounced } from '../ui'

export default function PatientPicker({ value, onChange }: { value: Patient | null; onChange: (p: Patient | null) => void }) {
  const [q, setQ] = useState('')
  const dq = useDebounced(q)
  const [results, setResults] = useState<Patient[]>([])
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    let active = true
    if (dq.trim().length < 2) {
      setResults([])
      return
    }
    api<Page<Patient>>('/api/patients', { query: { q: dq, size: 6 } })
      .then((r) => { if (active) setResults(r.content) })
      .catch((e) => { if (active) setError(detailMsg(e)) })
    return () => { active = false }
  }, [dq])

  if (value) {
    return (
      <div className="flex items-center justify-between rounded-md border bg-slate-50 px-3 py-2 text-sm">
        <span><span className="font-medium">{value.patientNo}</span> {value.fullName}</span>
        <button type="button" className={btnSmall} onClick={() => onChange(null)}>Change</button>
      </div>
    )
  }
  return (
    <div>
      <input
        className={inputCls}
        placeholder="Type at least 2 letters of the name, phone or number"
        value={q}
        onChange={(e) => setQ(e.target.value)}
      />
      <ErrorBox message={error} />
      {results.length > 0 && (
        <ul className="mt-1 rounded-md border bg-white">
          {results.map((p) => (
            <li key={p.id}>
              <button type="button" className="block w-full px-3 py-2 text-left text-sm hover:bg-teal-50" onClick={() => onChange(p)}>
                <span className="font-medium">{p.patientNo}</span> {p.fullName}
                <span className="text-slate-400"> · {p.phone}</span>
              </button>
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}