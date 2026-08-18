import type { ReactNode } from 'react'
import { Navigate } from 'react-router'
import { useAuth } from '../auth/AuthContext'

export function ProtectedRoute({ children }: { children: ReactNode }) {
  const { loading, isAuthenticated } = useAuth()

  if (loading) {
    return (
      <div className="screen-center">
        <div className="loading-card">
          <div className="brand-mark small">N</div>
          <strong>Carregando NexaPay...</strong>
        </div>
      </div>
    )
  }

  if (!isAuthenticated) {
    return <Navigate to="/login" replace />
  }

  return children
}
