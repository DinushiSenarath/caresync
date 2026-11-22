package backend.ward;

import backend.audit.AuditService;
import backend.common.ApiException;
import backend.ward.WardDtos.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class WardService {

    private final WardRepository wards;
    private final BedRepository beds;
    private final BedStayRepository stays;
    private final AuditService audit;

    @Transactional(readOnly = true)
    public List<WardResponse> list(boolean includeInactive) {
        Map<Long, BedStay> open = new HashMap<>();
        for (BedStay s : stays.findOpenWithDetails()) open.put(s.getBed().getId(), s);
        Map<Long, List<Bed>> byWard = new HashMap<>();
        for (Bed b : beds.findAllWithWard()) {
            byWard.computeIfAbsent(b.getWard().getId(), k -> new ArrayList<>()).add(b);
        }
        return wards.findAllByOrderByNameAsc().stream()
                .filter(w -> includeInactive || w.isActive())
                .map(w -> build(w, byWard.getOrDefault(w.getId(), List.of()), open))
                .toList();
    }

    @Transactional
    public WardResponse createWard(WardRequest r, Long actorId) {
        String name = r.name().trim();
        if (wards.existsByNameIgnoreCase(name)) {
            throw new ApiException(HttpStatus.CONFLICT, "A ward with this name already exists");
        }
        Ward w = new Ward();
        apply(w, r, name);
        w.setActive(r.active() == null || r.active());
        wards.save(w);
        audit.log(actorId, "WARD_CREATED", "WARD", w.getId(), name);
        return build(w, List.of(), Map.of());
    }

    @Transactional
    public WardResponse updateWard(Long id, WardRequest r, Long actorId) {
        Ward w = wards.findById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Ward not found"));
        String name = r.name().trim();
        if (wards.existsByNameIgnoreCaseAndIdNot(name, id)) {
            throw new ApiException(HttpStatus.CONFLICT, "A ward with this name already exists");
        }
        if (Boolean.FALSE.equals(r.active()) && w.isActive() && stays.existsOpenInWard(id)) {
            throw new ApiException(HttpStatus.CONFLICT, "This ward has patients in it. Move or discharge them first.");
        }
        apply(w, r, name);
        if (r.active() != null) w.setActive(r.active());
        audit.log(actorId, "WARD_UPDATED", "WARD", id, name);
        return list(true).stream().filter(x -> x.id().equals(id)).findFirst().orElseGet(() -> build(w, List.of(), Map.of()));
    }

    @Transactional
    public BedResponse createBed(Long wardId, BedRequest r, Long actorId) {
        Ward w = wards.findById(wardId).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Ward not found"));
        String bedNo = r.bedNo().trim();
        if (beds.existsByWardIdAndBedNoIgnoreCase(wardId, bedNo)) {
            throw new ApiException(HttpStatus.CONFLICT, "This ward already has a bed with that number");
        }
        Bed b = new Bed();
        b.setWard(w);
        b.setBedNo(bedNo);
        b.setActive(r.active() == null || r.active());
        beds.save(b);
        audit.log(actorId, "BED_CREATED", "BED", b.getId(), w.getName() + " " + bedNo);
        return bedResponse(b, null);
    }

    @Transactional
    public BedResponse updateBed(Long id, BedRequest r, Long actorId) {
        Bed b = beds.findById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Bed not found"));
        String bedNo = r.bedNo().trim();
        if (beds.existsByWardIdAndBedNoIgnoreCaseAndIdNot(b.getWard().getId(), bedNo, id)) {
            throw new ApiException(HttpStatus.CONFLICT, "This ward already has a bed with that number");
        }
        if (Boolean.FALSE.equals(r.active()) && b.isActive() && stays.existsByBedIdAndToAtIsNull(id)) {
            throw new ApiException(HttpStatus.CONFLICT, "This bed is occupied. Move the patient first.");
        }
        b.setBedNo(bedNo);
        if (r.active() != null) b.setActive(r.active());
        audit.log(actorId, "BED_UPDATED", "BED", id, b.getWard().getName() + " " + bedNo + ", active=" + b.isActive());
        return bedResponse(b, null);
    }

    // ---------- helpers ----------

    private void apply(Ward w, WardRequest r, String name) {
        w.setName(name);
        w.setWardType(r.wardType());
        String d = r.description();
        w.setDescription(d == null || d.isBlank() ? null : d.trim());
        w.setDailyRate(r.dailyRate().setScale(2, RoundingMode.HALF_UP));
    }

    private WardResponse build(Ward w, List<Bed> list, Map<Long, BedStay> open) {
        List<BedResponse> out = new ArrayList<>();
        int usable = 0;
        int occupied = 0;
        int outOfService = 0;
        for (Bed b : list) {
            BedStay s = open.get(b.getId());
            out.add(bedResponse(b, s));
            if (b.isActive()) {
                usable++;
                if (s != null) occupied++;
            } else {
                outOfService++;
            }
        }
        return new WardResponse(w.getId(), w.getName(), w.getWardType(), w.getDescription(), w.getDailyRate(),
                w.isActive(), usable, occupied, usable - occupied, outOfService, out);
    }

    private BedResponse bedResponse(Bed b, BedStay s) {
        if (s == null) {
            return new BedResponse(b.getId(), b.getWard().getId(), b.getBedNo(), b.isActive(), false,
                    null, null, null, null, null, null);
        }
        Admission a = s.getAdmission();
        return new BedResponse(b.getId(), b.getWard().getId(), b.getBedNo(), b.isActive(), true, a.getId(),
                a.getAdmissionNo(), a.getPatient().getId(), a.getPatient().getPatientNo(),
                a.getPatient().getFullName(), s.getFromAt());
    }
}