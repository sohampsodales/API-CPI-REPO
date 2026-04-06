package runner;

import com.aventstack.extentreports.ExtentReports;
import com.aventstack.extentreports.ExtentTest;
import com.aventstack.extentreports.reporter.ExtentSparkReporter;
import com.opencsv.CSVWriter;
import model.ApiTestCase;

import java.io.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.sodales.GlobalVariableHandler;
import org.sodales.OAuth2Client;
import org.sodales.PropertyLoader;
import org.testng.Assert;

public class ApiTestRunner extends OAuth2Client {

    private static String getCsvFile() {
        return org.sodales.ApiTestRunnerPaths.getCsvReportPath();
    }

    private static String getHtmlFile() {
        return org.sodales.ApiTestRunnerPaths.getHtmlReportPath();
    }

    private static String getExtentHtmlFile() {
        return org.sodales.ApiTestRunnerPaths.getExtentHtmlReportPath();
    }

    private static boolean htmlHeaderWritten = false;
    static ExtentReports extent = new ExtentReports();
    static Boolean responseflag;
    static Boolean codeflag;
    static GlobalVariableHandler variableHandler = new GlobalVariableHandler();

    public static void run(ApiTestCase test) {
        PrintStream originalOut = System.out;

        try {
            validateTestCase(test);

            HttpClient client = HttpClient.newHttpClient();
            URI uri = URI.create(variableHandler.globalvariablevaluereplacer(test.url));

            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder().uri(uri);

            // Authentication
            if (PropertyLoader.loadProperties("Authentication_Type").equalsIgnoreCase("OAUTH")) {
                if (accessToken == null || Instant.now().isAfter(tokenExpiry)) {
                    org.sodales.LogCollector.log("Access token missing or expired. Fetching a new one...");
                    getAccessToken();
                }
                System.setProperty("Authorization", "Bearer " + accessToken);
                requestBuilder.header("Authorization", System.getProperty("Authorization"));
            } else {
                requestBuilder.header("Authorization", PropertyLoader.loadProperties("Authorization"));
            }

            // Set headers from Excel
            if (test.headers != null && !test.headers.trim().isEmpty()) {
                for (String h : test.headers.split(";")) {
                    String[] kv = h.split(":", 2);
                    if (kv.length == 2) {
                        requestBuilder.header(
                                kv[0].trim(),
                                variableHandler.globalvariablevaluereplacer(kv[1].trim())
                        );
                    }
                }
            }

            // Build body dynamically
            HttpRequest.BodyPublisher bodyPublisher = buildBodyPublisher(test, requestBuilder);

            String method = test.method == null ? "GET" : test.method.trim().toUpperCase();

            switch (method) {
                case "POST":
                    requestBuilder.POST(bodyPublisher);
                    break;
                case "PUT":
                    requestBuilder.PUT(bodyPublisher);
                    break;
                case "PATCH":
                    requestBuilder.method("PATCH", bodyPublisher);
                    break;
                case "DELETE":
                    requestBuilder.method("DELETE", bodyPublisher);
                    break;
                case "GET":
                    requestBuilder.GET();
                    break;
                default:
                    throw new IllegalArgumentException("Unsupported HTTP method: " + method);
            }

            HttpRequest request = requestBuilder.build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            // Handle 401
            if (response.statusCode() == 401) {
                System.out.println("Access token expired, attempting refresh...");
                if (refreshToken != null) {
                    refreshAccessToken();
                } else {
                    System.out.println("No refresh token found. Requesting new access token...");
                    getAccessToken();
                }
                run(test);
                return;
            }

            int status = response.statusCode();
            String responseBody = response.body();

            variableHandler.setGlobalVariable(
                    test.globalvariablejsonxmlpath,
                    test.globalvariable,
                    responseBody,
                    test.xmlorjson
            );

            PrintStream out = new PrintStream(
                    new FileOutputStream("src/main/resources/api_debug_report.log", true), true
            );
            System.setOut(out);

            System.out.println("==> " + test.testName);
            System.out.println("Method: " + method);
            System.out.println("URL: " + test.url);
            System.out.println("Body Type: " + safeValue(test.bodyType));
            System.out.println("Status: " + status + " | Expected: " + test.expectedStatus);
            System.out.println("Contains: " + test.expectedResponse + " => " + responseBody.contains(safeValue(test.expectedResponse)));
            System.out.println("----------------------------------");
            System.out.println("Response: " + responseBody);
            System.out.println("----------------------------------");

            writeToCSV(
                    test.url,
                    safeValue(test.headers),
                    safeValue(test.expectedStatus).trim(),
                    status,
                    safeValue(test.expectedResponse),
                    responseBody
            );

            writeToHTML(
                    test.testName,
                    test.url,
                    safeValue(test.headers),
                    safeValue(test.expectedStatus),
                    status,
                    safeValue(test.expectedResponse),
                    responseBody
            );

            extendReport(
                    test.testName,
                    test.url,
                    safeValue(test.headers),
                    safeValue(test.expectedStatus),
                    status,
                    safeValue(test.expectedResponse),
                    responseBody
            );

            System.setOut(originalOut);

        } catch (Exception e) {
            e.printStackTrace();
            System.setOut(originalOut);
            Assert.fail("Test failed due to exception: " + e.getMessage());
        }
    }

