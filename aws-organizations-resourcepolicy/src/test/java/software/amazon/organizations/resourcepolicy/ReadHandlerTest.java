package software.amazon.organizations.resourcepolicy;

import java.time.Duration;

import software.amazon.awssdk.services.organizations.OrganizationsClient;
import software.amazon.awssdk.services.organizations.model.DescribeResourcePolicyRequest;
import software.amazon.awssdk.services.organizations.model.DescribeResourcePolicyResponse;
import software.amazon.awssdk.services.organizations.model.ListTagsForResourceRequest;
import software.amazon.awssdk.services.organizations.model.ListTagsForResourceResponse;
import software.amazon.awssdk.services.organizations.model.ResourcePolicyNotFoundException;
import software.amazon.awssdk.services.organizations.model.ServiceException;
import software.amazon.awssdk.services.organizations.model.TooManyRequestsException;
import software.amazon.awssdk.services.organizations.model.ConcurrentModificationException;

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
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
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

    @AfterEach
    public void tear_down() {
        verify(mockOrgsClient, atLeastOnce()).serviceName();
        verifyNoMoreInteractions(mockOrgsClient);
    }

    @Test
    public void handleRequest_SimpleSuccess() {
        final ResourceModel model = generateInitialResourceModel(true, TEST_RESOURCEPOLICY_CONTENT);

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
            .desiredResourceState(model)
            .build();

        final DescribeResourcePolicyResponse describeResourcePolicyResponse = getDescribeResourcePolicyResponse();
        when(mockProxyClient.client().describeResourcePolicy(any(DescribeResourcePolicyRequest.class))).thenReturn(describeResourcePolicyResponse);

        final ListTagsForResourceResponse listTagsResponse = TagTestResourceHelper.buildDefaultTagsResponse();
        when(mockProxyClient.client().listTagsForResource(any(ListTagsForResourceRequest.class))).thenReturn(listTagsResponse);

        final ProgressEvent<ResourceModel, CallbackContext> response = readHandler.handleRequest(mockAwsClientProxy, request, new CallbackContext(), mockProxyClient, logger);

        verifyHandlerSuccess(response, request);

        verify(mockProxyClient.client()).describeResourcePolicy(any(DescribeResourcePolicyRequest.class));
        verify(mockProxyClient.client()).listTagsForResource(any(ListTagsForResourceRequest.class));
    }

    @Test
    public void handleRequest_WithJSONContent_SimpleSuccess() {
        final ResourceModel model = generateInitialResourceModel(true, TEST_RESOURCEPOLICY_CONTENT_JSON);

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
            .desiredResourceState(model)
            .build();

        final DescribeResourcePolicyResponse describeResourcePolicyResponse = getDescribeResourcePolicyResponse();

        when(mockProxyClient.client().describeResourcePolicy(any(DescribeResourcePolicyRequest.class))).thenReturn(describeResourcePolicyResponse);

        final ListTagsForResourceResponse listTagsResponse = TagTestResourceHelper.buildDefaultTagsResponse();

        when(mockProxyClient.client().listTagsForResource(any(ListTagsForResourceRequest.class))).thenReturn(listTagsResponse);

        final ProgressEvent<ResourceModel, CallbackContext> response = readHandler.handleRequest(mockAwsClientProxy, request, new CallbackContext(), mockProxyClient, logger);

        verifyHandlerSuccess(response, request);

        verify(mockProxyClient.client()).describeResourcePolicy(any(DescribeResourcePolicyRequest.class));
        verify(mockProxyClient.client()).listTagsForResource(any(ListTagsForResourceRequest.class));
    }

    @Test
    protected void handlerRequest_Fails_With_CfnNotFoundException() {
        final ResourceModel model = ResourceModel.builder()
            .content(TEST_RESOURCEPOLICY_CONTENT)
            .build();

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
            .desiredResourceState(model)
            .build();

        when(mockProxyClient.client().describeResourcePolicy(any(DescribeResourcePolicyRequest.class))).thenThrow(ResourcePolicyNotFoundException.class);

        final ProgressEvent<ResourceModel, CallbackContext> response = readHandler.handleRequest(mockAwsClientProxy, request, new CallbackContext(), mockProxyClient, logger);
        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.FAILED);
        assertThat(response.getResourceModels()).isNull();
        assertThat(response.getErrorCode()).isEqualTo(HandlerErrorCode.NotFound);
    }

    @Test
    public void handleRequest_ListTagsForResource_Fails_With_ServiceException() {
        exceptionTestFailure_ListTagsForResource(ServiceException.class, HandlerErrorCode.ServiceInternalError);
    }

    @Test
    public void handleRequest_ListTagsForResource_Fails_With_TooManyRequestsException() {
        exceptionTestFailure_ListTagsForResource(TooManyRequestsException.class, HandlerErrorCode.Throttling);
    }

    @Test
    public void handleRequest_ListTagsForResource_Fails_With_ConcurrentModificationException() {
        exceptionTestFailure_ListTagsForResource(ConcurrentModificationException.class, HandlerErrorCode.ResourceConflict);
    }

    @Test
    public void handleRequest_DescribeResourcePolicy_Fails_With_ServiceException() {
        exceptionTestFailure_DescribeResourcePolicy(ServiceException.class, HandlerErrorCode.ServiceInternalError);
    }

    @Test
    public void handleRequest_DescribeResourcePolicy_Fails_With_TooManyRequestsException() {
        exceptionTestFailure_DescribeResourcePolicy(TooManyRequestsException.class, HandlerErrorCode.Throttling);
    }

    @Test
    public void handleRequest_DescribeResourcePolicy_Fails_With_ConcurrentModificationException() {
        exceptionTestFailure_DescribeResourcePolicy(ConcurrentModificationException.class, HandlerErrorCode.ResourceConflict);
    }

    private void exceptionTestFailure_ListTagsForResource(Class<? extends Exception> e, HandlerErrorCode errorCode) {
        final ResourceModel model = ResourceModel.builder().build();
        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
            .desiredResourceState(model)
            .build();

        final DescribeResourcePolicyResponse describeResourcePolicyResponse = getDescribeResourcePolicyResponse();
        when(mockProxyClient.client().describeResourcePolicy(any(DescribeResourcePolicyRequest.class))).thenReturn(describeResourcePolicyResponse);
        when(mockProxyClient.client().listTagsForResource(any(ListTagsForResourceRequest.class))).thenThrow(e);

        final ProgressEvent<ResourceModel, CallbackContext> response = readHandler.handleRequest(mockAwsClientProxy, request, new CallbackContext(), mockProxyClient, logger);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.FAILED);
        assertThat(response.getResourceModels()).isNull();
        assertThat(response.getErrorCode()).isEqualTo(errorCode);

        verify(mockProxyClient.client()).describeResourcePolicy(any(DescribeResourcePolicyRequest.class));
        verify(mockProxyClient.client()).listTagsForResource(any(ListTagsForResourceRequest.class));
    }

    private void exceptionTestFailure_DescribeResourcePolicy(Class<? extends Exception> e, HandlerErrorCode errorCode) {
        final ResourceModel model = ResourceModel.builder().build();
        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
            .desiredResourceState(model)
            .build();

        when(mockProxyClient.client().describeResourcePolicy(any(DescribeResourcePolicyRequest.class))).thenThrow(e);
        final ProgressEvent<ResourceModel, CallbackContext> response = readHandler.handleRequest(mockAwsClientProxy, request, new CallbackContext(), mockProxyClient, logger);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.FAILED);
        assertThat(response.getCallbackDelaySeconds()).isEqualTo(0);
        assertThat(response.getResourceModels()).isNull();
        assertThat(response.getErrorCode()).isEqualTo(errorCode);
        verify(mockProxyClient.client()).describeResourcePolicy(any(DescribeResourcePolicyRequest.class));
    }

}
