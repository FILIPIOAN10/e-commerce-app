package com.ecommerce.project.service;

import java.util.Map;

public interface EmailTemplateService {
    String render(String templateName, Map<String, String> placeholders);
}
