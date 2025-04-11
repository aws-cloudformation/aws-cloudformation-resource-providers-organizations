package software.amazon.organizations.organizationalunit;

import java.time.Duration;
import java.util.Collections;
import java.util.HashSet;

import software.amazon.awssdk.services.organizations.OrganizationsClient;
import software.amazon.awssdk.services.organizations.model.DescribeOrganizationalUnitRequest;
import software.amazon.awssdk.services.organizations.model.DescribeOrganizationalUnitResponse;
import software.amazon.awssdk.services.organizations.model.DuplicateOrganizationalUnitException;
import software.amazon.awssdk.services.organizations.model.ListOrganizationalUnitsForParentRequest;
import software.amazon.awssdk.services.organizations.model.ListOrganizationalUnitsForParentResponse;
import software.amazon.awssdk.services.organizations.model.CreateOrganizationalUnitRequest;
import software.amazon.awssdk.services.organizations.model.CreateOrganizationalUnitResponse;
import software.amazon.awssdk.services.organizations.model.ListTagsForResourceResponse;
import software.amazon.awssdk.services.organizations.model.ListTagsForResourceRequest;
import software.amazon.awssdk.services.organizations.model.ListParentsRequest;
import software.amazon.awssdk.services.organizations.model.ListParentsResponse;
import software.amazon.awssdk.services.organizations.model.OrganizationalUnit;
import software.amazon.awssdk.services.organizations.model.Parent;
import software.amazon.awssdk.services.organizations.model.ServiceException;
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
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static software.amazon.organizations.organizationalunit.TagTestResourcesHelper.defaultStackTags;

@ExtendWith(MockitoExtension.class)
public class CreateHandlerTest extends AbstractTestBase {

    @Mock
    private AmazonWebServicesClientProxy mockAwsClientProxy;

    @Mock
    private ProxyClient<OrganizationsClient> mockProxyClient;

    @Mock
    OrganizationsClient mockOrgsClient;

    private CreateHandler createHandler;
    final private String TEST_EXCEPTION_MESSAGE = "Test exception message";

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

        final ListOrganizationalUnitsForParentResponse listOUResponse = ListOrganizationalUnitsForParentResponse.builder()
                .organizationalUnits(Collections.emptyList())
                .build();
        when(mockProxyClient.client().listOrganizationalUnitsForParent(any(ListOrganizationalUnitsForParentRequest.class)))
                .thenReturn(listOUResponse);

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
            .desiredResourceState(model)
            .desiredResourceTags(defaultStackTags)
            .build();

        final CreateOrganizationalUnitResponse createOrganizationalUnitResponse = getCreateOrganizationalUnitResponse();
        final DescribeOrganizationalUnitResponse describeOrganizationalUnitResponse = getDescribeOrganizationalUnitResponse();
        final ListParentsResponse listParentsResponse = getListParentsResponse();
        final ListTagsForResourceResponse listTagsForResourceResponse = TagTestResourcesHelper.buildDefaultTagsResponse();

        when(mockProxyClient.client().createOrganizationalUnit(any(CreateOrganizationalUnitRequest.class))).thenReturn(createOrganizationalUnitResponse);
        when(mockProxyClient.client().describeOrganizationalUnit(any(DescribeOrganizationalUnitRequest.class))).thenReturn(describeOrganizationalUnitResponse);
        when(mockProxyClient.client().listParents(any(ListParentsRequest.class))).thenReturn(listParentsResponse);
        when(mockProxyClient.client().listTagsForResource(any(ListTagsForResourceRequest.class))).thenReturn(listTagsForResourceResponse);

