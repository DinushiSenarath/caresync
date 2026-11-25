package backend.report;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class ReportDtos {
    private ReportDtos() {}

    public record ReportInfo(String key, String title, String description) {}

    /** kind is one of: number, money, percent, text. */
    public record Metric(String label, Object value, String kind) {}

    public record Col(String label, String kind) {}

    public record Section(String title, String note, List<Col> columns, List<List<Object>> rows) {}

    public record ReportResponse(String key, String title, LocalDate from, LocalDate to, Instant generatedAt,
                                 List<Metric> metrics, List<Section> sections) {}
}