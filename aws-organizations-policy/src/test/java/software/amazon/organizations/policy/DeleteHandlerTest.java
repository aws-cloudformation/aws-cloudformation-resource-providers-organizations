package software.amazon.organizations.policy;

import org.junit.jupiter.api.AfterEach;
import software.amazon.awssdk.services.organizations.OrganizationsClient;
import software.amazon.awssdk.services.organizations.model.*;
import software.amazon.cloudformation.proxy.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
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

    @AfterEach
    public void tear_down() {
        verify(mockOrgsClient, atLeastOnce()).serviceName();
        verifyNoMoreInteractions(mockOrgsClient);
    }

    @Test
    public void deleteHandleRequest_SimpleSuccess() {
        final ResourceModel model = generateFinalResourceModel();

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
            .desiredResourceState(model)
            .build();

        final DetachPolicyResponse detachPolicyResponse = DetachPolicyResponse.builder().build();
        when(mockProxyClient.client().detachPolicy(any(DetachPolicyRequest.class))).thenReturn(detachPolicyResponse);

        final DeletePolicyResponse deleteOrganizationResponse = DeletePolicyResponse.builder().build();
        when(mockProxyClient.client().deletePolicy(any(DeletePolicyRequest.class))).thenReturn(deleteOrganizationResponse);

        final ProgressEvent<ResourceModel, CallbackContext> response = deleteHandler.handleRequest(mockAwsClientProxy, request, new CallbackContext(), mockProxyClient, logger);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.SUCCESS);
        assertThat(response.getCallbackDelaySeconds()).isEqualTo(0);
        assertThat(response.getResourceModel()).isNull();
        assertThat(response.getResourceModels()).isNull();
        assertThat(response.getMessage()).isNull();
        assertThat(response.getErrorCode()).isNull();

        verify(mockProxyClient.client()).deletePolicy(any(DeletePolicyRequest.class));
    }

    @Test
    public void deleteHandleRequest_Fails_With_CfnNotFoundException() {
        final ResourceModel model = generateFinalResourceModel();

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
    }

    @Test
    public void deleteHandleRequest_Fails_With_GeneralServiceException() {
        final ResourceModel model = generateFinalResourceModel();

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
        assertThat(response.getErrorCode()).isEqualTo(HandlerErrorCode.GeneralServiceException);
    }
}
