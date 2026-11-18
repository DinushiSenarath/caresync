package backend.prescription;

import backend.patient.Patient;
import backend.prescription.PrescriptionDtos.*;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class PrescriptionMapper {

    public PrescriptionResponse toResponse(Prescription p) {
        Patient patient = p.getAppointment().getPatient();
        String allergies = patient.getAllergies() == null ? "" : patient.getAllergies().toLowerCase();

        List<ItemResponse> items = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        for (PrescriptionItem i : p.getItems()) {
            items.add(new ItemResponse(i.getId(), i.getMedicine().getId(), i.getMedicine().getName(),
                    i.getMedicine().getStrength(), i.getMedicine().getForm(), i.getMedicine().getUnit(),
                    i.getDose(), i.getRoute(), i.getFrequency(), i.getDuration(), i.getInstructions(),
                    i.getQtyPrescribed(), i.getQtyDispensed(), i.getQtyPrescribed() - i.getQtyDispensed()));
            String name = i.getMedicine().getName().toLowerCase();
            if (!allergies.isBlank() && allergies.contains(name)) {
                warnings.add("The patient's allergy record mentions " + i.getMedicine().getName()
                        + ". Please check before issuing.");
            }
        }
        return new PrescriptionResponse(p.getId(), p.getAppointment().getId(), patient.getId(),
                patient.getPatientNo(), patient.getFullName(), p.getDoctor().getUser().getFullName(),
                p.getStatus(), p.getNotes(), p.getCreatedAt(), p.getIssuedAt(), items, warnings);
    }
}