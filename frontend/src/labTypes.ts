export type LabStatus = 'REQUESTED' | 'COLLECTED' | 'VERIFIED' | 'CANCELLED'
export type Flag = 'NORMAL' | 'LOW' | 'HIGH'
export type ResultType = 'NUMERIC' | 'TEXT'
export type Priority = 'ROUTINE' | 'URGENT'

export interface LabTest {
  id: number
  name: string
  category: string
  specimen: string
  resultType: ResultType
  unit: string | null
  refLow: number | null
  refHigh: number | null
  refText: string | null
  price: number
  active: boolean
}

export interface LabItem {
  id: number
  testId: number
  testName: string
  specimen: string
  resultType: ResultType
  unit: string | null
  refLow: number | null
  refHigh: number | null
  refText: string | null
  resultValue: string | null
  flag: Flag | null
  resultComment: string | null
  enteredByName: string | null
  enteredAt: string | null
}

export interface LabRequest {
  id: number
  requestNo: string
  appointmentId: number
  patientId: number
  patientNo: string
  patientName: string
  requestedByName: string
  priority: Priority
  clinicalNotes: string | null
  status: LabStatus
  sampleNo: string | null
  collectedByName: string | null
  collectedAt: string | null
  verifiedByName: string | null
  verifiedAt: string | null
  cancelReason: string | null
  createdAt: string
  items: LabItem[]
}

export interface LabSummary {
  id: number
  requestNo: string
  patientNo: string
  patientName: string
  priority: Priority
  status: LabStatus
  requestedByName: string
  createdAt: string
  itemCount: number
}

export const LAB_STATUS_LABEL: Record<LabStatus, string> = {
  REQUESTED: 'Waiting for sample',
  COLLECTED: 'Awaiting results',
  VERIFIED: 'Released',
  CANCELLED: 'Cancelled',
}