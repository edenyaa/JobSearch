package com.ats.service;

import com.ats.domain.FitScore;
import com.ats.domain.KeywordMatcher;
import com.ats.domain.Job;
import com.ats.domain.JobStatus;
import com.ats.domain.Posting;
import com.ats.domain.PostingFilter;
import com.ats.domain.SearchSettings;
import com.ats.domain.WatchedCompany;
import com.ats.dto.CompanyDtos.CompanyRequest;
import com.ats.dto.JobDtos.JobRequest;
import com.ats.dto.SearchDtos.SettingsRequest;
import com.ats.repository.SearchSettingsRepository;
import com.ats.repository.WatchedCompanyRepository;
import com.ats.service.FitJudge.Fit;
import com.ats.service.JobBoards.Board;
import jakarta.annotation.PreDestroy;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Optional;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Looks for openings on job boards and saves the relevant ones as SAVED jobs, together with their companies.
 * A search runs in the background when the user asks for it and, if the user turned it on, every day. It never
 * applies to anything: the user applies and moves the job to Applied.
 */
@Service
public class JobSearchService {

	private static final Logger log = LoggerFactory.getLogger(JobSearchService.class);

	static final int MAX_NEW_JOBS = 30;
	static final int MAX_COMPANIES_TO_PROBE = 30;
	static final int MAX_IMPORT = 400;
	/** Openings the AI rates in one search, best candidates first; the rest are ranked by their skill match. */
	static final int MAX_AI_RATINGS = 12;
	private static final int MAX_DESCRIPTION = 9500;

	private static final Pattern GREENHOUSE_URL = Pattern
			.compile("greenhouse\\.io/(?:embed/job_board\\?for=)?([A-Za-z0-9_-]+)");
	private static final Pattern ASHBY_URL = Pattern.compile("jobs\\.ashbyhq\\.com/([A-Za-z0-9_.-]+)");
	private static final Duration AGGREGATOR_MIN_GAP = Duration.ofHours(6);
	/** The address column holds 2000 characters. */
	private static final int MAX_ADDRESS = 2000;
	private static final Pattern LEVER_URL = Pattern.compile("jobs\\.lever\\.co/([A-Za-z0-9_-]+)");

	/** Israeli companies whose Greenhouse career pages are searched until the user changes the list. */
	private static final List<String> STARTER_COMPANIES = List.of("similarweb", "riskified", "jfrog", "taboola", "nice",
			"payoneer", "lightricks", "melio", "fireblocks", "torq", "appsflyer");

	private final SearchSettingsRepository settingsRepo;
	private final WatchedCompanyRepository watchedRepo;
	private final JobBoards boards;
	private final JobService jobs;
	private final CompanyService companies;
	private final SkillService skills;
	private final UserService users;
	private final FitJudge judge;
	private final String jsearchKey;
	private final int jsearchQueries;
	private volatile Instant lastAggregatorCall = Instant.EPOCH;
	private int aggregatorTurn;
	private final Set<Long> running = ConcurrentHashMap.newKeySet();
	private final Set<Long> importing = ConcurrentHashMap.newKeySet();
	private final Map<Long, String> importStatus = new ConcurrentHashMap<>();
	private final ExecutorService background = Executors.newSingleThreadExecutor(r -> {
		Thread t = new Thread(r, "job-search");
		t.setDaemon(true);
		return t;
	});

	public JobSearchService(SearchSettingsRepository settingsRepo, WatchedCompanyRepository watchedRepo,
			JobBoards boards, JobService jobs, CompanyService companies, SkillService skills, UserService users,
			FitJudge judge,
			@Value("${search.jsearch.api-key:}") String jsearchKey,
			@Value("${search.jsearch.max-queries:1}") int jsearchQueries) {
		this.judge = judge;
		this.jsearchKey = jsearchKey;
		this.jsearchQueries = jsearchQueries;
		this.settingsRepo = settingsRepo;
		this.watchedRepo = watchedRepo;
		this.boards = boards;
		this.jobs = jobs;
		this.companies = companies;
		this.skills = skills;
		this.users = users;
	}

