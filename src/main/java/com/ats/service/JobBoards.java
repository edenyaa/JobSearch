package com.ats.service;

import com.ats.domain.Posting;
import com.ats.domain.PostingFilter;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Reads openings from public job boards. A board that cannot be reached simply yields nothing. */
@Component
public class JobBoards {

	public static final String GREENHOUSE = "greenhouse";
	public static final String LEVER = "lever";

	public record Board(String kind, String slug) {
	}

	private final JsonMapper mapper;
	private final HttpClient http = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build();

	public JobBoards(JsonMapper mapper) {
		this.mapper = mapper;
	}

	public List<Posting> remotive(String term) {
		String url = "https://remotive.com/api/remote-jobs?limit=50&search="
				+ URLEncoder.encode(term, StandardCharsets.UTF_8);
		List<Posting> found = new ArrayList<>();
		for (JsonNode j : get(url).map(n -> n.path("jobs")).orElse(mapper.createArrayNode())) {
			found.add(new Posting("remotive", j.path("id").asString(""), j.path("title").asString(""),
					j.path("company_name").asString(""), j.path("candidate_required_location").asString("Remote"),
					j.path("url").asString(""), j.path("description").asString("")));
		}
		return found;
	}

	public List<Posting> arbeitnow() {
		List<Posting> found = new ArrayList<>();
		for (JsonNode j : get("https://www.arbeitnow.com/api/job-board-api").map(n -> n.path("data"))
				.orElse(mapper.createArrayNode())) {
			found.add(new Posting("arbeitnow", j.path("slug").asString(""), j.path("title").asString(""),
					j.path("company_name").asString(""),
					j.path("remote").asBoolean(false) ? "Remote" : j.path("location").asString(""),
					j.path("url").asString(""), j.path("description").asString("")));
		}
		return found;
	}

	/** Finds the company's career board on Greenhouse or Lever, trying each likely name. */
	public Optional<Board> findBoard(String company) {
		for (String slug : PostingFilter.slugGuesses(company)) {
			for (String kind : List.of(GREENHOUSE, LEVER)) {
				if (exists(new Board(kind, slug))) {
					return Optional.of(new Board(kind, slug));
				}
			}
		}
		return Optional.empty();
	}

	public boolean exists(Board board) {
		return GREENHOUSE.equals(board.kind())
				? get("https://boards-api.greenhouse.io/v1/boards/" + board.slug() + "/jobs").isPresent()
				: get("https://api.lever.co/v0/postings/" + board.slug() + "?mode=json").isPresent();
	}

	public List<Posting> openings(Board board, String company) {
		List<Posting> found = new ArrayList<>();
		if (GREENHOUSE.equals(board.kind())) {
			Optional<JsonNode> root = get(
					"https://boards-api.greenhouse.io/v1/boards/" + board.slug() + "/jobs?content=true");
			for (JsonNode j : root.map(n -> n.path("jobs")).orElse(mapper.createArrayNode())) {
				found.add(new Posting(GREENHOUSE, j.path("id").asString(""), j.path("title").asString(""), company,
						j.path("location").path("name").asString(""), j.path("absolute_url").asString(""),
						j.path("content").asString("")));
			}
		} else {
			Optional<JsonNode> root = get("https://api.lever.co/v0/postings/" + board.slug() + "?mode=json");
			for (JsonNode j : root.orElse(mapper.createArrayNode())) {
				String description = j.path("descriptionPlain").asString("");
				found.add(new Posting(LEVER, j.path("id").asString(""), j.path("text").asString(""), company,
						j.path("categories").path("location").asString(""), j.path("hostedUrl").asString(""),
						description.isBlank() ? j.path("description").asString("") : description));
			}
		}
		return found;
	}

	private Optional<JsonNode> get(String url) {
		HttpRequest request = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(30))
				.header("User-Agent", "auto-ats-job-search").header("Accept", "application/json").GET().build();
		try {
			HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
			return response.statusCode() == 200 ? Optional.of(mapper.readTree(response.body())) : Optional.empty();
		} catch (IOException | JacksonException e) {
			return Optional.empty();
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			return Optional.empty();
		}
	}

}
