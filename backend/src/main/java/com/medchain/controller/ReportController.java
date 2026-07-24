package com.medchain.controller;

import com.medchain.model.AccessLog;
import com.medchain.model.User;
import com.medchain.repository.AccessLogRepository;
import com.medchain.repository.MedicalRecordRepository;
import com.medchain.repository.UserRepository;
import com.opencsv.CSVWriter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.io.ByteArrayOutputStream;
import java.io.OutputStreamWriter;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * ReportController — generates PDF and CSV reports for admin users.
 *
 * Reports:
 *   - Access Logs (CSV / PDF)
 *   - User Activity Summary (CSV)
 *
 * Uses Apache PDFBox for PDF generation and OpenCSV for CSV export.
 */
@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
@Tag(name = "Reports", description = "PDF and CSV report generation for admins")
@SecurityRequirement(name = "Bearer Authentication")
public class ReportController {

    private final AccessLogRepository     accessLogRepository;
    private final UserRepository          userRepository;
    private final MedicalRecordRepository recordRepository;

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    // ─── Access Logs — CSV ────────────────────────────────────────────────────

    @GetMapping("/access-logs.csv")
    @Operation(summary = "Download access logs as CSV")
    public ResponseEntity<byte[]> accessLogsCsv() throws Exception {
        List<AccessLog> logs = accessLogRepository.findAll();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (CSVWriter writer = new CSVWriter(new OutputStreamWriter(baos))) {
            // Header
            writer.writeNext(new String[]{
                "Timestamp", "User", "Action", "Record ID", "Patient PID",
                "Risk Level", "Anomaly Score", "Granted", "Flagged", "IP Address", "TX Hash"
            });
            // Rows
            for (AccessLog log : logs) {
                writer.writeNext(new String[]{
                    log.getTimestamp() != null ? log.getTimestamp().format(FMT) : "",
                    log.getUserName()  != null ? log.getUserName() : "",
                    log.getAction()    != null ? log.getAction()   : "",
                    log.getRecordId()  != null ? log.getRecordId() : "",
                    log.getPatientPID() != null ? log.getPatientPID() : "",
                    log.getRiskLevel() != null ? log.getRiskLevel() : "LOW",
                    String.format("%.4f", log.getAnomalyScore()),
                    String.valueOf(log.isGranted()),
                    String.valueOf(log.isFlagged()),
                    log.getIpAddress() != null ? log.getIpAddress() : "",
                    log.getBlockchainTxHash() != null ? log.getBlockchainTxHash() : ""
                });
            }
        }

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"medchain-access-logs.csv\"")
                .contentType(MediaType.parseMediaType("text/csv"))
                .body(baos.toByteArray());
    }

    // ─── Access Logs — PDF ────────────────────────────────────────────────────

    @GetMapping("/access-logs.pdf")
    @Operation(summary = "Download access logs as PDF")
    public ResponseEntity<byte[]> accessLogsPdf() throws Exception {
        List<AccessLog> logs = accessLogRepository.findAll();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (PDDocument document = new PDDocument()) {
            // Title Page
            addPdfPage(document, logs);
            document.save(baos);
        }

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"medchain-access-logs.pdf\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(baos.toByteArray());
    }

    // ─── User Activity — CSV ──────────────────────────────────────────────────

    @GetMapping("/user-activity.csv")
    @Operation(summary = "Download user activity summary as CSV")
    public ResponseEntity<byte[]> userActivityCsv() throws Exception {
        List<User> users = userRepository.findAll();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (CSVWriter writer = new CSVWriter(new OutputStreamWriter(baos))) {
            writer.writeNext(new String[]{
                "Name", "Email", "Role", "Total Actions",
                "Views", "Uploads", "Grants", "Revocations", "Status"
            });

            for (User user : users) {
                List<AccessLog> userLogs = accessLogRepository.findByUserId(user.getId());
                long views      = userLogs.stream().filter(l -> "VIEW".equals(l.getAction())).count();
                long uploads    = userLogs.stream().filter(l -> "UPLOAD".equals(l.getAction())).count();
                long grants     = userLogs.stream().filter(l -> "GRANT".equals(l.getAction())).count();
                long revocations = userLogs.stream().filter(l -> "REVOKE".equals(l.getAction())).count();

                writer.writeNext(new String[]{
                    user.getName()  != null ? user.getName()  : "",
                    user.getEmail() != null ? user.getEmail() : "",
                    user.getRole()  != null ? user.getRole()  : "",
                    String.valueOf(userLogs.size()),
                    String.valueOf(views),
                    String.valueOf(uploads),
                    String.valueOf(grants),
                    String.valueOf(revocations),
                    user.isBlocked() ? "BLOCKED" : (user.isActive() ? "ACTIVE" : "INACTIVE")
                });
            }
        }

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"medchain-user-activity.csv\"")
                .contentType(MediaType.parseMediaType("text/csv"))
                .body(baos.toByteArray());
    }

    // ─── PDF Helper ───────────────────────────────────────────────────────────

    private void addPdfPage(PDDocument document, List<AccessLog> logs) throws Exception {
        PDPage page = new PDPage(PDRectangle.A4);
        document.addPage(page);

        float margin   = 40;
        float yStart   = page.getMediaBox().getHeight() - margin;
        float lineHeight = 14;
        float y        = yStart;
        int   rowCount = 0;

        try (PDPageContentStream content = new PDPageContentStream(document, page)) {
            // Title
            content.beginText();
            content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD), 16);
            content.newLineAtOffset(margin, y);
            content.showText("MedChain — Access Log Report");
            content.endText();
            y -= lineHeight * 2;

            // Subtitle
            content.beginText();
            content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 9);
            content.newLineAtOffset(margin, y);
            content.showText("Generated: " + java.time.LocalDateTime.now().format(FMT)
                             + "   Total Records: " + logs.size());
            content.endText();
            y -= lineHeight * 2;

            // Column headers
            content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD), 8);
            content.beginText();
            content.newLineAtOffset(margin, y);
            content.showText("Timestamp               User               Action    Risk   Score    Granted");
            content.endText();
            y -= lineHeight;

            // Separator line
            content.moveTo(margin, y);
            content.lineTo(page.getMediaBox().getWidth() - margin, y);
            content.stroke();
            y -= lineHeight * 0.5f;

            // Rows
            content.setFont(new PDType1Font(Standard14Fonts.FontName.COURIER), 7);
            for (AccessLog log : logs) {
                if (y < margin + lineHeight) {
                    // Add new page
                    content.close();
                    page = new PDPage(PDRectangle.A4);
                    document.addPage(page);
                    y = yStart;
                    break; // simplified — single-page for demo
                }

                String ts      = log.getTimestamp() != null ? log.getTimestamp().format(FMT) : "—";
                String name    = truncate(log.getUserName(), 15);
                String action  = truncate(log.getAction(), 8);
                String risk    = truncate(log.getRiskLevel() != null ? log.getRiskLevel() : "LOW", 8);
                String score   = String.format("%.3f", log.getAnomalyScore());
                String granted = log.isGranted() ? "YES" : "NO";

                content.beginText();
                content.newLineAtOffset(margin, y);
                content.showText(String.format("%-22s %-16s %-9s %-8s %-8s %s",
                        ts, name, action, risk, score, granted));
                content.endText();
                y -= lineHeight;
                rowCount++;

                if (rowCount > 40) break; // cap at 40 rows for single-page demo PDF
            }
        }
    }

    private String truncate(String s, int maxLen) {
        if (s == null) return "";
        return s.length() <= maxLen ? s : s.substring(0, maxLen - 1) + "…";
    }
}
