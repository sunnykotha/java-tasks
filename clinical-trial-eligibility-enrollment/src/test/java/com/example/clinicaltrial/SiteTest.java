package com.example.clinicaltrial;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SiteTest {

    private Trial validTrial() {
        return new Trial(1L, "Diabetes Study", 100, 25);
    }

    @Test
    void createsSiteAssociatedWithTrial() {
        Trial trial = validTrial();

        Site site = new Site(1L, "Hyderabad Site", trial);

        assertEquals(1L, site.id());
        assertEquals("Hyderabad Site", site.name());
        assertEquals(trial, site.trial());
    }

    @Test
    void rejectsNullSiteId() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new Site(null, "Hyderabad Site", validTrial())
        );
    }

    @Test
    void rejectsNonPositiveSiteId() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new Site(0L, "Hyderabad Site", validTrial())
        );
    }

    @Test
    void rejectsBlankSiteName() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new Site(1L, " ", validTrial())
        );
    }

    @Test
    void rejectsNullTrial() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new Site(1L, "Hyderabad Site", null)
        );
    }
}