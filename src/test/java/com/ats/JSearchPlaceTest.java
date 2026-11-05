package com.ats;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.ats.service.JobBoards;
import org.junit.jupiter.api.Test;

class JSearchPlaceTest {

    @Test
    public void GivenCityMissingButLocationGiven_WhenBuildingPlace_ThenLocationIsKept() {
        assertEquals("Tel Aviv-Yafo, Israel", JobBoards.jsearchPlace("", "", "Tel Aviv-Yafo", "IL", false));
    }

    @Test
    public void GivenCityAndLocationRepeatingIt_WhenBuildingPlace_ThenNotDuplicated() {
        assertEquals("Herzliya, Israel", JobBoards.jsearchPlace("Herzliya", "", "Herzliya", "IL", false));
    }

    @Test
    public void GivenStateAndRemote_WhenBuildingPlace_ThenAllPartsAreKept() {
        assertEquals("Haifa, Haifa District, Israel Remote",
                JobBoards.jsearchPlace("Haifa", "Haifa District", "", "IL", true));
    }

    @Test
    public void GivenNothingKnown_WhenBuildingPlace_ThenItIsEmpty() {
        assertEquals("", JobBoards.jsearchPlace("", "", "", "", false));
    }
}
