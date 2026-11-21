import { useEffect, useState } from 'react'
import { api, ApiError } from '../api'

interface QueueItem {
  appointmentId: number
  time: string
  patientNo: string
  patientName: string
  doctorName: string
  status: string
  vitalsRecorded: boolean | null
}

interface Summary {
  role: string
  generatedAt: string
  counts: Record<string, unknown>
  queue: QueueItem[]
}

function label(key: string): string {
  const spaced = key.replace(/([A-Z])/g, ' $1').toLowerCase()
  return spaced.charAt(0).toUpperCase() + spaced.slice(1)
}

const money = new Intl.NumberFormat('en-LK', { style: 'currency', currency: 'LKR' })

function show(key: string, value: unknown) {
  if (typeof value === 'number' && /(revenue|collected|outstanding|amount)/i.test(key)) return money.format(value)
  if (typeof value === 'number' || typeof value === 'string') return String(value)
  return ''
}

export default function Dashboard() {
  const [data, setData] = useState<Summary | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    let active = true
    api<Summary>('/api/dashboard/summary')
      .then((r) => { if (active) setData(r) })
      .catch((e) => { if (active) setError(e instanceof ApiError ? e.message : 'Could not load the dashboard.') })
    return () => { active = false }
  }, [])

  if (error) return <p role="alert" className="rounded-md bg-red-50 p-3 text-red-700">{error}</p>
  if (!data) return <p className="text-slate-500">Loading...</p>

  return (
    <div>
      <h1 className="mb-4 text-2xl font-semibold">Dashboard</h1>
      <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
        {Object.entries(data.counts).map(([key, value]) => (
          <div key={key} className="rounded-xl border bg-white p-4">
            <div className="text-sm text-slate-500">{label(key)}</div>
            {typeof value === 'object' && value !== null ? (
              <ul className="mt-1 text-sm">
                {Object.entries(value as Record<string, unknown>).map(([k, v]) => (
                  <li key={k} className="flex justify-between">
                    <span>{label(k)}</span>
                    <span className="font-medium">{String(v)}</span>
                  </li>
                ))}
              </ul>
            ) : (
              <div className="mt-1 text-2xl font-semibold">{show(key, value)}</div>
            )}
          </div>
        ))}
      </div>

      {data.queue.length > 0 && (
        <section className="mt-6">
          <h2 className="mb-2 text-lg font-medium">Today's queue</h2>
          <div className="overflow-x-auto rounded-xl border bg-white">
            <table className="w-full text-left text-sm">
              <thead className="bg-slate-50 text-slate-500">
                <tr>
                  <th className="px-3 py-2">Time</th>
                  <th className="px-3 py-2">Patient</th>
                  <th className="px-3 py-2">Doctor</th>
                  <th className="px-3 py-2">Status</th>
                  <th className="px-3 py-2">Vitals</th>
                </tr>
              </thead>
              <tbody>
                {data.queue.map((q) => (
                  <tr key={q.appointmentId} className="border-t">
                    <td className="px-3 py-2">{q.time.slice(0, 5)}</td>
                    <td className="px-3 py-2">{q.patientName} <span className="text-slate-400">({q.patientNo})</span></td>
                    <td className="px-3 py-2">{q.doctorName}</td>
                    <td className="px-3 py-2">{q.status.replace('_', ' ').toLowerCase()}</td>
                    <td className="px-3 py-2">{q.vitalsRecorded === null ? '' : q.vitalsRecorded ? 'Done' : 'Waiting'}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </section>
      )}
    </div>
  )
}