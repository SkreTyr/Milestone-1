package com.petshelter.service;

import com.petshelter.model.AnimalStatus;
import com.petshelter.model.Species;

import java.time.LocalDate;

/**
 * One parsed line of the historical intake/adoption log.
 */
public record CsvRow(
        String name,
        Species species,
        String breed,
        int ageMonths,
        LocalDate intakeDate,
        AnimalStatus status,
        LocalDate adoptionDate,
        String adopterName,
        String adopterEmail) {

    public boolean hasAdoption() {
        return adoptionDate != null && adopterEmail != null && !adopterEmail.isBlank();
    }
}
