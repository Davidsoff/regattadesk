package com.regattadesk.export;

import org.junit.jupiter.api.Test;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ExportJobStatusTest {

    @Test
    void fromValue_usesLocaleIndependentUppercase() {
        Locale previous = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            assertEquals(ExportJobStatus.PROCESSING, ExportJobStatus.fromValue("processing"));
        } finally {
            Locale.setDefault(previous);
        }
    }
}
