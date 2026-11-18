package backend.billing;

import backend.audit.AuditService;
import backend.billing.BillingDtos.*;
import backend.common.ApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ServiceChargeService {

    private final ServiceChargeRepository repo;
    private final AuditService audit;

    @Transactional(readOnly = true)
    public List<ServiceChargeResponse> list(boolean activeOnly) {
        List<ServiceCharge> list = activeOnly ? repo.findByActiveTrueOrderByNameAsc() : repo.findAllByOrderByNameAsc();
        return list.stream().map(ServiceChargeResponse::from).toList();
    }

    @Transactional
    public ServiceChargeResponse create(ServiceChargeRequest r, Long actorId) {
        String name = r.name().trim();
        if (repo.existsByNameIgnoreCase(name)) {
            throw new ApiException(HttpStatus.CONFLICT, "A service with this name already exists");
        }
        ServiceCharge c = new ServiceCharge();
        c.setName(name);
        c.setCategory(r.category());
        c.setPrice(r.price().setScale(2, RoundingMode.HALF_UP));
        c.setActive(r.active() == null || r.active());
        repo.save(c);
        audit.log(actorId, "SERVICE_CHARGE_CREATED", "SERVICE_CHARGE", c.getId(), name + " = " + c.getPrice());
        return ServiceChargeResponse.from(c);
    }

    @Transactional
    public ServiceChargeResponse update(Long id, ServiceChargeRequest r, Long actorId) {
        ServiceCharge c = repo.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Service not found"));
        String name = r.name().trim();
        if (repo.existsByNameIgnoreCaseAndIdNot(name, id)) {
            throw new ApiException(HttpStatus.CONFLICT, "A service with this name already exists");
        }
        BigDecimal oldPrice = c.getPrice();
        c.setName(name);
        c.setCategory(r.category());
        c.setPrice(r.price().setScale(2, RoundingMode.HALF_UP));
        if (r.active() != null) c.setActive(r.active());
        audit.log(actorId, "SERVICE_CHARGE_UPDATED", "SERVICE_CHARGE", id,
                name + ": " + oldPrice + " -> " + c.getPrice());
        return ServiceChargeResponse.from(c);
    }
}