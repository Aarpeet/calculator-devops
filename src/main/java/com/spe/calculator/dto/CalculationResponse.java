package com.spe.calculator.dto;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

/**
 * Successful calculation payload, e.g. {"operation":"add","a":2,"b":3,"result":5}.
 */
@JsonPropertyOrder({"operation", "a", "b", "result"})
public record CalculationResponse(String operation, BigDecimal a, BigDecimal b, BigDecimal result) {

    public static CalculationResponse of(String operation, BigDecimal a, BigDecimal b, BigDecimal result) {
        return new CalculationResponse(operation, clean(a), clean(b), clean(result));
    }

    /**
     * Normalises a number for display: 5.00 -> 5, 2.50 -> 2.5, 1E+2 -> 100.
     */
    private static BigDecimal clean(BigDecimal value) {
        BigDecimal stripped = value.stripTrailingZeros();
        return stripped.scale() < 0 ? stripped.setScale(0) : stripped;
    }
}
