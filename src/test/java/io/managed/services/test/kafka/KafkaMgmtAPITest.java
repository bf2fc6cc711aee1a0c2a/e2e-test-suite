package io.managed.services.test.kafka;

import com.openshift.cloud.api.kas.auth.models.NewTopicInput;
import com.openshift.cloud.api.kas.auth.models.TopicSettings;
import com.openshift.cloud.api.kas.models.KafkaRequest;
import com.openshift.cloud.api.kas.models.KafkaRequestPayload;
import com.openshift.cloud.api.kas.models.KafkaUpdateRequest;
import com.openshift.cloud.api.kas.models.ServiceAccount;
import com.openshift.cloud.api.kas.models.ServiceAccountRequest;
import io.managed.services.test.Environment;
import io.managed.services.test.TestBase;
import io.managed.services.test.TestUtils;
import io.managed.services.test.ThrowingFunction;
import io.managed.services.test.client.ApplicationServicesApi;
import io.managed.services.test.client.exception.ApiConflictException;
import io.managed.services.test.client.exception.ApiGenericException;
import io.managed.services.test.client.kafka.KafkaAdminUtils;
import io.managed.services.test.client.kafka.KafkaAuthMethod;
import io.managed.services.test.client.kafka.KafkaProducerClient;
import io.managed.services.test.client.kafkainstance.KafkaInstanceApi;
import io.managed.services.test.client.kafkainstance.KafkaInstanceApiAccessUtils;
import io.managed.services.test.client.kafkainstance.KafkaInstanceApiUtils;
import io.managed.services.test.client.kafkamgmt.KafkaMgmtApi;
import io.managed.services.test.client.kafkamgmt.KafkaMgmtApiUtils;
import io.managed.services.test.client.kafkamgmt.KafkaMgmtMetricsUtils;
import io.managed.services.test.client.securitymgmt.SecurityMgmtAPIUtils;
import io.managed.services.test.client.securitymgmt.SecurityMgmtApi;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.kafka.client.producer.KafkaProducerRecord;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import org.apache.kafka.common.KafkaException;
import org.apache.kafka.common.serialization.StringSerializer;
import org.javatuples.Pair;
import org.testng.SkipException;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

import static io.managed.services.test.TestUtils.assumeTeardown;
import static io.managed.services.test.TestUtils.bwait;
import static io.managed.services.test.TestUtils.waitFor;
import static io.managed.services.test.client.kafka.KafkaMessagingUtils.testTopic;
import static java.time.Duration.ofMinutes;
import static java.time.Duration.ofSeconds;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertThrows;
import static org.testng.Assert.assertTrue;


/**
 * Test Kafka Mgmt API
 *
 * <p>
 * <b>Requires:</b>
 * <ul>
 *     <li> PRIMARY_USERNAME
 *     <li> PRIMARY_PASSWORD
 * </ul>
 */
@Log4j2
public class KafkaMgmtAPITest extends TestBase {

    static final String SERVICE_ACCOUNT_NAME_FOR_DELETION = "mk-e2e-sa-delete" + Environment.LAUNCH_KEY;
    static final String KAFKA_INSTANCE_NAME = "mk-e2e-" + Environment.LAUNCH_KEY;
    static final String SERVICE_ACCOUNT_NAME = "mk-e2e-sa-" + Environment.LAUNCH_KEY;
    static final String TOPIC_NAME = "test-topic";
    static final String METRIC_TOPIC_NAME = "metric-test-topic";
    static final String[] KAFKA_METRICS = {
        "kafka_server_brokertopicmetrics_messages_in_total",
        "kafka_server_brokertopicmetrics_bytes_in_total",
        "kafka_server_brokertopicmetrics_bytes_out_total",
        "kubelet_volume_stats_available_bytes",
        "kubelet_volume_stats_used_bytes",
        "kafka_broker_quota_softlimitbytes",
        "kafka_broker_quota_totalstorageusedbytes",
        "kafka_controller_kafkacontroller_offline_partitions_count",
        "kafka_controller_kafkacontroller_global_partition_count",
        "kafka_topic:kafka_log_log_size:sum",
        "kafka_namespace:haproxy_server_bytes_in_total:rate5m",
        "kafka_namespace:haproxy_server_bytes_out_total:rate5m",
        "kafka_topic:kafka_topic_partitions:sum",
        "kafka_topic:kafka_topic_partitions:count",
        "consumergroup:kafka_consumergroup_members:count",
        "kafka_namespace:kafka_server_socket_server_metrics_connection_count:sum",
        "kafka_namespace:kafka_server_socket_server_metrics_connection_creation_rate:sum",
        "kafka_topic:kafka_server_brokertopicmetrics_messages_in_total:rate5m",
        "kafka_topic:kafka_server_brokertopicmetrics_bytes_in_total:rate5m",
        "kafka_topic:kafka_server_brokertopicmetrics_bytes_out_total:rate5m"
    };

