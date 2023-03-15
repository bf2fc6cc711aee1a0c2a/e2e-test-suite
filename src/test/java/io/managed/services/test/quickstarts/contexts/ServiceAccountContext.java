package io.managed.services.test.quickstarts.contexts;

import com.openshift.cloud.api.serviceaccounts.models.ServiceAccountData;
import lombok.Getter;
import lombok.Setter;

import java.util.Objects;

@Getter
@Setter
public class ServiceAccountContext {
    private ServiceAccountData serviceAccount;

    public ServiceAccountData requireServiceAccount() {
        return Objects.requireNonNull(serviceAccount);
    }
}
