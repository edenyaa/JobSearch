package com.ats.controllers;

import com.ats.dto.CourseDtos.CourseRequest;
import com.ats.dto.CourseDtos.CourseResponse;
import com.ats.service.CourseService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users/{userId}/courses")
public class CourseController {

	private final CourseService courses;

	public CourseController(CourseService courses) {
		this.courses = courses;
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public CourseResponse create(@PathVariable Long userId, @Valid @RequestBody CourseRequest req) {
		return CourseResponse.from(courses.create(userId, req));
	}

	@GetMapping
	public List<CourseResponse> list(@PathVariable Long userId) {
		return courses.list(userId).stream().map(CourseResponse::from).toList();
	}

	@GetMapping("/{id}")
	public CourseResponse get(@PathVariable Long userId, @PathVariable Long id) {
		return CourseResponse.from(courses.get(userId, id));
	}

	@PutMapping("/{id}")
	public CourseResponse update(@PathVariable Long userId, @PathVariable Long id, @Valid @RequestBody CourseRequest req) {
		return CourseResponse.from(courses.update(userId, id, req));
	}

	@DeleteMapping("/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void delete(@PathVariable Long userId, @PathVariable Long id) {
		courses.delete(userId, id);
	}

}
