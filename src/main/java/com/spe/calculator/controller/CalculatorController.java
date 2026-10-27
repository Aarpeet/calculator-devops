package com.spe.calculator.controller;

import java.math.BigDecimal;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.spe.calculator.dto.CalculationResponse;
import com.spe.calculator.service.CalculatorService;

/**
 * REST endpoints under /api/v1. Query parameters are bound straight to
 * BigDecimal, so non-numeric input is rejected by Spring's type conversion.
 */
@RestController
@RequestMapping("/api/v1")
public class CalculatorController {

    private final CalculatorService calculatorService;

    public CalculatorController(CalculatorService calculatorService) {
        this.calculatorService = calculatorService;
    }

    @GetMapping("/add")
    public CalculationResponse add(@RequestParam BigDecimal a, @RequestParam BigDecimal b) {
        return CalculationResponse.of("add", a, b, calculatorService.add(a, b));
    }

    @GetMapping("/subtract")
    public CalculationResponse subtract(@RequestParam BigDecimal a, @RequestParam BigDecimal b) {
        return CalculationResponse.of("subtract", a, b, calculatorService.subtract(a, b));
    }
}
