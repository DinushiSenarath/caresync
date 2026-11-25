package backend.report;

import backend.audit.AuditService;
import backend.common.ApiException;
import backend.pharmacy.PharmacyDtos.AlertsResponse;
import backend.pharmacy.PharmacyService;
import backend.report.ReportDtos.*;
import backend.user.Role;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ReportService {

    private static final ZoneId ZONE = ZoneId.of("Asia/Colombo");
    private static final String BETWEEN = " between :fromDate and :toDate";

    private static final List<ReportInfo> ALL = List.of(
            new ReportInfo("patients", "Patient report", "New registrations, age and gender mix, and online registration requests"),
            new ReportInfo("appointments", "Appointment report", "Appointments by status, doctor, department and day, with no-show and cancellation rates"),
            new ReportInfo("revenue", "Revenue report", "Money collected, invoiced, discounts and outstanding balances"),
            new ReportInfo("pharmacy", "Pharmacy report", "Medicines dispensed, stock movements, prescriptions, low and expiring stock"),
            new ReportInfo("laboratory", "Laboratory report", "Requests, tests ordered, time to release and abnormal results"),
            new ReportInfo("staff", "Staff report", "Headcount, attendance and leave"));

    private static final Map<String, Set<Role>> ACCESS = Map.of(
            "patients", Set.of(Role.ADMIN, Role.RECEPTIONIST),
            "appointments", Set.of(Role.ADMIN, Role.RECEPTIONIST),
            "revenue", Set.of(Role.ADMIN, Role.CASHIER),
            "pharmacy", Set.of(Role.ADMIN, Role.PHARMACIST),
            "laboratory", Set.of(Role.ADMIN, Role.LAB_TECH),
            "staff", Set.of(Role.ADMIN));

    private final EntityManager em;
    private final PharmacyService pharmacy;
    private final AuditService audit;

    public List<ReportInfo> available(Role role) {
        return ALL.stream().filter(r -> ACCESS.get(r.key()).contains(role)).toList();
    }

    @Transactional
    public ReportResponse generate(String key, LocalDate from, LocalDate to, Long doctorId, Long actorId, Role role) {
        Set<Role> allowed = ACCESS.get(key);
        if (allowed == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Unknown report");
        }
        if (!allowed.contains(role)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "You do not have access to this report");
        }
        LocalDate today = LocalDate.now(ZONE);
        LocalDate f = from != null ? from : today.withDayOfMonth(1);
        LocalDate t = to != null ? to : today;
        if (t.isBefore(f)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "The end date is before the start date");
        }
        if (f.plusDays(366).isBefore(t)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Choose a range of one year or less");
        }
        ReportResponse r = switch (key) {
            case "patients" -> patients(f, t);
            case "appointments" -> appointments(f, t, doctorId);
            case "revenue" -> revenue(f, t);
            case "pharmacy" -> pharmacyReport(f, t);
            case "laboratory" -> laboratory(f, t);
            default -> staff(f, t);
        };
        audit.log(actorId, "REPORT_GENERATED", "REPORT", null, key + ", " + f + " to " + t);
        return r;
    }

    // =====================================================================
    // patients
    // =====================================================================

    private ReportResponse patients(LocalDate f, LocalDate t) {
        Map<String, Object> p = range(f, t);
        String created = local("created_at");
        long newPatients = lng(one("select count(*) from patients where " + created + BETWEEN, p)[0]);
        long active = lng(one("select count(*) from patients where active = true", Map.of())[0]);
        long portal = lng(one("select count(*) from patients where active = true and user_id is not null", Map.of())[0]);
        Object[] reg = one("select count(*), count(*) filter (where status = 'APPROVED'), "
                + "count(*) filter (where status = 'REJECTED'), count(*) filter (where status = 'PENDING') "
                + "from registration_requests where " + created + BETWEEN, p);

        String age = "select grp, count(*) from (select case when a < 13 then '0 to 12' when a < 18 then '13 to 17' "
                + "when a < 40 then '18 to 39' when a < 60 then '40 to 59' else '60 and over' end as grp, a "
                + "from (select CAST(date_part('year', age(current_date, date_of_birth)) AS integer) as a "
                + "from patients where " + created + BETWEEN + ") x) y group by grp order by min(a)";

        return new ReportResponse("patients", "Patient report", f, t, Instant.now(),
                List.of(
                        mNum("New patients in this period", newPatients),
                        mNum("Active patients in total", active),
                        mNum("Patients with a portal account", portal),
                        mNum("Online registration requests received", lng(reg[0])),
                        mNum("Requests approved", lng(reg[1])),
                        mNum("Requests rejected", lng(reg[2])),
                        mNum("Requests still waiting", lng(reg[3]))),
                List.of(
                        sec("New patients by gender", null, List.of(cText("Gender"), cNum("Patients")),
                                rows("select initcap(lower(gender)), count(*) from patients where " + created + BETWEEN
                                        + " group by gender order by gender", p)),
                        sec("New patients by age group", null, List.of(cText("Age group"), cNum("Patients")), rows(age, p)),
                        sec("Registrations per day", null, List.of(cText("Date"), cNum("New patients")),
                                rows("select " + created + ", count(*) from patients where " + created + BETWEEN
                                        + " group by 1 order by 1", p))));
    }

    // =====================================================================
    // appointments
    // =====================================================================

    private ReportResponse appointments(LocalDate f, LocalDate t, Long doctorId) {
        Map<String, Object> p = new HashMap<>(range(f, t));
        String doctorFilter = "";
        String doctorName = null;
        if (doctorId != null) {
            List<Object[]> d = rows("select u.full_name from doctors d join users u on u.id = d.user_id where d.id = :doctorId",
                    Map.of("doctorId", doctorId));
            if (d.isEmpty()) {
                throw new ApiException(HttpStatus.NOT_FOUND, "Doctor not found");
            }
            doctorName = String.valueOf(d.get(0)[0]);
            doctorFilter = " and a.doctor_id = :doctorId";
            p.put("doctorId", doctorId);
        }
        String where = " where a.appointment_date" + BETWEEN + doctorFilter;

        Object[] s = one("select count(*), count(*) filter (where a.status = 'COMPLETED'), "
                + "count(*) filter (where a.status = 'CANCELLED'), count(*) filter (where a.status = 'MISSED'), "
                + "count(*) filter (where a.status in ('REQUESTED','SCHEDULED','CHECKED_IN','IN_PROGRESS')) "
                + "from appointments a" + where, p);
        long total = lng(s[0]);
        long completed = lng(s[1]);
        long cancelled = lng(s[2]);
        long missed = lng(s[3]);
        long open = lng(s[4]);
        double cancelRate = total == 0 ? 0 : round1(cancelled * 100.0 / total);
        long due = completed + missed;
        double missedRate = due == 0 ? 0 : round1(missed * 100.0 / due);

        List<Metric> metrics = new ArrayList<>();
        if (doctorName != null) metrics.add(mText("Doctor", doctorName));
        metrics.add(mNum("Appointments in this period", total));
        metrics.add(mNum("Completed", completed));
        metrics.add(mNum("Cancelled", cancelled));
        metrics.add(mNum("Missed (did not attend)", missed));
        metrics.add(mNum("Still open (requested, scheduled or in progress)", open));
        metrics.add(mPct("Cancellation rate", cancelRate));
        metrics.add(mPct("Missed rate (of completed and missed)", missedRate));

        String counts = "count(*), count(*) filter (where a.status = 'COMPLETED'), "
                + "count(*) filter (where a.status = 'CANCELLED'), count(*) filter (where a.status = 'MISSED')";
        String joins = " from appointments a join doctors d on d.id = a.doctor_id join users u on u.id = d.user_id "
                + "join departments dp on dp.id = d.department_id";
        List<Col> breakdown = List.of(cNum("Total"), cNum("Completed"), cNum("Cancelled"), cNum("Missed"));

        List<Col> doctorCols = new ArrayList<>(List.of(cText("Doctor"), cText("Department")));
        doctorCols.addAll(breakdown);
        List<Col> deptCols = new ArrayList<>(List.of(cText("Department")));
        deptCols.addAll(breakdown);
        List<Col> dayCols = new ArrayList<>(List.of(cText("Date")));
        dayCols.addAll(breakdown);

        return new ReportResponse("appointments", "Appointment report", f, t, Instant.now(), metrics,
                List.of(
                        sec("Appointments by status", null, List.of(cText("Status"), cNum("Appointments")),
                                rows("select initcap(replace(lower(a.status), '_', ' ')), count(*) from appointments a" + where
                                        + " group by a.status order by count(*) desc", p)),
                        sec("By doctor", null, doctorCols,
                                rows("select u.full_name, dp.name, " + counts + joins + where
                                        + " group by u.full_name, dp.name order by count(*) desc, u.full_name", p)),
                        sec("By department", null, deptCols,
                                rows("select dp.name, " + counts + joins + where
                                        + " group by dp.name order by count(*) desc, dp.name", p)),
                        sec("By day", null, dayCols,
                                rows("select a.appointment_date, " + counts + " from appointments a" + where
                                        + " group by a.appointment_date order by a.appointment_date", p))));
    }

    // =====================================================================
    // revenue
    // =====================================================================

    private ReportResponse revenue(LocalDate f, LocalDate t) {
        Map<String, Object> p = range(f, t);
        String paid = local("p.paid_at");
        String invCreated = local("i.created_at");

        Object[] c = one("select coalesce(sum(p.amount), 0), coalesce(sum(p.amount) filter (where p.amount > 0), 0), "
                + "coalesce(sum(p.amount) filter (where p.amount < 0), 0) from payments p where " + paid + BETWEEN, p);
        Object[] inv = one("select count(*), coalesce(sum(i.total), 0), coalesce(sum(i.discount), 0) from invoices i "
                + "where i.status <> 'VOIDED' and " + invCreated + BETWEEN, p);
        Object[] out = one("select coalesce(sum(i.total - i.amount_paid), 0), count(*) from invoices i "
                + "where i.status in ('UNPAID','PARTIALLY_PAID')", Map.of());

        return new ReportResponse("revenue", "Revenue report", f, t, Instant.now(),
                List.of(
                        mMoney("Net money collected", dec(c[0])),
                        mMoney("Payments received", dec(c[1])),
                        mMoney("Refunds paid", dec(c[2]).negate()),
                        mNum("Invoices created (not voided)", lng(inv[0])),
                        mMoney("Invoiced (after discounts)", dec(inv[1])),
                        mMoney("Discounts given", dec(inv[2])),
                        mMoney("Outstanding balance right now (all invoices)", dec(out[0])),
                        mNum("Invoices still unpaid or partly paid", lng(out[1]))),
                List.of(
                        sec("Money collected by payment method", "Net of refunds.",
                                List.of(cText("Method"), cMoney("Net amount"), cNum("Transactions")),
                                rows("select initcap(replace(lower(p.method), '_', ' ')), sum(p.amount), count(*) from payments p where "
                                        + paid + BETWEEN + " group by p.method order by sum(p.amount) desc", p)),
                        sec("Charges by type", "Invoices created in this period, before discounts. Voided invoices are left out.",
                                List.of(cText("Charge type"), cMoney("Amount"), cNum("Lines")),
                                rows("select case ii.item_type when 'SERVICE' then 'Services' when 'MEDICINE' then 'Medicines' "
                                        + "when 'LAB' then 'Laboratory tests' when 'BED' then 'Bed charges' else ii.item_type end, "
                                        + "sum(ii.line_total), count(*) from invoice_items ii join invoices i on i.id = ii.invoice_id "
                                        + "where i.status <> 'VOIDED' and " + invCreated + BETWEEN
                                        + " group by ii.item_type order by sum(ii.line_total) desc", p)),
                        sec("Invoices by status", "Invoices created in this period.",
                                List.of(cText("Status"), cNum("Invoices"), cMoney("Total"), cMoney("Still to be paid")),
                                rows("select initcap(replace(lower(i.status), '_', ' ')), count(*), coalesce(sum(i.total), 0), "
                                        + "coalesce(sum(case when i.status = 'VOIDED' then 0 else i.total - i.amount_paid end), 0) "
                                        + "from invoices i where " + invCreated + BETWEEN
                                        + " group by i.status order by count(*) desc", p)),
                        sec("Collections per day", "Net of refunds.",
                                List.of(cText("Date"), cMoney("Net amount"), cNum("Transactions")),
                                rows("select " + paid + ", sum(p.amount), count(*) from payments p where " + paid + BETWEEN
                                        + " group by 1 order by 1", p))));
    }

    // =====================================================================
    // pharmacy
    // =====================================================================

    private ReportResponse pharmacyReport(LocalDate f, LocalDate t) {
        Map<String, Object> p = range(f, t);
        String moved = local("sm.created_at");
        String issued = local("issued_at");

        Object[] rx = one("select count(*), count(*) filter (where status = 'DISPENSED'), "
                + "count(*) filter (where status = 'PARTIALLY_DISPENSED'), count(*) filter (where status = 'CANCELLED') "
                + "from prescriptions where issued_at is not null and " + issued + BETWEEN, p);
        long units = lng(one("select coalesce(-sum(sm.quantity), 0) from stock_movements sm where sm.type = 'DISPENSE' and "
                + moved + BETWEEN, p)[0]);

        AlertsResponse al = pharmacy.alerts();
        List<Object[]> low = new ArrayList<>();
        al.lowStock().forEach(m -> low.add(new Object[]{
                m.name() + (m.strength() == null ? "" : " " + m.strength()), m.availableStock(), m.reorderLevel(), m.unit()}));
        List<Object[]> near = new ArrayList<>();
        al.nearExpiry().forEach(b -> near.add(new Object[]{b.medicineName(), b.batchNo(), b.expiryDate().toString(), b.quantity()}));
        List<Object[]> expired = new ArrayList<>();
        al.expired().forEach(b -> expired.add(new Object[]{b.medicineName(), b.batchNo(), b.expiryDate().toString(), b.quantity()}));
        List<Col> batchCols = List.of(cText("Medicine"), cText("Batch"), cText("Expiry date"), cNum("Quantity left"));

        return new ReportResponse("pharmacy", "Pharmacy report", f, t, Instant.now(),
                List.of(
                        mNum("Prescriptions issued in this period", lng(rx[0])),
                        mNum("Fully dispensed", lng(rx[1])),
                        mNum("Partly dispensed", lng(rx[2])),
                        mNum("Cancelled after issue", lng(rx[3])),
                        mNum("Units dispensed", units),
                        mNum("Medicines low on stock now", low.size()),
                        mNum("Batches expiring within 30 days now", near.size()),
                        mNum("Expired batches still holding stock now", expired.size())),
                List.of(
                        sec("Most dispensed medicines", "Top 20 by quantity in this period.",
                                List.of(cText("Medicine"), cNum("Units dispensed")),
                                rows("select m.name || coalesce(' ' || m.strength, ''), -sum(sm.quantity) from stock_movements sm "
                                        + "join medicine_batches b on b.id = sm.batch_id join medicines m on m.id = b.medicine_id "
                                        + "where sm.type = 'DISPENSE' and " + moved + BETWEEN
                                        + " group by m.id, m.name, m.strength order by -sum(sm.quantity) desc, 1 limit 20", p)),
                        sec("Stock movements by type", null,
                                List.of(cText("Type"), cNum("Movements"), cNum("Net units")),
                                rows("select initcap(lower(sm.type)), count(*), coalesce(sum(sm.quantity), 0) from stock_movements sm where "
                                        + moved + BETWEEN + " group by sm.type order by sm.type", p)),
                        sec("Prescriptions by status", "Prescriptions issued in this period.",
                                List.of(cText("Status"), cNum("Prescriptions")),
                                rows("select initcap(replace(lower(status), '_', ' ')), count(*) from prescriptions "
                                        + "where issued_at is not null and " + issued + BETWEEN
                                        + " group by status order by count(*) desc", p)),
                        sec("Low stock right now", "Shows the current position, not the selected dates.",
                                List.of(cText("Medicine"), cNum("In stock"), cNum("Reorder level"), cText("Unit")), low),
                        sec("Expiring within 30 days", "Shows the current position, not the selected dates.", batchCols, near),
                        sec("Expired stock to remove", "Shows the current position, not the selected dates.", batchCols, expired)));
    }

    // =====================================================================
    // laboratory
    // =====================================================================

    private ReportResponse laboratory(LocalDate f, LocalDate t) {
        Map<String, Object> p = range(f, t);
        String created = local("r.created_at");
        String verified = local("r.verified_at");
        String collected = local("r.collected_at");

        Object[] s = one("select count(*), count(*) filter (where r.status = 'VERIFIED'), "
                + "count(*) filter (where r.status = 'CANCELLED'), count(*) filter (where r.priority = 'URGENT') "
                + "from lab_requests r where " + created + BETWEEN, p);
        Object release = one("select avg(extract(epoch from (r.verified_at - r.created_at)) / 3600.0) from lab_requests r "
                + "where r.status = 'VERIFIED' and " + verified + BETWEEN, p)[0];
        Object collect = one("select avg(extract(epoch from (r.collected_at - r.created_at)) / 3600.0) from lab_requests r "
                + "where r.collected_at is not null and " + collected + BETWEEN, p)[0];
        Object[] fl = one("select count(*) filter (where i.flag is not null), count(*) filter (where i.flag in ('LOW','HIGH')) "
                + "from lab_request_items i join lab_requests r on r.id = i.request_id "
                + "where r.status = 'VERIFIED' and " + verified + BETWEEN, p);
        long flagged = lng(fl[0]);
        double abnormal = flagged == 0 ? 0 : round1(lng(fl[1]) * 100.0 / flagged);

        return new ReportResponse("laboratory", "Laboratory report", f, t, Instant.now(),
                List.of(
                        mNum("Requests in this period", lng(s[0])),
                        mNum("Released", lng(s[1])),
                        mNum("Cancelled", lng(s[2])),
                        mNum("Urgent requests", lng(s[3])),
                        mText("Average hours from request to sample collected", hours(collect)),
                        mText("Average hours from request to released", hours(release)),
                        mPct("Numeric results outside the usual range", abnormal)),
                List.of(
                        sec("Requests by status", null, List.of(cText("Status"), cNum("Requests")),
                                rows("select initcap(replace(lower(r.status), '_', ' ')), count(*) from lab_requests r where "
                                        + created + BETWEEN + " group by r.status order by count(*) desc", p)),
                        sec("Requests by priority", null, List.of(cText("Priority"), cNum("Requests")),
                                rows("select initcap(lower(r.priority)), count(*) from lab_requests r where "
                                        + created + BETWEEN + " group by r.priority order by r.priority", p)),
                        sec("Most requested tests", "Top 20, cancelled requests left out.",
                                List.of(cText("Test"), cNum("Times requested")),
                                rows("select i.test_name, count(*) from lab_request_items i join lab_requests r on r.id = i.request_id "
                                        + "where r.status <> 'CANCELLED' and " + created + BETWEEN
                                        + " group by i.test_name order by count(*) desc, i.test_name limit 20", p)),
                        sec("Released numeric results by flag", "Compared with the reference range stored with each test. Not a diagnosis.",
                                List.of(cText("Flag"), cNum("Results")),
                                rows("select initcap(lower(i.flag)), count(*) from lab_request_items i join lab_requests r on r.id = i.request_id "
                                        + "where r.status = 'VERIFIED' and i.flag is not null and " + verified + BETWEEN
                                        + " group by i.flag order by i.flag", p)),
                        sec("Requests per day", null, List.of(cText("Date"), cNum("Requests")),
                                rows("select " + created + ", count(*) from lab_requests r where " + created + BETWEEN
                                        + " group by 1 order by 1", p))));
    }

    // =====================================================================
    // staff
    // =====================================================================

    private ReportResponse staff(LocalDate f, LocalDate t) {
        Map<String, Object> p = range(f, t);
        LocalDate today = LocalDate.now(ZONE);

        long headcount = lng(one("select count(*) from employees where active = true", Map.of())[0]);
        long onLeave = lng(one("select count(distinct l.employee_id) from leave_requests l join employees e on e.id = l.employee_id "
                + "where e.active = true and l.status = 'APPROVED' and l.from_date <= :today and l.to_date >= :today",
                Map.of("today", today))[0]);
        long pending = lng(one("select count(*) from leave_requests where status = 'PENDING'", Map.of())[0]);
        Object[] att = one("select count(*) filter (where a.status = 'PRESENT'), count(*) filter (where a.status = 'HALF_DAY'), "
                + "count(*) filter (where a.status = 'ABSENT'), "
                + "coalesce(round(CAST(sum(extract(epoch from (a.check_out - a.check_in)) / 3600.0) AS numeric), 1), 0) "
                + "from attendance_records a where a.work_date" + BETWEEN, p);

        return new ReportResponse("staff", "Staff report", f, t, Instant.now(),
                List.of(
                        mNum("Current employees", headcount),
                        mNum("On approved leave today", onLeave),
                        mNum("Leave requests waiting for a decision", pending),
                        mNum("Days present in this period", lng(att[0])),
                        mNum("Half days in this period", lng(att[1])),
                        mNum("Days absent in this period", lng(att[2])),
                        mNum("Hours worked in this period", dec(att[3]))),
                List.of(
                        sec("Current employees by department", null, List.of(cText("Department"), cNum("Employees")),
                                rows("select d.name, count(*) from employees e join departments d on d.id = e.department_id "
                                        + "where e.active = true group by d.name order by count(*) desc, d.name", Map.of())),
                        sec("Current employees by employment type", null, List.of(cText("Type"), cNum("Employees")),
                                rows("select initcap(replace(lower(e.employment_type), '_', ' ')), count(*) from employees e "
                                        + "where e.active = true group by e.employment_type order by count(*) desc", Map.of())),
                        sec("Attendance by department", "Attendance records dated in this period.",
                                List.of(cText("Department"), cNum("Days present"), cNum("Days absent"), cNum("Hours worked")),
                                rows("select d.name, count(*) filter (where a.status = 'PRESENT') + 0.5 * count(*) filter (where a.status = 'HALF_DAY'), "
                                        + "count(*) filter (where a.status = 'ABSENT'), "
                                        + "coalesce(round(CAST(sum(extract(epoch from (a.check_out - a.check_in)) / 3600.0) AS numeric), 1), 0) "
                                        + "from attendance_records a join employees e on e.id = a.employee_id join departments d on d.id = e.department_id "
                                        + "where a.work_date" + BETWEEN + " group by d.name order by d.name", p)),
                        sec("Approved leave starting in this period", null,
                                List.of(cText("Leave type"), cNum("Requests"), cNum("Working days")),
                                rows("select initcap(lower(l.leave_type)), count(*), coalesce(sum(l.days), 0) from leave_requests l "
                                        + "where l.status = 'APPROVED' and l.from_date" + BETWEEN
                                        + " group by l.leave_type order by l.leave_type", p)),
                        sec("Leave requests made in this period, by status", null,
                                List.of(cText("Status"), cNum("Requests")),
                                rows("select initcap(lower(l.status)), count(*) from leave_requests l where "
                                        + local("l.created_at") + BETWEEN + " group by l.status order by l.status", p))));
    }

    // =====================================================================
    // helpers
    // =====================================================================

    private Map<String, Object> range(LocalDate f, LocalDate t) {
        return Map.of("fromDate", f, "toDate", t);
    }

    /** A timestamp column as a calendar date in Sri Lankan time. The column name is always a fixed text from this class. */
    private String local(String column) {
        return "CAST((" + column + " AT TIME ZONE 'Asia/Colombo') AS date)";
    }

    private Query query(String sql, Map<String, Object> params) {
        Query q = em.createNativeQuery(sql);
        params.forEach(q::setParameter);
        return q;
    }

    private List<Object[]> rows(String sql, Map<String, Object> params) {
        List<?> raw = query(sql, params).getResultList();
        List<Object[]> out = new ArrayList<>();
        for (Object o : raw) {
            out.add(o instanceof Object[] a ? a : new Object[]{o});
        }
        return out;
    }

    private Object[] one(String sql, Map<String, Object> params) {
        List<Object[]> r = rows(sql, params);
        return r.isEmpty() ? new Object[]{0, 0, 0, 0, 0, 0, 0, 0} : r.get(0);
    }

    private long lng(Object o) {
        return o == null ? 0L : ((Number) o).longValue();
    }

    private BigDecimal dec(Object o) {
        return o == null ? BigDecimal.ZERO : new BigDecimal(o.toString());
    }

    private double round1(double v) {
        return Math.round(v * 10.0) / 10.0;
    }

    private String hours(Object avg) {
        return avg == null ? null : dec(avg).setScale(1, RoundingMode.HALF_UP).toPlainString();
    }

    private Object cell(Object o) {
        if (o == null) return null;
        if (o instanceof java.sql.Date d) return d.toLocalDate().toString();
        if (o instanceof LocalDate d) return d.toString();
        if (o instanceof Number || o instanceof String) return o;
        return o.toString();
    }

    private Section sec(String title, String note, List<Col> cols, List<Object[]> data) {
        List<List<Object>> out = new ArrayList<>();
        for (Object[] r : data) {
            List<Object> row = new ArrayList<>();
            for (Object o : r) row.add(cell(o));
            out.add(row);
        }
        return new Section(title, note, cols, out);
    }

    private Metric mNum(String label, Object v) { return new Metric(label, v, "number"); }
    private Metric mMoney(String label, Object v) { return new Metric(label, v, "money"); }
    private Metric mPct(String label, Object v) { return new Metric(label, v, "percent"); }
    private Metric mText(String label, Object v) { return new Metric(label, v, "text"); }
    private Col cText(String label) { return new Col(label, "text"); }
    private Col cNum(String label) { return new Col(label, "number"); }
    private Col cMoney(String label) { return new Col(label, "money"); }
}