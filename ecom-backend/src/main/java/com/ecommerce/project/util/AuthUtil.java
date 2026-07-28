package com.ecommerce.project.util;

import com.ecommerce.project.model.User;
import com.ecommerce.project.repository.UserRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Component;

@Component
public class AuthUtil {

    private final UserRepository userRepository;

    public AuthUtil(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public String loggedInEmail() {
        return loggedInUser().getEmail();
    }

    public Long loggedInUserId() {
        return loggedInUser().getUserId();
    }

    public User loggedInUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getName() == null) {
            throw new UsernameNotFoundException("No authenticated user found");
        }
        return userRepository.findByUserName(authentication.getName())
                .orElseThrow(() -> new UsernameNotFoundException(
                        "User Not Found with username: " + authentication.getName()));
    }
}