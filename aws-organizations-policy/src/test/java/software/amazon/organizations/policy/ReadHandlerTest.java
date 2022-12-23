package software.amazon.organizations.policy;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.organizations.OrganizationsClient;
import software.amazon.awssdk.services.organizations.model.DescribePolicyRequest;
import software.amazon.awssdk.services.organizations.model.DescribePolicyResponse;
import software.amazon.awssdk.services.organizations.model.ListTagsForResourceRequest;
import software.amazon.awssdk.services.organizations.model.ListTagsForResourceResponse;
import software.amazon.awssdk.services.organizations.model.ListTargetsForPolicyRequest;
import software.amazon.awssdk.services.organizations.model.ListTargetsForPolicyResponse;
import software.amazon.awssdk.services.organizations.model.Policy;
import software.amazon.awssdk.services.organizations.model.PolicyNotFoundException;
import software.amazon.awssdk.services.organizations.model.PolicySummary;
import software.amazon.awssdk.services.organizations.model.PolicyTargetSummary;
import software.amazon.awssdk.services.organizations.model.ServiceException;
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
import static org.mockito.Mockito.*;


@ExtendWith(MockitoExtension.class)
public class ReadHandlerTest extends AbstractTestBase {
    @Mock
    OrganizationsClient mockOrgsClient;
    @Mock
    private AmazonWebServicesClientProxy mockAwsClientProxy;
    @Mock
    private ProxyClient<OrganizationsClient> mockProxyClient;
    private ReadHandler readHandler;

    @BeforeEach
    public void setup() {
        readHandler = new ReadHandler();
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
    public void handleRequest_SimpleSuccess() {
        final ResourceModel model = buildResourceModel();

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
            .desiredResourceState(model)
            .build();

        final DescribePolicyResponse describePolicyResponse = buildDescribePolicyResponse();

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

        final ProgressEvent<ResourceModel, CallbackContext> response = readHandler.handleRequest(mockAwsClientProxy, request, new CallbackContext(), mockProxyClient, logger);

        final ResourceModel finalModel = generateFinalResourceModel(true, true);

        verifySuccessResponse(response, finalModel);

        verify(mockProxyClient.client()).describePolicy(any(DescribePolicyRequest.class));
        verify(mockProxyClient.client()).listTargetsForPolicy(any(ListTargetsForPolicyRequest.class));
        verify(mockProxyClient.client()).listTagsForResource(any(ListTagsForResourceRequest.class));
    }

    private static void verifySuccessResponse(ProgressEvent<ResourceModel, CallbackContext> response, ResourceModel finalModel) {
        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.SUCCESS);
        assertThat(response.getCallbackDelaySeconds()).isEqualTo(0);
        assertThat(response.getResourceModel()).isNotNull();
        assertThat(response.getResourceModel()).isEqualTo(finalModel);
        assertThat(response.getResourceModels()).isNull();
        assertThat(response.getMessage()).isNull();
        assertThat(response.getErrorCode()).isNull();
    }

