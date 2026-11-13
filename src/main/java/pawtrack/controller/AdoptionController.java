package pawtrack.controller;

import pawtrack.dao.AdoptionRecordDao;
import pawtrack.dao.AnimalDao;
import pawtrack.model.Adopter;
import pawtrack.model.AdoptionRecord;
import pawtrack.model.Animal;
import pawtrack.model.AnimalStatus;
import pawtrack.service.StatusHistoryService;

import java.sql.SQLException;
import java.time.LocalDate;

public final class AdoptionController {

    private final AnimalDao animalDao;
    private final AdoptionRecordDao recordDao;
    private final StatusHistoryService history;
    private final AuthController auth;

    public AdoptionController(AnimalDao animalDao, AdoptionRecordDao recordDao,
                              StatusHistoryService history, AuthController auth) {
        this.animalDao = animalDao;
        this.recordDao = recordDao;
        this.history = history;
        this.auth = auth;
    }

    public void markAdopted(Animal animal, Adopter adopter) throws SQLException {
        auth.requireStaff();
        AdoptionRecord record = new AdoptionRecord(animal.getId(), adopter.getId(), LocalDate.now(),
                animal.adoptionFee());
        recordDao.insert(record);
        AnimalStatus previous = animal.getStatus();
        animal.setStatus(AnimalStatus.ADOPTED);
        animalDao.updateStatus(animal.getId(), AnimalStatus.ADOPTED);
        history.record(animal.getId(), previous, AnimalStatus.ADOPTED);
    }
}
