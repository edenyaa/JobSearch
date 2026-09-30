package com.ats.config;

import com.ats.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Guards /api/users/{id}/** : requires "Authorization: Bearer &lt;token&gt;" with a live session
 * belonging to that same user id. Register and login are not under /{id}, so they stay open.
 */
@Component
public class AuthInterceptor implements HandlerInterceptor {

	public static final String TOKEN_ATTRIBUTE = "sessionToken";

	private static final Pattern USER_PATH = Pattern.compile("^/api/users/(\\d+)(/.*)?$");

	private final AuthService auth;

	public AuthInterceptor(AuthService auth) {
		this.auth = auth;
	}

	@Override
	public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
		if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
			return true;
		}
		Matcher m = USER_PATH.matcher(request.getRequestURI());
		if (!m.matches()) {
			return true;
		}
		String header = request.getHeader("Authorization");
		String token = header != null && header.startsWith("Bearer ") ? header.substring(7).trim() : null;
		Optional<Long> userId = auth.validate(token);
		if (userId.isEmpty()) {
			response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Missing or invalid session");
			return false;
		}
		if (!userId.get().equals(Long.parseLong(m.group(1)))) {
			response.sendError(HttpServletResponse.SC_FORBIDDEN, "Not your account");
			return false;
		}
		request.setAttribute(TOKEN_ATTRIBUTE, token);
		return true;
	}

}
