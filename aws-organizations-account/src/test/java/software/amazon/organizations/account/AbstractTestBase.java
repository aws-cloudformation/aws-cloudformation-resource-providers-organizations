package software.amazon.organizations.account;

import com.google.common.collect.ImmutableSet;
import software.amazon.awssdk.awscore.AwsRequest;
import software.amazon.awssdk.awscore.AwsResponse;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.pagination.sync.SdkIterable;
import software.amazon.awssdk.services.organizations.OrganizationsClient;
import software.amazon.awssdk.services.organizations.model.Account;
import software.amazon.awssdk.services.organizations.model.AccountStatus;
import software.amazon.awssdk.services.organizations.model.CreateAccountStatus;
import software.amazon.awssdk.services.organizations.model.DescribeAccountResponse;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.Credentials;
import software.amazon.cloudformation.proxy.LoggerProxy;
import software.amazon.cloudformation.proxy.ProxyClient;
import software.amazon.organizations.utils.OrgsLoggerWrapper;

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
    protected static final String TEST_SOURCE_PARENT_ID = "r-aaaa";
    protected static final String TEST_DESTINATION_PARENT_ID = "ou-abc1-abcd1234";
    protected static final String TEST_DESTINATION_UPDATED_PARENT_ID = "ou-abc1-abcd1235";
    protected static final Set<String> TEST_PARENT_IDS = ImmutableSet.of(TEST_DESTINATION_PARENT_ID);
    protected static final Set<String> TEST_PARENT_UPDATED_IDS = ImmutableSet.of(TEST_DESTINATION_UPDATED_PARENT_ID);
    protected static final Set<String> TEST_MULTIPLE_PARENT_IDS = ImmutableSet.of(TEST_SOURCE_PARENT_ID, TEST_DESTINATION_PARENT_ID);
    protected static final String EMAIL_ALREADY_EXISTS = "EMAIL_ALREADY_EXISTS";
    protected static final String ACCOUNT_LIMIT_EXCEEDED = "ACCOUNT_LIMIT_EXCEEDED";
    protected static final String INVALID_EMAIL = "INVALID_EMAIL";
    protected static final String INTERNAL_FAILURE = "INTERNAL_FAILURE";
    protected static final String UNKNOWN_FAILURE = "UNKNOWN_FAILURE";
    protected static final String CREATE_ACCOUNT_STATUS_ID = "car-123456789023";
    protected static final Instant REQUESTED_TIMESTAMP = Instant.parse("2017-02-03T10:37:30.00Z");
    protected static final Instant COMPLETED_TIMESTAMP = Instant.parse("2017-02-03T10:47:30.00Z");
    protected static final String FAILED = "FAILED";
    protected static final String SUCCEEDED = "SUCCEEDED";
    protected static final String IN_PROGRESS = "IN_PROGRESS";
    protected static final String TEST_NEXT_TOKEN = "mockNextTokenItem";
    protected static final String TEST_JOINED_METHOD = "CREATED";
    protected static final Instant TEST_JOINED_TIMESTAMP = Instant.parse("2017-02-03T10:47:30.00Z");
    protected static final String ACCOUNT_JSON_SCHEMA_FILE_NAME = "aws-organizations-account.json";
    protected static final String ACCOUNT_SCHEMA_SHA256_HEXSTRING = "F25AC8ED367293E5F6E354BFA4BFB6A45A3E968DD1412CF601990CA2D455FE17";

    protected static final DescribeAccountResponse describeAccountResponse = DescribeAccountResponse.builder().account(Account.builder()
                                                                                                          .arn(TEST_ACCOUNT_ARN)
                                                                                                          .email(TEST_ACCOUNT_EMAIL)
                                                                                                          .id(TEST_ACCOUNT_ID)
                                                                                                          .name(TEST_ACCOUNT_NAME)
                                                                                                          .status(AccountStatus.ACTIVE)
                                                                                                          .joinedMethod(TEST_JOINED_METHOD)
                                                                                                          .joinedTimestamp(TEST_JOINED_TIMESTAMP)
                                                                                                          .build()).build();
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

    protected static final CreateAccountStatus CreateAccountStatusFailedWithUnknownFailure = CreateAccountStatus.builder()
                                                                                                    .failureReason(UNKNOWN_FAILURE)
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
    protected static final LoggerProxy loggerProxy;
    protected static final OrgsLoggerWrapper logger;

    static {
        MOCK_CREDENTIALS = new Credentials("accessKey", "secretKey", "token");
        loggerProxy = new LoggerProxy();
        logger = new OrgsLoggerWrapper(loggerProxy);
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

    protected ResourceModel generateDeleteResourceModel() {
        ResourceModel model = ResourceModel.builder()
                                  .accountId(TEST_ACCOUNT_ID)
                                  .build();
        return model;
    }

}
