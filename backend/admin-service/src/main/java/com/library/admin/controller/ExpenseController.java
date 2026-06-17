package com.library.admin.controller;

import com.library.admin.dto.ExpenseDto;
import com.library.admin.dto.SaveExpenseRequest;
import com.library.admin.service.ExpenseService;
import com.library.common.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/admin/expenses")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class ExpenseController {

    private final ExpenseService expenseService;

    @GetMapping
    public ResponseEntity<ApiResponse<ExpenseDto>> getExpense(
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer month) {
        LocalDate now = LocalDate.now();
        int y = (year  != null) ? year  : now.getYear();
        int m = (month != null) ? month : now.getMonthValue();
        return ResponseEntity.ok(ApiResponse.success(expenseService.getExpense(y, m)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<ExpenseDto>> saveExpense(
            @RequestBody SaveExpenseRequest request) {
        return ResponseEntity.ok(ApiResponse.success(expenseService.saveExpense(request)));
    }
}
