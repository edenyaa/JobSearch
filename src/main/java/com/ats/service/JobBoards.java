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
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Reads openings from public job boards. A board that cannot be reached simply yields nothing. */
@Component
public class JobBoards {

	public static final String GREENHOUSE = "greenhouse";
	public static final String LEVER = "lever";
	public static final String ASHBY = "ashby";
	/** Amazon's own job site; the slug is the three-letter country code to list, such as ISR. */
	public static final String AMAZON = "amazon";
	/** Any career page that publishes its openings as schema.org JobPosting data; the slug is the page address. */
	public static final String PAGE = "page";

	private static final Pattern JSON_LD = Pattern.compile(
			"<script[^>]*type=[\"']application/ld\\+json[\"'][^>]*>(.*?)</script>", Pattern.DOTALL | Pattern.CASE_INSENSITIVE);

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
			for (String kind : List.of(GREENHOUSE, LEVER, ASHBY)) {
				if (exists(new Board(kind, slug))) {
					return Optional.of(new Board(kind, slug));
				}
			}
		}
		return Optional.empty();
	}

	public boolean exists(Board board) {
		return switch (board.kind()) {
			case GREENHOUSE -> get("https://boards-api.greenhouse.io/v1/boards/" + board.slug() + "/jobs").isPresent();
			case ASHBY -> get("https://api.ashbyhq.com/posting-api/job-board/" + board.slug()).isPresent();
			case AMAZON -> amazonPage(board.slug(), 0).isPresent();
			case PAGE -> getText(board.slug()).isPresent();
			default -> get("https://api.lever.co/v0/postings/" + board.slug() + "?mode=json").isPresent();
		};
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
		} else if (ASHBY.equals(board.kind())) {
			Optional<JsonNode> root = get("https://api.ashbyhq.com/posting-api/job-board/" + board.slug());
			for (JsonNode j : root.map(n -> n.path("jobs")).orElse(mapper.createArrayNode())) {
				if (!j.path("isListed").asBoolean(true)) {
					continue;
				}
				String place = j.path("isRemote").asBoolean(false) ? j.path("location").asString("") + " Remote"
						: j.path("location").asString("");
				found.add(new Posting(ASHBY, j.path("id").asString(""), j.path("title").asString(""), company, place,
						j.path("jobUrl").asString(""), j.path("descriptionHtml").asString("")));
			}
		} else if (AMAZON.equals(board.kind())) {
			// 100 per page, three pages at most: enough for a country's openings
			for (int offset = 0; offset < 300; offset += 100) {
				Optional<JsonNode> page = amazonPage(board.slug(), offset);
				JsonNode jobs = page.map(n -> n.path("jobs")).orElse(mapper.createArrayNode());
				for (JsonNode j : jobs) {
					String country = j.path("country_code").asString("");
					String details = j.path("description").asString("") + "<br/><br/>Basic qualifications:<br/>"
							+ j.path("basic_qualifications").asString("") + "<br/><br/>Preferred qualifications:<br/>"
							+ j.path("preferred_qualifications").asString("");
					found.add(new Posting(AMAZON, j.path("id").asString(""), j.path("title").asString(""), company,
							j.path("city").asString("") + ", " + ("ISR".equals(country) ? "Israel" : country),
							"https://www.amazon.jobs" + j.path("job_path").asString(""), details));
				}
				if (jobs.size() < 100) {
					break;
				}
			}
		} else if (PAGE.equals(board.kind())) {
			found.addAll(parseJobPostings(getText(board.slug()).orElse(""), board.slug(), company));
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

	private Optional<JsonNode> amazonPage(String countryCode, int offset) {
		return get("https://www.amazon.jobs/en/search.json?base_query=&result_limit=100&offset=" + offset
				+ "&normalized_country_code%5B%5D=" + URLEncoder.encode(countryCode, StandardCharsets.UTF_8));
	}

	/** What a source returned, and why it returned nothing when something went wrong. */
	public record Fetched(List<Posting> postings, String error) {
	}

	/** Openings from JSearch, which gathers listings of LinkedIn, Glassdoor, Indeed and others. Needs an API key. */
	public Fetched jsearch(String apiKey, String query, String countryCode) {
		String key = apiKey.strip();
		// keys of OpenWeb Ninja (the makers of JSearch) start with "ak_" and use their own address; the others are RapidAPI keys
		boolean direct = key.startsWith("ak_");
		String url = (direct ? "https://api.openwebninja.com/jsearch/search" : "https://jsearch.p.rapidapi.com/search")
				+ "?num_pages=1&page=1&query=" + URLEncoder.encode(query, StandardCharsets.UTF_8)
				+ (countryCode.isBlank() ? "" : "&country=" + countryCode);
		HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(40));
		HttpRequest request = (direct ? builder.header("X-API-Key", key)
				: builder.header("X-RapidAPI-Key", key).header("X-RapidAPI-Host", "jsearch.p.rapidapi.com")).GET().build();
		List<Posting> found = new ArrayList<>();
		try {
			HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
			if (response.statusCode() != 200) {
				return new Fetched(found, "JSearch answered HTTP " + response.statusCode() + switch (response.statusCode()) {
					case 401, 403 -> " (the key was refused: check it, and that you subscribed to the JSearch plan)";
					case 429 -> " (the monthly or hourly quota is used up)";
					default -> "";
				} + bodyHint(response.body()));
			}
			for (JsonNode j : mapper.readTree(response.body()).path("data")) {
				String place = jsearchPlace(j.path("job_city").asString(""), j.path("job_state").asString(""),
						j.path("job_location").asString(""), j.path("job_country").asString(""),
						j.path("job_is_remote").asBoolean(false));
				found.add(new Posting("jsearch", j.path("job_id").asString(""), j.path("job_title").asString(""),
						j.path("employer_name").asString(""), place, j.path("job_apply_link").asString(""),
						j.path("job_description").asString("")));
			}
			return new Fetched(found, null);
		} catch (IOException | JacksonException e) {
			return new Fetched(found, "JSearch could not be reached or read (" + e.getClass().getSimpleName() + ")");
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			return new Fetched(found, "JSearch was interrupted");
		}
	}

	/** The start of an error reply, so the run summary says what JSearch complained about. */
	private static String bodyHint(String body) {
		if (body == null || body.isBlank()) {
			return "";
		}
		String text = body.strip().replaceAll("\\s+", " ");
		return ": " + (text.length() > 200 ? text.substring(0, 200) + "..." : text);
	}

	/**
	 * The place text of a JSearch listing. The city is often empty and then the state or the free-text location
	 * ("Tel Aviv-Yafo, Israel") is the only clue, so all of them are kept for the place filter.
	 */
	public static String jsearchPlace(String city, String state, String location, String country, boolean remote) {
		List<String> parts = new ArrayList<>();
		for (String part : new String[] { city, state, location }) {
			if (part != null && !part.isBlank() && parts.stream()
					.noneMatch(p -> p.toLowerCase(Locale.ROOT).contains(part.strip().toLowerCase(Locale.ROOT)))) {
				parts.add(part.strip());
			}
		}
		String countryText = countryName(country == null ? "" : country);
		if (!countryText.isBlank()) {
			parts.add(countryText);
		}
		return String.join(", ", parts) + (remote ? " Remote" : "");
	}

	/** "IL" becomes "Israel", so that a place filter written in words recognises it. */
	public static String countryName(String code) {
		return code.length() == 2 ? Locale.of("", code).getDisplayCountry(Locale.ENGLISH) : code;
	}

	/** Reads the schema.org JobPosting data that many career pages embed as JSON-LD. */
	public List<Posting> parseJobPostings(String html, String pageUrl, String company) {
		List<Posting> found = new ArrayList<>();
		Matcher m = JSON_LD.matcher(html);
		while (m.find()) {
			try {
				collect(mapper.readTree(m.group(1).strip()), pageUrl, company, found);
			} catch (JacksonException e) {
				// a malformed block is skipped
			}
		}
		return found;
	}

	private void collect(JsonNode node, String pageUrl, String company, List<Posting> found) {
		if (node.isArray()) {
			node.forEach(n -> collect(n, pageUrl, company, found));
			return;
		}
		if (!node.isObject()) {
			return;
		}
		JsonNode type = node.path("@type");
		boolean isJob = type.isArray() ? type.toString().contains("JobPosting") : "JobPosting".equals(type.asString(""));
		if (isJob) {
			String title = node.path("title").asString("");
			String link = node.path("url").asString(pageUrl);
			String id = node.path("identifier").isObject() ? node.path("identifier").path("value").asString("")
					: node.path("identifier").asString("");
			found.add(new Posting("page", id.isBlank() ? Integer.toHexString(link.hashCode()) : id, title,
					company.isBlank() ? node.path("hiringOrganization").path("name").asString("") : company,
					placeOf(node), link.isBlank() ? pageUrl : link, node.path("description").asString("")));
			return;
		}
		collect(node.path("@graph"), pageUrl, company, found);
		collect(node.path("itemListElement"), pageUrl, company, found);
		collect(node.path("item"), pageUrl, company, found);
	}

	private static String placeOf(JsonNode job) {
		StringBuilder place = new StringBuilder();
		JsonNode locations = job.path("jobLocation");
		for (JsonNode location : locations.isArray() ? locations : List.of(locations)) {
			JsonNode address = location.path("address");
			JsonNode country = address.path("addressCountry");
			place.append(address.path("addressLocality").asString("")).append(' ')
					.append(country.isObject() ? country.path("name").asString("") : country.asString("")).append(' ');
		}
		if ("TELECOMMUTE".equals(job.path("jobLocationType").asString(""))) {
			place.append("Remote");
		}
		return place.toString().strip();
	}

	private Optional<String> getText(String url) {
		HttpRequest request = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(30))
				.header("User-Agent", "Mozilla/5.0 (compatible; auto-ats-job-search)").GET().build();
		try {
			HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
			return response.statusCode() == 200 ? Optional.of(response.body()) : Optional.empty();
		} catch (IOException | IllegalArgumentException e) {
			return Optional.empty();
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			return Optional.empty();
		}
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