    private KafkaMgmtApi kafkaMgmtApi;
    private SecurityMgmtApi securityMgmtApi;
    private KafkaRequest kafka;
    private ServiceAccount serviceAccount;
    private KafkaInstanceApi kafkaInstanceApi;


    @BeforeClass
    public void bootstrap() {
        assertNotNull(Environment.PRIMARY_USERNAME, "the PRIMARY_USERNAME env is null");
        assertNotNull(Environment.PRIMARY_PASSWORD, "the PRIMARY_PASSWORD env is null");

        var apps = ApplicationServicesApi.applicationServicesApi(
            Environment.PRIMARY_USERNAME,
            Environment.PRIMARY_PASSWORD);

        securityMgmtApi = apps.securityMgmt();
        kafkaMgmtApi = apps.kafkaMgmt();
    }

    @AfterClass(alwaysRun = true)
    public void teardown() {
        assumeTeardown();

        if (Environment.SKIP_KAFKA_TEARDOWN) {
            try {
                kafkaMgmtApi.updateKafka(kafka.getId(), new KafkaUpdateRequest().reauthenticationEnabled(true));
            } catch (Throwable t) {
                log.warn("resat kafka reauth error: ", t);
            }

            try {
                kafkaInstanceApi.deleteTopic(TOPIC_NAME);
            } catch (Throwable t) {
                log.warn("clean {} topic error: ", TOPIC_NAME, t);
            }

            try {
                kafkaInstanceApi.deleteTopic(METRIC_TOPIC_NAME);
            } catch (Throwable t) {
                log.warn("clean {} topic error: ", METRIC_TOPIC_NAME, t);
            }
        }

        // delete kafka instance
        try {
            KafkaMgmtApiUtils.cleanKafkaInstance(kafkaMgmtApi, KAFKA_INSTANCE_NAME);
        } catch (Throwable t) {
            log.error("clean main kafka instance error: ", t);
        }

        // delete service account
        try {
            SecurityMgmtAPIUtils.cleanServiceAccount(securityMgmtApi, SERVICE_ACCOUNT_NAME);
        } catch (Throwable t) {
            log.error("clean service account error: ", t);
        }

        try {
            SecurityMgmtAPIUtils.cleanServiceAccount(securityMgmtApi, SERVICE_ACCOUNT_NAME_FOR_DELETION);
        } catch (Throwable t) {
            log.error("clean service account error: ", t);
        }
    }

    @Test
    @SneakyThrows
    public void testCreateKafkaInstance() {

        // Create Kafka Instance
        var payload = new KafkaRequestPayload()
            .name(KAFKA_INSTANCE_NAME)
            .multiAz(true)
            .cloudProvider("aws")
            .region(Environment.DEFAULT_KAFKA_REGION);

        log.info("create kafka instance '{}'", payload.getName());
        kafka = KafkaMgmtApiUtils.applyKafkaInstance(kafkaMgmtApi, payload);

        kafkaInstanceApi = bwait(KafkaInstanceApiUtils.kafkaInstanceApi(kafka,
            Environment.PRIMARY_USERNAME, Environment.PRIMARY_PASSWORD));
    }

    @Test
    @SneakyThrows
    public void testCreateServiceAccount() {

        // Create Service Account
        log.info("create service account '{}'", SERVICE_ACCOUNT_NAME);
        serviceAccount = securityMgmtApi.createServiceAccount(new ServiceAccountRequest().name(SERVICE_ACCOUNT_NAME));
    }

