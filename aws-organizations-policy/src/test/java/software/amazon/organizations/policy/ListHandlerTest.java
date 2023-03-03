package software.amazon.organizations.policy;

import software.amazon.awssdk.services.organizations.OrganizationsClient;
import software.amazon.awssdk.services.organizations.model.ListPoliciesRequest;
import software.amazon.awssdk.services.organizations.model.ListPoliciesResponse;
import software.amazon.awssdk.services.organizations.model.PolicySummary;
import software.amazon.awssdk.services.organizations.model.ServiceException;
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
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ListHandlerTest extends AbstractTestBase {

    @Mock
    private AmazonWebServicesClientProxy mockAwsClientProxy;

    @Mock
    private ProxyClient<OrganizationsClient> mockProxyClient;

    @Mock
    OrganizationsClient mockOrgsClient;

    private ListHandler listHandlerToTest;

    @BeforeEach
    public void setup() {
        listHandlerToTest = new ListHandler();
        mockAwsClientProxy = new AmazonWebServicesClientProxy(loggerProxy, MOCK_CREDENTIALS, () -> Duration.ofSeconds(600).toMillis());
        mockOrgsClient = mock(OrganizationsClient.class);
        mockProxyClient = MOCK_PROXY(mockAwsClientProxy, mockOrgsClient);
    }

    @Test
    public void handleRequest_ServiceControlPolicies_WithNextToken_SimpleSuccess() {
        final ResourceModel serviceControlPolicyTypeModel = ResourceModel.builder()
            .type(PolicyConstants.PolicyType.SERVICE_CONTROL_POLICY.toString())
            .build();
        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
            .desiredResourceState(serviceControlPolicyTypeModel)
            .build();


        ListPoliciesResponse listPoliciesResponse = ListPoliciesResponse.builder()
            .policies(Arrays.asList(getMockPolicySummaryWithType(PolicyConstants.PolicyType.SERVICE_CONTROL_POLICY.toString())))
            .nextToken(TEST_NEXT_TOKEN)
            .build();
        when(mockProxyClient.client().listPolicies(any(ListPoliciesRequest.class))).thenReturn(listPoliciesResponse);

        final ProgressEvent<ResourceModel, CallbackContext> response =
            listHandlerToTest.handleRequest(mockAwsClientProxy, request, new CallbackContext(), mockProxyClient, logger);

        verifySuccessResponse(response);

        verify(mockProxyClient.client()).listPolicies(any(ListPoliciesRequest.class));
        verify(mockOrgsClient, atLeastOnce()).serviceName();
        verifyNoMoreInteractions(mockOrgsClient);
    }

    private static void verifySuccessResponse(ProgressEvent<ResourceModel, CallbackContext> response) {
        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.SUCCESS);
        assertThat(response.getCallbackContext()).isNull();
        assertThat(response.getCallbackDelaySeconds()).isEqualTo(0);
        assertThat(response.getResourceModel()).isNull();
        assertThat(response.getResourceModels()).isNotNull();
        assertThat(response.getResourceModels().stream().count()).isEqualTo(1);
        assertThat(response.getNextToken()).isNotNull();
        assertThat(response.getNextToken()).isEqualTo(TEST_NEXT_TOKEN);
        assertThat(response.getMessage()).isNull();
        assertThat(response.getErrorCode()).isNull();
    }

    @Test
    public void handleRequest_TagPolicies_SimpleSuccess() {
        final ResourceModel tagPolicyTypeModel = ResourceModel.builder()
            .type(PolicyConstants.PolicyType.TAG_POLICY.toString())
            .build();
        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
            .desiredResourceState(tagPolicyTypeModel)
            .build();


        ListPoliciesResponse listPoliciesResponse = ListPoliciesResponse.builder()
            .policies(Arrays.asList(getMockPolicySummaryWithType(PolicyConstants.PolicyType.TAG_POLICY.toString())))
            .build();
        when(mockProxyClient.client().listPolicies(any(ListPoliciesRequest.class))).thenReturn(listPoliciesResponse);

        final ProgressEvent<ResourceModel, CallbackContext> response =
            listHandlerToTest.handleRequest(mockAwsClientProxy, request, new CallbackContext(), mockProxyClient, logger);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.SUCCESS);
        assertThat(response.getCallbackContext()).isNull();
        assertThat(response.getCallbackDelaySeconds()).isEqualTo(0);
        assertThat(response.getResourceModel()).isNull();
        assertThat(response.getResourceModels()).isNotNull();
        assertThat(response.getResourceModels().stream().count()).isEqualTo(1);
        assertThat(response.getNextToken()).isNull();
        assertThat(response.getMessage()).isNull();
        assertThat(response.getErrorCode()).isNull();

        verify(mockProxyClient.client()).listPolicies(any(ListPoliciesRequest.class));
        verify(mockOrgsClient, atLeastOnce()).serviceName();
        verifyNoMoreInteractions(mockOrgsClient);
    }

    @Test
    public void handleRequest_BackupPolicies_SimpleSuccess() {
        final ResourceModel backupPolicyTypeModel = ResourceModel.builder()
            .type(PolicyConstants.PolicyType.BACKUP_POLICY.toString())
            .build();
        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
            .desiredResourceState(backupPolicyTypeModel)
            .build();


        ListPoliciesResponse listPoliciesResponse = ListPoliciesResponse.builder()
            .policies(Arrays.asList(getMockPolicySummaryWithType(PolicyConstants.PolicyType.BACKUP_POLICY.toString())))
            .nextToken(null)
            .build();
        when(mockProxyClient.client().listPolicies(any(ListPoliciesRequest.class))).thenReturn(listPoliciesResponse);

        final ProgressEvent<ResourceModel, CallbackContext> response =
            listHandlerToTest.handleRequest(mockAwsClientProxy, request, new CallbackContext(), mockProxyClient, logger);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.SUCCESS);
        assertThat(response.getCallbackContext()).isNull();
        assertThat(response.getCallbackDelaySeconds()).isEqualTo(0);
        assertThat(response.getResourceModel()).isNull();
        assertThat(response.getResourceModels()).isNotNull();
        assertThat(response.getResourceModels().stream().count()).isEqualTo(1);
        assertThat(response.getNextToken()).isNull();
        assertThat(response.getMessage()).isNull();
        assertThat(response.getErrorCode()).isNull();

        verify(mockProxyClient.client()).listPolicies(any(ListPoliciesRequest.class));
        verify(mockOrgsClient, atLeastOnce()).serviceName();
        verifyNoMoreInteractions(mockOrgsClient);
    }

    @Test
    public void handleRequest_AIOptOutServicesPolicies_SimpleSuccess() {
        final ResourceModel aiOptOutPolicyTypeModel = ResourceModel.builder()
            .type(PolicyConstants.PolicyType.AISERVICES_OPT_OUT_POLICY.toString())
            .build();
        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
            .desiredResourceState(aiOptOutPolicyTypeModel)
            .build();


        ListPoliciesResponse listPoliciesResponse = ListPoliciesResponse.builder()
            .policies(Arrays.asList(getMockPolicySummaryWithType(PolicyConstants.PolicyType.AISERVICES_OPT_OUT_POLICY.toString())))
            .build();
        when(mockProxyClient.client().listPolicies(any(ListPoliciesRequest.class))).thenReturn(listPoliciesResponse);

        final ProgressEvent<ResourceModel, CallbackContext> response =
            listHandlerToTest.handleRequest(mockAwsClientProxy, request, new CallbackContext(), mockProxyClient, logger);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.SUCCESS);
        assertThat(response.getCallbackContext()).isNull();
        assertThat(response.getCallbackDelaySeconds()).isEqualTo(0);
        assertThat(response.getResourceModel()).isNull();
        assertThat(response.getResourceModels()).isNotNull();
        assertThat(response.getResourceModels().stream().count()).isEqualTo(1);
        assertThat(response.getNextToken()).isNull();
        assertThat(response.getMessage()).isNull();
        assertThat(response.getErrorCode()).isNull();

        verify(mockProxyClient.client()).listPolicies(any(ListPoliciesRequest.class));
        verify(mockOrgsClient, atLeastOnce()).serviceName();
        verifyNoMoreInteractions(mockOrgsClient);
    }

    @Test
    public void handleRequest_NullDesiredModel_Fails_With_CfnInvalidRequest() {
        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
            .desiredResourceState(null)
            .build();

        final ProgressEvent<ResourceModel, CallbackContext> response =
            listHandlerToTest.handleRequest(mockAwsClientProxy, request, new CallbackContext(), mockProxyClient, logger);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.FAILED);
        assertThat(response.getResourceModels()).isNull();
        assertThat(response.getErrorCode()).isEqualTo(HandlerErrorCode.InvalidRequest);
    }

    @Test
    public void handleRequest_NullDesiredModelType_Fails_With_CfnInvalidRequest() {
        final ResourceModel noPolicyTypeModel = ResourceModel.builder()
            .build();
        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
            .desiredResourceState(noPolicyTypeModel)
            .build();

        final ProgressEvent<ResourceModel, CallbackContext> response =
            listHandlerToTest.handleRequest(mockAwsClientProxy, request, new CallbackContext(), mockProxyClient, logger);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.FAILED);
        assertThat(response.getResourceModels()).isNull();
        assertThat(response.getErrorCode()).isEqualTo(HandlerErrorCode.InvalidRequest);
    }


    protected PolicySummary getMockPolicySummaryWithType(final String policyType) {
        return PolicySummary.builder()
            .name(TEST_POLICY_NAME)
            .id(TEST_POLICY_ID)
            .arn(TEST_POLICY_ARN)
            .description(TEST_POLICY_DESCRIPTION)
            .type(policyType)
            .awsManaged(TEST_AWSMANAGED)
            .build();
    }

    @Test
    public void handleRequest_shouldReturnFailed_withServiceException_forListPoliciesCalls() {
        final ResourceModel serviceControlPolicyTypeModel = ResourceModel.builder()
                .type(PolicyConstants.PolicyType.SERVICE_CONTROL_POLICY.toString())
                .build();

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
                .desiredResourceState(serviceControlPolicyTypeModel)
                .build();
        when(mockProxyClient.client().listPolicies(any(ListPoliciesRequest.class))).thenThrow(ServiceException.class);

        final ProgressEvent<ResourceModel, CallbackContext> response =
                listHandlerToTest.handleRequest(mockAwsClientProxy, request, new CallbackContext(), mockProxyClient, logger);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.FAILED);
        assertThat(response.getResourceModels()).isNull();
        assertThat(response.getErrorCode()).isEqualTo(HandlerErrorCode.ServiceInternalError);
        verify(mockProxyClient.client()).listPolicies(any(ListPoliciesRequest.class));
        verify(mockOrgsClient, atLeastOnce()).serviceName();
        verifyNoMoreInteractions(mockOrgsClient);
    }
}
