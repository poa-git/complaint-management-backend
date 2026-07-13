package com.system.complaints.service;

import com.system.complaints.dto.ExcelReportRequest;
import com.system.complaints.model.ComplaintLog;
import com.system.complaints.repository.ComplaintHistoryRepository;
import com.system.complaints.repository.ComplaintLogRepository;
import com.system.complaints.repository.HardwareLogRepository;
import com.system.complaints.repository.RemarksUpdateRepository;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.sql.Date;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ExcelReportServiceTest {

    @Test
    void reportColumnsMatchLegacyOrder() throws Exception {
        ExcelReportService service = new ExcelReportService();

        assertIterableEquals(List.of(
                "Serial No.", "Ageing", "Date", "Bank Name", "Branch Code", "Branch Name", "City",
                "Reference Number", "Details", "Equipment Description", "Remarks", "Engineer Names",
                "Repeat Complaint", "Status", "Courier Status", "Last Action Date", "Complaint ID"
        ), headers(service, "Open", "standard"));

        assertIterableEquals(List.of(
                "Serial No.", "Ageing", "Date", "Bank Name", "Branch Code", "Branch Name", "City",
                "Reference Number", "Details", "Equipment Description", "Remarks", "Engineer Names",
                "Repeat Complaint", "Status", "Courier Status", "Quotation Date", "Last Action Date",
                "Closed Date", "Complaint ID"
        ), headers(service, "Overall", "standard"));

        assertIterableEquals(List.of(
                "Serial No.", "Date", "Ageing", "Bank Name", "Branch Code", "Branch Name", "Remarks", "Status"
        ), headers(service, "Open", "ageing"));

        assertIterableEquals(List.of(
                "Serial No.", "Date", "Ageing", "Bank Name", "Branch Code", "Branch Name", "Latest Remarks", "Status"
        ), headers(service, "Open", "untouched"));
    }

    @Test
    void standardOpenReportMatchesLegacyTemplate() throws Exception {
        ExcelReportService service = new ExcelReportService();
        ComplaintLogRepository complaintRepository = mock(ComplaintLogRepository.class);
        HardwareLogRepository hardwareRepository = mock(HardwareLogRepository.class);
        RemarksUpdateRepository remarksRepository = mock(RemarksUpdateRepository.class);
        ComplaintHistoryRepository historyRepository = mock(ComplaintHistoryRepository.class);

        inject(service, "complaintLogRepository", complaintRepository);
        inject(service, "hardwareLogRepository", hardwareRepository);
        inject(service, "remarksUpdateRepository", remarksRepository);
        inject(service, "complaintHistoryRepository", historyRepository);

        ComplaintLog complaint = new ComplaintLog();
        complaint.setId(1L);
        complaint.setComplaintId("QMS-1");
        complaint.setDate(Date.valueOf(LocalDate.now().minusDays(2)));
        complaint.setBankName("Test Bank");
        complaint.setBranchCode("0001");
        complaint.setBranchName("Main Branch");
        complaint.setCity("Karachi");
        complaint.setReferenceNumber("REF-1");
        complaint.setDetails("Test details");
        complaint.setEquipmentDescription("ATM");
        complaint.setVisitorName("Engineer One");
        complaint.setRepeatComplaint(true);
        complaint.setComplaintStatus("Open");
        complaint.setCourierStatus("Delivered");

        when(complaintRepository.findAllById(List.of(1L))).thenReturn(List.of(complaint));
        when(hardwareRepository.findByComplaintLogIdIn(anyList())).thenReturn(List.of());
        when(remarksRepository.findByComplaintLogIds(anyList())).thenReturn(List.of());
        when(historyRepository.findByComplaintIdInAndFieldNameOrderByChangeDateAsc(anyList(), eq("visitorName")))
                .thenReturn(List.of());

        ExcelReportRequest request = new ExcelReportRequest();
        request.setComplaintIds(List.of(1L));
        request.setViewStatus("Open");
        request.setSelectedReport("standard");

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        service.writeComplaintReport(request, output);

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(output.toByteArray()))) {
            var sheet = workbook.getSheet("Complaints Report");

            assertEquals("A1:G1", sheet.getMergedRegions().get(0).formatAsString());
            assertEquals("A2:G2", sheet.getMergedRegions().get(1).formatAsString());
            assertEquals("B6D7A8", rgb(sheet.getRow(1).getCell(0).getCellStyle()));
            assertEquals("6AA84F", rgb(sheet.getRow(2).getCell(0).getCellStyle()));
            assertEquals("FFF2CC", rgb(sheet.getRow(3).getCell(0).getCellStyle()));

            assertEquals("Karachi", sheet.getRow(2).getCell(0).getStringCellValue());
            assertEquals("Serial No.", sheet.getRow(3).getCell(0).getStringCellValue());
            assertEquals("Ageing", sheet.getRow(3).getCell(1).getStringCellValue());
            assertEquals("Last Action Date", sheet.getRow(3).getCell(15).getStringCellValue());
            assertEquals("Complaint ID", sheet.getRow(3).getCell(16).getStringCellValue());

            assertEquals(CellType.NUMERIC, sheet.getRow(4).getCell(0).getCellType());
            assertEquals(1D, sheet.getRow(4).getCell(0).getNumericCellValue());
            assertEquals(CellType.NUMERIC, sheet.getRow(4).getCell(1).getCellType());
            assertEquals(2D, sheet.getRow(4).getCell(1).getNumericCellValue());
            assertEquals("QMS-1", sheet.getRow(4).getCell(16).getStringCellValue());

            for (int column = 0; column < 17; column++) {
                assertEquals(18 * 256, sheet.getColumnWidth(column));
            }

            var dataStyle = sheet.getRow(4).getCell(0).getCellStyle();
            assertEquals(HorizontalAlignment.CENTER, dataStyle.getAlignment());
            assertEquals(VerticalAlignment.TOP, dataStyle.getVerticalAlignment());
            assertTrue(dataStyle.getWrapText());
            assertEquals("Calibri", workbook.getFontAt(dataStyle.getFontIndex()).getFontName());
            assertEquals(10, workbook.getFontAt(dataStyle.getFontIndex()).getFontHeightInPoints());
        }
    }

    private static String rgb(org.apache.poi.ss.usermodel.CellStyle style) {
        String argb = ((XSSFCellStyle) style).getFillForegroundXSSFColor().getARGBHex();
        return argb.substring(argb.length() - 6);
    }

    @SuppressWarnings("unchecked")
    private static List<String> headers(ExcelReportService service, String status, String report) throws Exception {
        Method method = ExcelReportService.class.getDeclaredMethod("buildHeaders", String.class, String.class);
        method.setAccessible(true);
        return (List<String>) method.invoke(service, status, report);
    }

    private static void inject(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