    @Test(dependsOnMethods = {"testCreateServiceAccount", "testCreateKafkaInstance"})
    @SneakyThrows
    public void testCreateProducerAndConsumerACLs() {

        var principal = KafkaInstanceApiAccessUtils.toPrincipal(serviceAccount.getClientId());
        log.info("create topic and group read and topic write ACLs for the principal '{}'", principal);

        // Create ACLs to consumer and produce messages
        KafkaInstanceApiAccessUtils.createProducerAndConsumerACLs(kafkaInstanceApi, principal);
    }

    @Test(dependsOnMethods = "testCreateKafkaInstance")
    @SneakyThrows
    public void testCreateTopics() {

        log.info("create topic '{}' on the instance '{}'", TOPIC_NAME, kafka.getName());
        var topicPayload = new NewTopicInput()
            .name(TOPIC_NAME)
            .settings(new TopicSettings().numPartitions(1));
        kafkaInstanceApi.createTopic(topicPayload);

        log.info("create topic '{}' on the instance '{}'", METRIC_TOPIC_NAME, kafka.getName());
        var metricTopicPayload = new NewTopicInput()
            .name(METRIC_TOPIC_NAME)
            .settings(new TopicSettings().numPartitions(1));
        kafkaInstanceApi.createTopic(metricTopicPayload);
    }

    // ADMIN API can check that requesting creation of topic to have more partition than is max limit on given instance could not be satisfied in any case
    @Test(dependsOnMethods = {"testCreateServiceAccount", "testCreateKafkaInstance"})
    @SneakyThrows
    public void testForbiddenToExceedPartitionLimitOnTopicCreation() {

        var topicName = "topic-part-exceed-create";
        final int maxPartitionLimit = KafkaMgmtApiUtils.getPartitionLimitMax(kafkaMgmtApi, kafka);
        log.info("Max partition limit per given instance: {}", maxPartitionLimit);

        final int exceedMaxPartitionLimit = maxPartitionLimit + 1;
        log.info("Expecting to fail while Attempting to create topic '{}', with too many partitions: {}", topicName, exceedMaxPartitionLimit);
        var payload = new NewTopicInput()
                .name(topicName)
                .settings(new TopicSettings().numPartitions(exceedMaxPartitionLimit));
        assertThrows(ApiGenericException.class,
                () -> kafkaInstanceApi.createTopic(payload));
    }

    // ADMIN API can check that requesting to change configuration of a topic to have more partition than is max limit on given instance could not be satisfied in any case
    @Test(dependsOnMethods = {"testCreateServiceAccount", "testCreateKafkaInstance"})
    @SneakyThrows
    public void testForbiddenToExceedPartitionLimitOnTopicConfiguration() {

        var topicName = "topic-part-exceed-conf";
        KafkaInstanceApiUtils.applyTopic(kafkaInstanceApi, topicName);
        log.info("Applying topic '{}', with 1 partition",topicName);

        final int maxPartitionLimit = KafkaMgmtApiUtils.getPartitionLimitMax(kafkaMgmtApi, kafka);
        log.info("Max partition limit for topics created by user: {}", maxPartitionLimit);

        final int exceedMaxPartitionLimit = maxPartitionLimit + 1;
        log.info("Expecting to fail while configuring topic '{}', to exceed max partition limit by having {} partitions",exceedMaxPartitionLimit, exceedMaxPartitionLimit);
        assertThrows(ApiGenericException.class,
                () -> KafkaInstanceApiUtils.updateTopicPartition(kafkaInstanceApi, topicName, exceedMaxPartitionLimit));
    }

