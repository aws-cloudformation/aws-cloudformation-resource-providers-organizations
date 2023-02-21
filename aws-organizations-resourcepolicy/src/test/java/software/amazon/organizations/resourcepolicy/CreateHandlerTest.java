package software.amazon.organizations.resourcepolicy;

import java.time.Duration;
import java.util.Map;

import software.amazon.awssdk.services.organizations.OrganizationsClient;
import software.amazon.awssdk.services.organizations.model.DescribeResourcePolicyRequest;
import software.amazon.awssdk.services.organizations.model.DescribeResourcePolicyResponse;
import software.amazon.awssdk.services.organizations.model.ListTagsForResourceRequest;
import software.amazon.awssdk.services.organizations.model.ListTagsForResourceResponse;
import software.amazon.awssdk.services.organizations.model.PutResourcePolicyRequest;
import software.amazon.awssdk.services.organizations.model.PutResourcePolicyResponse;
import software.amazon.awssdk.services.organizations.model.ResourcePolicy;
import software.amazon.awssdk.services.organizations.model.ResourcePolicyNotFoundException;
import software.amazon.awssdk.services.organizations.model.TooManyRequestsException;
import software.amazon.awssdk.services.organizations.model.ServiceException;
import software.amazon.awssdk.services.organizations.model.UnsupportedApiEndpointException;
import software.amazon.awssdk.services.organizations.model.ConcurrentModificationException;
import software.amazon.cloudformation.exceptions.CfnInvalidRequestException;

import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.HandlerErrorCode;
import software.amazon.cloudformation.proxy.OperationStatus;
import software.amazon.cloudformation.proxy.ProgressEvent;
import software.amazon.cloudformation.proxy.ProxyClient;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import com.amazonaws.event.request.Progress;

