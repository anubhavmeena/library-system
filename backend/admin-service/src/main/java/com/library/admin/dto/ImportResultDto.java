package com.library.admin.dto;

import lombok.*;

import java.util.List;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class ImportResultDto {
    private int totalRows;
    private int imported;
    private int skipped;
    private List<RowError> errors;

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class RowError {
        private int    row;
        private String name;
        private String phone;
        private String reason;
    }
}
