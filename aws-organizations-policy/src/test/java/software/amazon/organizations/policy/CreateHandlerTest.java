package software.amazon.organizations.policy;

import java.time.Duration;
import java.util.Arrays;

import software.amazon.awssdk.services.organizations.OrganizationsClient;
import software.amazon.awssdk.services.organizations.model.AttachPolicyRequest;
import software.amazon.awssdk.services.organizations.model.AttachPolicyResponse;
import software.amazon.awssdk.services.organizations.model.CreatePolicyRequest;
import software.amazon.awssdk.services.organizations.model.CreatePolicyResponse;
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

import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.HandlerErrorCode;
import software.amazon.cloudformation.proxy.OperationStatus;
import software.amazon.cloudformation.proxy.ProgressEvent;
import software.amazon.cloudformation.proxy.ProxyClient;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
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

    @AfterEach
    public void tear_down() {
        verify(mockOrgsClient, atLeastOnce()).serviceName();
        verifyNoMoreInteractions(mockOrgsClient);
    }

    @Test
    public void handleRequest_SimpleSuccess() {
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
