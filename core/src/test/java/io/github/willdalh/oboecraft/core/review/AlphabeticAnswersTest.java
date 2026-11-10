package io.github.willdalh.oboecraft.core.review;

import static org.junit.jupiter.api.Assertions.*;

import io.github.willdalh.oboecraft.core.item.*;
import java.util.List;
import org.junit.jupiter.api.Test;

class AlphabeticAnswersTest {
    private static boolean matches(String name, String typed) {
        return AnswerChecker.isCorrect(new NameListEntry(new ItemId("test"), List.of("test"), new PlainName(name)), typed);
    }

    @Test void caseSpacesHyphensAndPunctuationAreIgnored() {
        assertTrue(matches("Shulker-Kiste", " SHULKER kiste!"));
        assertTrue(matches("Eikenhouten stam", "eikenhoutenstam"));
        assertTrue(matches("Blå is", "blå-is"));
    }

    @Test void keyboardStandInsPreserveTheSound() {
        for (String[] example : new String[][] {
                {"Grünes Bett", "Gruenes Bett"}, {"Äpfel", "Aepfel"}, {"Öl", "Oel"}, {"Straße", "STRASSE"},
                {"Bjørketre", "Bjoerketre"}, {"Lær", "Laer"}, {"Blå is", "Blaa is"}}) {
            assertTrue(matches(example[0], example[1]), example[1]);
        }
        assertTrue(matches("Grünes Bett", "Gru\u0308nes Bett"), "decomposed Unicode");
    }

    @Test void onlyTheListedAccentsCanBeLeftOut() {
        for (String name : List.of("idée", "idèe", "idêe", "idëe")) assertTrue(matches(name, "idee"));
        assertTrue(matches("geïnfuseerd", "geinfuseerd"));
        assertFalse(matches("Grünes Bett", "Grunes Bett"));
        assertFalse(matches("Äpfel", "Apfel"));
        assertFalse(matches("Öl", "Ol"));
        assertFalse(matches("Bjørketre", "Bjorketre"));
        assertFalse(matches("Blå is", "Bla is"));
        assertFalse(matches("Lær", "Lar"));
        assertFalse(matches("é", "a"));
        assertFalse(matches("á", "a"));
    }

    @Test void thereIsNoTypoToleranceOrJapaneseSoundMatching() {
        for (String typed : List.of("Eichenstam", "Eichenstammn", "Eichenstamn", "der Eichenstamm", "おーく")) {
            assertFalse(matches("Eichenstamm", typed), typed);
        }
        assertFalse(matches("no", ""));
        assertFalse(matches("Bett", "Bet$ t"));
    }
}
