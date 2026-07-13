package com.system.complaints.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.system.complaints.model.AppUser;
import com.system.complaints.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.NoOpPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.ForwardedHeaderFilter;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

@Configuration
public class SecurityConfig {

    private final UserDetailsService userDetailsService;
    private final UserRepository userRepository;

    @Value("${cors.allowed-origins}")
    private String corsAllowedOrigins;

    public SecurityConfig(UserDetailsService userDetailsService, UserRepository userRepository) {
        this.userDetailsService = userDetailsService;
        this.userRepository = userRepository;
    }

    @Bean
    public ForwardedHeaderFilter forwardedHeaderFilter() {
        return new ForwardedHeaderFilter();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authenticationConfiguration)
            throws Exception {
        return authenticationConfiguration.getAuthenticationManager();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {

        http
                .csrf(csrf -> csrf.disable())

                .cors(cors -> cors.configurationSource(corsConfigurationSource()))

                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/", "/index.html", "/static/**").permitAll()
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers("/login", "/perform_login").permitAll()
                        .requestMatchers(HttpMethod.GET, "/visit-plan/plans/pending").hasAuthority("ADMIN")
                        .requestMatchers(HttpMethod.POST,
                                "/visit-plan/plans/*/approve",
                                "/visit-plan/plans/*/reject",
                                "/visit-plan/plans/*/items/*/approve",
                                "/visit-plan/plans/*/items/*/reject",
                                "/visit-plan/complaints/*/approve"
                        ).hasAuthority("ADMIN")
                        .requestMatchers("/admin/**").hasAuthority("ADMIN")
                        .requestMatchers("/user/**").hasAnyAuthority("USER", "ADMIN")
                        .anyRequest().authenticated()
                )

                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((request, response, authException) -> {
                            response.setStatus(401);
                            response.setContentType(MediaType.APPLICATION_JSON_VALUE);

                            Map<String, String> result = new HashMap<>();
                            result.put("status", "error");
                            result.put("message", "Unauthorized");

                            new ObjectMapper().writeValue(response.getWriter(), result);
                        })
                )

                .formLogin(form -> form
                        .loginPage("/login")
                        .loginProcessingUrl("/perform_login")
                        .permitAll()
                        .successHandler((request, response, authentication) -> {
                            response.setStatus(200);
                            response.setContentType(MediaType.APPLICATION_JSON_VALUE);

                            AppUser appUser = userRepository.findByUsername(authentication.getName())
                                    .orElseThrow(() -> new RuntimeException("User not found"));

                            Map<String, Object> result = new HashMap<>();
                            result.put("status", "success");
                            result.put("message", "Login successful");
                            result.put("id", appUser.getId());
                            result.put("username", appUser.getUsername());
                            result.put("roles", authentication.getAuthorities().stream()
                                    .map(authority -> authority.getAuthority())
                                    .toList());
                            result.put("platformType", appUser.getPlatformType());
                            result.put("userType", appUser.getUserType());
                            result.put("visitorId", appUser.getVisitor() != null ? appUser.getVisitor().getId() : null);
                            result.put("visitPlanAccess",
                                    "ADMIN".equals(appUser.getRole().getName()) || appUser.isVisitPlanAccess());

                            new ObjectMapper().writeValue(response.getWriter(), result);
                        })
                        .failureHandler((request, response, exception) -> {
                            response.setStatus(401);
                            response.setContentType(MediaType.APPLICATION_JSON_VALUE);

                            Map<String, String> result = new HashMap<>();
                            result.put("status", "error");
                            result.put("message", "Invalid username or password");

                            new ObjectMapper().writeValue(response.getWriter(), result);
                        })
                )

                .logout(logout -> logout
                        .logoutUrl("/logout")
                        .logoutSuccessHandler((request, response, authentication) -> {
                            response.setStatus(200);
                            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                            response.getWriter().write("{\"message\":\"Logged out successfully\"}");
                        })
                        .invalidateHttpSession(true)
                        .deleteCookies("JSESSIONID")
                )

                .sessionManagement(session -> session
                        .sessionFixation(sessionFixation -> sessionFixation.migrateSession())
                        .sessionConcurrency(concurrency -> concurrency
                                .maximumSessions(-1)
                                .maxSessionsPreventsLogin(false)
                        )
                )

                .httpBasic(Customizer.withDefaults());

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();

        configuration.setAllowedOrigins(
                Arrays.stream(corsAllowedOrigins.split(","))
                        .map(String::trim)
                        .toList()
        );

        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
        configuration.setAllowedHeaders(Arrays.asList("*"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return NoOpPasswordEncoder.getInstance();
    }
}
