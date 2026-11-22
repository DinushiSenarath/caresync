export type AdmissionStatus = 'ADMITTED' | 'DISCHARGED' | 'CANCELLED'

export interface Bed {
  id: number
  wardId: number
  bedNo: string
  active: boolean
  occupied: boolean
  admissionId: number | null
  admissionNo: string | null
  patientId: number | null
  patientNo: string | null
  patientName: string | null
  since: string | null
}

export interface Ward {
  id: number
  name: string
  wardType: string
  description: string | null
  dailyRate: number
  active: boolean
  usableBeds: number
  occupied: number
  free: number
  outOfService: number
  beds: Bed[]
}

export interface Stay {
  id: number
  wardName: string
  bedNo: string
  dailyRate: number
  fromAt: string
  toAt: string | null
  transferReason: string | null
}

export interface Admission {
  id: number
  admissionNo: string
  patientId: number
  patientNo: string
  patientName: string
  responsibleDoctorId: number
  responsibleDoctorName: string
  reason: string | null
  status: AdmissionStatus
  admittedAt: string
  admittedByName: string
  currentWard: string | null
  currentBed: string | null
  dischargedAt: string | null
  dischargedByName: string | null
  summaryWritten: boolean
  dischargeSummary: string | null
  dischargeInstructions: string | null
  cancelReason: string | null
  stays: Stay[]
}

export interface AdmissionSummary {
  id: number
  admissionNo: string
  patientNo: string
  patientName: string
  doctorName: string
  ward: string | null
  bed: string | null
  admittedAt: string
  status: AdmissionStatus
  dischargedAt: string | null
  summaryWritten: boolean
}