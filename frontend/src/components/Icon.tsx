export type IconName =
  | 'dashboard'
  | 'accounts'
  | 'payments'
  | 'ledger'
  | 'fraud'
  | 'profile'
  | 'logout'
  | 'arrow'
  | 'shield'
  | 'activity'
  | 'gateway'
  | 'server'
  | 'lock'
  | 'wallet'
  | 'pulse'

export function Icon({ name, size = 20 }: { name: IconName; size?: number }) {
  const common = {
    width: size,
    height: size,
    viewBox: '0 0 24 24',
    fill: 'none',
    stroke: 'currentColor',
    strokeWidth: 1.8,
    strokeLinecap: 'round' as const,
    strokeLinejoin: 'round' as const,
    'aria-hidden': true,
  }

  const paths: Record<IconName, React.ReactNode> = {
    dashboard: <><rect x="3" y="3" width="7" height="7" rx="2"/><rect x="14" y="3" width="7" height="7" rx="2"/><rect x="3" y="14" width="7" height="7" rx="2"/><rect x="14" y="14" width="7" height="7" rx="2"/></>,
    accounts: <><rect x="3" y="5" width="18" height="14" rx="3"/><path d="M7 9h10M7 13h5"/></>,
    payments: <><path d="M7 17 17 7"/><path d="M8 7h9v9"/></>,
    ledger: <><path d="M4 6h16M4 12h16M4 18h10"/><circle cx="18" cy="18" r="2"/></>,
    fraud: <><path d="M12 3 4.5 6v5.5c0 4.7 3 7.7 7.5 9.5 4.5-1.8 7.5-4.8 7.5-9.5V6L12 3Z"/><path d="m9.5 12 1.7 1.7 3.6-4"/></>,
    profile: <><circle cx="12" cy="8" r="3.5"/><path d="M5 21c.7-4 3-6 7-6s6.3 2 7 6"/></>,
    logout: <><path d="M10 5H5v14h5"/><path d="m14 8 4 4-4 4M18 12H9"/></>,
    arrow: <><path d="M5 12h14"/><path d="m14 7 5 5-5 5"/></>,
    shield: <><path d="M12 3 5 6v5c0 4.2 2.5 7.2 7 9 4.5-1.8 7-4.8 7-9V6l-7-3Z"/><path d="m9.5 11.8 1.7 1.7 3.5-4"/></>,
    activity: <><path d="M3 12h4l2-5 4 10 2-5h6"/></>,
    gateway: <><rect x="3" y="4" width="18" height="16" rx="3"/><path d="M8 9h8M8 13h5"/><circle cx="17" cy="13" r="1"/></>,
    server: <><rect x="4" y="4" width="16" height="6" rx="2"/><rect x="4" y="14" width="16" height="6" rx="2"/><path d="M8 7h.01M8 17h.01"/></>,
    lock: <><rect x="5" y="10" width="14" height="10" rx="2"/><path d="M8 10V7a4 4 0 0 1 8 0v3"/></>,
    wallet: <><path d="M4 7a3 3 0 0 1 3-3h11v4H7a3 3 0 0 0 0 6h13v5H7a3 3 0 0 1-3-3V7Z"/><path d="M20 10v4h-5a2 2 0 1 1 0-4h5Z"/></>,
    pulse: <><circle cx="12" cy="12" r="9"/><path d="M7 12h2l1.5-3 3 6 1.5-3H17"/></>,
  }

  return <svg {...common}>{paths[name]}</svg>
}
