package io.managed.services.test.kafka;

import com.openshift.cloud.api.kas.auth.models.ConfigEntry;
import com.openshift.cloud.api.kas.auth.models.NewTopicInput;
import com.openshift.cloud.api.kas.auth.models.TopicSettings;
import com.openshift.cloud.api.kas.models.KafkaRequest;
import io.fabric8.kubernetes.api.model.Quantity;
import io.managed.services.test.Environment;
import io.managed.services.test.TestBase;
import io.managed.services.test.TestGroups;
import io.managed.services.test.TestUtils;
import io.managed.services.test.client.ApplicationServicesApi;
import io.managed.services.test.client.exception.ApiGenericException;
import io.managed.services.test.client.exception.ApiLockedException;
import io.managed.services.test.client.exception.ApiUnauthorizedException;
import io.managed.services.test.client.kafka.KafkaAuthMethod;
import io.managed.services.test.client.kafka.KafkaConsumerClient;
import io.managed.services.test.client.kafka.KafkaMessagingUtils;
import io.managed.services.test.client.kafka.KafkaProducerClient;
import io.managed.services.test.client.kafkainstance.KafkaInstanceApi;
import io.managed.services.test.client.kafkainstance.KafkaInstanceApiAccessUtils;
import io.managed.services.test.client.kafkainstance.KafkaInstanceApiUtils;
import io.managed.services.test.client.kafkamgmt.KafkaMgmtApi;
import io.managed.services.test.client.kafkamgmt.KafkaMgmtApiUtils;
import io.managed.services.test.client.securitymgmt.SecurityMgmtAPIUtils;
import io.managed.services.test.client.securitymgmt.SecurityMgmtApi;
import io.vertx.core.Vertx;
import lombok.SneakyThrows;
import org.apache.kafka.common.errors.RecordTooLargeException;
import org.apache.kafka.common.serialization.StringSerializer;
import static org.testng.Assert.assertThrows;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import static io.managed.services.test.TestUtils.assumeTeardown;
import static io.managed.services.test.TestUtils.bwait;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

/**
 * Test the main endpoints of the kafka-admin-api[1] that is deployed alongside each Kafka Instance
 * and used to administer the Kafka Instance itself.
 * <p>
 * 1. https://github.com/bf2fc6cc711aee1a0c2a/kafka-admin-api
 * <p>
 * <b>Requires:</b>
 * <ul>
 *     <li> PRIMARY_OFFLINE_TOKEN
 * </ul>
 */
public class KafkaInstanceAPITest extends TestBase {
    private static final Logger LOGGER = LogManager.getLogger(KafkaInstanceAPITest.class);

    private static final String KAFKA_INSTANCE_NAME = "test-instance";
    private static final String SERVICE_ACCOUNT_NAME = "mk-e2e-kaa-sa-"  + Environment.LAUNCH_SUFFIX;
    private static final String TEST_TOPIC_NAME = "test-api-topic-1";
    private static final String TEST_NOT_EXISTING_TOPIC_NAME = "test-api-topic-not-exist";

    private static final String TEST_GROUP_NAME = "test-consumer-group";
    private static final String TEST_NOT_EXISTING_GROUP_NAME = "not-existing-group";

    private final Vertx vertx = Vertx.vertx();

    private KafkaInstanceApi kafkaInstanceApi;
    private KafkaMgmtApi kafkaMgmtApi;
    private SecurityMgmtApi securityMgmtApi;
    private KafkaRequest kafka;
    private KafkaConsumerClient<String, String> kafkaConsumer;

    // TODO: Test update topic with random values

    @BeforeClass(alwaysRun = true)
    @SneakyThrows
    public void bootstrap() {
        assertNotNull(Environment.PRIMARY_OFFLINE_TOKEN, "the PRIMARY_OFFLINE_TOKEN env is null");

        var apps = ApplicationServicesApi.applicationServicesApi(Environment.PRIMARY_OFFLINE_TOKEN);
        kafkaMgmtApi = apps.kafkaMgmt();
        securityMgmtApi = apps.securityMgmt();
        LOGGER.info("kafka and security mgmt api initialized");

        kafka = KafkaMgmtApiUtils.applyKafkaInstance(kafkaMgmtApi, KAFKA_INSTANCE_NAME);

        kafkaInstanceApi = KafkaInstanceApiUtils.kafkaInstanceApi(kafka, Environment.PRIMARY_OFFLINE_TOKEN);
        LOGGER.info("kafka instance api client initialized");
    }

