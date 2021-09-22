package io.managed.services.test.client;

import io.managed.services.test.Environment;
import io.managed.services.test.client.kafkamgmt.KafkaMgmtApi;
import io.managed.services.test.client.oauth.KeycloakOAuth;
import io.managed.services.test.client.registrymgmt.RegistryMgmtApi;
import io.managed.services.test.client.securitymgmt.SecurityMgmtApi;
import io.vertx.ext.auth.User;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;

import static io.managed.services.test.TestUtils.bwait;

@Log4j2
public class ApplicationServicesApi {

    private final KafkaMgmtApi kafkaMgmtApi;
    private final SecurityMgmtApi securityMgmtApi;
    private final RegistryMgmtApi registryMgmtApi;

    public ApplicationServicesApi(String basePath, User user) {
        this(basePath, KeycloakOAuth.getToken(user));
    }

    public ApplicationServicesApi(String basePath, String token) {
        this(new KasApiClient().basePath(basePath).bearerToken(token),
            new SrsApiClient().basePath(basePath).bearerToken(token));
    }

    public ApplicationServicesApi(KasApiClient kasApiClient, SrsApiClient srsApiClient) {
        this.kafkaMgmtApi = new KafkaMgmtApi(kasApiClient.getApiClient());
        this.securityMgmtApi = new SecurityMgmtApi(kasApiClient.getApiClient());
        this.registryMgmtApi = new RegistryMgmtApi(srsApiClient.getApiClient());
    }

    public static ApplicationServicesApi applicationServicesApi(String username, String password) {
        return applicationServicesApi(Environment.OPENSHIFT_API_URI, username, password);
    }

    public static ApplicationServicesApi applicationServicesApi(KeycloakOAuth auth) {
        return applicationServicesApi(auth, Environment.OPENSHIFT_API_URI);
    }

    public static ApplicationServicesApi applicationServicesApi(String basePath, String username, String password) {
        return applicationServicesApi(new KeycloakOAuth(username, password), basePath);
    }

    @SneakyThrows
    public static ApplicationServicesApi applicationServicesApi(KeycloakOAuth auth, String basePath) {

        log.info("authenticate user '{}' against RH SSO", auth.getUsername());
        var user = bwait(auth.loginToRedHatSSO());
        return new ApplicationServicesApi(basePath, user);
    }

    public KafkaMgmtApi kafkaMgmt() {
        return kafkaMgmtApi;
    }

    public SecurityMgmtApi securityMgmt() {
        return securityMgmtApi;
    }

    public RegistryMgmtApi registryMgmt() {
        return registryMgmtApi;
    }
}
