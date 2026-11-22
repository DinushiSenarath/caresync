import { useEffect, useRef, useState } from 'react'
import type { ReactNode } from 'react'
import { ApiError } from './api'
import type { AppointmentStatus } from './types'

export const inputCls =
  'w-full rounded-md border px-3 py-2 text-sm focus:outline-2 focus:outline-teal-600 disabled:bg-slate-100'
export const btnPrimary =
  'rounded-md bg-teal-700 px-3 py-2 text-sm font-medium text-white hover:bg-teal-800 disabled:opacity-60'
export const btnGhost = 'rounded-md border px-3 py-2 text-sm hover:bg-slate-100 disabled:opacity-60'
export const btnSmall = 'rounded border px-2 py-1 text-xs hover:bg-slate-100 disabled:opacity-60'

export function errMsg(e: unknown): string {
  return e instanceof ApiError ? e.message : 'Something went wrong. Please try again.'
}

/** Today's date in the hospital's time zone, as YYYY-MM-DD. */
export function todayStr(): string {
  return new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Colombo' }).format(new Date())
}

export function fmtDate(s: string | null | undefined): string {
  if (!s) return ''
  const [y, m, d] = s.split('-').map(Number)
  return new Date(y, m - 1, d).toLocaleDateString(undefined, { day: 'numeric', month: 'short', year: 'numeric' })
}

export function fmtTime(s: string | null | undefined): string {
  return s ? s.slice(0, 5) : ''
}

export function ageOf(dob: string | null): string {
  if (!dob) return ''
  const [y, m, d] = dob.split('-').map(Number)
  const [ty, tm, td] = todayStr().split('-').map(Number)
  let age = ty - y
  if (tm < m || (tm === m && td < d)) age--
  return String(age)
}

export function useDebounced<T>(value: T, ms = 350): T {
  const [v, setV] = useState(value)
  useEffect(() => {
    const t = setTimeout(() => setV(value), ms)
    return () => clearTimeout(t)
  }, [value, ms])
  return v
}

export function ErrorBox({ message }: { message: string | null }) {
  if (!message) return null
  return (
    <p role="alert" className="mb-3 rounded-md bg-red-50 p-3 text-sm text-red-700">
      {message}
    </p>
  )
}

export function Spinner({ text = 'Loading...' }: { text?: string }) {
  return <p className="py-6 text-center text-slate-500">{text}</p>
}

export function Field({
  label, error, children, className,
}: { label: string; error?: string; children: ReactNode; className?: string }) {
  return (
    <label className={`block ${className ?? ''}`}>
      <span className="mb-1 block text-sm font-medium">{label}</span>
      {children}
      {error && <span className="mt-1 block text-xs text-red-600">{error}</span>}
    </label>
  )
}

export function Modal({
  title, onClose, children, wide,
}: { title: string; onClose: () => void; children: ReactNode; wide?: boolean }) {
  const closeRef = useRef(onClose)
  const panelRef = useRef<HTMLDivElement>(null)
  useEffect(() => {
    closeRef.current = onClose
  })
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') closeRef.current()
    }
    window.addEventListener('keydown', onKey)
    panelRef.current?.focus()
    return () => window.removeEventListener('keydown', onKey)
  }, [])
  return (
    <div className="fixed inset-0 z-30 flex items-start justify-center overflow-y-auto bg-black/40 p-4">
      <div
        ref={panelRef}
        tabIndex={-1}
        role="dialog"
        aria-modal="true"
        aria-label={title}
        className={`mt-8 w-full ${wide ? 'max-w-3xl' : 'max-w-lg'} rounded-xl bg-white p-5 shadow-xl focus:outline-none`}
      >
        <div className="mb-4 flex items-start justify-between gap-3">
          <h2 className="text-lg font-semibold">{title}</h2>
          <button onClick={onClose} aria-label="Close" className="rounded p-1 text-slate-500 hover:bg-slate-100">
            ✕
          </button>
        </div>
        {children}
      </div>
    </div>
  )
}

export function Pagination({
  page, totalPages, onPage,
}: { page: number; totalPages: number; onPage: (p: number) => void }) {
  if (totalPages <= 1) return null
  return (
    <div className="mt-3 flex items-center justify-between text-sm">
      <button className={btnSmall} disabled={page === 0} onClick={() => onPage(page - 1)}>Previous</button>
      <span>Page {page + 1} of {totalPages}</span>
      <button className={btnSmall} disabled={page + 1 >= totalPages} onClick={() => onPage(page + 1)}>Next</button>
    </div>
  )
}

export const STATUS_LABEL: Record<AppointmentStatus, string> = {
  REQUESTED: 'Requested',
  SCHEDULED: 'Scheduled',
  CHECKED_IN: 'Checked in',
  IN_PROGRESS: 'In progress',
  COMPLETED: 'Completed',
  CANCELLED: 'Cancelled',
  MISSED: 'Missed',
}

const STATUS_STYLE: Record<AppointmentStatus, string> = {
  REQUESTED: 'bg-violet-100 text-violet-800',
  SCHEDULED: 'bg-sky-100 text-sky-800',
  CHECKED_IN: 'bg-amber-100 text-amber-800',
  IN_PROGRESS: 'bg-orange-100 text-orange-800',
  COMPLETED: 'bg-green-100 text-green-800',
  CANCELLED: 'bg-slate-200 text-slate-600',
  MISSED: 'bg-red-100 text-red-700',
}

export function StatusBadge({ status }: { status: AppointmentStatus }) {
  return (
    <span className={`rounded-full px-2 py-0.5 text-xs font-medium ${STATUS_STYLE[status]}`}>
      {STATUS_LABEL[status]}
    </span>
  )
}

const lkr = new Intl.NumberFormat('en-LK', { style: 'currency', currency: 'LKR' })

export function fmtMoney(n: number | null | undefined): string {
  return n == null ? '' : lkr.format(n)
}

export function fmtDateTime(s: string | null | undefined): string {
  return s ? new Date(s).toLocaleString() : ''
}

export function detailMsg(e: unknown): string {
  if (e instanceof ApiError && e.fieldErrors) {
    const parts = Object.values(e.fieldErrors)
    if (parts.length > 0) return `${e.message}: ${parts.join('; ')}`
  }
  return errMsg(e)
}

export function Tabs<T extends string>({
  tabs, value, onChange,
}: { tabs: { key: T; label: string }[]; value: T; onChange: (k: T) => void }) {
  return (
    <div className="mb-4 flex flex-wrap gap-1 border-b">
      {tabs.map((t) => (
        <button
          key={t.key}
          onClick={() => onChange(t.key)}
          className={`-mb-px border-b-2 px-4 py-2 text-sm ${
            value === t.key
              ? 'border-teal-700 font-medium text-teal-800'
              : 'border-transparent text-slate-600 hover:text-slate-900'
          }`}
        >
          {t.label}
        </button>
      ))}
    </div>
  )
}