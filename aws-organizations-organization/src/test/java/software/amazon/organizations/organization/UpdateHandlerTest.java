package software.amazon.organizations.organization;

import java.time.Duration;

import software.amazon.awssdk.services.organizations.OrganizationsClient;

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
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)

public class UpdateHandlerTest extends AbstractTestBase {
    @Mock
    private AmazonWebServicesClientProxy mockAwsClientProxy;

    @Mock
    private ProxyClient<OrganizationsClient> mockProxyClient;

    @Mock
    OrganizationsClient mockOrgsClient;

    private UpdateHandler updateHandler;

    @BeforeEach
    public void setup() {
        updateHandler = new UpdateHandler();
        mockAwsClientProxy = new AmazonWebServicesClientProxy(loggerProxy, MOCK_CREDENTIALS, () -> Duration.ofSeconds(600).toMillis());
        mockOrgsClient = mock(OrganizationsClient.class);
        mockProxyClient = MOCK_PROXY(mockAwsClientProxy, mockOrgsClient);
    }

    @Test
    protected void handleRequest_updateFeatureSet_shouldReturnFailed_withUpdateNotSupported() {

        final ResourceModel model = ResourceModel.builder()
                .featureSet(CONSOLIDATED_BILLING)
                .build();
        final ResourceModel previousModel = ResourceModel.builder()
                                        .featureSet(TEST_FEATURE_SET)
                                        .build();

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
                .desiredResourceState(model)
                .previousResourceState(previousModel)
                .build();

        final ProgressEvent<ResourceModel, CallbackContext> response = updateHandler.handleRequest(mockAwsClientProxy, request, new CallbackContext(), mockProxyClient, logger);
        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.FAILED);
        assertThat(response.getErrorCode()).isEqualTo(HandlerErrorCode.InvalidRequest);
    }

    @Test
    protected void handleRequest_shouldReturnSuccess() {

        final ResourceModel model = ResourceModel.builder()
                .featureSet(TEST_FEATURE_SET)
                .build();
        final ResourceModel previousModel = ResourceModel.builder().build();

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
                .previousResourceState(previousModel)
                .desiredResourceState(model)
                .build();

        final ProgressEvent<ResourceModel, CallbackContext> response = updateHandler.handleRequest(mockAwsClientProxy, request, new CallbackContext(), mockProxyClient, logger);
        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.SUCCESS);
    }
}
