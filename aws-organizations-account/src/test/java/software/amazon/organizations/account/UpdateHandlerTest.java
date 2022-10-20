package software.amazon.organizations.account;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.organizations.OrganizationsClient;
import software.amazon.awssdk.services.organizations.model.ConcurrentModificationException;
import software.amazon.awssdk.services.organizations.model.ConstraintViolationException;
import software.amazon.awssdk.services.organizations.model.DescribeAccountRequest;
import software.amazon.awssdk.services.organizations.model.DuplicateAccountException;
import software.amazon.awssdk.services.organizations.model.ListParentsRequest;
import software.amazon.awssdk.services.organizations.model.ListParentsResponse;
import software.amazon.awssdk.services.organizations.model.ListRootsRequest;
import software.amazon.awssdk.services.organizations.model.ListRootsResponse;
import software.amazon.awssdk.services.organizations.model.ListTagsForResourceRequest;
import software.amazon.awssdk.services.organizations.model.ListTagsForResourceResponse;
import software.amazon.awssdk.services.organizations.model.MoveAccountRequest;
import software.amazon.awssdk.services.organizations.model.MoveAccountResponse;
import software.amazon.awssdk.services.organizations.model.Parent;
import software.amazon.awssdk.services.organizations.model.Root;
import software.amazon.awssdk.services.organizations.model.Tag;
import software.amazon.awssdk.services.organizations.model.TagResourceRequest;
import software.amazon.awssdk.services.organizations.model.UntagResourceRequest;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.HandlerErrorCode;
import software.amazon.cloudformation.proxy.OperationStatus;
import software.amazon.cloudformation.proxy.ProgressEvent;
import software.amazon.cloudformation.proxy.ProxyClient;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;

