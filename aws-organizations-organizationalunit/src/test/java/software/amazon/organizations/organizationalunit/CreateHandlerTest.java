package software.amazon.organizations.organizationalunit;

import java.time.Duration;

import software.amazon.awssdk.services.organizations.OrganizationsClient;
import software.amazon.awssdk.services.organizations.model.DescribeOrganizationalUnitRequest;
import software.amazon.awssdk.services.organizations.model.DescribeOrganizationalUnitResponse;
import software.amazon.awssdk.services.organizations.model.CreateOrganizationalUnitRequest;
import software.amazon.awssdk.services.organizations.model.CreateOrganizationalUnitResponse;
import software.amazon.awssdk.services.organizations.model.DuplicateOrganizationalUnitException;
import software.amazon.awssdk.services.organizations.model.OrganizationalUnit;
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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class CreateHandlerTest extends AbstractTestBase {

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

    @Test
    public void handleRequest_SimpleSuccess() {
        final ResourceModel model = generateResourceModel();

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
            .desiredResourceState(model)
            .build();

        final CreateOrganizationalUnitResponse createOrganizationalUnitResponse = CreateOrganizationalUnitResponse.builder()
            .organizationalUnit(OrganizationalUnit.builder()
                .name(TEST_OU_NAME)
                .arn(TEST_OU_ARN)
                .id(TEST_OU_ID)
                .build()
            ).build();

        when(mockProxyClient.client().createOrganizationalUnit(any(CreateOrganizationalUnitRequest.class))).thenReturn(createOrganizationalUnitResponse);

        final DescribeOrganizationalUnitResponse describeOrganizationalUnitResponse = DescribeOrganizationalUnitResponse.builder()
            .organizationalUnit(OrganizationalUnit.builder()
                .name(TEST_OU_NAME)
                .arn(TEST_OU_ARN)
                .id(TEST_OU_ID)
                .build()
            ).build();

        when(mockProxyClient.client().describeOrganizationalUnit(any(DescribeOrganizationalUnitRequest.class))).thenReturn(describeOrganizationalUnitResponse);

        final ProgressEvent<ResourceModel, CallbackContext> response = createHandler.handleRequest(mockAwsClientProxy, request, new CallbackContext(false), mockProxyClient, logger);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.SUCCESS);
        assertThat(response.getCallbackDelaySeconds()).isEqualTo(0);
        assertThat(response.getResourceModel()).isNotNull();
        assertThat(response.getResourceModel()).isEqualTo(request.getDesiredResourceState());
        assertThat(response.getResourceModel().getName()).isEqualTo(TEST_OU_NAME);
        assertThat(response.getResourceModel().getArn()).isEqualTo(TEST_OU_ARN);
        assertThat(response.getResourceModel().getId()).isEqualTo(TEST_OU_ID);
        assertThat(response.getResourceModels()).isNull();
        assertThat(response.getMessage()).isNull();
        assertThat(response.getErrorCode()).isNull();

        verify(mockProxyClient.client()).createOrganizationalUnit(any(CreateOrganizationalUnitRequest.class));
    }

    @Test
    public void handleRequest_Fails_With_CfnAlreadyExistsException() {
        final ResourceModel model = generateResourceModel();

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
            .desiredResourceState(model)
            .build();

        when(mockProxyClient.client().createOrganizationalUnit(any(CreateOrganizationalUnitRequest.class))).thenThrow(DuplicateOrganizationalUnitException.class);

        final ProgressEvent<ResourceModel, CallbackContext> response = createHandler.handleRequest(mockAwsClientProxy, request, new CallbackContext(false), mockProxyClient, logger);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.FAILED);
        assertThat(response.getResourceModels()).isNull();
        assertThat(response.getErrorCode()).isEqualTo(HandlerErrorCode.AlreadyExists);
    }
}
