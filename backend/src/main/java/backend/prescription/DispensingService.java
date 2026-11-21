package backend.prescription;

import backend.audit.AuditService;
import backend.common.ApiException;
import backend.common.PagedResponse;
import backend.pharmacy.*;
import backend.prescription.PrescriptionDtos.*;
import backend.user.User;
import backend.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import backend.notification.NotificationService;
import backend.user.Role;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DispensingService {

    private static final ZoneId ZONE = ZoneId.of("Asia/Colombo");

    private final PrescriptionRepository prescriptions;
    private final DispensedItemRepository dispensedItems;
    private final MedicineBatchRepository batches;
    private final StockMovementRepository movements;
    private final UserRepository users;
    private final PrescriptionMapper mapper;
    private final AuditService audit;
    private final NotificationService notifications;

    @Transactional(readOnly = true)
    public PagedResponse<QueueEntry> queue(int page, int size) {
        PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100));
        return PagedResponse.of(prescriptions
                .queue(List.of(PrescriptionStatus.ISSUED, PrescriptionStatus.PARTIALLY_DISPENSED), pageable)
                .map(p -> new QueueEntry(p.getId(), p.getAppointment().getPatient().getPatientNo(),
                        p.getAppointment().getPatient().getFullName(), p.getDoctor().getUser().getFullName(),
                        p.getStatus(), p.getIssuedAt(), p.getItems().size())));
    }

    @Transactional
    public PrescriptionResponse dispense(Long prescriptionId, DispenseRequest req, Long actorId) {
        Prescription p = prescriptions.lockById(prescriptionId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Prescription not found"));
        if (p.getStatus() != PrescriptionStatus.ISSUED && p.getStatus() != PrescriptionStatus.PARTIALLY_DISPENSED) {
            throw new ApiException(HttpStatus.CONFLICT,
                    "Only issued prescriptions can be dispensed (this one is " + p.getStatus() + ")");
        }

        Map<Long, PrescriptionItem> byId = p.getItems().stream()
                .collect(Collectors.toMap(PrescriptionItem::getId, i -> i));
        Set<Long> seen = new HashSet<>();
        for (DispenseLine line : req.lines()) {
            PrescriptionItem item = byId.get(line.itemId());
            if (item == null) {
                throw new ApiException(HttpStatus.BAD_REQUEST,
                        "Item " + line.itemId() + " does not belong to this prescription");
            }
            if (!seen.add(line.itemId())) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "Item " + line.itemId() + " is listed twice");
            }
            int remaining = item.getQtyPrescribed() - item.getQtyDispensed();
            if (line.quantity() > remaining) {
                throw new ApiException(HttpStatus.BAD_REQUEST,
                        "Only " + remaining + " of " + item.getMedicine().getName() + " is still to be dispensed");
            }
        }

        User pharmacist = users.findById(actorId)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Account not available"));
        LocalDate today = LocalDate.now(ZONE);

        // lock stock in a fixed order (by medicine) so two pharmacists cannot deadlock each other
        List<DispenseLine> lines = new ArrayList<>(req.lines());
        lines.sort(Comparator.comparing(l -> byId.get(l.itemId()).getMedicine().getId()));

        StringBuilder summary = new StringBuilder();
        for (DispenseLine line : lines) {
            PrescriptionItem item = byId.get(line.itemId());
            List<MedicineBatch> usable = batches.lockUsableBatches(item.getMedicine().getId(), today);
            long available = usable.stream().mapToLong(MedicineBatch::getQuantity).sum();
            if (available < line.quantity()) {
                throw new ApiException(HttpStatus.CONFLICT, "Not enough unexpired stock for "
                        + item.getMedicine().getName() + ": available " + available + ", requested " + line.quantity()
                        + ". Nothing was dispensed.");
            }
            int needed = line.quantity();
            for (MedicineBatch b : usable) {
                if (needed == 0) break;
                int take = Math.min(needed, b.getQuantity());
                b.setQuantity(b.getQuantity() - take);
                needed -= take;

                DispensedItem d = new DispensedItem();
                d.setPrescriptionItemId(item.getId());
                d.setBatch(b);
                d.setQuantity(take);
                d.setDispensedBy(pharmacist);
                dispensedItems.save(d);

                StockMovement s = new StockMovement();
                s.setBatch(b);
                s.setType(MovementType.DISPENSE);
                s.setQuantity(-take);
                s.setReference("Prescription #" + p.getId());
                s.setUser(pharmacist);
                movements.save(s);
            }
            item.setQtyDispensed(item.getQtyDispensed() + line.quantity());
            int reorder = item.getMedicine().getReorderLevel();
            if (reorder > 0 && available > reorder && available - line.quantity() <= reorder) {
                notifications.toRole(Role.PHARMACIST, "LOW_STOCK",
                        "Low stock: " + item.getMedicine().getName() + " (" + (available - line.quantity()) + " left)",
                        "/pharmacy/alerts");
            }
            summary.append(item.getMedicine().getName()).append(" x").append(line.quantity()).append("; ");
        }

        boolean complete = p.getItems().stream().allMatch(i -> i.getQtyDispensed() >= i.getQtyPrescribed());
        p.setStatus(complete ? PrescriptionStatus.DISPENSED : PrescriptionStatus.PARTIALLY_DISPENSED);
        if (complete) {
            notifications.toRole(Role.CASHIER, "INVOICE_READY",
                    "Medicines were dispensed for " + p.getAppointment().getPatient().getPatientNo()
                            + ". The invoice can be prepared.", "/billing");
        }
        audit.log(actorId, "RX_DISPENSED", "PRESCRIPTION", p.getId(), summary.toString().trim());
        return mapper.toResponse(p);
    }

    @Transactional(readOnly = true)
    public List<DispenseRecord> history(Long prescriptionId) {
        Prescription p = prescriptions.findById(prescriptionId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Prescription not found"));
        if (p.getStatus() == PrescriptionStatus.DRAFT) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Prescription not found");
        }
        Map<Long, PrescriptionItem> byId = p.getItems().stream()
                .collect(Collectors.toMap(PrescriptionItem::getId, i -> i));
        if (byId.isEmpty()) return List.of();
        return dispensedItems.findByPrescriptionItemIdInOrderByDispensedAtAscIdAsc(byId.keySet()).stream()
                .map(d -> new DispenseRecord(d.getId(), d.getPrescriptionItemId(),
                        byId.get(d.getPrescriptionItemId()).getMedicine().getName(),
                        d.getBatch().getBatchNo(), d.getBatch().getExpiryDate(), d.getQuantity(),
                        d.getDispensedBy().getFullName(), d.getDispensedAt()))
                .toList();
    }
}