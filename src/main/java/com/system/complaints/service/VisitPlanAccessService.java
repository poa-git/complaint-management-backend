package com.system.complaints.service;

import com.system.complaints.model.AppUser;
import com.system.complaints.model.PlatformType;
import com.system.complaints.repository.UserRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class VisitPlanAccessService {
    private final UserRepository userRepository;

    public VisitPlanAccessService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public void requireAccess() {
        Authentication authentication = authentication();
        if (isAdmin(authentication)) return;

        AppUser user = userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new AccessDeniedException("User account was not found."));
        if (!user.isVisitPlanAccess()) {
            throw new AccessDeniedException("Visit Plan access has not been granted to this user.");
        }
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getAccessUsers() {
        requireAdmin();
        return userRepository.findByPlatformType(PlatformType.WEB).stream()
                .map(user -> {
                    boolean admin = "ADMIN".equalsIgnoreCase(user.getRole().getName());
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("id", user.getId());
                    row.put("username", user.getUsername());
                    row.put("role", user.getRole().getName());
                    row.put("enabled", admin || user.isVisitPlanAccess());
                    row.put("editable", !admin);
                    return row;
                })
                .toList();
    }

    @Transactional
    public Map<String, Object> setAccess(Long userId, boolean enabled) {
        requireAdmin();
        AppUser user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found."));
        boolean admin = "ADMIN".equalsIgnoreCase(user.getRole().getName());
        if (!admin) {
            user.setVisitPlanAccess(enabled);
            userRepository.save(user);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", user.getId());
        result.put("username", user.getUsername());
        result.put("enabled", admin || user.isVisitPlanAccess());
        result.put("editable", !admin);
        return result;
    }

    private void requireAdmin() {
        if (!isAdmin(authentication())) {
            throw new AccessDeniedException("Admin access is required.");
        }
    }

    private Authentication authentication() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new AccessDeniedException("Authentication is required.");
        }
        return authentication;
    }

    private boolean isAdmin(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .anyMatch(authority -> "ADMIN".equals(authority.getAuthority()));
    }
}
