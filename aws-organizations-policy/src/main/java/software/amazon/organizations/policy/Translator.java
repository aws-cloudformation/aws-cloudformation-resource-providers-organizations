package software.amazon.organizations.policy;

import software.amazon.awssdk.services.organizations.model.AttachPolicyRequest;
import software.amazon.awssdk.services.organizations.model.CreatePolicyRequest;
import software.amazon.awssdk.services.organizations.model.DeletePolicyRequest;
import software.amazon.awssdk.services.organizations.model.DescribePolicyRequest;
import software.amazon.awssdk.services.organizations.model.DetachPolicyRequest;
import software.amazon.awssdk.services.organizations.model.ListPoliciesRequest;
import software.amazon.awssdk.services.organizations.model.ListPoliciesResponse;
import software.amazon.awssdk.services.organizations.model.ListTagsForResourceRequest;
import software.amazon.awssdk.services.organizations.model.ListTargetsForPolicyRequest;
import software.amazon.awssdk.services.organizations.model.Tag;
import software.amazon.awssdk.services.organizations.model.TagResourceRequest;
import software.amazon.awssdk.services.organizations.model.UntagResourceRequest;
import software.amazon.awssdk.services.organizations.model.UpdatePolicyRequest;
import software.amazon.cloudformation.exceptions.CfnHandlerInternalFailureException;
import software.amazon.cloudformation.exceptions.CfnInvalidRequestException;
import com.fasterxml.jackson.databind.ObjectMapper;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * This class is a centralized placeholder for
 * - api request construction
 * - object translation to/from aws sdk
 * - resource model construction for read/list handlers
 */
public class Translator {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    static CreatePolicyRequest translateToCreateRequest(final ResourceModel model, final ResourceHandlerRequest<ResourceModel> request) {
        String content = convertObjectToString(model.getContent());
        if (model.getTags() == null && request.getDesiredResourceTags() == null) {
            return CreatePolicyRequest.builder()
                .content(content)
                .description(getOptionalDescription(model))
                .name(model.getName())
                .type(model.getType())
                .build();
        }

        return CreatePolicyRequest.builder()
            .content(content)
            .description(getOptionalDescription(model))
            .name(model.getName())
            .type(model.getType())
            .tags(translateTagsForTagResourceRequest(model.getTags(), request.getDesiredResourceTags()))
            .build();
    }

    static AttachPolicyRequest translateToAttachRequest(String policyId, String targetId) {
        return AttachPolicyRequest.builder().policyId(policyId).targetId(targetId).build();
    }

    static DetachPolicyRequest translateToDetachRequest(String policyId, String targetId) {
        return DetachPolicyRequest.builder().policyId(policyId).targetId(targetId).build();
    }


    static DescribePolicyRequest translateToReadRequest(final ResourceModel model) {
        return DescribePolicyRequest.builder().policyId(model.getId()).build();
    }

    static DeletePolicyRequest translateToDeleteRequest(final ResourceModel model) {
        return DeletePolicyRequest.builder().policyId(model.getId()).build();
    }

    static UpdatePolicyRequest translateToUpdateRequest(final ResourceModel model) {
        return UpdatePolicyRequest.builder()
            .policyId(model.getId())
            .name(model.getName())
            .description(getOptionalDescription(model))
            .content(convertObjectToString(model.getContent()))
            .build();
    }

    static ListTargetsForPolicyRequest translateToListTargetsForPolicyRequest(final String policyId, final String nextToken) {
        return ListTargetsForPolicyRequest.builder()
            .policyId(policyId)
            .nextToken(nextToken)
            .build();
    }

    static ListTagsForResourceRequest translateToListTagsForResourceRequest(final String policyId) {
        return ListTagsForResourceRequest.builder().resourceId(policyId).build();
    }

    static Collection<Tag> translateTagsForTagResourceRequest(Set<software.amazon.organizations.policy.Tag> tags, Map<String, String> desiredResourceTags) {
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

    static Set<software.amazon.organizations.policy.Tag> translateTagsFromSdkResponse(List<Tag> inputTags) {
        if (inputTags == null) return new HashSet<>();

        final Set<software.amazon.organizations.policy.Tag> tagsToReturn = new HashSet<>();
        for (Tag input : inputTags) {
            software.amazon.organizations.policy.Tag tag = software.amazon.organizations.policy.Tag.builder()
                .key(input.key())
                .value(input.value())
                .build();
            tagsToReturn.add(tag);
        }

        return tagsToReturn;
    }

    static TagResourceRequest translateToTagResourceRequest(Set<Tag> tags, String policyId) {
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

    static ListPoliciesRequest translateToListPoliciesRequest(final ResourceModel model, final String nextToken) {
        return ListPoliciesRequest.builder().filter(model.getType()).nextToken(nextToken).build();
    }

    static List<ResourceModel> translateListPoliciesResponseToResourceModels(final ListPoliciesResponse pageResponse) {
        return streamOfOrEmpty(pageResponse.policies())
            .map(policy -> ResourceModel.builder()
                .arn(policy.arn())
                .id(policy.id())
                .awsManaged(policy.awsManaged())
                .name(policy.name())
                .type(policy.typeAsString())
                .description(policy.description())
                .build())
            .collect(Collectors.toList());
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

    /**
     * Converts String to JSON object
     * @param content
     * @return
     **/
    static Object convertStringToObject(String content) {
        try {
            return MAPPER.readValue(content, Map.class);
        } catch (Exception e) {
            throw new CfnHandlerInternalFailureException(e);
        }
    }

    private static <T> Stream<T> streamOfOrEmpty(final Collection<T> collection) {
        return Optional.ofNullable(collection)
            .map(Collection::stream)
            .orElseGet(Stream::empty);
    }

    static String getOptionalDescription(final ResourceModel model) {
        return model.getDescription() == null ? "" : model.getDescription();
    }
}
