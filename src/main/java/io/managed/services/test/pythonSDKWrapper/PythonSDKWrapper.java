package io.managed.services.test.pythonSDKWrapper;

import com.openshift.cloud.api.kas.models.KafkaRequest;
import com.openshift.cloud.api.kas.models.KafkaRequestList;
import com.openshift.cloud.api.serviceaccounts.models.ServiceAccountData;
import io.managed.services.test.RetryUtils;
import io.managed.services.test.ThrowingSupplier;
import io.managed.services.test.cli.AsyncProcess;
import io.managed.services.test.cli.CliGenericException;
import io.managed.services.test.cli.ProcessException;
import lombok.extern.log4j.Log4j2;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static java.time.Duration.ofMinutes;
import static lombok.Lombok.sneakyThrow;

@Log4j2
public class PythonSDKWrapper {

    private static final Duration DEFAULT_TIMEOUT = ofMinutes(3);

    private static final String CLUSTER_CAPACITY_EXHAUSTED_CODE = "KAFKAS-MGMT-24";

    private final String workdir;
    private final String cmd;


    private final String pythonSDKPath = "e2e-test-suite/src/main/java/io/managed/services/test/pythonSDKWrapper/";

    public String FindPythonSDK() {
        String[] whichCmd = new String[]{"which", "python"};
        Process p = null;
        try {
            p = Runtime.getRuntime().exec(whichCmd);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
        String pythonExecutable;
        try {
            pythonExecutable = reader.readLine();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return pythonExecutable;
    }

    public PythonSDKWrapper(Path path) {
        this.workdir = path.getParent().toString();
        this.cmd = FindPythonSDK();
    }

    public String getWorkdir() {
        return this.workdir;
    }

    private ProcessBuilder builder(List<String> command) {
        var cmd = new ArrayList<String>();
        cmd.add(this.cmd);
        cmd.addAll(command);


        return new ProcessBuilder(cmd)
                .directory(new File(workdir));
    }

    private AsyncProcess exec(String... command) throws CliGenericException {
        return exec(List.of(command));
    }

    private AsyncProcess exec(List<String> command) throws CliGenericException {
        try {
            return execAsync(command).sync(DEFAULT_TIMEOUT);
        } catch (ProcessException e) {
            throw CliGenericException.exception(e);
        }
    }

    private AsyncProcess execAsync(String... command) {
        return execAsync(List.of(command));
    }

    private AsyncProcess execAsync(List<String> command) {
        try {
            return new AsyncProcess(builder(command).start());
        } catch (IOException e) {
            throw sneakyThrow(e);
        }
    }


    public KafkaRequest createKafka(String name, String cloudProvider, String region, String plan) throws CliGenericException {
        System.out.println("workdir==" + getWorkdir());
        return retryKafkaCreation(() -> exec(pythonSDKPath + "kafkamgmt/create_kafka.py", "--kafka_name", name, "--cloud_provider", cloudProvider, "--region", region, "--plan", plan))
                .asJson(KafkaRequest.class);
    }

    public void deleteKafka(String id) throws CliGenericException {
        retry(() -> exec(pythonSDKPath + "kafkamgmt/delete_kafka_by_id.py", "--kafka_id", id));
    }

    public KafkaRequest describeKafka(String id) throws CliGenericException {
        var kr = retry(() -> exec(pythonSDKPath + "kafkamgmt/describe_kafka.py", "--kafka_id", id));
        System.out.println("kr==" + kr);
        return kr.asJson(KafkaRequest.class);
    }

    public KafkaRequestList listKafka() throws CliGenericException {
        return retry(() -> exec(pythonSDKPath + "kafkamgmt/list_kafkas.py"))
                .asJson(KafkaRequestList.class);
    }


    public ServiceAccountData createServiceAccount(String name) throws CliGenericException {
        return retry(() -> exec(pythonSDKPath + "serviceaccount/create_service_account.py", "--service_account_name", name, "--service_account_description", "this is a description"))
                .asJson(ServiceAccountData.class);
    }

    public ServiceAccountData describeServiceAccount(String id) throws CliGenericException {
        return retry(() -> exec("service-account", "describe", "--id", id))
                .asJson(ServiceAccountData.class);
    }

    public ServiceAccountData[] listServiceAccounts() throws CliGenericException {
        return retry(() -> exec(pythonSDKPath + "serviceaccount/get_service_accounts.py"))
                .asJson(ServiceAccountData[].class);
    }

    public void deleteServiceAccount(String id) throws CliGenericException {
        retry(() -> exec(pythonSDKPath + "serviceaccount/delete_service_account.py", "--service_account_id", id));
    }

    private <T, E extends Throwable> T retry(ThrowingSupplier<T, E> call) throws E {
        return RetryUtils.retry(1, call, PythonSDKWrapper::retryCondition);
    }

    private <T, E extends Throwable> T retryKafkaCreation(ThrowingSupplier<T, E> call) throws E {
        return RetryUtils.retry(
                1, null, call, PythonSDKWrapper::retryConditionKafkaCreation, 12, Duration.ofSeconds(10));
    }

    private static boolean retryConditionKafkaCreation(Throwable t) {
        if (t instanceof CliGenericException) {
            // quota problem,
            if (((CliGenericException) t).getCode() == 403 && ((CliGenericException) t).getMessage().contains(CLUSTER_CAPACITY_EXHAUSTED_CODE)) {
                return true;
            }
            // server side problem
            return ((CliGenericException) t).getCode() >= 500 && ((CliGenericException) t).getCode() < 600;
        }
        return false;
    }

    private static boolean retryCondition(Throwable t) {
        if (t instanceof CliGenericException) {
            return ((CliGenericException) t).getCode() >= 500 && ((CliGenericException) t).getCode() < 600;
        }
        return false;
    }

}
