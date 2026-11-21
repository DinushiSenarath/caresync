export interface Page<T> {
  content: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

export interface Patient {
  id: number
  patientNo: string
  fullName: string
  dateOfBirth: string | null
  gender: 'MALE' | 'FEMALE' | 'OTHER' | null
  nic: string | null
  phone: string | null
  address: string | null
  emergencyContactName: string | null
  emergencyContactPhone: string | null
  allergies: string | null
  active: boolean
}

export interface Doctor {
  id: number
  userId: number
  fullName: string
  departmentId: number
  departmentName: string
  specialty: string | null
  workDays: string
  startTime: string
  endTime: string
  slotMinutes: number
  active: boolean
}

export type AppointmentStatus =
  | 'REQUESTED' | 'SCHEDULED' | 'CHECKED_IN' | 'IN_PROGRESS' | 'COMPLETED' | 'CANCELLED' | 'MISSED'

export interface Appointment {
  id: number
  patientId: number
  patientNo: string
  patientName: string
  doctorId: number
  doctorName: string
  departmentName: string
  date: string
  startTime: string
  status: AppointmentStatus
  reason: string | null
  cancelReason: string | null
}

export interface Slot {
  time: string
  available: boolean
}