package com.ats.controllers;

import com.ats.domain.User;
import com.ats.dto.AnalysisDtos.CvTextResponse;
import com.ats.dto.UserDtos.ChangeValue;
import com.ats.dto.UserDtos.CreateUser;
import com.ats.config.AuthInterceptor;
import com.ats.dto.UserDtos.Login;
import com.ats.dto.UserDtos.LoginResponse;
import com.ats.dto.UserDtos.UserResponse;
import com.ats.service.AuthService;
import com.ats.service.NotFoundException;
import com.ats.service.UserService;
import jakarta.validation.Valid;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/users")
public class UserController {

	private final UserService users;
	private final AuthService auth;

	public UserController(UserService users, AuthService auth) {
		this.users = users;
		this.auth = auth;
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public UserResponse register(@Valid @RequestBody CreateUser req) {
		return UserResponse.from(users.register(req));
	}

	@PostMapping("/login")
	public LoginResponse login(@Valid @RequestBody Login req) {
		User user = users.login(req);
		return new LoginResponse(auth.createSession(user.getId()), UserResponse.from(user));
	}

	@PostMapping("/{id}/logout")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void logout(@RequestAttribute(AuthInterceptor.TOKEN_ATTRIBUTE) String token) {
		auth.invalidateSession(token);
	}

	@GetMapping("/{id}")
	public UserResponse get(@PathVariable Long id) {
		return UserResponse.from(users.get(id));
	}

	@PutMapping("/{id}/name")
	public UserResponse changeName(@PathVariable Long id, @Valid @RequestBody ChangeValue req) {
		return UserResponse.from(users.changeName(id, req.oldValue(), req.newValue()));
	}

	@PutMapping("/{id}/email")
	public UserResponse changeEmail(@PathVariable Long id, @Valid @RequestBody ChangeValue req) {
		return UserResponse.from(users.changeEmail(id, req.oldValue(), req.newValue()));
	}

	@PutMapping("/{id}/password")
	public UserResponse changePassword(@PathVariable Long id, @Valid @RequestBody ChangeValue req) {
		return UserResponse.from(users.changePassword(id, req.oldValue(), req.newValue()));
	}

	@DeleteMapping("/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void delete(@PathVariable Long id) {
		users.delete(id);
	}

	@PutMapping(value = "/{id}/cv", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	public UserResponse uploadCv(@PathVariable Long id, @RequestParam("file") MultipartFile file) throws IOException {
		return UserResponse.from(users.setMainCv(id, file));
	}

	@GetMapping("/{id}/cv/text")
	public CvTextResponse cvText(@PathVariable Long id) {
		return new CvTextResponse(users.mainCvText(id));
	}

	@GetMapping("/{id}/cv")
	public ResponseEntity<byte[]> downloadCv(@PathVariable Long id) {
		User u = users.get(id);
		if (u.getMainCv() == null) {
			throw new NotFoundException("Main CV of user", id);
		}
		return ResponseEntity.ok()
				.header("Content-Disposition", "attachment; filename=\"" + u.getMainCvFileName() + "\"")
				.contentType(u.getMainCvContentType() != null ? MediaType.parseMediaType(u.getMainCvContentType())
						: MediaType.APPLICATION_OCTET_STREAM)
				.body(u.getMainCv());
	}

}
