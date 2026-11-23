export interface Employee {
  id: number
  employeeNo: string
  fullName: string
  nic: string | null
  phone: string
  email: string | null
  address: string | null
  jobTitle: string
  departmentId: number
  departmentName: string
  employmentType: string
  hireDate: string
  userId: number | null
  username: string | null
  userRole: string | null
  emergencyContactName: string | null
  emergencyContactPhone: string | null
  active: boolean
}

export interface UnlinkedUser {
  id: number
  username: string
  fullName: string
  role: string
}

export type AttStatus = 'PRESENT' | 'ABSENT' | 'HALF_DAY'

export interface Attendance {
  id: number
  employeeId: number
  employeeNo: string
  employeeName: string
  workDate: string
  checkIn: string | null
  checkOut: string | null
  hours: number | null
  status: AttStatus
  source: 'SELF' | 'MANUAL'
  note: string | null
  recordedByName: string
}

export interface SummaryRow {
  employeeId: number
  employeeNo: string
  fullName: string
  departmentName: string
  presentDays: number
  absentDays: number
  leaveDays: number
  daysWithoutRecord: number
  hoursWorked: number
}

export type LeaveType = 'ANNUAL' | 'CASUAL' | 'SICK' | 'UNPAID' | 'OTHER'
export type LeaveStatus = 'PENDING' | 'APPROVED' | 'REJECTED' | 'CANCELLED'

export interface Leave {
  id: number
  employeeId: number
  employeeNo: string
  employeeName: string
  leaveType: LeaveType
  fromDate: string
  toDate: string
  days: number
  reason: string | null
  status: LeaveStatus
  decidedByName: string | null
  decisionNote: string | null
  decidedAt: string | null
  createdAt: string
}

export interface Balance {
  type: LeaveType
  entitlement: number | null
  used: number
  pending: number
  remaining: number | null
}

export interface HrMe {
  employeeId: number
  employeeNo: string
  fullName: string
  jobTitle: string
  departmentName: string
  hireDate: string
  onLeaveToday: boolean
  today: Attendance | null
  balances: Balance[]
}

export const LEAVE_TYPES: { value: LeaveType; label: string }[] = [
  { value: 'ANNUAL', label: 'Annual' },
  { value: 'CASUAL', label: 'Casual' },
  { value: 'SICK', label: 'Sick' },
  { value: 'UNPAID', label: 'Unpaid' },
  { value: 'OTHER', label: 'Other' },
]

export const leaveLabel = (t: string) => LEAVE_TYPES.find((x) => x.value === t)?.label ?? t
export const nice = (s: string) => s.charAt(0) + s.slice(1).toLowerCase().replace('_', ' ')

export const LEAVE_STYLE: Record<LeaveStatus, string> = {
  PENDING: 'bg-violet-100 text-violet-800',
  APPROVED: 'bg-green-100 text-green-800',
  REJECTED: 'bg-red-100 text-red-700',
  CANCELLED: 'bg-slate-200 text-slate-600',
}