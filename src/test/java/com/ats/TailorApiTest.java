package com.ats;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.ats.service.CvTextExtractor;
import com.ats.service.LlmClient;
import com.ats.service.LlmException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

class TailorApiTest extends ApiTestSupport {

    private static final String CV = """
            Eden Yaakobi
            eden.cv@example.com
            +972 54 123 4567
            https://linkedin.com/in/edenyaakobi
            Backend engineer with Java and Spring.
            2019 - 2023 Acme: built REST APIs
            """;

    @MockitoBean
    LlmClient llm;

    @Autowired
    CvTextExtractor extractor;

    private String jobJson(String technologies) {
        return """
                {"title":"Backend Engineer","hardRequirements":["3 years of Java"],
                 "requiredTechnologies":%s,"preferredQualifications":[],"domainKeywords":[]}
                """.formatted(technologies);
    }

    private String resumeJson(String skillsJson, String bullet) {
        return """
                {"name":"[[NAME]] Yaakobi","contact":["[[EMAIL_1]]","[[PHONE_1]]","[[LINK_1]]"],
                 "summary":"Backend engineer focused on Java and Spring",
                 "skills":%s,
                 "experience":[{"role":"Backend Engineer","company":"Acme","period":"2019 - 2023","bullets":["%s"]}],
                 "projects":[],"education":[],"additional":[]}
                """.formatted(skillsJson, bullet);
    }

    private String bulletsJson(String optimized) {
        return "{\"bullets\":[{\"id\":0,\"optimizedBullet\":\"" + optimized + "\",\"matchedRequirement\":\"Java\"}]}";
    }

    private void stub(String job, String resume, String bullets) {
        when(llm.generateJson(anyString(), anyString(), anyDouble(), any())).thenAnswer(call -> {
            String system = call.getArgument(0);
            if (system.contains("reads job postings")) {
                return job;
            }
            if (system.contains("resume parser")) {
                return resume;
            }
            return bullets;
        });
    }

    private void stubDefault() {
        stub(jobJson("[\"Java\",\"Spring\"]"), resumeJson("[\"Java\",\"Spring\"]", "Built REST APIs"),
                bulletsJson("Built REST APIs"));
    }

    private byte[] resumeFile(Account account, long jobId) throws Exception {
        return mvc.perform(get("/api/users/" + account.id() + "/jobs/" + jobId + "/resume")
                .header("Authorization", account.bearer()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();
    }

    private void uploadCv(Account account, String content) throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "cv.txt", "text/plain", content.getBytes());
        mvc.perform(multipart(HttpMethod.PUT, "/api/users/" + account.id() + "/cv").file(file)
                .header("Authorization", account.bearer()))
                .andExpect(status().isOk());
    }

    private void createSkill(Account account, String body) throws Exception {
        mvc.perform(json(post("/api/users/" + account.id() + "/skills"), account, body)).andExpect(status().isCreated());
    }

