package com.ats.service;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.stereotype.Component;

@Component
public class CvTextExtractor {

	public String extract(byte[] data, String fileName) {
		String name = fileName == null ? "" : fileName.toLowerCase();
		try {
			if (name.endsWith(".pdf")) {
				return fromPdf(data);
			}
			if (name.endsWith(".docx")) {
				return fromDocx(data);
			}
			if (name.endsWith(".txt")) {
				return new String(data, StandardCharsets.UTF_8);
			}
		} catch (Exception e) {
			throw new ValidationException("Could not read the CV file: " + e.getMessage());
		}
		throw new ValidationException("Unsupported CV format. Use PDF, DOCX or TXT.");
	}

	private String fromPdf(byte[] data) throws Exception {
		try (PDDocument document = Loader.loadPDF(data)) {
			return new PDFTextStripper().getText(document);
		}
	}

	private String fromDocx(byte[] data) throws Exception {
		try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(data));
				XWPFWordExtractor extractor = new XWPFWordExtractor(document)) {
			return extractor.getText();
		}
	}

}
