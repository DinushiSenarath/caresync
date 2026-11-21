import { NavLink, Outlet } from 'react-router-dom'
import { useAuth } from '../auth'
import { MENU, ROLE_LABEL } from '../menu'
import NotificationBell from './NotificationBell'

export default function Layout() {
  const { user, logout } = useAuth()
  if (!user) return null
  const items = MENU.filter((m) => m.roles.includes(user.role))

  return (
    <div className="min-h-screen bg-slate-50 text-slate-900 md:flex">
      <aside className="bg-teal-800 text-white md:min-h-screen md:w-64 md:shrink-0">
        <div className="px-5 py-4 text-xl font-semibold">CareSync</div>
        <nav className="flex gap-1 overflow-x-auto px-2 pb-3 md:block md:space-y-1" aria-label="Main">
          {items.map((i) => (
            <NavLink
              key={i.path}
              to={i.path}
              end={i.path === '/portal'}
              className={({ isActive }) =>
                `block whitespace-nowrap rounded-md px-3 py-2 text-sm hover:bg-teal-700 ${isActive ? 'bg-teal-700 font-medium' : ''}`
              }
            >
              {i.label}
            </NavLink>
          ))}
        </nav>
      </aside>
      <div className="min-w-0 flex-1">
        <header className="flex items-center justify-between border-b bg-white px-5 py-3">
          <div>
            <div className="font-medium">{user.fullName}</div>
            <div className="text-sm text-slate-500">{ROLE_LABEL[user.role]}</div>
          </div>
          <div className="flex items-center gap-3">
            <NotificationBell />
            <button
              onClick={() => logout()}
              className="rounded-md border px-3 py-1.5 text-sm hover:bg-slate-100 focus:outline-2 focus:outline-teal-600"
            >
              Sign out
            </button>
          </div>
        </header>
        <main className="p-5">
          <Outlet />
        </main>
      </div>
    </div>
  )
}
