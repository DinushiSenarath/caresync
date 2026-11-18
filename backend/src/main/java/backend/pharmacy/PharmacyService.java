package backend.pharmacy;

import backend.audit.AuditService;
import backend.common.ApiException;
import backend.common.PagedResponse;
import backend.pharmacy.PharmacyDtos.*;
import backend.user.User;
import backend.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;

@Service
@RequiredArgsConstructor
public class PharmacyService {

    private static final ZoneId ZONE = ZoneId.of("Asia/Colombo");
    private static final int NEAR_EXPIRY_DAYS = 30;

    private final MedicineRepository medicines;
    private final MedicineBatchRepository batches;
    private final StockMovementRepository movements;
    private final UserRepository users;
    private final AuditService audit;

    // ---------- medicines ----------

    @Transactional(readOnly = true)
    public PagedResponse<MedicineResponse> listMedicines(String q, boolean includeInactive, int page, int size) {
        String cleaned = q == null ? "" : q.trim().replaceAll("[%_\\\\]", "");
        String pattern = "%" + cleaned.toLowerCase() + "%";
        Page<Medicine> result = medicines.search(pattern, includeInactive,
                PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100)));
        Map<Long, Long> stock = stockFor(result.getContent().stream().map(Medicine::getId).toList());
        return PagedResponse.of(result.map(m -> toResponse(m, stock.getOrDefault(m.getId(), 0L))));
    }

    @Transactional
    public MedicineResponse createMedicine(MedicineRequest r, Long actorId) {
        String name = r.name().trim();
        String strength = clean(r.strength());
        if (medicines.identityExists(name, strength == null ? "" : strength, r.form(), 0L)) {
            throw new ApiException(HttpStatus.CONFLICT, "This medicine (name, strength and form) already exists");
        }
        Medicine m = new Medicine();
        apply(m, r, name, strength);
        m.setActive(r.active() == null || r.active());
        medicines.save(m);
        audit.log(actorId, "MEDICINE_CREATED", "MEDICINE", m.getId(), "name=" + name);
        return toResponse(m, 0L);
    }

    @Transactional
    public MedicineResponse updateMedicine(Long id, MedicineRequest r, Long actorId) {
        Medicine m = findMedicine(id);
        String name = r.name().trim();
        String strength = clean(r.strength());
        if (medicines.identityExists(name, strength == null ? "" : strength, r.form(), id)) {
            throw new ApiException(HttpStatus.CONFLICT, "This medicine (name, strength and form) already exists");
        }
        apply(m, r, name, strength);
        if (r.active() != null) m.setActive(r.active());
        audit.log(actorId, "MEDICINE_UPDATED", "MEDICINE", id, "name=" + name);
        return toResponse(m, stockFor(List.of(id)).getOrDefault(id, 0L));
    }

    // ---------- batches and stock ----------

    @Transactional(readOnly = true)
    public List<BatchResponse> listBatches(Long medicineId) {
        findMedicine(medicineId);
        LocalDate today = today();
        return batches.findByMedicineIdOrderByExpiryDateAscIdAsc(medicineId).stream()
                .map(b -> BatchResponse.from(b, today)).toList();
    }

    @Transactional
    public BatchResponse addBatch(Long medicineId, BatchRequest r, Long actorId) {
        Medicine m = findMedicine(medicineId);
        if (!m.isActive()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "This medicine is not active");
        }
        LocalDate today = today();
        if (!r.expiryDate().isAfter(today)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Expiry date must be in the future");
        }
        String batchNo = r.batchNo().trim();
        if (batches.existsByMedicineIdAndBatchNoIgnoreCase(medicineId, batchNo)) {
            throw new ApiException(HttpStatus.CONFLICT,
                    "This batch number already exists for this medicine. Use a stock adjustment to change its quantity.");
        }
        MedicineBatch b = new MedicineBatch();
        b.setMedicine(m);
        b.setBatchNo(batchNo);
        b.setExpiryDate(r.expiryDate());
        b.setQuantity(r.quantity());
        batches.save(b);
        record(b, MovementType.RECEIPT, r.quantity(), "Batch received", actorId);
        audit.log(actorId, "STOCK_RECEIVED", "MEDICINE_BATCH", b.getId(),
                "medicineId=" + medicineId + ", batch=" + batchNo + ", qty=" + r.quantity());
        return BatchResponse.from(b, today);
    }

    @Transactional
    public BatchResponse adjust(Long batchId, AdjustRequest r, Long actorId) {
        if (r.quantityChange() == 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "The change cannot be zero");
        }
        MedicineBatch b = batches.lockById(batchId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Batch not found"));
        int newQuantity = b.getQuantity() + r.quantityChange();
        if (newQuantity < 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "This adjustment would make the quantity negative");
        }
        b.setQuantity(newQuantity);
        record(b, MovementType.ADJUSTMENT, r.quantityChange(), r.reason().trim(), actorId);
        audit.log(actorId, "STOCK_ADJUSTED", "MEDICINE_BATCH", batchId,
                "change=" + r.quantityChange() + ", reason=" + r.reason().trim());
        return BatchResponse.from(b, today());
    }

    @Transactional(readOnly = true)
    public AlertsResponse alerts() {
        LocalDate today = today();
        List<Medicine> tracked = medicines.findByActiveTrueAndReorderLevelGreaterThan(0);
        Map<Long, Long> stock = stockFor(tracked.stream().map(Medicine::getId).toList());
        List<MedicineResponse> low = tracked.stream()
                .filter(m -> stock.getOrDefault(m.getId(), 0L) <= m.getReorderLevel())
                .map(m -> toResponse(m, stock.getOrDefault(m.getId(), 0L)))
                .toList();

        List<MedicineBatch> soon = batches.findExpiringBy(today.plusDays(NEAR_EXPIRY_DAYS));
        List<BatchResponse> near = soon.stream().filter(b -> !b.getExpiryDate().isBefore(today))
                .map(b -> BatchResponse.from(b, today)).toList();
        List<BatchResponse> expired = soon.stream().filter(b -> b.getExpiryDate().isBefore(today))
                .map(b -> BatchResponse.from(b, today)).toList();
        return new AlertsResponse(low, near, expired);
    }

    @Transactional(readOnly = true)
    public PagedResponse<MovementResponse> listMovements(Long medicineId, int page, int size) {
        PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100));
        return PagedResponse.of(movements
                .search(medicineId == null, medicineId == null ? 0L : medicineId, pageable)
                .map(MovementResponse::from));
    }

    // ---------- helpers ----------

    private void record(MedicineBatch b, MovementType type, int quantity, String reference, Long actorId) {
        User u = users.findById(actorId)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Account not available"));
        StockMovement s = new StockMovement();
        s.setBatch(b);
        s.setType(type);
        s.setQuantity(quantity);
        s.setReference(reference);
        s.setUser(u);
        movements.save(s);
    }

    private void apply(Medicine m, MedicineRequest r, String name, String strength) {
        m.setName(name);
        m.setStrength(strength);
        m.setForm(r.form());
        m.setUnit(r.unit().trim());
        m.setCategory(clean(r.category()));
        m.setReorderLevel(r.reorderLevel());
        if (r.unitPrice() != null) m.setUnitPrice(r.unitPrice());
    }

    private Map<Long, Long> stockFor(Collection<Long> ids) {
        if (ids.isEmpty()) return Map.of();
        Map<Long, Long> map = new HashMap<>();
        for (Object[] row : batches.stockByMedicine(ids, today())) {
            map.put(((Number) row[0]).longValue(), ((Number) row[1]).longValue());
        }
        return map;
    }

    private MedicineResponse toResponse(Medicine m, long stock) {
        boolean low = m.getReorderLevel() > 0 && stock <= m.getReorderLevel();
                return new MedicineResponse(m.getId(), m.getName(), m.getStrength(), m.getForm(), m.getUnit(),
                m.getCategory(), m.getReorderLevel(), m.isActive(), stock, low, m.getUnitPrice());
    }

    private Medicine findMedicine(Long id) {
        return medicines.findById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Medicine not found"));
    }

    private String clean(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private LocalDate today() {
        return LocalDate.now(ZONE);
    }
}