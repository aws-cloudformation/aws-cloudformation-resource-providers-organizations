package software.amazon.organizations.organizationalunit;

import java.time.Duration;
import java.util.HashSet;

import software.amazon.awssdk.services.organizations.OrganizationsClient;
import software.amazon.awssdk.services.organizations.model.DescribeOrganizationalUnitRequest;
import software.amazon.awssdk.services.organizations.model.DescribeOrganizationalUnitResponse;
import software.amazon.awssdk.services.organizations.model.DuplicateOrganizationalUnitException;
import software.amazon.awssdk.services.organizations.model.CreateOrganizationalUnitRequest;
import software.amazon.awssdk.services.organizations.model.CreateOrganizationalUnitResponse;
import software.amazon.awssdk.services.organizations.model.ListTagsForResourceResponse;
import software.amazon.awssdk.services.organizations.model.ListTagsForResourceRequest;
import software.amazon.awssdk.services.organizations.model.ListParentsRequest;
import software.amazon.awssdk.services.organizations.model.ListParentsResponse;
import software.amazon.awssdk.services.organizations.model.OrganizationalUnit;
import software.amazon.awssdk.services.organizations.model.Parent;
import software.amazon.awssdk.services.organizations.model.Tag;
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
        mockAwsClientProxy = new AmazonWebServicesClientProxy(loggerProxy, MOCK_CREDENTIALS, () -> Duration.ofSeconds(600).toMillis());
        mockOrgsClient = mock(OrganizationsClient.class);
        mockProxyClient = MOCK_PROXY(mockAwsClientProxy, mockOrgsClient);
    }

    @Test
    public void handleRequest_SimpleSuccess() {
        final ResourceModel model = generateCreateResourceModel();

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
            .desiredResourceState(model)
            .build();

        final CreateOrganizationalUnitResponse createOrganizationalUnitResponse = getCreateOrganizationalUnitResponse();
        final DescribeOrganizationalUnitResponse describeOrganizationalUnitResponse = getDescribeOrganizationalUnitResponse();
        final ListParentsResponse listParentsResponse = getListParentsResponse();
        final ListTagsForResourceResponse listTagsForResourceResponse = TagTestResourcesHelper.buildDefaultTagsResponse();

        when(mockProxyClient.client().createOrganizationalUnit(any(CreateOrganizationalUnitRequest.class))).thenReturn(createOrganizationalUnitResponse);
        when(mockProxyClient.client().describeOrganizationalUnit(any(DescribeOrganizationalUnitRequest.class))).thenReturn(describeOrganizationalUnitResponse);
        when(mockProxyClient.client().listParents(any(ListParentsRequest.class))).thenReturn(listParentsResponse);
        when(mockProxyClient.client().listTagsForResource(any(ListTagsForResourceRequest.class))).thenReturn(listTagsForResourceResponse);

        final ProgressEvent<ResourceModel, CallbackContext> response = createHandler.handleRequest(mockAwsClientProxy, request, new CallbackContext(), mockProxyClient, logger);

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

        verify(mockProxyClient.client()).listTagsForResource(any(ListTagsForResourceRequest.class));
        verify(mockProxyClient.client()).listParents(any(ListParentsRequest.class));
        verify(mockProxyClient.client()).createOrganizationalUnit(any(CreateOrganizationalUnitRequest.class));
    }

    @Test
    public void handleRequestWithoutTags_SimpleSuccess() {
        final ResourceModel model = generateCreateResourceModelWithoutTags();

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
            .desiredResourceState(model)
            .build();

        final CreateOrganizationalUnitResponse createOrganizationalUnitResponse = getCreateOrganizationalUnitResponse();
        final DescribeOrganizationalUnitResponse describeOrganizationalUnitResponse = getDescribeOrganizationalUnitResponse();
        final ListParentsResponse listParentsResponse = getListParentsResponse();
        final ListTagsForResourceResponse listTagsForResourceResponse = TagTestResourcesHelper.buildEmptyTagsResponse();

        when(mockProxyClient.client().createOrganizationalUnit(any(CreateOrganizationalUnitRequest.class))).thenReturn(createOrganizationalUnitResponse);
        when(mockProxyClient.client().describeOrganizationalUnit(any(DescribeOrganizationalUnitRequest.class))).thenReturn(describeOrganizationalUnitResponse);
        when(mockProxyClient.client().listParents(any(ListParentsRequest.class))).thenReturn(listParentsResponse);
        when(mockProxyClient.client().listTagsForResource(any(ListTagsForResourceRequest.class))).thenReturn(listTagsForResourceResponse);

        final ProgressEvent<ResourceModel, CallbackContext> response = createHandler.handleRequest(mockAwsClientProxy, request, new CallbackContext(), mockProxyClient, logger);

        assertThat(response.getResourceModel().getTags()).isEqualTo(new HashSet<Tag>());

        verify(mockProxyClient.client()).listTagsForResource(any(ListTagsForResourceRequest.class));
        verify(mockProxyClient.client()).listParents(any(ListParentsRequest.class));
        verify(mockProxyClient.client()).createOrganizationalUnit(any(CreateOrganizationalUnitRequest.class));
    }

    @Test
    public void handleRequest_Fails_With_CfnAlreadyExistsException() {
        final ResourceModel model = generateCreateResourceModel();

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
            .desiredResourceState(model)
            .build();

        when(mockProxyClient.client().createOrganizationalUnit(any(CreateOrganizationalUnitRequest.class))).thenThrow(DuplicateOrganizationalUnitException.class);

        final ProgressEvent<ResourceModel, CallbackContext> response = createHandler.handleRequest(mockAwsClientProxy, request, new CallbackContext(), mockProxyClient, logger);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.FAILED);
        assertThat(response.getResourceModels()).isNull();
        assertThat(response.getErrorCode()).isEqualTo(HandlerErrorCode.AlreadyExists);
    }


    protected ResourceModel generateCreateResourceModel() {
        ResourceModel model = ResourceModel.builder()
            .name(TEST_OU_NAME)
            .parentId(TEST_PARENT_ID)
            .tags(TagTestResourcesHelper.translateOrganizationTagsToOrganizationalUnitTags(TagTestResourcesHelper.defaultTags))
            .build();
        return model;
    }

    protected ResourceModel generateCreateResourceModelWithoutTags() {
        ResourceModel model = ResourceModel.builder()
            .name(TEST_OU_NAME)
            .build();
        return model;
    }

    protected CreateOrganizationalUnitResponse getCreateOrganizationalUnitResponse() {
        return CreateOrganizationalUnitResponse.builder()
            .organizationalUnit(OrganizationalUnit.builder()
                .name(TEST_OU_NAME)
                .arn(TEST_OU_ARN)
                .id(TEST_OU_ID)
                .build()
            ).build();
    }

    protected DescribeOrganizationalUnitResponse getDescribeOrganizationalUnitResponse() {
        return DescribeOrganizationalUnitResponse.builder()
            .organizationalUnit(OrganizationalUnit.builder()
                .name(TEST_OU_NAME)
                .arn(TEST_OU_ARN)
                .id(TEST_OU_ID)
                .build()
            ).build();
    }

    protected ListParentsResponse getListParentsResponse() {
        return ListParentsResponse.builder()
            .parents(Parent.builder()
                .id(TEST_PARENT_ID)
                .build()
            ).build();
    }
}
