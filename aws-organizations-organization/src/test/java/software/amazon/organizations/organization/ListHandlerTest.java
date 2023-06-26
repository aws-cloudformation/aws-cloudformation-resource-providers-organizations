package software.amazon.organizations.organization;

import java.time.Duration;

import org.junit.jupiter.api.AfterEach;
import software.amazon.awssdk.services.organizations.model.*;
import software.amazon.awssdk.services.organizations.OrganizationsClient;

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
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.verifyNoMoreInteractions;

@ExtendWith(MockitoExtension.class)
public class ListHandlerTest extends AbstractTestBase {

    @Mock
    private AmazonWebServicesClientProxy mockAwsClientProxy;

    @Mock
    private ProxyClient<OrganizationsClient> mockProxyClient;

    @Mock
    OrganizationsClient mockOrgsClient;

    private ListHandler listHandler;

    @BeforeEach
    public void setup() {
        listHandler = new ListHandler();
        mockAwsClientProxy = new AmazonWebServicesClientProxy(loggerProxy, MOCK_CREDENTIALS, () -> Duration.ofSeconds(600).toMillis());
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

        final ProgressEvent<ResourceModel, CallbackContext> response = listHandler.handleRequest(mockAwsClientProxy, request, new CallbackContext(), mockProxyClient, logger);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.SUCCESS);
        assertThat(response.getCallbackDelaySeconds()).isEqualTo(0);
        assertThat(response.getResourceModels()).isNotNull();
        assertThat(response.getNextToken()).isNull();
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

        when(mockProxyClient.client().describeOrganization(any(DescribeOrganizationRequest.class))).thenThrow(AwsOrganizationsNotInUseException.class);

        final ProgressEvent<ResourceModel, CallbackContext> response = listHandler.handleRequest(mockAwsClientProxy, request, new CallbackContext(), mockProxyClient, logger);
        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.SUCCESS);
        assertThat(response.getResourceModels()).isNotNull();
    }

    @Test
    protected void handleRequest_Fails_With_ServiceException() {

        final ResourceModel model = ResourceModel.builder()
                .featureSet(TEST_FEATURE_SET)
                .build();

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
                .desiredResourceState(model)
                .build();

        when(mockProxyClient.client().describeOrganization(any(DescribeOrganizationRequest.class))).thenThrow(ServiceException.class);

        final ProgressEvent<ResourceModel, CallbackContext> response = listHandler.handleRequest(mockAwsClientProxy, request, new CallbackContext(), mockProxyClient, logger);
        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.FAILED);
        assertThat(response.getResourceModels()).isNull();
        assertThat(response.getErrorCode()).isEqualTo(HandlerErrorCode.ServiceInternalError);
        verify(mockProxyClient.client()).describeOrganization(any(DescribeOrganizationRequest.class));
    }
}
