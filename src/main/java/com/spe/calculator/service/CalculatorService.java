package com.spe.calculator.service;

import java.math.BigDecimal;

import org.springframework.stereotype.Service;

/**
 * Core arithmetic logic. Uses {@link BigDecimal} so decimal inputs are exact
 * (e.g. 0.1 + 0.2 = 0.3, not 0.30000000000000004).
 */
@Service
public class CalculatorService {

    public BigDecimal add(BigDecimal a, BigDecimal b) {
        return a.add(b);
    }

    public BigDecimal subtract(BigDecimal a, BigDecimal b) {
        return a.subtract(b);
    }
}
