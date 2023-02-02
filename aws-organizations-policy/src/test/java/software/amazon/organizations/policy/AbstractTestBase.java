package software.amazon.organizations.policy;

import com.google.common.collect.ImmutableSet;
import software.amazon.awssdk.services.organizations.OrganizationsClient;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import software.amazon.awssdk.awscore.AwsRequest;
import software.amazon.awssdk.awscore.AwsResponse;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.pagination.sync.SdkIterable;
import software.amazon.awssdk.services.organizations.model.DescribePolicyResponse;
import software.amazon.awssdk.services.organizations.model.Policy;
import software.amazon.awssdk.services.organizations.model.PolicySummary;
import software.amazon.awssdk.services.organizations.model.PolicyTargetSummary;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.Credentials;
import software.amazon.cloudformation.proxy.LoggerProxy;
import software.amazon.cloudformation.proxy.ProxyClient;

public class AbstractTestBase {
    protected static final String TEST_POLICY_ID = "p-1231231231";
    protected static final String TEST_POLICY_ID_CHANGED = "p-3213213213";
    protected static final String TEST_POLICY_ARN = "arn:aws:organizations:555555555555:policy/p-1231231231";
    protected static final String TEST_POLICY_CONTENT = "{\\\"Version\\\":\\\"2012-10-17\\\",\\\"Statement\\\":[{\\\"Effect\\\":\\\"Allow\\\",\\\"Action\\\":[\\\"s3:*\\\"],\\\"Resource\\\":[\\\"*\\\"]}]}";
    protected static final String TEST_POLICY_NAME = "AllowAllS3Actions";
    protected static final String TEST_POLICY_DESCRIPTION = "Allow All S3 Actions";
    protected static final String TEST_POLICY_UPDATED_CONTENT = "{\\\"Version\\\":\\\"2012-10-17\\\",\\\"Statement\\\":[{\\\"Effect\\\":\\\"Deny\\\",\\\"Action\\\":[\\\"s3:*\\\"],\\\"Resource\\\":[\\\"*\\\"]}]}";
    protected static final String TEST_POLICY_UPDATED_NAME = "DenyAllS3Actions";
    protected static final String TEST_POLICY_UPDATED_DESCRIPTION = "Deny All S3 Actions";
    protected static final String TEST_TYPE = PolicyConstants.PolicyType.SERVICE_CONTROL_POLICY.toString();
    protected static final String TEST_TYPE_CHANGED = PolicyConstants.PolicyType.TAG_POLICY.toString();
    protected static final Boolean TEST_AWSMANAGED = false;
    protected static final String TEST_TARGET_ROOT_ID = "r-11111";
    protected static final String TEST_TARGET_OU_ID = "ou-abc1-abcd1234";
    protected static final String TEST_TARGET_ACCOUNT_ID = "123456789012";
    protected static final Set<String> TEST_TARGET_IDS = ImmutableSet.of(TEST_TARGET_ROOT_ID, TEST_TARGET_OU_ID);
    protected static final Set<String> TEST_UPDATED_TARGET_IDS = ImmutableSet.of(TEST_TARGET_ROOT_ID, TEST_TARGET_ACCOUNT_ID);
    protected static final String TEST_NEXT_TOKEN = "mockNextTokenItem";
    protected static final String POLICY_SCHEMA_SHA256_HEXSTRING = "B22C4432E6B98EF865EFB5335B8E7905B3F94FB9AAAC9B1B2463B98A138AF4CC";
    protected static final String POLICY_JSON_SCHEMA_FILE_NAME = "aws-organizations-policy.json";

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

    static ResourceModel generateInitialResourceModel(boolean hasTargets, boolean hasTags) {
        return ResourceModel.builder()
            .targetIds(hasTargets ? TEST_TARGET_IDS : new HashSet<>())
            .tags(hasTags
                ? TagTestResourceHelper.translateOrganizationTagsToPolicyTags(TagTestResourceHelper.defaultTags)
                : null)
            .description(TEST_POLICY_DESCRIPTION)
            .content(TEST_POLICY_CONTENT)
            .name(TEST_POLICY_NAME)
            .type(TEST_TYPE)
            .build();
    }

    static ResourceModel generateFinalResourceModel(boolean hasTargets, boolean hasTags) {
        return ResourceModel.builder()
            .targetIds(hasTargets ? TEST_TARGET_IDS : new HashSet<>())
            .arn(TEST_POLICY_ARN)
            .description(TEST_POLICY_DESCRIPTION)
            .content(TEST_POLICY_CONTENT)
            .id(TEST_POLICY_ID)
            .name(TEST_POLICY_NAME)
            .type(TEST_TYPE)
            .awsManaged(TEST_AWSMANAGED)
            .tags(hasTags
                ? TagTestResourceHelper.translateOrganizationTagsToPolicyTags(TagTestResourceHelper.defaultTags)
                : null)
            .build();
    }

    static ResourceModel generateUpdatedResourceModel(boolean hasTargets, boolean hasTags) {
        return ResourceModel.builder()
            .id(TEST_POLICY_ID)
            .arn(TEST_POLICY_ARN)
            .awsManaged(TEST_AWSMANAGED)
            .name(TEST_POLICY_UPDATED_NAME)
            .type(TEST_TYPE)
            .content(TEST_POLICY_UPDATED_CONTENT)
            .description(TEST_POLICY_UPDATED_DESCRIPTION)
            .targetIds(hasTargets ? TEST_UPDATED_TARGET_IDS : new HashSet<>())
            .tags(hasTags
                ? TagTestResourceHelper.translateOrganizationTagsToPolicyTags(TagTestResourceHelper.updatedTags)
                : null)
            .build();
    }

    static DescribePolicyResponse getDescribePolicyResponse() {
        return DescribePolicyResponse.builder().policy(
                Policy.builder()
                    .content(TEST_POLICY_CONTENT)
                    .policySummary(PolicySummary.builder()
                        .arn(TEST_POLICY_ARN)
                        .awsManaged(TEST_AWSMANAGED)
                        .description(TEST_POLICY_DESCRIPTION)
                        .id(TEST_POLICY_ID)
                        .name(TEST_POLICY_NAME)
                        .type(TEST_TYPE)
                        .build())
                    .build())
            .build();
    }

    static PolicyTargetSummary getPolicyTargetSummaryWithTargetId(final String targetId) {
        return PolicyTargetSummary.builder()
            .targetId(targetId)
            .build();
    }
}
