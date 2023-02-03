package software.amazon.organizations.organizationalunit;

import java.time.Duration;

import software.amazon.awssdk.services.organizations.OrganizationsClient;
import software.amazon.awssdk.services.organizations.model.DescribeOrganizationalUnitRequest;
import software.amazon.awssdk.services.organizations.model.DescribeOrganizationalUnitResponse;
import software.amazon.awssdk.services.organizations.model.ListOrganizationalUnitsForParentRequest;
import software.amazon.awssdk.services.organizations.model.ListTagsForResourceRequest;
import software.amazon.awssdk.services.organizations.model.ListTagsForResourceResponse;
import software.amazon.awssdk.services.organizations.model.ListParentsRequest;
import software.amazon.awssdk.services.organizations.model.ListParentsResponse;
import software.amazon.awssdk.services.organizations.model.OrganizationalUnitNotFoundException;
import software.amazon.awssdk.services.organizations.model.OrganizationalUnit;
import software.amazon.awssdk.services.organizations.model.Parent;
import software.amazon.awssdk.services.organizations.model.ServiceException;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.HandlerErrorCode;
import software.amazon.cloudformation.proxy.OperationStatus;
import software.amazon.cloudformation.proxy.ProgressEvent;
import software.amazon.cloudformation.proxy.ProxyClient;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

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

    @Test
    public void handleRequest_SimpleSuccess() {
        final ResourceModel model = ResourceModel.builder()
            .id(TEST_OU_ID)
            .build();

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
            .desiredResourceState(model)
            .build();

        final DescribeOrganizationalUnitResponse describeOrganizationalUnitResponse = DescribeOrganizationalUnitResponse.builder()
            .organizationalUnit(OrganizationalUnit.builder()
                .name(TEST_OU_NAME)
                .arn(TEST_OU_ARN)
                .id(TEST_OU_ID)
                .build()
            ).build();

        final ListParentsResponse listParentsResponse = ListParentsResponse.builder()
            .parents(Parent.builder()
                .id(TEST_PARENT_ID)
                .build()
            ).build();

        final ListTagsForResourceResponse listTagsForResourceResponse = TagTestResourcesHelper.buildDefaultTagsResponse();

        when(mockProxyClient.client().describeOrganizationalUnit(any(DescribeOrganizationalUnitRequest.class))).thenReturn(describeOrganizationalUnitResponse);
        when(mockProxyClient.client().listParents(any(ListParentsRequest.class))).thenReturn(listParentsResponse);
        when(mockProxyClient.client().listTagsForResource(any(ListTagsForResourceRequest.class))).thenReturn(listTagsForResourceResponse);

        final ProgressEvent<ResourceModel, CallbackContext> response = readHandler.handleRequest(mockAwsClientProxy, request, new CallbackContext(), mockProxyClient, logger);

        verifySuccessResponse(response);

        verify(mockProxyClient.client()).describeOrganizationalUnit(any(DescribeOrganizationalUnitRequest.class));
        verify(mockProxyClient.client()).listParents(any(ListParentsRequest.class));
        verify(mockProxyClient.client()).listTagsForResource(any(ListTagsForResourceRequest.class));
    }

    private static void verifySuccessResponse(ProgressEvent<ResourceModel, CallbackContext> response) {
        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.SUCCESS);
        assertThat(response.getCallbackDelaySeconds()).isEqualTo(0);
        assertThat(response.getResourceModel()).isNotNull();
        assertThat(response.getResourceModel().getName()).isEqualTo(TEST_OU_NAME);
        assertThat(response.getResourceModel().getArn()).isEqualTo(TEST_OU_ARN);
        assertThat(response.getResourceModel().getId()).isEqualTo(TEST_OU_ID);
        assertThat(TagTestResourcesHelper.tagsEqual(response.getResourceModel().getTags(), TagTestResourcesHelper.defaultTags));
        assertThat(response.getResourceModels()).isNull();
        assertThat(response.getMessage()).isNull();
        assertThat(response.getErrorCode()).isNull();
    }

    @Test
    public void handleRequest_Fails_With_CfnNotFoundException() {
        final ResourceModel model = ResourceModel.builder()
            .id(TEST_OU_ID)
            .build();

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
            .desiredResourceState(model)
            .build();

        when(mockProxyClient.client().describeOrganizationalUnit(any(DescribeOrganizationalUnitRequest.class))).thenThrow(OrganizationalUnitNotFoundException.class);

        final ProgressEvent<ResourceModel, CallbackContext> response = readHandler.handleRequest(mockAwsClientProxy, request, new CallbackContext(), mockProxyClient, logger);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.FAILED);
        assertThat(response.getResourceModels()).isNull();
        assertThat(response.getErrorCode()).isEqualTo(HandlerErrorCode.NotFound);
    }


    @Test
    public void handleRequest_shouldReturnFailed_withServiceException_forDescribeOrganizationalUnitsCalls() {
        final ResourceModel model = ResourceModel.builder()
                .id(TEST_OU_ID)
                .build();

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
                .desiredResourceState(model)
                .build();

        when(mockProxyClient.client().describeOrganizationalUnit(any(DescribeOrganizationalUnitRequest.class))).thenThrow(ServiceException.class);

        final ProgressEvent<ResourceModel, CallbackContext> response = readHandler.handleRequest(mockAwsClientProxy, request, new CallbackContext(), mockProxyClient, logger);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.FAILED);
        assertThat(response.getResourceModels()).isNull();
        assertThat(response.getErrorCode()).isEqualTo(HandlerErrorCode.ServiceInternalError);
        verify(mockProxyClient.client()).describeOrganizationalUnit(any(DescribeOrganizationalUnitRequest.class));
    }

}
