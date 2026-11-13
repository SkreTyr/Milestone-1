package pawtrack.controller;

import pawtrack.dao.AnimalDao;
import pawtrack.model.Animal;

import javax.swing.SwingWorker;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class AnimalController {
    private static final Logger LOGGER = Logger.getLogger(AnimalController.class.getName());

    private final AnimalDao animalDao;
    private List<Animal> animals = new ArrayList<>();
    private Animal selected;
    private String status = "Ready";
    private Consumer<String> statusListener;
    private Runnable onChange;

    public AnimalController(AnimalDao animalDao) {
        this.animalDao = animalDao;
    }

    public void setStatusListener(Consumer<String> statusListener) {
        this.statusListener = statusListener;
    }

    public void setOnChange(Runnable onChange) {
        this.onChange = onChange;
    }

    public void refresh() {
        try {
            animals = animalDao.findAll();
            notifyChange();
            setStatus("Loaded " + animals.size() + " animal(s)");
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Failed to load animals", e);
            setStatus("Error loading animals: " + e.getMessage());
        }
    }

    public void refreshAsync() {
        new SwingWorker<List<Animal>, Void>() {
            @Override
            protected List<Animal> doInBackground() throws Exception {
                return animalDao.findAll();
            }

            @Override
            protected void done() {
                try {
                    animals = get();
                    notifyChange();
                    setStatus("Loaded " + animals.size() + " animal(s)");
                } catch (Exception e) {
                    LOGGER.log(Level.SEVERE, "Failed to load animals", e);
                    setStatus("Error loading animals: " + e.getMessage());
                }
            }
        }.execute();
    }

    public List<Animal> getAnimals() {
        return Collections.unmodifiableList(animals);
    }

    public void setAnimals(List<Animal> animals) {
        this.animals = new ArrayList<>(animals);
        notifyChange();
    }

    public Animal getSelected() {
        return selected;
    }

    public void setSelected(Animal selected) {
        this.selected = selected;
    }

    public String getStatus() {
        return status;
    }

    private void setStatus(String status) {
        this.status = status;
        if (statusListener != null) {
            statusListener.accept(status);
        }
    }

    private void notifyChange() {
        if (onChange != null) {
            onChange.run();
        }
    }
}
