package software.amazon.organizations.policy;

import java.time.Duration;
import java.util.Arrays;
import java.util.HashSet;

import software.amazon.awssdk.services.organizations.OrganizationsClient;
import software.amazon.awssdk.services.organizations.model.AttachPolicyRequest;
import software.amazon.awssdk.services.organizations.model.AttachPolicyResponse;
import software.amazon.awssdk.services.organizations.model.CreatePolicyRequest;
import software.amazon.awssdk.services.organizations.model.CreatePolicyResponse;
import software.amazon.awssdk.services.organizations.model.ConcurrentModificationException;
import software.amazon.awssdk.services.organizations.model.DescribePolicyRequest;
import software.amazon.awssdk.services.organizations.model.DescribePolicyResponse;
import software.amazon.awssdk.services.organizations.model.DuplicatePolicyException;
import software.amazon.awssdk.services.organizations.model.ListTagsForResourceRequest;
import software.amazon.awssdk.services.organizations.model.ListTagsForResourceResponse;
import software.amazon.awssdk.services.organizations.model.ListTargetsForPolicyRequest;
import software.amazon.awssdk.services.organizations.model.ListTargetsForPolicyResponse;
import software.amazon.awssdk.services.organizations.model.Policy;
import software.amazon.awssdk.services.organizations.model.PolicySummary;
import software.amazon.awssdk.services.organizations.model.PolicyTargetSummary;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.atMost;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
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
    public void handleRequest_NoTargetsNoTags_SimpleRequest() {
        final ResourceModel model = generateInitialResourceModel(false, false);

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
            .desiredResourceState(model)
            .build();

        final CreatePolicyResponse createPolicyResponse = getCreatePolicyResponse();
        when(mockProxyClient.client().createPolicy(any(CreatePolicyRequest.class))).thenReturn(createPolicyResponse);

        final DescribePolicyResponse describePolicyResponse = getDescribePolicyResponse();
        when(mockProxyClient.client().describePolicy(any(DescribePolicyRequest.class))).thenReturn(describePolicyResponse);

        final ListTargetsForPolicyResponse listTargetsResponse = ListTargetsForPolicyResponse.builder()
            .nextToken(null)
            .build();
        when(mockProxyClient.client().listTargetsForPolicy(any(ListTargetsForPolicyRequest.class))).thenReturn(listTargetsResponse);

        final ListTagsForResourceResponse listTagsResponse = TagTestResourceHelper.buildEmptyTagsResponse();
        when(mockProxyClient.client().listTagsForResource(any(ListTagsForResourceRequest.class))).thenReturn(listTagsResponse);

        final ProgressEvent<ResourceModel, CallbackContext> response = createHandler.handleRequest(mockAwsClientProxy, request, new CallbackContext(), mockProxyClient, logger);

        final ResourceModel finalModel = ResourceModel.builder()
            .targetIds(new HashSet<>())
            .arn(TEST_POLICY_ARN)
            .description(TEST_POLICY_DESCRIPTION)
            .content(TEST_POLICY_CONTENT)
            .id(TEST_POLICY_ID)
            .name(TEST_POLICY_NAME)
            .type(TEST_TYPE)
            .awsManaged(TEST_AWSMANAGED)
            .tags(TagTestResourceHelper.translateOrganizationTagsToPolicyTags(null))
            .build();

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.SUCCESS);
        assertThat(response.getCallbackDelaySeconds()).isEqualTo(0);
        assertThat(response.getResourceModel()).isNotNull();
        assertThat(response.getResourceModel()).isEqualTo(finalModel);
        assertThat(response.getResourceModels()).isNull();
        assertThat(response.getMessage()).isNull();
        assertThat(response.getErrorCode()).isNull();

        verify(mockProxyClient.client()).createPolicy(any(CreatePolicyRequest.class));
        verify(mockProxyClient.client()).describePolicy(any(DescribePolicyRequest.class));
        verify(mockProxyClient.client()).listTargetsForPolicy(any(ListTargetsForPolicyRequest.class));
        verify(mockProxyClient.client()).listTagsForResource(any(ListTagsForResourceRequest.class));

        verify(mockOrgsClient, atLeastOnce()).serviceName();
        verifyNoMoreInteractions(mockOrgsClient);
    }

    @Test
    public void handleRequest_NoDescriptionNoTargetsNoTags_SimpleRequest() {
        final ResourceModel model = ResourceModel.builder()
            .targetIds(new HashSet<>())
            .tags(null)
            .description(null)
            .content(TEST_POLICY_CONTENT)
            .name(TEST_POLICY_NAME)
            .type(TEST_TYPE)
            .build();

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
            .desiredResourceState(model)
            .build();

        final CreatePolicyResponse createPolicyResponse = getCreatePolicyResponse();
        when(mockProxyClient.client().createPolicy(any(CreatePolicyRequest.class))).thenReturn(createPolicyResponse);

        final DescribePolicyResponse describePolicyResponse = getDescribePolicyResponse();
        when(mockProxyClient.client().describePolicy(any(DescribePolicyRequest.class))).thenReturn(describePolicyResponse);

        final ListTargetsForPolicyResponse listTargetsResponse = ListTargetsForPolicyResponse.builder()
            .nextToken(null)
            .build();
        when(mockProxyClient.client().listTargetsForPolicy(any(ListTargetsForPolicyRequest.class))).thenReturn(listTargetsResponse);

        final ListTagsForResourceResponse listTagsResponse = TagTestResourceHelper.buildEmptyTagsResponse();
        when(mockProxyClient.client().listTagsForResource(any(ListTagsForResourceRequest.class))).thenReturn(listTagsResponse);

        final ProgressEvent<ResourceModel, CallbackContext> response = createHandler.handleRequest(mockAwsClientProxy, request, new CallbackContext(), mockProxyClient, logger);

        final ResourceModel finalModel = ResourceModel.builder()
            .targetIds(new HashSet<>())
            .arn(TEST_POLICY_ARN)
            .description(TEST_POLICY_DESCRIPTION)
            .content(TEST_POLICY_CONTENT)
            .id(TEST_POLICY_ID)
            .name(TEST_POLICY_NAME)
            .type(TEST_TYPE)
            .awsManaged(TEST_AWSMANAGED)
            .tags(TagTestResourceHelper.translateOrganizationTagsToPolicyTags(null))
            .build();

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.SUCCESS);
        assertThat(response.getCallbackDelaySeconds()).isEqualTo(0);
        assertThat(response.getResourceModel()).isNotNull();
        assertThat(response.getResourceModel()).isEqualTo(finalModel);
        assertThat(response.getResourceModels()).isNull();
        assertThat(response.getMessage()).isNull();
        assertThat(response.getErrorCode()).isNull();

        verify(mockProxyClient.client()).createPolicy(any(CreatePolicyRequest.class));
        verify(mockProxyClient.client()).describePolicy(any(DescribePolicyRequest.class));
        verify(mockProxyClient.client()).listTargetsForPolicy(any(ListTargetsForPolicyRequest.class));
        verify(mockProxyClient.client()).listTagsForResource(any(ListTagsForResourceRequest.class));

        verify(mockOrgsClient, atLeastOnce()).serviceName();
        verifyNoMoreInteractions(mockOrgsClient);
    }

    @Test
    public void handleRequest_WithTargetsAndTags_SimpleSuccess() {
        final ResourceModel model = generateInitialResourceModel(true, true);

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
            .desiredResourceState(model)
            .build();

        final CreatePolicyResponse createPolicyResponse = getCreatePolicyResponse();
        when(mockProxyClient.client().createPolicy(any(CreatePolicyRequest.class))).thenReturn(createPolicyResponse);

        final AttachPolicyResponse attachPolicyResponse = AttachPolicyResponse.builder().build();
        when(mockProxyClient.client().attachPolicy(any(AttachPolicyRequest.class))).thenReturn(attachPolicyResponse);

        final DescribePolicyResponse describePolicyResponse = getDescribePolicyResponse();
        when(mockProxyClient.client().describePolicy(any(DescribePolicyRequest.class))).thenReturn(describePolicyResponse);

        final PolicyTargetSummary rootTargetSummary = getPolicyTargetSummaryWithTargetId(TEST_TARGET_ROOT_ID);
        final PolicyTargetSummary ouTargetSummary = getPolicyTargetSummaryWithTargetId(TEST_TARGET_OU_ID);
        final ListTargetsForPolicyResponse listTargetsResponse = ListTargetsForPolicyResponse.builder()
            .targets(Arrays.asList(rootTargetSummary, ouTargetSummary))
            .nextToken(null)
            .build();

        when(mockProxyClient.client().listTargetsForPolicy(any(ListTargetsForPolicyRequest.class))).thenReturn(listTargetsResponse);

        final ListTagsForResourceResponse listTagsResponse = TagTestResourceHelper.buildDefaultTagsResponse();
        when(mockProxyClient.client().listTagsForResource(any(ListTagsForResourceRequest.class))).thenReturn(listTagsResponse);

        final ProgressEvent<ResourceModel, CallbackContext> response = createHandler.handleRequest(mockAwsClientProxy, request, new CallbackContext(), mockProxyClient, logger);

        final ResourceModel finalModel = generateFinalResourceModel(true, true);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.SUCCESS);
        assertThat(response.getCallbackDelaySeconds()).isEqualTo(0);
        assertThat(response.getResourceModel()).isNotNull();
        assertThat(response.getResourceModel()).isEqualTo(finalModel);
        assertThat(response.getResourceModels()).isNull();
        assertThat(response.getMessage()).isNull();
        assertThat(response.getErrorCode()).isNull();

        verify(mockProxyClient.client()).createPolicy(any(CreatePolicyRequest.class));
        verify(mockProxyClient.client(), times(2)).attachPolicy(any(AttachPolicyRequest.class));
        verify(mockProxyClient.client()).describePolicy(any(DescribePolicyRequest.class));
        verify(mockProxyClient.client()).listTargetsForPolicy(any(ListTargetsForPolicyRequest.class));
        verify(mockProxyClient.client()).listTagsForResource(any(ListTagsForResourceRequest.class));

        verify(mockOrgsClient, atLeastOnce()).serviceName();
        verifyNoMoreInteractions(mockOrgsClient);
    }

    @Test
    public void handleRequest_WithTargetsAndTags_ConcurrentModificationExceptionInAttachPolicy_shouldSkipCreatePolicyInRetry() {
        final ResourceModel model = generateInitialResourceModel(true, true);

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
                                                                  .desiredResourceState(model)
                                                                  .build();

        final CreatePolicyResponse createPolicyResponse = getCreatePolicyResponse();
        when(mockProxyClient.client().createPolicy(any(CreatePolicyRequest.class))).thenReturn(createPolicyResponse);

        when(mockProxyClient.client().attachPolicy(any(AttachPolicyRequest.class))).thenThrow(ConcurrentModificationException.class);

        CallbackContext context = new CallbackContext();
        ProgressEvent<ResourceModel, CallbackContext> response = createHandler.handleRequest(mockAwsClientProxy, request, context, mockProxyClient, logger);
        // retry attempt 1
        assertThat(context.isPolicyCreated()).isEqualTo(true);
        assertThat(context.getRetryAttachAttempt()).isEqualTo(1);
        assertThat(response.getStatus()).isEqualTo(OperationStatus.IN_PROGRESS);
        assertThat(response.getCallbackDelaySeconds()).isGreaterThan(0);
        // retry attempt 2
        response = createHandler.handleRequest(mockAwsClientProxy, request, context, mockProxyClient, logger);
        assertThat(context.isPolicyCreated()).isEqualTo(true);
        assertThat(context.getRetryAttachAttempt()).isEqualTo(2);
        assertThat(response.getStatus()).isEqualTo(OperationStatus.IN_PROGRESS);
        assertThat(response.getCallbackDelaySeconds()).isGreaterThan(0);
        // retry attempt 3
        response = createHandler.handleRequest(mockAwsClientProxy, request, context, mockProxyClient, logger);
        assertThat(context.isPolicyCreated()).isEqualTo(true);
        assertThat(context.getRetryAttachAttempt()).isEqualTo(3);
        assertThat(response.getStatus()).isEqualTo(OperationStatus.IN_PROGRESS);
        assertThat(response.getCallbackDelaySeconds()).isGreaterThan(0);

        // CloudFormation retry
        response = createHandler.handleRequest(mockAwsClientProxy, request, context, mockProxyClient, logger);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.FAILED);
        assertThat(response.getCallbackDelaySeconds()).isEqualTo(0);
        assertThat(response.getResourceModel()).isNotNull();
        assertThat(response.getResourceModels()).isNull();

        // verify createPolicy is only invoked 1 time and attachPolicy invoked at least maxRetryCount times
        verify(mockProxyClient.client(), times(1)).createPolicy(any(CreatePolicyRequest.class));
        verify(mockProxyClient.client(), atLeast(3)).attachPolicy(any(AttachPolicyRequest.class));

        verify(mockOrgsClient, atLeastOnce()).serviceName();
        verifyNoMoreInteractions(mockOrgsClient);
    }

    @Test
    public void handleRequest_MissingRequiredValueName_Fails_With_InvalidRequest() {
        final ResourceModel model = ResourceModel.builder()
            .type(TEST_TYPE)
            .content(TEST_POLICY_CONTENT)
            .build();

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
            .desiredResourceState(model)
            .build();

        final ProgressEvent<ResourceModel, CallbackContext> response = createHandler.handleRequest(mockAwsClientProxy, request, new CallbackContext(), mockProxyClient, logger);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.FAILED);
        assertThat(response.getResourceModels()).isNull();
        assertThat(response.getErrorCode()).isEqualTo(HandlerErrorCode.InvalidRequest);
    }

    @Test
    public void handleRequest_MissingRequiredValueType_Fails_With_InvalidRequest() {
        final ResourceModel model = ResourceModel.builder()
            .name(TEST_POLICY_NAME)
            .content(TEST_POLICY_CONTENT)
            .build();

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
            .desiredResourceState(model)
            .build();

        final ProgressEvent<ResourceModel, CallbackContext> response = createHandler.handleRequest(mockAwsClientProxy, request, new CallbackContext(), mockProxyClient, logger);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.FAILED);
        assertThat(response.getResourceModels()).isNull();
        assertThat(response.getErrorCode()).isEqualTo(HandlerErrorCode.InvalidRequest);
    }

    @Test
    public void handleRequest_MissingRequiredValueContent_Fails_With_InvalidRequest() {
        final ResourceModel model = ResourceModel.builder()
            .name(TEST_POLICY_NAME)
            .type(TEST_TYPE)
            .build();

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
            .desiredResourceState(model)
            .build();

        final ProgressEvent<ResourceModel, CallbackContext> response = createHandler.handleRequest(mockAwsClientProxy, request, new CallbackContext(), mockProxyClient, logger);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.FAILED);
        assertThat(response.getResourceModels()).isNull();
        assertThat(response.getErrorCode()).isEqualTo(HandlerErrorCode.InvalidRequest);
    }

    @Test
    public void handleRequest_WithTargets_AttachPolicyFails_With_NotFound() {
        final ResourceModel model = generateInitialResourceModel(true, false);

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
            .desiredResourceState(model)
            .build();

        final CreatePolicyResponse createPolicyResponse = getCreatePolicyResponse();
        when(mockProxyClient.client().createPolicy(any(CreatePolicyRequest.class))).thenReturn(createPolicyResponse);

        when(mockProxyClient.client().attachPolicy(any(AttachPolicyRequest.class))).thenThrow(TargetNotFoundException.class);

        final ProgressEvent<ResourceModel, CallbackContext> response = createHandler.handleRequest(mockAwsClientProxy, request, new CallbackContext(), mockProxyClient, logger);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.FAILED);
        assertThat(response.getResourceModels()).isNull();
        assertThat(response.getErrorCode()).isEqualTo(HandlerErrorCode.NotFound);

        verify(mockOrgsClient, atLeastOnce()).serviceName();
        verifyNoMoreInteractions(mockOrgsClient);
    }

    @Test
    public void handleRequest_Fails_With_CfnAlreadyExistsException() {
        final ResourceModel model = generateInitialResourceModel(true, true);

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
            .desiredResourceState(model)
            .build();

        when(mockProxyClient.client().createPolicy(any(CreatePolicyRequest.class))).thenThrow(DuplicatePolicyException.class);

        final ProgressEvent<ResourceModel, CallbackContext> response = createHandler.handleRequest(mockAwsClientProxy, request, new CallbackContext(), mockProxyClient, logger);
        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.FAILED);
        assertThat(response.getResourceModels()).isNull();
        assertThat(response.getErrorCode()).isEqualTo(HandlerErrorCode.AlreadyExists);

        verify(mockOrgsClient, atLeastOnce()).serviceName();
        verifyNoMoreInteractions(mockOrgsClient);
    }

    protected CreatePolicyResponse getCreatePolicyResponse() {
        return CreatePolicyResponse.builder().policy(
            Policy.builder()
                .content(TEST_POLICY_CONTENT)
                .policySummary(PolicySummary.builder()
                    .arn(TEST_POLICY_ARN)
                    .awsManaged(TEST_AWSMANAGED)
                    .description(TEST_POLICY_DESCRIPTION)
                    .id(TEST_POLICY_ID)
                    .name(TEST_POLICY_NAME)
                    .type(TEST_TYPE)
                    .build())
                .build())
            .build();
    }
}
