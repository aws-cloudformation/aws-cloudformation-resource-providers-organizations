package software.amazon.organizations.account;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.organizations.OrganizationsClient;
import software.amazon.awssdk.services.organizations.model.Account;
import software.amazon.awssdk.services.organizations.model.ListAccountsRequest;
import software.amazon.awssdk.services.organizations.model.ListAccountsResponse;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.HandlerErrorCode;
import software.amazon.cloudformation.proxy.OperationStatus;
import software.amazon.cloudformation.proxy.ProgressEvent;
import software.amazon.cloudformation.proxy.ProxyClient;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;

import java.time.Duration;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class ListHandlerTest extends AbstractTestBase{

    @Mock
    OrganizationsClient mockOrgsClient;
    @Mock
    private AmazonWebServicesClientProxy mockAwsClientProxy;
    @Mock
    private ProxyClient<OrganizationsClient> mockProxyClient;
    private ListHandler listHandler;

    @BeforeEach
    public void setup() {
        listHandler = new ListHandler();
        mockAwsClientProxy = new AmazonWebServicesClientProxy(logger, MOCK_CREDENTIALS, () -> Duration.ofSeconds(600).toMillis());
        mockOrgsClient = mock(OrganizationsClient.class);
        mockProxyClient = MOCK_PROXY(mockAwsClientProxy, mockOrgsClient);
    }

    @Test
    public void handleRequest_SimpleSuccess_WithNextToken() {
        final ResourceModel model = ResourceModel.builder()
                .build();
        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
                .desiredResourceState(model)
                .build();

        ListAccountsResponse listAccountsResponse = ListAccountsResponse.builder()
                .accounts(Arrays.asList(accountGetMockAccountSummary()))
                .nextToken(TEST_NEXT_TOKEN)
                .build();

        when(mockProxyClient.client().listAccounts(any(ListAccountsRequest.class))).thenReturn(listAccountsResponse);

        final ProgressEvent<ResourceModel, CallbackContext> response =
                listHandler.handleRequest(mockAwsClientProxy, request, new CallbackContext(), mockProxyClient, logger);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.SUCCESS);
        assertThat(response.getCallbackContext()).isNull();
        assertThat(response.getCallbackDelaySeconds()).isEqualTo(0);
        assertThat(response.getResourceModel()).isNull();
        assertThat(response.getResourceModels()).isNotNull();
        assertThat(response.getResourceModels().stream().count()).isEqualTo(1);
        assertThat(response.getNextToken()).isEqualTo(TEST_NEXT_TOKEN);
        assertThat(response.getMessage()).isNull();
        assertThat(response.getErrorCode()).isNull();

        ResourceModel responseModel = response.getResourceModels().get(0);
        assertThat(responseModel.getAccountId()).isEqualTo(TEST_ACCOUNT_ID);
        assertThat(responseModel.getAccountName()).isEqualTo(TEST_ACCOUNT_NAME);
        assertThat(responseModel.getEmail()).isEqualTo(TEST_ACCOUNT_EMAIL);

        verify(mockProxyClient.client()).listAccounts(any(ListAccountsRequest.class));
        verify(mockOrgsClient, atLeastOnce()).serviceName();
        verifyNoMoreInteractions(mockOrgsClient);
    }

    @Test
    public void handleRequest_FailsWithInvalidRequest(){
        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
                .build();

        final ProgressEvent<ResourceModel, CallbackContext> response =
                listHandler.handleRequest(mockAwsClientProxy, request, new CallbackContext(), mockProxyClient, logger);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.FAILED);
        assertThat(response.getResourceModels()).isNull();
        assertThat(response.getErrorCode()).isEqualTo(HandlerErrorCode.InvalidRequest);

    }

    protected Account accountGetMockAccountSummary(){
        return Account.builder()
                .email(TEST_ACCOUNT_EMAIL)
                .name(TEST_ACCOUNT_NAME)
                .id(TEST_ACCOUNT_ID)
                .build();
    }
}
