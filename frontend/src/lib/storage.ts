const ACCOUNTS_KEY = 'nexapay.tracked-accounts'
const PAYMENTS_KEY = 'nexapay.tracked-payments'

function readIds(key: string): string[] {
  try {
    const value = JSON.parse(localStorage.getItem(key) ?? '[]') as unknown
    return Array.isArray(value) ? value.filter((item): item is string => typeof item === 'string') : []
  } catch {
    return []
  }
}

function writeIds(key: string, ids: string[]) {
  localStorage.setItem(key, JSON.stringify([...new Set(ids)].slice(0, 20)))
}

export function getTrackedAccountIds() {
  return readIds(ACCOUNTS_KEY)
}

export function trackAccountId(id: string) {
  writeIds(ACCOUNTS_KEY, [id, ...readIds(ACCOUNTS_KEY).filter((item) => item !== id)])
}

export function getTrackedPaymentIds() {
  return readIds(PAYMENTS_KEY)
}

export function trackPaymentId(id: string) {
  writeIds(PAYMENTS_KEY, [id, ...readIds(PAYMENTS_KEY).filter((item) => item !== id)])
}
