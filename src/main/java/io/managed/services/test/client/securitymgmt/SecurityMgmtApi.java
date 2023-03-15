package io.managed.services.test.client.securitymgmt;

import com.openshift.cloud.api.serviceaccounts.ApiClient;
import com.openshift.cloud.api.serviceaccounts.models.ServiceAccountCreateRequestData;
import com.openshift.cloud.api.serviceaccounts.models.ServiceAccountData;
import io.managed.services.test.client.BaseApi;
import io.managed.services.test.client.exception.ApiGenericException;
import io.managed.services.test.client.exception.ApiUnknownException;

import java.util.List;
import java.util.concurrent.TimeUnit;

public class SecurityMgmtApi extends BaseApi {

    private final ApiClient apiClient;

    public SecurityMgmtApi(ApiClient apiClient, String offlineToken) {
        super(offlineToken);
        this.apiClient = apiClient;
    }

    @SuppressWarnings("unused")
    public ServiceAccountData getServiceAccountById(String id) throws ApiGenericException {
        return retry(() -> apiClient.apis().service_accounts().v1(id).get().get(10, TimeUnit.SECONDS));
    }

    public List<ServiceAccountData> getServiceAccounts() throws ApiGenericException {
        return retry(() -> apiClient.apis().service_accounts().v1().get().get(10, TimeUnit.SECONDS));
    }

    public ServiceAccountData createServiceAccount(ServiceAccountCreateRequestData serviceAccountRequest) throws ApiGenericException {
        return retry(() -> apiClient.apis().service_accounts().v1().post(serviceAccountRequest).get(10, TimeUnit.SECONDS));
    }

    public void deleteServiceAccountById(String id) throws ApiGenericException {
        retry(() -> apiClient.apis().service_accounts().v1(id).delete().get(10, TimeUnit.SECONDS));
    }

    public ServiceAccountData resetServiceAccountCreds(String id) throws ApiGenericException {
        return retry(() -> apiClient.apis().service_accounts().v1(id).resetSecret().post().get(10, TimeUnit.SECONDS));
    }

    @Override
    protected ApiUnknownException toApiException(Exception e) {
        return null;
    }
}
