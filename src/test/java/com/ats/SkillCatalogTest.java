package com.ats;

import static org.junit.jupiter.api.Assertions.*;

import com.ats.domain.SkillCatalog;
import com.ats.domain.SkillCatalog.Detected;
import com.ats.domain.SkillCatalog.Entry;
import com.ats.domain.SkillCategory;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.junit.jupiter.api.Test;

class SkillCatalogTest {

    private List<String> namesIn(String text) {
        return SkillCatalog.detect(text).stream().map(Detected::name).toList();
    }

    @Test
    public void GivenJavaText_WhenDetecting_ThenJavaIsFoundButNotJavaScript() {
        assertTrue(namesIn("Senior Java developer").contains("Java"));
        assertFalse(namesIn("Senior Java developer").contains("JavaScript"));
    }

    @Test
    public void GivenJavaScriptText_WhenDetecting_ThenJavaScriptIsFoundButNotJava() {
        assertTrue(namesIn("Strong JavaScript skills").contains("JavaScript"));
        assertFalse(namesIn("Strong JavaScript skills").contains("Java"));
    }

    @Test
    public void GivenAliases_WhenDetecting_ThenCanonicalNamesAreReturnedWithTheFormsFound() {
        List<Detected> detected = SkillCatalog.detect("Used Postgres and K8s daily");

        Detected postgres = detected.stream().filter(d -> d.name().equals("PostgreSQL")).findFirst().orElseThrow();
        Detected kubernetes = detected.stream().filter(d -> d.name().equals("Kubernetes")).findFirst().orElseThrow();
        assertEquals(List.of("Postgres"), postgres.foundForms());
        assertEquals(List.of("K8s"), kubernetes.foundForms());
    }

    @Test
    public void GivenNameAndAliasTogether_WhenDetecting_ThenBothFormsAreReported() {
        Detected postgres = SkillCatalog.detect("PostgreSQL (Postgres)").stream()
                .filter(d -> d.name().equals("PostgreSQL")).findFirst().orElseThrow();

        assertEquals(List.of("PostgreSQL", "Postgres"), postgres.foundForms());
    }

    @Test
    public void GivenSpecialCharacterSkills_WhenDetecting_ThenTheyAreFoundLiterally() {
        List<String> names = namesIn("Experience with C++, C# and .NET plus CI/CD pipelines");

        assertTrue(names.containsAll(List.of("C++", "C#", ".NET", "CI/CD")));
    }

    @Test
    public void GivenCaseInsensitiveSkill_WhenTextUsesOtherCase_ThenItIsStillDetected() {
        assertTrue(namesIn("DOCKER and kubernetes").containsAll(List.of("Docker", "Kubernetes")));
    }

    @Test
    public void GivenCaseSensitiveSkill_WhenTextUsesLowercase_ThenItIsNotDetected() {
        assertTrue(namesIn("Built services with Spring and Kafka").contains("Spring"));
        assertFalse(namesIn("We hire in spring and summer").contains("Spring"));
    }

    @Test
    public void GivenSpringBoot_WhenDetecting_ThenOnlyTheLongerNameIsReported() {
        List<String> names = namesIn("Built services with Spring Boot");

        assertTrue(names.contains("Spring Boot"));
        assertFalse(names.contains("Spring"));
    }

    @Test
    public void GivenJavaAndJavaScriptTogether_WhenDetecting_ThenBothAreReported() {
        List<String> names = namesIn("Java on the backend and JavaScript in the browser");

        assertTrue(names.containsAll(List.of("Java", "JavaScript")));
    }

    @Test
    public void GivenSoftSkills_WhenDetecting_ThenTheyAreFound() {
        List<String> names = namesIn("Strong communication, leadership and problem-solving");

        assertTrue(names.containsAll(List.of("Communication", "Leadership", "Problem Solving")));
    }

    @Test
    public void GivenSkills_WhenDetecting_ThenTheirCategoriesAreCorrect() {
        List<Detected> detected = SkillCatalog.detect("Java, PostgreSQL, Docker, Agile, Leadership");

        assertEquals(SkillCategory.LANGUAGES, categoryOf(detected, "Java"));
        assertEquals(SkillCategory.DATABASES, categoryOf(detected, "PostgreSQL"));
        assertEquals(SkillCategory.CLOUD_DEVOPS, categoryOf(detected, "Docker"));
        assertEquals(SkillCategory.PRACTICES, categoryOf(detected, "Agile"));
        assertEquals(SkillCategory.SOFT_SKILLS, categoryOf(detected, "Leadership"));
    }

    private SkillCategory categoryOf(List<Detected> detected, String name) {
        return detected.stream().filter(d -> d.name().equals(name)).findFirst().orElseThrow().category();
    }

    @Test
    public void GivenNullBlankOrIrrelevantText_WhenDetecting_ThenNothingIsFound() {
        assertTrue(SkillCatalog.detect(null).isEmpty());
        assertTrue(SkillCatalog.detect("   ").isEmpty());
        assertTrue(SkillCatalog.detect("We sell fresh bread and cakes").isEmpty());
    }

    @Test
    public void GivenAliasOrName_WhenFinding_ThenTheCatalogEntryIsReturned() {
        assertEquals("PostgreSQL", SkillCatalog.find("postgres").orElseThrow().name());
        assertEquals("Kubernetes", SkillCatalog.find("  K8S ").orElseThrow().name());
        assertEquals("Docker", SkillCatalog.find("Docker").orElseThrow().name());
    }

    @Test
    public void GivenUnknownOrBlankTerm_WhenFinding_ThenNothingIsReturned() {
        assertTrue(SkillCatalog.find("Salesforce").isEmpty());
        assertTrue(SkillCatalog.find(" ").isEmpty());
        assertTrue(SkillCatalog.find(null).isEmpty());
    }

    @Test
    public void GivenCatalog_WhenInspecting_ThenItIsBigEnoughAndWellFormed() {
        List<Entry> entries = SkillCatalog.entries();

        assertTrue(entries.size() >= 150);
        for (Entry entry : entries) {
            assertFalse(entry.name().isBlank());
            assertNotEquals(SkillCategory.OTHER, entry.category());
            assertEquals(entry.name(), entry.forms().get(0));
        }
    }

    @Test
    public void GivenCatalog_WhenInspecting_ThenNamesAndFormsAreUnique() {
        Set<String> names = new HashSet<>();
        Set<String> forms = new HashSet<>();

        for (Entry entry : SkillCatalog.entries()) {
            assertTrue(names.add(entry.name().toLowerCase(Locale.ROOT)), "duplicate name " + entry.name());
            for (String form : entry.forms()) {
                assertTrue(forms.add(form.toLowerCase(Locale.ROOT)), "duplicate form " + form);
            }
        }
    }

    @Test
    public void GivenEveryCategoryExceptOther_WhenInspecting_ThenCatalogHasEntriesForIt() {
        Set<SkillCategory> used = new HashSet<>();
        SkillCatalog.entries().forEach(entry -> used.add(entry.category()));

        for (SkillCategory category : SkillCategory.values()) {
            if (category != SkillCategory.OTHER) {
                assertTrue(used.contains(category), "no entries for " + category);
            }
        }
    }

}
