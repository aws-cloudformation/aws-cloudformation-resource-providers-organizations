package software.amazon.organizations.policy;

import com.google.common.collect.ImmutableSet;
import software.amazon.awssdk.services.organizations.OrganizationsClient;

import java.util.Set;
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

public class AbstractTestBase {
    protected static final String TEST_POLICY_ID = "p-1231231231";
    protected static final String TEST_POLICY_ARN = "arn:aws:organizations:555555555555:policy/p-1231231231";
    protected static final String TEST_POLICY_CONTENT = "{\\\"Version\\\":\\\"2012-10-17\\\",\\\"Statement\\\":[{\\\"Effect\\\":\\\"Allow\\\",\\\"Action\\\":[\\\"s3:*\\\"],\\\"Resource\\\":[\\\"*\\\"]}]}";
    protected static final String TEST_POLICY_NAME = "AllowAllS3Actions";
    protected static final String TEST_POLICY_DESCRIPTION = "Allow All S3 Actions";
    protected static final String TEST_TYPE = "SERVICE_CONTROL_POLICY";
    protected static final Boolean TEST_AWSMANAGED = false;
    protected static final Set<String> TEST_TARGET_IDS = ImmutableSet.of("r-11111");

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

    static ResourceModel generateInitialResourceModel() {
        return ResourceModel.builder()
            .targetIds(TEST_TARGET_IDS)
            .description(TEST_POLICY_DESCRIPTION)
            .content(TEST_POLICY_CONTENT)
            .name(TEST_POLICY_NAME)
            .type(TEST_TYPE)
            .build();
    }

    static ResourceModel generateFinalResourceModel() {
        return ResourceModel.builder()
            .targetIds(TEST_TARGET_IDS)
            .arn(TEST_POLICY_ARN)
            .description(TEST_POLICY_DESCRIPTION)
            .content(TEST_POLICY_CONTENT)
            .id(TEST_POLICY_ID)
            .name(TEST_POLICY_NAME)
            .type(TEST_TYPE)
            .awsManaged(TEST_AWSMANAGED)
            .build();
    }

//    static ResourceModel generateResourceModel() {
//        return ResourceModel.builder()
//            .targetIds(TEST_TARGET_IDS)
//            .arn(TEST_POLICY_ARN)
//            .description(TEST_POLICY_DESCRIPTION)
//            .content(TEST_POLICY_CONTENT)
//            .id(TEST_POLICY_ID)
//            .name(TEST_POLICY_NAME)
//            .type(TEST_TYPE)
//            .awsManaged(TEST_AWSMANAGED)
//            .build();
//    }
}
