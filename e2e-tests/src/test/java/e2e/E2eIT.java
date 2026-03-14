package e2e;

import com.intuit.karate.junit5.Karate;

/**
 * Runner E2E — lance tous les scenarios end-to-end.
 *
 * Ce module ne gere AUCUNE infra. L'app doit etre demarree avant.
 *
 * Usage :
 *   # env local (defaut : localhost:8081 / localhost:8080)
 *   cd e2e-tests && mvn verify
 *
 *   # env custom
 *   mvn verify -DbaseUrl=http://my-server:8081 -DkeycloakUrl=http://my-kc:8080
 */
class E2eIT {

    @Karate.Test
    Karate testAll() {
        return Karate.run("classpath:e2e")
                .relativeTo(getClass());
    }
}
