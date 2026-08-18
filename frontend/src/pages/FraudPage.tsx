import { useEffect, useState, type FormEvent } from 'react'
import { useSearchParams } from 'react-router'
import { useAuth } from '../auth/AuthContext'
import { apiFetch } from '../lib/api'
import { formatCurrency, formatDate, shortId } from '../lib/format'
import { trackPaymentId } from '../lib/storage'
import type { FraudDecision } from '../types'

function decisionClass(decision: string) {
  if (decision === 'APPROVED') return 'success'
  if (decision === 'BLOCKED') return 'danger'
  return 'warning'
}

export function FraudPage() {
  const { hasPermission } = useAuth()
  const [searchParams, setSearchParams] = useSearchParams()
  const initialPaymentId = searchParams.get('paymentId') ?? ''
  const [paymentId, setPaymentId] = useState(initialPaymentId)
  const [decision, setDecision] = useState<FraudDecision | null>(null)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')

  useEffect(() => {
    if (initialPaymentId && hasPermission('FRAUD_READ')) {
      void loadDecision(initialPaymentId)
    }
  }, [initialPaymentId, hasPermission])

  async function loadDecision(id: string) {
    if (!id.trim()) return
    setBusy(true)
    setError('')
    try {
      const response = await apiFetch<FraudDecision>(`/api/v1/fraud/payments/${id.trim()}`)
      setDecision(response)
      setPaymentId(response.paymentId)
      trackPaymentId(response.paymentId)
      setSearchParams({ paymentId: response.paymentId }, { replace: true })
    } catch (caught) {
      setDecision(null)
      setError(caught instanceof Error ? caught.message : 'Não foi possível consultar a decisão antifraude.')
    } finally {
      setBusy(false)
    }
  }

  async function handleSubmit(event: FormEvent) {
    event.preventDefault()
    await loadDecision(paymentId)
  }

  if (!hasPermission('FRAUD_READ')) {
    return (
      <div className="page">
        <div className="page-heading"><div><span className="eyebrow">FRAUD SERVICE</span><h1>Antifraude</h1></div></div>
        <div className="permission-denied">
          <span>403</span>
          <h2>Permissão FRAUD_READ necessária</h2>
          <p>O Gateway bloqueia esta área para usuários sem autorização administrativa.</p>
        </div>
      </div>
    )
  }

  return (
    <div className="page">
      <div className="page-heading">
        <div><span className="eyebrow">FRAUD SERVICE · ADMIN</span><h1>Análise antifraude</h1><p>Consulte a decisão assíncrona produzida após o evento de criação do pagamento.</p></div>
      </div>

      <section className="panel lookup-panel">
        <form className="lookup-row wide" onSubmit={handleSubmit}>
          <input value={paymentId} onChange={(event) => setPaymentId(event.target.value)} placeholder="UUID do pagamento" required />
          <button className="button primary" disabled={busy} type="submit">{busy ? 'Consultando...' : 'Consultar risco'}</button>
        </form>
      </section>

      {error && <div className="alert error page-alert">{error}</div>}

      <section className="panel result-panel">
        <div className="panel-heading">
          <div><span className="eyebrow">DECISÃO</span><h2>{decision ? decision.decision : 'Nenhuma análise selecionada'}</h2></div>
          {decision && <span className={`badge ${decisionClass(decision.decision)}`}>SCORE {decision.riskScore}</span>}
        </div>

        {!decision ? (
          <div className="empty-state"><strong>Informe um pagamento.</strong><span>A análise aparecerá após o Fraud Service consumir e processar o evento Kafka.</span></div>
        ) : (
          <>
            <div className={`risk-card ${decisionClass(decision.decision)}`}>
              <div>
                <span>Risk score</span>
                <strong>{decision.riskScore}<small>/100</small></strong>
              </div>
              <div className="risk-meter"><i style={{ width: `${Math.min(100, Math.max(0, decision.riskScore))}%` }} /></div>
              <p>{decision.reason}</p>
            </div>
            <div className="data-grid spaced">
              <div className="data-point"><span>Pagamento</span><strong>{decision.paymentId}</strong></div>
              <div className="data-point"><span>Valor</span><strong>{formatCurrency(decision.amount)}</strong></div>
              <div className="data-point"><span>Chave PIX</span><strong>{decision.pixKey}</strong></div>
              <div className="data-point"><span>Conta pagadora</span><strong>{shortId(decision.payerAccountId)}</strong></div>
              <div className="data-point"><span>Ocorrido em</span><strong>{formatDate(decision.occurredAt)}</strong></div>
              <div className="data-point"><span>Analisado em</span><strong>{formatDate(decision.analyzedAt)}</strong></div>
            </div>
          </>
        )}
      </section>
    </div>
  )
}
