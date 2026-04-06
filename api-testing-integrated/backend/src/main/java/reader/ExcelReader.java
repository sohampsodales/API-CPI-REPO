package reader;

import model.ApiTestCase;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.FileInputStream;
import java.util.*;

public class ExcelReader {

    public static List<ApiTestCase> readTestCases(String path) {
        List<ApiTestCase> cases = new ArrayList<>();

        try (Workbook workbook = new XSSFWorkbook(new FileInputStream(path))) {

            Sheet sheet = workbook.getSheetAt(0);
            Iterator<Row> rows = sheet.iterator();

            DataFormatter formatter = new DataFormatter();

            rows.next(); // skip header

            while (rows.hasNext()) {
                Row row = rows.next();

                // Skip empty rows
                if (row == null || isRowEmpty(row, formatter)) {
                    continue;
                }

                ApiTestCase tc = new ApiTestCase();

                // OLD FIELDS (kept same order)
                tc.testName = getCellValue(row, 0, formatter);
                tc.method = getCellValue(row, 1, formatter);
                tc.url = getCellValue(row, 2, formatter);
                tc.headers = getCellValue(row, 3, formatter);

                // NEW BODY HANDLING (IMPORTANT)
                tc.bodyType = getCellValue(row, 4, formatter);   // NEW
                tc.payload = getCellValue(row, 5, formatter);    // shifted
                tc.formData = getCellValue(row, 6, formatter);   // NEW
                tc.filePath = getCellValue(row, 7, formatter);   // NEW

                // VALIDATION FIELDS
                tc.expectedStatus = getCellValue(row, 8, formatter);
                tc.expectedResponse = getCellValue(row, 9, formatter);
                tc.skip = getCellValue(row, 10, formatter);

                // GLOBAL VARIABLE HANDLING
                tc.globalvariablejsonxmlpath = getCellValue(row, 11, formatter);
                tc.globalvariable = getCellValue(row, 12, formatter);
                tc.xmlorjson = getCellValue(row, 13, formatter);

                cases.add(tc);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        return cases;
    }

    // SAFE CELL READER (no crash)
    private static String getCellValue(Row row, int cellIndex, DataFormatter formatter) {
        Cell cell = row.getCell(cellIndex, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
        if (cell == null) {
            return "";
        }
        return formatter.formatCellValue(cell).trim();
    }

    // SKIP EMPTY ROWS
    private static boolean isRowEmpty(Row row, DataFormatter formatter) {
        for (int i = 0; i < row.getLastCellNum(); i++) {
            String value = getCellValue(row, i, formatter);
            if (value != null && !value.trim().isEmpty()) {
                return false;
            }
        }
        return true;
    }
}