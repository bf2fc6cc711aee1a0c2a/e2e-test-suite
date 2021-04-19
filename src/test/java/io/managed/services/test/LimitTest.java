package io.managed.services.test;

import io.managed.services.test.client.exception.ResponseException;
import io.managed.services.test.client.serviceapi.CreateServiceAccountPayload;
import io.managed.services.test.client.serviceapi.ServiceAPI;
import io.managed.services.test.client.serviceapi.ServiceAPIUtils;
import io.managed.services.test.framework.TestTag;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.junit5.Timeout;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.extension.ExtendWith;

import java.net.HttpURLConnection;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assumptions.assumeTrue;


@Tag(TestTag.SERVICE_API)
@ExtendWith(VertxExtension.class)
@Timeout(value = 5, timeUnit = TimeUnit.MINUTES)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class LimitTest extends TestBase {
    private static final Logger LOGGER = LogManager.getLogger(LimitTest.class);

    ServiceAPI api;
    final int SA_LIMIT = 3;
    final String SERVICE_ACCOUNT_NAME_PATTERN = "mk-e2e-sa-" + Environment.KAFKA_POSTFIX_NAME;

    @BeforeAll
    void bootstrap(Vertx vertx, VertxTestContext context) {
        ServiceAPIUtils.serviceAPI(vertx, Environment.SSO_SECONDARY_USERNAME, Environment.SSO_SECONDARY_PASSWORD)
                .onSuccess(a -> api = a)
                .onComplete(context.succeedingThenComplete());
    }

    @AfterAll
    void teardown(Vertx vertx, VertxTestContext context) {
        ServiceAPIUtils.deleteServiceAccountsByOwnerIfExists(api, Environment.SSO_SECONDARY_USERNAME)
                .onComplete(context.succeedingThenComplete());
    }

    void assertAPI() {
        assumeTrue(api != null, "api is null because the bootstrap has failed");
    }

    @Test
    void testLimitServiceAccount(VertxTestContext context) {
        assertAPI();

        // Create Service Account payloads
        List<CreateServiceAccountPayload> payloads = IntStream.range(0, SA_LIMIT).boxed().map(i -> {
            CreateServiceAccountPayload serviceAccountPayload = new CreateServiceAccountPayload();
            serviceAccountPayload.name = SERVICE_ACCOUNT_NAME_PATTERN + "-" + i;
            return serviceAccountPayload;
        }).collect(Collectors.toList());


        ServiceAPIUtils.deleteServiceAccountsByOwnerIfExists(api, Environment.SSO_SECONDARY_USERNAME) //remove all SA owned by user
                .compose(__ -> api.createServiceAccount(payloads.get(0)))
                .onSuccess(__ ->LOGGER.info("Service account {} created", payloads.get(0).name))
                .compose(__ -> api.createServiceAccount(payloads.get(1)))
                .onSuccess(__ ->LOGGER.info("Service account {} created", payloads.get(1).name))
                .compose(__ -> api.createServiceAccount(payloads.get(2)))
                .recover(throwable -> {
                    if (throwable instanceof ResponseException && ((ResponseException) throwable).response.statusCode() == HttpURLConnection.HTTP_FORBIDDEN) {
                        LOGGER.info("Create service account outside limit failed");
                        return Future.succeededFuture();
                    }
                    return Future.failedFuture(throwable);
                })
                .onComplete(context.succeedingThenComplete());
    }
}
