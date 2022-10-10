package software.amazon.organizations.account;

import com.google.common.collect.ImmutableSet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.organizations.OrganizationsClient;
import software.amazon.awssdk.services.organizations.model.Account;
import software.amazon.awssdk.services.organizations.model.AccountNotFoundException;
import software.amazon.awssdk.services.organizations.model.AccountStatus;
import software.amazon.awssdk.services.organizations.model.DescribeAccountRequest;
import software.amazon.awssdk.services.organizations.model.DescribeAccountResponse;
import software.amazon.awssdk.services.organizations.model.ListParentsRequest;
import software.amazon.awssdk.services.organizations.model.ListParentsResponse;
import software.amazon.awssdk.services.organizations.model.ListTagsForResourceRequest;
import software.amazon.awssdk.services.organizations.model.ListTagsForResourceResponse;
import software.amazon.awssdk.services.organizations.model.Parent;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.HandlerErrorCode;
import software.amazon.cloudformation.proxy.OperationStatus;
import software.amazon.cloudformation.proxy.ProgressEvent;
import software.amazon.cloudformation.proxy.ProxyClient;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class ReadHandlerTest extends AbstractTestBase {

    @Mock
    OrganizationsClient mockOrgsClient;
    @Mock
    private AmazonWebServicesClientProxy mockAwsClientProxy;
    @Mock
    private ProxyClient<OrganizationsClient> mockProxyClient;
    private ReadHandler readHandler;

    @BeforeEach
    public void setup() {
        readHandler = new ReadHandler();
        mockAwsClientProxy = new AmazonWebServicesClientProxy(logger, MOCK_CREDENTIALS, () -> Duration.ofSeconds(600).toMillis());
        mockOrgsClient = mock(OrganizationsClient.class);
        mockProxyClient = MOCK_PROXY(mockAwsClientProxy, mockOrgsClient);
    }

    @Test
    public void handleRequest_SimpleSuccess() {
        final ResourceModel model = ResourceModel.builder()
                .accountId(TEST_ACCOUNT_ID)
                .build();

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
                .desiredResourceState(model)
                .build();

        final ListParentsResponse listParentsResponse = ListParentsResponse.builder()
                .parents(Parent.builder()
                        .id(TEST_DESTINATION_PARENT_ID)
                        .build()
                ).build();

        final ListTagsForResourceResponse listTagsForResourceResponse = TagTestResourcesHelper.buildDefaultTagsResponse();
        when(mockProxyClient.client().describeAccount(any(DescribeAccountRequest.class))).thenReturn(describeAccountResponse);

        when(mockProxyClient.client().listParents(any(ListParentsRequest.class))).thenReturn(listParentsResponse);
        when(mockProxyClient.client().listTagsForResource(any(ListTagsForResourceRequest.class))).thenReturn(listTagsForResourceResponse);

        final ProgressEvent<ResourceModel, CallbackContext> response = readHandler.handleRequest(mockAwsClientProxy, request, new CallbackContext(), mockProxyClient, logger);
        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.SUCCESS);
        assertThat(response.getCallbackDelaySeconds()).isEqualTo(0);
        assertThat(response.getResourceModel()).isNotNull();
        assertThat(response.getResourceModel().getAccountName()).isEqualTo(TEST_ACCOUNT_NAME);
        assertThat(response.getResourceModel().getAccountId()).isEqualTo(TEST_ACCOUNT_ID);
        assertThat(response.getResourceModel().getArn()).isEqualTo(TEST_ACCOUNT_ARN);
        assertThat(response.getResourceModel().getStatus()).isEqualTo(AccountStatus.ACTIVE.toString());
        assertThat(response.getResourceModel().getJoinedMethod()).isEqualTo(TEST_JOINED_METHOD);
        assertThat(response.getResourceModel().getJoinedTimestamp()).isEqualTo(TEST_JOINED_TIMESTAMP.toString());
        assertThat(response.getResourceModel().getEmail()).isEqualTo(TEST_ACCOUNT_EMAIL);
        assertThat(response.getResourceModel().getParentIds()).isEqualTo(ImmutableSet.of(TEST_DESTINATION_PARENT_ID));
        assertThat(TagTestResourcesHelper.tagsEqual(response.getResourceModel().getTags(), TagTestResourcesHelper.defaultTags));
        assertThat(response.getResourceModels()).isNull();
        assertThat(response.getMessage()).isNull();
        assertThat(response.getErrorCode()).isNull();

        verify(mockProxyClient.client()).describeAccount(any(DescribeAccountRequest.class));
        verify(mockProxyClient.client()).listParents(any(ListParentsRequest.class));
        verify(mockProxyClient.client()).listTagsForResource(any(ListTagsForResourceRequest.class));
    }

    @Test
    public void handleRequest_Fails_With_CfnNotFoundException() {
        final ResourceModel model = ResourceModel.builder()
                .accountId(TEST_ACCOUNT_ID)
                .build();

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
                .desiredResourceState(model)
                .build();

        when(mockProxyClient.client().describeAccount(any(DescribeAccountRequest.class))).thenThrow(AccountNotFoundException.class);

        final ProgressEvent<ResourceModel, CallbackContext> response = readHandler.handleRequest(mockAwsClientProxy, request, new CallbackContext(), mockProxyClient, logger);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.FAILED);
        assertThat(response.getResourceModels()).isNull();
        assertThat(response.getErrorCode()).isEqualTo(HandlerErrorCode.NotFound);
    }

    @Test
    public void handleRequest_shouldReturnFailedIfAccountIsSuspended() {
        final ResourceModel model = ResourceModel.builder()
                                        .accountId(TEST_ACCOUNT_ID)
                                        .build();

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
                                                                  .desiredResourceState(model)
                                                                  .build();

        final DescribeAccountResponse describeAccountResponse = DescribeAccountResponse.builder().account(Account.builder()
                                                                                                              .arn(TEST_ACCOUNT_ARN)
                                                                                                              .email(TEST_ACCOUNT_EMAIL)
                                                                                                              .id(TEST_ACCOUNT_ID)
                                                                                                              .name(TEST_ACCOUNT_NAME)
                                                                                                              .status(AccountStatus.SUSPENDED)
                                                                                                              .build()).build();

        when(mockProxyClient.client().describeAccount(any(DescribeAccountRequest.class))).thenReturn(describeAccountResponse);

        final ProgressEvent<ResourceModel, CallbackContext> response = readHandler.handleRequest(mockAwsClientProxy, request, new CallbackContext(), mockProxyClient, logger);
        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.FAILED);
        assertThat(response.getCallbackDelaySeconds()).isEqualTo(0);
        assertThat(response.getResourceModel()).isNotNull();
        assertThat(TagTestResourcesHelper.tagsEqual(response.getResourceModel().getTags(), TagTestResourcesHelper.defaultTags));
        assertThat(response.getResourceModels()).isNull();
        assertThat(response.getErrorCode()).isEqualTo(HandlerErrorCode.NotFound);

        verify(mockProxyClient.client()).describeAccount(any(DescribeAccountRequest.class));
    }
}
