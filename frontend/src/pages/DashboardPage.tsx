import { useEffect, useState } from 'react'
import { Link } from 'react-router'
import { useAuth } from '../auth/AuthContext'
import { apiFetch } from '../lib/api'
import { formatCurrency, shortId } from '../lib/format'
import { getTrackedAccountIds, getTrackedPaymentIds } from '../lib/storage'
import type { Account } from '../types'

export function DashboardPage() {
  const { user } = useAuth()
  const [gatewayStatus, setGatewayStatus] = useState('verificando')
  const [trackedAccounts] = useState(() => getTrackedAccountIds())
  const [trackedPayments] = useState(() => getTrackedPaymentIds())
  const [visibleBalance, setVisibleBalance] = useState(0)

  useEffect(() => {
    void apiFetch<{ status: string }>('/actuator/health')
      .then((response) => setGatewayStatus(response.status))
      .catch(() => setGatewayStatus('INDISPONÍVEL'))

    if (trackedAccounts.length > 0) {
      void Promise.allSettled(
        trackedAccounts.slice(0, 5).map((id) => apiFetch<Account>(`/api/v1/accounts/${id}`)),
      ).then((results) => {
        const total = results.reduce((sum, result) => (
          result.status === 'fulfilled' ? sum + Number(result.value.balance) : sum
        ), 0)
        setVisibleBalance(total)
      })
    }
  }, [trackedAccounts])

  return (
    <div className="page">
      <div className="page-heading dashboard-heading">
        <div>
          <span className="eyebrow">NEXAPAY CONTROL CENTER</span>
          <h1>Visão geral</h1>
          <p>Bem-vindo, {user?.email}. Operações centralizadas pelo API Gateway.</p>
        </div>
        <div className={`gateway-pill ${gatewayStatus === 'UP' ? 'online' : ''}`}>
          <span className="status-dot" />
          Gateway {gatewayStatus}
        </div>
      </div>

      <section className="metric-grid">
        <article className="metric-card featured">
          <div className="metric-label">Saldo das contas acompanhadas</div>
          <strong>{formatCurrency(visibleBalance)}</strong>
          <small>Até 5 contas recentes consultadas pelo backend</small>
        </article>
        <article className="metric-card">
          <div className="metric-label">Contas acompanhadas</div>
          <strong>{trackedAccounts.length}</strong>
          <small>IDs salvos neste navegador</small>
        </article>
        <article className="metric-card">
          <div className="metric-label">Pagamentos acompanhados</div>
          <strong>{trackedPayments.length}</strong>
          <small>PIX criados ou consultados</small>
        </article>
        <article className="metric-card">
          <div className="metric-label">Permissões ativas</div>
          <strong>{user?.permissions.length ?? 0}</strong>
          <small>{user?.roles.join(' · ')}</small>
        </article>
      </section>

      <section className="content-grid two-columns">
        <article className="panel">
          <div className="panel-heading">
            <div>
              <span className="eyebrow">AÇÕES RÁPIDAS</span>
              <h2>Operar pelo Gateway</h2>
            </div>
          </div>
          <div className="quick-actions">
            <Link className="quick-action" to="/accounts">
              <span>▣</span>
              <div><strong>Nova conta</strong><small>Criar, consultar, creditar ou debitar</small></div>
              <b>→</b>
            </Link>
            <Link className="quick-action" to="/payments">
              <span>↗</span>
              <div><strong>Novo PIX</strong><small>Criar pagamento com Idempotency-Key</small></div>
              <b>→</b>
            </Link>
            <Link className="quick-action" to="/ledger">
              <span>≋</span>
              <div><strong>Movimentações</strong><small>Consultar lançamentos derivados do Kafka</small></div>
              <b>→</b>
            </Link>
          </div>
        </article>

        <article className="panel">
          <div className="panel-heading">
            <div>
              <span className="eyebrow">RECURSOS RECENTES</span>
              <h2>IDs acompanhados</h2>
            </div>
          </div>

          <div className="tracked-list">
            {trackedAccounts.slice(0, 3).map((id) => (
              <Link key={id} to={`/accounts?accountId=${encodeURIComponent(id)}`}>
                <span className="tracked-type">CONTA</span>
                <code>{shortId(id)}</code>
                <b>→</b>
              </Link>
            ))}
            {trackedPayments.slice(0, 3).map((id) => (
              <Link key={id} to={`/payments?paymentId=${encodeURIComponent(id)}`}>
                <span className="tracked-type">PIX</span>
                <code>{shortId(id)}</code>
                <b>→</b>
              </Link>
            ))}
            {trackedAccounts.length === 0 && trackedPayments.length === 0 && (
              <div className="empty-state compact">
                <strong>Nenhum recurso acompanhado ainda.</strong>
                <span>Crie uma conta ou um pagamento para começar.</span>
              </div>
            )}
          </div>
        </article>
      </section>

      <section className="architecture-strip">
        <div><span>Frontend</span><strong>React + Vite</strong></div>
        <i>→</i>
        <div><span>Entrada única</span><strong>Gateway :8080</strong></div>
        <i>→</i>
        <div><span>Segurança</span><strong>JWT + Permissions</strong></div>
        <i>→</i>
        <div><span>Backend</span><strong>5 microsserviços</strong></div>
      </section>
    </div>
  )
}
