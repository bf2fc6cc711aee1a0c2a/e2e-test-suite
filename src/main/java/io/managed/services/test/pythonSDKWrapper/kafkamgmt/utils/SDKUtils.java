package io.managed.services.test.pythonSDKWrapper.kafkamgmt.utils;

import com.openshift.cloud.api.kas.models.KafkaRequest;
import io.managed.services.test.cli.CliGenericException;
import io.managed.services.test.cli.CliNotFoundException;
import io.managed.services.test.client.kafkamgmt.KafkaMgmtApiUtils;
import io.managed.services.test.client.kafkamgmt.KafkaNotDeletedException;
import io.managed.services.test.client.kafkamgmt.KafkaNotReadyException;
import io.managed.services.test.client.kafkamgmt.KafkaUnknownHostsException;
import io.managed.services.test.pythonSDKWrapper.PythonSDKWrapper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Optional;


public class SDKUtils {

    private static final Logger LOGGER = LogManager.getLogger(SDKUtils.class);

    public static KafkaRequest waitUntilKafkaIsReady(PythonSDKWrapper pythonSDk, String id)
            throws KafkaUnknownHostsException, KafkaNotReadyException, InterruptedException, CliGenericException {

        return KafkaMgmtApiUtils.waitUntilKafkaIsReady(() -> pythonSDk.describeKafka(id));
    }


    public static void waitUntilKafkaIsDeleted(PythonSDKWrapper pythonSDK, String id)
            throws KafkaNotDeletedException, InterruptedException, CliGenericException {

        KafkaMgmtApiUtils.waitUntilKafkaIsDeleted(() -> {
            try {
                return Optional.of(pythonSDK.describeKafka(id));
            } catch (CliNotFoundException e) {
                return Optional.empty();
            }
        });
    }

}