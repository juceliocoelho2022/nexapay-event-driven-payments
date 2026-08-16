package br.com.nexapay.auth.service;

import br.com.nexapay.auth.api.RegisterRequest;
import br.com.nexapay.auth.api.UserResponse;
import br.com.nexapay.auth.domain.Role;
import br.com.nexapay.auth.domain.UserAccount;
import br.com.nexapay.auth.exception.EmailAlreadyRegisteredException;
import br.com.nexapay.auth.repository.RoleRepository;
import br.com.nexapay.auth.repository.UserAccountRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
public class RegistrationService {

    private static final String DEFAULT_ROLE = "ROLE_USER";

    private final UserAccountRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    public RegistrationService(
            UserAccountRepository userRepository,
            RoleRepository roleRepository,
            PasswordEncoder passwordEncoder
    ) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public UserResponse register(RegisterRequest request) {
        String normalizedEmail = request.email().trim().toLowerCase(Locale.ROOT);

        if (userRepository.existsByEmail(normalizedEmail)) {
            throw new EmailAlreadyRegisteredException(normalizedEmail);
        }

        Role defaultRole = roleRepository.findByName(DEFAULT_ROLE)
                .orElseThrow(() -> new IllegalStateException("Default role ROLE_USER is not configured"));

        UserAccount user = UserAccount.create(
                normalizedEmail,
                passwordEncoder.encode(request.password()),
                defaultRole
        );

        return UserResponse.from(userRepository.save(user));
    }
}
