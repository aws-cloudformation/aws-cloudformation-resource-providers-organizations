package software.amazon.organizations.resourcepolicy;

import java.time.Duration;
import java.util.Set;

import software.amazon.awssdk.services.organizations.OrganizationsClient;
import software.amazon.awssdk.services.organizations.model.ConstraintViolationException;
import software.amazon.awssdk.services.organizations.model.AccessDeniedException;
import software.amazon.awssdk.services.organizations.model.ConcurrentModificationException;
import software.amazon.awssdk.services.organizations.model.DescribeResourcePolicyRequest;
import software.amazon.awssdk.services.organizations.model.DescribeResourcePolicyResponse;
import software.amazon.awssdk.services.organizations.model.InvalidInputException;
import software.amazon.awssdk.services.organizations.model.ListTagsForResourceRequest;
import software.amazon.awssdk.services.organizations.model.ListTagsForResourceResponse;
import software.amazon.awssdk.services.organizations.model.PutResourcePolicyRequest;
import software.amazon.awssdk.services.organizations.model.PutResourcePolicyResponse;
import software.amazon.awssdk.services.organizations.model.ServiceException;
import software.amazon.awssdk.services.organizations.model.Tag;
import software.amazon.awssdk.services.organizations.model.TagResourceRequest;
import software.amazon.awssdk.services.organizations.model.TooManyRequestsException;
import software.amazon.awssdk.services.organizations.model.UnsupportedApiEndpointException;
import software.amazon.awssdk.services.organizations.model.UntagResourceRequest;

