package software.amazon.organizations.account;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.account.AccountClient;
import software.amazon.awssdk.services.account.model.AlternateContactType;
import software.amazon.awssdk.services.account.model.GetAlternateContactRequest;
import software.amazon.awssdk.services.account.model.GetAlternateContactResponse;
import software.amazon.awssdk.services.account.model.ResourceNotFoundException;
import software.amazon.awssdk.services.organizations.OrganizationsClient;
import software.amazon.awssdk.services.organizations.model.Account;
import software.amazon.awssdk.services.organizations.model.AccountNotFoundException;
import software.amazon.awssdk.services.organizations.model.DescribeAccountRequest;
import software.amazon.awssdk.services.organizations.model.DescribeAccountResponse;
import software.amazon.awssdk.services.organizations.model.ListParentsRequest;
import software.amazon.awssdk.services.organizations.model.ListParentsResponse;
import software.amazon.awssdk.services.organizations.model.ListTagsForResourceRequest;
import software.amazon.awssdk.services.organizations.model.ListTagsForResourceResponse;
import software.amazon.awssdk.services.organizations.model.Parent;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.HandlerErrorCode;
import software.amazon.cloudformation.proxy.OperationStatus;
import software.amazon.cloudformation.proxy.ProgressEvent;
import software.amazon.cloudformation.proxy.ProxyClient;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class ReadHandlerTest extends AbstractTestBase {

    @Mock
    OrganizationsClient mockOrgsClient;
    AccountClient mockAccountClient;
    @Mock
    private AmazonWebServicesClientProxy mockAwsClientProxy;
    @Mock
    private ProxyClient<OrganizationsClient> mockProxyClient;
    private ProxyClient<AccountClient> mockAccountProxyClient;
    private ReadHandler readHandler;

    @BeforeEach
    public void setup() {
        readHandler = new ReadHandler();
        mockAwsClientProxy = new AmazonWebServicesClientProxy(logger, MOCK_CREDENTIALS, () -> Duration.ofSeconds(600).toMillis());
        mockOrgsClient = mock(OrganizationsClient.class);
        mockAccountClient = mock(AccountClient.class);
        mockProxyClient = MOCK_PROXY(mockAwsClientProxy, mockOrgsClient);
        mockAccountProxyClient = MOCK_ACCOUNT_PROXY(mockAwsClientProxy, mockAccountClient);
    }

    @Test
    public void handleRequest_SimpleSuccess() {
        final ResourceModel model = ResourceModel.builder()
                                        .accountId(TEST_ACCOUNT_ID)
                                        .build();

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
                                                                  .desiredResourceState(model)
                                                                  .build();

        final DescribeAccountResponse describeAccountResponse = DescribeAccountResponse.builder().account(Account.builder()
                                                                                                              .arn(TEST_ACCOUNT_ARN)
                                                                                                              .email(TEST_ACCOUNT_EMAIL)
                                                                                                              .id(TEST_ACCOUNT_ID)
                                                                                                              .name(TEST_ACCOUNT_NAME)
                                                                                                              .build()).build();

        final GetAlternateContactResponse getAlternateContactResponseBilling = GetAlternateContactResponse.builder()
                                                                                   .alternateContact(software.amazon.awssdk.services.account.model.AlternateContact.builder()
                                                                                                         .alternateContactType(AlternateContactType.BILLING)
                                                                                                         .emailAddress(TEST_ALTERNATE_CONTACT_EMAIL_BILLING)
                                                                                                         .name(TEST_ALTERNATE_CONTACT_NAME_BILLING)
                                                                                                         .phoneNumber(TEST_ALTERNATE_CONTACT_PHONE_BILLING)
                                                                                                         .title(TEST_ALTERNATE_CONTACT_TITLE_BILLING)
                                                                                                         .build())
                                                                                   .build();

        final GetAlternateContactResponse getAlternateContactResponseOperations = GetAlternateContactResponse.builder()
                                                                                      .alternateContact(software.amazon.awssdk.services.account.model.AlternateContact.builder()
                                                                                                            .alternateContactType(AlternateContactType.OPERATIONS)
                                                                                                            .emailAddress(TEST_ALTERNATE_CONTACT_EMAIL_OPERATIONS)
                                                                                                            .name(TEST_ALTERNATE_CONTACT_NAME_OPERATIONS)
                                                                                                            .phoneNumber(TEST_ALTERNATE_CONTACT_PHONE_OPERATIONS)
                                                                                                            .title(TEST_ALTERNATE_CONTACT_TITLE_OPERATIONS)
                                                                                                            .build())
                                                                                      .build();

        final GetAlternateContactResponse getAlternateContactResponseSecurity = GetAlternateContactResponse.builder()
                                                                                    .build();

        final ListParentsResponse listParentsResponse = ListParentsResponse.builder()
                                                            .parents(Parent.builder()
                                                                         .id(TEST_DESTINATION_PARENT_ID)
                                                                         .build()
                                                            ).build();

        final ListTagsForResourceResponse listTagsForResourceResponse = TagTestResourcesHelper.buildDefaultTagsResponse();
        when(mockProxyClient.client().describeAccount(any(DescribeAccountRequest.class))).thenReturn(describeAccountResponse);

        when(mockAccountProxyClient.client().getAlternateContact(any(GetAlternateContactRequest.class))).thenReturn(getAlternateContactResponseBilling, getAlternateContactResponseOperations, getAlternateContactResponseSecurity);
        when(mockProxyClient.client().listParents(any(ListParentsRequest.class))).thenReturn(listParentsResponse);
        when(mockProxyClient.client().listTagsForResource(any(ListTagsForResourceRequest.class))).thenReturn(listTagsForResourceResponse);

        final ProgressEvent<ResourceModel, CallbackContext> response = readHandler.handleRequest(mockAwsClientProxy, request, new CallbackContext(), mockProxyClient, mockAccountProxyClient, logger);
        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.SUCCESS);
        assertThat(response.getCallbackDelaySeconds()).isEqualTo(0);
        assertThat(response.getResourceModel()).isNotNull();
        assertThat(response.getResourceModel().getAccountName()).isEqualTo(TEST_ACCOUNT_NAME);
        assertThat(response.getResourceModel().getAccountId()).isEqualTo(TEST_ACCOUNT_ID);
        assertThat(response.getResourceModel().getEmail()).isEqualTo(TEST_ACCOUNT_EMAIL);
        assertThat(response.getResourceModel().getAlternateContacts().getBilling().getEmailAddress()).isEqualTo(TEST_ALTERNATE_CONTACT_EMAIL_BILLING);
        assertThat(response.getResourceModel().getAlternateContacts().getOperations().getName()).isEqualTo(TEST_ALTERNATE_CONTACT_NAME_OPERATIONS);
        assertThat(response.getResourceModel().getAlternateContacts().getSecurity()).isNull();
        assertThat(TagTestResourcesHelper.tagsEqual(response.getResourceModel().getTags(), TagTestResourcesHelper.defaultTags));
        assertThat(response.getResourceModels()).isNull();
        assertThat(response.getMessage()).isNull();
        assertThat(response.getErrorCode()).isNull();

        verify(mockProxyClient.client()).describeAccount(any(DescribeAccountRequest.class));
        verify(mockProxyClient.client()).listParents(any(ListParentsRequest.class));
        verify(mockProxyClient.client()).listTagsForResource(any(ListTagsForResourceRequest.class));
    }

    @Test
    public void handleRequest_Fails_With_CfnNotFoundException() {
        final ResourceModel model = ResourceModel.builder()
                                        .accountId(TEST_ACCOUNT_ID)
                                        .build();

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
                                                                  .desiredResourceState(model)
                                                                  .build();

        when(mockProxyClient.client().describeAccount(any(DescribeAccountRequest.class))).thenThrow(AccountNotFoundException.class);

        final ProgressEvent<ResourceModel, CallbackContext> response = readHandler.handleRequest(mockAwsClientProxy, request, new CallbackContext(), mockProxyClient, mockAccountProxyClient, logger);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.FAILED);
        assertThat(response.getResourceModels()).isNull();
        assertThat(response.getErrorCode()).isEqualTo(HandlerErrorCode.NotFound);
    }

    @Test
    public void handleRequest_SuccessAfterResourceNotFoundExceptionForAlternateContact() {
        final ResourceModel model = ResourceModel.builder()
                                        .accountId(TEST_ACCOUNT_ID)
                                        .build();

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
                                                                  .desiredResourceState(model)
                                                                  .build();

        final DescribeAccountResponse describeAccountResponse = DescribeAccountResponse.builder().account(Account.builder()
                                                                                                              .arn(TEST_ACCOUNT_ARN)
                                                                                                              .email(TEST_ACCOUNT_EMAIL)
                                                                                                              .id(TEST_ACCOUNT_ID)
                                                                                                              .name(TEST_ACCOUNT_NAME)
                                                                                                              .build()).build();


        final ListParentsResponse listParentsResponse = ListParentsResponse.builder()
                                                            .parents(Parent.builder()
                                                                         .id(TEST_DESTINATION_PARENT_ID)
                                                                         .build()
                                                            ).build();

        final ListTagsForResourceResponse listTagsForResourceResponse = TagTestResourcesHelper.buildDefaultTagsResponse();
        when(mockProxyClient.client().describeAccount(any(DescribeAccountRequest.class))).thenReturn(describeAccountResponse);

        when(mockAccountProxyClient.client().getAlternateContact(any(GetAlternateContactRequest.class))).thenThrow(ResourceNotFoundException.class);
        when(mockProxyClient.client().listParents(any(ListParentsRequest.class))).thenReturn(listParentsResponse);
        when(mockProxyClient.client().listTagsForResource(any(ListTagsForResourceRequest.class))).thenReturn(listTagsForResourceResponse);

        final ProgressEvent<ResourceModel, CallbackContext> response = readHandler.handleRequest(mockAwsClientProxy, request, new CallbackContext(), mockProxyClient, mockAccountProxyClient, logger);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.SUCCESS);
        assertThat(response.getCallbackDelaySeconds()).isEqualTo(0);
        assertThat(response.getResourceModel()).isNotNull();
        assertThat(response.getResourceModel().getAccountName()).isEqualTo(TEST_ACCOUNT_NAME);
        assertThat(response.getResourceModel().getAccountId()).isEqualTo(TEST_ACCOUNT_ID);
        assertThat(response.getResourceModel().getEmail()).isEqualTo(TEST_ACCOUNT_EMAIL);
        assertThat(response.getResourceModel().getAlternateContacts()).isNull();
        assertThat(TagTestResourcesHelper.tagsEqual(response.getResourceModel().getTags(), TagTestResourcesHelper.defaultTags));
        assertThat(response.getResourceModels()).isNull();
        assertThat(response.getMessage()).isNull();
        assertThat(response.getErrorCode()).isNull();

        verify(mockProxyClient.client()).describeAccount(any(DescribeAccountRequest.class));
        verify(mockProxyClient.client()).listParents(any(ListParentsRequest.class));
        verify(mockProxyClient.client()).listTagsForResource(any(ListTagsForResourceRequest.class));
    }
}
