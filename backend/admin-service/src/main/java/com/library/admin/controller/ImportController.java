package com.library.admin.controller;

import com.library.admin.dto.ImportResultDto;
import com.library.admin.service.ImportService;
import com.library.common.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/admin/students")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class ImportController {

    private final ImportService importService;

    @PostMapping("/import")
    public ResponseEntity<ApiResponse<ImportResultDto>> importStudents(
            @RequestParam("file") MultipartFile file) throws Exception {
        return ResponseEntity.ok(ApiResponse.success(importService.importStudents(file)));
    }
}
