package backend.config;

import backend.billing.ChargeCategory;
import backend.billing.ServiceCharge;
import backend.billing.ServiceChargeRepository;
import backend.pharmacy.Medicine;
import backend.pharmacy.MedicineRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Map;

@Slf4j
@Component
@Order(4)
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.seed-demo-data", havingValue = "true")
public class DemoBillingSeeder implements CommandLineRunner {

    private final ServiceChargeRepository charges;
    private final MedicineRepository medicines;

    @Override
    public void run(String... args) {
        if (charges.count() == 0) {
            charge("General consultation", ChargeCategory.CONSULTATION, "1500.00");
            charge("Specialist consultation", ChargeCategory.CONSULTATION, "3000.00");
            charge("Minor procedure (dressing)", ChargeCategory.PROCEDURE, "1000.00");
            charge("Full blood count", ChargeCategory.INVESTIGATION, "1200.00");
            charge("ECG", ChargeCategory.INVESTIGATION, "2000.00");
            log.info("Synthetic service prices are ready.");
        }
        Map<String, String> prices = Map.of(
                "paracetamol", "5.00", "amoxicillin", "25.00", "cetirizine", "8.00",
                "aspirin", "6.00", "ors", "40.00", "salbutamol", "850.00");
        for (Medicine m : medicines.findAll()) {
            String price = prices.get(m.getName().toLowerCase());
            if (price != null && (m.getUnitPrice() == null || m.getUnitPrice().signum() == 0)) {
                m.setUnitPrice(new BigDecimal(price));
                medicines.save(m);
            }
        }
    }

    private void charge(String name, ChargeCategory category, String price) {
        ServiceCharge c = new ServiceCharge();
        c.setName(name);
        c.setCategory(category);
        c.setPrice(new BigDecimal(price));
        charges.save(c);
    }
}