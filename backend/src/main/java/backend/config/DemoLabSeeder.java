package backend.config;

import backend.lab.LabCategory;
import backend.lab.LabTest;
import backend.lab.LabTestRepository;
import backend.lab.ResultType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Slf4j
@Component
@Order(5)
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.seed-demo-data", havingValue = "true")
public class DemoLabSeeder implements CommandLineRunner {

    private final LabTestRepository tests;

    @Override
    public void run(String... args) {
        if (tests.count() > 0) return;
        // General adult reference values for DEMO only. A real laboratory sets its own ranges.
        numeric("Haemoglobin", LabCategory.HAEMATOLOGY, "Blood", "g/dL", "12.0", "17.5", "600.00");
        numeric("White cell count", LabCategory.HAEMATOLOGY, "Blood", "x10^9/L", "4.0", "11.0", "600.00");
        numeric("Platelet count", LabCategory.HAEMATOLOGY, "Blood", "x10^9/L", "150", "450", "600.00");
        numeric("Fasting blood sugar", LabCategory.BIOCHEMISTRY, "Blood", "mg/dL", "70", "100", "400.00");
        numeric("HbA1c", LabCategory.BIOCHEMISTRY, "Blood", "%", "4.0", "5.6", "1800.00");
        numeric("Serum creatinine", LabCategory.BIOCHEMISTRY, "Blood", "mg/dL", "0.6", "1.3", "700.00");
        numeric("Total cholesterol", LabCategory.BIOCHEMISTRY, "Blood", "mg/dL", "0", "200", "900.00");
        text("Urine full report", LabCategory.URINALYSIS, "Urine", "Within normal limits", "500.00");
        text("Dengue NS1 antigen", LabCategory.IMMUNOLOGY, "Blood", "Negative", "3500.00");
        text("Blood culture", LabCategory.MICROBIOLOGY, "Blood", "No growth", "4500.00");
        log.info("Synthetic laboratory tests are ready.");
    }

    private void numeric(String name, LabCategory c, String specimen, String unit, String low, String high, String price) {
        LabTest t = base(name, c, specimen, ResultType.NUMERIC, price);
        t.setUnit(unit);
        t.setRefLow(new BigDecimal(low));
        t.setRefHigh(new BigDecimal(high));
        tests.save(t);
    }

    private void text(String name, LabCategory c, String specimen, String refText, String price) {
        LabTest t = base(name, c, specimen, ResultType.TEXT, price);
        t.setRefText(refText);
        tests.save(t);
    }

    private LabTest base(String name, LabCategory c, String specimen, ResultType type, String price) {
        LabTest t = new LabTest();
        t.setName(name);
        t.setCategory(c);
        t.setSpecimen(specimen);
        t.setResultType(type);
        t.setPrice(new BigDecimal(price));
        return t;
    }
}