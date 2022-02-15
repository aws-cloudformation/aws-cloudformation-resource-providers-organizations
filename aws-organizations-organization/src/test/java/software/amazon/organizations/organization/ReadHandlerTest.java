package software.amazon.organizations.organization;

import software.amazon.awssdk.services.organizations.OrganizationsClient;
import software.amazon.awssdk.services.organizations.model.AwsOrganizationsNotInUseException;
import software.amazon.awssdk.services.organizations.model.DescribeOrganizationRequest;
import software.amazon.awssdk.services.organizations.model.DescribeOrganizationResponse;
import software.amazon.awssdk.services.organizations.model.ListRootsRequest;
import software.amazon.awssdk.services.organizations.model.ListRootsResponse;
import software.amazon.awssdk.services.organizations.model.Organization;

import java.time.Duration;

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
    private AmazonWebServicesClientProxy mockAwsClientProxy;

    @Mock
    private ProxyClient<OrganizationsClient> mockProxyClient;

    @Mock
    OrganizationsClient mockOrgsClient;

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

        final ResourceModel model = ResourceModel.builder()
            .featureSet(TEST_FEATURE_SET)
            .build();

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
            .desiredResourceState(model)
            .build();

        final ListRootsResponse listRootsResponse = ListRootsResponse.builder().roots(
            software.amazon.awssdk.services.organizations.model.Root.builder()
                .id(TEST_ROOT_ID)
                .build()).build();

        when(mockProxyClient.client().listRoots(any(ListRootsRequest.class))).thenReturn(listRootsResponse);

        final DescribeOrganizationResponse describeOrganizationResponse = DescribeOrganizationResponse.builder().organization(
            Organization.builder()
                .arn(TEST_ORG_ARN)
                .featureSet(TEST_FEATURE_SET)
                .id(TEST_ORG_ID)
                .masterAccountArn(TEST_MANAGEMENT_ACCOUNT_ARN)
                .masterAccountEmail(TEST_MANAGEMENT_ACCOUNT_EMAIL)
                .masterAccountId(TEST_MANAGEMENT_ACCOUNT_ID).build())
                .build();

        when(mockProxyClient.client().describeOrganization(any(DescribeOrganizationRequest.class))).thenReturn(describeOrganizationResponse);

        final ProgressEvent<ResourceModel, CallbackContext> response = readHandler.handleRequest(mockAwsClientProxy, request, new CallbackContext(), mockProxyClient, logger);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.SUCCESS);
        assertThat(response.getCallbackDelaySeconds()).isEqualTo(0);
        assertThat(response.getResourceModel()).isNotNull();
        assertThat(response.getResourceModel().getFeatureSet()).isEqualTo(TEST_FEATURE_SET);
        assertThat(response.getResourceModel().getId()).isEqualTo(TEST_ORG_ID);
        assertThat(response.getResourceModel().getArn()).isEqualTo(TEST_ORG_ARN);
        assertThat(response.getResourceModel().getManagementAccountArn()).isEqualTo(TEST_MANAGEMENT_ACCOUNT_ARN);
        assertThat(response.getResourceModel().getManagementAccountId()).isEqualTo(TEST_MANAGEMENT_ACCOUNT_ID);
        assertThat(response.getResourceModel().getManagementAccountEmail()).isEqualTo(TEST_MANAGEMENT_ACCOUNT_EMAIL);
        assertThat(response.getResourceModel().getRootIds().get(0)).isEqualTo(TEST_ROOT_ID);
        assertThat(response.getResourceModels()).isNull();
        assertThat(response.getMessage()).isNull();
        assertThat(response.getErrorCode()).isNull();

        verify(mockProxyClient.client()).describeOrganization(any(DescribeOrganizationRequest.class));
    }

    @Test
    protected void handleRequest_Fails_With_CfnNotFoundException() {

        final ResourceModel model = ResourceModel.builder()
            .featureSet(TEST_FEATURE_SET)
            .build();

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
                .desiredResourceState(model)
                .build();

        final ListRootsResponse listRootsResponse = ListRootsResponse.builder().roots(
            software.amazon.awssdk.services.organizations.model.Root.builder()
                .id(TEST_ROOT_ID)
                .build()).build();

        when(mockProxyClient.client().listRoots(any(ListRootsRequest.class))).thenReturn(listRootsResponse);
        when(mockProxyClient.client().describeOrganization(any(DescribeOrganizationRequest.class))).thenThrow(AwsOrganizationsNotInUseException.class);

        final ProgressEvent<ResourceModel, CallbackContext> response = readHandler.handleRequest(mockAwsClientProxy, request, new CallbackContext(), mockProxyClient, logger);
        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.FAILED);
        assertThat(response.getResourceModels()).isNull();
        assertThat(response.getErrorCode()).isEqualTo(HandlerErrorCode.NotFound);
    }
}
