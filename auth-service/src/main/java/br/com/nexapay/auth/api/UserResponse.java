package br.com.nexapay.auth.api;

import br.com.nexapay.auth.domain.Role;
import br.com.nexapay.auth.domain.UserAccount;

import java.time.OffsetDateTime;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public record UserResponse(
        UUID id,
        String email,
        boolean enabled,
        Set<String> roles,
        OffsetDateTime createdAt
) {

    public static UserResponse from(UserAccount user) {
        Set<String> roleNames = user.getRoles().stream()
                .map(Role::getName)
                .collect(Collectors.toUnmodifiableSet());

        return new UserResponse(
                user.getId(),
                user.getEmail(),
                user.isEnabled(),
                roleNames,
                user.getCreatedAt()
        );
    }
}
