package io.managed.services.test;

import io.managed.services.test.cli.CLI;
import io.managed.services.test.cli.CLIUtils;
import io.managed.services.test.cli.Platform;
import io.managed.services.test.cli.ProcessException;
import io.managed.services.test.client.github.GitHub;
import io.managed.services.test.client.serviceapi.KafkaResponse;
import io.managed.services.test.client.serviceapi.ServiceAccount;
import io.managed.services.test.executor.ExecBuilder;
import io.managed.services.test.framework.TestTag;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.file.OpenOptions;
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

import java.util.concurrent.TimeUnit;

import static io.managed.services.test.TestUtils.await;
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
    static final String CLI_NAME = "rhoas";
    static final String DOWNLOAD_ASSET_TEMPLATE = "%s_%s_%s.%s";
    static final String ARCHIVE_ENTRY_TEMPLATE = "%s_%s_%s/bin/%s";
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
    void testDownloadCLI(Vertx vertx) throws Exception {
        assertCredentials();

        workdir = await(vertx.fileSystem().createTempDirectory("cli"));
        LOGGER.info("workdir: {}", workdir);

        var client = new GitHub(vertx, Environment.BF2_GITHUB_TOKEN);

        LOGGER.info("retrieve release by tag '{}' in repository: '{}/{}'", Environment.CLI_VERSION, DOWNLOAD_ORG, DOWNLOAD_REPO);
        var release = await(client.getReleaseByTagName(DOWNLOAD_ORG, DOWNLOAD_REPO, Environment.CLI_VERSION));

        final var downloadAsset = String.format(DOWNLOAD_ASSET_TEMPLATE, CLI_NAME, release.tagName, Environment.CLI_ARCH, Platform.getArch().equals(Platform.WINDOWS) ? "zip" : "tar.gz");
        LOGGER.info("search for asset '{}' in release: '{}'", downloadAsset, release.toString());
        var asset = release.assets.stream()
                .filter(a -> a.name.equals(downloadAsset))
                .findFirst().orElseThrow();

        final var archive = workdir + "/cli." + (Platform.getArch().equals(Platform.WINDOWS) ? "zip" : "tar.gz");
        LOGGER.info("download asset '{}' to '{}'", asset.toString(), archive);
        var archiveFile = await(vertx.fileSystem().open(archive, new OpenOptions()
                .setCreate(true)
                .setAppend(false)));
        await(client.downloadAsset(DOWNLOAD_ORG, DOWNLOAD_REPO, asset.id, archiveFile));

        final var cli = workdir + "/" + CLI_NAME;
        final var entry = String.format(ARCHIVE_ENTRY_TEMPLATE, CLI_NAME, release.tagName, Environment.CLI_ARCH, CLI_NAME);
        LOGGER.info("extract {} from archive {} to: {}", entry, archive, cli);
        extractCLI(archive, entry, cli);

        // make the cli executable
        if (!Platform.getArch().equals(Platform.WINDOWS)) {
            await(vertx.fileSystem().chmod(cli, "rwxr-xr-x"));
        }

        this.cli = new CLI(workdir, CLI_NAME);

        LOGGER.info("validate cli");
        new ExecBuilder()
                .withCommand(cli, "--help")
                .logToOutput(true)
                .throwErrors(true)
                .exec();
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
    void testCreateKafkaInstance(Vertx vertx) {
        assertLoggedIn();
        assertServiceAccount();

        LOGGER.info("Create kafka cluster with name {}", KAFKA_INSTANCE_NAME);
        kafkaInstance = await(cli.createKafka(KAFKA_INSTANCE_NAME));
        LOGGER.info("Created kafka cluster {} with id {}", kafkaInstance.name, kafkaInstance.id);
        await(waitForKafkaReady(vertx, cli, kafkaInstance.id));
        LOGGER.info("Kafka cluster {} with id {} is ready", kafkaInstance.name, kafkaInstance.id);
    }

    @Test
    @Order(5)
    void testGetStatusOfKafkaInstance() {
        assertLoggedIn();
        assertKafka();

        LOGGER.info("Get kafka instance with name {}", KAFKA_INSTANCE_NAME);
        KafkaResponse getKafka = await(cli.describeKafka(kafkaInstance.id));
        assertEquals("ready", getKafka.status);
        LOGGER.info("Found kafka instance {} with id {}", getKafka.name, getKafka.id);
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
    void testCreateAlreadyCreatedKafka() {
        assertLoggedIn();
        assertKafka();

        await(cli.createKafka(KAFKA_INSTANCE_NAME)
                .compose(r -> Future.failedFuture("Create kafka with same name should fail"))
                .recover(throwable -> {
                    if (throwable instanceof Exception) {
                        LOGGER.info("Create kafka with already exists name failed");
                        return Future.succeededFuture();
                    }
                    return Future.failedFuture(throwable);
                })
        );
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
    void testDeleteKafkaInstance(Vertx vertx) {
        assertLoggedIn();
        assertKafka();

        LOGGER.info("Delete kafka instance {} with id {}", kafkaInstance.name, kafkaInstance.id);
        await(cli.deleteKafka(kafkaInstance.id));
        await(waitForKafkaDelete(vertx, cli, kafkaInstance.name));
    }
}