    // Requesting valid number of partition in created topic, but breaching the limit by Sum of partitions with all of existing Topic (i.e. topic visible to user)
    @Test(dependsOnMethods = {"testCreateServiceAccount", "testCreateKafkaInstance"})
    @SneakyThrows
    public void testTotalPartitionLimitByTopicCreation() {

        final int maxPartitionLimit = KafkaMgmtApiUtils.getPartitionLimitMax(kafkaMgmtApi, kafka);
        log.info("Partition limit: {}", maxPartitionLimit);
        final int currentPartitionCount = KafkaInstanceApiUtils.getPartitionCountTotal(kafkaInstanceApi);
        log.info("Current sum of partitions of all public topics: {}", currentPartitionCount);

        // Assuming we are not already overflowing given partition limit
        if (currentPartitionCount > maxPartitionLimit) {
            throw new SkipException("Skip kafka delete");
        }

        // create 2 topic, first can be created but spend half of free partitions, second topic fails as it will breach the max limit
        var topicName1 = "topic-part-total-exceed-1-create";
        var topicName2 = "topic-part-total-exceed-2-create";
        final int partitionCountOfTopic1 = (maxPartitionLimit - currentPartitionCount) / 2;
        log.info("Proposed partition count of topic '{}'", partitionCountOfTopic1);
        // Second topic contains second half of available partitions, and two extra partitions to actually breach (for sure) partition limit
        final int partitionCountOfTopic2 = partitionCountOfTopic1 + 2;
        log.info("Proposed partition count of topic '{}'", partitionCountOfTopic2);

        // creation of the first topic
        log.info("Creating topic '{}' with {} partitions", topicName1, partitionCountOfTopic1);
        var payloadTopic1 = new NewTopicInput()
                .name(topicName1)
                .settings(new TopicSettings().numPartitions(partitionCountOfTopic1));
        kafkaInstanceApi.createTopic(payloadTopic1);
        // wait to make sure changes were propagated
        log.info("Wait 15 seconds before making sure changes in partition count were propagated");
        Thread.sleep(ofSeconds(15).toMillis());

        // creation of the second topic
        log.info("Expecting to fail while creating topic '{}' with {} partitions", topicName2, partitionCountOfTopic2);
        var payloadTopic2 = new NewTopicInput()
                .name(topicName2)
                .settings(new TopicSettings().numPartitions(partitionCountOfTopic2));
        assertThrows(ApiGenericException.class, () -> kafkaInstanceApi.createTopic(payloadTopic2));

        // cleanup
        log.info("Cleanup");
        try {
            kafkaInstanceApi.deleteTopic(topicName1);
        } catch (Throwable t) {
            log.warn("clean {} topic error: ", topicName1, t);
        }
        try {
            kafkaInstanceApi.deleteTopic(topicName2);
        } catch (Throwable t) {
            log.warn("clean {} topic error: ", topicName2, t);
        }

        // there is a need to wait for some period of time before continue with other test as they still may need to create topics (i.e. extra partitions)
        log.info("wait 15 seconds before making sure changes in partition count were propagated");
        Thread.sleep(ofSeconds(15).toMillis());
    }

    @Test(dependsOnMethods = {"testCreateServiceAccount", "testCreateKafkaInstance"})
    @SneakyThrows
    public void testTotalPartitionLimitByTopicConfiguration() {

        final var topicFillPartitionCountName = "topic-fill-partition-count";
        final var topicName = "topic-part-total-exceed-conf";

        log.info("Apply topic with 1 partition");
        KafkaInstanceApiUtils.applyTopic(kafkaInstanceApi, topicName);

        log.info("Create topic with partitions so that there is still 1 more partition within limit");
        final int maxPartitionLimit = KafkaMgmtApiUtils.getPartitionLimitMax(kafkaMgmtApi, kafka);
        final int currentPartitionCount = KafkaInstanceApiUtils.getPartitionCountTotal(kafkaInstanceApi);
        var payloadTopicPartitionFiller = new NewTopicInput()
                .name(topicFillPartitionCountName)
                .settings(new TopicSettings().numPartitions(maxPartitionLimit - currentPartitionCount - 1));
        kafkaInstanceApi.createTopic(payloadTopicPartitionFiller);

        // there is a need to wait for some period of time before actually,
        log.info("wait 15 seconds before making sure changes in partition count were propagated");
        Thread.sleep(ofSeconds(15).toMillis());

        log.info("Increase partition count on topic to reach the total limit");
        KafkaInstanceApiUtils.updateTopicPartition(kafkaInstanceApi, topicName, 2);

        // there is a need to wait for some period of time before actually,
        log.info("wait 15 seconds before making sure changes in partition count were propagated");
        Thread.sleep(ofSeconds(15).toMillis());

        log.info("Increase partition count on topic to exceed the total partition limit");
        assertThrows(ApiGenericException.class, () -> KafkaInstanceApiUtils.updateTopicPartition(kafkaInstanceApi, topicName, 3));

        // cleanup
        log.info("Cleanup");
        try {
            kafkaInstanceApi.deleteTopic(topicName);
        } catch (Throwable t) {
            log.warn("clean {} topic error: ", topicName, t);
        }
        try {
            kafkaInstanceApi.deleteTopic(topicFillPartitionCountName);
        } catch (Throwable t) {
            log.warn("clean {} topic error: ", topicFillPartitionCountName, t);
        }

        // there is a need to wait for some period of time before continue with other test as they still may need to create topics (i.e. extra partitions)
        log.info("wait 15 seconds before making sure changes in partition count were propagated");
        Thread.sleep(ofSeconds(15).toMillis());
    }

