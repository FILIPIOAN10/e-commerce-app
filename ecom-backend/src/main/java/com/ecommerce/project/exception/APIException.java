package com.ecommerce.project.exception;


// Excepție custom pentru erori API (aruncată din servicii sau controller)

public class APIException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public APIException() {

    }
    public APIException(String message) {
        super(message);
    }
}
