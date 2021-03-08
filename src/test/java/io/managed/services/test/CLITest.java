package io.managed.services.test;

import io.managed.services.test.cli.CLI;
import io.managed.services.test.cli.CLIDownloader;
import io.managed.services.test.cli.CLIUtils;
import io.managed.services.test.cli.ProcessException;
import io.managed.services.test.client.serviceapi.KafkaResponse;
import io.managed.services.test.client.serviceapi.ServiceAccount;
import io.managed.services.test.framework.TestTag;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.junit5.Timeout;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static io.managed.services.test.cli.CLIUtils.deleteKafkaByNameIfExists;
import static io.managed.services.test.cli.CLIUtils.deleteServiceAccountByNameIfExists;
import static io.managed.services.test.cli.CLIUtils.extractCLI;
import static io.managed.services.test.cli.CLIUtils.waitForKafkaDelete;
import static io.managed.services.test.cli.CLIUtils.waitForKafkaReady;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeTrue;


@Tag(TestTag.CLI)
@ExtendWith(VertxExtension.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class CLITest extends TestBase {
    private static final Logger LOGGER = LogManager.getLogger(CLITest.class);

    static final String DOWNLOAD_ORG = "bf2fc6cc711aee1a0c2a";
    static final String DOWNLOAD_REPO = "cli";
    static final String KAFKA_INSTANCE_NAME = "cli-e2e-test-instance-" + Environment.KAFKA_POSTFIX_NAME;
    static final String SERVICE_ACCOUNT_NAME = "cli-e2e-service-account-" + Environment.KAFKA_POSTFIX_NAME;
    static final String TOPIC_NAME = "cli-e2e-test-topic";

    String workdir;
    CLI cli;
    boolean loggedIn;
    KafkaResponse kafkaInstance;
    ServiceAccount serviceAccount;
    String topic;

    @AfterAll
    void clean(Vertx vertx, VertxTestContext context) {

        Future.succeededFuture()

                // 1. delete the kafka instance and service account by name if it exists
                .compose(__ -> Optional.ofNullable(cli)
                        .map(c -> deleteServiceAccountByNameIfExists(c, SERVICE_ACCOUNT_NAME)
                                .compose(___ -> deleteKafkaByNameIfExists(c, KAFKA_INSTANCE_NAME)))
                        .orElse(Future.succeededFuture()))

                // 2. logout form the cli
                .compose(__ -> Optional.ofNullable(cli)
                        .map(c -> {
                            LOGGER.info("log-out from the CLI");
                            return c.logout().recover(t -> {
                                LOGGER.error("logout failed with error:", t);
                                return Future.succeededFuture();
                            });
                        })
                        .orElse(Future.succeededFuture()))

                // 3. delete workdir after logout if it exists
                .compose(__ -> Optional.ofNullable(workdir)
                        .map(w -> {
                            LOGGER.info("delete workdir: {}", workdir);
                            return vertx.fileSystem().deleteRecursive(workdir, true)
                                    .recover(t -> {
                                        LOGGER.error("failed to delete workdir {} with error:", workdir, t);
                                        return Future.succeededFuture();
                                    });
                        })
                        .orElse(Future.succeededFuture()))

                .onComplete(context.succeedingThenComplete());
    }

    void assertCLI() {
        assumeTrue(cli != null, "cli is null because the bootstrap has failed");
    }

    void assertLoggedIn() {
        assumeTrue(loggedIn, "cli is not logged in");
    }

    void assertKafka() {
        assumeTrue(kafkaInstance != null, "kafka is null because the testCreateKafkaInstance has failed to create the Kafka instance");
    }

    void assertCredentials() {
        assumeTrue(Environment.BF2_GITHUB_TOKEN != null, "the BF2_GITHUB_TOKEN env is null");
        assumeTrue(Environment.SSO_USERNAME != null, "the SSO_USERNAME env is null");
        assumeTrue(Environment.SSO_PASSWORD != null, "the SSO_PASSWORD env is null");
    }

    void assertServiceAccount() {
        assumeTrue(serviceAccount != null, "serviceAccount is null because the testCreateServiceAccount has failed to create the Service Account");
    }

    void assertTopic() {
        assumeTrue(topic != null, "topic is null because the testCreateTopic has failed to create the topic on the Kafka instance");
    }

    @Test
    @Order(1)
    void testDownloadCLI(Vertx vertx, VertxTestContext context) {
        assertCredentials();

        var downloader = new CLIDownloader(
                vertx,
                Environment.BF2_GITHUB_TOKEN,
                DOWNLOAD_ORG,
                DOWNLOAD_REPO,
                Environment.CLI_VERSION,
                Environment.CLI_PLATFORM,
                Environment.CLI_ARCH);

        // download the cli
        downloader.downloadCLIInTempDir()

                .compose(binary -> {
                    this.cli = new CLI(binary.directory, binary.name);

                    LOGGER.info("validate cli");
                    return cli.help();
                })

                .onComplete(context.succeedingThenComplete());
    }


    @Test
    @Order(2)
    @Timeout(value = 1, timeUnit = TimeUnit.MINUTES)
    void testLogin(Vertx vertx, VertxTestContext context) {
        assertCLI();

        LOGGER.info("verify that we aren't logged-in");
        cli.listKafka()
                .compose(r -> Future.failedFuture("cli kafka list should fail because we haven't log-in yet"))
                .recover(t -> {
                    if (t instanceof ProcessException) {
                        if (((ProcessException) t).process.exitValue() == 1) {
                            LOGGER.info("we haven't log-in yet");
                            return Future.succeededFuture();
                        }
                    }
                    return Future.failedFuture(t);
                })

                .compose(__ -> {
                    LOGGER.info("login the CLI");
                    return CLIUtils.login(vertx, cli, Environment.SSO_USERNAME, Environment.SSO_PASSWORD);
                })

                .compose(__ -> {

                    LOGGER.info("verify that we are logged-in");
                    return cli.listKafka();
                })

                .onSuccess(__ -> {
                    loggedIn = true;
                })

                .onComplete(context.succeedingThenComplete());
    }

    @Test
    @Order(3)
    void testCreateServiceAccount(Vertx vertx, VertxTestContext context) throws IOException {
        assertLoggedIn();
        CLIUtils.createServiceAccount(cli, SERVICE_ACCOUNT_NAME)
                .onSuccess(sa -> {
                    serviceAccount = sa.get();
                    LOGGER.info("Created serviceaccount {} with id {}", serviceAccount.name, serviceAccount.id);
                })
                .onComplete(context.succeedingThenComplete());
    }

    @Test
    @Order(4)
    void testCreateKafkaInstance(Vertx vertx, VertxTestContext context) {
        assertLoggedIn();
        assertServiceAccount();

        LOGGER.info("create kafka instance with name {}", KAFKA_INSTANCE_NAME);
        cli.createKafka(KAFKA_INSTANCE_NAME)
                .compose(kafka -> {
                    LOGGER.info("created kafka instance {} with id {}", kafka.name, kafka.id);
                    return waitForKafkaReady(vertx, cli, kafka.id)
                            .onSuccess(__ -> {
                                LOGGER.info("kafka instance {} with id {} is ready", kafka.name, kafka.id);
                                kafkaInstance = kafka;
                            });
                })

                .onComplete(context.succeedingThenComplete());
    }

    @Test
    @Order(5)
    void testGetStatusOfKafkaInstance(VertxTestContext context) {
        assertLoggedIn();
        assertKafka();

        LOGGER.info("Get kafka instance with name {}", KAFKA_INSTANCE_NAME);
        cli.describeKafka(kafkaInstance.id)
                .onSuccess(kafka -> context.verify(() -> {
                    assertEquals("ready", kafka.status);
                    LOGGER.info("found kafka instance {} with id {}", kafka.name, kafka.id);
                }))

                .onComplete(context.succeedingThenComplete());
    }

    @Test
    @Disabled("not implemented")
    @Order(6)
    void testCreateKafkaTopic(Vertx vertx) {
        //TODO
    }

    @Test
    @Disabled("not implemented")
    @Order(7)
    void testKafkaMessaging(Vertx vertx) {
        //TODO
    }

    @Test
    @Disabled("not implemented")
    @Order(8)
    void testUpdateKafkaTopic(Vertx vertx) {
        //TODO
    }

    @Test
    @Disabled("not implemented")
    @Order(9)
    void testMessagingOnUpdatedTopic(Vertx vertx) {
        //TODO
    }

    @Test
    @Disabled("not implemented")
    @Order(10)
    void testDeleteTopic(Vertx vertx) {
        //TODO
    }

    @Test
    @Order(11)
    void testCreateAlreadyCreatedKafka(VertxTestContext context) {
        assertLoggedIn();
        assertKafka();

        cli.createKafka(KAFKA_INSTANCE_NAME)
                .compose(r -> Future.failedFuture("Create kafka with same name should fail"))
                .recover(throwable -> {
                    if (throwable instanceof Exception) {
                        LOGGER.info("Create kafka with already exists name failed");
                        return Future.succeededFuture();
                    }
                    return Future.failedFuture(throwable);
                })

                .onComplete(context.succeedingThenComplete());
    }

    @Test
    @Order(12)
    void testDeleteServiceAccount(Vertx vertx, VertxTestContext context) {
        assertServiceAccount();
        cli.deleteServiceAccount(serviceAccount.id)
                .onSuccess(__ ->
                        LOGGER.info("Serviceaccount {} with id {} deleted", serviceAccount.name, serviceAccount.id))
                .onComplete(context.succeedingThenComplete());
    }

    @Test
    @Order(13)
    void testDeleteKafkaInstance(Vertx vertx, VertxTestContext context) {
        assertLoggedIn();
        assertKafka();

        LOGGER.info("Delete kafka instance {} with id {}", kafkaInstance.name, kafkaInstance.id);
        cli.deleteKafka(kafkaInstance.id)
                .compose(__ -> waitForKafkaDelete(vertx, cli, kafkaInstance.name))
                .onComplete(context.succeedingThenComplete());
    }
}
