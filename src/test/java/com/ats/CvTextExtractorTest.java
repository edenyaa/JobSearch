package com.ats;

import static org.junit.jupiter.api.Assertions.*;

import com.ats.service.CvTextExtractor;
import com.ats.service.ValidationException;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;

class CvTextExtractorTest {

    private final CvTextExtractor extractor = new CvTextExtractor();

    private byte[] pdfWith(String text) throws Exception {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDPage page = new PDPage();
            document.addPage(page);
            try (PDPageContentStream stream = new PDPageContentStream(document, page)) {
                stream.beginText();
                stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                stream.newLineAtOffset(50, 700);
                stream.showText(text);
                stream.endText();
            }
            document.save(out);
            return out.toByteArray();
        }
    }

    private byte[] docxWith(String text) throws Exception {
        try (XWPFDocument document = new XWPFDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            document.createParagraph().createRun().setText(text);
            document.write(out);
            return out.toByteArray();
        }
    }

    @Test
    public void GivenPdfFile_WhenExtracting_ThenTextIsReturned() throws Exception {
        byte[] pdf = pdfWith("Java developer with Spring");

        String text = extractor.extract(pdf, "cv.pdf");

        assertTrue(text.contains("Java developer with Spring"));
    }

    @Test
    public void GivenDocxFile_WhenExtracting_ThenTextIsReturned() throws Exception {
        byte[] docx = docxWith("Python engineer with Django");

        String text = extractor.extract(docx, "cv.docx");

        assertTrue(text.contains("Python engineer with Django"));
    }

    @Test
    public void GivenTxtFile_WhenExtracting_ThenTextIsReturned() {
        byte[] txt = "Plain text CV".getBytes(StandardCharsets.UTF_8);

        String text = extractor.extract(txt, "cv.txt");

        assertEquals("Plain text CV", text);
    }

    @Test
    public void GivenUppercaseExtension_WhenExtracting_ThenItIsSupported() {
        byte[] txt = "Plain".getBytes(StandardCharsets.UTF_8);

        String text = extractor.extract(txt, "CV.TXT");

        assertEquals("Plain", text);
    }

    @Test
    public void GivenUnsupportedExtensionOrNoName_WhenExtracting_ThenValidationExceptionIsThrown() {
        byte[] data = "x".getBytes(StandardCharsets.UTF_8);

        assertThrows(ValidationException.class, () -> extractor.extract(data, "cv.exe"));
        assertThrows(ValidationException.class, () -> extractor.extract(data, null));
    }

    @Test
    public void GivenCorruptPdfOrDocx_WhenExtracting_ThenValidationExceptionIsThrown() {
        byte[] garbage = "not really a file".getBytes(StandardCharsets.UTF_8);

        assertThrows(ValidationException.class, () -> extractor.extract(garbage, "cv.pdf"));
        assertThrows(ValidationException.class, () -> extractor.extract(garbage, "cv.docx"));
    }

}
