package backend.patient;

import backend.patient.PatientDtos.DuplicateMatch;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.List;

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice
public class PatientExceptionHandler {

    public record DuplicateResponse(Instant timestamp, int status, String message,
                                    List<DuplicateMatch> possibleDuplicates) {}

    @ExceptionHandler(DuplicatePatientException.class)
    public ResponseEntity<DuplicateResponse> handle(DuplicatePatientException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new DuplicateResponse(
                Instant.now(), 409,
                "Possible duplicate patient. Check the matches, then resubmit with confirmDuplicate=true to save anyway.",
                ex.getMatches()));
    }
}