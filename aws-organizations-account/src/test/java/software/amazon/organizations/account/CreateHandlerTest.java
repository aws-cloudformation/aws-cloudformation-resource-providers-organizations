package software.amazon.organizations.account;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.organizations.OrganizationsClient;
import software.amazon.awssdk.services.organizations.model.Account;
import software.amazon.awssdk.services.organizations.model.AccessDeniedException;
import software.amazon.awssdk.services.organizations.model.ConcurrentModificationException;
import software.amazon.awssdk.services.organizations.model.CreateAccountRequest;
import software.amazon.awssdk.services.organizations.model.CreateAccountResponse;
import software.amazon.awssdk.services.organizations.model.CreateAccountStatus;
import software.amazon.awssdk.services.organizations.model.DescribeCreateAccountStatusRequest;
import software.amazon.awssdk.services.organizations.model.DescribeCreateAccountStatusResponse;
import software.amazon.awssdk.services.organizations.model.DestinationParentNotFoundException;
import software.amazon.awssdk.services.organizations.model.DuplicateAccountException;
import software.amazon.awssdk.services.organizations.model.ListAccountsRequest;
import software.amazon.awssdk.services.organizations.model.ListAccountsResponse;
import software.amazon.awssdk.services.organizations.model.ListParentsRequest;
import software.amazon.awssdk.services.organizations.model.ListParentsResponse;
import software.amazon.awssdk.services.organizations.model.MoveAccountRequest;
import software.amazon.awssdk.services.organizations.model.MoveAccountResponse;
import software.amazon.awssdk.services.organizations.model.Parent;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.HandlerErrorCode;
import software.amazon.cloudformation.proxy.OperationStatus;
import software.amazon.cloudformation.proxy.ProgressEvent;
import software.amazon.cloudformation.proxy.ProxyClient;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;

import java.time.Duration;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.atMost;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static software.amazon.organizations.account.TagTestResourcesHelper.defaultStackTags;

@ExtendWith(MockitoExtension.class)
public class CreateHandlerTest extends AbstractTestBase {

    @Mock
    OrganizationsClient mockOrgsClient;
    @Mock
    private AmazonWebServicesClientProxy mockAwsClientProxy;
    @Mock
    private ProxyClient<OrganizationsClient> mockProxyClient;
    private CreateHandler createHandler;

    @BeforeEach
    public void setup() {
        createHandler = new CreateHandler();
        mockAwsClientProxy = new AmazonWebServicesClientProxy(loggerProxy, MOCK_CREDENTIALS, () -> Duration.ofSeconds(600).toMillis());
        mockOrgsClient = mock(OrganizationsClient.class);
        mockProxyClient = MOCK_PROXY(mockAwsClientProxy, mockOrgsClient);
    }

    @Test
    public void handleRequest_SimpleSuccess() {
        final ResourceModel model = generateCreateResourceModel();

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
                                                                  .desiredResourceState(model)
                                                                  .desiredResourceTags(defaultStackTags)
                                                                  .build();

        final ListAccountsResponse listAccountsResponse = ListAccountsResponse.builder()
                .accounts(Collections.emptyList())
                .build();
        when(mockProxyClient.client().listAccounts(any(ListAccountsRequest.class))).thenReturn(listAccountsResponse);

        final CreateAccountResponse createAccountResponse = getCreateAccountResponse();
        final DescribeCreateAccountStatusResponse describeCreateAccountStatusResponse = getDescribeCreateAccountStatusResponse(SUCCEEDED);
        final MoveAccountResponse moveAccountResponse = getMoveAccountResponse();
        final ListParentsResponse listParentsResponseBeforeMoveAccountResponse = getListParentsResponseBeforeMoveAccount();

        when(mockProxyClient.client().createAccount(any(CreateAccountRequest.class))).thenReturn(createAccountResponse);
        when(mockProxyClient.client().describeCreateAccountStatus(any(DescribeCreateAccountStatusRequest.class))).thenReturn(describeCreateAccountStatusResponse);
        lenient().when(mockProxyClient.client().listParents(any(ListParentsRequest.class))).thenReturn(listParentsResponseBeforeMoveAccountResponse);
        when(mockProxyClient.client().moveAccount(any(MoveAccountRequest.class))).thenReturn(moveAccountResponse);

        final ProgressEvent<ResourceModel, CallbackContext> response = createHandler.handleRequest(mockAwsClientProxy, request, new CallbackContext(), mockProxyClient, logger);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.SUCCESS);
        assertThat(response.getCallbackDelaySeconds()).isEqualTo(0);
        assertThat(response.getResourceModel()).isNotNull();
        assertThat(response.getResourceModel().getAccountId()).isEqualTo(TEST_ACCOUNT_ID);
        assertThat(response.getResourceModel().getEmail()).isEqualTo(TEST_ACCOUNT_EMAIL);
        assertThat(response.getResourceModel().getAccountName()).isEqualTo(TEST_ACCOUNT_NAME);
        assertThat(response.getResourceModel().getParentIds()).isEqualTo(TEST_PARENT_IDS);
        assertThat(TagTestResourcesHelper.tagsEqual(
                TagsHelper.convertAccountTagToOrganizationTag(response.getResourceModel().getTags()),
                TagTestResourcesHelper.defaultTags)).isTrue();
        assertThat(response.getResourceModels()).isNull();
        assertThat(response.getMessage()).isNull();
        assertThat(response.getErrorCode()).isNull();

