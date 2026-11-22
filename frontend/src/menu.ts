import type { Role } from './auth'

export interface MenuItem {
  label: string
  path: string
  roles: Role[]
}

const STAFF: Role[] = ['ADMIN', 'RECEPTIONIST', 'DOCTOR', 'NURSE', 'PHARMACIST', 'CASHIER', 'LAB_TECH']

export const MENU: MenuItem[] = [
  { label: 'Dashboard', path: '/dashboard', roles: STAFF },
  { label: 'Patients', path: '/patients', roles: ['ADMIN', 'RECEPTIONIST', 'DOCTOR', 'NURSE'] },
  { label: 'Appointments', path: '/appointments', roles: ['ADMIN', 'RECEPTIONIST', 'DOCTOR', 'NURSE'] },
  { label: 'Registration requests', path: '/registration-requests', roles: ['ADMIN', 'RECEPTIONIST'] },
  { label: 'Laboratory', path: '/laboratory', roles: ['ADMIN', 'LAB_TECH'] },
  { label: 'Pharmacy', path: '/pharmacy', roles: ['ADMIN', 'PHARMACIST'] },
  { label: 'Billing', path: '/billing', roles: ['ADMIN', 'CASHIER'] },
  { label: 'Staff accounts', path: '/admin/users', roles: ['ADMIN'] },
  { label: 'Departments and doctors', path: '/admin/doctors', roles: ['ADMIN'] },
  { label: 'Service prices', path: '/admin/prices', roles: ['ADMIN'] },
  { label: 'Audit log', path: '/audit', roles: ['ADMIN'] },
  { label: 'Home', path: '/portal', roles: ['PATIENT'] },
  { label: 'My appointments', path: '/portal/appointments', roles: ['PATIENT'] },
  { label: 'My prescriptions', path: '/portal/prescriptions', roles: ['PATIENT'] },
  { label: 'My lab results', path: '/portal/lab', roles: ['PATIENT'] },
  { label: 'My bills', path: '/portal/bills', roles: ['PATIENT'] },
  { label: 'My details', path: '/portal/profile', roles: ['PATIENT'] },
]

export const ROLE_LABEL: Record<Role, string> = {
  ADMIN: 'Administrator',
  RECEPTIONIST: 'Receptionist',
  DOCTOR: 'Doctor',
  NURSE: 'Nurse',
  PHARMACIST: 'Pharmacist',
  CASHIER: 'Accountant',
  PATIENT: 'Patient',
  LAB_TECH: 'Laboratory staff',
  WARD_STAFF: 'Ward staff',
}

export function homePath(role: Role): string {
  return role === 'PATIENT' ? '/portal' : '/dashboard'
}