    private static void validateTestCase(ApiTestCase test) {
        String method = safeValue(test.method).trim().toUpperCase();
        String bodyType = safeValue(test.bodyType).trim().toUpperCase();

        if (test.url == null || test.url.trim().isEmpty()) {
            throw new IllegalArgumentException("URL is missing for test: " + safeValue(test.testName));
        }

        if (method.isEmpty()) {
            throw new IllegalArgumentException("HTTP method is missing for test: " + safeValue(test.testName));
        }

        if (bodyType.equals("RAW") || bodyType.equals("PAYLOAD")) {
            if (safeValue(test.payload).isEmpty()) {
                System.out.println("Warning: Payload is empty for test: " + safeValue(test.testName));
            }
        }

        if (bodyType.equals("FORM_DATA")) {
            if (safeValue(test.formData).isEmpty()) {
                System.out.println("Warning: FormData is empty for test: " + safeValue(test.testName));
            }
        }

        if (bodyType.equals("BINARY")) {
            if (safeValue(test.filePath).isEmpty()) {
                throw new IllegalArgumentException("Binary file path is missing for test: " + safeValue(test.testName));
            }
        }
    }

    private static HttpRequest.BodyPublisher buildBodyPublisher(ApiTestCase test, HttpRequest.Builder requestBuilder) throws Exception {
        String bodyType = safeValue(test.bodyType).trim().toUpperCase();

        switch (bodyType) {
            case "RAW":
            case "PAYLOAD":
                return HttpRequest.BodyPublishers.ofString(
                        variableHandler.globalvariablevaluereplacer(safeValue(test.payload))
                );

            case "BINARY":
                return HttpRequest.BodyPublishers.ofFile(
                        Path.of(variableHandler.globalvariablevaluereplacer(safeValue(test.filePath)))
                );

            case "FORM_DATA":
                String boundary = "----Boundary" + UUID.randomUUID();
                requestBuilder.header("Content-Type", "multipart/form-data; boundary=" + boundary);

                ParsedFormData parsed = parseFormData(
                        variableHandler.globalvariablevaluereplacer(safeValue(test.formData))
                );

                return buildMultipartBody(parsed.fields, parsed.files, boundary);

            case "NONE":
            case "":
                return HttpRequest.BodyPublishers.noBody();

            default:
                throw new IllegalArgumentException("Unsupported body type: " + bodyType);
        }
    }

