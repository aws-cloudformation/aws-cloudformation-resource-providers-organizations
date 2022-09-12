package software.amazon.organizations.account;

import com.google.common.collect.ImmutableSet;
import software.amazon.awssdk.awscore.AwsRequest;
import software.amazon.awssdk.awscore.AwsResponse;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.pagination.sync.SdkIterable;
import software.amazon.awssdk.services.account.AccountClient;
import software.amazon.awssdk.services.organizations.OrganizationsClient;
import software.amazon.awssdk.services.organizations.model.CreateAccountStatus;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.Credentials;
import software.amazon.cloudformation.proxy.LoggerProxy;
import software.amazon.cloudformation.proxy.ProxyClient;

import java.time.Instant;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

public class AbstractTestBase {
    // Constants for unit test
    protected static final String GOV_CLOUD_PARTITION = "aws-us-gov";
    protected static final String TEST_ACCOUNT_ID = "111111111111";
    protected static final String TEST_ACCOUNT_ARN = "arn:aws:organizations::111111111111:account/o-1111111111/111111111111";
    protected static final String TEST_ACCOUNT_EMAIL = "testAccountEmail@amazon.com";
    protected static final String TEST_ACCOUNT_NAME = "TestAccountName";
    protected static final String TEST_ALTERNATE_CONTACT_EMAIL_BILLING = "testAlternateContactEmailBilling@amazon.com";
    protected static final String TEST_ALTERNATE_CONTACT_NAME_BILLING = "TestAlternateContactNameBilling";
    protected static final String TEST_ALTERNATE_CONTACT_PHONE_BILLING = "1111111111";
    protected static final String TEST_ALTERNATE_CONTACT_TITLE_BILLING = "TestAlternateContactTitleBilling";
    protected static final String TEST_ALTERNATE_CONTACT_EMAIL_OPERATIONS = "testAlternateContactEmailOperations@amazon.com";
    protected static final String TEST_ALTERNATE_CONTACT_NAME_OPERATIONS = "TestAlternateContactNameOperations";
    protected static final String TEST_ALTERNATE_CONTACT_PHONE_OPERATIONS = "2222222222";
    protected static final String TEST_ALTERNATE_CONTACT_TITLE_OPERATIONS = "TestAlternateContactTitleOperations";
    protected static final String TEST_ALTERNATE_CONTACT_EMAIL_SECURITY = "testAlternateContactEmailSecurity@amazon.com";
    protected static final String TEST_ALTERNATE_CONTACT_NAME_SECURITY = "TestAlternateContactNameSecurity";
    protected static final String TEST_ALTERNATE_CONTACT_PHONE_SECURITY = "3333333333";
    protected static final String TEST_ALTERNATE_CONTACT_TITLE_SECURITY = "TestAlternateContactTitleSecurity";
    protected static final String TEST_SOURCE_PARENT_ID = "r-aaaa";
    protected static final String TEST_DESTINATION_PARENT_ID = "ou-abc1-abcd1234";
    protected static final String TEST_DESTINATION_UPDATED_PARENT_ID = "ou-abc1-abcd1235";
    protected static final Set<String> TEST_PARENT_IDS = ImmutableSet.of(TEST_DESTINATION_PARENT_ID);
    protected static final Set<String> TEST_PARENT_UPDATED_IDS = ImmutableSet.of(TEST_DESTINATION_UPDATED_PARENT_ID);
    protected static final Set<String> TEST_MULTIPLE_PARENT_IDS = ImmutableSet.of(TEST_SOURCE_PARENT_ID, TEST_DESTINATION_PARENT_ID);
    protected static final AlternateContact TEST_ALTERNATE_CONTACT_BILLING = AlternateContact.builder()
                                                                                 .emailAddress(TEST_ALTERNATE_CONTACT_EMAIL_BILLING)
                                                                                 .name(TEST_ALTERNATE_CONTACT_NAME_BILLING)
                                                                                 .phoneNumber(TEST_ALTERNATE_CONTACT_PHONE_BILLING)
                                                                                 .title(TEST_ALTERNATE_CONTACT_TITLE_BILLING)
                                                                                 .build();
    protected static final AlternateContact TEST_ALTERNATE_CONTACT_OPERATIONS = AlternateContact.builder()
                                                                                    .emailAddress(TEST_ALTERNATE_CONTACT_EMAIL_OPERATIONS)
                                                                                    .name(TEST_ALTERNATE_CONTACT_NAME_OPERATIONS)
                                                                                    .phoneNumber(TEST_ALTERNATE_CONTACT_PHONE_OPERATIONS)
                                                                                    .title(TEST_ALTERNATE_CONTACT_TITLE_OPERATIONS)
                                                                                    .build();
    protected static final AlternateContact TEST_ALTERNATE_CONTACT_SECURITY = AlternateContact.builder()
                                                                                  .emailAddress(TEST_ALTERNATE_CONTACT_EMAIL_SECURITY)
                                                                                  .name(TEST_ALTERNATE_CONTACT_NAME_SECURITY)
                                                                                  .phoneNumber(TEST_ALTERNATE_CONTACT_PHONE_SECURITY)
                                                                                  .title(TEST_ALTERNATE_CONTACT_TITLE_SECURITY)
                                                                                  .build();

