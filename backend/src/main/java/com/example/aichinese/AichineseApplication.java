package com.example.aichinese;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Spring Boot entry point for the AI-Powered Interactive Chinese
 * Learning Platform backend.
 *
 * <p>{@link SpringBootApplication} enables component scanning of every
 * sub-package under {@code com.example.aichinese} so the repository /
 * service / controller / config / security / provider / mapper /
 * exception / util modules are all picked up automatically. No
 * additional {@code @ComponentScan} or {@code @EntityScan} is needed
 * because the root package matches the project layout.</p>
 *
 * <h2>Startup-time fail-fast guards</h2>
 * <p>Several beans run validators that refuse to start when their
 * critical configuration is missing or unsafe:</p>
 * <ul>
 *   <li>{@link com.example.aichinese.config.AiProductionGuard} - AI
 *       provider settings in production.</li>
 *   <li>{@link com.example.aichinese.config.PaymentProductionGuard} -
 *       payment provider (Stripe live-mode) configuration.</li>
 *   <li>{@link com.example.aichinese.config.EmailConfiguration} -
 *       SMTP settings when email is enabled.</li>
 *   <li>{@link com.example.aichinese.security.JwtService} - JWT
 *       signing secret length / format.</li>
 *   <li>{@link com.example.aichinese.config.CorsConfig} - wildcard +
 *       credentials rejection in production.</li>
 *   <li>{@link com.example.aichinese.config.DeploymentTopologyGuard} -
 *       multi-instance deployment safety checks.</li>
 * </ul>
 * <p>A misconfigured production deployment fails to boot rather than
 * silently serving traffic in an unsafe state.</p>
 */
@SpringBootApplication
public class AichineseApplication {

    public static void main(String[] args) {
        SpringApplication.run(AichineseApplication.class, args);
    }

}
