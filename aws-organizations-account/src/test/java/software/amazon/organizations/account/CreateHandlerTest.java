package software.amazon.organizations.account;

import com.google.common.collect.ImmutableSet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.organizations.OrganizationsClient;
import software.amazon.awssdk.services.organizations.model.AccessDeniedException;
import software.amazon.awssdk.services.organizations.model.Account;
import software.amazon.awssdk.services.organizations.model.ConcurrentModificationException;
import software.amazon.awssdk.services.organizations.model.CreateAccountRequest;
import software.amazon.awssdk.services.organizations.model.CreateAccountResponse;
import software.amazon.awssdk.services.organizations.model.CreateAccountStatus;
import software.amazon.awssdk.services.organizations.model.DescribeAccountRequest;
import software.amazon.awssdk.services.organizations.model.DescribeAccountResponse;
import software.amazon.awssdk.services.organizations.model.DescribeCreateAccountStatusRequest;
import software.amazon.awssdk.services.organizations.model.DescribeCreateAccountStatusResponse;
import software.amazon.awssdk.services.organizations.model.DestinationParentNotFoundException;
import software.amazon.awssdk.services.organizations.model.DuplicateAccountException;
import software.amazon.awssdk.services.organizations.model.ListParentsRequest;
import software.amazon.awssdk.services.organizations.model.ListParentsResponse;
import software.amazon.awssdk.services.organizations.model.ListTagsForResourceRequest;
import software.amazon.awssdk.services.organizations.model.ListTagsForResourceResponse;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.atMost;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
        mockAwsClientProxy = new AmazonWebServicesClientProxy(logger, MOCK_CREDENTIALS, () -> Duration.ofSeconds(600).toMillis());
        mockOrgsClient = mock(OrganizationsClient.class);
        mockProxyClient = MOCK_PROXY(mockAwsClientProxy, mockOrgsClient);
    }

    @Test
    public void handleRequest_SimpleSuccess() {
        final ResourceModel model = generateCreateResourceModel();

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
                                                                  .desiredResourceState(model)
                                                                  .build();

        final CreateAccountResponse createAccountResponse = getCreateAccountResponse();
        final DescribeCreateAccountStatusResponse describeCreateAccountStatusResponse = getDescribeCreateAccountStatusResponse(SUCCEEDED);
        final MoveAccountResponse moveAccountResponse = getMoveAccountResponse();
        final ListParentsResponse listParentsResponseBeforeMoveAccountResponse = getListParentsResponseBeforeMoveAccount();

        when(mockProxyClient.client().createAccount(any(CreateAccountRequest.class))).thenReturn(createAccountResponse);
        when(mockProxyClient.client().describeCreateAccountStatus(any(DescribeCreateAccountStatusRequest.class))).thenReturn(describeCreateAccountStatusResponse);
        lenient().when(mockProxyClient.client().listParents(any(ListParentsRequest.class))).thenReturn(listParentsResponseBeforeMoveAccountResponse);
        when(mockProxyClient.client().moveAccount(any(MoveAccountRequest.class))).thenReturn(moveAccountResponse);

        // read
        final DescribeAccountResponse describeAccountResponse = DescribeAccountResponse.builder().account(Account.builder()
                                                                                                              .arn(TEST_ACCOUNT_ARN)
                                                                                                              .email(TEST_ACCOUNT_EMAIL)
                                                                                                              .id(TEST_ACCOUNT_ID)
                                                                                                              .name(TEST_ACCOUNT_NAME)
                                                                                                              .build()).build();

        final ListParentsResponse listParentsResponseAfterMoveAccountResponse = getListParentsResponseAfterMoveAccount();


        final ListTagsForResourceResponse listTagsForResourceResponse = TagTestResourcesHelper.buildDefaultTagsResponse();
        when(mockProxyClient.client().describeAccount(any(DescribeAccountRequest.class))).thenReturn(describeAccountResponse);
        when(mockProxyClient.client().listParents(any(ListParentsRequest.class))).thenReturn(listParentsResponseAfterMoveAccountResponse);
        when(mockProxyClient.client().listTagsForResource(any(ListTagsForResourceRequest.class))).thenReturn(listTagsForResourceResponse);

        final ProgressEvent<ResourceModel, CallbackContext> response = createHandler.handleRequest(mockAwsClientProxy, request, new CallbackContext(), mockProxyClient, logger);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.SUCCESS);
        assertThat(response.getCallbackDelaySeconds()).isEqualTo(0);
        assertThat(response.getResourceModel()).isNotNull();
        assertThat(response.getResourceModel().getAccountId()).isEqualTo(TEST_ACCOUNT_ID);
        assertThat(response.getResourceModel().getEmail()).isEqualTo(TEST_ACCOUNT_EMAIL);
        assertThat(response.getResourceModel().getAccountName()).isEqualTo(TEST_ACCOUNT_NAME);
        assertThat(response.getResourceModel().getParentIds()).isEqualTo(TEST_PARENT_IDS);
        assertThat(TagTestResourcesHelper.tagsEqual(response.getResourceModel().getTags(), TagTestResourcesHelper.defaultTags));
        assertThat(response.getResourceModels()).isNull();
        assertThat(response.getMessage()).isNull();
        assertThat(response.getErrorCode()).isNull();

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
                                                                  .build();

        final ProgressEvent<ResourceModel, CallbackContext> response = createHandler.handleRequest(mockAwsClientProxy, request, new CallbackContext(), logger);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.FAILED);
        assertThat(response.getCallbackDelaySeconds()).isEqualTo(0);
        assertThat(response.getResourceModel()).isNotNull();
        assertThat(response.getResourceModel().getCreateAccountRequestId()).isNull();
        assertThat(response.getResourceModel().getFailureReason()).isNull();
        assertThat(response.getErrorCode()).isEqualTo(HandlerErrorCode.InvalidRequest);
        assertThat(response.getResourceModel().getAccountId()).isNull();
        assertThat(response.getResourceModel().getEmail()).isEqualTo(TEST_ACCOUNT_EMAIL);
        assertThat(response.getResourceModel().getAccountName()).isEqualTo(TEST_ACCOUNT_NAME);
        assertThat(response.getResourceModel().getParentIds()).isEqualTo(TEST_PARENT_IDS);
        assertThat(TagTestResourcesHelper.tagsEqual(response.getResourceModel().getTags(), TagTestResourcesHelper.defaultTags));

        verify(mockProxyClient.client(), times(0)).createAccount(any(CreateAccountRequest.class));
        verify(mockProxyClient.client(), times(0)).describeCreateAccountStatus(any(DescribeCreateAccountStatusRequest.class));
        verify(mockProxyClient.client(), times(0)).moveAccount(any(MoveAccountRequest.class));
    }

    @Test
    public void handleRequest_FailedWithCfnAlreadyExistException() {
        final ResourceModel model = generateCreateResourceModel();

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
                                                                  .desiredResourceState(model)
                                                                  .build();

        final CreateAccountResponse createAccountResponse = getCreateAccountResponse();
        final DescribeCreateAccountStatusResponse describeCreateAccountStatusResponse = getDescribeCreateAccountStatusResponse(FAILED);
        when(mockProxyClient.client().createAccount(any(CreateAccountRequest.class))).thenReturn(createAccountResponse);
        when(mockProxyClient.client().describeCreateAccountStatus(any(DescribeCreateAccountStatusRequest.class))).thenReturn(describeCreateAccountStatusResponse);
        final ProgressEvent<ResourceModel, CallbackContext> response = createHandler.handleRequest(mockAwsClientProxy, request, new CallbackContext(), mockProxyClient, logger);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.FAILED);
        assertThat(response.getCallbackDelaySeconds()).isEqualTo(0);
        assertThat(response.getResourceModel()).isNotNull();
        assertThat(response.getResourceModel().getCreateAccountRequestId()).isEqualTo(CREATE_ACCOUNT_STATUS_ID);
        assertThat(response.getResourceModel().getFailureReason()).isEqualTo(EMAIL_ALREADY_EXISTS);
        assertThat(response.getErrorCode()).isEqualTo(HandlerErrorCode.AlreadyExists);
        assertThat(response.getResourceModel().getAccountId()).isNull();
        assertThat(response.getResourceModel().getEmail()).isEqualTo(TEST_ACCOUNT_EMAIL);
        assertThat(response.getResourceModel().getAccountName()).isEqualTo(TEST_ACCOUNT_NAME);
        assertThat(response.getResourceModel().getParentIds()).isEqualTo(TEST_PARENT_IDS);
        assertThat(TagTestResourcesHelper.tagsEqual(response.getResourceModel().getTags(), TagTestResourcesHelper.defaultTags));

        verify(mockProxyClient.client()).createAccount(any(CreateAccountRequest.class));
        verify(mockProxyClient.client(), atLeast(1)).describeCreateAccountStatus(any(DescribeCreateAccountStatusRequest.class));
        verify(mockProxyClient.client(), times(0)).moveAccount(any(MoveAccountRequest.class));
    }


    @Test
    public void handleRequest_FailedInMoveAccountWhenMultipleParentIds() {
        final ResourceModel model = generateCreateResourceModelWithMultipleParentIds();

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
                                                                  .desiredResourceState(model)
                                                                  .build();

        final ProgressEvent<ResourceModel, CallbackContext> response = createHandler.handleRequest(mockAwsClientProxy, request, new CallbackContext(), mockProxyClient, logger);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.FAILED);
        assertThat(response.getCallbackDelaySeconds()).isEqualTo(0);
        assertThat(response.getResourceModel()).isNotNull();
        assertThat(response.getResourceModel().getAccountId()).isNull();
        assertThat(response.getResourceModel().getEmail()).isEqualTo(TEST_ACCOUNT_EMAIL);
        assertThat(response.getResourceModel().getAccountName()).isEqualTo(TEST_ACCOUNT_NAME);
        assertThat(TagTestResourcesHelper.tagsEqual(response.getResourceModel().getTags(), TagTestResourcesHelper.defaultTags));
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
                                                                  .build();

        final CreateAccountResponse createAccountResponse = getCreateAccountResponse();
        final DescribeCreateAccountStatusResponse describeCreateAccountStatusResponse = getDescribeCreateAccountStatusResponse(SUCCEEDED);
        final ListParentsResponse listParentsResponse = getListParentsResponseBeforeMoveAccount();


        when(mockProxyClient.client().createAccount(any(CreateAccountRequest.class))).thenReturn(createAccountResponse);
        when(mockProxyClient.client().describeCreateAccountStatus(any(DescribeCreateAccountStatusRequest.class))).thenReturn(describeCreateAccountStatusResponse);
        lenient().when(mockProxyClient.client().listParents(any(ListParentsRequest.class))).thenReturn(listParentsResponse);
        when(mockProxyClient.client().moveAccount(any(MoveAccountRequest.class))).thenThrow(DuplicateAccountException.class);

        // read
        final DescribeAccountResponse describeAccountResponse = DescribeAccountResponse.builder().account(Account.builder()
                                                                                                              .arn(TEST_ACCOUNT_ARN)
                                                                                                              .email(TEST_ACCOUNT_EMAIL)
                                                                                                              .id(TEST_ACCOUNT_ID)
                                                                                                              .name(TEST_ACCOUNT_NAME)
                                                                                                              .build()).build();

        final ListTagsForResourceResponse listTagsForResourceResponse = TagTestResourcesHelper.buildDefaultTagsResponse();
        final ListParentsResponse listParentsResponseAfterMove = getListParentsResponseAfterMoveAccount();

        when(mockProxyClient.client().describeAccount(any(DescribeAccountRequest.class))).thenReturn(describeAccountResponse);
        lenient().when(mockProxyClient.client().listParents(any(ListParentsRequest.class))).thenReturn(listParentsResponseAfterMove);
        when(mockProxyClient.client().listTagsForResource(any(ListTagsForResourceRequest.class))).thenReturn(listTagsForResourceResponse);

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
        assertThat(TagTestResourcesHelper.tagsEqual(response.getResourceModel().getTags(), TagTestResourcesHelper.defaultTags));

        verify(mockProxyClient.client()).createAccount(any(CreateAccountRequest.class));
        verify(mockProxyClient.client(), atLeast(1)).describeCreateAccountStatus(any(DescribeCreateAccountStatusRequest.class));
        verify(mockProxyClient.client(), times(1)).moveAccount(any(MoveAccountRequest.class));
    }

    @Test
    public void handleRequest_SkipCreateAccountAndDescribeCreateAccountStatusIfAccountAlreadyCreated() {
        final ResourceModel model = generateCreateResourceModel();

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
                                                                  .desiredResourceState(model)
                                                                  .build();

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

        verify(mockProxyClient.client(), times(1)).createAccount(any(CreateAccountRequest.class));
        verify(mockProxyClient.client(), atMost(3)).describeCreateAccountStatus(any(DescribeCreateAccountStatusRequest.class));
        verify(mockProxyClient.client(), times(2)).moveAccount(any(MoveAccountRequest.class));
    }

    @Test
    public void handleRequest_FailedWithCfnServiceLimitExceededException() {
        final ResourceModel model = generateCreateResourceModel();

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
                                                                  .desiredResourceState(model)
                                                                  .build();

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
        assertThat(response.getResourceModel().getCreateAccountRequestId()).isEqualTo(CREATE_ACCOUNT_STATUS_ID);
        assertThat(response.getResourceModel().getFailureReason()).isEqualTo(ACCOUNT_LIMIT_EXCEEDED);
        assertThat(response.getErrorCode()).isEqualTo(HandlerErrorCode.ServiceLimitExceeded);
        assertThat(response.getResourceModel().getAccountId()).isNull();
        assertThat(response.getResourceModel().getEmail()).isEqualTo(TEST_ACCOUNT_EMAIL);
        assertThat(response.getResourceModel().getAccountName()).isEqualTo(TEST_ACCOUNT_NAME);
        assertThat(response.getResourceModel().getParentIds()).isEqualTo(TEST_PARENT_IDS);
        assertThat(TagTestResourcesHelper.tagsEqual(response.getResourceModel().getTags(), TagTestResourcesHelper.defaultTags));

        verify(mockProxyClient.client()).createAccount(any(CreateAccountRequest.class));
        verify(mockProxyClient.client(), atLeast(1)).describeCreateAccountStatus(any(DescribeCreateAccountStatusRequest.class));
        verify(mockProxyClient.client(), times(0)).moveAccount(any(MoveAccountRequest.class));
    }

    @Test
    public void handleRequest_FailedWithCfnInvalidRequestException() {
        final ResourceModel model = generateCreateResourceModel();

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
                                                                  .desiredResourceState(model)
                                                                  .build();

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
        assertThat(response.getResourceModel().getCreateAccountRequestId()).isEqualTo(CREATE_ACCOUNT_STATUS_ID);
        assertThat(response.getResourceModel().getFailureReason()).isEqualTo(INVALID_EMAIL);
        assertThat(response.getErrorCode()).isEqualTo(HandlerErrorCode.InvalidRequest);
        assertThat(response.getResourceModel().getAccountId()).isNull();
        assertThat(response.getResourceModel().getEmail()).isEqualTo(TEST_ACCOUNT_EMAIL);
        assertThat(response.getResourceModel().getAccountName()).isEqualTo(TEST_ACCOUNT_NAME);
        assertThat(response.getResourceModel().getParentIds()).isEqualTo(TEST_PARENT_IDS);
        assertThat(TagTestResourcesHelper.tagsEqual(response.getResourceModel().getTags(), TagTestResourcesHelper.defaultTags));

        verify(mockProxyClient.client()).createAccount(any(CreateAccountRequest.class));
        verify(mockProxyClient.client(), atLeast(1)).describeCreateAccountStatus(any(DescribeCreateAccountStatusRequest.class));
        verify(mockProxyClient.client(), times(0)).moveAccount(any(MoveAccountRequest.class));
    }

    @Test
    public void handleRequest_FailedWithCfnGeneralServiceException() {
        final ResourceModel model = generateCreateResourceModel();

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
                                                                  .desiredResourceState(model)
                                                                  .build();

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
        assertThat(response.getResourceModel().getCreateAccountRequestId()).isEqualTo(CREATE_ACCOUNT_STATUS_ID);
        assertThat(response.getResourceModel().getFailureReason()).isEqualTo(INTERNAL_FAILURE);
        assertThat(response.getErrorCode()).isEqualTo(HandlerErrorCode.GeneralServiceException);
        assertThat(response.getResourceModel().getAccountId()).isNull();
        assertThat(response.getResourceModel().getEmail()).isEqualTo(TEST_ACCOUNT_EMAIL);
        assertThat(response.getResourceModel().getAccountName()).isEqualTo(TEST_ACCOUNT_NAME);
        assertThat(response.getResourceModel().getParentIds()).isEqualTo(TEST_PARENT_IDS);
        assertThat(TagTestResourcesHelper.tagsEqual(response.getResourceModel().getTags(), TagTestResourcesHelper.defaultTags));

        verify(mockProxyClient.client()).createAccount(any(CreateAccountRequest.class));
        verify(mockProxyClient.client(), atLeast(1)).describeCreateAccountStatus(any(DescribeCreateAccountStatusRequest.class));
        verify(mockProxyClient.client(), times(0)).moveAccount(any(MoveAccountRequest.class));
    }

    @Test
    public void handleRequest_shouldFailWhenNoAccountNameAndEmailAddress() {
        final ResourceModel model = generateCreateResourceModelNoAccountNameAndEmail();

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
                                                                  .desiredResourceState(model)
                                                                  .build();

        final ProgressEvent<ResourceModel, CallbackContext> response = createHandler.handleRequest(mockAwsClientProxy, request, new CallbackContext(), mockProxyClient, logger);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.FAILED);
        assertThat(response.getCallbackDelaySeconds()).isEqualTo(0);
        assertThat(response.getResourceModel()).isNotNull();
        assertThat(response.getResourceModel().getCreateAccountRequestId()).isNull();
        assertThat(response.getResourceModel().getFailureReason()).isNull();
        assertThat(response.getErrorCode()).isEqualTo(HandlerErrorCode.InvalidRequest);
        assertThat(response.getResourceModel().getAccountId()).isNull();
        assertThat(response.getResourceModel().getEmail()).isNull();
        assertThat(response.getResourceModel().getAccountName()).isNull();
        assertThat(response.getResourceModel().getParentIds()).isEqualTo(TEST_PARENT_IDS);
        assertThat(TagTestResourcesHelper.tagsEqual(response.getResourceModel().getTags(), TagTestResourcesHelper.defaultTags));

        verify(mockProxyClient.client(), times(0)).createAccount(any(CreateAccountRequest.class));
        verify(mockProxyClient.client(), atLeast(0)).describeCreateAccountStatus(any(DescribeCreateAccountStatusRequest.class));
        verify(mockProxyClient.client(), times(0)).moveAccount(any(MoveAccountRequest.class));
    }

    @Test
    public void handleRequest_shouldSkipMoveAccountWhenParentIdNotProvided() {
        final ResourceModel model = generateCreateResourceModelWithNoParentId();

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
                                                                  .desiredResourceState(model)
                                                                  .build();

        final CreateAccountResponse createAccountResponse = getCreateAccountResponse();
        final DescribeCreateAccountStatusResponse describeCreateAccountStatusResponse = getDescribeCreateAccountStatusResponse(SUCCEEDED);


        when(mockProxyClient.client().createAccount(any(CreateAccountRequest.class))).thenReturn(createAccountResponse);
        when(mockProxyClient.client().describeCreateAccountStatus(any(DescribeCreateAccountStatusRequest.class))).thenReturn(describeCreateAccountStatusResponse);

        // read
        final DescribeAccountResponse describeAccountResponse = DescribeAccountResponse.builder().account(Account.builder()
                                                                                                              .arn(TEST_ACCOUNT_ARN)
                                                                                                              .email(TEST_ACCOUNT_EMAIL)
                                                                                                              .id(TEST_ACCOUNT_ID)
                                                                                                              .name(TEST_ACCOUNT_NAME)
                                                                                                              .build()).build();

        final ListParentsResponse listParentsResponseAfterMoveAccountResponse = getListParentsResponseBeforeMoveAccount();

        final ListTagsForResourceResponse listTagsForResourceResponse = TagTestResourcesHelper.buildDefaultTagsResponse();
        when(mockProxyClient.client().describeAccount(any(DescribeAccountRequest.class))).thenReturn(describeAccountResponse);
        when(mockProxyClient.client().listParents(any(ListParentsRequest.class))).thenReturn(listParentsResponseAfterMoveAccountResponse);
        when(mockProxyClient.client().listTagsForResource(any(ListTagsForResourceRequest.class))).thenReturn(listTagsForResourceResponse);

        final ProgressEvent<ResourceModel, CallbackContext> response = createHandler.handleRequest(mockAwsClientProxy, request, new CallbackContext(), mockProxyClient, logger);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.SUCCESS);
        assertThat(response.getCallbackDelaySeconds()).isEqualTo(0);
        assertThat(response.getResourceModel()).isNotNull();
        assertThat(response.getResourceModel().getAccountId()).isEqualTo(TEST_ACCOUNT_ID);
        assertThat(response.getResourceModel().getEmail()).isEqualTo(TEST_ACCOUNT_EMAIL);
        assertThat(response.getResourceModel().getAccountName()).isEqualTo(TEST_ACCOUNT_NAME);
        assertThat(response.getResourceModel().getParentIds()).isEqualTo(ImmutableSet.of(TEST_SOURCE_PARENT_ID));
        assertThat(TagTestResourcesHelper.tagsEqual(response.getResourceModel().getTags(), TagTestResourcesHelper.defaultTags));
        assertThat(response.getResourceModels()).isNull();
        assertThat(response.getMessage()).isNull();
        assertThat(response.getErrorCode()).isNull();

        verify(mockProxyClient.client()).createAccount(any(CreateAccountRequest.class));
        verify(mockProxyClient.client(), times(0)).moveAccount(any(MoveAccountRequest.class));
        verify(mockProxyClient.client(), atLeast(1)).describeCreateAccountStatus(any(DescribeCreateAccountStatusRequest.class));
    }

    @Test
    public void handleRequest_shouldFailWhenMoveAccountThrowsDestinationParentNotFoundException() {
        final ResourceModel model = generateCreateResourceModel();

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
                                                                  .desiredResourceState(model)
                                                                  .build();

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
        assertThat(TagTestResourcesHelper.tagsEqual(response.getResourceModel().getTags(), TagTestResourcesHelper.defaultTags));

        verify(mockProxyClient.client()).createAccount(any(CreateAccountRequest.class));
        verify(mockProxyClient.client(), atLeast(1)).describeCreateAccountStatus(any(DescribeCreateAccountStatusRequest.class));
        verify(mockProxyClient.client(), times(1)).moveAccount(any(MoveAccountRequest.class));
    }

    @Test
    public void handleRequest_shouldFailWhenDescribeCreateAccountStatusThrowsAccessDeniedException() {
        final ResourceModel model = generateCreateResourceModel();

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
                                                                  .desiredResourceState(model)
                                                                  .build();

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
        assertThat(TagTestResourcesHelper.tagsEqual(response.getResourceModel().getTags(), TagTestResourcesHelper.defaultTags));

        verify(mockProxyClient.client()).createAccount(any(CreateAccountRequest.class));
        verify(mockProxyClient.client(), atLeast(1)).describeCreateAccountStatus(any(DescribeCreateAccountStatusRequest.class));
        verify(mockProxyClient.client(), times(0)).moveAccount(any(MoveAccountRequest.class));
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
