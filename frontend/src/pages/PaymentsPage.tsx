import { useEffect, useState, type FormEvent } from 'react'
import { Link, useSearchParams } from 'react-router'
import { useAuth } from '../auth/AuthContext'
import { apiFetch } from '../lib/api'
import { formatCurrency, formatDate, shortId } from '../lib/format'
import { trackPaymentId } from '../lib/storage'
import type { Payment } from '../types'

export function PaymentsPage() {
  const { hasPermission } = useAuth()
  const [searchParams, setSearchParams] = useSearchParams()
  const initialPaymentId = searchParams.get('paymentId') ?? ''
  const [lookupId, setLookupId] = useState(initialPaymentId)
  const [payerAccountId, setPayerAccountId] = useState('')
  const [pixKey, setPixKey] = useState('')
  const [amount, setAmount] = useState('')
  const [description, setDescription] = useState('')
  const [idempotencyKey, setIdempotencyKey] = useState(() => crypto.randomUUID())
  const [payment, setPayment] = useState<Payment | null>(null)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [message, setMessage] = useState('')

  useEffect(() => {
    if (initialPaymentId) {
      void loadPayment(initialPaymentId)
    }
  }, [initialPaymentId])

  async function loadPayment(id: string) {
    if (!id.trim()) return
    setBusy(true)
    setError('')
    setMessage('')
    try {
      const response = await apiFetch<Payment>(`/api/v1/payments/${id.trim()}`)
      setPayment(response)
      setLookupId(response.id)
      trackPaymentId(response.id)
      setSearchParams({ paymentId: response.id }, { replace: true })
    } catch (caught) {
      setPayment(null)
      setError(caught instanceof Error ? caught.message : 'Não foi possível consultar o pagamento.')
    } finally {
      setBusy(false)
    }
  }

  async function handleLookup(event: FormEvent) {
    event.preventDefault()
    await loadPayment(lookupId)
  }

  async function handleCreate(event: FormEvent) {
    event.preventDefault()
    setBusy(true)
    setError('')
    setMessage('')
    try {
      const response = await apiFetch<Payment>('/api/v1/payments/pix', {
        method: 'POST',
        headers: { 'Idempotency-Key': idempotencyKey },
        body: JSON.stringify({
          payerAccountId,
          pixKey,
          amount: Number(amount),
          description: description || null,
        }),
      })
      setPayment(response)
      setLookupId(response.id)
      trackPaymentId(response.id)
      setSearchParams({ paymentId: response.id }, { replace: true })
      setMessage('Pagamento PIX criado com sucesso.')
      setPixKey('')
      setAmount('')
      setDescription('')
      setIdempotencyKey(crypto.randomUUID())
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : 'Não foi possível criar o pagamento.')
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="page">
      <div className="page-heading">
        <div>
          <span className="eyebrow">PAYMENT SERVICE</span>
          <h1>Pagamentos PIX</h1>
          <p>Crie pagamentos idempotentes e consulte o estado persistido através do Gateway.</p>
        </div>
      </div>

      <section className="content-grid two-columns payment-grid">
        <article className="panel">
          <div className="panel-heading"><div><span className="eyebrow">NOVO PIX</span><h2>Criar pagamento</h2></div></div>
          <form className="form-grid" onSubmit={handleCreate}>
            <label className="field full-row">
              <span>Conta pagadora</span>
              <input required maxLength={80} value={payerAccountId} onChange={(event) => setPayerAccountId(event.target.value)} placeholder="UUID ou identificador da conta" />
            </label>
            <label className="field">
              <span>Chave PIX</span>
              <input required maxLength={180} value={pixKey} onChange={(event) => setPixKey(event.target.value)} placeholder="email@pix.com" />
            </label>
            <label className="field">
              <span>Valor</span>
              <input required type="number" min="0.01" step="0.01" value={amount} onChange={(event) => setAmount(event.target.value)} placeholder="0,00" />
            </label>
            <label className="field full-row">
              <span>Descrição</span>
              <textarea maxLength={255} value={description} onChange={(event) => setDescription(event.target.value)} placeholder="Descrição opcional" />
            </label>
            <div className="idempotency-row full-row">
              <span>Idempotency-Key</span>
              <code>{idempotencyKey}</code>
              <button className="mini-button" type="button" onClick={() => setIdempotencyKey(crypto.randomUUID())}>Gerar nova</button>
            </div>
            <div className="form-actions full-row"><button className="button primary" disabled={busy} type="submit">{busy ? 'Processando...' : 'Enviar PIX'}</button></div>
          </form>
        </article>

        <article className="panel">
          <div className="panel-heading"><div><span className="eyebrow">CONSULTA DIRETA</span><h2>Buscar pagamento</h2></div></div>
          <form className="lookup-row" onSubmit={handleLookup}>
            <input value={lookupId} onChange={(event) => setLookupId(event.target.value)} placeholder="UUID do pagamento" required />
            <button className="button secondary" disabled={busy} type="submit">Consultar</button>
          </form>
          <p className="helper-text">Pagamentos criados e consultados ficam acompanhados localmente para facilitar a navegação do painel.</p>
        </article>
      </section>

      {(error || message) && <div className={`alert page-alert ${error ? 'error' : 'success'}`}>{error || message}</div>}

      <section className="panel result-panel">
        <div className="panel-heading">
          <div><span className="eyebrow">PAGAMENTO ATIVO</span><h2>{payment ? formatCurrency(payment.amount) : 'Nenhum pagamento selecionado'}</h2></div>
          {payment && <span className="badge success">{payment.status}</span>}
        </div>

        {!payment ? (
          <div className="empty-state"><strong>Crie ou consulte um PIX.</strong><span>O resultado do Payment Service será exibido aqui.</span></div>
        ) : (
          <>
            <div className="payment-hero-card">
              <div><span>Valor</span><strong>{formatCurrency(payment.amount)}</strong></div>
              <div><span>Chave PIX</span><strong>{payment.pixKey}</strong></div>
              <div><span>Conta pagadora</span><strong>{shortId(payment.payerAccountId)}</strong></div>
            </div>
            <div className="data-grid spaced">
              <div className="data-point"><span>ID do pagamento</span><strong>{payment.id}</strong></div>
              <div className="data-point"><span>Criado em</span><strong>{formatDate(payment.createdAt)}</strong></div>
              <div className="data-point"><span>Descrição</span><strong>{payment.description || '—'}</strong></div>
            </div>
            {hasPermission('FRAUD_READ') && (
              <div className="result-actions">
                <Link className="button ghost link-button" to={`/fraud?paymentId=${encodeURIComponent(payment.id)}`}>Consultar análise antifraude</Link>
              </div>
            )}
          </>
        )}
      </section>
    </div>
  )
}
