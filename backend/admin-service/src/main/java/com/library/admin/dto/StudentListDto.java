package com.library.admin.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import java.util.List;

@Data
@AllArgsConstructor
public class StudentListDto {
    private List<StudentDto> students;
    private long total;
}
