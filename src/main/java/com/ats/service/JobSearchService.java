package com.ats.service;

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
import com.ats.service.JobBoards.Board;
import jakarta.annotation.PreDestroy;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
	private static final int MAX_DESCRIPTION = 9500;

	private static final Pattern GREENHOUSE_URL = Pattern
			.compile("greenhouse\\.io/(?:embed/job_board\\?for=)?([A-Za-z0-9_-]+)");
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
	private final Set<Long> running = ConcurrentHashMap.newKeySet();
	private final ExecutorService background = Executors.newSingleThreadExecutor(r -> {
		Thread t = new Thread(r, "job-search");
		t.setDaemon(true);
		return t;
	});

	public JobSearchService(SearchSettingsRepository settingsRepo, WatchedCompanyRepository watchedRepo,
			JobBoards boards, JobService jobs, CompanyService companies, SkillService skills, UserService users) {
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
		s.setTitles(new ArrayList<>(List.of("software engineer", "developer", "backend", "full stack", "java")));
		s.setExcludedTitles(new ArrayList<>(
				List.of("senior", "staff", "principal", "lead", "manager", "director", "head of", "intern")));
		s.setLocations(new ArrayList<>(List.of("israel", "tel aviv", "remote", "worldwide", "anywhere", "emea")));
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

	public SearchSettings update(Long userId, SettingsRequest req) {
		List<String> titles = clean(req.titles());
		if (titles.isEmpty()) {
			throw new ValidationException("Add at least one job title to search for.");
		}
		if (req.minMatchPercent() < 0 || req.minMatchPercent() > 100) {
			throw new ValidationException("The minimum match must be between 0 and 100.");
		}
		SearchSettings s = settings(userId);
		s.setTitles(titles);
		s.setExcludedTitles(clean(req.excludedTitles()));
		s.setLocations(clean(req.locations()));
		s.setMinMatchPercent(req.minMatchPercent());
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

	/** Adds a company by name or by the link of its Greenhouse or Lever career page. */
	public WatchedCompany watch(Long userId, String input) {
		String text = input.strip();
		Board board;
		String name;
		Matcher greenhouse = GREENHOUSE_URL.matcher(text);
		Matcher lever = LEVER_URL.matcher(text);
		if (greenhouse.find()) {
			board = new Board(JobBoards.GREENHOUSE, greenhouse.group(1));
			name = capitalize(board.slug());
		} else if (lever.find()) {
			board = new Board(JobBoards.LEVER, lever.group(1));
			name = capitalize(board.slug());
		} else {
			board = boards.findBoard(text).orElseThrow(() -> new ValidationException("No Greenhouse or Lever career page "
					+ "found for " + text + ". Paste the link of its career page instead."));
			name = text;
		}
		if (!boards.exists(board)) {
			throw new ValidationException("That career page could not be opened.");
		}
		settings(userId);
		WatchedCompany w = addWatched(userId, name, board);
		addToCompanies(userId, name);
		return w;
	}

	private WatchedCompany addWatched(Long userId, String name, Board board) {
		if (watchedRepo.existsByUserIdAndBoardAndSlug(userId, board.kind(), board.slug())) {
			throw new ConflictException(name + " is already in the search");
		}
		WatchedCompany w = new WatchedCompany();
		w.setUser(users.get(userId));
		w.setName(name);
		w.setBoard(board.kind());
		w.setSlug(board.slug());
		return watchedRepo.save(w);
	}

	public void unwatch(Long userId, Long id) {
		watchedRepo.delete(watchedRepo.findByIdAndUserId(id, userId).orElseThrow(
				() -> new NotFoundException("Searched company", id)));
	}

	private static String capitalize(String slug) {
		return slug.isEmpty() ? slug : slug.substring(0, 1).toUpperCase(Locale.ROOT) + slug.substring(1);
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

		List<Posting> found = new ArrayList<>();
		for (String title : s.getTitles()) {
			found.addAll(boards.remotive(title));
		}
		found.addAll(boards.arbeitnow());
		List<WatchedCompany> watched = watchedRepo.findByUserIdOrderByNameAsc(userId);
		Set<String> watchedNames = new HashSet<>();
		for (WatchedCompany w : watched) {
			watchedNames.add(PostingFilter.key(w.getName()));
			found.addAll(boards.openings(new Board(w.getBoard(), w.getSlug()), w.getName()));
		}
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

		int saved = 0;
		int newCompanies = 0;
		for (Posting p : candidates) {
			if (saved >= MAX_NEW_JOBS) {
				break;
			}
			if (p.link().isBlank() || !knownLinks.add(p.link()) || !knownNumbers.add(p.jobNumber())) {
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
			if (percent < s.getMinMatchPercent()) {
				continue;
			}
			jobs.create(userId, new JobRequest(p.jobNumber(), shorten(p.title()), shorten(p.company()), description,
					p.link(), LocalDate.now(), JobStatus.SAVED,
					"Found by the job search via " + p.source() + ". Keyword match: " + percent + "%."));
			saved++;
			if (!p.company().isBlank() && knownCompanies.add(PostingFilter.key(p.company()))) {
				addToCompanies(userId, p.company());
				newCompanies++;
			}
		}
		return "Checked " + checked + " openings: saved " + saved + " new job" + (saved == 1 ? "" : "s") + ", added "
				+ newCompanies + " new compan" + (newCompanies == 1 ? "y" : "ies") + (discovered > 0
						? ", found " + discovered + " new career page" + (discovered == 1 ? "" : "s") : "")
				+ ".";
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