    @Test(dependsOnMethods = {"testCreateTopics", "testCreateProducerAndConsumerACLs"})
    @SneakyThrows
    public void testMessageInTotalMetric() {

        log.info("test message in total metric");
        KafkaMgmtMetricsUtils.testMessageInTotalMetric(kafkaMgmtApi, kafka, serviceAccount, TOPIC_NAME);
    }

    @Test(dependsOnMethods = {"testCreateTopics", "testCreateProducerAndConsumerACLs"})
    @SneakyThrows
    public void testMessagingKafkaInstanceUsingOAuth() {

        var bootstrapHost = kafka.getBootstrapServerHost();
        var clientID = serviceAccount.getClientId();
        var clientSecret = serviceAccount.getClientSecret();

        bwait(testTopic(
            Vertx.vertx(),
            bootstrapHost,
            clientID,
            clientSecret,
            TOPIC_NAME,
            1000,
            10,
            100,
            KafkaAuthMethod.OAUTH));
    }

    @Test(dependsOnMethods = {"testCreateTopics", "testCreateProducerAndConsumerACLs"})
    @SneakyThrows
    public void testFailedToMessageKafkaInstanceUsingOAuthAndFakeSecret() {

        var bootstrapHost = kafka.getBootstrapServerHost();
        var clientID = serviceAccount.getClientId();

        assertThrows(KafkaException.class, () -> bwait(testTopic(
            Vertx.vertx(),
            bootstrapHost,
            clientID,
            "invalid",
            TOPIC_NAME,
            1,
            10,
            11,
            KafkaAuthMethod.OAUTH)));
    }

    @Test(dependsOnMethods = {"testCreateTopics", "testCreateProducerAndConsumerACLs"})
    @SneakyThrows
    public void testMessagingKafkaInstanceUsingPlainAuth() {

        var bootstrapHost = kafka.getBootstrapServerHost();
        var clientID = serviceAccount.getClientId();
        var clientSecret = serviceAccount.getClientSecret();

        bwait(testTopic(
            Vertx.vertx(),
            bootstrapHost,
            clientID,
            clientSecret,
            TOPIC_NAME,
            1000,
            10,
            100,
            KafkaAuthMethod.PLAIN));
    }

    @Test(dependsOnMethods = {"testCreateTopics", "testCreateProducerAndConsumerACLs"})
    @SneakyThrows
    public void testFailedToMessageKafkaInstanceUsingPlainAuthAndFakeSecret() {

        var bootstrapHost = kafka.getBootstrapServerHost();
        var clientID = serviceAccount.getClientId();

        assertThrows(KafkaException.class, () -> bwait(testTopic(
            Vertx.vertx(),
            bootstrapHost,
            clientID,
            "invalid",
            TOPIC_NAME,
            1,
            10,
            11,
            KafkaAuthMethod.PLAIN)));
    }

