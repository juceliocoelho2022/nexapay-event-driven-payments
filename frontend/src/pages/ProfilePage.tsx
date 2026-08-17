import { useAuth } from '../auth/AuthContext'

export function ProfilePage() {
  const { user } = useAuth()

  return (
    <div className="page">
      <div className="page-heading">
        <div><span className="eyebrow">AUTH SERVICE</span><h1>Perfil e permissões</h1><p>Informações derivadas do JWT e confirmadas pelo endpoint protegido <code>/api/v1/auth/me</code>.</p></div>
      </div>

      <section className="profile-hero panel">
        <div className="profile-avatar-large">{user?.email.slice(0, 1).toUpperCase()}</div>
        <div><span className="eyebrow">USUÁRIO AUTENTICADO</span><h2>{user?.email}</h2><code>{user?.userId}</code></div>
      </section>

      <section className="content-grid two-columns">
        <article className="panel">
          <div className="panel-heading"><div><span className="eyebrow">ROLES</span><h2>Papéis atribuídos</h2></div></div>
          <div className="permission-cloud">
            {user?.roles.map((role) => <span className="permission-chip role" key={role}>{role}</span>)}
          </div>
        </article>
        <article className="panel">
          <div className="panel-heading"><div><span className="eyebrow">PERMISSIONS</span><h2>Autorizações efetivas</h2></div></div>
          <div className="permission-cloud">
            {user?.permissions.map((permission) => <span className="permission-chip" key={permission}>{permission}</span>)}
          </div>
        </article>
      </section>

      <section className="security-note">
        <strong>Segurança em profundidade</strong>
        <p>O frontend usa as permissions somente para experiência de navegação. A autorização efetiva continua sendo aplicada pelo API Gateway e pelos próprios microsserviços.</p>
      </section>
    </div>
  )
}
