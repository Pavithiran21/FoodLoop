package com.foodloop.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Settings read from backend/.env (see application.yml). They are checked at startup by
 * {@link StartupConfigCheck}, which reports variable names only, never values.
 */
@ConfigurationProperties(prefix = "app")
public record AppProperties(
        String env,
        String frontendUrl,
        String jwtSecret,
        String jobTriggerSecret,
        String mailFrom,
        boolean rateLimitsEnabled,
        boolean demoMode,
        String demoPassword,
        boolean timeOverrideEnabled,
        String expiryCron,
        String autoCompleteCron,
        DemoSmtp demoSmtp,
        Nominatim nominatim) {

    public boolean isProduction() {
        return "production".equals(env);
    }

    /** The Ethereal inbox that demo accounts' emails go to (DEMO_SMTP_*); empty when not set. */
    public record DemoSmtp(String host, int port, String user, String pass) {

        public boolean isConfigured() {
            return isSet(host) && isSet(user) && isSet(pass);
        }

        private static boolean isSet(String value) {
            return value != null && !value.isBlank();
        }
    }

    /** OpenStreetMap's address search. Its usage policy needs a real User-Agent and at most 1 request a second. */
    public record Nominatim(String baseUrl, String userAgent, String countryCodes) {
    }
}
