package software.amazon.organizations.policy;

import software.amazon.awssdk.services.organizations.OrganizationsClient;
import software.amazon.awssdk.services.organizations.model.ConcurrentModificationException;
import software.amazon.awssdk.services.organizations.model.DeletePolicyRequest;
import software.amazon.awssdk.services.organizations.model.DeletePolicyResponse;
import software.amazon.awssdk.services.organizations.model.DetachPolicyRequest;
import software.amazon.awssdk.services.organizations.model.DetachPolicyResponse;
import software.amazon.awssdk.services.organizations.model.PolicyInUseException;
import software.amazon.awssdk.services.organizations.model.PolicyNotAttachedException;
import software.amazon.awssdk.services.organizations.model.PolicyNotFoundException;

import software.amazon.awssdk.services.organizations.model.TargetNotFoundException;
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

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class DeleteHandlerTest extends AbstractTestBase {

    @Mock
    OrganizationsClient mockOrgsClient;
    @Mock
    private AmazonWebServicesClientProxy mockAwsClientProxy;
    @Mock
    private ProxyClient<OrganizationsClient> mockProxyClient;
    private DeleteHandler deleteHandler;

    @BeforeEach
    public void setup() {
        deleteHandler = new DeleteHandler();
        mockAwsClientProxy = new AmazonWebServicesClientProxy(logger, MOCK_CREDENTIALS, () -> Duration.ofSeconds(600).toMillis());
        mockOrgsClient = mock(OrganizationsClient.class);
        mockProxyClient = MOCK_PROXY(mockAwsClientProxy, mockOrgsClient);
    }

    @Test
    public void handleRequest_NoTargets_SimpleRequest() {
        final ResourceModel model = generateFinalResourceModel(false, true);

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
            .desiredResourceState(model)
            .build();

        final DeletePolicyResponse deletePolicyResponse = DeletePolicyResponse.builder().build();
        when(mockProxyClient.client().deletePolicy(any(DeletePolicyRequest.class))).thenReturn(deletePolicyResponse);

        final ProgressEvent<ResourceModel, CallbackContext> response = deleteHandler.handleRequest(mockAwsClientProxy, request, new CallbackContext(), mockProxyClient, logger);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.SUCCESS);
        assertThat(response.getCallbackDelaySeconds()).isEqualTo(0);
        assertThat(response.getResourceModel()).isNull();
        assertThat(response.getResourceModels()).isNull();
        assertThat(response.getMessage()).isNull();
        assertThat(response.getErrorCode()).isNull();

        verify(mockProxyClient.client()).deletePolicy(any(DeletePolicyRequest.class));

        verify(mockOrgsClient, atLeastOnce()).serviceName();
        verifyNoMoreInteractions(mockOrgsClient);
    }

    @Test
    public void deleteHandleRequest_SimpleSuccess() {
        final ResourceModel model = generateFinalResourceModel(true, true);

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
            .desiredResourceState(model)
            .build();

        final DetachPolicyResponse detachPolicyResponse = DetachPolicyResponse.builder().build();
        when(mockProxyClient.client().detachPolicy(any(DetachPolicyRequest.class))).thenReturn(detachPolicyResponse);

        final DeletePolicyResponse deletePolicyResponse = DeletePolicyResponse.builder().build();
        when(mockProxyClient.client().deletePolicy(any(DeletePolicyRequest.class))).thenReturn(deletePolicyResponse);

        final ProgressEvent<ResourceModel, CallbackContext> response = deleteHandler.handleRequest(mockAwsClientProxy, request, new CallbackContext(), mockProxyClient, logger);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.SUCCESS);
        assertThat(response.getCallbackDelaySeconds()).isEqualTo(0);
        assertThat(response.getResourceModel()).isNull();
        assertThat(response.getResourceModels()).isNull();
        assertThat(response.getMessage()).isNull();
        assertThat(response.getErrorCode()).isNull();

        verify(mockProxyClient.client(), times(2)).detachPolicy(any(DetachPolicyRequest.class));
        verify(mockProxyClient.client()).deletePolicy(any(DeletePolicyRequest.class));

        verify(mockOrgsClient, atLeastOnce()).serviceName();
        verifyNoMoreInteractions(mockOrgsClient);
    }

    @Test
    public void handleRequest_WithTargets_DetachPolicyFailsWithPolicyNotAttached_HandlerSucceeds() {
        final ResourceModel model = generateFinalResourceModel(true, false);

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
            .desiredResourceState(model)
            .build();

        when(mockProxyClient.client().detachPolicy(any(DetachPolicyRequest.class))).thenThrow(PolicyNotAttachedException.class);

        final DeletePolicyResponse deletePolicyResponse = DeletePolicyResponse.builder().build();
        when(mockProxyClient.client().deletePolicy(any(DeletePolicyRequest.class))).thenReturn(deletePolicyResponse);

        final ProgressEvent<ResourceModel, CallbackContext> response = deleteHandler.handleRequest(mockAwsClientProxy, request, new CallbackContext(), mockProxyClient, logger);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.SUCCESS);
        assertThat(response.getCallbackDelaySeconds()).isEqualTo(0);
        assertThat(response.getResourceModel()).isNull();
        assertThat(response.getResourceModels()).isNull();
        assertThat(response.getMessage()).isNull();
        assertThat(response.getErrorCode()).isNull();

        verify(mockProxyClient.client(), times(2)).detachPolicy(any(DetachPolicyRequest.class));
        verify(mockProxyClient.client()).deletePolicy(any(DeletePolicyRequest.class));

        verify(mockOrgsClient, atLeastOnce()).serviceName();
        verifyNoMoreInteractions(mockOrgsClient);
    }

    @Test
    public void handleRequest_WithTargets_DetachPolicyFailsWithTargetNotFound_HandlerSucceeds() {
        final ResourceModel model = generateFinalResourceModel(true, false);

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
            .desiredResourceState(model)
            .build();

        when(mockProxyClient.client().detachPolicy(any(DetachPolicyRequest.class))).thenThrow(TargetNotFoundException.class);

        final DeletePolicyResponse deletePolicyResponse = DeletePolicyResponse.builder().build();
        when(mockProxyClient.client().deletePolicy(any(DeletePolicyRequest.class))).thenReturn(deletePolicyResponse);

        final ProgressEvent<ResourceModel, CallbackContext> response = deleteHandler.handleRequest(mockAwsClientProxy, request, new CallbackContext(), mockProxyClient, logger);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.SUCCESS);
        assertThat(response.getCallbackDelaySeconds()).isEqualTo(0);
        assertThat(response.getResourceModel()).isNull();
        assertThat(response.getResourceModels()).isNull();
        assertThat(response.getMessage()).isNull();
        assertThat(response.getErrorCode()).isNull();

        verify(mockProxyClient.client(), times(2)).detachPolicy(any(DetachPolicyRequest.class));
        verify(mockProxyClient.client()).deletePolicy(any(DeletePolicyRequest.class));

        verify(mockOrgsClient, atLeastOnce()).serviceName();
        verifyNoMoreInteractions(mockOrgsClient);
    }

    @Test
    public void handleRequest_WithTargets_DetachPolicyFailsWithPolicyNotFound_Fails_With_CfnNotFoundException() {
        final ResourceModel model = generateFinalResourceModel(true, false);

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
            .desiredResourceState(model)
            .build();

        when(mockProxyClient.client().detachPolicy(any(DetachPolicyRequest.class))).thenThrow(PolicyNotFoundException.class);

        final ProgressEvent<ResourceModel, CallbackContext> response = deleteHandler.handleRequest(mockAwsClientProxy, request, new CallbackContext(), mockProxyClient, logger);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.FAILED);
        assertThat(response.getResourceModels()).isNull();
        assertThat(response.getErrorCode()).isEqualTo(HandlerErrorCode.NotFound);

        verify(mockProxyClient.client()).detachPolicy(any(DetachPolicyRequest.class));

        verifyNoMoreInteractions(mockOrgsClient);
    }

    @Test
    public void handleRequest_WithTargets_DeletePolicyFailsWithConcurrentModificationException_ShouldSkipDetachPolicyIfAllDetachedAlready() {
        final ResourceModel model = generateFinalResourceModel(true, false);

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
                                                                  .desiredResourceState(model)
                                                                  .build();

        final DetachPolicyResponse detachPolicyResponse = DetachPolicyResponse.builder().build();
        when(mockProxyClient.client().detachPolicy(any(DetachPolicyRequest.class))).thenReturn(detachPolicyResponse);

        when(mockProxyClient.client().deletePolicy(any(DeletePolicyRequest.class))).thenThrow(ConcurrentModificationException.class);

        CallbackContext context = new CallbackContext();
        ProgressEvent<ResourceModel, CallbackContext> response = deleteHandler.handleRequest(mockAwsClientProxy, request, context, mockProxyClient, logger);
        // retry attempt 1
        assertThat(context.isPolicyDetached()).isEqualTo(true);
        assertThat(context.getRetryDeleteAttempt()).isEqualTo(1);
        assertThat(response.getStatus()).isEqualTo(OperationStatus.IN_PROGRESS);
        assertThat(response.getCallbackDelaySeconds()).isGreaterThan(0);
        // retry attempt 2
        response = deleteHandler.handleRequest(mockAwsClientProxy, request, context, mockProxyClient, logger);
        assertThat(context.getRetryDeleteAttempt()).isEqualTo(2);
        assertThat(response.getStatus()).isEqualTo(OperationStatus.IN_PROGRESS);
        assertThat(response.getCallbackDelaySeconds()).isGreaterThan(0);
        // retry attempt 3
        response = deleteHandler.handleRequest(mockAwsClientProxy, request, context, mockProxyClient, logger);
        assertThat(context.getRetryDeleteAttempt()).isEqualTo(3);
        assertThat(response.getStatus()).isEqualTo(OperationStatus.IN_PROGRESS);
        assertThat(response.getCallbackDelaySeconds()).isGreaterThan(0);

        response = deleteHandler.handleRequest(mockAwsClientProxy, request, context, mockProxyClient, logger);
        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.FAILED);
        assertThat(response.getResourceModels()).isNull();

        // verify detachPolicy is only invoked 2 times since there are 2 targets and attachPolicy invoked at least maxRetryCount times
        verify(mockProxyClient.client(), times(2)).detachPolicy(any(DetachPolicyRequest.class));
        verify(mockProxyClient.client(), atLeast(3)).deletePolicy(any(DeletePolicyRequest.class));
    }

    @Test
    public void deleteHandleRequest_Fails_With_CfnNotFoundException() {
        final ResourceModel model = generateFinalResourceModel(true, true);

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
            .desiredResourceState(model)
            .build();

        final DetachPolicyResponse detachPolicyResponse = DetachPolicyResponse.builder().build();
        when(mockProxyClient.client().detachPolicy(any(DetachPolicyRequest.class))).thenReturn(detachPolicyResponse);

        when(mockProxyClient.client().deletePolicy(any(DeletePolicyRequest.class))).thenThrow(PolicyNotFoundException.class);

        final ProgressEvent<ResourceModel, CallbackContext> response = deleteHandler.handleRequest(mockAwsClientProxy, request, new CallbackContext(), mockProxyClient, logger);
        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.FAILED);
        assertThat(response.getResourceModels()).isNull();
        assertThat(response.getErrorCode()).isEqualTo(HandlerErrorCode.NotFound);

        verify(mockOrgsClient, atLeastOnce()).serviceName();
        verifyNoMoreInteractions(mockOrgsClient);
    }

    @Test
    public void deleteHandleRequest_Fails_With_GeneralServiceException() {
        final ResourceModel model = generateFinalResourceModel(true, true);

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
            .desiredResourceState(model)
            .build();

        final DetachPolicyResponse detachPolicyResponse = DetachPolicyResponse.builder().build();
        when(mockProxyClient.client().detachPolicy(any(DetachPolicyRequest.class))).thenReturn(detachPolicyResponse);

        when(mockProxyClient.client().deletePolicy(any(DeletePolicyRequest.class))).thenThrow(PolicyInUseException.class);

        final ProgressEvent<ResourceModel, CallbackContext> response = deleteHandler.handleRequest(mockAwsClientProxy, request, new CallbackContext(), mockProxyClient, logger);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.FAILED);
        assertThat(response.getResourceModels()).isNull();
        assertThat(response.getErrorCode()).isEqualTo(HandlerErrorCode.InvalidRequest);

        verify(mockOrgsClient, atLeastOnce()).serviceName();
        verifyNoMoreInteractions(mockOrgsClient);
    }
}
