import { useEffect, useState, type FormEvent } from 'react'
import { Link, useSearchParams } from 'react-router'
import { apiFetch } from '../lib/api'
import { formatCurrency, formatDate, shortId } from '../lib/format'
import { trackAccountId } from '../lib/storage'
import type { Account } from '../types'

export function AccountsPage() {
  const [searchParams, setSearchParams] = useSearchParams()
  const initialAccountId = searchParams.get('accountId') ?? ''
  const [lookupId, setLookupId] = useState(initialAccountId)
  const [accountNumber, setAccountNumber] = useState('')
  const [holderName, setHolderName] = useState('')
  const [amount, setAmount] = useState('')
  const [account, setAccount] = useState<Account | null>(null)
  const [busy, setBusy] = useState(false)
  const [message, setMessage] = useState('')
  const [error, setError] = useState('')

  useEffect(() => {
    if (initialAccountId) {
      void loadAccount(initialAccountId)
    }
  }, [initialAccountId])

  async function loadAccount(id: string) {
    if (!id.trim()) return
    setBusy(true)
    setError('')
    setMessage('')
    try {
      const response = await apiFetch<Account>(`/api/v1/accounts/${id.trim()}`)
      setAccount(response)
      setLookupId(response.id)
      trackAccountId(response.id)
      setSearchParams({ accountId: response.id }, { replace: true })
    } catch (caught) {
      setAccount(null)
      setError(caught instanceof Error ? caught.message : 'Não foi possível consultar a conta.')
    } finally {
      setBusy(false)
    }
  }

  async function handleLookup(event: FormEvent) {
    event.preventDefault()
    await loadAccount(lookupId)
  }

  async function handleCreate(event: FormEvent) {
    event.preventDefault()
    setBusy(true)
    setError('')
    setMessage('')
    try {
      const response = await apiFetch<Account>('/api/v1/accounts', {
        method: 'POST',
        body: JSON.stringify({ accountNumber, holderName }),
      })
      setAccount(response)
      setLookupId(response.id)
      trackAccountId(response.id)
      setSearchParams({ accountId: response.id }, { replace: true })
      setMessage('Conta criada com sucesso.')
      setAccountNumber('')
      setHolderName('')
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : 'Não foi possível criar a conta.')
    } finally {
      setBusy(false)
    }
  }

  async function changeBalance(operation: 'credit' | 'debit') {
    if (!account || !amount) return
    setBusy(true)
    setError('')
    setMessage('')
    try {
      const response = await apiFetch<Account>(`/api/v1/accounts/${account.id}/${operation}`, {
        method: 'POST',
        body: JSON.stringify({ amount: Number(amount) }),
      })
      setAccount(response)
      setAmount('')
      setMessage(operation === 'credit' ? 'Crédito realizado.' : 'Débito realizado.')
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : 'Não foi possível atualizar o saldo.')
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="page">
      <div className="page-heading">
        <div>
          <span className="eyebrow">ACCOUNT SERVICE</span>
          <h1>Contas</h1>
          <p>Crie contas, consulte por UUID e execute operações de crédito e débito pelo Gateway.</p>
        </div>
      </div>

      <section className="content-grid two-columns">
        <article className="panel">
          <div className="panel-heading"><div><span className="eyebrow">NOVA CONTA</span><h2>Criar recurso</h2></div></div>
          <form className="form-grid" onSubmit={handleCreate}>
            <label className="field">
              <span>Número da conta</span>
              <input required maxLength={30} value={accountNumber} onChange={(event) => setAccountNumber(event.target.value)} placeholder="ACC-2026-001" />
            </label>
            <label className="field">
              <span>Titular</span>
              <input required maxLength={120} value={holderName} onChange={(event) => setHolderName(event.target.value)} placeholder="Nome do titular" />
            </label>
            <div className="form-actions full-row">
              <button className="button primary" disabled={busy} type="submit">{busy ? 'Processando...' : 'Criar conta'}</button>
            </div>
          </form>
        </article>

        <article className="panel">
          <div className="panel-heading"><div><span className="eyebrow">CONSULTA DIRETA</span><h2>Buscar por UUID</h2></div></div>
          <form className="lookup-row" onSubmit={handleLookup}>
            <input value={lookupId} onChange={(event) => setLookupId(event.target.value)} placeholder="UUID da conta" required />
            <button className="button secondary" disabled={busy} type="submit">Consultar</button>
          </form>
          <p className="helper-text">O backend atual não expõe listagem global; o painel acompanha os IDs criados ou consultados neste navegador.</p>
        </article>
      </section>

      {(error || message) && <div className={`alert page-alert ${error ? 'error' : 'success'}`}>{error || message}</div>}

      <section className="panel result-panel">
        <div className="panel-heading">
          <div><span className="eyebrow">CONTA ATIVA</span><h2>{account ? account.holderName : 'Nenhuma conta selecionada'}</h2></div>
          {account && <span className={`badge ${account.status === 'ACTIVE' ? 'success' : ''}`}>{account.status}</span>}
        </div>

        {!account ? (
          <div className="empty-state"><strong>Selecione ou crie uma conta.</strong><span>Os dados aparecerão aqui após a resposta real do Account Service.</span></div>
        ) : (
          <>
            <div className="account-balance-card">
              <span>Saldo disponível</span>
              <strong>{formatCurrency(account.balance)}</strong>
              <small>{account.accountNumber} · {shortId(account.id)}</small>
            </div>

            <div className="data-grid spaced">
              <div className="data-point"><span>ID</span><strong>{account.id}</strong></div>
              <div className="data-point"><span>Criada em</span><strong>{formatDate(account.createdAt)}</strong></div>
              <div className="data-point"><span>Atualizada em</span><strong>{formatDate(account.updatedAt)}</strong></div>
            </div>

            <div className="operation-box">
              <label className="field">
                <span>Valor da operação</span>
                <input type="number" min="0.01" step="0.01" value={amount} onChange={(event) => setAmount(event.target.value)} placeholder="0,00" />
              </label>
              <div className="operation-actions">
                <button className="button primary" type="button" disabled={busy || !amount} onClick={() => void changeBalance('credit')}>+ Crédito</button>
                <button className="button danger" type="button" disabled={busy || !amount} onClick={() => void changeBalance('debit')}>− Débito</button>
                <Link className="button ghost link-button" to={`/ledger?accountId=${encodeURIComponent(account.id)}`}>Ver movimentações</Link>
              </div>
            </div>
          </>
        )}
      </section>
    </div>
  )
}
