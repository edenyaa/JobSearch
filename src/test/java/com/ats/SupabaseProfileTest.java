package com.ats;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.PropertiesLoaderUtils;

class SupabaseProfileTest {

    private Properties load(String name) throws Exception {
        return PropertiesLoaderUtils.loadProperties(new ClassPathResource(name));
    }

    @Test
    public void GivenSupabaseProfile_WhenReading_ThenItPointsAtPostgresOverSsl() throws Exception {
        Properties properties = load("application-supabase.properties");

        assertTrue(properties.getProperty("spring.datasource.url").startsWith("jdbc:postgresql://"));
        assertTrue(properties.getProperty("spring.datasource.url").contains("sslmode=require"));
        assertEquals("org.postgresql.Driver", properties.getProperty("spring.datasource.driver-class-name"));
    }

    @Test
    public void GivenSupabaseProfile_WhenReading_ThenPasswordComesFromAnEnvironmentVariable() throws Exception {
        Properties properties = load("application-supabase.properties");

        assertEquals("${DB_PASSWORD}", properties.getProperty("spring.datasource.password"));
    }

    @Test
    public void GivenDefaultConfiguration_WhenReading_ThenNoDatabaseUrlIsSetSoTestsStayOnTheInMemoryDatabase() throws Exception {
        Properties properties = new Properties();
        try (java.io.InputStream in = java.nio.file.Files.newInputStream(java.nio.file.Path.of("src/main/resources/application.properties"))) {
            properties.load(in);
        }

        assertNull(properties.getProperty("spring.datasource.url"));
    }

    @Test
    public void GivenPostgresDriver_WhenLoadingClass_ThenItIsOnTheClasspath() throws Exception {
        assertNotNull(Class.forName("org.postgresql.Driver"));
    }

    @Test
    public void GivenSupabaseProfile_WhenReading_ThenPoolStaysWellBelowThePoolerLimitOf15() throws Exception {
        Properties properties = load("application-supabase.properties");

        assertTrue(Integer.parseInt(properties.getProperty("spring.datasource.hikari.maximum-pool-size")) <= 10);
    }

    @Test
    public void GivenSupabaseProfile_WhenReading_ThenNoLiteralPasswordIsStored() throws Exception {
        Properties properties = load("application-supabase.properties");

        assertTrue(properties.getProperty("spring.datasource.password").startsWith("${"));
        assertNull(properties.getProperty("spring.datasource.url.h2"));
        assertFalse(properties.getProperty("spring.datasource.url").contains("h2"));
    }

    @Test
    public void GivenGitIgnore_WhenReading_ThenTheLocalSecretsFileIsIgnored() throws Exception {
        java.util.List<String> lines = java.nio.file.Files.readAllLines(java.nio.file.Path.of(".gitignore"));

        assertTrue(lines.stream().anyMatch(line -> line.trim().equals(".env")));
    }

}