        ProgressEvent<ResourceModel, CallbackContext> response = null;
        do {
            final CallbackContext callbackContext = (response == null) ? new CallbackContext() : response.getCallbackContext();
            response = createHandler.handleRequest(mockAwsClientProxy, request, callbackContext, mockProxyClient, logger);

            if (response.getStatus().equals(OperationStatus.IN_PROGRESS)) {
                assertThat(response.getCallbackDelaySeconds()).isEqualTo(CALLBACK_DELAY);
            }

        } while (response.getStatus().equals(OperationStatus.IN_PROGRESS));

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.SUCCESS);
        assertThat(response.getCallbackDelaySeconds()).isEqualTo(0);
        assertThat(response.getResourceModel()).isNotNull();
        assertThat(response.getResourceModel().getName()).isEqualTo(TEST_OU_NAME);
        assertThat(response.getResourceModel().getArn()).isEqualTo(TEST_OU_ARN);
        assertThat(response.getResourceModel().getId()).isEqualTo(TEST_OU_ID);
        assertThat(TagTestResourcesHelper.tagsEqual(
                TagsHelper.convertOrganizationalUnitTagToOrganizationTag(response.getResourceModel().getTags()),
                TagTestResourcesHelper.defaultTags)).isTrue();
        assertThat(response.getResourceModels()).isNull();
        assertThat(response.getMessage()).isNull();
        assertThat(response.getErrorCode()).isNull();

        verify(mockProxyClient.client()).listOrganizationalUnitsForParent(any(ListOrganizationalUnitsForParentRequest.class));
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

        final ListOrganizationalUnitsForParentResponse listOUResponse = ListOrganizationalUnitsForParentResponse.builder()
                .organizationalUnits(Collections.emptyList())
                .build();
        when(mockProxyClient.client().listOrganizationalUnitsForParent(any(ListOrganizationalUnitsForParentRequest.class)))
                .thenReturn(listOUResponse);

        final CreateOrganizationalUnitResponse createOrganizationalUnitResponse = getCreateOrganizationalUnitResponse();
        final DescribeOrganizationalUnitResponse describeOrganizationalUnitResponse = getDescribeOrganizationalUnitResponse();
        final ListParentsResponse listParentsResponse = getListParentsResponse();
        final ListTagsForResourceResponse listTagsForResourceResponse = TagTestResourcesHelper.buildEmptyTagsResponse();

        when(mockProxyClient.client().createOrganizationalUnit(any(CreateOrganizationalUnitRequest.class))).thenReturn(createOrganizationalUnitResponse);
        when(mockProxyClient.client().describeOrganizationalUnit(any(DescribeOrganizationalUnitRequest.class))).thenReturn(describeOrganizationalUnitResponse);
        when(mockProxyClient.client().listParents(any(ListParentsRequest.class))).thenReturn(listParentsResponse);
        when(mockProxyClient.client().listTagsForResource(any(ListTagsForResourceRequest.class))).thenReturn(listTagsForResourceResponse);

        ProgressEvent<ResourceModel, CallbackContext> response = null;
        do {
            final CallbackContext callbackContext = (response == null) ? new CallbackContext() : response.getCallbackContext();
            response = createHandler.handleRequest(mockAwsClientProxy, request, callbackContext, mockProxyClient, logger);

            if (response.getStatus().equals(OperationStatus.IN_PROGRESS)) {
                assertThat(response.getCallbackDelaySeconds()).isEqualTo(CALLBACK_DELAY);
            }

        } while (response.getStatus().equals(OperationStatus.IN_PROGRESS));

        assertThat(response.getResourceModel().getTags()).isEqualTo(new HashSet<Tag>());

        verify(mockProxyClient.client()).listOrganizationalUnitsForParent(any(ListOrganizationalUnitsForParentRequest.class));
        verify(mockProxyClient.client()).listTagsForResource(any(ListTagsForResourceRequest.class));
        verify(mockProxyClient.client()).listParents(any(ListParentsRequest.class));
        verify(mockProxyClient.client()).createOrganizationalUnit(any(CreateOrganizationalUnitRequest.class));
    }

    @Test
    public void handleRequest_Fails_With_CfnAlreadyExistsException() {
        final ResourceModel model = generateCreateResourceModel();

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
            .desiredResourceState(model)
            .desiredResourceTags(defaultStackTags)
            .build();

        final ListOrganizationalUnitsForParentResponse listOUResponse = ListOrganizationalUnitsForParentResponse.builder()
                .organizationalUnits(Collections.singletonList(
                        OrganizationalUnit.builder()
                                .id(TEST_OU_ID)
                                .name(model.getName())
                                .build()
                ))
                .build();
        when(mockProxyClient.client().listOrganizationalUnitsForParent(any(ListOrganizationalUnitsForParentRequest.class)))
                .thenReturn(listOUResponse);

        final ProgressEvent<ResourceModel, CallbackContext> response = createHandler.handleRequest(mockAwsClientProxy, request, new CallbackContext(), mockProxyClient, logger);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.FAILED);
        assertThat(response.getResourceModels()).isNull();
        assertThat(response.getErrorCode()).isEqualTo(HandlerErrorCode.AlreadyExists);
        assertThat(response.getMessage()).contains(String.format("OrganizationalUnit with name [%s] already exists", model.getName()));

        verify(mockProxyClient.client()).listOrganizationalUnitsForParent(any(ListOrganizationalUnitsForParentRequest.class));
        verify(mockProxyClient.client(), times(0)).createOrganizationalUnit(any(CreateOrganizationalUnitRequest.class));
    }

    @Test
    public void handleRequest_OrganizationalUnitAlreadyExists() {
        final ResourceModel model = generateCreateResourceModel();

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
                .desiredResourceState(model)
                .build();

        final ListOrganizationalUnitsForParentResponse listOUResponse = ListOrganizationalUnitsForParentResponse.builder()
                .organizationalUnits(Collections.singletonList(
                        OrganizationalUnit.builder()
                                .id(TEST_OU_ID)
                                .name(model.getName())
                                .build()
                ))
                .build();
        when(mockProxyClient.client().listOrganizationalUnitsForParent(any(ListOrganizationalUnitsForParentRequest.class)))
                .thenReturn(listOUResponse);

        final ProgressEvent<ResourceModel, CallbackContext> response = createHandler.handleRequest(mockAwsClientProxy, request, new CallbackContext(), mockProxyClient, logger);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.FAILED);
        assertThat(response.getCallbackDelaySeconds()).isEqualTo(0);
        assertThat(response.getResourceModel()).isNotNull();
        assertThat(response.getResourceModel().getId()).isEqualTo(TEST_OU_ID);
        assertThat(response.getResourceModel().getName()).isEqualTo(model.getName());
        assertThat(response.getResourceModel().getParentId()).isEqualTo(model.getParentId());
        assertThat(response.getResourceModels()).isNull();
        assertThat(response.getErrorCode()).isEqualTo(HandlerErrorCode.AlreadyExists);
        assertThat(response.getMessage()).contains(String.format("OrganizationalUnit with name [%s] already exists", model.getName()));

        verify(mockProxyClient.client()).listOrganizationalUnitsForParent(any(ListOrganizationalUnitsForParentRequest.class));
        verify(mockProxyClient.client(), times(0)).createOrganizationalUnit(any(CreateOrganizationalUnitRequest.class));
    }

    @Test
    public void handleRequest_OrganizationalUnitDoesNotExist() {
        final ResourceModel model = generateCreateResourceModel();

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
                .desiredResourceState(model)
                .build();

        final ListOrganizationalUnitsForParentResponse listOUResponse = ListOrganizationalUnitsForParentResponse.builder()
                .organizationalUnits(Collections.emptyList())
                .build();
        when(mockProxyClient.client().listOrganizationalUnitsForParent(any(ListOrganizationalUnitsForParentRequest.class)))
                .thenReturn(listOUResponse);

        final CreateOrganizationalUnitResponse createOrganizationalUnitResponse = getCreateOrganizationalUnitResponse();
        final DescribeOrganizationalUnitResponse describeOrganizationalUnitResponse = getDescribeOrganizationalUnitResponse();
        final ListParentsResponse listParentsResponse = getListParentsResponse();
        final ListTagsForResourceResponse listTagsForResourceResponse = TagTestResourcesHelper.buildEmptyTagsResponse();

        when(mockProxyClient.client().createOrganizationalUnit(any(CreateOrganizationalUnitRequest.class))).thenReturn(createOrganizationalUnitResponse);
        when(mockProxyClient.client().describeOrganizationalUnit(any(DescribeOrganizationalUnitRequest.class))).thenReturn(describeOrganizationalUnitResponse);
        when(mockProxyClient.client().listParents(any(ListParentsRequest.class))).thenReturn(listParentsResponse);
        when(mockProxyClient.client().listTagsForResource(any(ListTagsForResourceRequest.class))).thenReturn(listTagsForResourceResponse);

        ProgressEvent<ResourceModel, CallbackContext> response = null;
        do {
            final CallbackContext callbackContext = (response == null) ? new CallbackContext() : response.getCallbackContext();
            response = createHandler.handleRequest(mockAwsClientProxy, request, callbackContext, mockProxyClient, logger);

            if (response.getStatus().equals(OperationStatus.IN_PROGRESS)) {
                assertThat(response.getCallbackDelaySeconds()).isEqualTo(CALLBACK_DELAY);
            }

        } while (response.getStatus().equals(OperationStatus.IN_PROGRESS));

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.SUCCESS);
        assertThat(response.getCallbackDelaySeconds()).isEqualTo(0);
        assertThat(response.getResourceModel()).isNotNull();
        assertThat(response.getResourceModel().getId()).isEqualTo(TEST_OU_ID);
        assertThat(response.getResourceModel().getName()).isEqualTo(model.getName());
        assertThat(response.getResourceModel().getParentId()).isEqualTo(model.getParentId());
        assertThat(response.getResourceModels()).isNull();
        assertThat(response.getMessage()).isNull();
        assertThat(response.getErrorCode()).isNull();

        verify(mockProxyClient.client()).listOrganizationalUnitsForParent(any(ListOrganizationalUnitsForParentRequest.class));
        verify(mockProxyClient.client()).createOrganizationalUnit(any(CreateOrganizationalUnitRequest.class));
        verify(mockProxyClient.client()).describeOrganizationalUnit(any(DescribeOrganizationalUnitRequest.class));
        verify(mockProxyClient.client()).listParents(any(ListParentsRequest.class));
        verify(mockProxyClient.client()).listTagsForResource(any(ListTagsForResourceRequest.class));
    }

    @Test
    public void handleRequest_OrganizationalUnitAlreadyExists_PaginationTest() {
        final ResourceModel model = generateCreateResourceModel();

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
                .desiredResourceState(model)
                .build();

        final ListOrganizationalUnitsForParentResponse listOUResponseFirst = ListOrganizationalUnitsForParentResponse.builder()
                .organizationalUnits(Collections.singletonList(
                        OrganizationalUnit.builder()
                                .id("TestOU")
                                .name("TestName")
                                .build()
                ))
                .nextToken("nextPageToken")
                .build();

        final ListOrganizationalUnitsForParentResponse listOUResponseSecond = ListOrganizationalUnitsForParentResponse.builder()
                .organizationalUnits(Collections.singletonList(
                        OrganizationalUnit.builder()
                                .id(TEST_OU_ID)
                                .name(model.getName())
                                .build()
                ))
                .build();

        when(mockProxyClient.client().listOrganizationalUnitsForParent(any(ListOrganizationalUnitsForParentRequest.class)))
                .thenReturn(listOUResponseFirst)
                .thenReturn(listOUResponseSecond);

        ProgressEvent<ResourceModel, CallbackContext> response = null;
        do {
            final CallbackContext callbackContext = (response == null) ? new CallbackContext() : response.getCallbackContext();
            response = createHandler.handleRequest(mockAwsClientProxy, request, callbackContext, mockProxyClient, logger);

            if (response.getStatus().equals(OperationStatus.IN_PROGRESS)) {
                assertThat(response.getCallbackDelaySeconds()).isEqualTo(CALLBACK_DELAY);
            }

        } while (response.getStatus().equals(OperationStatus.IN_PROGRESS));

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.FAILED);
        assertThat(response.getCallbackDelaySeconds()).isEqualTo(0);
        assertThat(response.getResourceModel()).isNotNull();
        assertThat(response.getResourceModel().getId()).isEqualTo(TEST_OU_ID);
        assertThat(response.getResourceModel().getName()).isEqualTo(model.getName());
        assertThat(response.getResourceModel().getParentId()).isEqualTo(model.getParentId());
        assertThat(response.getResourceModels()).isNull();
        assertThat(response.getErrorCode()).isEqualTo(HandlerErrorCode.AlreadyExists);
        assertThat(response.getMessage()).contains(String.format("OrganizationalUnit with name [%s] already exists", model.getName()));

        verify(mockProxyClient.client(), times(2)).listOrganizationalUnitsForParent(any(ListOrganizationalUnitsForParentRequest.class));
        verify(mockProxyClient.client(), times(0)).createOrganizationalUnit(any(CreateOrganizationalUnitRequest.class));

        verify(mockOrgsClient, atLeastOnce()).serviceName();
        verifyNoMoreInteractions(mockOrgsClient);
    }

    @Test
    public void handleRequest_ServiceExceptionOnCreateOuCall_ShouldFail() {
        final ResourceModel model = generateCreateResourceModel();

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
                .desiredResourceState(model)
                .build();

        // Simulate createOU invocation throwing ServiceException
        when(mockProxyClient.client().createOrganizationalUnit(any(CreateOrganizationalUnitRequest.class)))
                .thenThrow(ServiceException.builder()
                        .message(TEST_EXCEPTION_MESSAGE)
                        .build());

        ProgressEvent<ResourceModel, CallbackContext> response = null;
        do {
            final CallbackContext callbackContext = (response == null) ? new CallbackContext() : response.getCallbackContext();
            response = createHandler.handleRequest(mockAwsClientProxy, request, callbackContext, mockProxyClient, logger);

            if (response.getStatus().equals(OperationStatus.IN_PROGRESS)) {
                assertThat(response.getCallbackDelaySeconds()).isEqualTo(CALLBACK_DELAY);
            }

        } while (response.getStatus().equals(OperationStatus.IN_PROGRESS));

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.FAILED);
        assertThat(response.getCallbackDelaySeconds()).isEqualTo(0);
        assertThat(response.getResourceModel()).isNotNull();
        assertThat(response.getResourceModel().getId()).isNull();
        assertThat(response.getResourceModel().getName()).isEqualTo(model.getName());
        assertThat(response.getResourceModel().getParentId()).isEqualTo(model.getParentId());
        assertThat(response.getResourceModels()).isNull();
        assertThat(response.getMessage()).isEqualTo(TEST_EXCEPTION_MESSAGE);
        assertThat(response.getErrorCode()).isEqualTo(HandlerErrorCode.ServiceInternalError);

        verify(mockProxyClient.client()).listOrganizationalUnitsForParent(any(ListOrganizationalUnitsForParentRequest.class));
        verify(mockProxyClient.client()).createOrganizationalUnit(any(CreateOrganizationalUnitRequest.class));

        // Since handleError is invoked, verify that calls in ReadHandler handleRequest are never invoked
        verify(mockProxyClient.client(), never()).describeOrganizationalUnit(any(DescribeOrganizationalUnitRequest.class));
        verify(mockProxyClient.client(), never()).listParents(any(ListParentsRequest.class));
        verify(mockProxyClient.client(), never()).listTagsForResource(any(ListTagsForResourceRequest.class));
    }

    @Test
    public void handleRequest_ReInvokedAfterPreCheckPasses_OuAlreadyCreated_ShouldSwallowErrorAndSucceed() {
        final ResourceModel model = generateCreateResourceModel();

        // Simulate the first invocation being complete by setting PreExistenceCheckComplete true
        CallbackContext context = new CallbackContext();
        context.setPreExistenceCheckComplete(true);

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
                .desiredResourceState(model)
                .build();

        final DescribeOrganizationalUnitResponse describeOrganizationalUnitResponse = getDescribeOrganizationalUnitResponse();
        final ListParentsResponse listParentsResponse = getListParentsResponse();
        final ListTagsForResourceResponse listTagsForResourceResponse = TagTestResourcesHelper.buildEmptyTagsResponse();

        // Simulate first invocation already successfully created OU, giving DuplicateOrganizationalUnitException on second createOU invocation
        when(mockProxyClient.client().createOrganizationalUnit(any(CreateOrganizationalUnitRequest.class)))
                .thenThrow(DuplicateOrganizationalUnitException.builder()
                        .message(TEST_EXCEPTION_MESSAGE)
                        .build());

        when(mockProxyClient.client().describeOrganizationalUnit(any(DescribeOrganizationalUnitRequest.class))).thenReturn(describeOrganizationalUnitResponse);
        when(mockProxyClient.client().listParents(any(ListParentsRequest.class))).thenReturn(listParentsResponse);
        when(mockProxyClient.client().listTagsForResource(any(ListTagsForResourceRequest.class))).thenReturn(listTagsForResourceResponse);

        final ProgressEvent<ResourceModel, CallbackContext> response = createHandler.handleRequest(mockAwsClientProxy, request, context, mockProxyClient, logger);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.SUCCESS);
        assertThat(response.getCallbackDelaySeconds()).isEqualTo(0);
        assertThat(response.getResourceModel()).isNotNull();
        assertThat(response.getResourceModel().getId()).isEqualTo(TEST_OU_ID);
        assertThat(response.getResourceModel().getName()).isEqualTo(model.getName());
        assertThat(response.getResourceModel().getParentId()).isEqualTo(model.getParentId());
        assertThat(response.getResourceModels()).isNull();
        assertThat(response.getMessage()).isNull();
        assertThat(response.getErrorCode()).isNull();

        // Verify checkIfOrganizationalUnitExists is not reached since PreExistenceCheck is already complete
        verify(mockProxyClient.client(), never()).listOrganizationalUnitsForParent(any(ListOrganizationalUnitsForParentRequest.class));

        verify(mockProxyClient.client()).createOrganizationalUnit(any(CreateOrganizationalUnitRequest.class));
        verify(mockProxyClient.client()).describeOrganizationalUnit(any(DescribeOrganizationalUnitRequest.class));
        verify(mockProxyClient.client()).listParents(any(ListParentsRequest.class));
        verify(mockProxyClient.client()).listTagsForResource(any(ListTagsForResourceRequest.class));
    }

    @Test
    public void handleRequest_ReInvokedAfterPreCheckPasses_OuNotPresent_ShouldSucceed() {
        final ResourceModel model = generateCreateResourceModel();

        // Simulate the first invocation being complete by setting PreExistenceCheckComplete true
        CallbackContext context = new CallbackContext();
        context.setPreExistenceCheckComplete(true);

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
                .desiredResourceState(model)
                .build();

        final CreateOrganizationalUnitResponse createOrganizationalUnitResponse = getCreateOrganizationalUnitResponse();
        final DescribeOrganizationalUnitResponse describeOrganizationalUnitResponse = getDescribeOrganizationalUnitResponse();
        final ListParentsResponse listParentsResponse = getListParentsResponse();
        final ListTagsForResourceResponse listTagsForResourceResponse = TagTestResourcesHelper.buildEmptyTagsResponse();

        // Successful OU creation as OU not already present
        when(mockProxyClient.client().createOrganizationalUnit(any(CreateOrganizationalUnitRequest.class))).thenReturn(createOrganizationalUnitResponse);

        when(mockProxyClient.client().describeOrganizationalUnit(any(DescribeOrganizationalUnitRequest.class))).thenReturn(describeOrganizationalUnitResponse);
        when(mockProxyClient.client().listParents(any(ListParentsRequest.class))).thenReturn(listParentsResponse);
        when(mockProxyClient.client().listTagsForResource(any(ListTagsForResourceRequest.class))).thenReturn(listTagsForResourceResponse);

        ProgressEvent<ResourceModel, CallbackContext> response = null;
        do {
            final CallbackContext callbackContext = (response == null) ? context : response.getCallbackContext();
            response = createHandler.handleRequest(mockAwsClientProxy, request, callbackContext, mockProxyClient, logger);

            if (response.getStatus().equals(OperationStatus.IN_PROGRESS)) {
                assertThat(response.getCallbackDelaySeconds()).isEqualTo(CALLBACK_DELAY);
            }

        } while (response.getStatus().equals(OperationStatus.IN_PROGRESS));

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.SUCCESS);
        assertThat(response.getCallbackDelaySeconds()).isEqualTo(0);
        assertThat(response.getResourceModel()).isNotNull();
        assertThat(response.getResourceModel().getId()).isEqualTo(TEST_OU_ID);
        assertThat(response.getResourceModel().getName()).isEqualTo(model.getName());
        assertThat(response.getResourceModel().getParentId()).isEqualTo(model.getParentId());
        assertThat(response.getResourceModels()).isNull();
        assertThat(response.getMessage()).isNull();
        assertThat(response.getErrorCode()).isNull();

        // Verify checkIfOrganizationalUnitExists is not reached since PreExistenceCheck is already complete
        verify(mockProxyClient.client(), never()).listOrganizationalUnitsForParent(any(ListOrganizationalUnitsForParentRequest.class));

        verify(mockProxyClient.client()).createOrganizationalUnit(any(CreateOrganizationalUnitRequest.class));
        verify(mockProxyClient.client()).describeOrganizationalUnit(any(DescribeOrganizationalUnitRequest.class));
        verify(mockProxyClient.client()).listParents(any(ListParentsRequest.class));
        verify(mockProxyClient.client()).listTagsForResource(any(ListTagsForResourceRequest.class));
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
