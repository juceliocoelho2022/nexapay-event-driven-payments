import { useState, type FormEvent } from 'react'
import { Navigate, useNavigate } from 'react-router'
import { useAuth } from '../auth/AuthContext'
import { Icon } from '../components/Icon'
import { ApiError } from '../lib/api'

export function LoginPage() {
  const { login, register, isAuthenticated, loading } = useAuth()
  const navigate = useNavigate()
  const [mode, setMode] = useState<'login' | 'register'>('login')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState('')

  if (!loading && isAuthenticated) {
    return <Navigate to="/" replace />
  }

  async function handleSubmit(event: FormEvent) {
    event.preventDefault()
    setSubmitting(true)
    setError('')

    try {
      if (mode === 'register') {
        await register(email, password)
      } else {
        await login(email, password)
      }
      navigate('/', { replace: true })
    } catch (caught) {
      if (caught instanceof ApiError && caught.status === 429) {
        setError('Muitas tentativas. Aguarde alguns segundos antes de tentar novamente.')
      } else if (caught instanceof Error) {
        setError(caught.message)
      } else {
        setError('Não foi possível concluir a autenticação.')
      }
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <div className="auth-screen recruiter-auth-screen">
      <section className="auth-hero recruiter-auth-hero">
        <div className="brand-lockup">
          <div className="brand-mark large">N</div>
          <div>
            <strong>NexaPay</strong>
            <span>Financial Engineering Platform</span>
          </div>
        </div>

        <div className="hero-copy recruiter-hero-copy">
          <span className="eyebrow">FULL-STACK FINTECH PORTFOLIO</span>
          <h1>Uma experiência bancária moderna sobre arquitetura distribuída.</h1>
          <p>
            Frontend React conectado a uma plataforma Java event-driven com API Gateway,
            autenticação JWT, Kafka, PostgreSQL, resiliência e observabilidade.
          </p>

          <div className="login-tech-stack" aria-label="Stack principal">
            <span>React + TypeScript</span>
            <span>Spring Cloud Gateway</span>
            <span>Kafka</span>
            <span>PostgreSQL</span>
          </div>
        </div>

        <div className="hero-points recruiter-hero-points">
          <div>
            <span className="hero-point-icon"><Icon name="shield" size={18} /></span>
            <p><strong>Security by design</strong>JWT e permissions validados no Gateway e nos serviços.</p>
          </div>
          <div>
            <span className="hero-point-icon"><Icon name="activity" size={18} /></span>
            <p><strong>Event-driven core</strong>Transactional Outbox, Kafka, retry e DLT.</p>
          </div>
          <div>
            <span className="hero-point-icon"><Icon name="pulse" size={18} /></span>
            <p><strong>Observable platform</strong>Prometheus, Grafana, Loki e Alloy.</p>
          </div>
        </div>
      </section>

      <section className="auth-panel recruiter-auth-panel">
        <form className="auth-card recruiter-auth-card" onSubmit={handleSubmit}>
          <div className="auth-product-status">
            <span className="status-dot" />
            <span>Ambiente local operacional</span>
          </div>

          <div className="auth-heading recruiter-auth-heading">
            <span className="eyebrow">SECURE ACCESS</span>
            <h2>{mode === 'login' ? 'Acessar o NexaPay' : 'Criar acesso'}</h2>
            <p>
              {mode === 'login'
                ? 'Entre para acessar o painel financeiro e as operações protegidas.'
                : 'Crie um usuário local para explorar os fluxos da plataforma.'}
            </p>
          </div>

          <label className="field">
            <span>E-mail</span>
            <input
              type="email"
              autoComplete="email"
              required
              value={email}
              onChange={(event) => setEmail(event.target.value)}
              placeholder="voce@exemplo.com"
              aria-describedby="email-hint"
            />
            <small id="email-hint" className="field-hint">Identidade utilizada no JWT da sessão.</small>
          </label>

          <label className="field">
            <span>Senha</span>
            <input
              type="password"
              autoComplete={mode === 'login' ? 'current-password' : 'new-password'}
              required
              minLength={mode === 'register' ? 8 : undefined}
              value={password}
              onChange={(event) => setPassword(event.target.value)}
              placeholder="••••••••"
            />
          </label>

          {error && <div className="alert error" role="alert">{error}</div>}

          <button className="button primary large full recruiter-primary-button" type="submit" disabled={submitting}>
            {submitting ? 'Autenticando...' : mode === 'login' ? 'Entrar no painel' : 'Criar conta e entrar'}
            {!submitting && <Icon name="arrow" size={17} />}
          </button>

          <div className="auth-divider"><span>ou</span></div>

          <button
            className="auth-switch recruiter-auth-switch"
            type="button"
            onClick={() => {
              setMode(mode === 'login' ? 'register' : 'login')
              setError('')
            }}
          >
            {mode === 'login' ? 'Criar usuário de demonstração' : 'Voltar para o login'}
          </button>

          <div className="auth-security-note">
            <Icon name="lock" size={15} />
            <span>Token armazenado somente no <code>sessionStorage</code> durante a sessão.</span>
          </div>
        </form>
      </section>
    </div>
  )
}
