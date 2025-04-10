package software.amazon.organizations.organizationalunit;

import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import software.amazon.awssdk.awscore.AwsRequest;
import software.amazon.awssdk.awscore.AwsResponse;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.pagination.sync.SdkIterable;
import software.amazon.awssdk.services.organizations.OrganizationsClient;

import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.Credentials;
import software.amazon.cloudformation.proxy.LoggerProxy;
import software.amazon.cloudformation.proxy.ProxyClient;
import software.amazon.organizations.utils.OrgsLoggerWrapper;

public class AbstractTestBase {
    protected static final String TEST_OU_NAME = "test_ou_name";
    protected static final String TEST_OU_UPDATED_NAME = "test_ou_updated_name";
    protected static final String TEST_OU_ARN = "arn:aws:organizations::111111111111:ou/o-0101010101/ou-abc1-abcd1234";
    protected static final String TEST_OU_ID = "abcd1234";
    protected static final String TEST_OU_ID_CHANGED = "4321dcba";
    protected static final String TEST_PARENT_ID = "r-hhhu";
    protected static final String OU_JSON_SCHEMA_FILE_NAME = "aws-organizations-organizationalunit.json";
    protected static final String OU_SCHEMA_SHA256_HEXSTRING = "CD9565D9E0859B36109E6106F61BFA9781C0366DD054511D31F30765FEC1933E";
    protected static final int CALLBACK_DELAY = 1;

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
}
