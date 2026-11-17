package backend.patient;

import backend.patient.PatientDtos.DuplicateMatch;

import java.util.List;

public class DuplicatePatientException extends RuntimeException {
    private final List<DuplicateMatch> matches;

    public DuplicatePatientException(List<DuplicateMatch> matches) {
        super("Possible duplicate patient");
        this.matches = matches;
    }

    public List<DuplicateMatch> getMatches() {
        return matches;
    }
}