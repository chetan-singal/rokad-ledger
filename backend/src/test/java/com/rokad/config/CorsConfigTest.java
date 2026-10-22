package com.rokad.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CorsConfigTest {

  private static final String VITE_ORIGIN = "http://localhost:5173";

  @Autowired private MockMvc mockMvc;

  private ResultActions preflight(String origin, String method, String path) throws Exception {
    return mockMvc.perform(
        options(path)
            .header("Origin", origin)
            .header("Access-Control-Request-Method", method)
            .header("Access-Control-Request-Headers", "content-type"));
  }

  @Test
  void preflightFromTheViteDevOriginIsAllowedForEveryMethodTheApiUses() throws Exception {
    String[][] calls = {
      {"GET", "/api/transactions"},
      {"POST", "/api/transactions"},
      {"PUT", "/api/transactions/1"},
      {"DELETE", "/api/transactions/1"},
      {"POST", "/api/ledger/setup"}
    };
    for (String[] call : calls) {
      preflight(VITE_ORIGIN, call[0], call[1])
          .andExpect(status().isOk())
          .andExpect(header().string("Access-Control-Allow-Origin", VITE_ORIGIN));
    }
  }

  @Test
  void preflightFromAnyOtherOriginIsRejected() throws Exception {
    preflight("http://example.invalid", "POST", "/api/transactions")
        .andExpect(status().isForbidden());
  }
}
