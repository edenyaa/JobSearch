package com.ats.service;

import com.ats.domain.User;
import com.ats.dto.UserDtos.CreateUser;
import com.ats.dto.UserDtos.Login;
import com.ats.repository.UserRepository;
import java.io.IOException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@Transactional
public class UserService {

	private final UserRepository users;
	private final UserHandler handler;
	private final AuthService auth;
	private final CvTextExtractor extractor;

	public UserService(UserRepository users, UserHandler handler, AuthService auth, CvTextExtractor extractor) {
		this.users = users;
		this.handler = handler;
		this.auth = auth;
		this.extractor = extractor;
	}

	@Transactional(readOnly = true)
	public String mainCvText(Long id) {
		User u = get(id);
		if (u.getMainCv() == null) {
			throw new ValidationException("Upload a main CV first.");
		}
		return extractor.extract(u.getMainCv(), u.getMainCvFileName());
	}

	public User register(CreateUser req) {
		handler.validateUserDoesNotExist(users.findByEmailIgnoreCase(req.email() == null ? "" : req.email().trim()).orElse(null));
		return users.save(handler.registerUser(req.name(), req.email(), req.password()));
	}

	public User login(Login req) {
		User user = users.findByEmailIgnoreCase(req.email().trim()).orElse(null);
		handler.loginUser(user, req.password());
		return user;
	}

	@Transactional(readOnly = true)
	public User get(Long id) {
		return users.findById(id).orElseThrow(() -> new NotFoundException("User", id));
	}

	public User changeName(Long id, String oldName, String newName) {
		User u = get(id);
		handler.editUsername(u, oldName, newName);
		return u;
	}

	public User changeEmail(Long id, String oldEmail, String newEmail) {
		User u = get(id);
		if (users.existsByEmailIgnoreCaseAndIdNot(newEmail.trim(), id)) {
			throw new ConflictException("Email already registered");
		}
		handler.editEmail(u, oldEmail, newEmail);
		return u;
	}

	public User changePassword(Long id, String oldPassword, String newPassword) {
		User u = get(id);
		handler.editPassword(u, oldPassword, newPassword);
		auth.invalidateAllSessions(id); // a password change logs out every session
		return u;
	}

	public void delete(Long id) {
		users.delete(get(id));
		auth.invalidateAllSessions(id);
	}

	public User setMainCv(Long id, MultipartFile file) throws IOException {
		User u = get(id);
		u.setMainCvFileName(file.getOriginalFilename());
		u.setMainCvContentType(file.getContentType());
		u.setMainCv(file.getBytes());
		return u;
	}

}
