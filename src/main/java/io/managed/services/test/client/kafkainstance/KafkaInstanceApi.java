package io.managed.services.test.client.kafkainstance;

import com.openshift.cloud.api.kas.auth.AclsApi;
import com.openshift.cloud.api.kas.auth.GroupsApi;
import com.openshift.cloud.api.kas.auth.TopicsApi;
import com.openshift.cloud.api.kas.auth.invoker.ApiClient;
import com.openshift.cloud.api.kas.auth.invoker.ApiException;
import com.openshift.cloud.api.kas.auth.models.AclBinding;
import com.openshift.cloud.api.kas.auth.models.AclBindingListPage;
import com.openshift.cloud.api.kas.auth.models.AclOperationFilter;
import com.openshift.cloud.api.kas.auth.models.AclPatternTypeFilter;
import com.openshift.cloud.api.kas.auth.models.AclPermissionTypeFilter;
import com.openshift.cloud.api.kas.auth.models.AclResourceTypeFilter;
import com.openshift.cloud.api.kas.auth.models.ConsumerGroup;
import com.openshift.cloud.api.kas.auth.models.ConsumerGroupList;
import com.openshift.cloud.api.kas.auth.models.NewTopicInput;
import com.openshift.cloud.api.kas.auth.models.Topic;
import com.openshift.cloud.api.kas.auth.models.TopicsList;
import io.managed.services.test.client.BaseApi;
import io.managed.services.test.client.exception.ApiGenericException;
import io.managed.services.test.client.exception.ApiUnknownException;
import io.managed.services.test.client.oauth.KeycloakUser;

import java.math.BigDecimal;

public class KafkaInstanceApi extends BaseApi {

    private final ApiClient apiClient;
    private final AclsApi aclsApi;
    private final GroupsApi groupsApi;
    private final TopicsApi topicsApi;

    public KafkaInstanceApi(ApiClient apiClient, KeycloakUser user) {
        super(user);
        this.apiClient = apiClient;
        this.aclsApi = new AclsApi(apiClient);
        this.groupsApi = new GroupsApi(apiClient);
        this.topicsApi = new TopicsApi(apiClient);
    }

    @Override
    protected ApiUnknownException toApiException(Exception e) {
        if (e instanceof ApiException) {
            var ex = (ApiException) e;
            return new ApiUnknownException(ex.getMessage(), ex.getCode(), ex.getResponseHeaders(), ex.getResponseBody(), ex);
        }
        return null;
    }

    @Override
    protected void setAccessToken(String t) {
        apiClient.setAccessToken(t);
    }

    public TopicsList getTopics() throws ApiGenericException {
        return getTopics(null, null, null, null, null);
    }

    public TopicsList getTopics(Integer size, Integer page, String filter, String order, String orderKey) throws ApiGenericException {
        return retry(() -> topicsApi.getTopics(size, filter, page, order, orderKey));
    }

    public Topic getTopic(String topicName) throws ApiGenericException {
        return retry(() -> topicsApi.getTopic(topicName));
    }

    public Topic createTopic(NewTopicInput newTopicInput) throws ApiGenericException {
        return retry(() -> topicsApi.createTopic(newTopicInput));
    }

    public void deleteTopic(String topicName) throws ApiGenericException {
        retry(() -> topicsApi.deleteTopic(topicName));
    }

    public ConsumerGroupList getConsumerGroups() throws ApiGenericException {
        return getConsumerGroups(null, null, null, null, null, null);
    }

    public ConsumerGroupList getConsumerGroups(Integer size, Integer page, String topic, String groupIdFilter, String order, String orderKey) throws ApiGenericException {
        return retry(() -> groupsApi.getConsumerGroups(size, page, topic, groupIdFilter, order, orderKey));
    }

    public ConsumerGroup getConsumerGroupById(String consumerGroupId) throws ApiGenericException {
        return getConsumerGroupById(consumerGroupId, null, null, null, null);
    }

    public ConsumerGroup getConsumerGroupById(String consumerGroupId, String order, String orderKey, Integer partitionFilter, String topic) throws ApiGenericException {
        return retry(() -> groupsApi.getConsumerGroupById(consumerGroupId, order, orderKey, partitionFilter, topic));
    }

    public void deleteConsumerGroupById(String consumerGroupId) throws ApiGenericException {
        retry(() -> groupsApi.deleteConsumerGroupById(consumerGroupId));
    }

    public AclBindingListPage getAcls(AclResourceTypeFilter resourceType, String resourceName, AclPatternTypeFilter patternType, String principal, AclOperationFilter operation, AclPermissionTypeFilter permission, BigDecimal page, BigDecimal size, String order, String orderKey) throws ApiGenericException {
        return retry(() -> aclsApi.getAcls(resourceType, resourceName, patternType, principal, operation, permission, page, size, order, orderKey));
    }

    public void createAcl(AclBinding aclBinding) throws ApiGenericException {
        retry(() -> aclsApi.createAcl(aclBinding));
    }

    public AclBindingListPage deleteAcls(AclResourceTypeFilter resourceType, String resourceName, AclPatternTypeFilter patternType, String principal, AclOperationFilter operation, AclPermissionTypeFilter permission) throws ApiGenericException {
        return retry(() -> aclsApi.deleteAcls(resourceType, resourceName, patternType, principal, operation, permission));
    }
}
