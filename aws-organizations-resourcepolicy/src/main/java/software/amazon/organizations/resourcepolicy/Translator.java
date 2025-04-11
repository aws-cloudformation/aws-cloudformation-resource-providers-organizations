package software.amazon.organizations.resourcepolicy;

import com.fasterxml.jackson.databind.ObjectMapper;

import software.amazon.awssdk.services.organizations.model.DeleteResourcePolicyRequest;
import software.amazon.awssdk.services.organizations.model.DescribeResourcePolicyRequest;
import software.amazon.awssdk.services.organizations.model.ListTagsForResourceRequest;
import software.amazon.awssdk.services.organizations.model.PutResourcePolicyRequest;
import software.amazon.awssdk.services.organizations.model.Tag;
import software.amazon.awssdk.services.organizations.model.TagResourceRequest;
import software.amazon.awssdk.services.organizations.model.UntagResourceRequest;
import software.amazon.cloudformation.exceptions.CfnHandlerInternalFailureException;
import software.amazon.cloudformation.exceptions.CfnInvalidRequestException;
import software.amazon.organizations.utils.OrgsLoggerWrapper;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

/**
 * This class is a centralized placeholder for
 * - api request construction
 * - object translation to/from aws sdk
 * - resource model construction for read/list handlers
 */
public class Translator {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    static PutResourcePolicyRequest translateToCreateRequest(final ResourceModel model, final ResourceHandlerRequest<ResourceModel> request) {
        String content = convertObjectToString(model.getContent());

        if (model.getTags() == null && request.getDesiredResourceTags() == null) {
            return PutResourcePolicyRequest.builder()
                .content(content)
                .build();
        }

        return PutResourcePolicyRequest.builder()
            .content(content)
            .tags(translateTagsForTagResourceRequest(model.getTags(), request.getDesiredResourceTags()))
            .build();
    }

    static PutResourcePolicyRequest translateToUpdateRequest(final ResourceModel model) {
        // PutResourcePolicyRequest does not support tagging updates
        return PutResourcePolicyRequest.builder()
            .content(convertObjectToString(model.getContent()))
            .build();
    }

    static DescribeResourcePolicyRequest translateToReadRequest(final ResourceModel model) {
      return DescribeResourcePolicyRequest.builder().build();
    }

    static DeleteResourcePolicyRequest translateToDeleteRequest(final ResourceModel model) {
      return DeleteResourcePolicyRequest.builder().build();
    }

    static ListTagsForResourceRequest translateToListTagsForResourceRequest(final String resourcePolicyId) {
        return ListTagsForResourceRequest.builder().resourceId(resourcePolicyId).build();
    }

    static Collection<Tag> translateTagsForTagResourceRequest(Set<software.amazon.organizations.resourcepolicy.Tag> tags, Map<String, String> desiredResourceTags) {
        Map<String, String> tagsToReturn = new HashMap<>();

        // Add stack level tags
        if (desiredResourceTags != null) {
            tagsToReturn.putAll(desiredResourceTags);
        }

        // Add resource level tags
        if (tags != null) {
            tags.forEach(tag -> tagsToReturn.put(tag.getKey(), tag.getValue()));
        }

        return TagsHelper.tagMapToTagSetConverter(tagsToReturn);
    }

    static Set<software.amazon.organizations.resourcepolicy.Tag> translateTagsFromSdkResponse(List<Tag> inputTags) {
        if (inputTags == null) return new HashSet<>();

        final Set<software.amazon.organizations.resourcepolicy.Tag> tagsToReturn = new HashSet<>();
        for (Tag input : inputTags) {
            software.amazon.organizations.resourcepolicy.Tag tag = software.amazon.organizations.resourcepolicy.Tag.builder()
                .key(input.key())
                .value(input.value())
                .build();
            tagsToReturn.add(tag);
        }

        return tagsToReturn;
    }

    static TagResourceRequest translateToTagResourceRequest(Collection<Tag> tags, String policyId) {
        return TagResourceRequest.builder()
            .resourceId(policyId)
            .tags(tags)
            .build();
    }

    static UntagResourceRequest translateToUntagResourceRequest(Set<String> tagKeys, String policyId) {
        return UntagResourceRequest.builder()
            .resourceId(policyId)
            .tagKeys(tagKeys)
            .build();
    }

    /**
    * Converts user inputted JSON to a String
    * @param content
    * @return
    **/
    static String convertObjectToString(Object content) {
        try {
            if (content instanceof String) {
                return (String)content;
            }
            return MAPPER.writeValueAsString(content);
        } catch (Exception e) {
            throw new CfnInvalidRequestException(e);
        }
    }

    static Object convertStringToObject(String content, OrgsLoggerWrapper logger) {
        try {
            return MAPPER.readValue(content, Map.class);
        } catch (Exception e) {
            logger.log("Internal handler failure when converting response.");
            throw new CfnHandlerInternalFailureException(e);
        }
    }

    private static <T> Stream<T> streamOfOrEmpty(final Collection<T> collection) {
        return Optional.ofNullable(collection)
            .map(Collection::stream)
            .orElseGet(Stream::empty);
    }
}
