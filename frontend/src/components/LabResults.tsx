import type { Flag, LabItem, LabStatus, Priority } from '../labTypes'
import { LAB_STATUS_LABEL } from '../labTypes'

export function rangeText(i: LabItem): string {
  if (i.resultType === 'TEXT') return i.refText ?? ''
  if (i.refLow != null && i.refHigh != null) return `${i.refLow} - ${i.refHigh}`
  if (i.refLow != null) return `at least ${i.refLow}`
  if (i.refHigh != null) return `up to ${i.refHigh}`
  return ''
}

const FLAG_STYLE: Record<Flag, string> = {
  NORMAL: 'bg-green-100 text-green-800',
  LOW: 'bg-amber-100 text-amber-800',
  HIGH: 'bg-red-100 text-red-700',
}

const FLAG_LABEL: Record<Flag, string> = { NORMAL: 'Normal', LOW: 'Low', HIGH: 'High' }

export function FlagBadge({ flag }: { flag: Flag | null }) {
  if (!flag) return null
  return <span className={`rounded px-1.5 py-0.5 text-xs font-medium ${FLAG_STYLE[flag]}`}>{FLAG_LABEL[flag]}</span>
}

const STATUS_STYLE: Record<LabStatus, string> = {
  REQUESTED: 'bg-sky-100 text-sky-800',
  COLLECTED: 'bg-amber-100 text-amber-800',
  VERIFIED: 'bg-green-100 text-green-800',
  CANCELLED: 'bg-slate-200 text-slate-600',
}

export function LabStatusBadge({ status }: { status: LabStatus }) {
  return (
    <span className={`rounded-full px-2 py-0.5 text-xs font-medium ${STATUS_STYLE[status]}`}>
      {LAB_STATUS_LABEL[status]}
    </span>
  )
}

export function PriorityBadge({ priority }: { priority: Priority }) {
  if (priority !== 'URGENT') return null
  return <span className="rounded-full bg-red-600 px-2 py-0.5 text-xs font-medium text-white">Urgent</span>
}

export function ResultsTable({ items, showResults }: { items: LabItem[]; showResults: boolean }) {
  return (
    <div className="overflow-x-auto">
      <table className="w-full text-left text-sm">
        <thead className="border-b text-xs text-slate-500">
          <tr>
            <th className="py-1 pr-3">Test</th>
            <th className="py-1 pr-3">Specimen</th>
            <th className="py-1 pr-3">Result</th>
            <th className="py-1 pr-3">Reference</th>
            <th className="py-1">Flag</th>
          </tr>
        </thead>
        <tbody>
          {items.map((i) => (
            <tr key={i.id} className="border-b align-top">
              <td className="py-1 pr-3">
                {i.testName}
                {showResults && i.resultComment && <div className="text-xs text-slate-500">{i.resultComment}</div>}
              </td>
              <td className="py-1 pr-3">{i.specimen}</td>
              <td className="py-1 pr-3 font-medium">
                {showResults && i.resultValue != null ? (
                  <>{i.resultValue}{i.unit && <span className="font-normal text-slate-500"> {i.unit}</span>}</>
                ) : (
                  <span className="font-normal text-slate-400">Pending</span>
                )}
              </td>
              <td className="py-1 pr-3 text-slate-600">{rangeText(i)}{i.resultType === 'NUMERIC' && i.unit && rangeText(i) ? ` ${i.unit}` : ''}</td>
              <td className="py-1">{showResults && <FlagBadge flag={i.flag} />}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}