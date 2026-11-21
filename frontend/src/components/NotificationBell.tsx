import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { api } from '../api'

interface Note {
  id: number
  type: string
  message: string
  link: string | null
  read: boolean
  createdAt: string
}

interface Page<T> {
  content: T[]
}

export default function NotificationBell() {
  const navigate = useNavigate()
  const [unread, setUnread] = useState(0)
  const [open, setOpen] = useState(false)
  const [items, setItems] = useState<Note[]>([])

  async function loadCount() {
    try {
      const r = await api<{ unread: number }>('/api/notifications/unread-count')
      setUnread(r.unread)
    } catch {
      /* the bell is optional, so errors are ignored */
    }
  }

  useEffect(() => {
    let active = true
    async function poll() {
      try {
        const r = await api<{ unread: number }>('/api/notifications/unread-count')
        if (active) setUnread(r.unread)
      } catch {
        /* ignore */
      }
    }
    poll()
    const id = setInterval(poll, 30000)
    return () => {
      active = false
      clearInterval(id)
    }
  }, [])

  async function toggle() {
    const next = !open
    setOpen(next)
    if (next) {
      try {
        const r = await api<Page<Note>>('/api/notifications', { query: { size: 10 } })
        setItems(r.content)
      } catch {
        setItems([])
      }
    }
  }

  async function openNote(n: Note) {
    try {
      if (!n.read) await api(`/api/notifications/${n.id}/read`, { method: 'POST' })
    } finally {
      setOpen(false)
      loadCount()
      if (n.link) navigate(n.link)
    }
  }

  async function markAll() {
    await api('/api/notifications/read-all', { method: 'POST' })
    setItems((list) => list.map((n) => ({ ...n, read: true })))
    setUnread(0)
  }

  return (
    <div className="relative">
      <button
        onClick={toggle}
        aria-label={`Notifications, ${unread} unread`}
        className="relative rounded-full p-2 hover:bg-slate-100 focus:outline-2 focus:outline-teal-600"
      >
        <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
          <path d="M6 8a6 6 0 0 1 12 0c0 7 3 9 3 9H3s3-2 3-9" />
          <path d="M10.3 21a1.94 1.94 0 0 0 3.4 0" />
        </svg>
        {unread > 0 && (
          <span className="absolute -right-0.5 -top-0.5 min-w-5 rounded-full bg-red-600 px-1 text-center text-xs font-semibold text-white">
            {unread > 99 ? '99+' : unread}
          </span>
        )}
      </button>
      {open && (
        <div className="absolute right-0 z-20 mt-2 w-80 max-w-[90vw] rounded-lg border bg-white shadow-lg">
          <div className="flex items-center justify-between border-b px-3 py-2">
            <span className="font-medium">Notifications</span>
            <button onClick={markAll} className="text-sm text-teal-700 hover:underline">Mark all read</button>
          </div>
          <ul className="max-h-80 overflow-y-auto">
            {items.length === 0 && <li className="px-3 py-4 text-sm text-slate-500">Nothing here yet.</li>}
            {items.map((n) => (
              <li key={n.id}>
                <button
                  onClick={() => openNote(n)}
                  className={`block w-full border-b px-3 py-2 text-left text-sm hover:bg-slate-50 ${n.read ? 'text-slate-500' : 'font-medium'}`}
                >
                  {n.message}
                  <span className="block text-xs font-normal text-slate-400">
                    {new Date(n.createdAt).toLocaleString()}
                  </span>
                </button>
              </li>
            ))}
          </ul>
        </div>
      )}
    </div>
  )
}