    private static ParsedFormData parseFormData(String formDataText) {
        Map<String, String> fields = new HashMap<>();
        Map<String, Path> files = new HashMap<>();

        if (formDataText == null || formDataText.trim().isEmpty()) {
            return new ParsedFormData(fields, files);
        }

        String[] pairs = formDataText.split(";");
        for (String pair : pairs) {
            String[] parts = pair.split(":", 2);
            if (parts.length < 2) {
                continue;
            }

            String key = parts[0].trim();
            String value = parts[1].trim();

            if (value.startsWith("@")) {
                files.put(key, Path.of(value.substring(1)));
            } else {
                fields.put(key, value);
            }
        }

        return new ParsedFormData(fields, files);
    }

    private static HttpRequest.BodyPublisher buildMultipartBody(
            Map<String, String> fields,
            Map<String, Path> files,
            String boundary
    ) throws Exception {

        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        PrintWriter writer = new PrintWriter(new OutputStreamWriter(bos, StandardCharsets.UTF_8), true);

        for (Map.Entry<String, String> entry : fields.entrySet()) {
            writer.append("--").append(boundary).append("\r\n");
            writer.append("Content-Disposition: form-data; name=\"")
                    .append(entry.getKey())
                    .append("\"\r\n");
            writer.append("\r\n");
            writer.append(entry.getValue()).append("\r\n");
            writer.flush();
        }

        for (Map.Entry<String, Path> entry : files.entrySet()) {
            String mimeType = Files.probeContentType(entry.getValue());
            if (mimeType == null) {
                mimeType = "application/octet-stream";
            }

            writer.append("--").append(boundary).append("\r\n");
            writer.append("Content-Disposition: form-data; name=\"")
                    .append(entry.getKey())
                    .append("\"; filename=\"")
                    .append(entry.getValue().getFileName().toString())
                    .append("\"\r\n");
            writer.append("Content-Type: ").append(mimeType).append("\r\n");
            writer.append("\r\n");
            writer.flush();

            Files.copy(entry.getValue(), bos);
            bos.write("\r\n".getBytes(StandardCharsets.UTF_8));
        }

        writer.append("--").append(boundary).append("--").append("\r\n");
        writer.flush();

        return HttpRequest.BodyPublishers.ofByteArray(bos.toByteArray());
    }

    private static class ParsedFormData {
        Map<String, String> fields;
        Map<String, Path> files;

        ParsedFormData(Map<String, String> fields, Map<String, Path> files) {
            this.fields = fields;
            this.files = files;
        }
    }

