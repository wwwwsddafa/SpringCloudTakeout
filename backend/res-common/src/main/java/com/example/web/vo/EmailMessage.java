package com.example.web.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EmailMessage implements Serializable {

    private static final long serialVersionUID = 1L;

    public static final String EMAIL_EXCHANGE = "email.exchange";
    public static final String EMAIL_ROUTING_KEY = "email.send";

    public static final String TYPE_REGISTER = "REGISTER";
    public static final String TYPE_LOGIN = "LOGIN";
    public static final String TYPE_ORDER = "ORDER";
    public static final String TYPE_PAYMENT = "PAYMENT";
    public static final String TYPE_OPS_REPORT = "OPS_REPORT";

    private String type;

    private String userId;

    private String email;

    private String username;

    private Map<String, Object> params;
}