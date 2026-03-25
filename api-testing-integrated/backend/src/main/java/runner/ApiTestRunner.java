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
import java.time.Instant;

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
    static GlobalVariableHandler variableHandler=new GlobalVariableHandler();

    // This method is used to run create and run the API test.
    // Authentication is handled based on the authentication property mentioned in the C:\Users\SohamPatel\Documents\Code\apiautomationjava\src\main\resources\framework.properties.
    public static void run(ApiTestCase test) {
        PrintStream originalOut = System.out;
        try {
            HttpClient client = HttpClient.newHttpClient();
            // Prepare URI and method
            URI uri = URI.create(test.url);
            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                    .uri(uri)
                    .method(test.method.toUpperCase(),
                            (test.method.equalsIgnoreCase("POST") || test.method.equalsIgnoreCase("PUT") || test.method.equalsIgnoreCase("DELETE"))
                                    ? HttpRequest.BodyPublishers.ofString(variableHandler.globalvariablevaluereplacer(test.payload))
                                    : HttpRequest.BodyPublishers.noBody());
            if (PropertyLoader.loadProperties("Authentication_Type").equalsIgnoreCase("OAUTH")) {
                if (accessToken == null || Instant.now().isAfter(tokenExpiry)) {
                    System.out.println("Access token missing or expired. Fetching a new one...");
                    getAccessToken();
                }
                System.setProperty("Authorization", "Bearer " + accessToken);
                requestBuilder.header("Authorization", System.getProperty("Authorization"));
            } else {
                requestBuilder.header("Authorization", PropertyLoader.loadProperties("Authorization"));
            }
            // Set headers
            if (test.headers != null && !test.headers.trim().isEmpty()) {
                for (String h : test.headers.split(";")) {
                    String[] kv = h.split(":");
                    if (kv.length == 2) {
                        requestBuilder.header(kv[0].trim(), kv[1].trim());
                    }
                }
            }
            // Send request and get response
            HttpRequest request = requestBuilder.build();
            String requeststructure=request.toString();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            // Handle unauthorized (expired token)
            if (response.statusCode() == 401) {
                System.out.println("Access token expired, attempting refresh...");
                if (refreshToken != null) {
                    refreshAccessToken();
                } else {
                    System.out.println("No refresh token found. Requesting new access token...");
                    getAccessToken();
                }

                // Retry the API call after refresh
                run(test);
            }
            int status = response.statusCode();
            String responseBody = response.body();
            variableHandler.setGlobalVariable(test.globalvariablejsonxmlpath, test.globalvariable, responseBody,test.xmlorjson);

            PrintStream out = new PrintStream(
                    new FileOutputStream("src/main/resources/api_debug_report.log", true), true);
            System.setOut(out);
            System.out.println("==> " + test.testName);
            System.out.println("Status: " + status + " | Expected: " + test.expectedStatus);
            System.out.println("Contains: " + test.expectedResponse + " => " + responseBody.contains(test.expectedResponse));
            System.out.println("----------------------------------");
            System.out.println("----------------------------------");
            System.out.println("Response: " + responseBody);
            System.out.println("----------------------------------");
            writeToCSV(test.url, test.headers.toString(), test.expectedStatus.trim(), status, test.expectedResponse, responseBody);
            writeToHTML(test.testName, test.url, test.headers.toString(), test.expectedStatus, status, test.expectedResponse, responseBody);
            extendReport(test.testName, test.url, test.headers.toString(), test.expectedStatus, status, test.expectedResponse, responseBody);
            System.setOut(originalOut);
        } catch (Exception e) {
            e.printStackTrace();
            Assert.fail("Test failed due to exception: " + e.getMessage());
            System.setOut(originalOut);
        }
    }

    // This method used to create and write the html report.
    // Accepts the parameters which needs to be in the report.
    private static void writeToCSV(String requestUrl, String requestHeaders, String expectedresponsecode, int responseCode, String expectedresponsebody, String responseBody) {
        File file = new File(getCsvFile());
        boolean fileExists = file.exists();
        try (CSVWriter writer = new CSVWriter(new FileWriter(getCsvFile(), true))) {
            if (!fileExists) {
                writer.writeNext(new String[]{"Request URL", "Request Headers", "Expected Response Code", "Actual Response Code", "Expected Response Body", "Actual Response Body"});
            }
            writer.writeNext(new String[]{requestUrl, requestHeaders, expectedresponsecode, String.valueOf(responseCode), expectedresponsebody, responseBody});
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // This method used to create and write the html report.
    // Accepts the parameters which needs to be in the report and also mark the table rows red for failure and green for pass.
    private static void writeToHTML(String testName, String requestUrl, String requestHeaders,
                                    String expectedresponsecode, int responseCode, String expectedresponsebody, String responseBody) {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(getHtmlFile(), true))) {
    File htmlFile = new File(getHtmlFile());
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
            // Define assertion logic
            String status = "";
            String rowClass = "";
            if (expectedresponsebody != "NA") {
                status = ((responseCode == Integer.parseInt(expectedresponsecode)) && (responseBody.contains(expectedresponsebody))) ? "PASS" : "FAIL";
                rowClass = status.equals("PASS") ? "pass" : "fail";
            } else {
                status = responseCode == Integer.parseInt(expectedresponsecode) ? "PASS" : "FAIL";
                rowClass = status.equals("PASS") ? "pass" : "fail";
            }
            writer.write("<tr class='" + rowClass + "'>");
            writer.write("<td>" + escapeHTML(testName) + "</td>");
            writer.write("<td>" + escapeHTML(requestUrl) + "</td>");
            writer.write("<td><pre>" + escapeHTML(requestHeaders) + "</pre></td>");
            writer.write("<td>" + expectedresponsecode + "</td>");
            writer.write("<td>" + responseCode + "</td>");
            writer.write("<td><pre>" + escapeHTML(expectedresponsebody) + "</pre></td>");
            writer.write("<td><pre>" + escapeHTML(responseBody) + "</pre></td>");
            writer.write("</tr>");
            writer.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // This method is used to format the HTML report generated.
    private static String escapeHTML(String text) {
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }

    // This method is used to create and write the extent report.
    // This method accepts the parameters to print in the report.
    private static void extendReport(String testName, String requestUrl, String requestHeaders,
                                     String expectedresponsecode, int responseCode, String expectedresponsebody, String responseBody) {
        ExtentSparkReporter spark = new ExtentSparkReporter(getExtentHtmlFile());
        extent.attachReporter(spark);
        ExtentTest test = extent.createTest(testName);
        test.info(requestUrl);
        test.info(requestHeaders);
        test.info(expectedresponsecode);
        test.info(expectedresponsebody);
        if (responseCode == Integer.parseInt(expectedresponsecode)) {
            //test.pass("Response code matching");
            test.info("Response code matching");
            responseflag = true;
        } else {
            //test.fail("Response code matching");
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
}