    @Test
    protected void handleRequest_Fails_With_CfnNotFoundException() {
        final ResourceModel model = buildResourceModel();

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
            .desiredResourceState(model)
            .build();

        when(mockProxyClient.client().describePolicy(any(DescribePolicyRequest.class))).thenThrow(PolicyNotFoundException.class);

        final ProgressEvent<ResourceModel, CallbackContext> response = readHandler.handleRequest(mockAwsClientProxy, request, new CallbackContext(), mockProxyClient, logger);
        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.FAILED);
        assertThat(response.getResourceModels()).isNull();
        assertThat(response.getErrorCode()).isEqualTo(HandlerErrorCode.NotFound);
    }

    @Test
    public void handleRequest_ListTargetsException_FailsWith_ServiceInternalErrorException() {
        final ResourceModel model = buildResourceModel();

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
            .desiredResourceState(model)
            .build();


        final DescribePolicyResponse describePolicyResponse = buildDescribePolicyResponse();

        when(mockProxyClient.client().describePolicy(any(DescribePolicyRequest.class))).thenReturn(describePolicyResponse);

        when(mockProxyClient.client().listTargetsForPolicy(any(ListTargetsForPolicyRequest.class))).thenThrow(ServiceException.class);

        CallbackContext context = new CallbackContext();
        ProgressEvent<ResourceModel, CallbackContext> response = readHandler.handleRequest(mockAwsClientProxy, request, context, mockProxyClient, logger);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.FAILED);
        assertThat(response.getResourceModels()).isNull();
        assertThat(response.getErrorCode()).isEqualTo(HandlerErrorCode.ServiceInternalError);

        verify(mockProxyClient.client()).describePolicy(any(DescribePolicyRequest.class));
        verify(mockProxyClient.client(), times(3)).listTargetsForPolicy(any(ListTargetsForPolicyRequest.class));
    }

    @Test
    public void handleRequest_ListTagsException_FailsWith_ServiceInternalErrorException() {
        final ResourceModel model = buildResourceModel();

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
            .desiredResourceState(model)
            .build();

        final DescribePolicyResponse describePolicyResponse = buildDescribePolicyResponse();

        when(mockProxyClient.client().describePolicy(any(DescribePolicyRequest.class))).thenReturn(describePolicyResponse);

        final PolicyTargetSummary rootTargetSummary = getPolicyTargetSummaryWithTargetId(TEST_TARGET_ROOT_ID);
        final PolicyTargetSummary ouTargetSummary = getPolicyTargetSummaryWithTargetId(TEST_TARGET_OU_ID);
        final ListTargetsForPolicyResponse listTargetsResponse = ListTargetsForPolicyResponse.builder()
            .targets(Arrays.asList(rootTargetSummary, ouTargetSummary))
            .nextToken(null)
            .build();

        when(mockProxyClient.client().listTargetsForPolicy(any(ListTargetsForPolicyRequest.class))).thenReturn(listTargetsResponse);

        when(mockProxyClient.client().listTagsForResource(any(ListTagsForResourceRequest.class))).thenThrow(ServiceException.class);

        CallbackContext context = new CallbackContext();
        ProgressEvent<ResourceModel, CallbackContext> response = readHandler.handleRequest(mockAwsClientProxy, request, context, mockProxyClient, logger);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.FAILED);
        assertThat(response.getResourceModels()).isNull();
        assertThat(response.getErrorCode()).isEqualTo(HandlerErrorCode.ServiceInternalError);

        verify(mockProxyClient.client()).describePolicy(any(DescribePolicyRequest.class));
        verify(mockProxyClient.client()).listTargetsForPolicy(any(ListTargetsForPolicyRequest.class));
        verify(mockProxyClient.client(), times(3)).listTagsForResource(any(ListTagsForResourceRequest.class));
    }


    @Test
    public void handleRequest_shouldReturnSuccess_onSecondRetry_forReadPoliciesCalls() {
        final ResourceModel model = buildResourceModel();

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
                .desiredResourceState(model)
                .build();

        final DescribePolicyResponse describePolicyResponse = buildDescribePolicyResponse();

        when(mockProxyClient.client().describePolicy(any(DescribePolicyRequest.class))).thenThrow(ServiceException.class).thenReturn(describePolicyResponse);

        final PolicyTargetSummary rootTargetSummary = getPolicyTargetSummaryWithTargetId(TEST_TARGET_ROOT_ID);
        final PolicyTargetSummary ouTargetSummary = getPolicyTargetSummaryWithTargetId(TEST_TARGET_OU_ID);
        final ListTargetsForPolicyResponse listTargetsResponse = ListTargetsForPolicyResponse.builder()
                .targets(Arrays.asList(rootTargetSummary, ouTargetSummary))
                .nextToken(null)
                .build();

        when(mockProxyClient.client().listTargetsForPolicy(any(ListTargetsForPolicyRequest.class))).thenThrow(ServiceException.class).thenThrow(ServiceException.class).thenReturn(listTargetsResponse);

        final ListTagsForResourceResponse listTagsResponse = TagTestResourceHelper.buildDefaultTagsResponse();

        when(mockProxyClient.client().listTagsForResource(any(ListTagsForResourceRequest.class))).thenReturn(listTagsResponse);

        final ProgressEvent<ResourceModel, CallbackContext> response = readHandler.handleRequest(mockAwsClientProxy, request, new CallbackContext(), mockProxyClient, logger);

        final ResourceModel finalModel = generateFinalResourceModel(true, true);

        verifySuccessResponse(response, finalModel);

        verify(mockProxyClient.client(), times(2)).describePolicy(any(DescribePolicyRequest.class));
        verify(mockProxyClient.client(), times(3)).listTargetsForPolicy(any(ListTargetsForPolicyRequest.class));
        verify(mockProxyClient.client()).listTagsForResource(any(ListTagsForResourceRequest.class));
    }

    @Test
    protected void handleRequest_shouldReturnFailed_AfterThirdRetry_forDescribePoliciesCalls() {
        final ResourceModel model = buildResourceModel();

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
                .desiredResourceState(model)
                .build();

        when(mockProxyClient.client().describePolicy(any(DescribePolicyRequest.class))).thenThrow(ServiceException.class);

        final ProgressEvent<ResourceModel, CallbackContext> response = readHandler.handleRequest(mockAwsClientProxy, request, new CallbackContext(), mockProxyClient, logger);
        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.FAILED);
        assertThat(response.getResourceModels()).isNull();
        assertThat(response.getErrorCode()).isEqualTo(HandlerErrorCode.ServiceInternalError);
        verify(mockProxyClient.client(), times(3)).describePolicy(any(DescribePolicyRequest.class));
    }

    @Test
    public void handleRequest_ShoulReturnSuccess_WhenPaginatedResultForListTargetsForPolicy() {
        final ResourceModel model = buildResourceModel();

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
                .desiredResourceState(model)
                .build();

        final DescribePolicyResponse describePolicyResponse = buildDescribePolicyResponse();

        when(mockProxyClient.client().describePolicy(any(DescribePolicyRequest.class))).thenReturn(describePolicyResponse);

        final PolicyTargetSummary rootTargetSummary = getPolicyTargetSummaryWithTargetId(TEST_TARGET_ROOT_ID);
        final PolicyTargetSummary ouTargetSummary = getPolicyTargetSummaryWithTargetId(TEST_TARGET_OU_ID);
        final ListTargetsForPolicyResponse listTargetsResponse1 = ListTargetsForPolicyResponse.builder()
                .targets(Arrays.asList(rootTargetSummary))
                .nextToken("NotEmptyNextToken")
                .build();

        final ListTargetsForPolicyResponse listTargetsResponse2 = ListTargetsForPolicyResponse.builder()
                .targets(Arrays.asList( ouTargetSummary))
                .nextToken(null)
                .build();

        when(mockProxyClient.client().listTargetsForPolicy(any(ListTargetsForPolicyRequest.class))).thenReturn(listTargetsResponse1).thenReturn(listTargetsResponse2);

        final ListTagsForResourceResponse listTagsResponse = TagTestResourceHelper.buildDefaultTagsResponse();

        when(mockProxyClient.client().listTagsForResource(any(ListTagsForResourceRequest.class))).thenReturn(listTagsResponse);

        final ProgressEvent<ResourceModel, CallbackContext> response = readHandler.handleRequest(mockAwsClientProxy, request, new CallbackContext(), mockProxyClient, logger);

        final ResourceModel finalModel = generateFinalResourceModel(true, true);

        verifySuccessResponse(response, finalModel);

        verify(mockProxyClient.client()).describePolicy(any(DescribePolicyRequest.class));
        verify(mockProxyClient.client(), times(2)).listTargetsForPolicy(any(ListTargetsForPolicyRequest.class));
        verify(mockProxyClient.client()).listTagsForResource(any(ListTagsForResourceRequest.class));
    }

    private static DescribePolicyResponse buildDescribePolicyResponse() {
        return DescribePolicyResponse.builder().policy(
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

    private static ResourceModel buildResourceModel() {
        return ResourceModel.builder()
                .content(TEST_POLICY_CONTENT)
                .description(TEST_POLICY_DESCRIPTION)
                .name(TEST_POLICY_NAME)
                .type(TEST_TYPE)
                .id(TEST_POLICY_ID)
                .targetIds(TEST_TARGET_IDS)
                .build();
    }
}