        verify(mockProxyClient.client()).listAccounts(any(ListAccountsRequest.class));
        verify(mockProxyClient.client()).createAccount(any(CreateAccountRequest.class));
        verify(mockProxyClient.client()).moveAccount(any(MoveAccountRequest.class));
        verify(mockProxyClient.client(), atLeast(1)).describeCreateAccountStatus(any(DescribeCreateAccountStatusRequest.class));
    }

    @Test
    public void handleRequest_FailedIfRequestPartitionIsGovCloud() {
        final ResourceModel model = generateCreateResourceModel();

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
                                                                  .awsPartition(GOV_CLOUD_PARTITION)
                                                                  .desiredResourceState(model)
                                                                  .desiredResourceTags(defaultStackTags)
                                                                  .build();

        final ProgressEvent<ResourceModel, CallbackContext> response = createHandler.handleRequest(mockAwsClientProxy, request, new CallbackContext(), loggerProxy);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.FAILED);
        assertThat(response.getCallbackDelaySeconds()).isEqualTo(0);
        assertThat(response.getResourceModel()).isNotNull();
        assertThat(response.getErrorCode()).isEqualTo(HandlerErrorCode.InvalidRequest);
        assertThat(response.getResourceModel().getAccountId()).isNull();
        assertThat(response.getResourceModel().getEmail()).isEqualTo(TEST_ACCOUNT_EMAIL);
        assertThat(response.getResourceModel().getAccountName()).isEqualTo(TEST_ACCOUNT_NAME);
        assertThat(response.getResourceModel().getParentIds()).isEqualTo(TEST_PARENT_IDS);
        assertThat(TagTestResourcesHelper.tagsEqual(
                TagsHelper.convertAccountTagToOrganizationTag(response.getResourceModel().getTags()),
                TagTestResourcesHelper.defaultTags)).isTrue();

        verify(mockProxyClient.client(), times(0)).createAccount(any(CreateAccountRequest.class));
        verify(mockProxyClient.client(), times(0)).describeCreateAccountStatus(any(DescribeCreateAccountStatusRequest.class));
        verify(mockProxyClient.client(), times(0)).moveAccount(any(MoveAccountRequest.class));
    }

    @Test
    public void handleRequest_FailedWithCfnAlreadyExistException() {
        final ResourceModel model = generateCreateResourceModel();

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
                                                                  .desiredResourceState(model)
                                                                  .desiredResourceTags(defaultStackTags)
                                                                  .build();

        final ListAccountsResponse listAccountsResponse = ListAccountsResponse.builder()
                .accounts(Collections.singletonList(Account.builder()
                        .id(TEST_ACCOUNT_ID)
                        .email(TEST_ACCOUNT_EMAIL)
                        .name(TEST_ACCOUNT_NAME)
                        .build()))
                .build();
        when(mockProxyClient.client().listAccounts(any(ListAccountsRequest.class))).thenReturn(listAccountsResponse);

        final ProgressEvent<ResourceModel, CallbackContext> response = createHandler.handleRequest(mockAwsClientProxy, request, new CallbackContext(), mockProxyClient, logger);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.FAILED);
        assertThat(response.getCallbackDelaySeconds()).isEqualTo(0);
        assertThat(response.getResourceModel()).isNotNull();
        assertThat(response.getErrorCode()).isEqualTo(HandlerErrorCode.AlreadyExists);
        assertThat(response.getResourceModel().getAccountId()).isEqualTo(TEST_ACCOUNT_ID);
        assertThat(response.getResourceModel().getEmail()).isEqualTo(TEST_ACCOUNT_EMAIL);
        assertThat(response.getResourceModel().getAccountName()).isEqualTo(TEST_ACCOUNT_NAME);
        assertThat(response.getResourceModel().getParentIds()).isEqualTo(TEST_PARENT_IDS);
        assertThat(TagTestResourcesHelper.tagsEqual(
                TagsHelper.convertAccountTagToOrganizationTag(response.getResourceModel().getTags()),
                TagTestResourcesHelper.defaultTags)).isTrue();

        verify(mockProxyClient.client()).listAccounts(any(ListAccountsRequest.class));
        verify(mockProxyClient.client(), times(0)).createAccount(any(CreateAccountRequest.class));
        verify(mockProxyClient.client(), times(0)).describeCreateAccountStatus(any(DescribeCreateAccountStatusRequest.class));
        verify(mockProxyClient.client(), times(0)).moveAccount(any(MoveAccountRequest.class));
    }


    @Test
    public void handleRequest_FailedInMoveAccountWhenMultipleParentIds() {
        final ResourceModel model = generateCreateResourceModelWithMultipleParentIds();

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
                                                                  .desiredResourceState(model)
                                                                  .desiredResourceTags(defaultStackTags)
                                                                  .build();

        final ProgressEvent<ResourceModel, CallbackContext> response = createHandler.handleRequest(mockAwsClientProxy, request, new CallbackContext(), mockProxyClient, logger);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.FAILED);
        assertThat(response.getCallbackDelaySeconds()).isEqualTo(0);
        assertThat(response.getResourceModel()).isNotNull();
        assertThat(response.getResourceModel().getAccountId()).isNull();
        assertThat(response.getResourceModel().getEmail()).isEqualTo(TEST_ACCOUNT_EMAIL);
        assertThat(response.getResourceModel().getAccountName()).isEqualTo(TEST_ACCOUNT_NAME);
        assertThat(TagTestResourcesHelper.tagsEqual(
                TagsHelper.convertAccountTagToOrganizationTag(response.getResourceModel().getTags()),
                TagTestResourcesHelper.defaultTags)).isTrue();
        assertThat(response.getErrorCode()).isEqualTo(HandlerErrorCode.InvalidRequest);

        verify(mockProxyClient.client(), times(0)).createAccount(any(CreateAccountRequest.class));
        verify(mockProxyClient.client(), times(0)).describeCreateAccountStatus(any(DescribeCreateAccountStatusRequest.class));
        verify(mockProxyClient.client(), times(0)).moveAccount(any(MoveAccountRequest.class));
    }

    @Test
    public void handleRequest_SuccessWhenMoveAccountThrowsDuplicateAccountException() {
        final ResourceModel model = generateCreateResourceModel();

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
                                                                  .desiredResourceState(model)
                                                                  .desiredResourceTags(defaultStackTags)
                                                                  .build();

        final ListAccountsResponse listAccountsResponse = ListAccountsResponse.builder()
                .accounts(Collections.emptyList())
                .build();
        when(mockProxyClient.client().listAccounts(any(ListAccountsRequest.class))).thenReturn(listAccountsResponse);

        final CreateAccountResponse createAccountResponse = getCreateAccountResponse();
        final DescribeCreateAccountStatusResponse describeCreateAccountStatusResponse = getDescribeCreateAccountStatusResponse(SUCCEEDED);
        final ListParentsResponse listParentsResponse = getListParentsResponseBeforeMoveAccount();


        when(mockProxyClient.client().createAccount(any(CreateAccountRequest.class))).thenReturn(createAccountResponse);
        when(mockProxyClient.client().describeCreateAccountStatus(any(DescribeCreateAccountStatusRequest.class))).thenReturn(describeCreateAccountStatusResponse);
        lenient().when(mockProxyClient.client().listParents(any(ListParentsRequest.class))).thenReturn(listParentsResponse);
        when(mockProxyClient.client().moveAccount(any(MoveAccountRequest.class))).thenThrow(DuplicateAccountException.class);

        final ProgressEvent<ResourceModel, CallbackContext> response = createHandler.handleRequest(mockAwsClientProxy, request, new CallbackContext(), mockProxyClient, logger);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.SUCCESS);
        assertThat(response.getCallbackDelaySeconds()).isEqualTo(0);
        assertThat(response.getResourceModel()).isNotNull();
        assertThat(response.getErrorCode()).isNull();
        assertThat(response.getResourceModel().getAccountId()).isEqualTo(TEST_ACCOUNT_ID);
        assertThat(response.getResourceModel().getEmail()).isEqualTo(TEST_ACCOUNT_EMAIL);
        assertThat(response.getResourceModel().getAccountName()).isEqualTo(TEST_ACCOUNT_NAME);
        assertThat(response.getResourceModel().getParentIds()).isEqualTo(TEST_PARENT_IDS);
        assertThat(TagTestResourcesHelper.tagsEqual(
                TagsHelper.convertAccountTagToOrganizationTag(response.getResourceModel().getTags()),
                TagTestResourcesHelper.defaultTags)).isTrue();

        verify(mockProxyClient.client()).listAccounts(any(ListAccountsRequest.class));
        verify(mockProxyClient.client()).createAccount(any(CreateAccountRequest.class));
        verify(mockProxyClient.client(), atLeast(1)).describeCreateAccountStatus(any(DescribeCreateAccountStatusRequest.class));
        verify(mockProxyClient.client(), times(1)).moveAccount(any(MoveAccountRequest.class));
    }

    @Test
    public void handleRequest_SkipCreateAccountAndDescribeCreateAccountStatusIfAccountAlreadyCreated() {
        final ResourceModel model = generateCreateResourceModel();

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
                                                                  .desiredResourceState(model)
                                                                  .desiredResourceTags(defaultStackTags)
                                                                  .build();

        final ListAccountsResponse emptyListAccountsResponse = ListAccountsResponse.builder()
                .accounts(Collections.emptyList())
                .build();

        final ListAccountsResponse existingAccountResponse = ListAccountsResponse.builder()
                .accounts(Collections.singletonList(Account.builder()
                        .id(TEST_ACCOUNT_ID)
                        .email(TEST_ACCOUNT_EMAIL)
                        .name(TEST_ACCOUNT_NAME)
                        .build()))
                .build();
        when(mockProxyClient.client().listAccounts(any(ListAccountsRequest.class)))
                .thenReturn(emptyListAccountsResponse)
                .thenReturn(existingAccountResponse);

        final CreateAccountResponse createAccountResponse = getCreateAccountResponse();
        final DescribeCreateAccountStatusResponse describeCreateAccountStatusResponse = getDescribeCreateAccountStatusResponse(SUCCEEDED);
        final ListParentsResponse listParentsResponse = getListParentsResponseBeforeMoveAccount();

        when(mockProxyClient.client().createAccount(any(CreateAccountRequest.class))).thenReturn(createAccountResponse);
        when(mockProxyClient.client().describeCreateAccountStatus(any(DescribeCreateAccountStatusRequest.class))).thenReturn(describeCreateAccountStatusResponse);
        when(mockProxyClient.client().listParents(any(ListParentsRequest.class))).thenReturn(listParentsResponse);
        when(mockProxyClient.client().moveAccount(any(MoveAccountRequest.class))).thenThrow(ConcurrentModificationException.class);

        CallbackContext context = new CallbackContext();
        ProgressEvent<ResourceModel, CallbackContext> response = createHandler.handleRequest(mockAwsClientProxy, request, context, mockProxyClient, logger);
        assertThat(response).isNotNull();
        assertThat(response.getCallbackContext().isAccountCreated()).isEqualTo(true);
        assertThat(response.getStatus()).isEqualTo(OperationStatus.IN_PROGRESS);
        assertThat(response.getCallbackDelaySeconds()).isGreaterThan(0);
        assertThat(response.getResourceModel()).isNotNull();
        assertThat(response.getResourceModel().getAccountId()).isEqualTo(TEST_ACCOUNT_ID);
        assertThat(response.getResourceModel().getEmail()).isEqualTo(TEST_ACCOUNT_EMAIL);
        assertThat(response.getResourceModel().getAccountName()).isEqualTo(TEST_ACCOUNT_NAME);
        assertThat(response.getErrorCode()).isEqualTo(null);

        response = createHandler.handleRequest(mockAwsClientProxy, request, context, mockProxyClient, logger);
        assertThat(response.getCallbackContext().isAccountCreated()).isEqualTo(true);

        verify(mockProxyClient.client(), times(1)).listAccounts(any(ListAccountsRequest.class));
        verify(mockProxyClient.client(), times(1)).createAccount(any(CreateAccountRequest.class));
        verify(mockProxyClient.client(), atMost(3)).describeCreateAccountStatus(any(DescribeCreateAccountStatusRequest.class));
        verify(mockProxyClient.client(), times(2)).moveAccount(any(MoveAccountRequest.class));
    }

    @Test
    public void handleRequest_FailedWithCfnServiceLimitExceededException() {
        final ResourceModel model = generateCreateResourceModel();

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
                                                                  .desiredResourceState(model)
                                                                  .desiredResourceTags(defaultStackTags)
                                                                  .build();

        final ListAccountsResponse listAccountsResponse = ListAccountsResponse.builder()
                .accounts(Collections.emptyList())
                .build();
        when(mockProxyClient.client().listAccounts(any(ListAccountsRequest.class))).thenReturn(listAccountsResponse);

        final CreateAccountResponse createAccountResponse = getCreateAccountResponse();
        final DescribeCreateAccountStatusResponse describeCreateAccountStatusResponse = DescribeCreateAccountStatusResponse.builder()
                                                                                            .createAccountStatus(CreateAccountStatusFailedWithAccountLimitExceed)
                                                                                            .build();

        when(mockProxyClient.client().createAccount(any(CreateAccountRequest.class))).thenReturn(createAccountResponse);
        when(mockProxyClient.client().describeCreateAccountStatus(any(DescribeCreateAccountStatusRequest.class))).thenReturn(describeCreateAccountStatusResponse);
        final ProgressEvent<ResourceModel, CallbackContext> response = createHandler.handleRequest(mockAwsClientProxy, request, new CallbackContext(), mockProxyClient, logger);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.FAILED);
        assertThat(response.getCallbackDelaySeconds()).isEqualTo(0);
        assertThat(response.getResourceModel()).isNotNull();
        assertThat(response.getErrorCode()).isEqualTo(HandlerErrorCode.ServiceLimitExceeded);
        assertThat(response.getResourceModel().getAccountId()).isNull();
        assertThat(response.getResourceModel().getEmail()).isEqualTo(TEST_ACCOUNT_EMAIL);
        assertThat(response.getResourceModel().getAccountName()).isEqualTo(TEST_ACCOUNT_NAME);
        assertThat(response.getResourceModel().getParentIds()).isEqualTo(TEST_PARENT_IDS);
        assertThat(TagTestResourcesHelper.tagsEqual(
                TagsHelper.convertAccountTagToOrganizationTag(response.getResourceModel().getTags()),
                TagTestResourcesHelper.defaultTags)).isTrue();

        verify(mockProxyClient.client()).listAccounts(any(ListAccountsRequest.class));
        verify(mockProxyClient.client()).createAccount(any(CreateAccountRequest.class));
        verify(mockProxyClient.client(), atLeast(1)).describeCreateAccountStatus(any(DescribeCreateAccountStatusRequest.class));
        verify(mockProxyClient.client(), times(0)).moveAccount(any(MoveAccountRequest.class));
    }

    @Test
    public void handleRequest_FailedWithCfnInvalidRequestException() {
        final ResourceModel model = generateCreateResourceModel();

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
                                                                  .desiredResourceState(model)
                                                                  .desiredResourceTags(defaultStackTags)
                                                                  .build();

        final ListAccountsResponse listAccountsResponse = ListAccountsResponse.builder()
                .accounts(Collections.emptyList())
                .build();
        when(mockProxyClient.client().listAccounts(any(ListAccountsRequest.class))).thenReturn(listAccountsResponse);

        final CreateAccountResponse createAccountResponse = getCreateAccountResponse();
        final DescribeCreateAccountStatusResponse describeCreateAccountStatusResponse = DescribeCreateAccountStatusResponse.builder()
                                                                                            .createAccountStatus(CreateAccountStatusFailedWithInvalidInput)
                                                                                            .build();

        when(mockProxyClient.client().createAccount(any(CreateAccountRequest.class))).thenReturn(createAccountResponse);
        when(mockProxyClient.client().describeCreateAccountStatus(any(DescribeCreateAccountStatusRequest.class))).thenReturn(describeCreateAccountStatusResponse);
        final ProgressEvent<ResourceModel, CallbackContext> response = createHandler.handleRequest(mockAwsClientProxy, request, new CallbackContext(), mockProxyClient, logger);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.FAILED);
        assertThat(response.getCallbackDelaySeconds()).isEqualTo(0);
        assertThat(response.getResourceModel()).isNotNull();
        assertThat(response.getErrorCode()).isEqualTo(HandlerErrorCode.InvalidRequest);
        assertThat(response.getResourceModel().getAccountId()).isNull();
        assertThat(response.getResourceModel().getEmail()).isEqualTo(TEST_ACCOUNT_EMAIL);
        assertThat(response.getResourceModel().getAccountName()).isEqualTo(TEST_ACCOUNT_NAME);
        assertThat(response.getResourceModel().getParentIds()).isEqualTo(TEST_PARENT_IDS);
        assertThat(TagTestResourcesHelper.tagsEqual(
                TagsHelper.convertAccountTagToOrganizationTag(response.getResourceModel().getTags()),
                TagTestResourcesHelper.defaultTags)).isTrue();

        verify(mockProxyClient.client()).listAccounts(any(ListAccountsRequest.class));
        verify(mockProxyClient.client()).createAccount(any(CreateAccountRequest.class));
        verify(mockProxyClient.client(), atLeast(1)).describeCreateAccountStatus(any(DescribeCreateAccountStatusRequest.class));
        verify(mockProxyClient.client(), times(0)).moveAccount(any(MoveAccountRequest.class));
    }

    @Test
    public void handleRequest_FailedWithCfnGeneralServiceException() {
        final ResourceModel model = generateCreateResourceModel();

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
                                                                  .desiredResourceState(model)
                                                                  .desiredResourceTags(defaultStackTags)
                                                                  .build();

        final ListAccountsResponse listAccountsResponse = ListAccountsResponse.builder()
                .accounts(Collections.emptyList())
                .build();
        when(mockProxyClient.client().listAccounts(any(ListAccountsRequest.class))).thenReturn(listAccountsResponse);

        final CreateAccountResponse createAccountResponse = getCreateAccountResponse();
        final DescribeCreateAccountStatusResponse describeCreateAccountStatusResponse = DescribeCreateAccountStatusResponse.builder()
                                                                                            .createAccountStatus(CreateAccountStatusFailedWithUnknownFailure)
                                                                                            .build();

        when(mockProxyClient.client().createAccount(any(CreateAccountRequest.class))).thenReturn(createAccountResponse);
        when(mockProxyClient.client().describeCreateAccountStatus(any(DescribeCreateAccountStatusRequest.class))).thenReturn(describeCreateAccountStatusResponse);
        final ProgressEvent<ResourceModel, CallbackContext> response = createHandler.handleRequest(mockAwsClientProxy, request, new CallbackContext(), mockProxyClient, logger);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.FAILED);
        assertThat(response.getCallbackDelaySeconds()).isEqualTo(0);
        assertThat(response.getResourceModel()).isNotNull();
        assertThat(response.getErrorCode()).isEqualTo(HandlerErrorCode.GeneralServiceException);
        assertThat(response.getResourceModel().getAccountId()).isNull();
        assertThat(response.getResourceModel().getEmail()).isEqualTo(TEST_ACCOUNT_EMAIL);
        assertThat(response.getResourceModel().getAccountName()).isEqualTo(TEST_ACCOUNT_NAME);
        assertThat(response.getResourceModel().getParentIds()).isEqualTo(TEST_PARENT_IDS);
        assertThat(TagTestResourcesHelper.tagsEqual(
                TagsHelper.convertAccountTagToOrganizationTag(response.getResourceModel().getTags()),
                TagTestResourcesHelper.defaultTags)).isTrue();

        verify(mockProxyClient.client()).listAccounts(any(ListAccountsRequest.class));
        verify(mockProxyClient.client()).createAccount(any(CreateAccountRequest.class));
        verify(mockProxyClient.client(), atLeast(1)).describeCreateAccountStatus(any(DescribeCreateAccountStatusRequest.class));
        verify(mockProxyClient.client(), times(0)).moveAccount(any(MoveAccountRequest.class));
    }

    @Test
    public void handleRequest_FailedWithCfnServiceInternalError() {
        final ResourceModel model = generateCreateResourceModel();

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
                .desiredResourceState(model)
                .desiredResourceTags(defaultStackTags)
                .build();

        final ListAccountsResponse listAccountsResponse = ListAccountsResponse.builder()
                .accounts(Collections.emptyList())
                .build();
        when(mockProxyClient.client().listAccounts(any(ListAccountsRequest.class))).thenReturn(listAccountsResponse);

        final CreateAccountResponse createAccountResponse = getCreateAccountResponse();
        final DescribeCreateAccountStatusResponse describeCreateAccountStatusResponse = DescribeCreateAccountStatusResponse.builder()
                .createAccountStatus(CreateAccountStatusFailedWithInternalFailure)
                .build();

        when(mockProxyClient.client().createAccount(any(CreateAccountRequest.class))).thenReturn(createAccountResponse);
        when(mockProxyClient.client().describeCreateAccountStatus(any(DescribeCreateAccountStatusRequest.class))).thenReturn(describeCreateAccountStatusResponse);
        final ProgressEvent<ResourceModel, CallbackContext> response = createHandler.handleRequest(mockAwsClientProxy, request, new CallbackContext(), mockProxyClient, logger);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.FAILED);
        assertThat(response.getCallbackDelaySeconds()).isEqualTo(0);
        assertThat(response.getResourceModel()).isNotNull();
        assertThat(response.getErrorCode()).isEqualTo(HandlerErrorCode.ServiceInternalError);
        assertThat(response.getResourceModel().getAccountId()).isNull();
        assertThat(response.getResourceModel().getEmail()).isEqualTo(TEST_ACCOUNT_EMAIL);
        assertThat(response.getResourceModel().getAccountName()).isEqualTo(TEST_ACCOUNT_NAME);
        assertThat(response.getResourceModel().getParentIds()).isEqualTo(TEST_PARENT_IDS);
        assertThat(TagTestResourcesHelper.tagsEqual(
                TagsHelper.convertAccountTagToOrganizationTag(response.getResourceModel().getTags()),
                TagTestResourcesHelper.defaultTags)).isTrue();

        verify(mockProxyClient.client()).listAccounts(any(ListAccountsRequest.class));
        verify(mockProxyClient.client()).createAccount(any(CreateAccountRequest.class));
        verify(mockProxyClient.client(), atLeast(1)).describeCreateAccountStatus(any(DescribeCreateAccountStatusRequest.class));
        verify(mockProxyClient.client(), times(0)).moveAccount(any(MoveAccountRequest.class));
    }

    @Test
    public void handleRequest_shouldFailWhenNoAccountNameAndEmailAddress() {
        final ResourceModel model = generateCreateResourceModelNoAccountNameAndEmail();

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
                                                                  .desiredResourceState(model)
                                                                  .desiredResourceTags(defaultStackTags)
                                                                  .build();

        final ProgressEvent<ResourceModel, CallbackContext> response = createHandler.handleRequest(mockAwsClientProxy, request, new CallbackContext(), mockProxyClient, logger);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.FAILED);
        assertThat(response.getCallbackDelaySeconds()).isEqualTo(0);
        assertThat(response.getResourceModel()).isNotNull();
        assertThat(response.getErrorCode()).isEqualTo(HandlerErrorCode.InvalidRequest);
        assertThat(response.getResourceModel().getAccountId()).isNull();
        assertThat(response.getResourceModel().getEmail()).isNull();
        assertThat(response.getResourceModel().getAccountName()).isNull();
        assertThat(response.getResourceModel().getParentIds()).isEqualTo(TEST_PARENT_IDS);
        assertThat(TagTestResourcesHelper.tagsEqual(
                TagsHelper.convertAccountTagToOrganizationTag(response.getResourceModel().getTags()),
                TagTestResourcesHelper.defaultTags)).isTrue();

        verify(mockProxyClient.client(), times(0)).createAccount(any(CreateAccountRequest.class));
        verify(mockProxyClient.client(), atLeast(0)).describeCreateAccountStatus(any(DescribeCreateAccountStatusRequest.class));
        verify(mockProxyClient.client(), times(0)).moveAccount(any(MoveAccountRequest.class));
    }

    @Test
    public void handleRequest_shouldSkipMoveAccountWhenParentIdNotProvided() {
        final ResourceModel model = generateCreateResourceModelWithNoParentId();

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
                                                                  .desiredResourceState(model)
                                                                  .desiredResourceTags(defaultStackTags)
                                                                  .build();

        final ListAccountsResponse listAccountsResponse = ListAccountsResponse.builder()
                .accounts(Collections.emptyList())
                .build();
        when(mockProxyClient.client().listAccounts(any(ListAccountsRequest.class))).thenReturn(listAccountsResponse);

        final CreateAccountResponse createAccountResponse = getCreateAccountResponse();
        final DescribeCreateAccountStatusResponse describeCreateAccountStatusResponse = getDescribeCreateAccountStatusResponse(SUCCEEDED);


        when(mockProxyClient.client().createAccount(any(CreateAccountRequest.class))).thenReturn(createAccountResponse);
        when(mockProxyClient.client().describeCreateAccountStatus(any(DescribeCreateAccountStatusRequest.class))).thenReturn(describeCreateAccountStatusResponse);

        final ProgressEvent<ResourceModel, CallbackContext> response = createHandler.handleRequest(mockAwsClientProxy, request, new CallbackContext(), mockProxyClient, logger);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.SUCCESS);
        assertThat(response.getCallbackDelaySeconds()).isEqualTo(0);
        assertThat(response.getResourceModel()).isNotNull();
        assertThat(response.getResourceModel().getAccountId()).isEqualTo(TEST_ACCOUNT_ID);
        assertThat(response.getResourceModel().getEmail()).isEqualTo(TEST_ACCOUNT_EMAIL);
        assertThat(response.getResourceModel().getAccountName()).isEqualTo(TEST_ACCOUNT_NAME);
        assertThat(TagTestResourcesHelper.tagsEqual(
                TagsHelper.convertAccountTagToOrganizationTag(response.getResourceModel().getTags()),
                TagTestResourcesHelper.defaultTags)).isTrue();
        assertThat(response.getResourceModels()).isNull();
        assertThat(response.getMessage()).isNull();
        assertThat(response.getErrorCode()).isNull();

        verify(mockProxyClient.client()).listAccounts(any(ListAccountsRequest.class));
        verify(mockProxyClient.client()).createAccount(any(CreateAccountRequest.class));
        verify(mockProxyClient.client(), times(0)).moveAccount(any(MoveAccountRequest.class));
        verify(mockProxyClient.client(), atLeast(1)).describeCreateAccountStatus(any(DescribeCreateAccountStatusRequest.class));
    }

    @Test
    public void handleRequest_shouldFailWhenMoveAccountThrowsDestinationParentNotFoundException() {
        final ResourceModel model = generateCreateResourceModel();

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
                                                                  .desiredResourceState(model)
                                                                  .desiredResourceTags(defaultStackTags)
                                                                  .build();

        final ListAccountsResponse listAccountsResponse = ListAccountsResponse.builder()
                .accounts(Collections.emptyList())
                .build();
        when(mockProxyClient.client().listAccounts(any(ListAccountsRequest.class))).thenReturn(listAccountsResponse);

        final CreateAccountResponse createAccountResponse = getCreateAccountResponse();
        final DescribeCreateAccountStatusResponse describeCreateAccountStatusResponse = getDescribeCreateAccountStatusResponse(SUCCEEDED);
        final ListParentsResponse listParentsResponse = getListParentsResponseBeforeMoveAccount();

        when(mockProxyClient.client().createAccount(any(CreateAccountRequest.class))).thenReturn(createAccountResponse);
        when(mockProxyClient.client().describeCreateAccountStatus(any(DescribeCreateAccountStatusRequest.class))).thenReturn(describeCreateAccountStatusResponse);
        lenient().when(mockProxyClient.client().listParents(any(ListParentsRequest.class))).thenReturn(listParentsResponse);
        when(mockProxyClient.client().moveAccount(any(MoveAccountRequest.class))).thenThrow(DestinationParentNotFoundException.class);

        final ProgressEvent<ResourceModel, CallbackContext> response = createHandler.handleRequest(mockAwsClientProxy, request, new CallbackContext(), mockProxyClient, logger);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.FAILED);
        assertThat(response.getCallbackDelaySeconds()).isEqualTo(0);
        assertThat(response.getResourceModel()).isNotNull();
        assertThat(response.getErrorCode()).isEqualTo(HandlerErrorCode.InvalidRequest);
        assertThat(response.getResourceModel().getAccountId()).isEqualTo(TEST_ACCOUNT_ID);
        assertThat(response.getResourceModel().getEmail()).isEqualTo(TEST_ACCOUNT_EMAIL);
        assertThat(response.getResourceModel().getAccountName()).isEqualTo(TEST_ACCOUNT_NAME);
        assertThat(TagTestResourcesHelper.tagsEqual(
                TagsHelper.convertAccountTagToOrganizationTag(response.getResourceModel().getTags()),
                TagTestResourcesHelper.defaultTags)).isTrue();

        verify(mockProxyClient.client()).listAccounts(any(ListAccountsRequest.class));
        verify(mockProxyClient.client()).createAccount(any(CreateAccountRequest.class));
        verify(mockProxyClient.client(), atLeast(1)).describeCreateAccountStatus(any(DescribeCreateAccountStatusRequest.class));
        verify(mockProxyClient.client(), times(1)).moveAccount(any(MoveAccountRequest.class));
    }

    @Test
    public void handleRequest_shouldFailWhenDescribeCreateAccountStatusThrowsAccessDeniedException() {
        final ResourceModel model = generateCreateResourceModel();

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
                                                                  .desiredResourceState(model)
                                                                  .desiredResourceTags(defaultStackTags)
                                                                  .build();

        final ListAccountsResponse listAccountsResponse = ListAccountsResponse.builder()
                .accounts(Collections.emptyList())
                .build();
        when(mockProxyClient.client().listAccounts(any(ListAccountsRequest.class))).thenReturn(listAccountsResponse);

        final CreateAccountResponse createAccountResponse = getCreateAccountResponse();

        when(mockProxyClient.client().createAccount(any(CreateAccountRequest.class))).thenReturn(createAccountResponse);
        when(mockProxyClient.client().describeCreateAccountStatus(any(DescribeCreateAccountStatusRequest.class))).thenThrow(AccessDeniedException.class);

        final ProgressEvent<ResourceModel, CallbackContext> response = createHandler.handleRequest(mockAwsClientProxy, request, new CallbackContext(), mockProxyClient, logger);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.FAILED);
        assertThat(response.getCallbackDelaySeconds()).isEqualTo(0);
        assertThat(response.getResourceModel()).isNotNull();
        assertThat(response.getErrorCode()).isEqualTo(HandlerErrorCode.AccessDenied);
        assertThat(response.getResourceModel().getAccountId()).isNull();
        assertThat(response.getResourceModel().getEmail()).isEqualTo(TEST_ACCOUNT_EMAIL);
        assertThat(response.getResourceModel().getAccountName()).isEqualTo(TEST_ACCOUNT_NAME);
        assertThat(TagTestResourcesHelper.tagsEqual(
                TagsHelper.convertAccountTagToOrganizationTag(response.getResourceModel().getTags()),
                TagTestResourcesHelper.defaultTags)).isTrue();

        verify(mockProxyClient.client()).listAccounts(any(ListAccountsRequest.class));
        verify(mockProxyClient.client()).createAccount(any(CreateAccountRequest.class));
        verify(mockProxyClient.client(), atLeast(1)).describeCreateAccountStatus(any(DescribeCreateAccountStatusRequest.class));
        verify(mockProxyClient.client(), times(0)).moveAccount(any(MoveAccountRequest.class));
    }

    @Test
    public void handleRequest_checkIfAccountExists_AccountAlreadyExists() {
        final ResourceModel model = ResourceModel.builder()
                .email(TEST_ACCOUNT_EMAIL)
                .accountName(TEST_ACCOUNT_NAME)
                .build();

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
                .desiredResourceState(model)
                .desiredResourceTags(defaultStackTags)
                .build();

        final ListAccountsResponse listAccountsResponse = ListAccountsResponse.builder()
                .accounts(Collections.singletonList(Account.builder()
                        .id(TEST_ACCOUNT_ID)
                        .email(TEST_ACCOUNT_EMAIL)
                        .name(TEST_ACCOUNT_NAME)
                        .build()))
                .build();
        when(mockProxyClient.client().listAccounts(any(ListAccountsRequest.class))).thenReturn(listAccountsResponse);

        final ProgressEvent<ResourceModel, CallbackContext> response = createHandler.handleRequest(mockAwsClientProxy, request, new CallbackContext(), mockProxyClient, logger);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.FAILED);
        assertThat(response.getCallbackDelaySeconds()).isEqualTo(0);
        assertThat(response.getResourceModel()).isNotNull();
        assertThat(response.getErrorCode()).isEqualTo(HandlerErrorCode.AlreadyExists);
        assertThat(response.getResourceModel().getAccountId()).isEqualTo(TEST_ACCOUNT_ID);
        assertThat(response.getResourceModel().getEmail()).isEqualTo(TEST_ACCOUNT_EMAIL);
        assertThat(response.getResourceModel().getAccountName()).isEqualTo(TEST_ACCOUNT_NAME);
        assertThat(response.getMessage()).contains("Account with email [" + TEST_ACCOUNT_EMAIL + "] already exists");

        verify(mockProxyClient.client()).listAccounts(any(ListAccountsRequest.class));
    }

    @Test
    public void handleRequest_checkIfAccountExists_AccountDoesNotExist() {
        final ResourceModel model = ResourceModel.builder()
                .email(TEST_ACCOUNT_EMAIL)
                .accountName(TEST_ACCOUNT_NAME)
                .build();

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
                .desiredResourceState(model)
                .desiredResourceTags(defaultStackTags)
                .build();

        final ListAccountsResponse listAccountsResponse = ListAccountsResponse.builder()
                .accounts(Collections.emptyList())
                .build();
        when(mockProxyClient.client().listAccounts(any(ListAccountsRequest.class))).thenReturn(listAccountsResponse);

        final CreateAccountResponse createAccountResponse = getCreateAccountResponse();
        final DescribeCreateAccountStatusResponse describeCreateAccountStatusResponse = getDescribeCreateAccountStatusResponse(SUCCEEDED);
        when(mockProxyClient.client().createAccount(any(CreateAccountRequest.class))).thenReturn(createAccountResponse);
        when(mockProxyClient.client().describeCreateAccountStatus(any(DescribeCreateAccountStatusRequest.class))).thenReturn(describeCreateAccountStatusResponse);

        final ProgressEvent<ResourceModel, CallbackContext> response = createHandler.handleRequest(mockAwsClientProxy, request, new CallbackContext(), mockProxyClient, logger);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.SUCCESS);
        assertThat(response.getCallbackDelaySeconds()).isEqualTo(0);
        assertThat(response.getResourceModel()).isNotNull();
        assertThat(response.getResourceModel().getAccountId()).isEqualTo(TEST_ACCOUNT_ID);
        assertThat(response.getResourceModel().getEmail()).isEqualTo(TEST_ACCOUNT_EMAIL);
        assertThat(response.getResourceModel().getAccountName()).isEqualTo(TEST_ACCOUNT_NAME);

        verify(mockProxyClient.client()).listAccounts(any(ListAccountsRequest.class));
        verify(mockProxyClient.client()).createAccount(any(CreateAccountRequest.class));
        verify(mockProxyClient.client(), atLeast(1)).describeCreateAccountStatus(any(DescribeCreateAccountStatusRequest.class));
    }

    @Test
    public void handleRequest_checkIfAccountExists_Pagination_AccountAlreadyExists() {
        final ResourceModel model = ResourceModel.builder()
                .email(TEST_ACCOUNT_EMAIL)
                .accountName(TEST_ACCOUNT_NAME)
                .build();

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
                .desiredResourceState(model)
                .desiredResourceTags(defaultStackTags)
                .build();

        final ListAccountsResponse firstListAccountsResponse = ListAccountsResponse.builder()
                .accounts(Collections.singletonList(Account.builder()
                        .id("Account 1")
                        .email("paginationtestaccount")
                        .name("Account1")
                        .build()))
                .nextToken("nextPageToken")
                .build();

        final ListAccountsResponse secondListAccountsResponse = ListAccountsResponse.builder()
                .accounts(Collections.singletonList(Account.builder()
                        .id(TEST_ACCOUNT_ID)
                        .email(TEST_ACCOUNT_EMAIL)
                        .name(TEST_ACCOUNT_NAME)
                        .build()))
                .build();

        when(mockProxyClient.client().listAccounts(any(ListAccountsRequest.class)))
                .thenReturn(firstListAccountsResponse)
                .thenReturn(secondListAccountsResponse);

        final ProgressEvent<ResourceModel, CallbackContext> response = createHandler.handleRequest(mockAwsClientProxy, request, new CallbackContext(), mockProxyClient, logger);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.FAILED);
        assertThat(response.getCallbackDelaySeconds()).isEqualTo(0);
        assertThat(response.getResourceModel()).isNotNull();
        assertThat(response.getErrorCode()).isEqualTo(HandlerErrorCode.AlreadyExists);
        assertThat(response.getResourceModel().getAccountId()).isEqualTo(TEST_ACCOUNT_ID);
        assertThat(response.getResourceModel().getEmail()).isEqualTo(TEST_ACCOUNT_EMAIL);
        assertThat(response.getResourceModel().getAccountName()).isEqualTo(TEST_ACCOUNT_NAME);
        assertThat(response.getMessage()).contains("Account with email [" + TEST_ACCOUNT_EMAIL + "] already exists");

        verify(mockProxyClient.client(), times(2)).listAccounts(any(ListAccountsRequest.class));
    }

    protected ResourceModel generateCreateResourceModel() {
        ResourceModel model = ResourceModel.builder()
                                  .email(TEST_ACCOUNT_EMAIL)
                                  .accountName(TEST_ACCOUNT_NAME)
                                  .parentIds(TEST_PARENT_IDS)
                                  .tags(TagTestResourcesHelper.translateOrganizationTagsToAccountTags(TagTestResourcesHelper.defaultTags))
                                  .build();
        return model;
    }

    protected ResourceModel generateCreateResourceModelWithNoParentId() {
        ResourceModel model = ResourceModel.builder()
                                  .email(TEST_ACCOUNT_EMAIL)
                                  .accountName(TEST_ACCOUNT_NAME)
                                  .tags(TagTestResourcesHelper.translateOrganizationTagsToAccountTags(TagTestResourcesHelper.defaultTags))
                                  .build();
        return model;
    }

    protected ResourceModel generateCreateResourceModelNoAccountNameAndEmail() {
        ResourceModel model = ResourceModel.builder()
                                  .parentIds(TEST_PARENT_IDS)
                                  .tags(TagTestResourcesHelper.translateOrganizationTagsToAccountTags(TagTestResourcesHelper.defaultTags))
                                  .build();
        return model;
    }

    protected ResourceModel generateCreateResourceModelWithMultipleParentIds() {
        ResourceModel model = ResourceModel.builder()
                                  .email(TEST_ACCOUNT_EMAIL)
                                  .accountName(TEST_ACCOUNT_NAME)
                                  .parentIds(TEST_MULTIPLE_PARENT_IDS)
                                  .tags(TagTestResourcesHelper.translateOrganizationTagsToAccountTags(TagTestResourcesHelper.defaultTags))
                                  .build();
        return model;
    }

    protected CreateAccountResponse getCreateAccountResponse() {
        return CreateAccountResponse.builder()
                   .createAccountStatus(CreateAccountStatus
                                            .builder()
                                            .id(CREATE_ACCOUNT_STATUS_ID)
                                            .build())
                   .build();

    }

    protected DescribeCreateAccountStatusResponse getDescribeCreateAccountStatusResponse(String status) {
        if (status.equals(FAILED)) {
            return DescribeCreateAccountStatusResponse.builder()
                       .createAccountStatus(CreateAccountStatusFailedWithAlreadyExist)
                       .build();
        } else if (status.equals(IN_PROGRESS)) {
            return DescribeCreateAccountStatusResponse.builder()
                       .createAccountStatus(CreateAccountStatusInProgress)
                       .build();
        }
        return DescribeCreateAccountStatusResponse.builder()
                   .createAccountStatus(CreateAccountStatusSucceeded)
                   .build();
    }


    protected MoveAccountResponse getMoveAccountResponse() {
        return MoveAccountResponse.builder().build();
    }

    protected ListParentsResponse getListParentsResponseBeforeMoveAccount() {
        return ListParentsResponse.builder()
                   .parents(Parent.builder()
                                .id(TEST_SOURCE_PARENT_ID)
                                .build()
                   ).build();
    }

    protected ListParentsResponse getListParentsResponseAfterMoveAccount() {
        return ListParentsResponse.builder()
                   .parents(Parent.builder()
                                .id(TEST_DESTINATION_PARENT_ID)
                                .build()
                   ).build();
    }
}