    @AfterClass(alwaysRun = true)
    public void teardown() {
        assumeTeardown();

        // delete kafka instance
        // TODO enterprise : clean kafka instance only if it is not enterprise testing or we don't want to skip it
        if (!(Environment.SKIP_KAFKA_TEARDOWN || Environment.IS_ENTERPRISE)) {
            try {
                KafkaMgmtApiUtils.cleanKafkaInstance(kafkaMgmtApi, KAFKA_INSTANCE_NAME);
            } catch (Throwable t) {
                LOGGER.error("failed to clean kafka instance: ", t);
            }
        }

        // delete service account
        try {
            SecurityMgmtAPIUtils.cleanServiceAccount(securityMgmtApi, SERVICE_ACCOUNT_NAME);
        } catch (Throwable t) {
            LOGGER.error("failed to clean service account: ", t);
        }

        try {
            if (kafkaConsumer != null) {
                bwait(kafkaConsumer.asyncClose());
            }
        } catch (Throwable t) {
            LOGGER.error("failed to close consumer: ", t);
        }

        try {
            bwait(vertx.close());
        } catch (Throwable t) {
            LOGGER.error("failed to close vertx: ", t);
        }
    }

    @Test(groups = TestGroups.INTEGRATION)
    @SneakyThrows
    public void testFailToCallAPIIfUserBelongsToADifferentOrganization() {
        var kafkaInstanceApi = KafkaInstanceApiUtils.kafkaInstanceApi(kafka, Environment.ALIEN_OFFLINE_TOKEN);
        assertThrows(ApiUnauthorizedException.class, () -> kafkaInstanceApi.getTopics());
    }

    @Test(groups = TestGroups.INTEGRATION)
    @SneakyThrows
    public void testFailToCallAPIIfTokenIsInvalid() {
        var api = KafkaInstanceApiUtils.kafkaInstanceApi(kafka, TestUtils.FAKE_TOKEN);
        assertThrows(Exception.class, api::getTopics);
    }

    @Test(groups = {"pr-check", TestGroups.INTEGRATION})
    @SneakyThrows
    public void testCreateTopic() {

        // getting test-topic should fail because the topic shouldn't exist
        assertThrows(Exception.class, () -> kafkaInstanceApi.getTopic(TEST_TOPIC_NAME));
        LOGGER.info("topic '{}' not found", TEST_TOPIC_NAME);

        LOGGER.info("create topic '{}'", TEST_TOPIC_NAME);
        var payload = new NewTopicInput();
        payload.setName(TEST_TOPIC_NAME);
        var settings = new TopicSettings();
        settings.setNumPartitions(1);
        payload.setSettings(settings);
        var topic = kafkaInstanceApi.createTopic(payload);
        LOGGER.debug(topic);
    }

    @Test(groups = TestGroups.INTEGRATION)
    @SneakyThrows
    public void testMaxMessageSizeLimit() {

        // 1 MB
        final int maxMessageSize = 1_000_000;

        LOGGER.info("create or retrieve service account '{}'", SERVICE_ACCOUNT_NAME);
        var serviceAccount = SecurityMgmtAPIUtils.applyServiceAccount(securityMgmtApi, SERVICE_ACCOUNT_NAME);

        var bootstrapHost = kafka.getBootstrapServerHost();
        var clientID = serviceAccount.getClientId();
        var clientSecret = serviceAccount.getClientSecret();

        KafkaProducerClient<String, String> kafkaProducerClient = new KafkaProducerClient(
                Vertx.vertx(),
                bootstrapHost,
                clientID,
                clientSecret,
                KafkaAuthMethod.OAUTH,
                StringSerializer.class,
                StringSerializer.class
        );
        LOGGER.info("send message");
        bwait(KafkaMessagingUtils.sendSingleMessage(kafkaProducerClient, TEST_TOPIC_NAME, maxMessageSize));
        kafkaProducerClient.close();

    }

