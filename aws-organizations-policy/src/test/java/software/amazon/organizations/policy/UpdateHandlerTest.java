package software.amazon.organizations.policy;

import software.amazon.awssdk.services.organizations.OrganizationsClient;
import software.amazon.awssdk.services.organizations.model.AttachPolicyRequest;
import software.amazon.awssdk.services.organizations.model.ConstraintViolationException;
import software.amazon.awssdk.services.organizations.model.DescribePolicyRequest;
import software.amazon.awssdk.services.organizations.model.DescribePolicyResponse;
import software.amazon.awssdk.services.organizations.model.DetachPolicyRequest;
import software.amazon.awssdk.services.organizations.model.DuplicatePolicyAttachmentException;
import software.amazon.awssdk.services.organizations.model.ListTagsForResourceRequest;
import software.amazon.awssdk.services.organizations.model.ListTagsForResourceResponse;
import software.amazon.awssdk.services.organizations.model.ListTargetsForPolicyRequest;
import software.amazon.awssdk.services.organizations.model.ListTargetsForPolicyResponse;
import software.amazon.awssdk.services.organizations.model.Policy;
import software.amazon.awssdk.services.organizations.model.PolicyNotAttachedException;
import software.amazon.awssdk.services.organizations.model.PolicyNotFoundException;
import software.amazon.awssdk.services.organizations.model.PolicySummary;
import software.amazon.awssdk.services.organizations.model.PolicyTargetSummary;
import software.amazon.awssdk.services.organizations.model.Tag;
import software.amazon.awssdk.services.organizations.model.TagResourceRequest;
import software.amazon.awssdk.services.organizations.model.TargetNotFoundException;
import software.amazon.awssdk.services.organizations.model.UntagResourceRequest;
import software.amazon.awssdk.services.organizations.model.UpdatePolicyRequest;
import software.amazon.awssdk.services.organizations.model.UpdatePolicyResponse;
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;


@ExtendWith(MockitoExtension.class)
public class UpdateHandlerTest extends AbstractTestBase {

    @Mock
    private AmazonWebServicesClientProxy mockAwsClientproxy;

    @Mock
    private ProxyClient<OrganizationsClient> mockProxyClient;

    @Mock
    OrganizationsClient mockOrgsClient;

    private UpdateHandler updateHandlerToTest;

    @BeforeEach
    public void setup() {
        updateHandlerToTest = new UpdateHandler();
        mockAwsClientproxy = new AmazonWebServicesClientProxy(logger, MOCK_CREDENTIALS, () -> Duration.ofSeconds(600).toMillis());
        mockOrgsClient = mock(OrganizationsClient.class);
        mockProxyClient = MOCK_PROXY(mockAwsClientproxy, mockOrgsClient);
    }

    @Test
    public void handleRequest_SimpleSuccess() {
        final ResourceModel initialResourceModel = generateFinalResourceModel(false, false);
        final ResourceModel updatedResourceModel = generateUpdatedResourceModel(false, false);

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
            .previousResourceState(initialResourceModel)
            .desiredResourceState(updatedResourceModel)
            .build();

        final UpdatePolicyResponse updatePolicyResponse = getUpdatePolicyResponse();
        when(mockProxyClient.client().updatePolicy(any(UpdatePolicyRequest.class))).thenReturn(updatePolicyResponse);

        final DescribePolicyResponse describePolicyResponse = getDescribePolicyResponse();
        when(mockProxyClient.client().describePolicy(any(DescribePolicyRequest.class))).thenReturn(describePolicyResponse);

        final ListTargetsForPolicyResponse listTargetsResponse = ListTargetsForPolicyResponse.builder()
            .targets(new ArrayList<>())
            .nextToken(null)
            .build();

        when(mockProxyClient.client().listTargetsForPolicy(any(ListTargetsForPolicyRequest.class))).thenReturn(listTargetsResponse);

        final ListTagsForResourceResponse listTagsResponse = TagTestResourceHelper.buildEmptyTagsResponse();
        when(mockProxyClient.client().listTagsForResource(any(ListTagsForResourceRequest.class))).thenReturn(listTagsResponse);

        final ProgressEvent<ResourceModel, CallbackContext> response = updateHandlerToTest.handleRequest(mockAwsClientproxy, request, new CallbackContext(), mockProxyClient, logger);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.SUCCESS);
        assertThat(response.getCallbackContext()).isNull();
        assertThat(response.getCallbackDelaySeconds()).isEqualTo(0);
        assertThat(response.getResourceModel()).isEqualTo(request.getDesiredResourceState());
        assertThat(response.getResourceModels()).isNull();
        assertThat(response.getMessage()).isNull();
        assertThat(response.getErrorCode()).isNull();

