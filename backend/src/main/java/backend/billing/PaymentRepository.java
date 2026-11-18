package backend.billing;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    @Query(value = "SELECT nextval('receipt_no_seq')", nativeQuery = true)
    Long nextReceiptNumber();

    @Query(value = "SELECT nextval('refund_no_seq')", nativeQuery = true)
    Long nextRefundNumber();

    List<Payment> findByInvoiceIdOrderByPaidAtAscIdAsc(Long invoiceId);
}