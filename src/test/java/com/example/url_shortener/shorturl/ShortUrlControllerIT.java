package com.example.url_shortener.shorturl;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.url_shortener.AbstractIntegrationTest;
import com.example.url_shortener.AuthTestSupport;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class ShortUrlControllerIT extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void createReturnsShortUrlWithGeneratedCode() throws Exception {
        String token = registerUser();

        mockMvc.perform(post("/api/urls")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody("https://example.com/create-test", null)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.shortCode").isNotEmpty())
                .andExpect(jsonPath("$.originalUrl").value("https://example.com/create-test"))
                .andExpect(jsonPath("$.clickCount").value(0))
                .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    void createWithCustomAliasUsesIt() throws Exception {
        String token = registerUser();
        String alias = "alias-" + System.nanoTime();

        mockMvc.perform(post("/api/urls")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody("https://example.com/alias-test", alias)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.shortCode").value(alias));
    }

    @Test
    void createRejectsDuplicateAlias() throws Exception {
        String token = registerUser();
        String alias = "dup-" + System.nanoTime();
        createUrl(token, "https://example.com/first", alias);

        mockMvc.perform(post("/api/urls")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody("https://example.com/second", alias)))
                .andExpect(status().isConflict());
    }

    @Test
    void createRejectsInvalidUrl() throws Exception {
        String token = registerUser();

        mockMvc.perform(post("/api/urls")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody("not-a-url", null)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createRequiresAuthentication() throws Exception {
        mockMvc.perform(post("/api/urls")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody("https://example.com/no-auth", null)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void listReturnsOnlyCallersUrls() throws Exception {
        String tokenA = registerUser();
        String tokenB = registerUser();
        createUrl(tokenA, "https://example.com/mine", null);

        mockMvc.perform(get("/api/urls").header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(0));

        mockMvc.perform(get("/api/urls").header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1));
    }

    @Test
    void getReturnsNotFoundForAnotherUsersUrl() throws Exception {
        String owner = registerUser();
        String intruder = registerUser();
        long id = createUrlAndGetId(owner, "https://example.com/private", null);

        mockMvc.perform(get("/api/urls/" + id).header("Authorization", "Bearer " + intruder))
                .andExpect(status().isNotFound());
    }

    @Test
    void updateChangesOnlyProvidedFields() throws Exception {
        String token = registerUser();
        long id = createUrlAndGetId(token, "https://example.com/before", null);

        mockMvc.perform(patch("/api/urls/" + id)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                """
                                {"originalUrl":"https://example.com/after"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.originalUrl").value("https://example.com/after"))
                .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    void updateRejectsAnotherUsersUrl() throws Exception {
        String owner = registerUser();
        String intruder = registerUser();
        long id = createUrlAndGetId(owner, "https://example.com/private", null);

        mockMvc.perform(patch("/api/urls/" + id)
                        .header("Authorization", "Bearer " + intruder)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                """
                                {"originalUrl":"https://evil.example.com"}
                                """))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteRemovesTheUrl() throws Exception {
        String token = registerUser();
        long id = createUrlAndGetId(token, "https://example.com/to-delete", null);

        mockMvc.perform(delete("/api/urls/" + id).header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/urls/" + id).header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteRejectsAnotherUsersUrl() throws Exception {
        String owner = registerUser();
        String intruder = registerUser();
        long id = createUrlAndGetId(owner, "https://example.com/private", null);

        mockMvc.perform(delete("/api/urls/" + id).header("Authorization", "Bearer " + intruder))
                .andExpect(status().isNotFound());
    }

    private String registerUser() throws Exception {
        return AuthTestSupport.registerAndGetToken(mockMvc, AuthTestSupport.uniqueEmail("shorturl-it"), "password123");
    }

    private void createUrl(String token, String originalUrl, String customAlias) throws Exception {
        mockMvc.perform(post("/api/urls")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(originalUrl, customAlias)))
                .andExpect(status().isCreated());
    }

    private long createUrlAndGetId(String token, String originalUrl, String customAlias) throws Exception {
        String body = mockMvc.perform(post("/api/urls")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(originalUrl, customAlias)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        Number id = JsonPath.read(body, "$.id");
        return id.longValue();
    }

    private static String createBody(String originalUrl, String customAlias) {
        if (customAlias == null) {
            return """
                    {"originalUrl":"%s"}
                    """
                    .formatted(originalUrl);
        }
        return """
                {"originalUrl":"%s","customAlias":"%s"}
                """
                .formatted(originalUrl, customAlias);
    }
}
