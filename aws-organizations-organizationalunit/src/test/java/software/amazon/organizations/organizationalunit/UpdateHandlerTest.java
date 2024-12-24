package software.amazon.organizations.organizationalunit;

import java.time.Duration;
import java.util.HashSet;
import java.util.Set;

import software.amazon.awssdk.services.organizations.OrganizationsClient;
import software.amazon.awssdk.services.organizations.model.ConstraintViolationException;
import software.amazon.awssdk.services.organizations.model.DescribeOrganizationalUnitRequest;
import software.amazon.awssdk.services.organizations.model.DescribeOrganizationalUnitResponse;
import software.amazon.awssdk.services.organizations.model.ListTagsForResourceRequest;
import software.amazon.awssdk.services.organizations.model.ListTagsForResourceResponse;
import software.amazon.awssdk.services.organizations.model.ListParentsRequest;
import software.amazon.awssdk.services.organizations.model.ListParentsResponse;
import software.amazon.awssdk.services.organizations.model.OrganizationalUnitNotFoundException;
import software.amazon.awssdk.services.organizations.model.OrganizationalUnit;
import software.amazon.awssdk.services.organizations.model.Parent;
import software.amazon.awssdk.services.organizations.model.Tag;
import software.amazon.awssdk.services.organizations.model.TagResourceRequest;
import software.amazon.awssdk.services.organizations.model.UntagResourceRequest;
import software.amazon.awssdk.services.organizations.model.UpdateOrganizationalUnitRequest;
import software.amazon.awssdk.services.organizations.model.UpdateOrganizationalUnitResponse;
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
public class UpdateHandlerTest extends AbstractTestBase {

    @Mock
    private AmazonWebServicesClientProxy mockAwsClientProxy;

    @Mock
    private ProxyClient<OrganizationsClient> mockProxyClient;

    @Mock
    OrganizationsClient mockOrgsClient;

    private UpdateHandler updateHandler;

    @BeforeEach
    public void setup() {
        updateHandler = new UpdateHandler();
        mockAwsClientProxy = new AmazonWebServicesClientProxy(loggerProxy, MOCK_CREDENTIALS, () -> Duration.ofSeconds(600).toMillis());
        mockOrgsClient = mock(OrganizationsClient.class);
        mockProxyClient = MOCK_PROXY(mockAwsClientProxy, mockOrgsClient);
    }

    @Test
    public void handleRequest_SimpleSuccess() {
        final ResourceModel previousResourceModel = generatePreviousResourceModel();

        final ResourceModel model = generateUpdatedResourceModel();

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
            .previousResourceState(previousResourceModel)
            .desiredResourceState(model)
            .previousResourceTags(TagTestResourcesHelper.defaultStackTags)
            .desiredResourceTags(TagTestResourcesHelper.updatedStackTags)
            .build();

        final UpdateOrganizationalUnitResponse updateOrganizationalUnitResponse = getUpdateOrganizationalUnitResponse();
        final DescribeOrganizationalUnitResponse describeOrganizationalUnitResponse = getDescribeOrganizationalUnitResponse();
        final ListParentsResponse listParentsResponse = getListParentsResponse();
        final ListTagsForResourceResponse listTagsForResourceResponse = TagTestResourcesHelper.buildUpdatedTagsResponse();

        when(mockProxyClient.client().updateOrganizationalUnit(any(UpdateOrganizationalUnitRequest.class))).thenReturn(updateOrganizationalUnitResponse);
        when(mockProxyClient.client().describeOrganizationalUnit(any(DescribeOrganizationalUnitRequest.class))).thenReturn(describeOrganizationalUnitResponse);
        when(mockProxyClient.client().listParents(any(ListParentsRequest.class))).thenReturn(listParentsResponse);
        when(mockProxyClient.client().listTagsForResource(any(ListTagsForResourceRequest.class))).thenReturn(listTagsForResourceResponse);

        final ProgressEvent<ResourceModel, CallbackContext> response = updateHandler.handleRequest(mockAwsClientProxy, request, new CallbackContext(), mockProxyClient, logger);

        final Set<Tag> oldTags = TagsHelper.mergeTags(
                TagsHelper.convertOrganizationalUnitTagToOrganizationTag(previousResourceModel.getTags()),
                request.getPreviousResourceTags()
        );

        final Set<Tag> newTags = TagsHelper.mergeTags(
                TagsHelper.convertOrganizationalUnitTagToOrganizationTag(model.getTags()),
                request.getDesiredResourceTags()
        );

        final Set<Tag> tagsToAddOrUpdate = TagsHelper.getTagsToAddOrUpdate(oldTags, newTags);
        final Set<String> tagsToRemove = TagsHelper.getTagKeysToRemove(oldTags, newTags);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.SUCCESS);
        assertThat(response.getCallbackDelaySeconds()).isEqualTo(0);
        assertThat(response.getResourceModel()).isNotNull();
        assertThat(response.getResourceModel().getName()).isEqualTo(TEST_OU_UPDATED_NAME);
        assertThat(response.getResourceModel().getArn()).isEqualTo(TEST_OU_ARN);
        assertThat(response.getResourceModel().getId()).isEqualTo(TEST_OU_ID);
        assertThat(TagTestResourcesHelper.tagsEqual(
                TagsHelper.convertOrganizationalUnitTagToOrganizationTag(response.getResourceModel().getTags()),
                TagTestResourcesHelper.updatedTags)).isTrue();
        assertThat(TagTestResourcesHelper.correctTagsInTagAndUntagRequests(tagsToAddOrUpdate, tagsToRemove)).isTrue();
        assertThat(response.getResourceModels()).isNull();
        assertThat(response.getMessage()).isNull();
        assertThat(response.getErrorCode()).isNull();

