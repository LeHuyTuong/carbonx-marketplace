import { ROLES, type Role } from '@/shared/stores/auth.store'

/** Backend dùng CC_BUYER cho vai doanh nghiệp mua tín chỉ. */
const ROLE_ALIASES: Record<string, Role> = {
  CC_BUYER: 'COMPANY',
  BUYER: 'COMPANY',
  EV: 'EV_OWNER',
}

export function normalizeRole(value: unknown): Role | null {
  if (typeof value !== 'string') return null
  const upper = value.trim().toUpperCase()
  const stripped = upper.startsWith('ROLE_') ? upper.slice(5) : upper
  if (ROLE_ALIASES[stripped]) return ROLE_ALIASES[stripped]
  return (ROLES as readonly string[]).includes(stripped) ? (stripped as Role) : null
}

export function toBackendRole(role: Role): string {
  return `ROLE_${role}`
}

export function roleHome(role: Role | null): string {
  switch (role) {
    case 'EV_OWNER':
      return '/app/ev'
    case 'COMPANY':
      return '/app/company'
    case 'CVA':
      return '/app/cva'
    case 'ADMIN':
      return '/app/admin'
    default:
      return '/login'
  }
}

export const ROLE_LABELS: Record<Role, string> = {
  EV_OWNER: 'Chủ xe điện',
  COMPANY: 'Doanh nghiệp',
  CVA: 'Đơn vị thẩm định',
  ADMIN: 'Quản trị viên',
}

export function canAccess(role: Role | null, allow: readonly Role[]): boolean {
  return role !== null && allow.includes(role)
}
