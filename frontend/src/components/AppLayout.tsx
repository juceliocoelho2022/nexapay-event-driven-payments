import { NavLink, Outlet, useLocation, useNavigate } from 'react-router'
import { useAuth } from '../auth/AuthContext'
import { Icon, type IconName } from './Icon'

type NavigationItem = {
  to: string
  label: string
  icon: IconName
  end?: boolean
}

const coreNavigation: NavigationItem[] = [
  { to: '/', label: 'Visão geral', icon: 'dashboard', end: true },
  { to: '/accounts', label: 'Contas', icon: 'accounts' },
  { to: '/payments', label: 'Pagamentos', icon: 'payments' },
  { to: '/ledger', label: 'Movimentações', icon: 'ledger' },
]

const routeTitles: Record<string, { title: string; eyebrow: string }> = {
  '/': { title: 'Visão geral', eyebrow: 'CONTROL CENTER' },
  '/accounts': { title: 'Contas', eyebrow: 'BANKING OPERATIONS' },
  '/payments': { title: 'Pagamentos', eyebrow: 'PIX OPERATIONS' },
  '/ledger': { title: 'Movimentações', eyebrow: 'EVENT LEDGER' },
  '/fraud': { title: 'Antifraude', eyebrow: 'RISK ENGINE' },
  '/profile': { title: 'Perfil e acesso', eyebrow: 'IDENTITY & ACCESS' },
}

export function AppLayout() {
  const { user, logout, hasPermission } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const currentRoute = routeTitles[location.pathname] ?? routeTitles['/']

  function signOut() {
    logout()
    navigate('/login', { replace: true })
  }

  return (
    <div className="app-shell professional-shell">
      <a className="skip-link" href="#main-content">Pular para o conteúdo</a>

      <aside className="sidebar professional-sidebar">
        <div className="sidebar-brand">
          <div className="brand-mark">N</div>
          <div>
            <strong>NexaPay</strong>
            <span>Financial Platform</span>
          </div>
        </div>

        <div className="sidebar-context">
          <span className="environment-dot" />
          <div>
            <strong>Portfolio environment</strong>
            <small>Gateway · localhost:8080</small>
          </div>
        </div>

        <span className="nav-section-label">Workspace</span>
        <nav className="nav-list" aria-label="Navegação principal">
          {coreNavigation.map((item) => (
            <NavLink
              key={item.to}
              to={item.to}
              end={item.end}
              className={({ isActive }) => `nav-item ${isActive ? 'active' : ''}`}
            >
              <span className="nav-icon"><Icon name={item.icon} /></span>
              <span>{item.label}</span>
            </NavLink>
          ))}

          {hasPermission('FRAUD_READ') && (
            <NavLink
              to="/fraud"
              className={({ isActive }) => `nav-item ${isActive ? 'active' : ''}`}
            >
              <span className="nav-icon"><Icon name="fraud" /></span>
              <span>Antifraude</span>
            </NavLink>
          )}
        </nav>

        <div className="sidebar-footer">
          <div className="security-summary">
            <span className="security-summary-icon"><Icon name="shield" size={18} /></span>
            <div>
              <strong>Secure session</strong>
              <small>JWT + permissions</small>
            </div>
          </div>

          <NavLink
            to="/profile"
            className={({ isActive }) => `profile-link ${isActive ? 'active' : ''}`}
          >
            <span className="avatar">{user?.email.slice(0, 1).toUpperCase()}</span>
            <span className="profile-copy">
              <strong>{user?.email}</strong>
              <small>{user?.roles.join(', ')}</small>
            </span>
            <Icon name="arrow" size={16} />
          </NavLink>

          <button className="button ghost full logout-button" type="button" onClick={signOut}>
            <Icon name="logout" size={16} />
            Sair da sessão
          </button>
        </div>
      </aside>

      <main className="main-content" id="main-content">
        <header className="app-topbar">
          <div className="topbar-copy">
            <span>{currentRoute.eyebrow}</span>
            <strong>{currentRoute.title}</strong>
          </div>
          <div className="topbar-actions">
            <span className="topbar-connection"><span className="status-dot" /> API Gateway</span>
            <NavLink to="/profile" className="topbar-avatar" aria-label="Abrir perfil">
              {user?.email.slice(0, 1).toUpperCase()}
            </NavLink>
          </div>
        </header>

        <header className="mobile-header">
          <div className="sidebar-brand compact">
            <div className="brand-mark small">N</div>
            <div>
              <strong>NexaPay</strong>
              <span>{currentRoute.title}</span>
            </div>
          </div>
          <button className="button ghost icon-button" type="button" onClick={signOut} aria-label="Sair">
            <Icon name="logout" size={18} />
          </button>
        </header>
        <Outlet />
      </main>
    </div>
  )
}
