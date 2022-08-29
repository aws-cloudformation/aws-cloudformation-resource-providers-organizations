package software.amazon.organizations.account;

import java.time.Duration;

import software.amazon.awssdk.services.account.AccountClient;
import software.amazon.awssdk.services.organizations.OrganizationsClient;
import software.amazon.awssdk.services.organizations.model.AccountNotFoundException;
import software.amazon.awssdk.services.organizations.model.CloseAccountRequest;
import software.amazon.awssdk.services.organizations.model.CloseAccountResponse;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class DeleteHandlerTest extends AbstractTestBase {

    @Mock
    OrganizationsClient mockOrgsClient;
    AccountClient mockAccountClient;
    @Mock
    private AmazonWebServicesClientProxy mockAwsClientProxy;
    @Mock
    private ProxyClient<OrganizationsClient> mockProxyClient;
    private ProxyClient<AccountClient> mockAccountProxyClient;
    private DeleteHandler deleteHandler;

    @BeforeEach
    public void setup() {
        deleteHandler = new DeleteHandler();
        mockAwsClientProxy = new AmazonWebServicesClientProxy(logger, MOCK_CREDENTIALS, () -> Duration.ofSeconds(600).toMillis());
        mockOrgsClient = mock(OrganizationsClient.class);
        mockAccountClient = mock(AccountClient.class);
        mockProxyClient = MOCK_PROXY(mockAwsClientProxy, mockOrgsClient);
        mockAccountProxyClient = MOCK_ACCOUNT_PROXY(mockAwsClientProxy, mockAccountClient);
    }

    @Test
    public void handleRequest_SimpleSuccess() {
        final ResourceModel model = generateDeleteResourceModel();

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
                                                                  .desiredResourceState(model)
                                                                  .build();

        final CloseAccountResponse closeAccountResponse = CloseAccountResponse.builder().build();

        when(mockProxyClient.client().closeAccount(any(CloseAccountRequest.class))).thenReturn(closeAccountResponse);

        final ProgressEvent<ResourceModel, CallbackContext> response = deleteHandler.handleRequest(mockAwsClientProxy, request, new CallbackContext(), mockProxyClient, mockAccountProxyClient, logger);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.SUCCESS);
        assertThat(response.getCallbackDelaySeconds()).isEqualTo(0);
        assertThat(response.getResourceModel()).isNull();
        assertThat(response.getResourceModels()).isNull();
        assertThat(response.getMessage()).isNull();
        assertThat(response.getErrorCode()).isNull();

        verify(mockProxyClient.client()).closeAccount(any(CloseAccountRequest.class));
    }

    @Test
    public void handleRequest_Fails_With_CfnNotFoundException() {
        final ResourceModel model = generateDeleteResourceModel();

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
                                                                  .desiredResourceState(model)
                                                                  .build();

        when(mockProxyClient.client().closeAccount(any(CloseAccountRequest.class))).thenThrow(AccountNotFoundException.class);

        final ProgressEvent<ResourceModel, CallbackContext> response = deleteHandler.handleRequest(mockAwsClientProxy, request, new CallbackContext(), mockProxyClient, mockAccountProxyClient, logger);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.FAILED);
        assertThat(response.getResourceModels()).isNull();
        assertThat(response.getErrorCode()).isEqualTo(HandlerErrorCode.NotFound);
    }
}
