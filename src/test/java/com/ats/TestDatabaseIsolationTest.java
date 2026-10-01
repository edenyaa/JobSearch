package com.ats;

import static org.junit.jupiter.api.Assertions.*;

import java.sql.Connection;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;

@SpringBootTest
class TestDatabaseIsolationTest {

    @Autowired
    DataSource dataSource;

    @Autowired
    Environment environment;

    @Test
    public void GivenTestRun_WhenOpeningAConnection_ThenTheDatabaseIsTheInMemoryOneAndNotSupabase() throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            String url = connection.getMetaData().getURL();

            assertTrue(url.startsWith("jdbc:h2:mem:"), url);
            assertFalse(url.contains("supabase"));
        }
    }

    @Test
    public void GivenTestRun_WhenReadingActiveProfiles_ThenSupabaseProfileIsNotActive() {
        assertFalse(java.util.Arrays.asList(environment.getActiveProfiles()).contains("supabase"));
    }

}
