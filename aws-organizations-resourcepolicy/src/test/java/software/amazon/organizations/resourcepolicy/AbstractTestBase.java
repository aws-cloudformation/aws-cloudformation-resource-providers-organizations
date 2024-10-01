package software.amazon.organizations.resourcepolicy;

import com.google.common.collect.ImmutableSet;

import software.amazon.awssdk.services.organizations.OrganizationsClient;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashSet;
import java.util.Set;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import software.amazon.awssdk.awscore.AwsRequest;
import software.amazon.awssdk.awscore.AwsResponse;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.pagination.sync.SdkIterable;

import software.amazon.awssdk.services.organizations.model.DescribeResourcePolicyResponse;
import software.amazon.awssdk.services.organizations.model.PutResourcePolicyResponse;
import software.amazon.awssdk.services.organizations.model.ResourcePolicy;
import software.amazon.awssdk.services.organizations.model.ResourcePolicySummary;

import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.Credentials;
import software.amazon.cloudformation.proxy.LoggerProxy;
import software.amazon.cloudformation.proxy.ProxyClient;
import software.amazon.cloudformation.proxy.ProgressEvent;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;
import software.amazon.cloudformation.proxy.OperationStatus;


import software.amazon.cloudformation.exceptions.CfnInvalidRequestException;
import software.amazon.organizations.utils.OrgsLoggerWrapper;

import static org.assertj.core.api.Assertions.assertThat;

public class AbstractTestBase {
    private static final ObjectMapper MAPPER = new ObjectMapper();

  protected static final String TEST_RESOURCEPOLICY_ID = "rp-yrrnv7fj";
  protected static final String TEST_RESOURCEPOLICY_ID_CHANGED = "rp-abcdefgh";
  protected static final String TEST_NAME = "rp-yrrnv7fj";
  protected static final String TEST_RESOURCEPOLICY_ARN = "arn:aws:organizations::111111111111:resourcepolicy/o-ttnhg9yq61/rp-yrrnv7fj";
  protected static final String TEST_RESOURCEPOLICY_CONTENT = "{\"Version\":\"2012-10-17\",\"Statement\":[{\"Effect\":\"Allow\",\"Action\":[\"s3:*\"],\"Resource\":[\"*\"]}]}";
  protected static final String TEST_RESOURCEPOLICY_UPDATED_CONTENT = "{\"Version\":\"2012-10-17\",\"Statement\":[{\"Effect\":\"Deny\",\"Action\":[\"s3:*\"],\"Resource\":[\"*\"]}]}";
  protected static final Map<String, Object> TEST_RESOURCEPOLICY_CONTENT_JSON = convertStringToJsonObject(TEST_RESOURCEPOLICY_CONTENT);
  protected static final Map<String, Object> TEST_RESOURCEPOLICY_UPDATED_CONTENT_JSON = convertStringToJsonObject(TEST_RESOURCEPOLICY_UPDATED_CONTENT);
  protected static final String TEST_NEXT_TOKEN = "mockNextTokenItem";
  protected static final String RESOURCE_POLICY_SCHEMA_SHA256_HEXSTRING = "C48EDAD6CE9CC65CC1E5FC37BBAC107D685574B0DA9CC4C46090D56761E714BF";
  protected static final String RESOURCE_POLICY_JSON_SCHEMA_FILE_NAME = "aws-organizations-resourcepolicy.json";

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

  static ResourceModel generateInitialResourceModel(boolean hasTags, Object content) {
      return ResourceModel.builder()
          .content(content)
          .tags(hasTags
              ? TagTestResourceHelper.translateOrganizationTagsToResourcePolicyTags(TagTestResourceHelper.defaultTags)
              : TagTestResourceHelper.translateOrganizationTagsToResourcePolicyTags(null))
          .build();
  }

  static ResourceModel generateFinalResourceModel(boolean hasTags, Object content) {
      return ResourceModel.builder()
          .content(content)
          .id(TEST_NAME)
          .arn(TEST_RESOURCEPOLICY_ARN)
          .tags(hasTags
              ? TagTestResourceHelper.translateOrganizationTagsToResourcePolicyTags(TagTestResourceHelper.defaultTags)
              : TagTestResourceHelper.translateOrganizationTagsToResourcePolicyTags(null))
          .build();
  }

  static ResourceModel generateUpdatedResourceModel(boolean hasTags, Object content) {
    return ResourceModel.builder()
        .content(content)
        .tags(hasTags
            ? TagTestResourceHelper.translateOrganizationTagsToResourcePolicyTags(TagTestResourceHelper.updatedTags)
            : TagTestResourceHelper.translateOrganizationTagsToResourcePolicyTags(null))
        .build();
  }

  static DescribeResourcePolicyResponse getDescribeResourcePolicyResponse() {
    return DescribeResourcePolicyResponse.builder().resourcePolicy(
        ResourcePolicy.builder()
                .content(TEST_RESOURCEPOLICY_CONTENT)
                .resourcePolicySummary(ResourcePolicySummary.builder()
                    .arn(TEST_RESOURCEPOLICY_ARN)
                    .id(TEST_RESOURCEPOLICY_ID)
                    .build())
                .build())
        .build();
  }

  static PutResourcePolicyResponse getPutResourcePolicyResponse() {
    return PutResourcePolicyResponse.builder().resourcePolicy(
        ResourcePolicy.builder()
                .content(TEST_RESOURCEPOLICY_CONTENT)
                .resourcePolicySummary(ResourcePolicySummary.builder()
                    .arn(TEST_RESOURCEPOLICY_ARN)
                    .id(TEST_RESOURCEPOLICY_ID)
                    .build())
                .build())
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

    protected void verifyHandlerSuccess(ProgressEvent<ResourceModel, CallbackContext> response, ResourceHandlerRequest<ResourceModel> request){
        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.SUCCESS);
        assertThat(response.getCallbackContext()).isNull();
        assertThat(response.getCallbackDelaySeconds()).isEqualTo(0);
        assertThat(response.getResourceModel()).isEqualTo(request.getDesiredResourceState());
        assertThat(response.getResourceModels()).isNull();
        assertThat(response.getMessage()).isNull();
        assertThat(response.getErrorCode()).isNull();
    }
}
