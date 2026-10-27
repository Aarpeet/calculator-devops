package com.spe.calculator.service;

import java.math.BigDecimal;
import java.math.MathContext;

import org.springframework.stereotype.Service;

/**
 * Core arithmetic logic. Uses {@link BigDecimal} so decimal inputs are exact
 * (e.g. 0.1 + 0.2 = 0.3, not 0.30000000000000004).
 */
@Service
public class CalculatorService {

    /** Non-terminating quotients (e.g. 10 / 3) are rounded to 34 significant digits. */
    private static final MathContext DIVISION_CONTEXT = MathContext.DECIMAL128;

    /**
     * Operands must be zero or have a magnitude between 1e-100 and 1e100. Without this
     * bound a crafted input such as a=1e999999999 would force the JVM to materialise a
     * billion-digit number when the result is rendered.
     */
    private static final int MAX_EXPONENT = 100;

    public BigDecimal add(BigDecimal a, BigDecimal b) {
        validate(a, b);
        return a.add(b);
    }

    public BigDecimal subtract(BigDecimal a, BigDecimal b) {
        validate(a, b);
        return a.subtract(b);
    }

    public BigDecimal multiply(BigDecimal a, BigDecimal b) {
        validate(a, b);
        return a.multiply(b);
    }

    public BigDecimal divide(BigDecimal a, BigDecimal b) {
        validate(a, b);
        if (b.signum() == 0) {
            throw new ArithmeticException("Division by zero is not allowed");
        }
        return a.divide(b, DIVISION_CONTEXT);
    }

    private static void validate(BigDecimal a, BigDecimal b) {
        requireSupportedRange("a", a);
        requireSupportedRange("b", b);
    }

    private static void requireSupportedRange(String name, BigDecimal value) {
        if (value.signum() == 0) {
            return;
        }
        // value = d.ddd x 10^exponent; long arithmetic avoids int overflow on extreme scales
        long exponent = (long) value.precision() - value.scale() - 1;
        if (Math.abs(exponent) > MAX_EXPONENT) {
            throw new IllegalArgumentException("Operand '" + name
                    + "' is out of range: magnitude must be between 1e-" + MAX_EXPONENT
                    + " and 1e" + MAX_EXPONENT);
        }
    }
}
