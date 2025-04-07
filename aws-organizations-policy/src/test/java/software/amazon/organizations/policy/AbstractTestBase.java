package software.amazon.organizations.policy;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableSet;
import software.amazon.awssdk.services.organizations.OrganizationsClient;

import java.util.HashSet;
import java.util.Map;
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
import software.amazon.cloudformation.exceptions.CfnInvalidRequestException;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.Credentials;
import software.amazon.cloudformation.proxy.LoggerProxy;
import software.amazon.cloudformation.proxy.ProxyClient;
import software.amazon.organizations.utils.OrgsLoggerWrapper;

public class AbstractTestBase {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    protected static final String TEST_POLICY_ID = "p-1231231231";
    protected static final String TEST_POLICY_ID_CHANGED = "p-3213213213";
    protected static final String TEST_POLICY_ARN = "arn:aws:organizations:555555555555:policy/p-1231231231";
    protected static final String TEST_POLICY_CONTENT = "{\"Version\":\"2012-10-17\",\"Statement\":[{\"Effect\":\"Allow\",\"Action\":[\"s3:*\"],\"Resource\":[\"*\"]}]}";
    protected static final Map<String, Object> TEST_POLICY_CONTENT_JSON = convertStringToJsonObject(TEST_POLICY_CONTENT);
    protected static final String TEST_POLICY_UPDATED_CONTENT = "{\"Version\":\"2012-10-17\",\"Statement\":[{\"Effect\":\"Deny\",\"Action\":[\"s3:*\"],\"Resource\":[\"*\"]}]}";
    protected static final Map<String, Object> TEST_POLICY_UPDATED_CONTENT_JSON = convertStringToJsonObject(TEST_POLICY_UPDATED_CONTENT);
    protected static final String TEST_POLICY_NAME = "AllowAllS3Actions";
    protected static final String TEST_POLICY_DESCRIPTION = "Allow All S3 Actions";
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
    protected static final String POLICY_SCHEMA_SHA256_HEXSTRING = "4F8BADE6D11D6984EECB9B8561FCD73697E744E1A2AD44F069D73D08C495962B";
    protected static final String POLICY_JSON_SCHEMA_FILE_NAME = "aws-organizations-policy.json";
    protected static final int CALLBACK_DELAY = 1;
    protected static final int MAX_RETRY_ATTEMPT = 2;

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

    // recommended content type is JSON
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

    static ResourceModel generateInitialResourceModelWithJsonStringContent(boolean hasTargets, boolean hasTags) {
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
            .content(TEST_POLICY_CONTENT_JSON)
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
            .content(TEST_POLICY_UPDATED_CONTENT_JSON)
            .description(TEST_POLICY_UPDATED_DESCRIPTION)
            .targetIds(hasTargets ? TEST_UPDATED_TARGET_IDS : new HashSet<>())
            .tags(hasTags
                ? TagTestResourceHelper.translateOrganizationTagsToPolicyTags(TagTestResourceHelper.updatedTags)
                : null)
            .build();
    }

    static ResourceModel generateUpdatedResourceModelWithJsonContent(boolean hasTargets, boolean hasTags) {
        return ResourceModel.builder()
                   .id(TEST_POLICY_ID)
                   .arn(TEST_POLICY_ARN)
                   .awsManaged(TEST_AWSMANAGED)
                   .name(TEST_POLICY_UPDATED_NAME)
                   .type(TEST_TYPE)
                   .content(TEST_POLICY_UPDATED_CONTENT_JSON)
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

    /**
     * Converts a String to a JSON object.
     * Ref: https://fasterxml.github.io/jackson-databind/javadoc/2.7/com/fasterxml/jackson/databind/ObjectMapper.html#readValue(java.lang.String,%20com.fasterxml.jackson.core.type.TypeReference)
     *
     * @param content
     * @return Map<String, Object>
     */
    static Map<String, Object> convertStringToJsonObject(final String content) {
        try {
            return MAPPER.readValue(content, new TypeReference<Map<String, Object>>() {
            });
        } catch (Exception e) {
            throw new CfnInvalidRequestException(e);
        }
    }
}