        verify(mockProxyClient.client()).tagResource(any(TagResourceRequest.class));
        verify(mockProxyClient.client()).untagResource(any(UntagResourceRequest.class));
        verify(mockProxyClient.client()).listTagsForResource(any(ListTagsForResourceRequest.class));
        verify(mockProxyClient.client()).listParents(any(ListParentsRequest.class));
        verify(mockProxyClient.client()).updateOrganizationalUnit(any(UpdateOrganizationalUnitRequest.class));
        verify(mockProxyClient.client()).describeOrganizationalUnit(any(DescribeOrganizationalUnitRequest.class));
    }

    @Test
    public void handleRequestWithoutTags_SimpleSuccess() {
        final ResourceModel previousResourceModel = generatePreviousResourceModel();

        final ResourceModel model = generateUpdatedResourceModel();

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
            .previousResourceState(previousResourceModel)
            .desiredResourceState(model)
            .build();

        final UpdateOrganizationalUnitResponse updateOrganizationalUnitResponse = getUpdateOrganizationalUnitResponse();
        final DescribeOrganizationalUnitResponse describeOrganizationalUnitResponse = getDescribeOrganizationalUnitResponse();
        final ListParentsResponse listParentsResponse = getListParentsResponse();
        final ListTagsForResourceResponse listTagsForResourceResponse = TagTestResourcesHelper.buildEmptyTagsResponse();

        when(mockProxyClient.client().updateOrganizationalUnit(any(UpdateOrganizationalUnitRequest.class))).thenReturn(updateOrganizationalUnitResponse);
        when(mockProxyClient.client().describeOrganizationalUnit(any(DescribeOrganizationalUnitRequest.class))).thenReturn(describeOrganizationalUnitResponse);
        when(mockProxyClient.client().listParents(any(ListParentsRequest.class))).thenReturn(listParentsResponse);
        when(mockProxyClient.client().listTagsForResource(any(ListTagsForResourceRequest.class))).thenReturn(listTagsForResourceResponse);

        final ProgressEvent<ResourceModel, CallbackContext> response = updateHandler.handleRequest(mockAwsClientProxy, request, new CallbackContext(), mockProxyClient, logger);

        assertThat(response.getResourceModel().getTags()).isEqualTo(new HashSet<Tag>());

        verify(mockProxyClient.client()).updateOrganizationalUnit(any(UpdateOrganizationalUnitRequest.class));
    }

    @Test
    public void handleRequest_withNullPreviousModel_Success() {
        final ResourceModel model = generateUpdatedResourceModel();

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
            .previousResourceState(null)
            .desiredResourceState(model)
            .build();

        final UpdateOrganizationalUnitResponse updateOrganizationalUnitResponse = getUpdateOrganizationalUnitResponse();
        final DescribeOrganizationalUnitResponse describeOrganizationalUnitResponse = getDescribeOrganizationalUnitResponse();
        final ListParentsResponse listParentsResponse = getListParentsResponse();
        final ListTagsForResourceResponse listTagsForResourceResponse = TagTestResourcesHelper.buildEmptyTagsResponse();

        when(mockProxyClient.client().updateOrganizationalUnit(any(UpdateOrganizationalUnitRequest.class))).thenReturn(updateOrganizationalUnitResponse);
        when(mockProxyClient.client().describeOrganizationalUnit(any(DescribeOrganizationalUnitRequest.class))).thenReturn(describeOrganizationalUnitResponse);
        when(mockProxyClient.client().listParents(any(ListParentsRequest.class))).thenReturn(listParentsResponse);
        when(mockProxyClient.client().listTagsForResource(any(ListTagsForResourceRequest.class))).thenReturn(listTagsForResourceResponse);

        final ProgressEvent<ResourceModel, CallbackContext> response = updateHandler.handleRequest(mockAwsClientProxy, request, new CallbackContext(), mockProxyClient, logger);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.SUCCESS);
        assertThat(response.getResourceModels()).isNull();
    }

    @Test
    public void handleRequest_WithTags_UntagResourceFails_Fails_With_CfnServiceLimitExceededException() {
        final ResourceModel previousResourceModel = generatePreviousResourceModel();

        final ResourceModel model = generateUpdatedResourceModel();

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
            .previousResourceState(previousResourceModel)
            .desiredResourceState(model)
            .build();

        when(mockProxyClient.client().untagResource(any(UntagResourceRequest.class))).thenThrow(ConstraintViolationException.class);

        final ProgressEvent<ResourceModel, CallbackContext> response = updateHandler.handleRequest(mockAwsClientProxy, request, new CallbackContext(), mockProxyClient, logger);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.FAILED);
        assertThat(response.getResourceModels()).isNull();
        assertThat(response.getErrorCode()).isEqualTo(HandlerErrorCode.ServiceLimitExceeded);

        verify(mockProxyClient.client()).updateOrganizationalUnit(any(UpdateOrganizationalUnitRequest.class));
        verify(mockProxyClient.client()).untagResource(any(UntagResourceRequest.class));
    }

    @Test
    public void handleRequest_WithTags_TagResourceFails_Fails_With_CfnServiceLimitExceededException() {
        final ResourceModel previousResourceModel = generatePreviousResourceModel();

        final ResourceModel model = generateUpdatedResourceModel();

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
            .previousResourceState(previousResourceModel)
            .desiredResourceState(model)
            .build();

        when(mockProxyClient.client().tagResource(any(TagResourceRequest.class))).thenThrow(ConstraintViolationException.class);

        final ProgressEvent<ResourceModel, CallbackContext> response = updateHandler.handleRequest(mockAwsClientProxy, request, new CallbackContext(), mockProxyClient, logger);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.FAILED);
        assertThat(response.getResourceModels()).isNull();
        assertThat(response.getErrorCode()).isEqualTo(HandlerErrorCode.ServiceLimitExceeded);

        verify(mockProxyClient.client()).updateOrganizationalUnit(any(UpdateOrganizationalUnitRequest.class));
        verify(mockProxyClient.client()).untagResource(any(UntagResourceRequest.class));
        verify(mockProxyClient.client()).tagResource(any(TagResourceRequest.class));
    }

    @Test
    public void handleRequest_Fails_With_CfnNotFoundException() {
        final ResourceModel previousResourceModel = ResourceModel.builder()
            .name(TEST_OU_NAME)
            .id(TEST_OU_ID)
            .build();

        final ResourceModel model = ResourceModel.builder()
            .name(TEST_OU_UPDATED_NAME)
            .id(TEST_OU_ID)
            .build();

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
            .previousResourceState(previousResourceModel)
            .desiredResourceState(model)
            .build();

        when(mockProxyClient.client().updateOrganizationalUnit(any(UpdateOrganizationalUnitRequest.class))).thenThrow(OrganizationalUnitNotFoundException.class);

        final ProgressEvent<ResourceModel, CallbackContext> response = updateHandler.handleRequest(mockAwsClientProxy, request, new CallbackContext(), mockProxyClient, logger);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.FAILED);
        assertThat(response.getResourceModels()).isNull();
        assertThat(response.getErrorCode()).isEqualTo(HandlerErrorCode.NotFound);
    }

    @Test
    public void handleRequest_Fails_With_CfnNotUpdatableException() {
        final ResourceModel previousResourceModel = ResourceModel.builder()
            .name(TEST_OU_NAME)
            .id(TEST_OU_ID)
            .build();

        final ResourceModel model = ResourceModel.builder()
            .name(TEST_OU_UPDATED_NAME)
            .id(TEST_OU_ID_CHANGED)
            .build();

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
            .previousResourceState(previousResourceModel)
            .desiredResourceState(model)
            .build();

        final ProgressEvent<ResourceModel, CallbackContext> response = updateHandler.handleRequest(mockAwsClientProxy, request, new CallbackContext(), mockProxyClient, logger);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.FAILED);
        assertThat(response.getResourceModels()).isNull();
        assertThat(response.getErrorCode()).isEqualTo(HandlerErrorCode.NotUpdatable);
    }

    protected ResourceModel generatePreviousResourceModel() {
        return ResourceModel.builder()
            .name(TEST_OU_NAME)
            .id(TEST_OU_ID)
            .tags(TagTestResourcesHelper.translateOrganizationTagsToOrganizationalUnitTags(TagTestResourcesHelper.defaultTags))
            .build();
    }

    protected ResourceModel generateUpdatedResourceModel() {
        return ResourceModel.builder()
            .name(TEST_OU_UPDATED_NAME)
            .id(TEST_OU_ID)
            .tags(TagTestResourcesHelper.translateOrganizationTagsToOrganizationalUnitTags(TagTestResourcesHelper.updatedTags))
            .build();
    }

    protected UpdateOrganizationalUnitResponse getUpdateOrganizationalUnitResponse() {
        return UpdateOrganizationalUnitResponse.builder()
            .organizationalUnit(OrganizationalUnit.builder()
                .name(TEST_OU_UPDATED_NAME)
                .arn(TEST_OU_ARN)
                .id(TEST_OU_ID)
                .build()
            ).build();
    }

    protected DescribeOrganizationalUnitResponse getDescribeOrganizationalUnitResponse() {
        return DescribeOrganizationalUnitResponse.builder()
            .organizationalUnit(OrganizationalUnit.builder()
                .name(TEST_OU_UPDATED_NAME)
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