    private static void writeToCSV(String requestUrl, String requestHeaders, String expectedresponsecode,
                                   int responseCode, String expectedresponsebody, String responseBody) {
        File file = new File(getCsvFile());
        boolean fileExists = file.exists();

        try (CSVWriter writer = new CSVWriter(new FileWriter(getCsvFile(), true))) {
            if (!fileExists) {
                writer.writeNext(new String[]{
                        "Request URL",
                        "Request Headers",
                        "Expected Response Code",
                        "Actual Response Code",
                        "Expected Response Body",
                        "Actual Response Body"
                });
            }
            writer.writeNext(new String[]{
                    requestUrl,
                    requestHeaders,
                    expectedresponsecode,
                    String.valueOf(responseCode),
                    expectedresponsebody,
                    responseBody
            });
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void writeToHTML(String testName, String requestUrl, String requestHeaders,
                                    String expectedresponsecode, int responseCode,
                                    String expectedresponsebody, String responseBody) {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(getHtmlFile(), true))) {
            if (!htmlHeaderWritten) {
                writer.write(
                        "<html><head><style>" +
                                "body { font-family: Arial; padding: 20px; }" +
                                "table { border-collapse: collapse; width: 100%;}" +
                                "th, td { border: 1px solid #ddd; padding: 8px; vertical-align: top; }" +
                                "th { background-color: #f2f2f2; }" +
                                ".pass { background-color: #d4edda; }" +
                                ".fail { background-color: #f8d7da; }" +
                                ".short-text { overflow: hidden; white-space: nowrap; text-overflow: ellipsis; display: block; }" +
                                ".full-text { display: none; white-space: normal; }" +
                                ".toggle-link { color: blue; cursor: pointer; display: inline-block; margin-top: 5px; font-size: 12px; }" +
                                "</style>" +
                                "<script>" +
                                "function toggleText(id) {" +
                                "  var shortText = document.getElementById('short_' + id);" +
                                "  var fullText = document.getElementById('full_' + id);" +
                                "  var toggleLink = document.getElementById('toggle_' + id);" +
                                "  if (shortText.style.display !== 'none') {" +
                                "    shortText.style.display = 'none';" +
                                "    fullText.style.display = 'block';" +
                                "    toggleLink.innerText = 'less';" +
                                "  } else {" +
                                "    shortText.style.display = 'block';" +
                                "    fullText.style.display = 'none';" +
                                "    toggleLink.innerText = 'more';" +
                                "  }" +
                                "}" +
                                "</script></head><body>" +
                                "<h2>API Debug Report</h2><table>"
                );
                writer.write(
                        "<tr>" +
                                "<th>Test Name</th>" +
                                "<th>Request URL</th>" +
                                "<th>Request Headers</th>" +
                                "<th>Expected Response Code</th>" +
                                "<th>Actual Response Code</th>" +
                                "<th>Expected Response Body</th>" +
                                "<th>Actual Response Body</th>" +
                                "</tr>"
                );
                htmlHeaderWritten = true;
            }

            String status;
            String rowClass;

            if (!"NA".equalsIgnoreCase(expectedresponsebody)) {
                status = ((responseCode == Integer.parseInt(expectedresponsecode))
                        && responseBody.contains(expectedresponsebody)) ? "PASS" : "FAIL";
            } else {
                status = (responseCode == Integer.parseInt(expectedresponsecode)) ? "PASS" : "FAIL";
            }

            rowClass = status.equals("PASS") ? "pass" : "fail";

            writer.write("<tr class='" + rowClass + "'>");
            writer.write("<td>" + escapeHTML(testName) + "</td>");
            writer.write("<td>" + escapeHTML(requestUrl) + "</td>");
            writer.write("<td><pre>" + escapeHTML(requestHeaders) + "</pre></td>");
            writer.write("<td>" + escapeHTML(expectedresponsecode) + "</td>");
            writer.write("<td>" + responseCode + "</td>");
            writer.write("<td><pre>" + escapeHTML(expectedresponsebody) + "</pre></td>");
            writer.write("<td><pre>" + escapeHTML(responseBody) + "</pre></td>");
            writer.write("</tr>");
            writer.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static String escapeHTML(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }

    private static void extendReport(String testName, String requestUrl, String requestHeaders,
                                     String expectedresponsecode, int responseCode,
                                     String expectedresponsebody, String responseBody) {
        ExtentSparkReporter spark = new ExtentSparkReporter(getExtentHtmlFile());
        extent.attachReporter(spark);

        ExtentTest test = extent.createTest(testName);
        test.info(requestUrl);
        test.info(requestHeaders);
        test.info(expectedresponsecode);
        test.info(expectedresponsebody);

        if (responseCode == Integer.parseInt(expectedresponsecode)) {
            test.info("Response code matching");
            responseflag = true;
        } else {
            test.info("Response code not matching");
            responseflag = false;
        }

        if (expectedresponsebody.equalsIgnoreCase("NA")) {
            codeflag = true;
        } else {
            if (responseBody.contains(expectedresponsebody)) {
                test.info("Response body matching");
                codeflag = true;
            } else {
                test.info("Response body not matching");
                codeflag = false;
            }
        }

        if (responseflag && codeflag) {
            test.pass("Response Code and Response Body Matched");
        } else {
            test.fail("Either Response Body or Response Code or both of them not Matched");
        }

        test.info("Response Body").info(responseBody);
        extent.flush();
    }

    private static String safeValue(String value) {
        return value == null ? "" : value;
    }
}