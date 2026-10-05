package com.essalud.email.model;

import java.util.Map;

import lombok.Data;

@Data
public class EmailRequest {
    private String to;
    private String subject;
    private String templateName;
    private Map<String, Object> variables;
}