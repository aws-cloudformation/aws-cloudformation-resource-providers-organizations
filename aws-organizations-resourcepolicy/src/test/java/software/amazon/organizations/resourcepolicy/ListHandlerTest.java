package software.amazon.organizations.resourcepolicy;

import java.time.Duration;

import software.amazon.awssdk.services.organizations.model.AccessDeniedException;
import software.amazon.awssdk.services.organizations.model.AwsOrganizationsNotInUseException;
import software.amazon.awssdk.services.organizations.OrganizationsClient;
import software.amazon.awssdk.services.organizations.model.DescribeResourcePolicyRequest;
import software.amazon.awssdk.services.organizations.model.DescribeResourcePolicyResponse;
import software.amazon.awssdk.services.organizations.model.ResourcePolicyNotFoundException;
import software.amazon.awssdk.services.organizations.model.ServiceException;
import software.amazon.awssdk.services.organizations.model.TooManyRequestsException;
import software.amazon.awssdk.services.organizations.model.UnsupportedApiEndpointException;

import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.HandlerErrorCode;
import software.amazon.cloudformation.proxy.OperationStatus;
import software.amazon.cloudformation.proxy.ProgressEvent;
import software.amazon.cloudformation.proxy.ProxyClient;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class ListHandlerTest extends AbstractTestBase {

    @Mock
    private AmazonWebServicesClientProxy mockAwsClientProxy;

    @Mock
    private ProxyClient<OrganizationsClient> mockProxyClient;

    @Mock
    OrganizationsClient mockOrgsClient;

    private ListHandler listHandler;

    @BeforeEach
    public void setup() {
        listHandler = new ListHandler();
        mockAwsClientProxy = new AmazonWebServicesClientProxy(logger, MOCK_CREDENTIALS, () -> Duration.ofSeconds(600).toMillis());
        mockOrgsClient = mock(OrganizationsClient.class);
        mockProxyClient = MOCK_PROXY(mockAwsClientProxy, mockOrgsClient);
    }

    @Test
    public void handleRequest_SimpleSuccess() {
        final ResourceModel model = generateInitialResourceModel(true, TEST_RESOURCEPOLICY_CONTENT);
        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
            .desiredResourceState(model)
            .build();

        final DescribeResourcePolicyResponse describeResourcePolicyResponse = getDescribeResourcePolicyResponse();
        when(mockProxyClient.client().describeResourcePolicy(any(DescribeResourcePolicyRequest.class))).thenReturn(describeResourcePolicyResponse);

        final ProgressEvent<ResourceModel, CallbackContext> response = listHandler.handleRequest(mockAwsClientProxy, request, new CallbackContext(), mockProxyClient, logger);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.SUCCESS);
        assertThat(response.getCallbackDelaySeconds()).isEqualTo(0);
        assertThat(response.getResourceModel()).isNull();
        assertThat(response.getResourceModels()).isNotNull();
        assertThat(response.getNextToken()).isNull();
        assertThat(response.getMessage()).isNull();
        assertThat(response.getErrorCode()).isNull();

        verify(mockProxyClient.client()).describeResourcePolicy(any(DescribeResourcePolicyRequest.class));
    }

    @Test
    public void handleRequest_NullDesiredModel_Fails_With_CfnInvalidRequest() {
        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
            .build();

        final ProgressEvent<ResourceModel, CallbackContext> response =
            listHandler.handleRequest(mockAwsClientProxy, request, new CallbackContext(), mockProxyClient, logger);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.FAILED);
        assertThat(response.getResourceModels()).isNull();
        assertThat(response.getErrorCode()).isEqualTo(HandlerErrorCode.InvalidRequest);
    }

    @Test
    public void handleRequest_Succeeds_With_ResourcePolicyNotFoundException() {
        final ResourceModel model = ResourceModel.builder().build();
        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
            .desiredResourceState(model)
            .build();

        when(mockProxyClient.client().describeResourcePolicy(any(DescribeResourcePolicyRequest.class))).thenThrow(ResourcePolicyNotFoundException.class);
        final ProgressEvent<ResourceModel, CallbackContext> response = listHandler.handleRequest(mockAwsClientProxy, request, new CallbackContext(), mockProxyClient, logger);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.SUCCESS);
        assertThat(response.getResourceModels()).isNotNull();
        assertThat(response.getNextToken()).isNull();
        assertThat(response.getMessage()).isNull();
        assertThat(response.getErrorCode()).isNull();
    }

    @Test
    public void handleRequest_Fails_With_AccessDeniedException() {
        nonRetriableExceptionTest(AccessDeniedException.class, HandlerErrorCode.AccessDenied);
    }

    @Test
    public void handleRequest_Fails_With_UnsupportedApiEndpointException() {
        nonRetriableExceptionTest(UnsupportedApiEndpointException.class, HandlerErrorCode.InvalidRequest);
    }

    @Test
    public void handleRequest_Fails_With_AwsOrganizationsNotInUseException() {
        nonRetriableExceptionTest(AwsOrganizationsNotInUseException.class, HandlerErrorCode.NotFound);
    }

    @Test
    public void handleRequest_Fails_With_ServiceException() {
        exceptionTestFailure(ServiceException.class, HandlerErrorCode.ServiceInternalError);
    }

    @Test
    public void handleRequest_Fails_With_TooManyRequestsException() {
        exceptionTestFailure(TooManyRequestsException.class, HandlerErrorCode.Throttling);
    }

    @Test
    public void handleRequest_Succeeds_With_ServiceException_Retry() {
        exceptionTestFailure(ServiceException.class, HandlerErrorCode.ServiceInternalError);
    }

    @Test
    public void handleRequest_Succeeds_With_TooManyRequestsException_Retry() {
        exceptionTestFailure(TooManyRequestsException.class, HandlerErrorCode.Throttling);
    }

    private void nonRetriableExceptionTest(Class<? extends Exception> e, HandlerErrorCode errorCode) {
        final ResourceModel model = ResourceModel.builder().build();
        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
            .desiredResourceState(model)
            .build();

        when(mockProxyClient.client().describeResourcePolicy(any(DescribeResourcePolicyRequest.class))).thenThrow(e);
        final ProgressEvent<ResourceModel, CallbackContext> response = listHandler.handleRequest(mockAwsClientProxy, request, new CallbackContext(), mockProxyClient, logger);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.FAILED);
        assertThat(response.getResourceModels()).isNull();
        assertThat(response.getErrorCode()).isEqualTo(errorCode);
    }

    private void exceptionTestFailure(Class<? extends Exception> e, HandlerErrorCode errorCode) {
        final ResourceModel model = ResourceModel.builder().build();
        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
            .desiredResourceState(model)
            .build();

        when(mockProxyClient.client().describeResourcePolicy(any(DescribeResourcePolicyRequest.class))).thenThrow(e);
        final ProgressEvent<ResourceModel, CallbackContext> response = listHandler.handleRequest(mockAwsClientProxy, request, new CallbackContext(), mockProxyClient, logger);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.FAILED);
        assertThat(response.getCallbackDelaySeconds()).isEqualTo(0);
        assertThat(response.getResourceModels()).isNull();
        assertThat(response.getErrorCode()).isEqualTo(errorCode);
        verify(mockProxyClient.client()).describeResourcePolicy(any(DescribeResourcePolicyRequest.class));
    }

}
