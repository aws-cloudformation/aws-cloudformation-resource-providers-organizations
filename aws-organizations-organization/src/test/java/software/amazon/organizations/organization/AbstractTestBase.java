package software.amazon.organizations.organization;

import software.amazon.awssdk.services.organizations.OrganizationsClient;

import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import software.amazon.awssdk.awscore.AwsRequest;
import software.amazon.awssdk.awscore.AwsResponse;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.pagination.sync.SdkIterable;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.Credentials;
import software.amazon.cloudformation.proxy.LoggerProxy;
import software.amazon.cloudformation.proxy.ProxyClient;
import software.amazon.organizations.utils.OrgsLoggerWrapper;

public class AbstractTestBase {
    protected static final String TEST_ORG_ID = "o-1231231231";
    protected static final String TEST_ORG_ARN = "arn:org:test::555555555555:organization/o-2222222222";
    protected static final String TEST_FEATURE_SET = "ALL";
    protected static final String TEST_MANAGEMENT_ACCOUNT_ARN = "arn:account:test::555555555555:organization/o-2222222222";
    protected static final String TEST_MANAGEMENT_ACCOUNT_EMAIL = "testEmail@test.com";
    protected static final String TEST_MANAGEMENT_ACCOUNT_ID = "000000000000";
    protected static final String TEST_ROOT_ID = "r-12345";
    protected static final String ORGANIZATION_JSON_SCHEMA_FILE_NAME = "aws-organizations-organization.json";
    protected static final String ORGANIZATION_SCHEMA_SHA256_HEXSTRING = "7B5FCE7CF69C8BFE812BEB41F19FC8132E2C9A226AAEDA1C7C065B2C1F01CDF3";
    protected static final String CONSOLIDATED_BILLING = "CONSOLIDATED_BILLING";
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

    protected ResourceModel generateResourceModel() {
        ResourceModel model = ResourceModel.builder()
                .featureSet(TEST_FEATURE_SET)
                .id(TEST_ORG_ID)
                .arn(TEST_ORG_ARN)
                .managementAccountArn(TEST_MANAGEMENT_ACCOUNT_ARN)
                .managementAccountId(TEST_MANAGEMENT_ACCOUNT_ID)
                .managementAccountEmail(TEST_MANAGEMENT_ACCOUNT_EMAIL)
                .rootId(TEST_ROOT_ID)
                .build();
        return model;
    }
}