    protected static final AlternateContacts TEST_ALTERNATE_CONTACTS = AlternateContacts.builder()
                                                                           .billing(TEST_ALTERNATE_CONTACT_BILLING)
                                                                           .operations(TEST_ALTERNATE_CONTACT_OPERATIONS)
                                                                           .security(TEST_ALTERNATE_CONTACT_SECURITY)
                                                                           .build();

    protected static final String EMAIL_ALREADY_EXISTS = "EMAIL_ALREADY_EXISTS";
    protected static final String ACCOUNT_LIMIT_EXCEEDED = "ACCOUNT_LIMIT_EXCEEDED";
    protected static final String INVALID_EMAIL = "INVALID_EMAIL";
    protected static final String INTERNAL_FAILURE = "INTERNAL_FAILURE";
    protected static final String CREATE_ACCOUNT_STATUS_ID = "car-123456789023";
    protected static final Instant REQUESTED_TIMESTAMP = Instant.parse("2017-02-03T10:37:30.00Z");
    protected static final Instant COMPLETED_TIMESTAMP = Instant.parse("2017-02-03T10:47:30.00Z");
    protected static final String FAILED = "FAILED";
    protected static final String SUCCEEDED = "SUCCEEDED";
    protected static final String IN_PROGRESS = "IN_PROGRESS";
    protected static final String TEST_NEXT_TOKEN = "mockNextTokenItem";
    protected static final CreateAccountStatus CreateAccountStatusInProgress = CreateAccountStatus.builder()
                                                                                   .accountName(TEST_ACCOUNT_NAME)
                                                                                   .id(CREATE_ACCOUNT_STATUS_ID)
                                                                                   .state(IN_PROGRESS)
                                                                                   .requestedTimestamp(REQUESTED_TIMESTAMP)
                                                                                   .build();
    protected static final CreateAccountStatus CreateAccountStatusFailedWithAlreadyExist = CreateAccountStatus.builder()
                                                                                               .failureReason(EMAIL_ALREADY_EXISTS)
                                                                                               .id(CREATE_ACCOUNT_STATUS_ID)
                                                                                               .state(FAILED)
                                                                                               .requestedTimestamp(REQUESTED_TIMESTAMP)
                                                                                               .build();

    protected static final CreateAccountStatus CreateAccountStatusFailedWithInvalidInput = CreateAccountStatus.builder()
                                                                                               .failureReason(INVALID_EMAIL)
                                                                                               .id(CREATE_ACCOUNT_STATUS_ID)
                                                                                               .state(FAILED)
                                                                                               .requestedTimestamp(REQUESTED_TIMESTAMP)
                                                                                               .build();

    protected static final CreateAccountStatus CreateAccountStatusFailedWithAccountLimitExceed = CreateAccountStatus.builder()
                                                                                                     .failureReason(ACCOUNT_LIMIT_EXCEEDED)
                                                                                                     .id(CREATE_ACCOUNT_STATUS_ID)
                                                                                                     .state(FAILED)
                                                                                                     .requestedTimestamp(REQUESTED_TIMESTAMP)
                                                                                                     .build();

    protected static final CreateAccountStatus CreateAccountStatusFailedWithInternalFailure = CreateAccountStatus.builder()
                                                                                                  .failureReason(INTERNAL_FAILURE)
                                                                                                  .id(CREATE_ACCOUNT_STATUS_ID)
                                                                                                  .state(FAILED)
                                                                                                  .requestedTimestamp(REQUESTED_TIMESTAMP)
                                                                                                  .build();

