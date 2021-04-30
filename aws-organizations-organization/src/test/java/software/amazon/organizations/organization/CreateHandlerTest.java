package software.amazon.organizations.organization;

import software.amazon.awssdk.services.organizations.OrganizationsClient;
import software.amazon.awssdk.services.organizations.model.AlreadyInOrganizationException;
import software.amazon.awssdk.services.organizations.model.CreateOrganizationRequest;
import software.amazon.awssdk.services.organizations.model.CreateOrganizationResponse;
import software.amazon.awssdk.services.organizations.model.DescribeOrganizationRequest;
import software.amazon.awssdk.services.organizations.model.DescribeOrganizationResponse;
import software.amazon.awssdk.services.organizations.model.Organization;

import java.time.Duration;
import software.amazon.cloudformation.exceptions.CfnAlreadyExistsException;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class CreateHandlerTest extends AbstractTestBase {

    private final String TEST_ORG_ID = "o-1231231231";
    private final String TEST_ORG_ARN = "arn:org:test::555555555555:organization/o-2222222222";
    private final String TEST_FEATURE_SET = "ALL";
    private final String TEST_MANAGEMENT_ACCOUNT_ARN = "arn:account:test::555555555555:organization/o-2222222222";
    private final String TEST_MANAGEMENT_ACCOUNT_EMAIL = "testEmail@test.com";
    private final String TEST_MANAGEMENT_ACCOUNT_ID = "000000000000";

    @Mock
    private AmazonWebServicesClientProxy mockAwsClientProxy;

    @Mock
    private ProxyClient<OrganizationsClient> mockProxyClient;

    @Mock
    OrganizationsClient mockOrgsClient;

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
        final ResourceModel model = ResourceModel.builder()
                .id(TEST_ORG_ID)
                .arn(TEST_ORG_ARN)
                .featureSet(TEST_FEATURE_SET)
                .masterAccountArn(TEST_MANAGEMENT_ACCOUNT_ARN)
                .masterAccountId(TEST_MANAGEMENT_ACCOUNT_ID)
                .masterAccountEmail(TEST_MANAGEMENT_ACCOUNT_EMAIL)
                .build();

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
                .desiredResourceState(model)
                .build();

        final CreateOrganizationResponse createOrganizationResponse = CreateOrganizationResponse.builder().build();
        when(mockProxyClient.client().createOrganization(any(CreateOrganizationRequest.class))).thenReturn(createOrganizationResponse);

        final DescribeOrganizationResponse describeOrganizationResponse = DescribeOrganizationResponse.builder()
                .organization(Organization.builder()
                        .id(TEST_ORG_ID)
                        .arn(TEST_ORG_ARN)
                        .featureSet(TEST_FEATURE_SET)
                        .masterAccountArn(TEST_MANAGEMENT_ACCOUNT_ARN)
                        .masterAccountId(TEST_MANAGEMENT_ACCOUNT_ID)
                        .masterAccountEmail(TEST_MANAGEMENT_ACCOUNT_EMAIL)
                        .build()
                )
                .build();
        when(mockProxyClient.client().describeOrganization(any(DescribeOrganizationRequest.class))).thenReturn(describeOrganizationResponse);

        final ProgressEvent<ResourceModel, CallbackContext> response = createHandler.handleRequest(mockAwsClientProxy, request, new CallbackContext(), mockProxyClient, logger);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.SUCCESS);
        assertThat(response.getCallbackDelaySeconds()).isEqualTo(0);
        assertThat(response.getResourceModel()).isEqualTo(request.getDesiredResourceState());
        assertThat(response.getResourceModels()).isNull();
        assertThat(response.getMessage()).isNull();
        assertThat(response.getErrorCode()).isNull();

        verify(mockProxyClient.client()).createOrganization(any(CreateOrganizationRequest.class));
    }

    @Test
    public void handleRequest_Fails_With_CfnAlreadyExistsException() {
        final ResourceModel model = ResourceModel.builder()
                .featureSet(TEST_FEATURE_SET)
                .build();

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
                .desiredResourceState(model)
                .build();

        when(mockProxyClient.client().createOrganization(any(CreateOrganizationRequest.class))).thenThrow(AlreadyInOrganizationException.class);

        assertThrows(CfnAlreadyExistsException.class,
                () -> createHandler.handleRequest(mockAwsClientProxy, request, new CallbackContext(), mockProxyClient, logger));
    }
}
