package org.sodales;

public class ApiTestRunnerPaths {

    private static String htmlReportPath =
            System.getProperty("java.io.tmpdir") + "/api-testing/api_debug_report.html";

    private static String csvReportPath =
            System.getProperty("java.io.tmpdir") + "/api-testing/api_debug_report.csv";

    private static String extentHtmlReportPath =
            System.getProperty("java.io.tmpdir") + "/api-testing/api_extent_debug_report.html";

    public static String getHtmlReportPath() {
        return htmlReportPath;
    }

    public static void setHtmlReportPath(String htmlReportPath) {
        if (htmlReportPath != null && !htmlReportPath.isBlank()) {
            ApiTestRunnerPaths.htmlReportPath = htmlReportPath;
        }
    }

    public static String getCsvReportPath() {
        return csvReportPath;
    }

    public static void setCsvReportPath(String csvReportPath) {
        if (csvReportPath != null && !csvReportPath.isBlank()) {
            ApiTestRunnerPaths.csvReportPath = csvReportPath;
        }
    }

    public static String getExtentHtmlReportPath() {
        return extentHtmlReportPath;
    }

    public static void setExtentHtmlReportPath(String extentHtmlReportPath) {
        if (extentHtmlReportPath != null && !extentHtmlReportPath.isBlank()) {
            ApiTestRunnerPaths.extentHtmlReportPath = extentHtmlReportPath;
        }
    }
}