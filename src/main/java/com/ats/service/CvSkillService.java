package com.ats.service;

import com.ats.domain.SkillCatalog;
import com.ats.domain.SkillCatalog.Detected;
import com.ats.domain.User;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CvSkillService {

	private final UserService users;
	private final SkillService skills;
	private final CvTextExtractor extractor;

	public CvSkillService(UserService users, SkillService skills, CvTextExtractor extractor) {
		this.users = users;
		this.skills = skills;
		this.extractor = extractor;
	}

	@Transactional(readOnly = true)
	public List<Detected> detect(Long userId) {
		return SkillCatalog.detect(users.mainCvText(userId));
	}

	@Transactional
	public int importFromCv(Long userId) {
		User user = users.get(userId);
		if (user.getMainCv() == null) {
			return 0;
		}
		String text;
		try {
			text = extractor.extract(user.getMainCv(), user.getMainCvFileName());
		} catch (ValidationException e) {
			return 0;
		}
		Set<String> existing = new HashSet<>();
		skills.list(userId).forEach(skill -> existing.add(skill.getName().toLowerCase(Locale.ROOT)));
		int imported = 0;
		for (Detected detected : SkillCatalog.detect(text)) {
			if (existing.add(detected.name().toLowerCase(Locale.ROOT))) {
				List<String> keywords = detected.foundForms().stream()
						.filter(form -> !form.equalsIgnoreCase(detected.name()))
						.toList();
				skills.createFromCv(userId, detected.name(), keywords);
				imported++;
			}
		}
		return imported;
	}

}
