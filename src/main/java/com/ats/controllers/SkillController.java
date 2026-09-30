package com.ats.controllers;

import com.ats.dto.SkillDtos.SkillRequest;
import com.ats.dto.SkillDtos.SkillResponse;
import com.ats.service.SkillService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users/{userId}/skills")
public class SkillController {

	private final SkillService skills;

	public SkillController(SkillService skills) {
		this.skills = skills;
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public SkillResponse create(@PathVariable Long userId, @Valid @RequestBody SkillRequest req) {
		return SkillResponse.from(skills.create(userId, req));
	}

	@GetMapping
	public List<SkillResponse> list(@PathVariable Long userId) {
		return skills.list(userId).stream().map(SkillResponse::from).toList();
	}

	@GetMapping("/{id}")
	public SkillResponse get(@PathVariable Long userId, @PathVariable Long id) {
		return SkillResponse.from(skills.get(userId, id));
	}

	@PutMapping("/{id}")
	public SkillResponse update(@PathVariable Long userId, @PathVariable Long id, @Valid @RequestBody SkillRequest req) {
		return SkillResponse.from(skills.update(userId, id, req));
	}

	@DeleteMapping("/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void delete(@PathVariable Long userId, @PathVariable Long id) {
		skills.delete(userId, id);
	}

}
