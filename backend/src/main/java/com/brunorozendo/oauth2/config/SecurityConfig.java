package com.brunorozendo.oauth2.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.OncePerRequestFilter;

import java.util.List;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private static final Logger logger = LoggerFactory.getLogger(SecurityConfig.class);

    @Value("${app.frontend.origin}")
    private String frontendOrigin;

    /** Extra origins (comma-separated) allowed to POST /api/auth/exchange
     *  and /api/auth/refresh. Mobile / extension / web clients live here. */
    @Value("${app.auth.allowed-origins:}")
    private String authAllowedOriginsCsv;

    @Autowired
    private ClientRegistrationRepository clientRegistrationRepository;

    @Autowired
    private AuthenticationSuccessHandler oAuth2SuccessHandler;

    @Autowired
    private OncePerRequestFilter jwtAuthFilter;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
                .authorizeHttpRequests(auth -> auth
                        // /api/auth/user + /logout require a valid access token
                        // (they identify the caller via JwtPrincipal). /exchange
                        // and /refresh are authenticated by possession of the
                        // PKCE code or refresh token in the request body, so
                        // they must be permitAll here.
                        .requestMatchers("/api/auth/user", "/api/auth/logout").authenticated()
                        .anyRequest().permitAll())
                .exceptionHandling(e -> e
                        .defaultAuthenticationEntryPointFor(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED),
                                request -> request.getRequestURI().startsWith("/api/")))
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
                .oauth2Login(oauth2 -> oauth2
                        .authorizationEndpoint(authorization -> authorization
                                .authorizationRequestResolver(
                                        authorizationRequestResolver(this.clientRegistrationRepository)))
                        .successHandler(oAuth2SuccessHandler)
                        .failureHandler((request, response, exception) ->
                                response.sendRedirect(frontendOrigin + "/?error=auth_failed")));
        return http.build();
    }

    @Bean
    public OAuth2AuthorizationRequestResolver authorizationRequestResolver(
            ClientRegistrationRepository clientRegistrationRepository) {

        DefaultOAuth2AuthorizationRequestResolver delegate = new DefaultOAuth2AuthorizationRequestResolver(
                clientRegistrationRepository, "/oauth2/authorization");

        delegate.setAuthorizationRequestCustomizer(authorizationRequest ->
                authorizationRequest.additionalParameters(params -> {
                    params.put("access_type", "offline");
                    params.put("prompt", "select_account");
                }));

        return new OAuth2AuthorizationRequestResolver() {
            @Override
            public OAuth2AuthorizationRequest resolve(jakarta.servlet.http.HttpServletRequest request) {
                OAuth2AuthorizationRequest authRequest = delegate.resolve(request);
                if (authRequest != null) captureClient(request);
                return authRequest;
            }

            @Override
            public OAuth2AuthorizationRequest resolve(jakarta.servlet.http.HttpServletRequest request, String clientRegistrationId) {
                OAuth2AuthorizationRequest authRequest = delegate.resolve(request, clientRegistrationId);
                if (authRequest != null) captureClient(request);
                return authRequest;
            }

            private void captureClient(jakarta.servlet.http.HttpServletRequest request) {
                String client = request.getParameter("client");
                if (client != null && !client.isBlank()) {
                    request.getSession(true).setAttribute("oauth2_client", client);
                    logger.debug("Mobile client captured in session: {}", client);
                }
            }
        };
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        // The legacy web frontend keeps the old (credentialed) CORS so the
        // /api/auth/user + /logout cookies survive.
        CorsConfiguration credentialed = new CorsConfiguration();
        credentialed.setAllowedOrigins(List.of(frontendOrigin));
        credentialed.setAllowedMethods(List.of("GET", "POST", "OPTIONS"));
        credentialed.setAllowedHeaders(List.of("*"));
        credentialed.setAllowCredentials(true);

        // /api/auth/exchange + /api/auth/refresh use bearer tokens in the
        // body — no cookies — so we widen the allowed origins to every
        // client that needs to call them (web SPA, Chrome extension,
        // potentially native apps).
        java.util.List<String> bearerOrigins = new java.util.ArrayList<>();
        bearerOrigins.add(frontendOrigin);
        if (authAllowedOriginsCsv != null && !authAllowedOriginsCsv.isBlank()) {
            for (String o : authAllowedOriginsCsv.split(",")) {
                String t = o.trim();
                if (!t.isEmpty() && !bearerOrigins.contains(t)) bearerOrigins.add(t);
            }
        }
        CorsConfiguration bearer = new CorsConfiguration();
        bearer.setAllowedOrigins(bearerOrigins);
        bearer.setAllowedMethods(List.of("POST", "OPTIONS"));
        bearer.setAllowedHeaders(List.of("Content-Type", "Accept", "Authorization"));
        bearer.setAllowCredentials(false);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/auth/exchange", bearer);
        source.registerCorsConfiguration("/api/auth/refresh", bearer);
        source.registerCorsConfiguration("/**", credentialed);
        return source;
    }
}
