package com.ats.service;

import java.util.Map;

public interface LlmClient {

	String generateJson(String systemInstruction, String userPrompt);

	default String generateJson(String systemInstruction, String userPrompt, double temperature,
			Map<String, Object> responseSchema) {
		return generateJson(systemInstruction, userPrompt);
	}

}
