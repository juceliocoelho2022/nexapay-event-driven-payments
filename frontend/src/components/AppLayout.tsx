import { NavLink, Outlet, useNavigate } from 'react-router'
import { useAuth } from '../auth/AuthContext'

const coreNavigation = [
  { to: '/', label: 'Visão geral', icon: '◫', end: true },
  { to: '/accounts', label: 'Contas', icon: '▣' },
  { to: '/payments', label: 'Pagamentos', icon: '↗' },
  { to: '/ledger', label: 'Movimentações', icon: '≋' },
]

export function AppLayout() {
  const { user, logout, hasPermission } = useAuth()
  const navigate = useNavigate()

  function signOut() {
    logout()
    navigate('/login', { replace: true })
  }

  return (
    <div className="app-shell">
      <aside className="sidebar">
        <div className="sidebar-brand">
          <div className="brand-mark">N</div>
          <div>
            <strong>NexaPay</strong>
            <span>Event-Driven Payments</span>
          </div>
        </div>

        <nav className="nav-list" aria-label="Navegação principal">
          {coreNavigation.map((item) => (
            <NavLink
              key={item.to}
              to={item.to}
              end={item.end}
              className={({ isActive }) => `nav-item ${isActive ? 'active' : ''}`}
            >
              <span className="nav-icon">{item.icon}</span>
              {item.label}
            </NavLink>
          ))}

          {hasPermission('FRAUD_READ') && (
            <NavLink
              to="/fraud"
              className={({ isActive }) => `nav-item ${isActive ? 'active' : ''}`}
            >
              <span className="nav-icon">◇</span>
              Antifraude
            </NavLink>
          )}
        </nav>

        <div className="sidebar-footer">
          <NavLink
            to="/profile"
            className={({ isActive }) => `profile-link ${isActive ? 'active' : ''}`}
          >
            <span className="avatar">{user?.email.slice(0, 1).toUpperCase()}</span>
            <span className="profile-copy">
              <strong>{user?.email}</strong>
              <small>{user?.roles.join(', ')}</small>
            </span>
          </NavLink>
          <button className="button ghost full" type="button" onClick={signOut}>
            Sair
          </button>
        </div>
      </aside>

      <main className="main-content">
        <header className="mobile-header">
          <div className="sidebar-brand compact">
            <div className="brand-mark small">N</div>
            <strong>NexaPay</strong>
          </div>
          <button className="button ghost" type="button" onClick={signOut}>Sair</button>
        </header>
        <Outlet />
      </main>
    </div>
  )
}
