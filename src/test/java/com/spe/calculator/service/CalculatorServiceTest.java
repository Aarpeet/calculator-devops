package com.spe.calculator.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Pure unit tests for {@link CalculatorService} (no Spring context).
 */
class CalculatorServiceTest {

    private CalculatorService service;

    @BeforeEach
    void setUp() {
        service = new CalculatorService();
    }

    private static BigDecimal bd(String value) {
        return new BigDecimal(value);
    }

    @ParameterizedTest(name = "{0} + {1} = {2}")
    @CsvSource({
            "2, 3, 5",
            "-2, -3, -5",
            "-2, 3, 1",
            "0.1, 0.2, 0.3",
            "2.5, 2.5, 5",
            "0, 0, 0"
    })
    @DisplayName("add: positives, negatives and exact decimals")
    void add(String a, String b, String expected) {
        assertThat(service.add(bd(a), bd(b))).isEqualByComparingTo(expected);
    }

    @ParameterizedTest(name = "{0} - {1} = {2}")
    @CsvSource({
            "10, 5, 5",
            "5, 10, -5",
            "-3, -3, 0",
            "0.3, 0.1, 0.2",
            "-1.5, 2.25, -3.75"
    })
    @DisplayName("subtract: positives, negatives and exact decimals")
    void subtract(String a, String b, String expected) {
        assertThat(service.subtract(bd(a), bd(b))).isEqualByComparingTo(expected);
    }

    @ParameterizedTest(name = "{0} * {1} = {2}")
    @CsvSource({
            "10, 5, 50",
            "-4, 2.5, -10",
            "-3, -3, 9",
            "1.5, 1.5, 2.25",
            "123, 0, 0"
    })
    @DisplayName("multiply: positives, negatives, decimals and zero")
    void multiply(String a, String b, String expected) {
        assertThat(service.multiply(bd(a), bd(b))).isEqualByComparingTo(expected);
    }

    @ParameterizedTest(name = "{0} / {1} = {2}")
    @CsvSource({
            "10, 5, 2",
            "10, 4, 2.5",
            "-9, 3, -3",
            "-1, -4, 0.25",
            "1, 8, 0.125",
            "0, 7, 0"
    })
    @DisplayName("divide: exact quotients, negatives and decimals")
    void divide(String a, String b, String expected) {
        assertThat(service.divide(bd(a), bd(b))).isEqualByComparingTo(expected);
    }

    @Test
    @DisplayName("divide: non-terminating quotient is rounded to 34 significant digits")
    void divideNonTerminating() {
        assertThat(service.divide(bd("10"), bd("3")))
                .isEqualTo(bd("3.333333333333333333333333333333333"));
    }

    @ParameterizedTest(name = "divide by {0} throws ArithmeticException")
    @ValueSource(strings = {"0", "0.0", "-0", "0E+5"})
    @DisplayName("divide: division by zero throws ArithmeticException")
    void divideByZeroThrows(String zero) {
        assertThatThrownBy(() -> service.divide(bd("10"), bd(zero)))
                .isInstanceOf(ArithmeticException.class)
                .hasMessage("Division by zero is not allowed");
    }

    @Test
    @DisplayName("operands at the 1e100 / 1e-100 boundary are accepted")
    void boundaryOperandsAccepted() {
        assertThat(service.multiply(bd("1e100"), bd("1e-100"))).isEqualByComparingTo("1");
    }

    @ParameterizedTest(name = "a = {0} is rejected")
    @ValueSource(strings = {"1e101", "-1e101", "1e-101", "1e999999999"})
    @DisplayName("out-of-range operands throw IllegalArgumentException")
    void outOfRangeOperandRejected(String huge) {
        assertThatThrownBy(() -> service.add(bd(huge), bd("1")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Operand 'a' is out of range");
    }

    @Test
    @DisplayName("out-of-range divisor is rejected before the zero check")
    void outOfRangeDivisorRejected() {
        assertThatThrownBy(() -> service.divide(bd("1"), bd("1e-200")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Operand 'b' is out of range");
    }
}