        verify(mockProxyClient.client()).updatePolicy(any(UpdatePolicyRequest.class));
        verify(mockProxyClient.client()).describePolicy(any(DescribePolicyRequest.class));
        verify(mockProxyClient.client()).listTargetsForPolicy(any(ListTargetsForPolicyRequest.class));
        verify(mockProxyClient.client()).listTagsForResource(any(ListTagsForResourceRequest.class));

        verify(mockOrgsClient, atLeastOnce()).serviceName();
        verifyNoMoreInteractions(mockOrgsClient);
    }

    @Test
    public void handleRequest_WithTargets_SimpleSuccess() {
        final ResourceModel initialResourceModel = generateFinalResourceModel(true, false);
        final ResourceModel updatedResourceModel = generateUpdatedResourceModel(true, false);

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
            .previousResourceState(initialResourceModel)
            .desiredResourceState(updatedResourceModel)
            .build();

        final UpdatePolicyResponse updatePolicyResponse = getUpdatePolicyResponse();
        when(mockProxyClient.client().updatePolicy(any(UpdatePolicyRequest.class))).thenReturn(updatePolicyResponse);

        final DescribePolicyResponse describePolicyResponse = getDescribePolicyResponse();
        when(mockProxyClient.client().describePolicy(any(DescribePolicyRequest.class))).thenReturn(describePolicyResponse);

        final PolicyTargetSummary rootTargetSummary = getPolicyTargetSummaryWithTargetId(TEST_TARGET_ROOT_ID);
        final PolicyTargetSummary accountTargetSummary = getPolicyTargetSummaryWithTargetId(TEST_TARGET_ACCOUNT_ID);
        final ListTargetsForPolicyResponse listTargetsResponse = ListTargetsForPolicyResponse.builder()
            .targets(Arrays.asList(rootTargetSummary, accountTargetSummary))
            .nextToken(null)
            .build();

        when(mockProxyClient.client().listTargetsForPolicy(any(ListTargetsForPolicyRequest.class))).thenReturn(listTargetsResponse);

        final ListTagsForResourceResponse listTagsResponse = TagTestResourceHelper.buildEmptyTagsResponse();
        when(mockProxyClient.client().listTagsForResource(any(ListTagsForResourceRequest.class))).thenReturn(listTagsResponse);

        final ProgressEvent<ResourceModel, CallbackContext> response = updateHandlerToTest.handleRequest(mockAwsClientproxy, request, new CallbackContext(), mockProxyClient, logger);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.SUCCESS);
        assertThat(response.getCallbackContext()).isNull();
        assertThat(response.getCallbackDelaySeconds()).isEqualTo(0);
        assertThat(response.getResourceModel()).isEqualTo(request.getDesiredResourceState());
        assertThat(response.getResourceModels()).isNull();
        assertThat(response.getMessage()).isNull();
        assertThat(response.getErrorCode()).isNull();

        verify(mockProxyClient.client()).updatePolicy(any(UpdatePolicyRequest.class));
        verify(mockProxyClient.client()).detachPolicy(any(DetachPolicyRequest.class));
        verify(mockProxyClient.client()).attachPolicy(any(AttachPolicyRequest.class));
        verify(mockProxyClient.client()).describePolicy(any(DescribePolicyRequest.class));
        verify(mockProxyClient.client()).listTargetsForPolicy(any(ListTargetsForPolicyRequest.class));
        verify(mockProxyClient.client()).listTagsForResource(any(ListTagsForResourceRequest.class));

        verify(mockOrgsClient, atLeastOnce()).serviceName();
        verifyNoMoreInteractions(mockOrgsClient);
    }

    @Test
    public void handleRequest_WithTargets_TargetsChangedOutOfBand_SimpleSuccess() {
        final ResourceModel initialResourceModel = generateFinalResourceModel(true, false);
        final ResourceModel updatedResourceModel = generateUpdatedResourceModel(true, false);

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
            .previousResourceState(initialResourceModel)
            .desiredResourceState(updatedResourceModel)
            .build();

        final UpdatePolicyResponse updatePolicyResponse = getUpdatePolicyResponse();
        when(mockProxyClient.client().updatePolicy(any(UpdatePolicyRequest.class))).thenReturn(updatePolicyResponse);

        //mock exceptions for AttachPolicy and DetachPolicy calls
        when(mockProxyClient.client().attachPolicy(any(AttachPolicyRequest.class))).thenThrow(DuplicatePolicyAttachmentException.class);
        when(mockProxyClient.client().detachPolicy((any(DetachPolicyRequest.class)))).thenThrow(PolicyNotAttachedException.class);

        final DescribePolicyResponse describePolicyResponse = getDescribePolicyResponse();
        when(mockProxyClient.client().describePolicy(any(DescribePolicyRequest.class))).thenReturn(describePolicyResponse);

        final PolicyTargetSummary rootTargetSummary = getPolicyTargetSummaryWithTargetId(TEST_TARGET_ROOT_ID);
        final PolicyTargetSummary accountTargetSummary = getPolicyTargetSummaryWithTargetId(TEST_TARGET_ACCOUNT_ID);
        final ListTargetsForPolicyResponse listTargetsResponse = ListTargetsForPolicyResponse.builder()
            .targets(Arrays.asList(rootTargetSummary, accountTargetSummary))
            .nextToken(null)
            .build();

        when(mockProxyClient.client().listTargetsForPolicy(any(ListTargetsForPolicyRequest.class))).thenReturn(listTargetsResponse);

        final ListTagsForResourceResponse listTagsResponse = TagTestResourceHelper.buildEmptyTagsResponse();
        when(mockProxyClient.client().listTagsForResource(any(ListTagsForResourceRequest.class))).thenReturn(listTagsResponse);

        final ProgressEvent<ResourceModel, CallbackContext> response = updateHandlerToTest.handleRequest(mockAwsClientproxy, request, new CallbackContext(), mockProxyClient, logger);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.SUCCESS);
        assertThat(response.getCallbackContext()).isNull();
        assertThat(response.getCallbackDelaySeconds()).isEqualTo(0);
        assertThat(response.getResourceModel()).isEqualTo(request.getDesiredResourceState());
        assertThat(response.getResourceModels()).isNull();
        assertThat(response.getMessage()).isNull();
        assertThat(response.getErrorCode()).isNull();

        verify(mockProxyClient.client()).updatePolicy(any(UpdatePolicyRequest.class));
        verify(mockProxyClient.client()).detachPolicy(any(DetachPolicyRequest.class));
        verify(mockProxyClient.client()).attachPolicy(any(AttachPolicyRequest.class));
        verify(mockProxyClient.client()).describePolicy(any(DescribePolicyRequest.class));
        verify(mockProxyClient.client()).listTargetsForPolicy(any(ListTargetsForPolicyRequest.class));
        verify(mockProxyClient.client()).listTagsForResource(any(ListTagsForResourceRequest.class));

        verify(mockOrgsClient, atLeastOnce()).serviceName();
        verifyNoMoreInteractions(mockOrgsClient);
    }

    @Test
    public void handleRequest_WithTags_SimpleSuccess() {
        final ResourceModel initialResourceModel = generateFinalResourceModel(false, true);
        final ResourceModel updatedResourceModel = generateUpdatedResourceModel(false, true);

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
            .previousResourceState(initialResourceModel)
            .desiredResourceState(updatedResourceModel)
            .build();

        final UpdatePolicyResponse updatePolicyResponse = getUpdatePolicyResponse();
        when(mockProxyClient.client().updatePolicy(any(UpdatePolicyRequest.class))).thenReturn(updatePolicyResponse);

        final DescribePolicyResponse describePolicyResponse = getDescribePolicyResponse();
        when(mockProxyClient.client().describePolicy(any(DescribePolicyRequest.class))).thenReturn(describePolicyResponse);

        final ListTargetsForPolicyResponse listTargetsResponse = ListTargetsForPolicyResponse.builder()
            .targets(new ArrayList<>())
            .nextToken(null)
            .build();

        when(mockProxyClient.client().listTargetsForPolicy(any(ListTargetsForPolicyRequest.class))).thenReturn(listTargetsResponse);

        final ListTagsForResourceResponse listTagsResponse = TagTestResourceHelper.buildUpdatedTagsResponse();
        when(mockProxyClient.client().listTagsForResource(any(ListTagsForResourceRequest.class))).thenReturn(listTagsResponse);

        final ProgressEvent<ResourceModel, CallbackContext> response = updateHandlerToTest.handleRequest(mockAwsClientproxy, request, new CallbackContext(), mockProxyClient, logger);

        final Collection<Tag> tagsToAddOrUpdate = UpdateHandler.getTagsToAddOrUpdate(
            TagTestResourceHelper.translatePolicyTagsToOrganizationTags(initialResourceModel.getTags()),
            TagTestResourceHelper.translatePolicyTagsToOrganizationTags(updatedResourceModel.getTags()));

        final List<String> tagKeysToRemove = UpdateHandler.getTagsToRemove(
            TagTestResourceHelper.translatePolicyTagsToOrganizationTags(initialResourceModel.getTags()),
            TagTestResourceHelper.translatePolicyTagsToOrganizationTags(updatedResourceModel.getTags())
        );

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.SUCCESS);
        assertThat(response.getCallbackContext()).isNull();
        assertThat(response.getCallbackDelaySeconds()).isEqualTo(0);
        assertThat(response.getResourceModel()).isEqualTo(request.getDesiredResourceState());
        assertThat(response.getResourceModels()).isNull();
        assertThat(TagTestResourceHelper.tagsEqual(response.getResourceModel().getTags(), TagTestResourceHelper.updatedTags));
        assertThat(TagTestResourceHelper.correctTagsInTagAndUntagRequests(tagsToAddOrUpdate, tagKeysToRemove));
        assertThat(response.getMessage()).isNull();
        assertThat(response.getErrorCode()).isNull();

        verify(mockProxyClient.client()).updatePolicy(any(UpdatePolicyRequest.class));
        verify(mockProxyClient.client()).tagResource(any(TagResourceRequest.class));
        verify(mockProxyClient.client()).untagResource(any(UntagResourceRequest.class));
        verify(mockProxyClient.client()).describePolicy(any(DescribePolicyRequest.class));
        verify(mockProxyClient.client()).listTargetsForPolicy(any(ListTargetsForPolicyRequest.class));
        verify(mockProxyClient.client()).listTagsForResource(any(ListTagsForResourceRequest.class));

        verify(mockOrgsClient, atLeastOnce()).serviceName();
        verifyNoMoreInteractions(mockOrgsClient);
    }

    @Test
    public void handleRequest_WithTargets_AttachPolicyFailsWithTargetNotFound_With_CfnNotFoundException() {
        final ResourceModel initialResourceModel = generateFinalResourceModel(true, false);
        final ResourceModel updatedResourceModel = generateUpdatedResourceModel(true, false);

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
            .previousResourceState(initialResourceModel)
            .desiredResourceState(updatedResourceModel)
            .build();

        final UpdatePolicyResponse updatePolicyResponse = getUpdatePolicyResponse();
        when(mockProxyClient.client().updatePolicy(any(UpdatePolicyRequest.class))).thenReturn(updatePolicyResponse);

        //mock exception for AttachPolicy
        when(mockProxyClient.client().attachPolicy(any(AttachPolicyRequest.class))).thenThrow(TargetNotFoundException.class);

        final ProgressEvent<ResourceModel, CallbackContext> response = updateHandlerToTest.handleRequest(mockAwsClientproxy, request, new CallbackContext(), mockProxyClient, logger);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.FAILED);
        assertThat(response.getResourceModels()).isNull();
        assertThat(response.getErrorCode()).isEqualTo(HandlerErrorCode.NotFound);

        verify(mockProxyClient.client()).updatePolicy(any(UpdatePolicyRequest.class));
        verify(mockProxyClient.client()).attachPolicy(any(AttachPolicyRequest.class));

        verify(mockOrgsClient, atLeastOnce()).serviceName();
        verifyNoMoreInteractions(mockOrgsClient);
    }

    @Test
    public void handleRequest_WithTargets_DetachPolicyFailsWithTargetNotFound_With_CfnNotFoundException() {
        final ResourceModel initialResourceModel = generateFinalResourceModel(true, false);
        final ResourceModel updatedResourceModel = generateUpdatedResourceModel(true, false);

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
            .previousResourceState(initialResourceModel)
            .desiredResourceState(updatedResourceModel)
            .build();

        final UpdatePolicyResponse updatePolicyResponse = getUpdatePolicyResponse();
        when(mockProxyClient.client().updatePolicy(any(UpdatePolicyRequest.class))).thenReturn(updatePolicyResponse);

        //mock exception for DetachPolicy
        when(mockProxyClient.client().detachPolicy(any(DetachPolicyRequest.class))).thenThrow(TargetNotFoundException.class);

        final ProgressEvent<ResourceModel, CallbackContext> response = updateHandlerToTest.handleRequest(mockAwsClientproxy, request, new CallbackContext(), mockProxyClient, logger);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.FAILED);
        assertThat(response.getResourceModels()).isNull();
        assertThat(response.getErrorCode()).isEqualTo(HandlerErrorCode.NotFound);

        verify(mockProxyClient.client()).updatePolicy(any(UpdatePolicyRequest.class));
        verify(mockProxyClient.client()).attachPolicy(any(AttachPolicyRequest.class));

        verify(mockOrgsClient, atLeastOnce()).serviceName();
        verifyNoMoreInteractions(mockOrgsClient);
    }

    @Test
    public void handleRequest_WithTags_UntagResourceFails_With_CfnServiceLimitExceeded() {
        final ResourceModel initialResourceModel = generateFinalResourceModel(false, true);
        final ResourceModel updatedResourceModel = generateUpdatedResourceModel(false, true);

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
            .previousResourceState(initialResourceModel)
            .desiredResourceState(updatedResourceModel)
            .build();

        final UpdatePolicyResponse updatePolicyResponse = getUpdatePolicyResponse();
        when(mockProxyClient.client().updatePolicy(any(UpdatePolicyRequest.class))).thenReturn(updatePolicyResponse);

        //mock exception for UntagResource
        when(mockProxyClient.client().untagResource(any(UntagResourceRequest.class))).thenThrow(ConstraintViolationException.class);

        final ProgressEvent<ResourceModel, CallbackContext> response = updateHandlerToTest.handleRequest(mockAwsClientproxy, request, new CallbackContext(), mockProxyClient, logger);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.FAILED);
        assertThat(response.getResourceModels()).isNull();
        assertThat(response.getErrorCode()).isEqualTo(HandlerErrorCode.ServiceLimitExceeded);

        verify(mockProxyClient.client()).updatePolicy(any(UpdatePolicyRequest.class));
        verify(mockProxyClient.client()).untagResource(any(UntagResourceRequest.class));

        verify(mockOrgsClient, atLeastOnce()).serviceName();
        verifyNoMoreInteractions(mockOrgsClient);
    }

    @Test
    public void handleRequest_WithTags_TagResourceFails_With_CfnServiceLimitExceeded() {
        final ResourceModel initialResourceModel = generateFinalResourceModel(false, true);
        final ResourceModel updatedResourceModel = generateUpdatedResourceModel(false, true);

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
            .previousResourceState(initialResourceModel)
            .desiredResourceState(updatedResourceModel)
            .build();

        final UpdatePolicyResponse updatePolicyResponse = getUpdatePolicyResponse();
        when(mockProxyClient.client().updatePolicy(any(UpdatePolicyRequest.class))).thenReturn(updatePolicyResponse);

        //mock exception for TagResource
        when(mockProxyClient.client().tagResource(any(TagResourceRequest.class))).thenThrow(ConstraintViolationException.class);

        final ProgressEvent<ResourceModel, CallbackContext> response = updateHandlerToTest.handleRequest(mockAwsClientproxy, request, new CallbackContext(), mockProxyClient, logger);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.FAILED);
        assertThat(response.getResourceModels()).isNull();
        assertThat(response.getErrorCode()).isEqualTo(HandlerErrorCode.ServiceLimitExceeded);

        verify(mockProxyClient.client()).updatePolicy(any(UpdatePolicyRequest.class));
        verify(mockProxyClient.client()).untagResource(any(UntagResourceRequest.class));
        verify(mockProxyClient.client()).tagResource(any(TagResourceRequest.class));

        verify(mockOrgsClient, atLeastOnce()).serviceName();
        verifyNoMoreInteractions(mockOrgsClient);
    }

    @Test
    public void handleRequest_Fails_WithCfnNotFoundException() {
        final ResourceModel initialResourceModel = generateFinalResourceModel(false, false);
        final ResourceModel updatedResourceModel = generateUpdatedResourceModel(false, false);
        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
            .previousResourceState(initialResourceModel)
            .desiredResourceState(updatedResourceModel)
            .build();

        when(mockProxyClient.client().updatePolicy(any(UpdatePolicyRequest.class))).thenThrow(PolicyNotFoundException.class);

        final ProgressEvent<ResourceModel, CallbackContext> response = updateHandlerToTest.handleRequest(mockAwsClientproxy, request, new CallbackContext(), mockProxyClient, logger);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.FAILED);
        assertThat(response.getResourceModels()).isNull();
        assertThat(response.getErrorCode()).isEqualTo(HandlerErrorCode.NotFound);

        verify(mockOrgsClient, atLeastOnce()).serviceName();
        verifyNoMoreInteractions(mockOrgsClient);
    }

    @Test
    public void handleRequest_IdChanged_Fails_WithCfnNotFoundException() {
        final ResourceModel previousResourceModel = generateFinalResourceModel(false, false);
        final ResourceModel desiredResourceModel = ResourceModel.builder()
            .name(TEST_POLICY_UPDATED_NAME)
            .id(TEST_POLICY_ID_CHANGED)
            .build();

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
            .previousResourceState(previousResourceModel)
            .desiredResourceState(desiredResourceModel)
            .build();

        final ProgressEvent<ResourceModel, CallbackContext> response = updateHandlerToTest.handleRequest(mockAwsClientproxy, request, new CallbackContext(), mockProxyClient, logger);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.FAILED);
        assertThat(response.getResourceModels()).isNull();
        assertThat(response.getErrorCode()).isEqualTo(HandlerErrorCode.NotFound);
    }

    @Test
    public void handleRequest_PreviousModelNull_Fails_WithCfnNotFoundException() {
        final ResourceModel desiredResourceModel = ResourceModel.builder()
            .name(TEST_POLICY_UPDATED_NAME)
            .id(TEST_POLICY_ID_CHANGED)
            .build();

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
            .previousResourceState(null)
            .desiredResourceState(desiredResourceModel)
            .build();

        final ProgressEvent<ResourceModel, CallbackContext> response = updateHandlerToTest.handleRequest(mockAwsClientproxy, request, new CallbackContext(), mockProxyClient, logger);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.FAILED);
        assertThat(response.getResourceModels()).isNull();
        assertThat(response.getErrorCode()).isEqualTo(HandlerErrorCode.NotFound);
    }

    @Test
    public void handleRequest_PreviousModelIdNull_Fails_WithCfnNotFoundException() {
        final ResourceModel previousResourceModel = ResourceModel.builder()
            .id(null)
            .name(TEST_POLICY_NAME)
            .build();

        final ResourceModel desiredResourceModel = ResourceModel.builder()
            .name(TEST_POLICY_UPDATED_NAME)
            .id(TEST_POLICY_ID_CHANGED)
            .build();

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
            .previousResourceState(previousResourceModel)
            .desiredResourceState(desiredResourceModel)
            .build();

        final ProgressEvent<ResourceModel, CallbackContext> response = updateHandlerToTest.handleRequest(mockAwsClientproxy, request, new CallbackContext(), mockProxyClient, logger);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.FAILED);
        assertThat(response.getResourceModels()).isNull();
        assertThat(response.getErrorCode()).isEqualTo(HandlerErrorCode.NotFound);
    }

    @Test
    public void handleRequest_PreviousModelNewModelTypeMismatch_Fails_WithCfnNotUpdatableException() {
        final ResourceModel previousResourceModel = ResourceModel.builder()
            .id(TEST_POLICY_ID)
            .name(TEST_POLICY_NAME)
            .type(TEST_TYPE)
            .build();

        final ResourceModel desiredResourceModel = ResourceModel.builder()
            .id(TEST_POLICY_ID)
            .name(TEST_POLICY_UPDATED_NAME)
            .type(TEST_TYPE_CHANGED)
            .build();

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
            .previousResourceState(previousResourceModel)
            .desiredResourceState(desiredResourceModel)
            .build();

        final ProgressEvent<ResourceModel, CallbackContext> response = updateHandlerToTest.handleRequest(mockAwsClientproxy, request, new CallbackContext(), mockProxyClient, logger);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.FAILED);
        assertThat(response.getResourceModels()).isNull();
        assertThat(response.getErrorCode()).isEqualTo(HandlerErrorCode.NotUpdatable);
    }

    protected UpdatePolicyResponse getUpdatePolicyResponse() {
        return UpdatePolicyResponse.builder().policy(
            Policy.builder()
                .content(TEST_POLICY_UPDATED_CONTENT)
                .policySummary(PolicySummary.builder()
                    .id(TEST_POLICY_ID)
                    .arn(TEST_POLICY_ARN)
                    .awsManaged(TEST_AWSMANAGED)
                    .name(TEST_POLICY_UPDATED_NAME)
                    .description(TEST_POLICY_UPDATED_DESCRIPTION)
                    .type(TEST_TYPE)
                    .build())
            .build())
        .build();
    }
}
