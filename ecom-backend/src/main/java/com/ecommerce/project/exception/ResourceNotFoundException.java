package com.ecommerce.project.exception;


// Excepție pentru resurse negăsite (entități inexistente în DB)
public class ResourceNotFoundException extends RuntimeException {
    String resourceName;
    String field;
    String fieldName;
    Long fieldId;

    public ResourceNotFoundException() {
    }
    /**
     * Construiește eroarea pe baza numelui resursei și numelui câmpului.
     */
    public ResourceNotFoundException(String resourceName, String field, String fieldName) {
        super(String.format(" %s not found with %s:  %s", resourceName,field,fieldName));
        this.resourceName = resourceName;
        this.field = field;
        this.fieldName = fieldName;

    }
    /**
     * Construiește eroarea pentru ID-uri (ex: Product not found with id: 5).
     */
    public ResourceNotFoundException(String resourceName, String field, Long fieldId) {
        super(String.format(" %s not found with %s: %d", resourceName,field,fieldId));
        this.resourceName = resourceName;
        this.field = field;
        this.fieldId = fieldId;

    }



}
