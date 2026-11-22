package backend.config;

import backend.ward.Bed;
import backend.ward.BedRepository;
import backend.ward.Ward;
import backend.ward.WardRepository;
import backend.ward.WardType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Slf4j
@Component
@Order(6)
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.seed-demo-data", havingValue = "true")
public class DemoWardSeeder implements CommandLineRunner {

    private final WardRepository wards;
    private final BedRepository beds;

    @Override
    public void run(String... args) {
        if (wards.count() > 0) return;
        ward("General Ward A", WardType.GENERAL, "Adult general medicine", "1500.00", "A-", 12);
        ward("Private Rooms", WardType.PRIVATE, "Single rooms", "5000.00", "P-", 6);
        ward("Intensive Care Unit", WardType.ICU, "Critical care", "15000.00", "ICU-", 4);
        ward("Maternity Ward", WardType.MATERNITY, "Mothers and newborns", "2500.00", "M-", 6);
        log.info("Synthetic wards and beds are ready.");
    }

    private void ward(String name, WardType type, String description, String rate, String prefix, int count) {
        Ward w = new Ward();
        w.setName(name);
        w.setWardType(type);
        w.setDescription(description);
        w.setDailyRate(new BigDecimal(rate));
        wards.save(w);
        for (int i = 1; i <= count; i++) {
            Bed b = new Bed();
            b.setWard(w);
            b.setBedNo(prefix + String.format("%02d", i));
            beds.save(b);
        }
    }
}