    @Test(groups = TestGroups.INTEGRATION)
    @SneakyThrows
    public void testFailToProduceMessageAboveSizeLimit() {

        // 1.5 MB, which is above the max limit of message size
        final int messageSize = 1_500_000;

        LOGGER.info("create or retrieve service account '{}'", SERVICE_ACCOUNT_NAME);
        var serviceAccount = SecurityMgmtAPIUtils.applyServiceAccount(securityMgmtApi, SERVICE_ACCOUNT_NAME);

        var bootstrapHost = kafka.getBootstrapServerHost();
        var clientID = serviceAccount.getClientId();
        var clientSecret = serviceAccount.getClientSecret();

        // fail to send single message and close production
        try (KafkaProducerClient<String, String> kafkaProducerClient = new KafkaProducerClient(
                Vertx.vertx(),
                bootstrapHost,
                clientID,
                clientSecret,
                KafkaAuthMethod.OAUTH,
                StringSerializer.class,
                StringSerializer.class)) {
            LOGGER.info("try to send too big message");
            assertThrows(RecordTooLargeException.class, () -> bwait(KafkaMessagingUtils.sendSingleMessage(kafkaProducerClient, TEST_TOPIC_NAME, messageSize)));
        }

    }

    @Test(dependsOnMethods = "testCreateTopic", groups = TestGroups.INTEGRATION)
    public void testFailToCreateTopicIfItAlreadyExist() {
        // create existing topic should fail
        var payload = new NewTopicInput();
        payload.setName(TEST_TOPIC_NAME);
        var settings = new TopicSettings();
        settings.setNumPartitions(1);
        payload.setSettings(settings);
        assertThrows(ApiGenericException.class,
            () -> kafkaInstanceApi.createTopic(payload));
    }

    private static ConfigEntry newCE(String key, String value) {
        var ce = new ConfigEntry();
        ce.setKey(key);
        ce.setValue(value);
        return ce;
    }

    @DataProvider(name = "policyData")
    public Object[][] policyData() {
        final int tenMi = Quantity.getAmountInBytes(Quantity.parse("10Mi")).intValue();
        final int fiftyMi = Quantity.getAmountInBytes(Quantity.parse("50Mi")).intValue();

        int messageSizeLimit, desiredBrokerCount;
        try {
            messageSizeLimit = KafkaMgmtApiUtils.getMessageSizeLimit(kafkaMgmtApi, kafka);
            desiredBrokerCount = KafkaMgmtApiUtils.getDesiredBrokerCount(kafkaMgmtApi, kafka);
        } catch (Exception e) {
            // Fallback for kas-installer installed environments, see: https://github.com/bf2fc6cc711aee1a0c2a/kas-installer/issues/202
            LOGGER.warn("Failed to read metrics, falling back to constants instead");
            messageSizeLimit = 1048588;
            desiredBrokerCount = 3;
        }

        return new Object[][] {
                {true, newCE("compression.type", "producer")}, // default permitted
                {false, newCE("compression.type", "gzip")},

                {true, newCE("file.delete.delay.ms", "60000")}, // default permitted
                {false, newCE("file.delete.delay.ms", "1")},

                {true, newCE("flush.messages", Long.toString(Long.MAX_VALUE))}, // default permitted
                {false, newCE("flush.messages", "1")},

                {true, newCE("flush.ms", Long.toString(Long.MAX_VALUE))}, // default permitted
                {false, newCE("flush.ms", "1")},

                {false, newCE("follower.replication.throttled.replicas", "*")},
                {false, newCE("follower.replication.throttled.replicas", "1:1")},

                {true, newCE("index.interval.bytes", "4096")}, // default permitted
                {false, newCE("index.interval.bytes", "1")},

                {false, newCE("leader.replication.throttled.replicas", "*")},
                {false, newCE("leader.replication.throttled.replicas", "1:1")},

                {true, newCE("max.message.bytes", Integer.toString(messageSizeLimit))},
                {true, newCE("max.message.bytes", "1")},
                {true, newCE("max.message.bytes", Integer.toString(messageSizeLimit - 1))},
                {false, newCE("max.message.bytes", Integer.toString(messageSizeLimit + 1))},

                {false, newCE("message.format.version", "3.0")},
                {false, newCE("message.format.version", "2.8")},
                {false, newCE("message.format.version", "2.1")},

                {true, newCE("min.cleanable.dirty.ratio", "0.5")},
                {false, newCE("min.cleanable.dirty.ratio", "0")},
                {false, newCE("min.cleanable.dirty.ratio", "1")},

                {desiredBrokerCount > 2, newCE("min.insync.replicas", desiredBrokerCount > 2 ? "2" : "1")},
                {desiredBrokerCount < 3, newCE("min.insync.replicas", "1")},

                {true, newCE("segment.bytes", Integer.toString(fiftyMi))},
                {true, newCE("segment.bytes", Integer.toString(fiftyMi + 1))},
                {false, newCE("segment.bytes", Integer.toString(fiftyMi - 1))},
                {false, newCE("segment.bytes", Integer.toString(1))},

                {true, newCE("segment.index.bytes", Integer.toString(tenMi))},
                {false, newCE("segment.index.bytes", "1")},

                {false, newCE("segment.jitter.ms", "0")},
                {false, newCE("segment.jitter.ms", "1")},

                {true, newCE("segment.ms", Long.toString(Duration.ofDays(7).toMillis()))},
                {true, newCE("segment.ms", Long.toString(Duration.ofMinutes(10).toMillis()))},
                {false, newCE("segment.ms", Long.toString(Duration.ofMinutes(10).toMillis() - 1))},

                {true, newCE("unclean.leader.election.enable", "false")},
                {false, newCE("unclean.leader.election.enable", "true")},
        };
    }

