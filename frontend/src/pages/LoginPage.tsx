import { useState, type FormEvent } from 'react'
import { Navigate, useNavigate } from 'react-router'
import { useAuth } from '../auth/AuthContext'
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
    <div className="auth-screen">
      <section className="auth-hero">
        <div className="brand-lockup">
          <div className="brand-mark large">N</div>
          <div>
            <strong>NexaPay</strong>
            <span>Event-Driven Payment Platform</span>
          </div>
        </div>

        <div className="hero-copy">
          <span className="eyebrow">SPRINT 9 · FRONTEND</span>
          <h1>Controle financeiro com arquitetura distribuída por trás.</h1>
          <p>
            Uma interface única para acessar contas, PIX, movimentações e análise antifraude
            através do API Gateway do NexaPay.
          </p>
        </div>

        <div className="hero-points">
          <div><span>01</span><p>JWT e permissions validados no Gateway.</p></div>
          <div><span>02</span><p>Rate limiting distribuído com Redis.</p></div>
          <div><span>03</span><p>Eventos Kafka com observabilidade completa.</p></div>
        </div>
      </section>

      <section className="auth-panel">
        <form className="auth-card" onSubmit={handleSubmit}>
          <div className="auth-heading">
            <span className="status-dot" />
            <small>Gateway · localhost:8080</small>
            <h2>{mode === 'login' ? 'Acessar o NexaPay' : 'Criar acesso'}</h2>
            <p>
              {mode === 'login'
                ? 'Use sua conta para entrar no painel.'
                : 'Cadastre um usuário e entre automaticamente.'}
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
            />
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

          {error && <div className="alert error">{error}</div>}

          <button className="button primary large full" type="submit" disabled={submitting}>
            {submitting ? 'Processando...' : mode === 'login' ? 'Entrar no painel' : 'Criar conta e entrar'}
          </button>

          <button
            className="auth-switch"
            type="button"
            onClick={() => {
              setMode(mode === 'login' ? 'register' : 'login')
              setError('')
            }}
          >
            {mode === 'login' ? 'Ainda não tem acesso? Criar usuário' : 'Já possui usuário? Fazer login'}
          </button>
        </form>
      </section>
    </div>
  )
}
