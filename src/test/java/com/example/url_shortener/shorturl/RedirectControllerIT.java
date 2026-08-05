package com.example.url_shortener.shorturl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.url_shortener.AbstractIntegrationTest;
import com.example.url_shortener.AuthTestSupport;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class RedirectControllerIT extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Test
    void redirectReturnsFoundWithLocationHeader() throws Exception {
        String token = registerUser();
        String code = createUrlAndGetCode(token, "https://example.com/redirect-test");

        mockMvc.perform(get("/r/" + code))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "https://example.com/redirect-test"));
    }

    @Test
    void redirectPopulatesRedisCacheOnFirstHit() throws Exception {
        String token = registerUser();
        String code = createUrlAndGetCode(token, "https://example.com/cache-test");

        assertThat(redisTemplate.opsForValue().get("shorturl:" + code)).isNull();

        mockMvc.perform(get("/r/" + code)).andExpect(status().isFound());

        String cached = redisTemplate.opsForValue().get("shorturl:" + code);
        assertThat(cached).isNotNull().contains("https://example.com/cache-test");
    }

    @Test
    void redirectIncrementsClickCountOnCacheHitAndMiss() throws Exception {
        String token = registerUser();
        long id = createUrlAndGetId(token, "https://example.com/click-count-test");
        String code = getShortCode(token, id);

        mockMvc.perform(get("/r/" + code)).andExpect(status().isFound());
        mockMvc.perform(get("/r/" + code)).andExpect(status().isFound());

        mockMvc.perform(get("/api/urls/" + id).header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.clickCount").value(2));
    }

    @Test
    void redirectReturnsNotFoundForUnknownCode() throws Exception {
        mockMvc.perform(get("/r/does-not-exist")).andExpect(status().isNotFound());
    }

    @Test
    void redirectReturnsGoneForDeactivatedUrl() throws Exception {
        String token = registerUser();
        long id = createUrlAndGetId(token, "https://example.com/deactivate-test");
        String code = getShortCode(token, id);
        mockMvc.perform(get("/r/" + code)).andExpect(status().isFound());

        mockMvc.perform(patch("/api/urls/" + id)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                """
                                {"active":false}
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(get("/r/" + code)).andExpect(status().isGone());
    }

    @Test
    void updateEvictsCacheAndSubsequentRedirectUsesNewUrl() throws Exception {
        String token = registerUser();
        long id = createUrlAndGetId(token, "https://example.com/original");
        String code = getShortCode(token, id);

        mockMvc.perform(get("/r/" + code)).andExpect(status().isFound());
        assertThat(redisTemplate.opsForValue().get("shorturl:" + code)).isNotNull();

        mockMvc.perform(patch("/api/urls/" + id)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                """
                                {"originalUrl":"https://example.com/updated"}
                                """))
                .andExpect(status().isOk());

        assertThat(redisTemplate.opsForValue().get("shorturl:" + code)).isNull();

        mockMvc.perform(get("/r/" + code))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "https://example.com/updated"));

        assertThat(redisTemplate.opsForValue().get("shorturl:" + code)).contains("https://example.com/updated");
    }

    @Test
    void deleteEvictsCacheAndSubsequentRedirectIsNotFound() throws Exception {
        String token = registerUser();
        long id = createUrlAndGetId(token, "https://example.com/to-delete");
        String code = getShortCode(token, id);

        mockMvc.perform(get("/r/" + code)).andExpect(status().isFound());
        assertThat(redisTemplate.opsForValue().get("shorturl:" + code)).isNotNull();

        mockMvc.perform(delete("/api/urls/" + id).header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        assertThat(redisTemplate.opsForValue().get("shorturl:" + code)).isNull();
        mockMvc.perform(get("/r/" + code)).andExpect(status().isNotFound());
    }

    private String registerUser() throws Exception {
        return AuthTestSupport.registerAndGetToken(mockMvc, AuthTestSupport.uniqueEmail("redirect-it"), "password123");
    }

    private long createUrlAndGetId(String token, String originalUrl) throws Exception {
        String body = mockMvc.perform(post("/api/urls")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(originalUrl)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        Number id = JsonPath.read(body, "$.id");
        return id.longValue();
    }

    private String createUrlAndGetCode(String token, String originalUrl) throws Exception {
        String body = mockMvc.perform(post("/api/urls")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(originalUrl)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return JsonPath.read(body, "$.shortCode");
    }

    private String getShortCode(String token, long id) throws Exception {
        String body = mockMvc.perform(get("/api/urls/" + id).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return JsonPath.read(body, "$.shortCode");
    }

    private static String createBody(String originalUrl) {
        return """
                {"originalUrl":"%s"}
                """
                .formatted(originalUrl);
    }
}