    @Test(dataProvider = "policyData")
    @SneakyThrows
    public void testCreateTopicEnforcesPolicy(boolean allowed, ConfigEntry configEntry) {
        String testTopicName = UUID.randomUUID().toString();
        var createSettings = new TopicSettings();
        createSettings.setConfig(List.of(configEntry));
        var payload = new NewTopicInput();
        payload.setName(testTopicName);
        payload.setSettings(createSettings);
        try {
            if (allowed) {
                // create should success without exception
                kafkaInstanceApi.createTopic(payload);
            } else {
                // create should cause exception
                assertThrows(ApiGenericException.class,
                        () -> {
                            kafkaInstanceApi.createTopic(payload);
                        });
            }
        } finally {
            try {
                kafkaInstanceApi.deleteTopic(testTopicName);
            } catch (ApiGenericException ignored) {
               // ignore
            }
        }
    }

    @Test(dataProvider = "policyData")
    @SneakyThrows
    public void testAlterTopicEnforcesPolicy(boolean allowed, ConfigEntry configEntry) {
        var testTopicName = UUID.randomUUID().toString();
        var updateSettings = new TopicSettings();
        updateSettings.setConfig(List.of(configEntry));

        try {
            var topicInput = new NewTopicInput();
            topicInput.setName(testTopicName);
            topicInput.setSettings(new TopicSettings());
            kafkaInstanceApi.createTopic(topicInput);
            var first = kafkaInstanceApi.getTopics().getItems().stream().filter(topic -> testTopicName.equals(topic.getName())).findFirst();
            assertTrue(first.isPresent(), "failed to create topic before test");

            if (allowed) {
                // update should success without exception
                kafkaInstanceApi.updateTopic(testTopicName, updateSettings);
            } else {
                // update should cause exception
                assertThrows(ApiGenericException.class,
                        () -> {
                            kafkaInstanceApi.updateTopic(testTopicName, updateSettings);
                        });
            }
        } finally {
            try {
                kafkaInstanceApi.deleteTopic(testTopicName);
            } catch (ApiGenericException ignored) {
               // ignore
            }
        }
    }

    @Test(dependsOnMethods = "testCreateTopic", groups = {"pr-check", TestGroups.INTEGRATION})
    @SneakyThrows
    public void testGetTopicByName() {
        var topic = kafkaInstanceApi.getTopic("rama");
        LOGGER.debug(topic);
        assertEquals(topic.getName(), TEST_TOPIC_NAME);
    }

    @Test(dependsOnMethods = "testCreateTopic", groups = TestGroups.INTEGRATION)
    public void testFailToGetTopicIfItDoesNotExist() {
        // get none existing topic should fail
        assertThrows(ApiGenericException.class,
            () -> kafkaInstanceApi.getTopic(TEST_NOT_EXISTING_TOPIC_NAME));
    }

    @Test(dependsOnMethods = "testCreateTopic", groups = TestGroups.INTEGRATION)
    @SneakyThrows
    public void tetGetAllTopics() {
        var topics = kafkaInstanceApi.getTopics();
        LOGGER.debug(topics);

        var filteredTopics = Objects.requireNonNull(topics.getItems())
            .stream()
            .filter(k -> TEST_TOPIC_NAME.equals(k.getName()))
            .findAny();

        assertTrue(filteredTopics.isPresent());
    }

    @Test(groups = TestGroups.INTEGRATION)
    @SneakyThrows
    public void testFailToDeleteTopicIfItDoesNotExist() {
        // deleting not existing topic should fail
        assertThrows(ApiGenericException.class,
            () -> kafkaInstanceApi.deleteTopic(TEST_NOT_EXISTING_TOPIC_NAME));
    }