import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.HandlerErrorCode;
import software.amazon.cloudformation.proxy.OperationStatus;
import software.amazon.cloudformation.proxy.ProgressEvent;
import software.amazon.cloudformation.proxy.ProxyClient;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;
import software.amazon.organizations.resourcepolicy.ResourcePolicyConstants.Action;
import software.amazon.organizations.resourcepolicy.ResourcePolicyConstants.Handler;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

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
    public void handleRequest_NoTags_SimpleSuccess() {
        final ResourceModel initialResourceModel = generateFinalResourceModel(false, TEST_RESOURCEPOLICY_CONTENT);
        final ResourceModel updatedResourceModel = generateUpdatedResourceModel(false, TEST_RESOURCEPOLICY_UPDATED_CONTENT);

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
            .previousResourceState(initialResourceModel)
            .desiredResourceState(updatedResourceModel)
            .build();

        mockReadHandler(false);
        final PutResourcePolicyResponse putResourcePolicyResponse = getPutResourcePolicyResponse();
        when(mockProxyClient.client().putResourcePolicy(any(PutResourcePolicyRequest.class))).thenReturn(putResourcePolicyResponse);

        final ProgressEvent<ResourceModel, CallbackContext> response = updateHandler.handleRequest(mockAwsClientProxy, request, new CallbackContext(), mockProxyClient, logger);

        verifyHandlerSuccess(response, request);

        verify(mockProxyClient.client()).putResourcePolicy(any(PutResourcePolicyRequest.class));
        verifyReadHandler();

        verify(mockOrgsClient, atLeastOnce()).serviceName();
        verifyNoMoreInteractions(mockOrgsClient);
    }

    @Test
    public void handleRequest_NoTags_WithJSONContent_SimpleSuccess() {
        final ResourceModel initialResourceModel = generateFinalResourceModel(false, TEST_RESOURCEPOLICY_CONTENT);
        final ResourceModel updatedResourceModel = generateUpdatedResourceModel(false, TEST_RESOURCEPOLICY_UPDATED_CONTENT_JSON);

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
            .previousResourceState(initialResourceModel)
            .desiredResourceState(updatedResourceModel)
            .build();

        mockReadHandler(false);
        final PutResourcePolicyResponse putResourcePolicyResponse = getPutResourcePolicyResponse();
        when(mockProxyClient.client().putResourcePolicy(any(PutResourcePolicyRequest.class))).thenReturn(putResourcePolicyResponse);

        final ProgressEvent<ResourceModel, CallbackContext> response = updateHandler.handleRequest(mockAwsClientProxy, request, new CallbackContext(), mockProxyClient, logger);

        verifyHandlerSuccess(response, request);

        verify(mockProxyClient.client()).putResourcePolicy(any(PutResourcePolicyRequest.class));
        verifyReadHandler();

        verify(mockOrgsClient, atLeastOnce()).serviceName();
        verifyNoMoreInteractions(mockOrgsClient);
    }

    @Test
    public void handleRequest_WithTags_SimpleSuccess() {
        final ResourceModel initialResourceModel = generateFinalResourceModel(true, TEST_RESOURCEPOLICY_CONTENT);
        final ResourceModel updatedResourceModel = generateUpdatedResourceModel(true, TEST_RESOURCEPOLICY_UPDATED_CONTENT);

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
            .previousResourceState(initialResourceModel)
            .desiredResourceState(updatedResourceModel)
            .build();

        mockReadHandler(true);
        final PutResourcePolicyResponse putResourcePolicyResponse = getPutResourcePolicyResponse();
        when(mockProxyClient.client().putResourcePolicy(any(PutResourcePolicyRequest.class))).thenReturn(putResourcePolicyResponse);

        final ProgressEvent<ResourceModel, CallbackContext> response = updateHandler.handleRequest(mockAwsClientProxy, request, new CallbackContext(), mockProxyClient, logger);

        final Set<Tag> tagsToAddOrUpdate = UpdateHandler.getTagsToAddOrUpdate(
            TagTestResourceHelper.translateResourcePolicyTagsToOrganizationTags(initialResourceModel.getTags()),
            TagTestResourceHelper.translateResourcePolicyTagsToOrganizationTags(updatedResourceModel.getTags())
        );

        final Set<String> tagKeysToRemove = UpdateHandler.getTagKeysToRemove(
            TagTestResourceHelper.translateResourcePolicyTagsToOrganizationTags(initialResourceModel.getTags()),
            TagTestResourceHelper.translateResourcePolicyTagsToOrganizationTags(updatedResourceModel.getTags())
        );

        verifyHandlerSuccess(response, request);
        assertThat(TagTestResourceHelper.tagsEqual(response.getResourceModel().getTags(), TagTestResourceHelper.updatedTags));
        assertThat(TagTestResourceHelper.correctTagsInTagAndUntagRequests(tagsToAddOrUpdate, tagKeysToRemove));

        verify(mockProxyClient.client()).putResourcePolicy(any(PutResourcePolicyRequest.class));
        verifyReadHandler();
        verify(mockProxyClient.client()).tagResource(any(TagResourceRequest.class));
        verify(mockProxyClient.client()).untagResource(any(UntagResourceRequest.class));

        verify(mockOrgsClient, atLeastOnce()).serviceName();
        verifyNoMoreInteractions(mockOrgsClient);
    }

    @Test
    public void handleRequest_AddTags_SimpleSuccess() {
        final ResourceModel initialResourceModel = generateFinalResourceModel(false, TEST_RESOURCEPOLICY_CONTENT);
        final ResourceModel updatedResourceModel = generateUpdatedResourceModel(true, TEST_RESOURCEPOLICY_UPDATED_CONTENT);

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
            .previousResourceState(initialResourceModel)
            .desiredResourceState(updatedResourceModel)
            .build();

        mockReadHandler(true);
        final PutResourcePolicyResponse putResourcePolicyResponse = getPutResourcePolicyResponse();
        when(mockProxyClient.client().putResourcePolicy(any(PutResourcePolicyRequest.class))).thenReturn(putResourcePolicyResponse);

        final ProgressEvent<ResourceModel, CallbackContext> response = updateHandler.handleRequest(mockAwsClientProxy, request, new CallbackContext(), mockProxyClient, logger);

        final Set<Tag> tagsToAddOrUpdate = UpdateHandler.getTagsToAddOrUpdate(
            TagTestResourceHelper.translateResourcePolicyTagsToOrganizationTags(initialResourceModel.getTags()),
            TagTestResourceHelper.translateResourcePolicyTagsToOrganizationTags(updatedResourceModel.getTags())
        );

        final Set<String> tagKeysToRemove = UpdateHandler.getTagKeysToRemove(
            TagTestResourceHelper.translateResourcePolicyTagsToOrganizationTags(initialResourceModel.getTags()),
            TagTestResourceHelper.translateResourcePolicyTagsToOrganizationTags(updatedResourceModel.getTags())
        );

        verifyHandlerSuccess(response, request);
        assertThat(TagTestResourceHelper.tagsEqual(response.getResourceModel().getTags(), TagTestResourceHelper.updatedTags));
        assertThat(TagTestResourceHelper.correctTagsInTagAndUntagRequests(tagsToAddOrUpdate, tagKeysToRemove));

        verify(mockProxyClient.client()).putResourcePolicy(any(PutResourcePolicyRequest.class));
        verifyReadHandler();
        verify(mockProxyClient.client()).tagResource(any(TagResourceRequest.class));
        verify(mockProxyClient.client(), never()).untagResource(any(UntagResourceRequest.class));

        verify(mockOrgsClient, atLeastOnce()).serviceName();
        verifyNoMoreInteractions(mockOrgsClient);
    }

    @Test
    public void handleRequest_RemoveTags_SimpleSuccess() {
        final ResourceModel initialResourceModel = generateFinalResourceModel(true, TEST_RESOURCEPOLICY_CONTENT);
        final ResourceModel updatedResourceModel = generateUpdatedResourceModel(false, TEST_RESOURCEPOLICY_UPDATED_CONTENT);

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
            .previousResourceState(initialResourceModel)
            .desiredResourceState(updatedResourceModel)
            .build();

        mockReadHandler(true);
        final PutResourcePolicyResponse putResourcePolicyResponse = getPutResourcePolicyResponse();
        when(mockProxyClient.client().putResourcePolicy(any(PutResourcePolicyRequest.class))).thenReturn(putResourcePolicyResponse);

        final ProgressEvent<ResourceModel, CallbackContext> response = updateHandler.handleRequest(mockAwsClientProxy, request, new CallbackContext(), mockProxyClient, logger);

        final Set<Tag> tagsToAddOrUpdate = UpdateHandler.getTagsToAddOrUpdate(
            TagTestResourceHelper.translateResourcePolicyTagsToOrganizationTags(initialResourceModel.getTags()),
            TagTestResourceHelper.translateResourcePolicyTagsToOrganizationTags(updatedResourceModel.getTags())
        );

        final Set<String> tagKeysToRemove = UpdateHandler.getTagKeysToRemove(
            TagTestResourceHelper.translateResourcePolicyTagsToOrganizationTags(initialResourceModel.getTags()),
            TagTestResourceHelper.translateResourcePolicyTagsToOrganizationTags(updatedResourceModel.getTags())
        );

        verifyHandlerSuccess(response, request);
        assertThat(TagTestResourceHelper.tagsEqual(response.getResourceModel().getTags(), TagTestResourceHelper.updatedTags));
        assertThat(TagTestResourceHelper.correctTagsInTagAndUntagRequests(tagsToAddOrUpdate, tagKeysToRemove));

        verify(mockProxyClient.client()).putResourcePolicy(any(PutResourcePolicyRequest.class));
        verifyReadHandler();
        verify(mockProxyClient.client(), never()).tagResource(any(TagResourceRequest.class));
        verify(mockProxyClient.client()).untagResource(any(UntagResourceRequest.class));

        verify(mockOrgsClient, atLeastOnce()).serviceName();
        verifyNoMoreInteractions(mockOrgsClient);
    }

    @Test
    public void handleRequest_PreviousModelNull_Fails_WithCfnNotFoundException() {
        final ResourceModel updatedResourceModel = generateUpdatedResourceModel(false, TEST_RESOURCEPOLICY_UPDATED_CONTENT);

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
            .previousResourceState(null)
            .desiredResourceState(updatedResourceModel)
            .build();

        final ProgressEvent<ResourceModel, CallbackContext> response = updateHandler.handleRequest(mockAwsClientProxy, request, new CallbackContext(), mockProxyClient, logger);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.FAILED);
        assertThat(response.getResourceModels()).isNull();
        assertThat(response.getErrorCode()).isEqualTo(HandlerErrorCode.NotFound);
    }

    @Test
    public void handleRequest_ModelContentNull_Fails_InvalidRequestException() {
        final ResourceModel initialResourceModel = generateFinalResourceModel(true, TEST_RESOURCEPOLICY_CONTENT);
        final ResourceModel updatedResourceModel = generateUpdatedResourceModel(false, TEST_RESOURCEPOLICY_UPDATED_CONTENT);
        updatedResourceModel.setContent(null);

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
            .previousResourceState(initialResourceModel)
            .desiredResourceState(updatedResourceModel)
            .build();

        final ProgressEvent<ResourceModel, CallbackContext> response = updateHandler.handleRequest(mockAwsClientProxy, request, new CallbackContext(), mockProxyClient, logger);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.FAILED);
        assertThat(response.getResourceModels()).isNull();
        assertThat(response.getErrorCode()).isEqualTo(HandlerErrorCode.InvalidRequest);
    }

    @Test
    public void handleRequest_WithTags_UntagResourceFails_With_CfnServiceLimitExceeded() {
        final ResourceModel initialResourceModel = generateFinalResourceModel(true, TEST_RESOURCEPOLICY_CONTENT);
        final ResourceModel updatedResourceModel = generateUpdatedResourceModel(true, TEST_RESOURCEPOLICY_UPDATED_CONTENT);

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
            .previousResourceState(initialResourceModel)
            .desiredResourceState(updatedResourceModel)
            .build();

        final PutResourcePolicyResponse putResourcePolicyResponse = getPutResourcePolicyResponse();
        when(mockProxyClient.client().putResourcePolicy(any(PutResourcePolicyRequest.class))).thenReturn(putResourcePolicyResponse);

        // Mock exception for UntagResource
        when(mockProxyClient.client().untagResource(any(UntagResourceRequest.class))).thenThrow(ConstraintViolationException.class);

        final ProgressEvent<ResourceModel, CallbackContext> response = updateHandler.handleRequest(mockAwsClientProxy, request, new CallbackContext(), mockProxyClient, logger);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.FAILED);
        assertThat(response.getResourceModels()).isNull();
        assertThat(response.getErrorCode()).isEqualTo(HandlerErrorCode.ServiceLimitExceeded);

        verify(mockProxyClient.client()).putResourcePolicy(any(PutResourcePolicyRequest.class));
        verify(mockProxyClient.client()).untagResource(any(UntagResourceRequest.class));

        verify(mockOrgsClient, atLeastOnce()).serviceName();
        verifyNoMoreInteractions(mockOrgsClient);
    }

    @Test
    public void handleRequest_WithTags_TagResourceFails_With_CfnServiceLimitExceeded() {
        final ResourceModel initialResourceModel = generateFinalResourceModel(true, TEST_RESOURCEPOLICY_CONTENT);
        final ResourceModel updatedResourceModel = generateUpdatedResourceModel(true, TEST_RESOURCEPOLICY_UPDATED_CONTENT);

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
            .previousResourceState(initialResourceModel)
            .desiredResourceState(updatedResourceModel)
            .build();

        final PutResourcePolicyResponse putResourcePolicyResponse = getPutResourcePolicyResponse();
        when(mockProxyClient.client().putResourcePolicy(any(PutResourcePolicyRequest.class))).thenReturn(putResourcePolicyResponse);

        // Mock exception for TagResource
        when(mockProxyClient.client().tagResource(any(TagResourceRequest.class))).thenThrow(ConstraintViolationException.class);

        final ProgressEvent<ResourceModel, CallbackContext> response = updateHandler.handleRequest(mockAwsClientProxy, request, new CallbackContext(), mockProxyClient, logger);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.FAILED);
        assertThat(response.getResourceModels()).isNull();
        assertThat(response.getErrorCode()).isEqualTo(HandlerErrorCode.ServiceLimitExceeded);

        verify(mockProxyClient.client()).putResourcePolicy(any(PutResourcePolicyRequest.class));
        verify(mockProxyClient.client()).untagResource(any(UntagResourceRequest.class));
        verify(mockProxyClient.client()).tagResource(any(TagResourceRequest.class));

        verify(mockOrgsClient, atLeastOnce()).serviceName();
        verifyNoMoreInteractions(mockOrgsClient);
    }

    @Test
    public void handleRequest_Fails_With_ConcurrentModificationException() {
        retriableExceptionTest(ConcurrentModificationException.class, HandlerErrorCode.ResourceConflict);
    }

    @Test
    public void handleRequest_Fails_With_ServiceException() {
        retriableExceptionTest(ServiceException.class, HandlerErrorCode.ServiceInternalError);
    }

    @Test
    public void handleRequest_Fails_With_TooManyRequestsException() {
        retriableExceptionTest(TooManyRequestsException.class, HandlerErrorCode.Throttling);
    }

    @Test
    public void handleRequest_Fails_With_UnsupportedApiEndpointException() {
        nonRetriableExceptionTest(UnsupportedApiEndpointException.class, HandlerErrorCode.InvalidRequest);
    }

    @Test
    public void handleRequest_Fails_With_AccessDeniedException() {
        nonRetriableExceptionTest(AccessDeniedException.class, HandlerErrorCode.AccessDenied);
    }

    @Test
    public void handleRequest_Fails_With_InvalidInputException() {
        nonRetriableExceptionTest(InvalidInputException.class, HandlerErrorCode.InvalidRequest);
    }

    @Test
    public void handleRequest_Fails_With_ConstraintViolationException() {
        nonRetriableExceptionTest(ConstraintViolationException.class, HandlerErrorCode.ServiceLimitExceeded);
    }

    private void mockReadHandler(Boolean tags){
        final DescribeResourcePolicyResponse describeResourcePolicyResponse = getDescribeResourcePolicyResponse();
        when(mockProxyClient.client().describeResourcePolicy(any(DescribeResourcePolicyRequest.class))).thenReturn(describeResourcePolicyResponse);

        final ListTagsForResourceResponse listTagsResponse = tags ? TagTestResourceHelper.buildUpdatedTagsResponse() : TagTestResourceHelper.buildEmptyTagsResponse();
        when(mockProxyClient.client().listTagsForResource(any(ListTagsForResourceRequest.class))).thenReturn(listTagsResponse);
    }

    private void verifyReadHandler(){
        verify(mockProxyClient.client()).listTagsForResource(any(ListTagsForResourceRequest.class));
        verify(mockProxyClient.client()).describeResourcePolicy(any(DescribeResourcePolicyRequest.class));
    }

    private void retriableExceptionTest(Class<? extends Exception> e, HandlerErrorCode errorCode) {
        final ResourceModel initialResourceModel = generateFinalResourceModel(false, TEST_RESOURCEPOLICY_CONTENT);
        final ResourceModel updatedResourceModel = generateUpdatedResourceModel(false, TEST_RESOURCEPOLICY_UPDATED_CONTENT);

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
            .previousResourceState(initialResourceModel)
            .desiredResourceState(updatedResourceModel)
            .build();

        when(mockProxyClient.client().putResourcePolicy(any(PutResourcePolicyRequest.class))).thenThrow(e);
        CallbackContext context = new CallbackContext();
        ProgressEvent<ResourceModel, CallbackContext> response = updateHandler.handleRequest(mockAwsClientProxy, request, context, mockProxyClient, logger);

        // Retry attempt 1
        assertThat(context.getCurrentRetryAttempt(Action.UPDATE_RESOURCEPOLICY, Handler.UPDATE)).isEqualTo(1);
        assertThat(response.getStatus()).isEqualTo(OperationStatus.IN_PROGRESS);
        assertThat(response.getCallbackDelaySeconds()).isGreaterThan(0);

        // Retry attempt 2
        response = updateHandler.handleRequest(mockAwsClientProxy, request, context, mockProxyClient, logger);
        assertThat(context.getCurrentRetryAttempt(Action.UPDATE_RESOURCEPOLICY, Handler.UPDATE)).isEqualTo(2);
        assertThat(response.getStatus()).isEqualTo(OperationStatus.IN_PROGRESS);
        assertThat(response.getCallbackDelaySeconds()).isGreaterThan(0);

        response = updateHandler.handleRequest(mockAwsClientProxy, request, context, mockProxyClient, logger);
        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.FAILED);
        assertThat(response.getCallbackDelaySeconds()).isEqualTo(0);
        assertThat(response.getResourceModels()).isNull();
        assertThat(response.getResourceModel()).isEqualTo(request.getDesiredResourceState());
        assertThat(response.getErrorCode()).isEqualTo(errorCode);

        verify(mockProxyClient.client(), atLeast(3)).putResourcePolicy(any(PutResourcePolicyRequest.class));
    }

    private void nonRetriableExceptionTest(Class<? extends Exception> e, HandlerErrorCode errorCode) {
        final ResourceModel initialResourceModel = generateFinalResourceModel(false, TEST_RESOURCEPOLICY_CONTENT);
        final ResourceModel updatedResourceModel = generateUpdatedResourceModel(false, TEST_RESOURCEPOLICY_UPDATED_CONTENT);

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
            .previousResourceState(initialResourceModel)
            .desiredResourceState(updatedResourceModel)
            .build();

        when(mockProxyClient.client().putResourcePolicy(any(PutResourcePolicyRequest.class))).thenThrow(e);

        final ProgressEvent<ResourceModel, CallbackContext> response = updateHandler.handleRequest(mockAwsClientProxy, request, new CallbackContext(), mockProxyClient, logger);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.FAILED);
        assertThat(response.getResourceModels()).isNull();
        assertThat(response.getErrorCode()).isEqualTo(errorCode);

        verify(mockProxyClient.client()).putResourcePolicy(any(PutResourcePolicyRequest.class));

        verify(mockOrgsClient, atLeastOnce()).serviceName();
        verifyNoMoreInteractions(mockOrgsClient);
    }

}
