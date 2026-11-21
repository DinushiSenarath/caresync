package backend.billing;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.math.BigDecimal;
import java.time.Instant;
import org.springframework.data.repository.query.Param;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    @Query(value = "SELECT nextval('receipt_no_seq')", nativeQuery = true)
    Long nextReceiptNumber();

    @Query(value = "SELECT nextval('refund_no_seq')", nativeQuery = true)
    Long nextRefundNumber();

    List<Payment> findByInvoiceIdOrderByPaidAtAscIdAsc(Long invoiceId);

    @Query("select sum(p.amount) from Payment p where p.paidAt >= :from and p.paidAt < :to")
    BigDecimal netCollected(@Param("from") Instant from, @Param("to") Instant to);
}