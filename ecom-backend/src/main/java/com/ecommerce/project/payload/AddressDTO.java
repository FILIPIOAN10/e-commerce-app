package com.ecommerce.project.payload;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * The constraints here mirror the ones on the {@code Address} entity.
 *
 * <p>They were only on the entity, where Bean Validation runs at Hibernate
 * flush time: {@code @Valid @RequestBody AddressDTO} had nothing to check, a
 * too-short building name sailed through the controller, and the request died
 * as a {@code ConstraintViolationException} during persist — reaching the
 * client as a bare 500. Declared at the boundary they are rejected before any
 * work is done, and the caller gets the 400 with the offending field named,
 * like every other DTO in this package.
 *
 * <p>{@code max = 255} matches the VARCHAR(255) columns in V1, so an oversized
 * value is a validation error rather than a database one.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AddressDTO {

    private Long addressId;

    @NotBlank(message = "Street is required")
    @Size(min = 5, max = 255, message = "Street must be at least 5 characters")
    private String street;

    @NotBlank(message = "Building name is required")
    @Size(min = 5, max = 255, message = "Building name must be at least 5 characters")
    private String buildingName;

    @NotBlank(message = "City is required")
    @Size(min = 4, max = 255, message = "City must be at least 4 characters")
    private String city;

    @NotBlank(message = "State is required")
    @Size(min = 2, max = 255, message = "State must be at least 2 characters")
    private String state;

    @NotBlank(message = "Country is required")
    @Size(min = 2, max = 255, message = "Country must be at least 2 characters")
    private String country;

    @NotBlank(message = "Pincode is required")
    @Size(min = 5, max = 255, message = "Pincode must be at least 5 characters")
    private String pincode;
}
