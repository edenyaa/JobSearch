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
        Properties properties = load("application.properties");

        assertNull(properties.getProperty("spring.datasource.url"));
    }

    @Test
    public void GivenPostgresDriver_WhenLoadingClass_ThenItIsOnTheClasspath() throws Exception {
        assertNotNull(Class.forName("org.postgresql.Driver"));
    }

}
