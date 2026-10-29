package com.spe.calculator.controller;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Full-context integration tests: real controller, service, exception handler,
 * Jackson configuration and Actuator, exercised through MockMvc.
 */
@SpringBootTest
@AutoConfigureMockMvc
class CalculatorControllerIntegrationTest {

    private static final String ISO_INSTANT = "\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(\\.\\d+)?Z";

    @Autowired
    private MockMvc mockMvc;

    private ResultActions call(String operation, String a, String b) throws Exception {
        return mockMvc.perform(get("/api/v1/" + operation).param("a", a).param("b", b));
    }

    private static void assertBadRequest(ResultActions result, String expectedMessageFragment) throws Exception {
        result.andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.error", containsString(expectedMessageFragment)))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.timestamp", matchesPattern(ISO_INSTANT)));
    }

    @Test
    @DisplayName("GET /api/v1/add returns the exact spec JSON with clean numbers")
    void add() throws Exception {
        call("add", "2", "3")
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(content().string("{\"operation\":\"add\",\"a\":2,\"b\":3,\"result\":5}"));
    }

    @Test
    @DisplayName("GET /api/v1/subtract returns a - b")
    void subtract() throws Exception {
        call("subtract", "10", "5")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.operation").value("subtract"))
                .andExpect(jsonPath("$.a").value(10))
                .andExpect(jsonPath("$.b").value(5))
                .andExpect(jsonPath("$.result").value(5));
    }

    @Test
    @DisplayName("GET /api/v1/multiply returns a * b")
    void multiply() throws Exception {
        call("multiply", "10", "5")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.operation").value("multiply"))
                .andExpect(jsonPath("$.result").value(50));
    }

    @Test
    @DisplayName("GET /api/v1/divide returns a decimal quotient without trailing zeros")
    void divide() throws Exception {
        call("divide", "10", "4")
                .andExpect(status().isOk())
                .andExpect(content().string("{\"operation\":\"divide\",\"a\":10,\"b\":4,\"result\":2.5}"));
    }

    @Test
    @DisplayName("negative and decimal inputs are handled exactly")
    void negativeAndDecimalInputs() throws Exception {
        call("add", "0.1", "0.2")
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("\"result\":0.3}")));
        call("multiply", "-4", "2.5")
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("\"result\":-10}")));
    }

    @Test
    @DisplayName("very small results are rendered in plain notation, not 1E-7")
    void smallResultPlainNotation() throws Exception {
        call("divide", "1", "10000000")
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("\"result\":0.0000001}")));
    }

    @Test
    @DisplayName("GET /actuator/health reports UP")
    void health() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    @DisplayName("GET /actuator/info exposes the app name, build number and Maven version")
    void info() throws Exception {
        mockMvc.perform(get("/actuator/info"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.app.name").value("calculator-api"))
                .andExpect(jsonPath("$.app.build").value("local"))
                .andExpect(jsonPath("$.build.version").value("1.0.0"));
    }

    @Test
    @DisplayName("division by zero returns HTTP 400 with error JSON")
    void divideByZero() throws Exception {
        assertBadRequest(call("divide", "10", "0"), "Division by zero is not allowed");
    }

    @ParameterizedTest(name = "non-numeric input to /{0} returns 400")
    @ValueSource(strings = {"add", "subtract", "multiply", "divide"})
    @DisplayName("non-numeric parameter returns HTTP 400 with error JSON")
    void nonNumericParameter(String operation) throws Exception {
        assertBadRequest(call(operation, "abc", "5"), "Invalid value 'abc' for query parameter 'a'");
    }

    @Test
    @DisplayName("missing parameter returns HTTP 400 with error JSON")
    void missingParameter() throws Exception {
        assertBadRequest(mockMvc.perform(get("/api/v1/add").param("a", "2")),
                "Required query parameter 'b' is missing or empty");
    }

    @Test
    @DisplayName("empty parameter returns HTTP 400 with error JSON")
    void emptyParameter() throws Exception {
        assertBadRequest(call("subtract", "", "3"), "Required query parameter 'a' is missing or empty");
    }

    @Test
    @DisplayName("out-of-range operand returns HTTP 400 with error JSON")
    void outOfRangeOperand() throws Exception {
        assertBadRequest(call("add", "1e999999999", "1"), "Operand 'a' is out of range");
    }
}
