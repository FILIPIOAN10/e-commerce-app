package com.ecommerce.project.payload;

import lombok.Data;

/**
 * Body of the "start erasing my account" request.
 *
 * <p>The password is not {@code @NotBlank}: an account created through OAuth has
 * no password to re-enter, and for those the emailed confirmation is the whole
 * proof. Local accounts must supply it — enforced in the service, where it can
 * see which kind of account this is.
 */
@Data
public class GdprErasureRequest {

    private String password;
}
