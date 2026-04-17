package org.sodales;

import model.AuthContext;
import model.TestRunContext;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@RestController
@CrossOrigin("*")
public class ApiController {

    private static final String BASE_TEMP_DIR =
            System.getProperty("java.io.tmpdir") + File.separator + "api-testing";

    @PostMapping("/run-tests")
    public ResponseEntity<byte[]> runTests(
            @RequestParam("file") MultipartFile file,
            @RequestParam String authType,
            @RequestParam(required = false) String username,
            @RequestParam(required = false) String password,
            @RequestParam(required = false) String clientId,
            @RequestParam(required = false) String clientSecret,
            @RequestParam(required = false) String tokenUrl,
            @RequestParam(required = false) String grantType
    ) {
        try {
            cleanupOldRuns(); // added for cleanup of old reports
            TestRunContext context = createRunContext();
            AuthContext authContext = buildAuthContext(
                    authType, username, password, clientId, clientSecret, tokenUrl, grantType
            );

            LogCollector.clear();
            LogCollector.log("=== /run-tests called ===");
            LogCollector.log("Run ID: " + context.runId);
            LogCollector.log("Authentication Type: " + authType);
            LogCollector.log("Uploaded File: " + (file != null ? file.getOriginalFilename() : "null"));
            LogCollector.log("Run directory: " + context.runDir);

            if (file == null || file.isEmpty()) {
                LogCollector.log("Excel file is missing.");
                return ResponseEntity.badRequest()
                        .body("Excel file is required".getBytes());
            }

            String lowerName = file.getOriginalFilename() == null
                    ? ""
                    : file.getOriginalFilename().toLowerCase();

            if (!lowerName.endsWith(".xlsx") && !lowerName.endsWith(".xls")) {
                LogCollector.log("Invalid file type uploaded.");
                return ResponseEntity.badRequest()
                        .body("Only Excel files are allowed".getBytes());
            }

            LogCollector.log("Validating authentication inputs...");
            validateAuthInputs(authType, username, password, clientId, clientSecret, tokenUrl);

            LogCollector.log("Saving Excel to: " + context.excelPath);
            file.transferTo(new File(context.excelPath));

            LogCollector.log("Starting API test execution...");
            ApiTests.run(context, authContext);

            File htmlReportFile = new File(context.htmlPath);
            File csvReportFile = new File(context.csvPath);
            File extentHtmlReportFile = new File(context.extentPath);
            // File logFile = new File(context.logPath);

            if (!htmlReportFile.exists() && !csvReportFile.exists() && !extentHtmlReportFile.exists()) {
                LogCollector.log("Report files are not generated.");
                return ResponseEntity.internalServerError()
                        .body("Report files are not generated".getBytes());
            }

            LogCollector.log("Preparing ZIP download...");
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ZipOutputStream zipOut = new ZipOutputStream(baos);

            addFileToZip(zipOut, htmlReportFile, "api_debug_report.html");
            addFileToZip(zipOut, csvReportFile, "api_debug_report.csv");
            addFileToZip(zipOut, extentHtmlReportFile, "api_extent_debug_report.html");
            // addFileToZip(zipOut, logFile, "api_debug_report.log");

            zipOut.close();

            LogCollector.log("Execution completed. Report downloaded.");

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + context.runId + "_api-test-results.zip")
                    .header(HttpHeaders.CONTENT_TYPE, "application/zip")
                    .body(baos.toByteArray());

        } catch (Exception e) {
            e.printStackTrace();
            LogCollector.log("Execution failed: " + e.getMessage());
            return ResponseEntity.internalServerError()
                    .body(("Error: " + e.getClass().getName() + " - " + e.getMessage()).getBytes());
        }
    }

    @GetMapping("/logs")
    public ResponseEntity<List<String>> getLogs() {
        return ResponseEntity.ok(LogCollector.getLogs());
    }

    private TestRunContext createRunContext() throws IOException {
        String runId = "run_" + System.currentTimeMillis() + "_" + UUID.randomUUID();
        String runDir = BASE_TEMP_DIR + File.separator + runId;

        File runFolder = new File(runDir);
        if (!runFolder.exists()) {
            runFolder.mkdirs();
        }

        TestRunContext context = new TestRunContext();
        context.runId = runId;
        context.runDir = runDir;
        context.excelPath = runDir + File.separator + "api_test_data.xlsx";
        context.htmlPath = runDir + File.separator + "api_debug_report.html";
        context.extentPath = runDir + File.separator + "api_extent_debug_report.html";
        context.csvPath = runDir + File.separator + "api_debug_report.csv";
        // context.logPath = runDir + File.separator + "api_debug_report.log";

        return context;
    }

    private AuthContext buildAuthContext(String authType,
                                     String username,
                                     String password,
                                     String clientId,
                                     String clientSecret,
                                     String tokenUrl,
                                     String grantType) {

    AuthContext authContext = new AuthContext();
    authContext.authenticationType = authType;

    if ("BASIC".equalsIgnoreCase(authType)) {

        if (isBlank(username) || isBlank(password)) {
            throw new RuntimeException("Username and Password are required for BASIC authentication");
        }

        String encoded = Base64.getEncoder()
                .encodeToString((username + ":" + password).getBytes());

        authContext.authorizationHeader = "Basic " + encoded;

        authContext.username = username;
        authContext.password = password;

    } else {

        if (isBlank(clientId) || isBlank(clientSecret) || isBlank(tokenUrl)) {
            throw new RuntimeException("Token URL, Client ID and Client Secret are required for OAUTH2");
        }

        String encoded = Base64.getEncoder()
                .encodeToString((clientId + ":" + clientSecret).getBytes());

        authContext.authorizationHeader = "Basic " + encoded;
        authContext.tokenUrl = tokenUrl;
        authContext.clientId = clientId;
        authContext.clientSecret = clientSecret;
        authContext.grantType = isBlank(grantType) ? "client_credentials" : grantType;
    }

    return authContext;
}

    private void validateAuthInputs(String authType,
                                String username,
                                String password,
                                String clientId,
                                String clientSecret,
                                String tokenUrl) {

    if ("BASIC".equalsIgnoreCase(authType)) {

        if (isBlank(username) || isBlank(password)) {
            throw new RuntimeException("Username and Password are required for BASIC authentication");
        }

    } else if ("OAUTH2".equalsIgnoreCase(authType) || "OAUTH".equalsIgnoreCase(authType)) {

        if (isBlank(clientId) || isBlank(clientSecret) || isBlank(tokenUrl)) {
            throw new RuntimeException("Token URL, Client ID and Client Secret are required for OAUTH2");
        }

    } else {
        throw new RuntimeException("Unsupported Authentication Type: " + authType);
    }
}

    private void addFileToZip(ZipOutputStream zipOut, File file, String fileName) throws IOException {
        if (file != null && file.exists()) {
            try (FileInputStream fis = new FileInputStream(file)) {
                zipOut.putNextEntry(new ZipEntry(fileName));
                byte[] buffer = new byte[1024];
                int length;
                while ((length = fis.read(buffer)) > 0) {
                    zipOut.write(buffer, 0, length);
                }
                zipOut.closeEntry();
            }
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private String safeValue(String value) {
        return value == null ? "" : value;
    }

    // Added this part for cleanup of old reports 
    private void cleanupOldRuns() {
    File baseDir = new File(BASE_TEMP_DIR);
    if (!baseDir.exists()) return;

    File[] files = baseDir.listFiles();
    if (files == null) return;

    long cutoff = System.currentTimeMillis() - (24 * 60 * 60 * 1000);

    for (File file : files) {
        if (file.isDirectory() && file.lastModified() < cutoff) {
            deleteDirectory(file);
        }
    }
}

private void deleteDirectory(File dir) {
    File[] files = dir.listFiles();
    if (files != null) {
        for (File f : files) {
            if (f.isDirectory()) {
                deleteDirectory(f);
            } else {
                f.delete();
            }
        }
    }
    dir.delete();
}
}