import java.time.Duration;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class UpdateHandlerTest extends AbstractTestBase {

    @Mock
    OrganizationsClient mockOrgsClient;
    @Mock
    private AmazonWebServicesClientProxy mockAwsClientProxy;
    @Mock
    private ProxyClient<OrganizationsClient> mockProxyClient;
    private UpdateHandler updateHandler;

    @BeforeEach
    public void setup() {
        updateHandler = new UpdateHandler();
        mockAwsClientProxy = new AmazonWebServicesClientProxy(logger, MOCK_CREDENTIALS, () -> Duration.ofSeconds(600).toMillis());
        mockOrgsClient = mock(OrganizationsClient.class);
        mockProxyClient = MOCK_PROXY(mockAwsClientProxy, mockOrgsClient);
    }

    @Test
    public void handleRequest_SimpleSuccess() {
        final ResourceModel previousResourceModel = generatePreviousResourceModel(null);
        final ResourceModel model = generateUpdatedResourceModel(null);

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
                .previousResourceState(previousResourceModel)
                .desiredResourceState(model)
                .build();

        // Update Handler Mocks
        final MoveAccountResponse moveAccountResponse = getMoveAccountResponse();
        when(mockProxyClient.client().moveAccount(any(MoveAccountRequest.class))).thenReturn(moveAccountResponse);

        // Read Handler Mocks
        whenReadMockSetup(request, null);

        final ProgressEvent<ResourceModel, CallbackContext> response =
                updateHandler.handleRequest(mockAwsClientProxy, request, new CallbackContext(), mockProxyClient, logger);

        // Verify Response
        verifyHandlerSuccess(response, request);
        verifyReadHandler();

        // Verify Orgs API calls
        verify(mockProxyClient.client()).moveAccount(any(MoveAccountRequest.class));

        tearDown();
    }

    @Test
    public void handleRequest_OnlyTags_Success() {
        final ResourceModel previousResourceModel = generatePreviousResourceModel(TagTestResourcesHelper.defaultTags);
        final ResourceModel model = generatePreviousResourceModel(TagTestResourcesHelper.updatedTags);

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
                .previousResourceState(previousResourceModel)
                .desiredResourceState(model)
                .build();

        final TagResourceRequest expectedTagResourceRequest = TagResourceRequest.builder()
                .tags(TagTestResourcesHelper.expectedTagsToAddOrUpdate)
                .resourceId(TEST_ACCOUNT_ID)
                .build();
        final UntagResourceRequest expectedUntagResourceRequest = UntagResourceRequest.builder()
                .tagKeys(TagTestResourcesHelper.expectedTagsToRemove)
                .resourceId(TEST_ACCOUNT_ID)
                .build();

        // Read Handler Mocks
        whenReadMockSetup(request, TagTestResourcesHelper.updatedTags);

        final ProgressEvent<ResourceModel, CallbackContext> response =
                updateHandler.handleRequest(mockAwsClientProxy, request, new CallbackContext(), mockProxyClient, logger);

        // Verify Response
        verifyHandlerSuccess(response, request);
        verifyReadHandler();

        // Verify Tags
        ArgumentCaptor<UntagResourceRequest> untagResourceRequestArgumentCaptor = ArgumentCaptor.forClass(UntagResourceRequest.class);
        verify(mockProxyClient.client()).untagResource(untagResourceRequestArgumentCaptor.capture());
        assertEquals(expectedUntagResourceRequest.tagKeys(), untagResourceRequestArgumentCaptor.getValue().tagKeys());
        assertEquals(expectedUntagResourceRequest.resourceId(), untagResourceRequestArgumentCaptor.getValue().resourceId());

        ArgumentCaptor<TagResourceRequest> tagResourceRequestArgumentCaptor = ArgumentCaptor.forClass(TagResourceRequest.class);
        verify(mockProxyClient.client()).tagResource(tagResourceRequestArgumentCaptor.capture());
        assertEquals(expectedTagResourceRequest.tags(), tagResourceRequestArgumentCaptor.getValue().tags());
        assertEquals(expectedTagResourceRequest.resourceId(), tagResourceRequestArgumentCaptor.getValue().resourceId());

        // Verify account was not moved
        verify(mockProxyClient.client(), never()).listRoots(any(ListRootsRequest.class));
        verify(mockProxyClient.client(), never()).moveAccount(any(MoveAccountRequest.class));

        tearDown();
    }

    @Test
    public void handleRequest_SourceAndDestinationNull_Success() {
        final ResourceModel previousResourceModel = generatePreviousResourceModel(null).toBuilder()
                .parentIds(null)
                .build();
        final ResourceModel model = generateUpdatedResourceModel(null).toBuilder()
                .parentIds(null)
                .build();

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
                .previousResourceState(previousResourceModel)
                .desiredResourceState(model)
                .build();

        // Read Handler Mocks
        whenReadMockSetup(request, null);

        final ProgressEvent<ResourceModel, CallbackContext> response =
                updateHandler.handleRequest(mockAwsClientProxy, request, new CallbackContext(), mockProxyClient, logger);

        // Verify Response
        verifyHandlerSuccess(response, request);
        verifyReadHandler();

        // Verify account was not moved
        verify(mockProxyClient.client(), never()).listRoots(any(ListRootsRequest.class));
        verify(mockProxyClient.client(), never()).moveAccount(any(MoveAccountRequest.class));

        tearDown();
    }

    @Test
    public void handleRequest_SourceTargetNull_Success() {
        final ResourceModel previousResourceModel = generatePreviousResourceModel(null).toBuilder()
                .parentIds(null)
                .build();
        final ResourceModel model = generateUpdatedResourceModel(null);

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
                .previousResourceState(previousResourceModel)
                .desiredResourceState(model)
                .build();

        ListRootsResponse listRootsResponse = getListRootsResponse();
        MoveAccountResponse moveAccountResponse = getMoveAccountResponse();

        when(mockProxyClient.client().listRoots(any(ListRootsRequest.class))).thenReturn(listRootsResponse);
        when(mockProxyClient.client().moveAccount(any(MoveAccountRequest.class))).thenReturn(moveAccountResponse);

        whenReadMockSetup(request, null);

        final ProgressEvent<ResourceModel, CallbackContext> response =
                updateHandler.handleRequest(mockAwsClientProxy, request, new CallbackContext(), mockProxyClient, logger);

        // Verify Response
        verifyHandlerSuccess(response, request);
        verify(mockProxyClient.client()).moveAccount(any(MoveAccountRequest.class));
        verify(mockProxyClient.client()).listRoots(any(ListRootsRequest.class));

        tearDown();
    }

    @Test
    public void handleRequest_DestinationTargetNull_Success() {
        final ResourceModel previousResourceModel = generatePreviousResourceModel(null);
        final ResourceModel model = generateUpdatedResourceModel(null).toBuilder()
                .parentIds(null)
                .build();

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
                .previousResourceState(previousResourceModel)
                .desiredResourceState(model)
                .build();

        ListRootsResponse listRootsResponse = getListRootsResponse();
        MoveAccountResponse moveAccountResponse = getMoveAccountResponse();

        when(mockProxyClient.client().listRoots(any(ListRootsRequest.class))).thenReturn(listRootsResponse);
        when(mockProxyClient.client().moveAccount(any(MoveAccountRequest.class))).thenReturn(moveAccountResponse);
        whenReadMockSetup(request, null);

        final ProgressEvent<ResourceModel, CallbackContext> response =
                updateHandler.handleRequest(mockAwsClientProxy, request, new CallbackContext(), mockProxyClient, logger);

        // Verify Response
        verifyHandlerSuccess(response, request);
        verify(mockProxyClient.client()).moveAccount(any(MoveAccountRequest.class));
        verify(mockProxyClient.client()).listRoots(any(ListRootsRequest.class));

        tearDown();
    }

    @Test
    public void handleRequest_MoveAccountThrowsDuplicateAccountException_Success() {
        final ResourceModel previousResourceModel = generatePreviousResourceModel(null);
        final ResourceModel model = generateUpdatedResourceModel(null);

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
                .previousResourceState(previousResourceModel)
                .desiredResourceState(model)
                .build();

        // Update Handler Mocks
        when(mockProxyClient.client().moveAccount(any(MoveAccountRequest.class))).thenThrow(DuplicateAccountException.class);
        whenReadMockSetup(request, null);

        final ProgressEvent<ResourceModel, CallbackContext> response =
                updateHandler.handleRequest(mockAwsClientProxy, request, new CallbackContext(), mockProxyClient, logger);

        // Verify Orgs API calls
        verifyHandlerSuccess(response, request);

        tearDown();
    }

    @Test
    public void handleRequest_WithTags_UntagResource_ThrowsCfnServiceLimitExceeded_Fails() {
        final ResourceModel initialResourceModel = generatePreviousResourceModel(TagTestResourcesHelper.defaultTags);
        final ResourceModel updatedResourceModel = generatePreviousResourceModel(TagTestResourcesHelper.updatedTags);

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
                .previousResourceState(initialResourceModel)
                .desiredResourceState(updatedResourceModel)
                .build();

        //mock exception for UntagResource
        when(mockProxyClient.client().untagResource(any(UntagResourceRequest.class))).thenThrow(ConstraintViolationException.class);

        final ProgressEvent<ResourceModel, CallbackContext> response =
                updateHandler.handleRequest(mockAwsClientProxy, request, new CallbackContext(), mockProxyClient, logger);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.FAILED);
        assertThat(response.getResourceModels()).isNull();
        assertThat(response.getErrorCode()).isEqualTo(HandlerErrorCode.ServiceLimitExceeded);

        verify(mockProxyClient.client()).untagResource(any(UntagResourceRequest.class));

        verifyNoMoreInteractions(mockOrgsClient);
    }

    @Test
    public void handleRequest_WithTags_TagResource_ThrowsCfnServiceLimitExceeded_Fails() {
        final ResourceModel initialResourceModel = generatePreviousResourceModel(TagTestResourcesHelper.defaultTags);
        final ResourceModel updatedResourceModel = generatePreviousResourceModel(TagTestResourcesHelper.updatedTags);

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
                .previousResourceState(initialResourceModel)
                .desiredResourceState(updatedResourceModel)
                .build();

        //mock exception for UntagResource
        when(mockProxyClient.client().tagResource(any(TagResourceRequest.class))).thenThrow(ConstraintViolationException.class);

        final ProgressEvent<ResourceModel, CallbackContext> response =
                updateHandler.handleRequest(mockAwsClientProxy, request, new CallbackContext(), mockProxyClient, logger);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.FAILED);
        assertThat(response.getResourceModels()).isNull();
        assertThat(response.getErrorCode()).isEqualTo(HandlerErrorCode.ServiceLimitExceeded);

        verify(mockProxyClient.client()).tagResource(any(TagResourceRequest.class));
        verify(mockProxyClient.client()).untagResource(any(UntagResourceRequest.class));

        verifyNoMoreInteractions(mockOrgsClient);
    }

    @Test
    public void handleRequest_ThrowsCfnInvalidRequestException_RoleName_Fails() {
        final ResourceModel previousResourceModel = generatePreviousResourceModel(null);

        final ResourceModel model = generateUpdatedResourceModel(null).toBuilder()
                .roleName("UpdatedRoleName")
                .build();

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
                .previousResourceState(previousResourceModel)
                .desiredResourceState(model)
                .build();

        final ProgressEvent<ResourceModel, CallbackContext> response =
                updateHandler.handleRequest(mockAwsClientProxy, request, new CallbackContext(), mockProxyClient, logger);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.FAILED);
        assertThat(response.getResourceModels()).isNull();
        assertThat(response.getErrorCode()).isEqualTo(HandlerErrorCode.InvalidRequest);
    }

    @Test
    public void handleRequest_ThrowsCfnInvalidRequestException_AccountName_Fails() {
        final ResourceModel previousResourceModel = generatePreviousResourceModel(null);

        final ResourceModel model = generateUpdatedResourceModel(null).toBuilder()
                .accountName("UpdatedAccountName")
                .build();

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
                .previousResourceState(previousResourceModel)
                .desiredResourceState(model)
                .build();

        final ProgressEvent<ResourceModel, CallbackContext> response =
                updateHandler.handleRequest(mockAwsClientProxy, request, new CallbackContext(), mockProxyClient, logger);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.FAILED);
        assertThat(response.getResourceModels()).isNull();
        assertThat(response.getErrorCode()).isEqualTo(HandlerErrorCode.InvalidRequest);
    }

    @Test
    public void handleRequest_ThrowsCfnInvalidRequestException_Email_Fails() {
        final ResourceModel previousResourceModel = generatePreviousResourceModel(null);

        final ResourceModel model = generateUpdatedResourceModel(null).toBuilder()
                .email("UpdatedEmail@email.com")
                .build();

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
                .previousResourceState(previousResourceModel)
                .desiredResourceState(model)
                .build();

        final ProgressEvent<ResourceModel, CallbackContext> response =
                updateHandler.handleRequest(mockAwsClientProxy, request, new CallbackContext(), mockProxyClient, logger);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.FAILED);
        assertThat(response.getResourceModels()).isNull();
        assertThat(response.getErrorCode()).isEqualTo(HandlerErrorCode.InvalidRequest);
    }

        @Test
    public void handleRequest_MoveAccountWithMultipleParentIds_Fails() {
            final ResourceModel previousResourceModel = generatePreviousResourceModel(null);

            final ResourceModel model = generateUpdatedResourceModel(null).toBuilder()
                    .parentIds(TEST_MULTIPLE_PARENT_IDS)
                    .build();

            final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
                    .previousResourceState(previousResourceModel)
                    .desiredResourceState(model)
                    .build();

            final ProgressEvent<ResourceModel, CallbackContext> response =
                    updateHandler.handleRequest(mockAwsClientProxy, request, new CallbackContext(), mockProxyClient, logger);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.FAILED);
        assertThat(response.getCallbackDelaySeconds()).isEqualTo(0);
        assertThat(response.getResourceModel()).isNotNull();
        assertThat(response.getResourceModel()).isEqualTo(request.getDesiredResourceState());
        assertThat(response.getErrorCode()).isEqualTo(HandlerErrorCode.InvalidRequest);

    }

    @Test
    public void handleRequest_MoveAccountThrowsConcurrentModificationException_Fails() {
        final ResourceModel previousResourceModel = generatePreviousResourceModel(null);
        final ResourceModel model = generateUpdatedResourceModel(null);

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
                .previousResourceState(previousResourceModel)
                .desiredResourceState(model)
                .build();

        // Update Handler Mocks
        when(mockProxyClient.client().moveAccount(any(MoveAccountRequest.class))).thenThrow(ConcurrentModificationException.class);
        CallbackContext context = new CallbackContext();
        ProgressEvent<ResourceModel, CallbackContext> response = updateHandler.handleRequest(mockAwsClientProxy, request, context, mockProxyClient, logger);
        // retry attempt 1
        assertThat(context.getCurrentRetryAttempt(AccountConstants.Action.MOVE_ACCOUNT, AccountConstants.Handler.UPDATE)).isEqualTo(1);
        assertThat(response.getStatus()).isEqualTo(OperationStatus.IN_PROGRESS);
        assertThat(response.getCallbackDelaySeconds()).isGreaterThan(0);
        // retry attempt 2
        response = updateHandler.handleRequest(mockAwsClientProxy, request, context, mockProxyClient, logger);
        assertThat(context.getCurrentRetryAttempt(AccountConstants.Action.MOVE_ACCOUNT, AccountConstants.Handler.UPDATE)).isEqualTo(2);
        assertThat(response.getStatus()).isEqualTo(OperationStatus.IN_PROGRESS);
        assertThat(response.getCallbackDelaySeconds()).isGreaterThan(0);

        response = updateHandler.handleRequest(mockAwsClientProxy, request, context, mockProxyClient, logger);
        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.FAILED);
        assertThat(response.getCallbackDelaySeconds()).isEqualTo(0);
        assertThat(response.getResourceModel()).isNotNull();
        assertThat(response.getResourceModel()).isEqualTo(request.getDesiredResourceState());
        assertThat(response.getErrorCode()).isEqualTo(HandlerErrorCode.ResourceConflict);

        verify(mockProxyClient.client(), atLeast(3)).moveAccount(any(MoveAccountRequest.class));

        tearDown();
    }

    private void whenReadMockSetup(ResourceHandlerRequest<ResourceModel> request, Set<Tag> tags){
        final ListParentsResponse listParentsResponse = ListParentsResponse.builder()
                .parents(Parent.builder()
                        .id(TEST_DESTINATION_PARENT_ID)
                        .build()
                ).build();

        final ListTagsForResourceResponse listTagsForResourceResponse = TagTestResourcesHelper.buildTagsResponse(tags);
        when(mockProxyClient.client().describeAccount(any(DescribeAccountRequest.class))).thenReturn(describeAccountResponse);
        when(mockProxyClient.client().listParents(any(ListParentsRequest.class))).thenReturn(listParentsResponse);
        when(mockProxyClient.client().listTagsForResource(any(ListTagsForResourceRequest.class))).thenReturn(listTagsForResourceResponse);
    }

    private void verifyReadHandler(){
        verify(mockProxyClient.client()).describeAccount(any(DescribeAccountRequest.class));
        verify(mockProxyClient.client()).listParents(any(ListParentsRequest.class));
        verify(mockProxyClient.client()).listTagsForResource(any(ListTagsForResourceRequest.class));
    }

    private void verifyHandlerSuccess(ProgressEvent<ResourceModel, CallbackContext> response, ResourceHandlerRequest<ResourceModel> request){
        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.SUCCESS);
        assertThat(response.getCallbackContext()).isNull();
        assertThat(response.getCallbackDelaySeconds()).isEqualTo(0);
        assertThat(response.getResourceModel()).isEqualTo(request.getDesiredResourceState());
        assertThat(response.getResourceModels()).isNull();
        assertThat(response.getMessage()).isNull();
        assertThat(response.getErrorCode()).isNull();
    }

    private void tearDown(){
        verify(mockOrgsClient, atLeastOnce()).serviceName();
        verifyNoMoreInteractions(mockOrgsClient);
    }

    protected MoveAccountResponse getMoveAccountResponse() {
        return MoveAccountResponse.builder().build();
    }

    protected ListRootsResponse getListRootsResponse() {
        return ListRootsResponse.builder()
                .roots(Root.builder()
                        .id("r-root")
                        .build())
                .build();
    }

    protected ResourceModel generatePreviousResourceModel(Set<Tag> tags) {
        return ResourceModel.builder()
                .accountName(TEST_ACCOUNT_NAME)
                .accountId(TEST_ACCOUNT_ID)
                .email(TEST_ACCOUNT_EMAIL)
                .parentIds(TEST_PARENT_IDS)
                .tags(TagTestResourcesHelper.translateOrganizationTagsToAccountTags(tags))
                .build();
    }

    protected ResourceModel generateUpdatedResourceModel(Set<Tag> tags) {
        return ResourceModel.builder()
                .accountName(TEST_ACCOUNT_NAME)
                .accountId(TEST_ACCOUNT_ID)
                .email(TEST_ACCOUNT_EMAIL)
                .parentIds(TEST_PARENT_UPDATED_IDS)
                .tags(TagTestResourcesHelper.translateOrganizationTagsToAccountTags(tags))
                .build();
    }
}
