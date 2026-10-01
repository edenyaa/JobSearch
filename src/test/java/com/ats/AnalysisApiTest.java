package com.ats;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.ResultActions;

class AnalysisApiTest extends ApiTestSupport {

    private ResultActions uploadCv(Account account, String fileName, String content) throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", fileName, "text/plain", content.getBytes());
        return mvc.perform(multipart(HttpMethod.PUT, "/api/users/" + account.id() + "/cv").file(file)
                .header("Authorization", account.bearer()));
    }

    private long createJob(Account account, String description) throws Exception {
        String response = mvc.perform(json(post("/api/users/" + account.id() + "/jobs"), account,
                "{\"description\":\"" + description + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return idOf(response);
    }

    private void createSkill(Account account, String body) throws Exception {
        mvc.perform(json(post("/api/users/" + account.id() + "/skills"), account, body)).andExpect(status().isCreated());
    }

    private String analysisUrl(Account account, long jobId) {
        return "/api/users/" + account.id() + "/jobs/" + jobId + "/analysis";
    }

    @Test
    public void GivenCvButNoSkillsAddedManually_WhenAnalyzing_ThenSkillsFromTheCvAreUsed() throws Exception {
        Account account = newAccount();
        uploadCv(account, "cv.txt", "Backend engineer using Java and Docker").andExpect(status().isOk());
        long jobId = createJob(account, "We need Java, Docker and Kubernetes");

        mvc.perform(get(analysisUrl(account, jobId)).header("Authorization", account.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(3))
                .andExpect(jsonPath("$.matched").value(2))
                .andExpect(jsonPath("$.missing").value(1))
                .andExpect(jsonPath("$.coveragePercent").value(67))
                .andExpect(jsonPath("$.potentialPercent").value(67))
                .andExpect(jsonPath("$.terms[?(@.name=='Kubernetes')].status", contains("MISSING")))
                .andExpect(jsonPath("$.terms[?(@.name=='Java')].status", contains("MATCHED")));
    }

    @Test
    public void GivenSkillAddedByUserButNotInCv_WhenAnalyzing_ThenItIsInProfileAndPotentialRises() throws Exception {
        Account account = newAccount();
        uploadCv(account, "cv.txt", "Backend engineer using Java and Docker").andExpect(status().isOk());
        createSkill(account, "{\"name\":\"Kubernetes\"}");
        long jobId = createJob(account, "We need Java, Docker and Kubernetes");

        mvc.perform(get(analysisUrl(account, jobId)).header("Authorization", account.bearer()))
                .andExpect(jsonPath("$.coveragePercent").value(67))
                .andExpect(jsonPath("$.potentialPercent").value(100))
                .andExpect(jsonPath("$.inProfile").value(1))
                .andExpect(jsonPath("$.terms[?(@.name=='Kubernetes')].status", contains("IN_PROFILE")));
    }

    @Test
    public void GivenAnalysis_WhenReading_ThenCategoriesCarryLabelsAndCvCompositionIsIncluded() throws Exception {
        Account account = newAccount();
        uploadCv(account, "cv.txt", "Java Python Docker Git").andExpect(status().isOk());
        long jobId = createJob(account, "Java and Docker needed");

        mvc.perform(get(analysisUrl(account, jobId)).header("Authorization", account.bearer()))
                .andExpect(jsonPath("$.categories[0].label").value("Languages"))
                .andExpect(jsonPath("$.categories[0].percent").value(100))
                .andExpect(jsonPath("$.cvCategories[0].label").value("Languages"))
                .andExpect(jsonPath("$.cvCategories[0].count").value(2))
                .andExpect(jsonPath("$.extraCvSkills", containsInAnyOrder("Python", "Git")));
    }

    @Test
    public void GivenAnalysis_WhenReading_ThenOverallScoreAndWeightedComponentsAreIncluded() throws Exception {
        Account account = newAccount();
        uploadCv(account, "cv.txt", "Backend engineer using Java and Docker").andExpect(status().isOk());
        long jobId = createJob(account, "We need Java and Docker");

        mvc.perform(get(analysisUrl(account, jobId)).header("Authorization", account.bearer()))
                .andExpect(jsonPath("$.coveragePercent").value(100))
                .andExpect(jsonPath("$.overallScore", lessThan(100)))
                .andExpect(jsonPath("$.components[0].key").value("skills"))
                .andExpect(jsonPath("$.components[0].weight").value(50))
                .andExpect(jsonPath("$.components[?(@.key=='readiness')].weight", contains(10)))
                .andExpect(jsonPath("$.readinessChecks", hasSize(7)));
    }

    @Test
    public void GivenJobWithTitleAndYears_WhenAnalyzing_ThenRequirementChecksAreReturned() throws Exception {
        Account account = newAccount();
        uploadCv(account, "cv.txt", "Developer 2024 - 2025\nJava").andExpect(status().isOk());
        String created = mvc.perform(json(post("/api/users/" + account.id() + "/jobs"), account,
                "{\"title\":\"Backend Engineer\",\"description\":\"Java developer with 5+ years of experience\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();

        mvc.perform(get(analysisUrl(account, idOf(created))).header("Authorization", account.bearer()))
                .andExpect(jsonPath("$.requirementChecks[?(@.id=='years')].result", contains("FAIL")))
                .andExpect(jsonPath("$.requirementChecks[?(@.id=='title')].result", contains("FAIL")))
                .andExpect(jsonPath("$.components[?(@.key=='requirements')].percent", contains(0)));
    }

    @Test
    public void GivenNiceToHaveSkill_WhenAnalyzing_ThenTermCarriesImportance() throws Exception {
        Account account = newAccount();
        uploadCv(account, "cv.txt", "Java developer").andExpect(status().isOk());
        long jobId = createJob(account, "We need Java.\\nKubernetes is a plus.");

        mvc.perform(get(analysisUrl(account, jobId)).header("Authorization", account.bearer()))
                .andExpect(jsonPath("$.terms[?(@.name=='Java')].importance", contains("REQUIRED")))
                .andExpect(jsonPath("$.terms[?(@.name=='Kubernetes')].importance", contains("PREFERRED")));
    }

    @Test
    public void GivenRepeatedPhraseInPosting_WhenAnalyzing_ThenItIsListedAsOtherKeyword() throws Exception {
        Account account = newAccount();
        uploadCv(account, "cv.txt", "Java developer").andExpect(status().isOk());
        long jobId = createJob(account, "Payment systems matter. Payment systems scale. We use Java.");

        mvc.perform(get(analysisUrl(account, jobId)).header("Authorization", account.bearer()))
                .andExpect(jsonPath("$.keywords[0].term").value("payment systems"))
                .andExpect(jsonPath("$.keywords[0].inCv").value(false))
                .andExpect(jsonPath("$.keywords[0].mentions").value(2));
    }

    @Test
    public void GivenNoMainCv_WhenAnalyzing_ThenBadRequest() throws Exception {
        Account account = newAccount();
        long jobId = createJob(account, "Java developer");

        mvc.perform(get(analysisUrl(account, jobId)).header("Authorization", account.bearer()))
                .andExpect(status().isBadRequest());
    }

    @Test
    public void GivenUnknownJob_WhenAnalyzing_ThenNotFound() throws Exception {
        Account account = newAccount();
        uploadCv(account, "cv.txt", "Java").andExpect(status().isOk());

        mvc.perform(get(analysisUrl(account, 999999)).header("Authorization", account.bearer()))
                .andExpect(status().isNotFound());
    }

    @Test
    public void GivenAnotherUsersTokenOrNoToken_WhenAnalyzing_ThenForbiddenOrUnauthorized() throws Exception {
        Account owner = newAccount();
        Account other = newAccount();
        long jobId = createJob(owner, "Java developer");

        mvc.perform(get(analysisUrl(owner, jobId)).header("Authorization", other.bearer())).andExpect(status().isForbidden());
        mvc.perform(get(analysisUrl(owner, jobId))).andExpect(status().isUnauthorized());
    }

    @Test
    public void GivenCvWithSkills_WhenUploading_ThenSkillsAreAddedWithSourceCvAndCountIsReturned() throws Exception {
        Account account = newAccount();

        uploadCv(account, "cv.txt", "Java, Postgres, PostgreSQL and Docker")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasMainCv").value(true))
                .andExpect(jsonPath("$.importedSkills").value(3));

        mvc.perform(get("/api/users/" + account.id() + "/skills").header("Authorization", account.bearer()))
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[*].name", containsInAnyOrder("Java", "PostgreSQL", "Docker")))
                .andExpect(jsonPath("$[*].source", everyItem(is("CV"))))
                .andExpect(jsonPath("$[?(@.name=='PostgreSQL')].keywords[0]", contains("Postgres")));
    }

    @Test
    public void GivenSkillsAlreadyImported_WhenUploadingSameCvAgain_ThenNothingIsDuplicated() throws Exception {
        Account account = newAccount();
        uploadCv(account, "cv.txt", "Java and Docker").andExpect(jsonPath("$.importedSkills").value(2));

        uploadCv(account, "cv.txt", "Java and Docker").andExpect(jsonPath("$.importedSkills").value(0));

        mvc.perform(get("/api/users/" + account.id() + "/skills").header("Authorization", account.bearer()))
                .andExpect(jsonPath("$", hasSize(2)));
    }

    @Test
    public void GivenManualSkillWithSameName_WhenUploadingCv_ThenItIsKeptUntouchedAndNotImportedAgain() throws Exception {
        Account account = newAccount();
        createSkill(account, "{\"name\":\"java\",\"explanation\":\"mine\"}");

        uploadCv(account, "cv.txt", "Java and Docker").andExpect(jsonPath("$.importedSkills").value(1));

        mvc.perform(get("/api/users/" + account.id() + "/skills").header("Authorization", account.bearer()))
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[?(@.name=='java')].source", contains("MANUAL")))
                .andExpect(jsonPath("$[?(@.name=='java')].explanation", contains("mine")));
    }

    @Test
    public void GivenUnsupportedCvFormat_WhenUploading_ThenUploadSucceedsWithoutImportingAnything() throws Exception {
        Account account = newAccount();

        uploadCv(account, "cv.exe", "Java and Docker")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.importedSkills").value(0));

        mvc.perform(get("/api/users/" + account.id() + "/skills").header("Authorization", account.bearer()))
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    public void GivenCvWithoutKnownSkills_WhenUploading_ThenNothingIsImported() throws Exception {
        Account account = newAccount();

        uploadCv(account, "cv.txt", "I bake bread").andExpect(jsonPath("$.importedSkills").value(0));
    }

    @Test
    public void GivenSkillCreatedByUser_WhenListing_ThenSourceIsManual() throws Exception {
        Account account = newAccount();

        mvc.perform(json(post("/api/users/" + account.id() + "/skills"), account, "{\"name\":\"Cooking\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.source").value("MANUAL"));
    }

    @Test
    public void GivenUploadedCv_WhenListingDetectedSkills_ThenTheyCarryCategoryAndKeywords() throws Exception {
        Account account = newAccount();
        uploadCv(account, "cv.txt", "Used Postgres with Java").andExpect(status().isOk());

        mvc.perform(get("/api/users/" + account.id() + "/cv/skills").header("Authorization", account.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.name=='PostgreSQL')].categoryLabel", contains("Databases")))
                .andExpect(jsonPath("$[?(@.name=='PostgreSQL')].keywords[0]", contains("Postgres")))
                .andExpect(jsonPath("$[?(@.name=='Java')].category", contains("LANGUAGES")));
    }

    @Test
    public void GivenNoCv_WhenListingDetectedSkills_ThenBadRequest() throws Exception {
        Account account = newAccount();

        mvc.perform(get("/api/users/" + account.id() + "/cv/skills").header("Authorization", account.bearer()))
                .andExpect(status().isBadRequest());
    }

}
