export type LoginResponse = {
  accessToken: string
  tokenType: string
  expiresInSeconds: number
  userId: string
  email: string
  roles: string[]
}

export type MeResponse = {
  userId: string
  email: string
  roles: string[]
  permissions: string[]
}

export type Account = {
  id: string
  accountNumber: string
  holderName: string
  balance: number
  status: string
  createdAt: string
  updatedAt: string
}

export type Payment = {
  id: string
  payerAccountId: string
  pixKey: string
  amount: number
  description: string | null
  status: string
  createdAt: string
}

export type LedgerEntry = {
  id: string
  eventId: string
  accountId: string
  accountNumber: string
  entryType: string
  amount: number
  balanceAfter: number
  occurredAt: string
  recordedAt: string
}

export type FraudDecision = {
  id: string
  eventId: string
  paymentId: string
  payerAccountId: string
  pixKey: string
  amount: number
  decision: 'APPROVED' | 'REVIEW' | 'BLOCKED' | string
  riskScore: number
  reason: string
  occurredAt: string
  analyzedAt: string
}

export type PageResponse<T> = {
  content: T[]
  totalElements: number
  totalPages: number
  number: number
  size: number
  first: boolean
  last: boolean
}