    private long createJob(Account account, String description) throws Exception {
        String response = mvc.perform(json(post("/api/users/" + account.id() + "/jobs"), account,
                "{\"jobNumber\":\"REQ/7\",\"description\":\"" + description + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return idOf(response);
    }

    private String tailorUrl(Account account, long jobId) {
        return "/api/users/" + account.id() + "/jobs/" + jobId + "/tailor";
    }

    @Test
    public void GivenCvSkillsAndJob_WhenTailoring_ThenResumeIsStoredWithRestoredContactDetails() throws Exception {
        Account account = newAccount();
        uploadCv(account, CV);
        long jobId = createJob(account, "Java and Spring backend role");
        stubDefault();

        mvc.perform(post(tailorUrl(account, jobId)).header("Authorization", account.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.job.hasTailoredResume").value(true))
                .andExpect(jsonPath("$.job.tailoredResumeFileName").value("tailored-cv-REQ_7.docx"));

        String text = extractor.extract(resumeFile(account, jobId), "cv.docx");
        assertTrue(text.contains("eden.cv@example.com"));
        assertTrue(text.contains("+972 54 123 4567"));
        assertTrue(text.contains("https://linkedin.com/in/edenyaakobi"));
        assertTrue(text.contains("Eden Yaakobi"));
        assertFalse(text.contains("[["));
    }

    @Test
    public void GivenCv_WhenTailoring_ThenNoPromptSentToAiContainsPersonalDetails() throws Exception {
        Account account = newAccount();
        uploadCv(account, CV);
        long jobId = createJob(account, "Java role");
        stubDefault();

        mvc.perform(post(tailorUrl(account, jobId)).header("Authorization", account.bearer()))
                .andExpect(status().isOk());

        ArgumentCaptor<String> prompts = ArgumentCaptor.forClass(String.class);
        verify(llm, times(3)).generateJson(anyString(), prompts.capture(), anyDouble(), any());
        String all = String.join("\n", prompts.getAllValues());
        assertFalse(all.contains("eden.cv@example.com"));
        assertFalse(all.contains("123 4567"));
        assertFalse(all.contains("linkedin.com/in/edenyaakobi"));
        assertFalse(all.contains("Eden"));
        assertTrue(all.contains("[[EMAIL_1]]"));
        assertTrue(all.contains("Java role"));
        assertTrue(all.contains("Backend engineer with Java and Spring"));
    }

    @Test
    public void GivenCv_WhenTailoring_ThenEveryAiCallUsesLowTemperatureAndAJsonSchema() throws Exception {
        Account account = newAccount();
        uploadCv(account, CV);
        long jobId = createJob(account, "Java role");
        stubDefault();

        mvc.perform(post(tailorUrl(account, jobId)).header("Authorization", account.bearer()))
                .andExpect(status().isOk());

        verify(llm, times(3)).generateJson(anyString(), anyString(), eq(0.1),
                argThat(schema -> schema != null && "object".equals(schema.get("type"))));
    }

    @Test
    public void GivenUserSkillsAndRelevantCourse_WhenTailoring_ThenSkillsAreMergedAndCourseIsListed() throws Exception {
        Account account = newAccount();
        uploadCv(account, CV);
        createSkill(account, "{\"name\":\"Kubernetes\",\"explanation\":\"Ran clusters\"}");
        mvc.perform(json(post("/api/users/" + account.id() + "/courses"), account,
                "{\"courseNumber\":\"CS101\",\"institution\":\"TAU\",\"description\":\"Kubernetes lab\"}"))
                .andExpect(status().isCreated());
        mvc.perform(json(post("/api/users/" + account.id() + "/courses"), account,
                "{\"courseNumber\":\"ART9\",\"institution\":\"TAU\",\"description\":\"Watercolor\"}"))
                .andExpect(status().isCreated());
        long jobId = createJob(account, "Kubernetes cloud role");
        stub(jobJson("[\"Kubernetes\"]"), resumeJson("[\"Java\"]", "Built REST APIs"), bulletsJson("Built REST APIs"));

        mvc.perform(post(tailorUrl(account, jobId)).header("Authorization", account.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resumeMarkdown", containsString("Kubernetes, Java")))
                .andExpect(jsonPath("$.resumeMarkdown", containsString("Course: CS101 (TAU)")))
                .andExpect(jsonPath("$.resumeMarkdown", not(containsString("ART9"))))
                .andExpect(jsonPath("$.unsupportedSkills", hasSize(0)));
    }

    @Test
    public void GivenAiClaimsSkillNotInCvOrSkills_WhenTailoring_ThenItIsReportedAsUnsupported() throws Exception {
        Account account = newAccount();
        uploadCv(account, CV);
        long jobId = createJob(account, "Java role");
        stub(jobJson("[\"Java\"]"), resumeJson("[\"Java\",\"Rust\"]", "Built REST APIs"), bulletsJson("Built REST APIs"));

        mvc.perform(post(tailorUrl(account, jobId)).header("Authorization", account.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unsupportedSkills", hasSize(1)))
                .andExpect(jsonPath("$.unsupportedSkills", hasItem("Rust")));
    }

    @Test
    public void GivenTailoredCvAddsKnownSkill_WhenTailoring_ThenCoverageIncreasesAndNothingIsUnsupported() throws Exception {
        Account account = newAccount();
        uploadCv(account, CV);
        createSkill(account, "{\"name\":\"Java\",\"keywords\":[\"Spring\",\"Kubernetes\"]}");
        long jobId = createJob(account, "We need Java, Spring and Kubernetes");
        stub(jobJson("[\"Java\",\"Spring\",\"Kubernetes\"]"),
                resumeJson("[\"Java\",\"Spring\",\"Kubernetes\"]", "Built REST APIs"), bulletsJson("Built REST APIs"));

        mvc.perform(post(tailorUrl(account, jobId)).header("Authorization", account.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.coverageBefore").value(66))
                .andExpect(jsonPath("$.coverageAfter").value(100))
                .andExpect(jsonPath("$.missingTerms", hasSize(0)))
                .andExpect(jsonPath("$.unsupportedSkills", hasSize(0)));
    }

    @Test
    public void GivenJobNamesPostgreSqlButCvSaysPostgres_WhenTailoring_ThenTermIsNormalizedToTheJobSpelling() throws Exception {
        Account account = newAccount();
        uploadCv(account, CV + "Used Postgres daily\n");
        long jobId = createJob(account, "PostgreSQL role");
        stub(jobJson("[\"PostgreSQL\"]"), resumeJson("[\"Java\",\"Postgres\"]", "Tuned Postgres queries"),
                bulletsJson("Tuned Postgres queries"));

        mvc.perform(post(tailorUrl(account, jobId)).header("Authorization", account.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.namingFixes[0].from").value("Postgres"))
                .andExpect(jsonPath("$.namingFixes[0].to").value("PostgreSQL"))
                .andExpect(jsonPath("$.missingKeywords", hasSize(0)));

        String text = extractor.extract(resumeFile(account, jobId), "cv.docx");
        assertTrue(text.contains("Tuned PostgreSQL queries"));
        assertFalse(text.contains("Postgres "));
    }

    @Test
    public void GivenJobKeywordTheCvLacks_WhenTailoring_ThenItIsListedAsMissingAndNotAddedToTheResume() throws Exception {
        Account account = newAccount();
        uploadCv(account, CV);
        long jobId = createJob(account, "Java and Terraform role");
        stub(jobJson("[\"Java\",\"Terraform\"]"), resumeJson("[\"Java\"]", "Built REST APIs"), bulletsJson("Built REST APIs"));

        mvc.perform(post(tailorUrl(account, jobId)).header("Authorization", account.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.missingKeywords", hasItem("Terraform")))
                .andExpect(jsonPath("$.resumeMarkdown", not(containsString("Terraform"))));
    }

    @Test
    public void GivenGroundedOptimizedBullet_WhenTailoring_ThenItReplacesTheOriginalAndMetricIsPreserved() throws Exception {
        Account account = newAccount();
        uploadCv(account, CV);
        long jobId = createJob(account, "Java role");
        stub(jobJson("[\"Java\"]"), resumeJson("[\"Java\"]", "Built REST APIs serving 2M requests"),
                bulletsJson("Built Java REST APIs that served 2M requests"));

        mvc.perform(post(tailorUrl(account, jobId)).header("Authorization", account.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bullets", hasSize(1)))
                .andExpect(jsonPath("$.bullets[0].originalBullet").value("Built REST APIs serving 2M requests"))
                .andExpect(jsonPath("$.bullets[0].optimizedBullet").value("Built Java REST APIs that served 2M requests"))
                .andExpect(jsonPath("$.bullets[0].matchedRequirement").value("Java"))
                .andExpect(jsonPath("$.bullets[0].metricPreserved").value(true))
                .andExpect(jsonPath("$.rejectedBullets", hasSize(0)))
                .andExpect(jsonPath("$.resumeMarkdown", containsString("- Built Java REST APIs that served 2M requests")));
    }

    @Test
    public void GivenOptimizedBulletAddsTechnologyFromTheJob_WhenTailoring_ThenOriginalBulletIsKeptAndReported() throws Exception {
        Account account = newAccount();
        uploadCv(account, CV);
        long jobId = createJob(account, "Java and Kubernetes role");
        stub(jobJson("[\"Java\",\"Kubernetes\"]"), resumeJson("[\"Java\"]", "Built REST APIs"),
                bulletsJson("Built REST APIs deployed on Kubernetes"));

        mvc.perform(post(tailorUrl(account, jobId)).header("Authorization", account.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bullets", hasSize(0)))
                .andExpect(jsonPath("$.rejectedBullets[0]", containsString("adds the technology 'Kubernetes'")))
                .andExpect(jsonPath("$.resumeMarkdown", containsString("- Built REST APIs")))
                .andExpect(jsonPath("$.resumeMarkdown", not(containsString("Kubernetes"))));
    }

    @Test
    public void GivenOptimizedBulletInventsAMetric_WhenTailoring_ThenOriginalBulletIsKept() throws Exception {
        Account account = newAccount();
        uploadCv(account, CV);
        long jobId = createJob(account, "Java role");
        stub(jobJson("[\"Java\"]"), resumeJson("[\"Java\"]", "Built REST APIs"),
                bulletsJson("Built REST APIs and cut latency by 40%"));

        mvc.perform(post(tailorUrl(account, jobId)).header("Authorization", account.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bullets", hasSize(0)))
                .andExpect(jsonPath("$.rejectedBullets[0]", containsString("adds numbers")))
                .andExpect(jsonPath("$.resumeMarkdown", not(containsString("40%"))));
    }

    @Test
    public void GivenOptimizedBulletUsesBuzzword_WhenTailoring_ThenOriginalBulletIsKept() throws Exception {
        Account account = newAccount();
        uploadCv(account, CV);
        long jobId = createJob(account, "Java role");
        stub(jobJson("[\"Java\"]"), resumeJson("[\"Java\"]", "Built REST APIs"),
                bulletsJson("Spearheaded innovative REST APIs"));

        mvc.perform(post(tailorUrl(account, jobId)).header("Authorization", account.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rejectedBullets[0]", containsString("buzzword")))
                .andExpect(jsonPath("$.resumeMarkdown", not(containsString("Spearheaded"))));
    }

    @Test
    public void GivenAiOmitsABullet_WhenTailoring_ThenOriginalBulletIsKept() throws Exception {
        Account account = newAccount();
        uploadCv(account, CV);
        long jobId = createJob(account, "Java role");
        stub(jobJson("[\"Java\"]"), resumeJson("[\"Java\"]", "Built REST APIs"), "{\"bullets\":[]}");

        mvc.perform(post(tailorUrl(account, jobId)).header("Authorization", account.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rejectedBullets[0]", containsString("no result")))
                .andExpect(jsonPath("$.resumeMarkdown", containsString("- Built REST APIs")));
    }

    @Test
    public void GivenFinishedResume_WhenTailoring_ThenMarkdownIsSingleColumnWithRestoredContactAndNoTables() throws Exception {
        Account account = newAccount();
        uploadCv(account, CV);
        long jobId = createJob(account, "Java role");
        stubDefault();

        String body = mvc.perform(post(tailorUrl(account, jobId)).header("Authorization", account.bearer()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertTrue(body.contains("## EXPERIENCE"));
        assertTrue(body.contains("## SKILLS"));
        assertTrue(body.contains("eden.cv@example.com"));
        assertFalse(body.contains("|---"));
        assertFalse(body.contains("[["));
    }

    @Test
    public void GivenAiWrapsJsonInCodeFence_WhenTailoring_ThenItIsAccepted() throws Exception {
        Account account = newAccount();
        uploadCv(account, CV);
        long jobId = createJob(account, "Java role");
        stub("```json\n" + jobJson("[\"Java\"]") + "\n```",
                "```json\n" + resumeJson("[\"Java\"]", "Built REST APIs") + "\n```",
                "```json\n" + bulletsJson("Built REST APIs") + "\n```");

        mvc.perform(post(tailorUrl(account, jobId)).header("Authorization", account.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.job.hasTailoredResume").value(true));
    }

    @Test
    public void GivenAiReturnsGarbage_WhenTailoring_ThenBadGatewayAndNoResumeStored() throws Exception {
        Account account = newAccount();
        uploadCv(account, CV);
        long jobId = createJob(account, "Java role");
        when(llm.generateJson(anyString(), anyString(), anyDouble(), any())).thenReturn("sorry, I cannot do that");

        mvc.perform(post(tailorUrl(account, jobId)).header("Authorization", account.bearer()))
                .andExpect(status().isBadGateway());

        mvc.perform(get("/api/users/" + account.id() + "/jobs/" + jobId).header("Authorization", account.bearer()))
                .andExpect(jsonPath("$.hasTailoredResume").value(false));
    }

    @Test
    public void GivenAiReturnsEmptyResume_WhenTailoring_ThenBadGateway() throws Exception {
        Account account = newAccount();
        uploadCv(account, CV);
        long jobId = createJob(account, "Java role");
        stub(jobJson("[\"Java\"]"), "{}", bulletsJson("x"));

        mvc.perform(post(tailorUrl(account, jobId)).header("Authorization", account.bearer()))
                .andExpect(status().isBadGateway());
    }

    @Test
    public void GivenAiFindsNothingInJobPosting_WhenTailoring_ThenBadGateway() throws Exception {
        Account account = newAccount();
        uploadCv(account, CV);
        long jobId = createJob(account, "Java role");
        stub("{}", resumeJson("[\"Java\"]", "Built REST APIs"), bulletsJson("x"));

        mvc.perform(post(tailorUrl(account, jobId)).header("Authorization", account.bearer()))
                .andExpect(status().isBadGateway());
    }

    @Test
    public void GivenAiCallFails_WhenTailoring_ThenBadGatewayWithItsMessageAndNoResumeStored() throws Exception {
        Account account = newAccount();
        uploadCv(account, CV);
        long jobId = createJob(account, "Java role");
        when(llm.generateJson(anyString(), anyString(), anyDouble(), any()))
                .thenThrow(new LlmException("Gemini rate limit reached."));

        mvc.perform(post(tailorUrl(account, jobId)).header("Authorization", account.bearer()))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.message").value("Gemini rate limit reached."));

        mvc.perform(get("/api/users/" + account.id() + "/jobs/" + jobId).header("Authorization", account.bearer()))
                .andExpect(jsonPath("$.hasTailoredResume").value(false));
    }

    @Test
    public void GivenNoMainCv_WhenTailoring_ThenBadRequestAndAiIsNotCalled() throws Exception {
        Account account = newAccount();
        long jobId = createJob(account, "Java role");

        mvc.perform(post(tailorUrl(account, jobId)).header("Authorization", account.bearer()))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(llm);
    }

    @Test
    public void GivenUnknownJob_WhenTailoring_ThenNotFoundAndAiIsNotCalled() throws Exception {
        Account account = newAccount();
        uploadCv(account, CV);

        mvc.perform(post(tailorUrl(account, 999999)).header("Authorization", account.bearer()))
                .andExpect(status().isNotFound());

        verifyNoInteractions(llm);
    }

    @Test
    public void GivenAnotherUsersToken_WhenTailoring_ThenForbiddenAndAiIsNotCalled() throws Exception {
        Account owner = newAccount();
        Account other = newAccount();
        uploadCv(owner, CV);
        long jobId = createJob(owner, "Java role");

        mvc.perform(post(tailorUrl(owner, jobId)).header("Authorization", other.bearer()))
                .andExpect(status().isForbidden());

        verifyNoInteractions(llm);
    }

    @Test
    public void GivenNoToken_WhenTailoring_ThenUnauthorized() throws Exception {
        Account account = newAccount();
        uploadCv(account, CV);
        long jobId = createJob(account, "Java role");

        mvc.perform(post(tailorUrl(account, jobId))).andExpect(status().isUnauthorized());

        verifyNoInteractions(llm);
    }

    @Test
    public void GivenJobAlreadyTailored_WhenTailoringAgain_ThenResumeIsReplaced() throws Exception {
        Account account = newAccount();
        uploadCv(account, CV);
        long jobId = createJob(account, "Java role");
        stubDefault();
        mvc.perform(post(tailorUrl(account, jobId)).header("Authorization", account.bearer())).andExpect(status().isOk());
        stub(jobJson("[\"Java\"]"),
                resumeJson("[\"Java\"]", "Built REST APIs").replace("Backend engineer focused on Java and Spring", "Second version"),
                bulletsJson("Built REST APIs"));

        mvc.perform(post(tailorUrl(account, jobId)).header("Authorization", account.bearer())).andExpect(status().isOk());

        assertTrue(extractor.extract(resumeFile(account, jobId), "cv.docx").contains("Second version"));
    }

}
