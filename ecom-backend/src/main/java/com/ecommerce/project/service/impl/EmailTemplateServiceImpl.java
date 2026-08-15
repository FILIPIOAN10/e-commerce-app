package com.ecommerce.project.service.impl;

import com.ecommerce.project.service.EmailTemplateService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@Slf4j
@Service
public class EmailTemplateServiceImpl implements EmailTemplateService {

    private static final String TEMPLATE_PREFIX = "email-templates/";
    private static final String TEMPLATE_SUFFIX = ".html";

    @Override
    public String render(String templateName, Map<String, String> placeholders) {
        String content = load(TEMPLATE_PREFIX + templateName + TEMPLATE_SUFFIX);
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            content = content.replace("{{" + entry.getKey() + "}}", entry.getValue());
        }
        return content;
    }

    private String load(String path) {
        try {
            return new String(new ClassPathResource(path).getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.error("Failed to load email template {}: {}", path, e.getMessage());
            throw new RuntimeException("Email template not found: " + path);
        }
    }
}