	@PreDestroy
	void shutdown() {
		background.shutdownNow();
	}

	// ---- settings and watched companies --------------------------------------------------------

	public SearchSettings settings(Long userId) {
		return settingsRepo.findByUserId(userId).orElseGet(() -> createDefaults(userId));
	}

	private SearchSettings createDefaults(Long userId) {
		SearchSettings s = new SearchSettings();
		s.setUser(users.get(userId));
		applyRecommended(s);
		s = settingsRepo.save(s);
		for (String slug : STARTER_COMPANIES) {
			WatchedCompany w = new WatchedCompany();
			w.setUser(s.getUser());
			w.setName(slug.substring(0, 1).toUpperCase(Locale.ROOT) + slug.substring(1));
			w.setBoard(JobBoards.GREENHOUSE);
			w.setSlug(slug);
			watchedRepo.save(w);
		}
		return s;
	}

	/** QA, development, AI backend and prompting roles, junior, student or intern level, in Israel with Rehovot first. */
	private static void applyRecommended(SearchSettings s) {
		s.setTitles(new ArrayList<>(List.of("qa", "automation", "developer", "software engineer", "backend", "ai",
				"machine learning", "llm", "prompt", "junior", "student", "intern", "trainee", "graduate", "entry level",
				"full stack")));
		s.setExcludedTitles(new ArrayList<>(
				List.of("senior", "staff", "principal", "lead", "manager", "director", "head of", "vp", "architect")));
		s.setLocations(new ArrayList<>(List.of("israel", "rehovot", "rechovot", "tel aviv", "ramat gan", "herzliya",
				"petah tikva", "petach tikva", "holon", "rishon", "ness ziona", "nes ziona", "modiin", "jerusalem", "haifa",
				"netanya", "raanana", "ra'anana", "kfar saba", "hod hasharon", "yokneam", "beer sheva", "be'er sheva",
				"givatayim", "bnei brak", "lod", "ramla", "yavne", "ashdod")));
		s.setPriorityLocations(new ArrayList<>(List.of("rehovot", "rechovot")));
		s.setMinMatchPercent(20);
		s.setMinFitPercent(40);
	}

	/** Puts the recommended titles, places and thresholds back; searched companies and the daily switch stay. */
	public SearchSettings resetToRecommended(Long userId) {
		SearchSettings s = settings(userId);
		applyRecommended(s);
		return settingsRepo.save(s);
	}

	public SearchSettings update(Long userId, SettingsRequest req) {
		List<String> titles = clean(req.titles());
		if (titles.isEmpty()) {
			throw new ValidationException("Add at least one job title to search for.");
		}
		if (req.minMatchPercent() < 0 || req.minMatchPercent() > 100 || req.minFitPercent() < 0
				|| req.minFitPercent() > 100) {
			throw new ValidationException("The minimum percentages must be between 0 and 100.");
		}
		SearchSettings s = settings(userId);
		s.setTitles(titles);
		s.setExcludedTitles(clean(req.excludedTitles()));
		s.setLocations(clean(req.locations()));
		s.setPriorityLocations(clean(req.priorityLocations()));
		s.setMinMatchPercent(req.minMatchPercent());
		s.setMinFitPercent(req.minFitPercent());
		s.setDaily(req.daily());
		return settingsRepo.save(s);
	}

