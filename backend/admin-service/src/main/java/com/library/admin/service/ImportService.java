package com.library.admin.service;

import com.library.admin.dto.CreateCashMembershipRequest;
import com.library.admin.dto.ImportResultDto;
import com.library.admin.entity.Plan;
import com.library.admin.entity.User;
import com.library.admin.repository.PlanRepository;
import com.library.admin.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class ImportService {

    private final UserRepository          userRepository;
    private final PlanRepository          planRepository;
    private final AdminMembershipService  membershipService;

    private static final List<DateTimeFormatter> DATE_FORMATS = List.of(
            DateTimeFormatter.ofPattern("dd-MM-yyyy"),
            DateTimeFormatter.ofPattern("d-M-yyyy"),
            DateTimeFormatter.ofPattern("dd/MM/yyyy"),
            DateTimeFormatter.ofPattern("d/M/yyyy"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd"),
            DateTimeFormatter.ofPattern("MM/dd/yyyy"),
            DateTimeFormatter.ofPattern("dd/MM/yy"),
            DateTimeFormatter.ofPattern("d/M/yy")
    );

    public ImportResultDto importStudents(MultipartFile file) throws Exception {
        String filename = Objects.requireNonNullElse(file.getOriginalFilename(), "").toLowerCase();
        List<String[]> rows = filename.endsWith(".xlsx") || filename.endsWith(".xls")
                ? parseExcel(file)
                : parseCsv(file);

        // Skip header row
        int imported = 0;
        List<ImportResultDto.RowError> errors = new ArrayList<>();
        int totalRows = Math.max(0, rows.size() - 1);

        List<Plan> activePlans = planRepository.findAll().stream()
                .filter(p -> Boolean.TRUE.equals(p.getIsActive()))
                .toList();

        for (int i = 1; i < rows.size(); i++) {
            String[] cols = rows.get(i);
            int rowNum = i + 1;
            String name  = safeGet(cols, 1);
            String phone = safeGet(cols, 2);

            if (name.isBlank() && phone.isBlank()) {
                totalRows--;   // ignore empty trailing rows
                continue;
            }

            try {
                processRow(cols, activePlans);
                imported++;
            } catch (Exception e) {
                log.warn("Import row {}: {}", rowNum, e.getMessage());
                errors.add(ImportResultDto.RowError.builder()
                        .row(rowNum).name(name).phone(phone).reason(e.getMessage())
                        .build());
            }
        }

        return ImportResultDto.builder()
                .totalRows(totalRows)
                .imported(imported)
                .skipped(totalRows - imported)
                .errors(errors)
                .build();
    }

    private void processRow(String[] cols, List<Plan> activePlans) {
        String name       = safeGet(cols, 1);
        String phone      = safeGet(cols, 2).replaceAll("[^0-9]", "");
        String feesRaw    = safeGet(cols, 3).replaceAll("[^0-9.]", "");
        String dateRaw    = safeGet(cols, 4);
        String seatNumber = safeGet(cols, 5).toUpperCase().replaceAll("[\\s-]", "");

        if (name.isBlank())  throw new IllegalArgumentException("Name is blank");
        if (phone.isBlank()) throw new IllegalArgumentException("Phone is blank");
        if (seatNumber.isBlank()) throw new IllegalArgumentException("Seat is blank");

        BigDecimal fees = feesRaw.isBlank() ? BigDecimal.ZERO : new BigDecimal(feesRaw);
        LocalDate  startDate = parseDate(dateRaw);

        // Find or create user
        User user = userRepository.findByMobile(phone).orElseGet(() -> {
            User u = User.builder()
                    .id(UUID.randomUUID())
                    .name(name)
                    .mobile(phone)
                    .role(User.Role.STUDENT)
                    .isActive(true)
                    .createdAt(LocalDateTime.now())
                    .build();
            return userRepository.save(u);
        });

        // Match plan by closest price
        if (activePlans.isEmpty()) throw new IllegalArgumentException("No active plans configured");
        Plan plan = activePlans.stream()
                .min(Comparator.comparingDouble(p ->
                        Math.abs(p.getPrice().subtract(fees).doubleValue())))
                .orElseThrow();

        String shift = plan.getPlanType() == Plan.PlanType.FULL_DAY ? "FULL_DAY" : "MORNING";

        CreateCashMembershipRequest req = new CreateCashMembershipRequest();
        req.setStudentId(user.getId().toString());
        req.setPlanId(plan.getId().toString());
        req.setShift(shift);
        req.setSeatNumber(seatNumber);
        req.setStartDate(startDate.toString());

        membershipService.createCashMembership(req);
    }

    // ── Parsing helpers ───────────────────────────────────────────────────────

    private List<String[]> parseCsv(MultipartFile file) throws Exception {
        List<String[]> rows = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(file.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.isBlank()) rows.add(splitCsvLine(line));
            }
        }
        return rows;
    }

    private String[] splitCsvLine(String line) {
        // handles quoted fields containing commas
        List<String> fields = new ArrayList<>();
        boolean inQuotes = false;
        StringBuilder sb = new StringBuilder();
        for (char c : line.toCharArray()) {
            if (c == '"') { inQuotes = !inQuotes; }
            else if (c == ',' && !inQuotes) { fields.add(sb.toString().trim()); sb.setLength(0); }
            else { sb.append(c); }
        }
        fields.add(sb.toString().trim());
        return fields.toArray(new String[0]);
    }

    private List<String[]> parseExcel(MultipartFile file) throws Exception {
        List<String[]> rows = new ArrayList<>();
        try (Workbook wb = new XSSFWorkbook(file.getInputStream())) {
            Sheet sheet = wb.getSheetAt(0);
            DataFormatter formatter = new DataFormatter();
            for (Row row : sheet) {
                int lastCol = row.getLastCellNum();
                if (lastCol < 0) continue;
                String[] cells = new String[lastCol];
                for (int i = 0; i < lastCol; i++) {
                    Cell cell = row.getCell(i);
                    cells[i] = (cell != null) ? formatter.formatCellValue(cell).trim() : "";
                }
                rows.add(cells);
            }
        }
        return rows;
    }

    private LocalDate parseDate(String raw) {
        if (raw == null || raw.isBlank()) return LocalDate.now();
        for (DateTimeFormatter fmt : DATE_FORMATS) {
            try { return LocalDate.parse(raw.trim(), fmt); } catch (DateTimeParseException ignored) {}
        }
        throw new IllegalArgumentException("Cannot parse date: " + raw);
    }

    private String safeGet(String[] arr, int idx) {
        return (arr != null && idx < arr.length && arr[idx] != null) ? arr[idx].trim() : "";
    }
}