    @Test(dependsOnMethods = {"testCreateKafkaInstance"})
    @SneakyThrows
    public <T extends Throwable> void testFederateMetrics() {
        // Verify all expected user facing Kafka metrics retrieved from Observatorium are included in the response in a Prometheus Text Format
        var missingMetricsAtom = new AtomicReference<List<String>>();
        ThrowingFunction<Boolean, Boolean, ApiGenericException> isMetricAvailable = last -> {
            var metrics = kafkaMgmtApi.federateMetrics(kafka.getId());
            log.debug(metrics);

            var missingMetrics = new ArrayList<String>();
            for (var metricName : KAFKA_METRICS) {
                var metricTypeDefinition = String.format("# TYPE %s gauge", metricName);
                if (!metrics.contains(metricTypeDefinition)) missingMetrics.add(metricName);
            }

            missingMetricsAtom.set(missingMetrics);
            log.debug("missing metrics: {}", missingMetrics);

            return missingMetrics.size() == 0;
        };
        try {
            waitFor("all federated metrics to become available", ofSeconds(3), ofMinutes(5), isMetricAvailable);
        } catch (TimeoutException e) {
            throw new AssertionError(TestUtils.message("Missing metrics: expected metrics: {} but missing: {}", KAFKA_METRICS, missingMetricsAtom.get()), e);
        }
    }

    @Test(dependsOnMethods = {"testCreateKafkaInstance"})
    @SneakyThrows
    public void testListAndSearchKafkaInstance() {

        // TODO: Split in between list kafka and search kafka by name

        // List kafka instances
        log.info("get kafka list");
        var kafkaList = kafkaMgmtApi.getKafkas(null, null, null, null);

        log.debug(kafkaList);
        assertTrue(kafkaList.getItems().size() > 0);

        // Get created kafka instance from the list
        log.info("find kafka instance '{}' in list", KAFKA_INSTANCE_NAME);
        var findKafka = kafkaList.getItems().stream()
            .filter(k -> KAFKA_INSTANCE_NAME.equals(k.getName()))
            .findAny();
        log.debug(findKafka.orElse(null));
        assertTrue(findKafka.isPresent());

        // Search kafka by name
        log.info("search kafka instance '{}' by name", KAFKA_INSTANCE_NAME);
        var kafkaOptional = KafkaMgmtApiUtils.getKafkaByName(kafkaMgmtApi, KAFKA_INSTANCE_NAME);
        log.debug(kafkaOptional.orElse(null));
        assertTrue(kafkaOptional.isPresent());
        assertEquals(kafkaOptional.get().getName(), KAFKA_INSTANCE_NAME);
    }

    @Test(dependsOnMethods = {"testCreateKafkaInstance"})
    public void testFailToCreateKafkaInstanceIfItAlreadyExist() {

        // Create Kafka Instance with existing name
        var payload = new KafkaRequestPayload()
            .name(KAFKA_INSTANCE_NAME)
            .multiAz(true)
            .cloudProvider("aws")
            .region(Environment.DEFAULT_KAFKA_REGION);

        log.info("create kafka instance '{}' with existing name", payload.getName());
        assertThrows(ApiConflictException.class, () -> kafkaMgmtApi.createKafka(true, payload));
    }

    @Test(dependsOnMethods = {"testCreateServiceAccount", "testCreateKafkaInstance"}, priority = 1)
    @SneakyThrows
    public void testReauthentication() {

        // make sure reauthentication is enabled by default
        assertTrue(kafka.getReauthenticationEnabled());

        var initialSessionLifetimeMs = KafkaAdminUtils.getAuthenticatorPositiveSessionLifetimeMs(
            kafka.getBootstrapServerHost(),
            serviceAccount.getClientId(),
            serviceAccount.getClientSecret());
        log.debug("positiveSessionLifetimeMs: {}", initialSessionLifetimeMs);
        // because reauth is enabled the session lifetime can not be null
        assertNotNull(initialSessionLifetimeMs);

        // disable kafka instance reauthentication
        log.info("set Kafka reauthentication to false");
        kafka = kafkaMgmtApi.updateKafka(kafka.getId(), new KafkaUpdateRequest().reauthenticationEnabled(false));
        assertFalse(kafka.getReauthenticationEnabled());

        ThrowingFunction<Boolean, Boolean, ApiGenericException> isReauthenticationDisabled = last -> {

            var sessionLifetimeMs = KafkaAdminUtils.getAuthenticatorPositiveSessionLifetimeMs(
                kafka.getBootstrapServerHost(),
                serviceAccount.getClientId(),
                serviceAccount.getClientSecret());

            log.debug("positiveSessionLifetimeMs: {}", sessionLifetimeMs);

            // session lifetime should become null after disabling reauth
            return sessionLifetimeMs == null;
        };
        waitFor("kafka reauthentication to be disabled", ofSeconds(10), ofMinutes(5), isReauthenticationDisabled);
    }