import javafx.util.Callback;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class CreateHandlerTest extends AbstractTestBase {
    @Mock
    OrganizationsClient mockOrgsClient;
    @Mock
    private AmazonWebServicesClientProxy mockAwsClientProxy;
    @Mock
    private ProxyClient<OrganizationsClient> mockProxyClient;
    @Mock
    private CreateHandler createHandler;

    @BeforeEach
    public void setup() {
        createHandler = new CreateHandler();
        mockAwsClientProxy = new AmazonWebServicesClientProxy(logger, MOCK_CREDENTIALS, () -> Duration.ofSeconds(600).toMillis());
        mockOrgsClient = mock(OrganizationsClient.class);
        mockProxyClient = MOCK_PROXY(mockAwsClientProxy, mockOrgsClient);
    }

    @Test
    public void handleRequest_NoTags_SimpleSuccess() {
        final ResourceModel model = generateInitialResourceModel(false, TEST_RESOURCEPOLICY_CONTENT);

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
            .desiredResourceState(model)
            .build();

        final DescribeResourcePolicyResponse describeResourcePolicyResponse = getDescribeResourcePolicyResponse();
        when(mockProxyClient.client().describeResourcePolicy(any(DescribeResourcePolicyRequest.class))).thenReturn(null, describeResourcePolicyResponse);

        final PutResourcePolicyResponse putResourcePolicyResponse = getPutResourcePolicyResponse();
        when(mockProxyClient.client().putResourcePolicy(any(PutResourcePolicyRequest.class))).thenReturn(putResourcePolicyResponse);

        final ListTagsForResourceResponse listTagsResponse = TagTestResourceHelper.buildEmptyTagsResponse();
        when(mockProxyClient.client().listTagsForResource(any(ListTagsForResourceRequest.class))).thenReturn(listTagsResponse);

        final ProgressEvent<ResourceModel, CallbackContext> response = createHandler.handleRequest(mockAwsClientProxy, request, new CallbackContext(), mockProxyClient, logger);

        verifyHandlerSuccess(response, request);

        verify(mockProxyClient.client()).putResourcePolicy(any(PutResourcePolicyRequest.class));
        verify(mockProxyClient.client(), times(2)).describeResourcePolicy(any(DescribeResourcePolicyRequest.class));
        verify(mockProxyClient.client()).listTagsForResource(any(ListTagsForResourceRequest.class));

        verify(mockOrgsClient, atLeastOnce()).serviceName();
        verifyNoMoreInteractions(mockOrgsClient);
    }

    @Test
    public void handleRequest_WithTags_SimpleSuccess() {
        final ResourceModel model = generateInitialResourceModel(false, TEST_RESOURCEPOLICY_CONTENT);

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
            .desiredResourceState(model)
            .build();

        final DescribeResourcePolicyResponse describeResourcePolicyResponse = getDescribeResourcePolicyResponse();
        when(mockProxyClient.client().describeResourcePolicy(any(DescribeResourcePolicyRequest.class))).thenReturn(null, describeResourcePolicyResponse);

        final PutResourcePolicyResponse putResourcePolicyResponse = getPutResourcePolicyResponse();
        when(mockProxyClient.client().putResourcePolicy(any(PutResourcePolicyRequest.class))).thenReturn(putResourcePolicyResponse);


        final ListTagsForResourceResponse listTagsResponse = TagTestResourceHelper.buildDefaultTagsResponse();
        when(mockProxyClient.client().listTagsForResource(any(ListTagsForResourceRequest.class))).thenReturn(listTagsResponse);

        final ProgressEvent<ResourceModel, CallbackContext> response = createHandler.handleRequest(mockAwsClientProxy, request, new CallbackContext(), mockProxyClient, logger);

        verifyHandlerSuccess(response, request);

        verify(mockProxyClient.client()).putResourcePolicy(any(PutResourcePolicyRequest.class));
        verify(mockProxyClient.client(), times(2)).describeResourcePolicy(any(DescribeResourcePolicyRequest.class));
        verify(mockProxyClient.client()).listTagsForResource(any(ListTagsForResourceRequest.class));

        verify(mockOrgsClient, atLeastOnce()).serviceName();
        verifyNoMoreInteractions(mockOrgsClient);
    }

    @Test
    public void handleRequest_WithTags_WithJSONContent_SimpleSuccess() {
        final ResourceModel model = generateInitialResourceModel(false, TEST_RESOURCEPOLICY_CONTENT_JSON);

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
            .desiredResourceState(model)
            .build();

        final DescribeResourcePolicyResponse describeResourcePolicyResponse = getDescribeResourcePolicyResponse();
        when(mockProxyClient.client().describeResourcePolicy(any(DescribeResourcePolicyRequest.class))).thenReturn(null, describeResourcePolicyResponse);

        final PutResourcePolicyResponse putResourcePolicyResponse = getPutResourcePolicyResponse();
        when(mockProxyClient.client().putResourcePolicy(any(PutResourcePolicyRequest.class))).thenReturn(putResourcePolicyResponse);


        final ListTagsForResourceResponse listTagsResponse = TagTestResourceHelper.buildDefaultTagsResponse();
        when(mockProxyClient.client().listTagsForResource(any(ListTagsForResourceRequest.class))).thenReturn(listTagsResponse);

        final ProgressEvent<ResourceModel, CallbackContext> response = createHandler.handleRequest(mockAwsClientProxy, request, new CallbackContext(), mockProxyClient, logger);

        verifyHandlerSuccess(response, request);

        verify(mockProxyClient.client()).putResourcePolicy(any(PutResourcePolicyRequest.class));
        verify(mockProxyClient.client(), times(2)).describeResourcePolicy(any(DescribeResourcePolicyRequest.class));
        verify(mockProxyClient.client()).listTagsForResource(any(ListTagsForResourceRequest.class));

        verify(mockOrgsClient, atLeastOnce()).serviceName();
        verifyNoMoreInteractions(mockOrgsClient);
    }

    @Test
    public void handleRequest_MissingRequiredValueContent_Fails_With_InvalidRequest() {
        final ResourceModel model = ResourceModel.builder()
            .build();

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
            .desiredResourceState(model)
            .build();

        final ProgressEvent<ResourceModel, CallbackContext> response = createHandler.handleRequest(mockAwsClientProxy, request, new CallbackContext(), mockProxyClient, logger);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.FAILED);
        assertThat(response.getResourceModels()).isNull();
        assertThat(response.getErrorCode()).isEqualTo(HandlerErrorCode.InvalidRequest);
    }

    @Test
    public void handleRequest_Fails_With_CfnAlreadyExistsException() {
        final ResourceModel model = generateInitialResourceModel(false, TEST_RESOURCEPOLICY_CONTENT);

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
            .desiredResourceState(model)
            .build();

        final DescribeResourcePolicyResponse describeResourcePolicyResponse = getDescribeResourcePolicyResponse();
        when(mockProxyClient.client().describeResourcePolicy(any(DescribeResourcePolicyRequest.class))).thenReturn(describeResourcePolicyResponse);

        final ProgressEvent<ResourceModel, CallbackContext> response = createHandler.handleRequest(mockAwsClientProxy, request, new CallbackContext(), mockProxyClient, logger);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.FAILED);
        assertThat(response.getResourceModels()).isNull();
        assertThat(response.getErrorCode()).isEqualTo(HandlerErrorCode.AlreadyExists);

        verify(mockProxyClient.client()).describeResourcePolicy(any(DescribeResourcePolicyRequest.class));

        verify(mockOrgsClient, atLeastOnce()).serviceName();
        verifyNoMoreInteractions(mockOrgsClient);
    }

    @Test
    public void handleRequest_Fails_With_UnsupportedApiEndpointException() {
        final ResourceModel model = generateInitialResourceModel(false, TEST_RESOURCEPOLICY_CONTENT);

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
            .desiredResourceState(model)
            .build();

        when(mockProxyClient.client().describeResourcePolicy(any(DescribeResourcePolicyRequest.class))).thenThrow(UnsupportedApiEndpointException.class);

        final ProgressEvent<ResourceModel, CallbackContext> response = createHandler.handleRequest(mockAwsClientProxy, request, new CallbackContext(), mockProxyClient, logger);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.FAILED);
        assertThat(response.getResourceModels()).isNull();
        assertThat(response.getErrorCode()).isEqualTo(HandlerErrorCode.InvalidRequest);

        verify(mockProxyClient.client()).describeResourcePolicy(any(DescribeResourcePolicyRequest.class));

        verify(mockOrgsClient, atLeastOnce()).serviceName();
        verifyNoMoreInteractions(mockOrgsClient);
    }

    @Test
    public void handleRequest_Fails_With_ConcurrentModificationException() {
        handleRetriableException(ConcurrentModificationException.class, HandlerErrorCode.ResourceConflict);
    }

    @Test
    public void handleRequest_Fails_With_TooManyRequestsException() {
        handleRetriableException(TooManyRequestsException.class, HandlerErrorCode.Throttling);
    }

    @Test
    public void handleRequest_Fails_With_ServiceException() {
        handleRetriableException(ServiceException.class, HandlerErrorCode.ServiceInternalError);
    }

    private void handleRetriableException(Class<? extends Exception> exception, HandlerErrorCode errorCode) {
        final ResourceModel model = generateInitialResourceModel(false, TEST_RESOURCEPOLICY_CONTENT);

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
            .desiredResourceState(model)
            .build();

        final DescribeResourcePolicyResponse describeResourcePolicyResponse = getDescribeResourcePolicyResponse();
        when(mockProxyClient.client().describeResourcePolicy(any(DescribeResourcePolicyRequest.class))).thenReturn(null);

        final PutResourcePolicyResponse putResourcePolicyResponse = getPutResourcePolicyResponse();
        when(mockProxyClient.client().putResourcePolicy(any(PutResourcePolicyRequest.class))).thenThrow(exception);

        final ProgressEvent<ResourceModel, CallbackContext> response = createHandler.handleRequest(mockAwsClientProxy, request, new CallbackContext(), mockProxyClient, logger);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.FAILED);
        assertThat(response.getResourceModels()).isNull();
        assertThat(response.getErrorCode()).isEqualTo(errorCode);

        verify(mockProxyClient.client()).describeResourcePolicy(any(DescribeResourcePolicyRequest.class));

        verify(mockOrgsClient, atLeastOnce()).serviceName();
        verifyNoMoreInteractions(mockOrgsClient);
    }
}
