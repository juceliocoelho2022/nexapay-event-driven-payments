import { useEffect, useState, type FormEvent } from 'react'
import { useSearchParams } from 'react-router'
import { apiFetch } from '../lib/api'
import { formatCurrency, formatDate, shortId } from '../lib/format'
import { trackAccountId } from '../lib/storage'
import type { LedgerEntry, PageResponse } from '../types'

export function LedgerPage() {
  const [searchParams, setSearchParams] = useSearchParams()
  const initialAccountId = searchParams.get('accountId') ?? ''
  const [accountId, setAccountId] = useState(initialAccountId)
  const [entries, setEntries] = useState<LedgerEntry[]>([])
  const [pageInfo, setPageInfo] = useState<PageResponse<LedgerEntry> | null>(null)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')

  useEffect(() => {
    if (initialAccountId) {
      void loadLedger(initialAccountId)
    }
  }, [initialAccountId])

  async function loadLedger(id: string) {
    if (!id.trim()) return
    setBusy(true)
    setError('')
    try {
      const response = await apiFetch<PageResponse<LedgerEntry>>(
        `/api/v1/ledger/accounts/${id.trim()}?page=0&size=20&sort=occurredAt,desc`,
      )
      setEntries(response.content ?? [])
      setPageInfo(response)
      setAccountId(id.trim())
      trackAccountId(id.trim())
      setSearchParams({ accountId: id.trim() }, { replace: true })
    } catch (caught) {
      setEntries([])
      setPageInfo(null)
      setError(caught instanceof Error ? caught.message : 'Não foi possível consultar as movimentações.')
    } finally {
      setBusy(false)
    }
  }

  async function handleSubmit(event: FormEvent) {
    event.preventDefault()
    await loadLedger(accountId)
  }

  return (
    <div className="page">
      <div className="page-heading">
        <div>
          <span className="eyebrow">LEDGER SERVICE</span>
          <h1>Movimentações</h1>
          <p>Visualize os lançamentos derivados dos eventos de crédito e débito consumidos via Kafka.</p>
        </div>
      </div>

      <section className="panel lookup-panel">
        <form className="lookup-row wide" onSubmit={handleSubmit}>
          <input value={accountId} onChange={(event) => setAccountId(event.target.value)} placeholder="UUID da conta" required />
          <button className="button primary" disabled={busy} type="submit">{busy ? 'Consultando...' : 'Consultar extrato'}</button>
        </form>
      </section>

      {error && <div className="alert error page-alert">{error}</div>}

      <section className="panel result-panel">
        <div className="panel-heading">
          <div><span className="eyebrow">LEDGER</span><h2>{entries.length ? `${pageInfo?.totalElements ?? entries.length} lançamento(s)` : 'Extrato da conta'}</h2></div>
          {accountId && <code className="header-code">{shortId(accountId)}</code>}
        </div>

        {entries.length === 0 ? (
          <div className="empty-state"><strong>Nenhuma movimentação carregada.</strong><span>Informe uma conta para consultar o histórico persistido.</span></div>
        ) : (
          <div className="table-wrap">
            <table>
              <thead><tr><th>Data</th><th>Tipo</th><th>Valor</th><th>Saldo após</th><th>Conta</th><th>Evento</th></tr></thead>
              <tbody>
                {entries.map((entry) => (
                  <tr key={entry.id}>
                    <td>{formatDate(entry.occurredAt)}</td>
                    <td><span className={`badge ${entry.entryType === 'CREDIT' ? 'success' : 'danger'}`}>{entry.entryType}</span></td>
                    <td className={entry.entryType === 'CREDIT' ? 'positive' : 'negative'}>{entry.entryType === 'CREDIT' ? '+' : '−'} {formatCurrency(entry.amount)}</td>
                    <td>{formatCurrency(entry.balanceAfter)}</td>
                    <td><code>{entry.accountNumber}</code></td>
                    <td><code>{shortId(entry.eventId)}</code></td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </section>
    </div>
  )
}