    @Test(dependsOnMethods = "testCreateTopics")
    @SneakyThrows
    public void testDeleteServiceAccount() {

        // create SA specifically for purpose of demonstration that it works, afterwards deleting it and fail to use it anymore
        log.info("create service account '{}'", SERVICE_ACCOUNT_NAME_FOR_DELETION);
        var serviceAccountForDeletion = securityMgmtApi.createServiceAccount(new ServiceAccountRequest().name(SERVICE_ACCOUNT_NAME_FOR_DELETION));

        // ACLs
        var principal = KafkaInstanceApiAccessUtils.toPrincipal(serviceAccountForDeletion.getClientId());
        log.info("create topic and group read and topic write ACLs for the principal '{}'", principal);
        KafkaInstanceApiAccessUtils.createProducerAndConsumerACLs(kafkaInstanceApi, principal);

        // working Communication (Producing & Consuming) using  SA (serviceAccountForDeletion)
        var bootstrapHost = kafka.getBootstrapServerHost();
        var clientID = serviceAccountForDeletion.getClientId();
        var clientSecret = serviceAccountForDeletion.getClientSecret();

        bwait(testTopic(
            Vertx.vertx(),
            bootstrapHost,
            clientID,
            clientSecret,
            TOPIC_NAME,
            1000,
            10,
            100,
            KafkaAuthMethod.PLAIN));

        // deletion of SA (serviceAccountForDeletion)
        securityMgmtApi.deleteServiceAccountById(serviceAccountForDeletion.getId());

        // fail to communicate due to service account being deleted using PLAIN & OAUTH
        assertThrows(KafkaException.class, () -> {
            bwait(testTopic(
                Vertx.vertx(),
                bootstrapHost,
                clientID,
                clientSecret,
                TOPIC_NAME,
                1000,
                10,
                100,
                KafkaAuthMethod.PLAIN
            ));
        });
        assertThrows(KafkaException.class, () -> {
            bwait(testTopic(
                Vertx.vertx(),
                bootstrapHost,
                clientID,
                clientSecret,
                TOPIC_NAME,
                1000,
                10,
                100,
                KafkaAuthMethod.OAUTH
            ));
        });
    }

    @Test(dependsOnMethods = {"testCreateKafkaInstance"}, priority = 2)
    @SneakyThrows
    public void testDeleteKafkaInstance() {
        if (Environment.SKIP_KAFKA_TEARDOWN) {
            throw new SkipException("Skip kafka delete");
        }

        var bootstrapHost = kafka.getBootstrapServerHost();
        var clientID = serviceAccount.getClientId();
        var clientSecret = serviceAccount.getClientSecret();

        // Connect the Kafka producer
        log.info("initialize kafka producer");
        var producer = new KafkaProducerClient<>(
            Vertx.vertx(),
            bootstrapHost,
            clientID,
            clientSecret,
            KafkaAuthMethod.PLAIN,
            StringSerializer.class,
            StringSerializer.class,
            new HashMap<>());

        // Delete the Kafka instance
        log.info("delete kafka instance '{}'", KAFKA_INSTANCE_NAME);
        kafkaMgmtApi.deleteKafkaById(kafka.getId(), true);
        KafkaMgmtApiUtils.waitUntilKafkaIsDeleted(kafkaMgmtApi, kafka.getId());

        // Produce Kafka messages
        log.info("send message to topic '{}'", TOPIC_NAME);
        waitFor(Vertx.vertx(), "sent message to fail", ofSeconds(1), ofSeconds(30), last ->
            producer.send(KafkaProducerRecord.create(TOPIC_NAME, "hello world"))
                .compose(
                    __ -> Future.succeededFuture(Pair.with(false, null)),
                    t -> Future.succeededFuture(Pair.with(true, null))));

        log.info("close kafka producer and consumer");
        bwait(producer.asyncClose());
    }

}
