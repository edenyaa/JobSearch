package com.ats.controllers;

import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public liveness check. An uptime monitor calling it every few minutes keeps the free-tier
 * host awake, and the query keeps the free-tier database counted as active.
 */
@RestController
@RequestMapping("/api/health")
public class HealthController {

	private final JdbcTemplate jdbc;

	public HealthController(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	@GetMapping
	public Map<String, String> health() {
		jdbc.queryForObject("SELECT 1", Integer.class);
		return Map.of("status", "ok");
	}

}