	private static List<String> clean(List<String> values) {
		if (values == null) {
			return new ArrayList<>();
		}
		return new ArrayList<>(values.stream().filter(v -> v != null && !v.isBlank()).map(String::strip)
				.collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new)));
	}

	public List<WatchedCompany> watched(Long userId) {
		settings(userId);
		return watchedRepo.findByUserIdOrderByNameAsc(userId);
	}

	/** A company added to the search, and how many openings were read from its page when it was added. */
	public record Watched(WatchedCompany company, int openings) {
	}

	/**
	 * Adds a company to the search. Give the link of its careers page, or only its name and the page is looked up.
	 * The name is optional when a link is given. Advertising parameters in a pasted link are dropped.
	 */
	public Watched watch(Long userId, String company, String name, String link) {
		String text = link != null && !link.isBlank() ? link.strip() : company == null ? "" : company.strip();
		if (text.isEmpty()) {
			throw new ValidationException("Enter the company name, or the link of its careers page.");
		}
		Board board;
		String derived;
		Matcher greenhouse = GREENHOUSE_URL.matcher(text);
		Matcher lever = LEVER_URL.matcher(text);
		Matcher ashby = ASHBY_URL.matcher(text);
		boolean isLink = text.matches("(?i)https?://\\S+");
		if (greenhouse.find()) {
			board = new Board(JobBoards.GREENHOUSE, greenhouse.group(1));
			derived = capitalize(board.slug());
		} else if (lever.find()) {
			board = new Board(JobBoards.LEVER, lever.group(1));
			derived = capitalize(board.slug());
		} else if (ashby.find()) {
			board = new Board(JobBoards.ASHBY, ashby.group(1));
			derived = capitalize(board.slug());
		} else if (isLink && hostOf(text).matches("(?i)(www\\.)?amazon\\.jobs")) {
			board = new Board(JobBoards.AMAZON, amazonCountry(text));
			derived = "Amazon";
		} else if (isLink) {
			String url = PostingFilter.cleanUrl(text);
			if (url.length() > MAX_ADDRESS) {
				throw new ValidationException("That link is too long even without its tracking parameters.");
			}
			board = new Board(JobBoards.PAGE, url);
			derived = capitalize(hostOf(url).replaceFirst("^(www|careers|jobs)\\.", "").split("\\.")[0]);
		} else {
			board = boards.findBoard(text).orElseThrow(() -> new ValidationException("No Greenhouse, Lever or Ashby "
					+ "career page found for " + text + ". Paste the link of its careers page as well."));
			derived = text;
		}
		String shown = name != null && !name.isBlank() ? name.strip() : derived;
		if (!boards.exists(board)) {
			throw new ValidationException("That careers page could not be opened.");
		}
		settings(userId);
		WatchedCompany w = addWatched(userId, shown, board);
		int openings = boards.openings(board, shown).size();
		addToCompanies(userId, shown);
		return new Watched(w, openings);
	}

	/** The board a saved company is read from. */
	static Board boardOf(WatchedCompany w) {
		boolean page = JobBoards.PAGE.equals(w.getBoard()) && w.getAddress() != null;
		return new Board(w.getBoard(), page ? w.getAddress() : w.getSlug());
	}

	private WatchedCompany addWatched(Long userId, String name, Board board) {
		boolean page = JobBoards.PAGE.equals(board.kind());
		String slug = page ? fingerprint(board.slug()) : board.slug();
		if (watchedRepo.existsByUserIdAndBoardAndSlug(userId, board.kind(), slug)) {
			throw new ConflictException(name + " is already in the search");
		}
		WatchedCompany w = new WatchedCompany();
		w.setUser(users.get(userId));
		w.setName(name);
		w.setBoard(board.kind());
		w.setSlug(slug);
		w.setAddress(page ? board.slug() : null);
		return watchedRepo.save(w);
	}

	private static String hostOf(String url) {
		try {
			String host = URI.create(url).getHost();
			return host == null ? "" : host;
		} catch (IllegalArgumentException e) {
			return "";
		}
	}

	/** Amazon's list is filtered by country: the link's country (IL or ISR), Israel when it names none. */
	private static String amazonCountry(String url) {
		Matcher m = Pattern.compile("(?:country|normalized_country_code)(?:%5B%5D|\\[\\])=([A-Za-z]{2,3})").matcher(url);
		if (!m.find()) {
			return "ISR";
		}
		String code = m.group(1).toUpperCase(Locale.ROOT);
		try {
			return code.length() == 2 ? Locale.of("", code).getISO3Country() : code;
		} catch (java.util.MissingResourceException e) {
			return "ISR";
		}
	}

	/** A short stable name for an address too long for the unique slug column. */
	private static String fingerprint(String address) {
		try {
			byte[] hash = java.security.MessageDigest.getInstance("SHA-256")
					.digest(address.getBytes(java.nio.charset.StandardCharsets.UTF_8));
			StringBuilder hex = new StringBuilder("p");
			for (int i = 0; i < 12; i++) {
				hex.append(String.format("%02x", hash[i]));
			}
			return hex.toString();
		} catch (java.security.NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}

	public void unwatch(Long userId, Long id) {
		watchedRepo.delete(watchedRepo.findByIdAndUserId(id, userId).orElseThrow(
				() -> new NotFoundException("Searched company", id)));
	}

	private static String capitalize(String slug) {
		return slug.isEmpty() ? slug : slug.substring(0, 1).toUpperCase(Locale.ROOT) + slug.substring(1);
	}

	// ---- importing many companies ---------------------------------------------------------------

	/** Well-known Israeli tech companies. Each is looked up, and the ones with no readable career page are skipped. */
	static final List<String> ISRAELI_COMPANIES = List.of("Wix", "monday.com", "Fiverr", "Check Point",
			"CyberArk", "NICE", "Amdocs", "Playtika", "Taboola", "Outbrain", "Similarweb", "Payoneer", "JFrog",
			"SentinelOne", "Cato Networks", "Snyk", "Wiz", "Armis", "Transmit Security", "Riskified", "Lemonade",
			"HiBob", "Papaya Global", "Rapyd", "Pagaya", "Melio", "Lightricks", "Fireblocks", "Torq", "AppsFlyer",
			"Gong", "Cloudinary", "Forter", "Sisense", "Yotpo", "Zesty", "Aqua Security", "Orca Security", "Axonius",
			"Cybereason", "Varonis", "Imperva", "Radware", "Kaltura", "Perion", "Nayax", "Tufin", "Cellebrite",
			"SolarEdge", "Mobileye", "Moon Active", "Plarium", "Lusha", "Pecan AI", "Hippo", "Fundbox", "Tipalti",
			"Nanox", "Verbit", "Placer.ai", "Redis", "Elementor", "ironSource", "Unity", "Vimeo", "Bringg", "Nuvei",
			"Global-e", "Digital Turbine", "Tabnine", "Run:ai", "Deci", "AI21 Labs", "Anyword", "Quantum Machines",
			"Classiq", "Bria", "Hailo", "NeuroBlade", "Augury", "Navan", "Cyera", "Island", "Dazz", "Jit",
			"Legit Security", "Zafran", "Upwind", "Salt Security", "Pentera", "Cymulate", "XM Cyber", "Checkmarx",
			"Sapiens", "Cognyte", "Verint", "Palo Alto Networks", "Kornit Digital", "Next Insurance", "Lendbuzz",
			"Bluevine", "Rivery", "Atera", "Gloat", "Honeybook", "Skai", "Optimove", "Zoominfo", "Walkme", "Lightrun",
			"Komodor", "Granulate", "Vayyar", "Innoviz", "Arbe Robotics", "Otonomo", "Nexar", "Cortica",
			"Tailor Brands", "Kryon", "Dynamic Yield", "Voyantis", "Bizzabo", "Deel", "Ultima Genomics", "Mend",
			"Cycode", "Wing Security", "Silverfort", "Hunters", "Aidoc", "Zebra Medical", "Itamar Medical", "Medigus",
			"Camtek", "Tower Semiconductor", "Ceva", "Valens", "Cyren");

	public boolean isImporting(Long userId) {
		return importing.contains(userId);
	}

	public String importStatus(Long userId) {
		return importStatus.get(userId);
	}

	/**
	 * Looks up the career page of every given company name (the built-in Israeli tech list when none is given) in
	 * the background and adds the ones found to the searched companies.
	 */
	public void startImport(Long userId, List<String> names) {
		settings(userId);
		List<String> wanted = clean(names).stream().limit(MAX_IMPORT).toList();
		List<String> list = wanted.isEmpty() ? ISRAELI_COMPANIES.stream().distinct().toList() : wanted;
		if (importing.add(userId)) {
			importStatus.put(userId, "Looking up " + list.size() + " companies...");
			background.execute(() -> runImport(userId, list));
		}
	}

	private void runImport(Long userId, List<String> names) {
		int added = 0;
		int without = 0;
		int known = 0;
		try {
			Set<String> watchedKeys = new HashSet<>();
			Set<String> boardKeys = new HashSet<>();
			for (WatchedCompany w : watchedRepo.findByUserIdOrderByNameAsc(userId)) {
				watchedKeys.add(PostingFilter.key(w.getName()));
				boardKeys.add(w.getBoard() + "/" + w.getSlug());
			}
			int done = 0;
			for (String name : names) {
				done++;
				importStatus.put(userId, "Looked up " + done + " of " + names.size() + " companies, added " + added
						+ " career pages so far...");
				if (watchedKeys.contains(PostingFilter.key(name))) {
					known++;
					continue;
				}
				Optional<Board> board = boards.findBoard(name);
				if (board.isEmpty()) {
					without++;
					continue;
				}
				if (!boardKeys.add(board.get().kind() + "/" + board.get().slug())) {
					known++;
					continue;
				}
				try {
					addWatched(userId, name, board.get());
					watchedKeys.add(PostingFilter.key(name));
					added++;
				} catch (ConflictException e) {
					known++;
				}
			}
			importStatus.put(userId, "Looked up " + names.size() + " companies: added " + added + " career pages"
					+ (known > 0 ? ", " + known + " were already in the list" : "") + ", " + without
					+ " have no Greenhouse, Lever or Ashby page that can be read.");
		} catch (RuntimeException e) {
			log.warn("Company import failed for user {}", userId, e);
			importStatus.put(userId, "The import stopped: " + e.getMessage() + " (" + added + " added so far).");
		} finally {
			importing.remove(userId);
		}
	}

	// ---- running -------------------------------------------------------------------------------

	public boolean isRunning(Long userId) {
		return running.contains(userId);
	}

	/** Starts a search in the background and returns at once; a search that is already running is left alone. */
	public void startRun(Long userId) {
		settings(userId);
		if (running.add(userId)) {
			background.execute(() -> finish(userId));
		}
	}

	/** The automatic run, every 3 hours, for every user who turned it on. Change it with search.auto-cron. */
	@Scheduled(cron = "${search.auto-cron:0 0 */3 * * *}")
	public void runAutomatic() {
		for (Long userId : settingsRepo.findDailyUserIds()) {
			if (running.add(userId)) {
				finish(userId);
			}
		}
	}

	private void finish(Long userId) {
		String summary;
		try {
			summary = search(userId);
		} catch (RuntimeException e) {
			log.warn("Job search failed for user {}", userId, e);
			summary = "The search failed: " + e.getMessage();
		} finally {
			running.remove(userId);
		}
		String result = summary.length() > 1900 ? summary.substring(0, 1900) : summary;
		settingsRepo.findByUserId(userId).ifPresent(s -> {
			s.setLastRunAt(Instant.now());
			s.setLastRunSummary(result);
			settingsRepo.save(s);
		});
	}

	String search(Long userId) {
		SearchSettings s = settings(userId);
		String cv = users.mainCvText(userId);
		List<String> terms = skills.matchTerms(userId);
		if (terms.isEmpty()) {
			throw new ValidationException("Add your skills first, so openings can be matched against them.");
		}
		KeywordMatcher matcher = new KeywordMatcher();
		List<String> problems = new ArrayList<>();

		List<Posting> found = new ArrayList<>();
		for (String title : s.getTitles()) {
			found.addAll(boards.remotive(title));
		}
		found.addAll(boards.arbeitnow());
		found.addAll(aggregatorOpenings(s, problems));
		List<WatchedCompany> watched = watchedRepo.findByUserIdOrderByNameAsc(userId);
		Set<String> watchedNames = new HashSet<>();
		for (WatchedCompany w : watched) {
			watchedNames.add(PostingFilter.key(w.getName()));
			found.addAll(boards.openings(boardOf(w), w.getName()));
		}
		Map<String, Integer> bySource = new TreeMap<>();
		found.forEach(p -> bySource.merge(p.source(), 1, Integer::sum));
		int checked = found.size();
		List<Posting> candidates = new ArrayList<>(found.stream().filter(p -> wanted(p, s)).toList());

		// Deep search: companies seen on the general boards may have their own career page with more openings.
		int discovered = 0;
		Set<String> probe = new LinkedHashSet<>();
		for (Posting p : candidates) {
			if (!p.company().isBlank() && !watchedNames.contains(PostingFilter.key(p.company()))) {
				probe.add(p.company());
			}
		}
		for (String company : probe.stream().limit(MAX_COMPANIES_TO_PROBE).toList()) {
			var board = boards.findBoard(company);
			if (board.isEmpty()) {
				continue;
			}
			List<Posting> own = boards.openings(board.get(), company);
			checked += own.size();
			List<Posting> relevant = own.stream().filter(p -> wanted(p, s)).toList();
			if (!relevant.isEmpty()) {
				candidates.addAll(relevant);
				addWatched(userId, company, board.get());
				discovered++;
			}
		}
		int matchedPlaceAndTitle = candidates.size();

		Set<String> knownLinks = new HashSet<>();
		Set<String> knownNumbers = new HashSet<>();
		for (Job j : jobs.list(userId, null)) {
			if (j.getLink() != null) {
				knownLinks.add(j.getLink());
			}
			knownNumbers.add(j.getJobNumber());
		}
		Set<String> knownCompanies = new HashSet<>();
		companies.list(userId).forEach(c -> knownCompanies.add(PostingFilter.key(c.getName())));

		record Ranked(Posting posting, String description, int keywordPercent, boolean priority) {
		}
		int alreadyKnown = 0;
		List<Ranked> ranked = new ArrayList<>();
		for (Posting p : candidates) {
			if (p.link().isBlank() || !knownLinks.add(p.link()) || !knownNumbers.add(p.jobNumber())) {
				alreadyKnown++;
				continue;
			}
			String description = PostingFilter.text(p.description());
			if (description.length() < 50) {
				continue;
			}
			if (description.length() > MAX_DESCRIPTION) {
				description = description.substring(0, MAX_DESCRIPTION);
			}
			int percent = matcher.match(description, cv, terms).coveragePercent();
			ranked.add(new Ranked(p, description, percent, PostingFilter.inPriorityPlace(p, s.getPriorityLocations())));
		}
		// priority places first, then the best skill match, so the AI rates the most promising openings
		ranked.sort((a, b) -> {
			int byPlace = Boolean.compare(b.priority(), a.priority());
			return byPlace != 0 ? byPlace : Integer.compare(b.keywordPercent(), a.keywordPercent());
		});

		int saved = 0;
		int newCompanies = 0;
		int rated = 0;
		int tooWeak = 0;
		int lowSkillMatch = 0;
		boolean aiDown = false;
		for (Ranked r : ranked) {
			if (saved >= MAX_NEW_JOBS) {
				break;
			}
			Posting p = r.posting();
			Optional<Fit> ai = Optional.empty();
			if (rated < MAX_AI_RATINGS && !aiDown) {
				ai = judge.judge(cv, p, r.description(), s.getTitles());
				aiDown = ai.isEmpty();
				rated += ai.isPresent() ? 1 : 0;
			}
			int fit;
			String note;
			if (ai.isPresent()) {
				// the AI reads the whole CV, so its opinion decides even when few listed skills appear in the posting
				if (ai.get().score() < s.minFit()) {
					tooWeak++;
					continue;
				}
				fit = FitScore.withPriority(ai.get().score(), r.priority());
				note = ai.get().reason();
			} else {
				if (r.keywordPercent() < s.getMinMatchPercent()) {
					lowSkillMatch++;
					continue;
				}
				fit = FitScore.withPriority(FitScore.fromKeywords(r.keywordPercent(), p.title()), r.priority());
				note = "Skill match " + r.keywordPercent() + "%. Not rated by the AI.";
			}
			Job job = jobs.create(userId, new JobRequest(p.jobNumber(), shorten(p.title()), shorten(p.company()),
					r.description(), p.link(), LocalDate.now(), JobStatus.SAVED,
					"Found by the job search via " + p.source() + ". Keyword match: " + r.keywordPercent() + "%."));
			jobs.annotate(userId, job.getId(), p.location(), fit, note, r.priority());
			saved++;
			if (!p.company().isBlank() && knownCompanies.add(PostingFilter.key(p.company()))) {
				addToCompanies(userId, p.company());
				newCompanies++;
			}
		}
		StringBuilder summary = new StringBuilder("Checked ").append(checked).append(" openings ").append(bySource)
				.append(". ").append(matchedPlaceAndTitle).append(" fit your titles and places");
		if (alreadyKnown > 0) {
			summary.append(", ").append(alreadyKnown).append(" were already saved");
		}
		if (lowSkillMatch > 0) {
			summary.append(", ").append(lowSkillMatch).append(" mention too few of your skills");
		}
		summary.append(". Saved ").append(saved).append(" new job").append(saved == 1 ? "" : "s").append(" (")
				.append(rated).append(" rated by the AI");
		if (tooWeak > 0) {
			summary.append(", ").append(tooWeak).append(" dropped as a weak fit");
		}
		summary.append("), added ").append(newCompanies).append(" new compan").append(newCompanies == 1 ? "y" : "ies");
		if (discovered > 0) {
			summary.append(", found ").append(discovered).append(" new career page").append(discovered == 1 ? "" : "s");
		}
		summary.append('.');
		if (rated == 0 && !ranked.isEmpty()) {
			summary.append(" The AI could not rate jobs (is GEMINI_API_KEY set on the server?), so skill match was used.");
		}
		problems.forEach(problem -> summary.append(' ').append(problem).append('.'));
		return summary.toString();
	}

	/**
	 * LinkedIn, Glassdoor and Indeed listings through JSearch; skipped without a key and at most every 6 hours.
	 * Each call asks for the next of the user's titles, so the titles take turns.
	 */
	private List<Posting> aggregatorOpenings(SearchSettings s, List<String> problems) {
		if (!aggregatorEnabled()) {
			return List.of();
		}
		if (Instant.now().isBefore(lastAggregatorCall.plus(AGGREGATOR_MIN_GAP))) {
			problems.add("JSearch was asked less than 6 hours ago, so it was skipped this time");
			return List.of();
		}
		boolean israel = s.getLocations().stream().anyMatch(l -> l.equalsIgnoreCase("israel"));
		String place = israel ? "israel" : s.getLocations().stream()
				.filter(l -> !List.of("remote", "worldwide", "anywhere", "emea").contains(l.toLowerCase(Locale.ROOT)))
				.findFirst().orElse("");
		List<Posting> found = new ArrayList<>();
		for (int i = 0; i < jsearchQueries && !s.getTitles().isEmpty(); i++) {
			String title = s.getTitles().get(Math.floorMod(aggregatorTurn++, s.getTitles().size()));
			String query = title + " jobs" + (place.isBlank() ? "" : " in " + place);
			JobBoards.Fetched fetched = boards.jsearch(jsearchKey, query, israel ? "il" : "");
			found.addAll(fetched.postings());
			if (fetched.error() != null) {
				problems.add(fetched.error());
				return found;
			}
			problems.add("JSearch \"" + query + "\" returned " + fetched.postings().size() + " openings");
		}
		lastAggregatorCall = Instant.now();
		return found;
	}

	public boolean aggregatorEnabled() {
		return jsearchKey != null && !jsearchKey.isBlank();
	}

	private static boolean wanted(Posting p, SearchSettings s) {
		return PostingFilter.wanted(p, s.getTitles(), s.getExcludedTitles(), s.getLocations());
	}

	private void addToCompanies(Long userId, String name) {
		try {
			companies.create(userId, new CompanyRequest(shorten(name), List.of()));
		} catch (ConflictException e) {
			// already on the Companies page
		}
	}

	private static String shorten(String s) {
		return s.length() > 250 ? s.substring(0, 250) : s;
	}

}