    protected static final CreateAccountStatus CreateAccountStatusSucceeded = CreateAccountStatus.builder()
                                                                                  .accountId(TEST_ACCOUNT_ID)
                                                                                  .accountName(TEST_ACCOUNT_NAME)
                                                                                  .completedTimestamp(COMPLETED_TIMESTAMP)
                                                                                  .id(CREATE_ACCOUNT_STATUS_ID)
                                                                                  .state(SUCCEEDED)
                                                                                  .requestedTimestamp(REQUESTED_TIMESTAMP)
                                                                                  .build();
    protected static final Credentials MOCK_CREDENTIALS;
    protected static final LoggerProxy logger;

    static {
        MOCK_CREDENTIALS = new Credentials("accessKey", "secretKey", "token");
        logger = new LoggerProxy();
    }

    static ProxyClient<OrganizationsClient> MOCK_PROXY(
        final AmazonWebServicesClientProxy proxy,
        final OrganizationsClient orgsClient) {
        return new ProxyClient<OrganizationsClient>() {
            @Override
            public <RequestT extends AwsRequest, ResponseT extends AwsResponse> ResponseT
            injectCredentialsAndInvokeV2(RequestT request, Function<RequestT, ResponseT> requestFunction) {
                return proxy.injectCredentialsAndInvokeV2(request, requestFunction);
            }

            @Override
            public <RequestT extends AwsRequest, ResponseT extends AwsResponse>
            CompletableFuture<ResponseT>
            injectCredentialsAndInvokeV2Async(RequestT request, Function<RequestT, CompletableFuture<ResponseT>> requestFunction) {
                throw new UnsupportedOperationException();
            }

            @Override
            public <RequestT extends AwsRequest, ResponseT extends AwsResponse, IterableT extends SdkIterable<ResponseT>>
            IterableT
            injectCredentialsAndInvokeIterableV2(RequestT request, Function<RequestT, IterableT> requestFunction) {
                return proxy.injectCredentialsAndInvokeIterableV2(request, requestFunction);
            }

            @Override
            public <RequestT extends AwsRequest, ResponseT extends AwsResponse> ResponseInputStream<ResponseT>
            injectCredentialsAndInvokeV2InputStream(RequestT requestT, Function<RequestT, ResponseInputStream<ResponseT>> function) {
                throw new UnsupportedOperationException();
            }

            @Override
            public <RequestT extends AwsRequest, ResponseT extends AwsResponse> ResponseBytes<ResponseT>
            injectCredentialsAndInvokeV2Bytes(RequestT requestT, Function<RequestT, ResponseBytes<ResponseT>> function) {
                throw new UnsupportedOperationException();
            }

            @Override
            public OrganizationsClient client() {
                return orgsClient;
            }
        };
    }

    static ProxyClient<AccountClient> MOCK_ACCOUNT_PROXY(
        final AmazonWebServicesClientProxy proxy,
        final AccountClient accountClient) {
        return new ProxyClient<AccountClient>() {
            @Override
            public <RequestT extends AwsRequest, ResponseT extends AwsResponse> ResponseT
            injectCredentialsAndInvokeV2(RequestT request, Function<RequestT, ResponseT> requestFunction) {
                return proxy.injectCredentialsAndInvokeV2(request, requestFunction);
            }

            @Override
            public <RequestT extends AwsRequest, ResponseT extends AwsResponse>
            CompletableFuture<ResponseT>
            injectCredentialsAndInvokeV2Async(RequestT request, Function<RequestT, CompletableFuture<ResponseT>> requestFunction) {
                throw new UnsupportedOperationException();
            }

            @Override
            public <RequestT extends AwsRequest, ResponseT extends AwsResponse, IterableT extends SdkIterable<ResponseT>>
            IterableT
            injectCredentialsAndInvokeIterableV2(RequestT request, Function<RequestT, IterableT> requestFunction) {
                return proxy.injectCredentialsAndInvokeIterableV2(request, requestFunction);
            }

            @Override
            public <RequestT extends AwsRequest, ResponseT extends AwsResponse> ResponseInputStream<ResponseT>
            injectCredentialsAndInvokeV2InputStream(RequestT requestT, Function<RequestT, ResponseInputStream<ResponseT>> function) {
                throw new UnsupportedOperationException();
            }

            @Override
            public <RequestT extends AwsRequest, ResponseT extends AwsResponse> ResponseBytes<ResponseT>
            injectCredentialsAndInvokeV2Bytes(RequestT requestT, Function<RequestT, ResponseBytes<ResponseT>> function) {
                throw new UnsupportedOperationException();
            }

            @Override
            public AccountClient client() {
                return accountClient;
            }
        };
    }


    protected ResourceModel generateDeleteResourceModel() {
        ResourceModel model = ResourceModel.builder()
                                  .accountId(TEST_ACCOUNT_ID)
                                  .build();
        return model;
    }

}
