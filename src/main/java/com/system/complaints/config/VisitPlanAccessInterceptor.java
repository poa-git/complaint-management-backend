package com.system.complaints.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.system.complaints.service.VisitPlanAccessService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Map;

@Component
public class VisitPlanAccessInterceptor implements HandlerInterceptor {
    private final VisitPlanAccessService accessService;
    private final ObjectMapper objectMapper;

    public VisitPlanAccessInterceptor(
            VisitPlanAccessService accessService,
            ObjectMapper objectMapper
    ) {
        this.accessService = accessService;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean preHandle(
            HttpServletRequest request,
            HttpServletResponse response,
            Object handler
    ) throws Exception {
        try {
            accessService.requireAccess();
            return true;
        } catch (AccessDeniedException exception) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            objectMapper.writeValue(
                    response.getWriter(),
                    Map.of("message", exception.getMessage())
            );
            return false;
        }
    }
}
