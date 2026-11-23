package backend.hr;

import backend.common.ApiException;
import org.springframework.http.HttpStatus;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;

public final class HrDates {
    public static final ZoneId ZONE = ZoneId.of("Asia/Colombo");

    private HrDates() {}

    public static LocalDate today() {
        return LocalDate.now(ZONE);
    }

    public static boolean isWorkingDay(LocalDate d) {
        DayOfWeek w = d.getDayOfWeek();
        return w != DayOfWeek.SATURDAY && w != DayOfWeek.SUNDAY;
    }

    /** Monday to Friday days between the two dates, both included. */
    public static int workingDays(LocalDate from, LocalDate to) {
        int n = 0;
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            if (isWorkingDay(d)) n++;
        }
        return n;
    }

    public static YearMonth month(String s) {
        if (s == null || s.isBlank()) return YearMonth.now(ZONE);
        try {
            return YearMonth.parse(s.trim());
        } catch (DateTimeParseException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Month must look like 2026-11");
        }
    }
}