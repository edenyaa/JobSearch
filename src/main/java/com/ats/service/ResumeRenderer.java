package com.ats.service;

import com.ats.domain.TailoredCv;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.springframework.stereotype.Component;

@Component
public class ResumeRenderer {

	public byte[] render(TailoredCv cv) {
		try (XWPFDocument document = new XWPFDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
			line(document, cv.name(), 18, true);
			for (String contact : cv.contact()) {
				line(document, contact, 11, false);
			}
			if (notBlank(cv.summary())) {
				heading(document, "SUMMARY");
				line(document, cv.summary(), 11, false);
			}
			if (!cv.skills().isEmpty()) {
				heading(document, "SKILLS");
				line(document, String.join(", ", cv.skills()), 11, false);
			}
			if (!cv.experience().isEmpty()) {
				heading(document, "EXPERIENCE");
				for (TailoredCv.Experience job : cv.experience()) {
					line(document, join(job.role(), job.company(), job.period()), 11, true);
					for (String bullet : job.bullets()) {
						line(document, "• " + bullet, 11, false);
					}
				}
			}
			if (!cv.projects().isEmpty()) {
				heading(document, "PROJECTS");
				for (TailoredCv.Project project : cv.projects()) {
					line(document, join(project.name(), String.join(", ", project.technologies())), 11, true);
					for (String bullet : project.bullets()) {
						line(document, "• " + bullet, 11, false);
					}
				}
			}
			if (!cv.education().isEmpty()) {
				heading(document, "EDUCATION");
				for (TailoredCv.Education education : cv.education()) {
					line(document, join(education.degree(), education.institution(), education.period()), 11, false);
				}
			}
			if (!cv.additional().isEmpty()) {
				heading(document, "ADDITIONAL");
				for (String item : cv.additional()) {
					line(document, "• " + item, 11, false);
				}
			}
			document.write(out);
			return out.toByteArray();
		} catch (IOException e) {
			throw new IllegalStateException("Could not build the resume file", e);
		}
	}

	private void heading(XWPFDocument document, String title) {
		line(document, title, 12, true);
	}

	private void line(XWPFDocument document, String text, int size, boolean bold) {
		if (!notBlank(text)) {
			return;
		}
		XWPFParagraph paragraph = document.createParagraph();
		XWPFRun run = paragraph.createRun();
		run.setFontFamily("Calibri");
		run.setFontSize(size);
		run.setBold(bold);
		run.setText(text);
	}

	private String join(String... parts) {
		StringBuilder joined = new StringBuilder();
		for (String part : parts) {
			if (notBlank(part)) {
				if (joined.length() > 0) {
					joined.append(" | ");
				}
				joined.append(part);
			}
		}
		return joined.toString();
	}

	private boolean notBlank(String value) {
		return value != null && !value.isBlank();
	}

}
