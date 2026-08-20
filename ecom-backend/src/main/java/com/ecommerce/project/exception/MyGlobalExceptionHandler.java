package com.ecommerce.project.exception;

import com.ecommerce.project.payload.ApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
// Handler global pentru excepții (validează și formatează răspunsurile cu erori)

public class MyGlobalExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(MyGlobalExceptionHandler.class);

    /**
     * Gestionează erorile de validare a DTO-urilor.
     * Returnează o mapă cu numele câmpului și mesajul de eroare.
     */

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> myMethodArgumentNotValidException(MethodArgumentNotValidException e) {
        Map<String, String> response = new HashMap<>();
        e.getBindingResult().getAllErrors().forEach((error) -> {
            String filedName = ((FieldError) error).getField();
            String message = error.getDefaultMessage();
            response.put(filedName, message);
        });
        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }
    /**
     * Gestionează cazurile când o resursă nu este găsită în baza de date.
     */
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiResponse> myResourceNotFoundException(ResourceNotFoundException e) {
        String message = e.getMessage();
        ApiResponse apiResponse = new ApiResponse(message, false);
        return new ResponseEntity<>(apiResponse, HttpStatus.NOT_FOUND);
    }
    /**
     * Gestionează erorile API definite de programator (APIException).
     */
    @ExceptionHandler(APIException.class)
    public ResponseEntity<ApiResponse> myAPIException(APIException e){
        String message = e.getMessage();
        ApiResponse apiResponse = new ApiResponse(message, false);
        return new ResponseEntity<>(apiResponse, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(UserNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleUserNotFound(UserNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("message", e.getMessage()));
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<Map<String, String>> handleInvalidCredentials(InvalidCredentialsException e) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("message", e.getMessage()));
    }

    @ExceptionHandler(EmailAlreadyExistsException.class)
    public ResponseEntity<Map<String, String>> handleEmailAlreadyExists(EmailAlreadyExistsException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("message", e.getMessage()));
    }

    @ExceptionHandler(AccountLockedException.class)
    public ResponseEntity<Map<String, Object>> handleAccountLocked(AccountLockedException e) {
        Map<String, Object> body = new HashMap<>();
        body.put("message", e.getMessage());
        body.put("locked", true);
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(body);
    }

    @ExceptionHandler(AccountNotVerifiedException.class)
    public ResponseEntity<Map<String, String>> handleAccountNotVerified(AccountNotVerifiedException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Map.of("message", e.getMessage()));
    }

    @ExceptionHandler(UsernameAlreadyExistsException.class)
    public ResponseEntity<Map<String, String>> handleUsernameAlreadyExists(UsernameAlreadyExistsException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("message", e.getMessage()));
    }

    @ExceptionHandler(RoleNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleRoleNotFound(RoleNotFoundException e) {
        logger.error("Default role not found", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("message", "Internal server error"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleGenericException(Exception e) {
        logger.error("Unhandled exception", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("message", "Internal server error"));
    }
}