    @Test(dependsOnMethods = "testCreateTopic", groups = {"pr-check", TestGroups.INTEGRATION})
    @SneakyThrows
    public void testConsumerGroup() {
        LOGGER.info("create or retrieve service account '{}'", SERVICE_ACCOUNT_NAME);
        var account = SecurityMgmtAPIUtils.applyServiceAccount(securityMgmtApi, SERVICE_ACCOUNT_NAME);

        LOGGER.info("grant access to the service account '{}'", SERVICE_ACCOUNT_NAME);
        KafkaInstanceApiAccessUtils.createProducerAndConsumerACLs(kafkaInstanceApi, KafkaInstanceApiAccessUtils.toPrincipal(account.getClientId()));

        kafkaConsumer = bwait(KafkaInstanceApiUtils.startConsumerGroup(vertx,
            TEST_GROUP_NAME,
            TEST_TOPIC_NAME,
            kafka.getBootstrapServerHost(),
            account.getClientId(),
            account.getClientSecret()));

        var group = KafkaInstanceApiUtils.waitForConsumerGroup(kafkaInstanceApi, TEST_GROUP_NAME);
        LOGGER.debug(group);

        group = KafkaInstanceApiUtils.waitForConsumersInConsumerGroup(kafkaInstanceApi, group.getGroupId());
        LOGGER.debug(group);

        assertEquals(group.getGroupId(), TEST_GROUP_NAME);
        assertTrue(group.getConsumers().size() > 0);
    }

    @Test(dependsOnMethods = "testConsumerGroup", groups = {"pr-check", TestGroups.INTEGRATION})
    @SneakyThrows
    public void testGetAllConsumerGroups() {
        var groups = kafkaInstanceApi.getConsumerGroups();
        LOGGER.debug(groups);

        var filteredGroup = Objects.requireNonNull(groups.getItems())
            .stream()
            .filter(g -> TEST_GROUP_NAME.equals(g.getGroupId()))
            .findAny();

        assertTrue(filteredGroup.isPresent());
    }

    @Test(dependsOnMethods = "testConsumerGroup", groups = TestGroups.INTEGRATION)
    public void testFailToGetConsumerGroupIfItDoesNotExist() {
        // get consumer group non-existing consumer group should fail
        assertThrows(ApiGenericException.class,
            () -> kafkaInstanceApi.getConsumerGroupById(TEST_NOT_EXISTING_GROUP_NAME));
    }

    @Test(dependsOnMethods = "testConsumerGroup", groups = TestGroups.INTEGRATION)
    public void testFailToDeleteConsumerGroupIfItIsActive() {
        // deleting active consumer group should fail
        assertThrows(ApiLockedException.class,
            () -> kafkaInstanceApi.deleteConsumerGroupById(TEST_GROUP_NAME));
    }

    @Test(dependsOnMethods = "testConsumerGroup", groups = TestGroups.INTEGRATION)
    public void testFailToDeleteConsumerGroupIfItDoesNotExist() {
        // deleting not existing consumer group should fail
        assertThrows(ApiGenericException.class,
            () -> kafkaInstanceApi.deleteConsumerGroupById(TEST_NOT_EXISTING_GROUP_NAME));
    }

    @Test(dependsOnMethods = "testConsumerGroup", priority = 1, groups = {"pr-check", TestGroups.INTEGRATION})
    public void testDeleteConsumerGroup() throws Throwable {
        LOGGER.info("close kafka consumer");
        bwait(kafkaConsumer.asyncClose());

        LOGGER.info("delete consumer group '{}'", TEST_GROUP_NAME);
        kafkaInstanceApi.deleteConsumerGroupById(TEST_GROUP_NAME);

        // consumer group should have been deleted
        assertThrows(Exception.class,
            () -> kafkaInstanceApi.getConsumerGroupById(TEST_GROUP_NAME));
        LOGGER.info("consumer group '{}' not found", TEST_GROUP_NAME);
    }

    @Test(dependsOnMethods = "testCreateTopic", priority = 2, groups = {"pr-check", TestGroups.INTEGRATION})
    public void testDeleteTopic() throws Throwable {
        kafkaInstanceApi.deleteTopic(TEST_TOPIC_NAME);
        LOGGER.info("topic '{}' deleted", TEST_TOPIC_NAME);

        // get test-topic should fail due to topic being deleted in current test
        assertThrows(Exception.class,
            () -> kafkaInstanceApi.getTopic(TEST_TOPIC_NAME));
        LOGGER.info("topic '{}' not found", TEST_TOPIC_NAME);
    }
}
