package software.amazon.organizations.organization;

import software.amazon.awssdk.services.organizations.OrganizationsClient;
import software.amazon.awssdk.services.organizations.model.AwsOrganizationsNotInUseException;
import software.amazon.awssdk.services.organizations.model.DescribeOrganizationRequest;
import software.amazon.awssdk.services.organizations.model.DescribeOrganizationResponse;
import software.amazon.awssdk.services.organizations.model.Organization;

import java.time.Duration;

import software.amazon.cloudformation.exceptions.CfnNotFoundException;
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
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ReadHandlerTest extends AbstractTestBase {

    @Mock
    private AmazonWebServicesClientProxy proxy;

    @Mock
    private ProxyClient<OrganizationsClient> proxyClient;

    @Mock
    OrganizationsClient orgsClient;

    @BeforeEach
    public void setup() {
        proxy = new AmazonWebServicesClientProxy(logger, MOCK_CREDENTIALS, () -> Duration.ofSeconds(600).toMillis());
        orgsClient = mock(OrganizationsClient.class);
        proxyClient = MOCK_PROXY(proxy, orgsClient);
    }

    @AfterEach
    public void tear_down() {
        verify(orgsClient, atLeastOnce()).serviceName();
        verifyNoMoreInteractions(orgsClient);
    }

    @Test
    public void handleRequest_SimpleSuccess() {
        final ReadHandler handler = new ReadHandler();

        final ResourceModel model = ResourceModel.builder().arn("arn:aws:organizations:us-east-1:111111111111:i-1234567890abcdef0").featureSet("ALL").orgId("o-2222222222222")
                .masterAccountArn("arn:aws:organizations:us-east-1:333333333333:i-1234567890abcdef0").masterAccountEmail("testEmail000@email.com")
                .masterAccountId("555555555555")
                .build();

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
            .desiredResourceState(model)
            .build();

        final DescribeOrganizationResponse describeOrganizationResponse = DescribeOrganizationResponse.builder().organization(
                Organization.builder().arn("arn:aws:organizations:us-east-1:111111111111:i-1234567890abcdef0").featureSet("ALL").id("o-2222222222222")
                .masterAccountArn("arn:aws:organizations:us-east-1:333333333333:i-1234567890abcdef0").masterAccountEmail("testEmail000@email.com")
                .masterAccountId("555555555555").build())
                .build();

        when(proxyClient.client().describeOrganization(any(DescribeOrganizationRequest.class))).thenReturn(describeOrganizationResponse);

        final ProgressEvent<ResourceModel, CallbackContext> response = handler.handleRequest(proxy, request, new CallbackContext(), proxyClient, logger);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.SUCCESS);
        assertThat(response.getCallbackDelaySeconds()).isEqualTo(0);
        assertThat(response.getResourceModel()).isEqualTo(request.getDesiredResourceState());
        assertThat(response.getResourceModels()).isNull();
        assertThat(response.getMessage()).isNull();
        assertThat(response.getErrorCode()).isNull();

        verify(proxyClient.client()).describeOrganization(any(DescribeOrganizationRequest.class));
    }

    @Test
    protected void handleRequest_Fails_With_CfnNotFoundException() {
        final ReadHandler handler = new ReadHandler();

        final ResourceModel model = ResourceModel.builder().arn("arn:aws:organizations:us-east-1:111111111111:i-1234567890abcdef0").featureSet("ALL").orgId("o-2222222222222")
                .masterAccountArn("arn:aws:organizations:us-east-1:333333333333:i-1234567890abcdef0").masterAccountEmail("testEmail000@email.com")
                .masterAccountId("555555555555")
                .build();

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
                .desiredResourceState(model)
                .build();

        when(proxyClient.client().describeOrganization(any(DescribeOrganizationRequest.class))).thenThrow(AwsOrganizationsNotInUseException.class);

        assertThrows(CfnNotFoundException.class,
                () -> handler.handleRequest(proxy, request, new CallbackContext(), proxyClient, logger));
    }
}
