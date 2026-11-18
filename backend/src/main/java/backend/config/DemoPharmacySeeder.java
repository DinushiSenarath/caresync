package backend.config;

import backend.pharmacy.*;
import backend.user.User;
import backend.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;

@Slf4j
@Component
@Order(3)
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.seed-demo-data", havingValue = "true")
public class DemoPharmacySeeder implements CommandLineRunner {

    private final MedicineRepository medicines;
    private final MedicineBatchRepository batches;
    private final StockMovementRepository movements;
    private final UserRepository users;

    @Override
    public void run(String... args) {
        if (medicines.count() > 0) return;
        User pharmacist = users.findByUsername("pharmacist").orElse(null);
        if (pharmacist == null) return;
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Colombo"));

        Medicine para = medicine("Paracetamol", "500 mg", MedicineForm.TABLET, "tablet", "Analgesic", 200);
        Medicine amox = medicine("Amoxicillin", "500 mg", MedicineForm.CAPSULE, "capsule", "Antibiotic", 100);
        Medicine ceti = medicine("Cetirizine", "10 mg", MedicineForm.TABLET, "tablet", "Antihistamine", 50);
        Medicine aspi = medicine("Aspirin", "75 mg", MedicineForm.TABLET, "tablet", "Antiplatelet", 50);
        Medicine ors = medicine("ORS", "20.5 g", MedicineForm.OTHER, "sachet", "Rehydration", 40);

        batch(para, "PCM-A1", today.plusMonths(18), 1000, pharmacist);
        batch(para, "PCM-OLD", today.minusDays(10), 50, pharmacist);   // expired example
        batch(amox, "AMX-B1", today.plusMonths(12), 300, pharmacist);
        batch(amox, "AMX-B2", today.plusDays(20), 40, pharmacist);     // near-expiry example
        batch(ceti, "CTZ-C1", today.plusMonths(24), 200, pharmacist);
        batch(aspi, "ASP-D1", today.plusMonths(10), 30, pharmacist);   // low-stock example
        batch(ors, "ORS-E1", today.plusMonths(20), 150, pharmacist);
        log.info("Synthetic pharmacy stock is ready.");
    }

    private Medicine medicine(String name, String strength, MedicineForm form, String unit,
                              String category, int reorderLevel) {
        Medicine m = new Medicine();
        m.setName(name);
        m.setStrength(strength);
        m.setForm(form);
        m.setUnit(unit);
        m.setCategory(category);
        m.setReorderLevel(reorderLevel);
        return medicines.save(m);
    }

    private void batch(Medicine m, String batchNo, LocalDate expiry, int qty, User user) {
        MedicineBatch b = new MedicineBatch();
        b.setMedicine(m);
        b.setBatchNo(batchNo);
        b.setExpiryDate(expiry);
        b.setQuantity(qty);
        batches.save(b);
        StockMovement s = new StockMovement();
        s.setBatch(b);
        s.setType(MovementType.RECEIPT);
        s.setQuantity(qty);
        s.setReference("Opening stock");
        s.setUser(user);
        movements.save(s);
    }
}