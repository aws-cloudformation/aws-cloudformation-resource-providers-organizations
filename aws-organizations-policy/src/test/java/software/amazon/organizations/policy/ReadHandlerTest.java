package software.amazon.organizations.policy;

import software.amazon.awssdk.services.organizations.OrganizationsClient;
import software.amazon.awssdk.services.organizations.model.*;

import java.time.Duration;
import java.util.Arrays;

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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;


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

    //@Test
    public void handleRequest_SimpleSuccess() {
        final ResourceModel model = ResourceModel.builder()
            .content(TEST_POLICY_CONTENT)
            .description(TEST_POLICY_DESCRIPTION)
            .name(TEST_POLICY_NAME)
            .type(TEST_TYPE)
            .id(TEST_POLICY_ID)
            .targetIds(TEST_TARGET_IDS)
            .build();

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
            .desiredResourceState(model)
            .build();


        final DescribePolicyResponse describePolicyResponse = DescribePolicyResponse.builder().policy(
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

        when(mockProxyClient.client().describePolicy(any(DescribePolicyRequest.class))).thenReturn(describePolicyResponse);

        final PolicyTargetSummary targetSummary = PolicyTargetSummary.builder()
            .targetId(TEST_TARGET_ID)
            .build();

        final ListTargetsForPolicyResponse listTargetsResponse = ListTargetsForPolicyResponse.builder()
            .targets(Arrays.asList(targetSummary))
            .nextToken(null)
            .build();

        when(mockProxyClient.client().listTargetsForPolicy(any(ListTargetsForPolicyRequest.class))).thenReturn(listTargetsResponse);

        final ProgressEvent<ResourceModel, CallbackContext> response = readHandler.handleRequest(mockAwsClientProxy, request, new CallbackContext(), mockProxyClient, logger);

        final ResourceModel finalModel = generateFinalResourceModel();

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.SUCCESS);
        assertThat(response.getCallbackDelaySeconds()).isEqualTo(0);
        assertThat(response.getResourceModel()).isNotNull();
        assertThat(response.getResourceModel()).isEqualTo(finalModel);
        assertThat(response.getResourceModels()).isNull();
        assertThat(response.getMessage()).isNull();
        assertThat(response.getErrorCode()).isNull();

        verify(mockProxyClient.client()).describePolicy(any(DescribePolicyRequest.class));
//        verify(mockOrgsClient, atLeastOnce()).injectCredentialsAndInvokeV2();
    }

    //@Test
    protected void handleRequest_Fails_With_CfnNotFoundException() {

        final ResourceModel model = ResourceModel.builder()
            .content(TEST_POLICY_CONTENT)
            .description(TEST_POLICY_DESCRIPTION)
            .name(TEST_POLICY_NAME)
            .type(TEST_TYPE)
            .id(TEST_POLICY_ID)
            .targetIds(TEST_TARGET_IDS)
            .build();

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
}
