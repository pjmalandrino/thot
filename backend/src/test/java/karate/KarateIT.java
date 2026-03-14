package karate;

import com.intuit.karate.junit5.Karate;

/**
 * Runner pour les tests d'integration Karate.
 * Execute via : mvn verify -P integration-test
 *
 * Pre-requis : docker compose up -d postgres keycloak backend
 */
class KarateIT {

    @Karate.Test
    Karate testAll() {
        return Karate.run("classpath:karate")
                .relativeTo(getClass());
    }
}
