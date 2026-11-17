package com.example.clinicaltrial;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class JavaEnvironmentTest {

    @Test
    void projectRunsOnJava21() {
        assertEquals(21, Runtime.version().feature());
    }
}