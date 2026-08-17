import { useEffect, useMemo, useState } from 'react'
import { Link } from 'react-router'
import { useAuth } from '../auth/AuthContext'
import { Icon } from '../components/Icon'
import { apiFetch } from '../lib/api'
import { formatCurrency, shortId } from '../lib/format'
import { getTrackedAccountIds, getTrackedPaymentIds } from '../lib/storage'
import type { Account } from '../types'

export function DashboardPage() {
  const { user, hasPermission } = useAuth()
  const [gatewayStatus, setGatewayStatus] = useState<'checking' | 'UP' | 'DOWN'>('checking')
  const [trackedAccounts] = useState(() => getTrackedAccountIds())
  const [trackedPayments] = useState(() => getTrackedPaymentIds())
  const [visibleBalance, setVisibleBalance] = useState(0)
  const [loadedAccounts, setLoadedAccounts] = useState(0)
  const [balanceLoading, setBalanceLoading] = useState(true)

  useEffect(() => {
    void apiFetch<{ status: string }>('/actuator/health')
      .then((response) => setGatewayStatus(response.status === 'UP' ? 'UP' : 'DOWN'))
      .catch(() => setGatewayStatus('DOWN'))

    if (trackedAccounts.length === 0) {
      setBalanceLoading(false)
      return
    }

    void Promise.allSettled(
      trackedAccounts.slice(0, 5).map((id) => apiFetch<Account>(`/api/v1/accounts/${id}`)),
    ).then((results) => {
      const fulfilled = results.filter((result): result is PromiseFulfilledResult<Account> => result.status === 'fulfilled')
      setVisibleBalance(fulfilled.reduce((sum, result) => sum + Number(result.value.balance), 0))
      setLoadedAccounts(fulfilled.length)
      setBalanceLoading(false)
    })
  }, [trackedAccounts])

  const firstName = useMemo(() => {
    const identity = user?.email.split('@')[0] ?? 'usuário'
    return identity.charAt(0).toUpperCase() + identity.slice(1)
  }, [user?.email])

  const gatewayLabel = gatewayStatus === 'checking'
    ? 'Verificando'
    : gatewayStatus === 'UP'
      ? 'Operacional'
      : 'Indisponível'

  return (
    <div className="page dashboard-page">
      <section className="dashboard-hero">
        <div className="dashboard-hero-copy">
          <span className="eyebrow">NEXAPAY FINANCIAL PLATFORM</span>
          <h1>Olá, {firstName}.</h1>
          <p>
            Uma visão unificada das operações financeiras, protegida por JWT e centralizada pelo API Gateway.
          </p>
          <div className="hero-badges" aria-label="Tecnologias principais">
            <span><Icon name="gateway" size={15} /> Spring Cloud Gateway</span>
            <span><Icon name="shield" size={15} /> JWT + Permissions</span>
            <span><Icon name="activity" size={15} /> Event-driven</span>
          </div>
        </div>

        <div className="gateway-health-card" aria-live="polite">
          <div className="gateway-health-header">
            <span className={`health-indicator ${gatewayStatus === 'UP' ? 'healthy' : gatewayStatus === 'DOWN' ? 'unhealthy' : ''}`} />
            <span>API Gateway</span>
          </div>
          <strong>{gatewayLabel}</strong>
          <small>Entrada única · porta 8080</small>
        </div>
      </section>

      <section className="executive-grid" aria-label="Resumo operacional">
        <article className="executive-card balance-card">
          <div className="executive-card-top">
            <span className="executive-icon"><Icon name="wallet" /></span>
            <span className="badge neutral">Dados reais</span>
          </div>
          <span className="metric-label">Saldo consolidado visível</span>
          {balanceLoading ? (
            <div className="metric-skeleton" aria-label="Carregando saldo" />
          ) : (
            <strong>{formatCurrency(visibleBalance)}</strong>
          )}
          <small>
            {trackedAccounts.length === 0
              ? 'Nenhuma conta acompanhada neste navegador.'
              : `${loadedAccounts} de ${Math.min(trackedAccounts.length, 5)} contas consultadas agora.`}
          </small>
        </article>

        <article className="executive-card">
          <div className="executive-card-top">
            <span className="executive-icon secondary"><Icon name="accounts" /></span>
            <span className="metric-status">TRACKED</span>
          </div>
          <span className="metric-label">Contas acompanhadas</span>
          <strong>{trackedAccounts.length}</strong>
          <small>Referências locais; dados sempre buscados no backend.</small>
        </article>

        <article className="executive-card">
          <div className="executive-card-top">
            <span className="executive-icon secondary"><Icon name="payments" /></span>
            <span className="metric-status">PIX</span>
          </div>
          <span className="metric-label">Pagamentos acompanhados</span>
          <strong>{trackedPayments.length}</strong>
          <small>Pagamentos criados ou consultados nesta sessão de demonstração.</small>
        </article>

        <article className="executive-card">
          <div className="executive-card-top">
            <span className="executive-icon secondary"><Icon name="lock" /></span>
            <span className="metric-status">RBAC</span>
          </div>
          <span className="metric-label">Permissões ativas</span>
          <strong>{user?.permissions.length ?? 0}</strong>
          <small>{user?.roles.join(' · ')}</small>
        </article>
      </section>

      <section className="content-grid dashboard-main-grid">
        <article className="panel premium-panel">
          <div className="panel-heading">
            <div>
              <span className="eyebrow">OPERAÇÕES</span>
              <h2>Acesso rápido</h2>
              <p className="panel-description">Fluxos reais consumindo exclusivamente o API Gateway.</p>
            </div>
          </div>

          <div className="professional-actions">
            <Link className="professional-action" to="/accounts">
              <span className="action-icon"><Icon name="accounts" /></span>
              <div>
                <strong>Gerenciar contas</strong>
                <small>Criar, consultar, creditar e debitar.</small>
              </div>
              <Icon name="arrow" size={18} />
            </Link>

            <Link className="professional-action" to="/payments">
              <span className="action-icon"><Icon name="payments" /></span>
              <div>
                <strong>Enviar PIX</strong>
                <small>Idempotência garantida por chave de requisição.</small>
              </div>
              <Icon name="arrow" size={18} />
            </Link>

            <Link className="professional-action" to="/ledger">
              <span className="action-icon"><Icon name="ledger" /></span>
              <div>
                <strong>Consultar movimentações</strong>
                <small>Eventos financeiros persistidos a partir do Kafka.</small>
              </div>
              <Icon name="arrow" size={18} />
            </Link>

            {hasPermission('FRAUD_READ') && (
              <Link className="professional-action" to="/fraud">
                <span className="action-icon"><Icon name="fraud" /></span>
                <div>
                  <strong>Analisar risco</strong>
                  <small>Decisões antifraude e score da transação.</small>
                </div>
                <Icon name="arrow" size={18} />
              </Link>
            )}
          </div>
        </article>

        <article className="panel premium-panel architecture-card">
          <div className="panel-heading">
            <div>
              <span className="eyebrow">ARQUITETURA</span>
              <h2>Engineering snapshot</h2>
              <p className="panel-description">O que está por trás desta interface.</p>
            </div>
          </div>

          <div className="architecture-stack">
            <div className="architecture-node">
              <span><Icon name="gateway" /></span>
              <div><strong>API Gateway</strong><small>Routing · rate limiting · JWT</small></div>
              <b>8080</b>
            </div>
            <div className="architecture-node">
              <span><Icon name="server" /></span>
              <div><strong>5 microsserviços</strong><small>Spring Boot · PostgreSQL</small></div>
              <b>Java 21</b>
            </div>
            <div className="architecture-node">
              <span><Icon name="activity" /></span>
              <div><strong>Event-driven</strong><small>Kafka · Outbox · retry/DLT</small></div>
              <b>At-least-once</b>
            </div>
            <div className="architecture-node">
              <span><Icon name="pulse" /></span>
              <div><strong>Observabilidade</strong><small>Prometheus · Grafana · Loki</small></div>
              <b>Live</b>
            </div>
          </div>
        </article>
      </section>

      <section className="panel premium-panel recent-resources-panel">
        <div className="panel-heading">
          <div>
            <span className="eyebrow">CONTEXTO DE NAVEGAÇÃO</span>
            <h2>Recursos acompanhados</h2>
            <p className="panel-description">
              O browser armazena somente os IDs para facilitar a demonstração; os dados são lidos novamente pela API.
            </p>
          </div>
        </div>

        <div className="recent-resource-grid">
          {trackedAccounts.slice(0, 3).map((id) => (
            <Link key={id} className="resource-chip" to={`/accounts?accountId=${encodeURIComponent(id)}`}>
              <span className="resource-chip-icon"><Icon name="accounts" size={17} /></span>
              <span><small>Conta</small><code>{shortId(id)}</code></span>
              <Icon name="arrow" size={16} />
            </Link>
          ))}
          {trackedPayments.slice(0, 3).map((id) => (
            <Link key={id} className="resource-chip" to={`/payments?paymentId=${encodeURIComponent(id)}`}>
              <span className="resource-chip-icon"><Icon name="payments" size={17} /></span>
              <span><small>Pagamento</small><code>{shortId(id)}</code></span>
              <Icon name="arrow" size={16} />
            </Link>
          ))}
          {trackedAccounts.length === 0 && trackedPayments.length === 0 && (
            <div className="empty-state recruiter-empty-state">
              <span className="empty-state-icon"><Icon name="activity" size={24} /></span>
              <strong>Nenhuma operação acompanhada ainda.</strong>
              <span>Crie uma conta ou um PIX para preencher o painel com dados reais.</span>
            </div>
          )}
        </div>
      </section>
    </div>
  )
}
