package backend.portal;

import backend.appointment.AppointmentDtos.AppointmentResponse;
import backend.appointment.AppointmentDtos.SlotResponse;
import backend.appointment.AppointmentRepository;
import backend.appointment.AppointmentService;
import backend.appointment.AppointmentStatus;
import backend.audit.AuditService;
import backend.billing.BillingService;
import backend.billing.Invoice;
import backend.billing.InvoiceRepository;
import backend.billing.InvoiceStatus;
import backend.billing.Payment;
import backend.billing.PaymentRepository;
import backend.billing.BillingDtos.InvoiceDetail;
import backend.billing.BillingDtos.InvoiceSummary;
import backend.billing.BillingDtos.ReceiptResponse;
import backend.common.ApiException;
import backend.common.PagedResponse;
import backend.doctor.DoctorRepository;
import backend.patient.Patient;
import backend.patient.PatientRepository;
import backend.portal.PortalDtos.*;
import backend.prescription.PrescriptionDtos.PrescriptionResponse;
import backend.prescription.PrescriptionMapper;
import backend.prescription.PrescriptionRepository;
import backend.prescription.PrescriptionStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PortalService {

    private static final ZoneId ZONE = ZoneId.of("Asia/Colombo");
    private static final LocalDate MIN = LocalDate.of(1900, 1, 1);
    private static final LocalDate MAX = LocalDate.of(2999, 12, 31);

    private final PatientRepository patients;
    private final AppointmentRepository appointmentRepo;
    private final AppointmentService appointmentService;
    private final DoctorRepository doctorRepo;
    private final PrescriptionRepository prescriptionRepo;
    private final PrescriptionMapper prescriptionMapper;
    private final InvoiceRepository invoiceRepo;
    private final PaymentRepository paymentRepo;
    private final BillingService billing;
    private final AuditService audit;

    /** The patient is always found from the login, never from an id sent by the browser. */
    private Patient me(Long userId) {
        return patients.findByUserId(userId)
                .filter(Patient::isActive)
                .orElseThrow(() -> new ApiException(HttpStatus.FORBIDDEN,
                        "No active patient record is linked to this account"));
    }

    // ---------- profile ----------

    @Transactional(readOnly = true)
    public ProfileResponse profile(Long userId) {
        return toProfile(me(userId));
    }

    @Transactional
    public ProfileResponse updateProfile(Long userId, ProfileUpdateRequest r) {
        Patient p = me(userId);
        p.setPhone(r.phone().trim());
        p.setAddress(clean(r.address()));
        p.setEmergencyContactName(clean(r.emergencyContactName()));
        p.setEmergencyContactPhone(clean(r.emergencyContactPhone()));
        audit.log(userId, "PORTAL_PROFILE_UPDATED", "PATIENT", p.getId(), null);
        return toProfile(p);
    }

    // ---------- home ----------

    @Transactional(readOnly = true)
    public HomeResponse home(Long userId) {
        Patient p = me(userId);
        LocalDate today = LocalDate.now(ZONE);
        AppointmentResponse next = appointmentRepo.search(today, MAX,
                        List.of(AppointmentStatus.REQUESTED, AppointmentStatus.SCHEDULED, AppointmentStatus.CHECKED_IN),
                        true, 0L, false, p.getId(), PageRequest.of(0, 1))
                .map(AppointmentResponse::from).stream().findFirst().orElse(null);
        List<PrescriptionResponse> recent = prescriptionRepo.findForPatient(p.getId(), PrescriptionStatus.DRAFT)
                .stream().limit(3).map(prescriptionMapper::toResponse).toList();
        BigDecimal outstanding = invoiceRepo
                .search(List.of(InvoiceStatus.UNPAID, InvoiceStatus.PARTIALLY_PAID), false, p.getId(),
                        PageRequest.of(0, 100))
                .stream().map(Invoice::balance).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new HomeResponse(p.getFullName(), next, recent, outstanding);
    }

    // ---------- appointments ----------

    @Transactional(readOnly = true)
    public PagedResponse<AppointmentResponse> appointments(Long userId, String scope, int page, int size) {
        Patient p = me(userId);
        LocalDate today = LocalDate.now(ZONE);
        boolean past = "past".equalsIgnoreCase(scope);
        LocalDate from = past ? MIN : today;
        LocalDate to = past ? today.minusDays(1) : MAX;
        PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100));
        return PagedResponse.of(appointmentRepo
                .search(from, to, List.of(AppointmentStatus.values()), true, 0L, false, p.getId(), pageable)
                .map(AppointmentResponse::from));
    }

    @Transactional(readOnly = true)
    public List<DoctorOption> doctors() {
        return doctorRepo.findAllByOrderByIdAsc().stream()
                .filter(d -> d.isActive() && d.getUser().isActive())
                .map(d -> new DoctorOption(d.getId(), d.getUser().getFullName(), d.getDepartment().getName(),
                        d.getSpecialty(), d.getWorkDays()))
                .toList();
    }

    public List<SlotResponse> slots(Long doctorId, LocalDate date) {
        return appointmentService.slots(doctorId, date);
    }

    public AppointmentResponse requestAppointment(Long userId, AppointmentRequestBody r) {
        Patient p = me(userId);
        return appointmentService.requestAsPatient(p, r.doctorId(), r.date(), r.time(), r.reason(), userId);
    }

    public AppointmentResponse cancelAppointment(Long userId, Long appointmentId, String reason) {
        Patient p = me(userId);
        return appointmentService.cancelAsPatient(appointmentId, p.getId(), reason, userId);
    }

    // ---------- prescriptions and bills ----------

    @Transactional(readOnly = true)
    public List<PrescriptionResponse> prescriptions(Long userId) {
        Patient p = me(userId);
        return prescriptionRepo.findForPatient(p.getId(), PrescriptionStatus.DRAFT).stream()
                .map(prescriptionMapper::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public PagedResponse<InvoiceSummary> invoices(Long userId, int page, int size) {
        Patient p = me(userId);
        PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100));
        return PagedResponse.of(invoiceRepo
                .search(List.of(InvoiceStatus.values()), false, p.getId(), pageable)
                .map(InvoiceSummary::from));
    }

    public InvoiceDetail invoice(Long userId, Long invoiceId) {
        Patient p = me(userId);
        Invoice inv = invoiceRepo.findById(invoiceId).orElse(null);
        if (inv == null || !inv.getPatient().getId().equals(p.getId())) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Invoice not found");
        }
        return billing.get(invoiceId);
    }

    public ReceiptResponse receipt(Long userId, Long paymentId) {
        Patient p = me(userId);
        Payment pay = paymentRepo.findById(paymentId).orElse(null);
        Invoice inv = pay == null ? null : invoiceRepo.findById(pay.getInvoiceId()).orElse(null);
        if (inv == null || !inv.getPatient().getId().equals(p.getId())) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Receipt not found");
        }
        return billing.receipt(paymentId);
    }

    // ---------- helpers ----------

    private ProfileResponse toProfile(Patient p) {
        return new ProfileResponse(p.getId(), p.getPatientNo(), p.getFullName(), p.getDateOfBirth(), p.getGender(),
                p.getNic(), p.getPhone(), p.getAddress(), p.getEmergencyContactName(),
                p.getEmergencyContactPhone(), p.getAllergies());
    }

    private String clean(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}