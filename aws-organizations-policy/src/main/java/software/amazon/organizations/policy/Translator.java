package software.amazon.organizations.policy;

import software.amazon.awssdk.services.organizations.model.AttachPolicyRequest;
import software.amazon.awssdk.services.organizations.model.CreatePolicyRequest;
import software.amazon.awssdk.services.organizations.model.DeletePolicyRequest;
import software.amazon.awssdk.services.organizations.model.DescribePolicyRequest;
import software.amazon.awssdk.services.organizations.model.DescribePolicyResponse;
import software.amazon.awssdk.services.organizations.model.DetachPolicyRequest;
import software.amazon.awssdk.services.organizations.model.ListPoliciesResponse;
import software.amazon.awssdk.services.organizations.model.ListPoliciesRequest;
import software.amazon.awssdk.services.organizations.model.ListTagsForResourceResponse;
import software.amazon.awssdk.services.organizations.model.ListTagsForResourceRequest;
import software.amazon.awssdk.services.organizations.model.ListTargetsForPolicyRequest;
import software.amazon.awssdk.services.organizations.model.Tag;
import software.amazon.awssdk.services.organizations.model.TagResourceRequest;
import software.amazon.awssdk.services.organizations.model.UntagResourceRequest;
import software.amazon.awssdk.services.organizations.model.UpdatePolicyRequest;
//import software.amazon.awssdk.services.organizations.model.UpdatePolicyResponse;
import software.amazon.cloudformation.proxy.Logger;



import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
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
    static CreatePolicyRequest translateToCreateRequest(final ResourceModel model) {
        if (model.getTags() == null) {
            return CreatePolicyRequest.builder()
                .content(model.getContent())
                .description(getOptionalDescription(model))
                .name(model.getName())
                .type(model.getType())
                .build();
        }
        // convert type
        List<Tag> tags = new ArrayList<>();
        model.getTags().forEach(tag -> {
            tags.add(Tag.builder().key(tag.getKey()).value(tag.getValue()).build());
        });
        return CreatePolicyRequest.builder()
            .content(model.getContent())
            .description(getOptionalDescription(model))
            .name(model.getName())
            .type(model.getType())
            .tags(tags)
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

    static ResourceModel translateFromReadResponse(final DescribePolicyResponse describePolicyResponse, final ResourceModel model, Logger logger) {
        ResourceModel toReturn = ResourceModel.builder()
            .targetIds(model.getTargetIds())
            .arn(describePolicyResponse.policy().policySummary().arn().toString())
            .description(describePolicyResponse.policy().policySummary().description())
            .content(describePolicyResponse.policy().content())
            .id(model.getId())
            .name(describePolicyResponse.policy().policySummary().name())
            .type(describePolicyResponse.policy().policySummary().type().toString())
            .awsManaged(describePolicyResponse.policy().policySummary().awsManaged())
            .build();
        logger.log("describeResponse: " + describePolicyResponse);
        logger.log("describeResponseArn: " + describePolicyResponse.policy().policySummary().arn());
        logger.log("translateDescribeModel: " + model);
        return toReturn;
    }

    static ResourceModel translateFromListTagsResponse(final ResourceModel model, final ListTagsForResourceResponse response, Logger logger) {
        logger.log("model4: " + model);
        return ResourceModel.builder()
            .arn(model.getArn())
            .id(model.getId())
            .name(model.getName())
            .description(model.getDescription())
            .targetIds(model.getTargetIds())
            .tags(translateTagsFromSdkResponse(response.tags()))
            .build();
    }

    static DeletePolicyRequest translateToDeleteRequest(final ResourceModel model) {
        return DeletePolicyRequest.builder().policyId(model.getId()).build();
    }

    static UpdatePolicyRequest translateToUpdateRequest(final ResourceModel model) {
        return UpdatePolicyRequest.builder()
            .policyId(model.getId())
            .name(model.getName())
            .description(getOptionalDescription(model))
            .content(model.getContent())
            .build();
    }

    static ListPoliciesRequest translateToListPoliciesRequest(final String filterPolicyType, final String nextToken) {
        return ListPoliciesRequest.builder().filter(filterPolicyType).nextToken(nextToken).build();
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

    static TagResourceRequest translateToTagResourceRequest(Collection<Tag> tags, String policyId) {
        return TagResourceRequest.builder()
            .resourceId(policyId)
            .tags(tags)
            .build();
    }

    static UntagResourceRequest translateToUntagResourceRequest(List<String> tagKeys, String policyId) {
        return UntagResourceRequest.builder()
            .resourceId(policyId)
            .tagKeys(tagKeys)
            .build();
    }

    static String getOptionalDescription(final ResourceModel model) {
        return model.getDescription() == null ? "" : model.getDescription();
    }

//    static List<ResourceModel> translateFromListResponse(final ListPoliciesResponse listPoliciesResponse) {
//        return streamOfOrEmpty(listPoliciesResponse.policies())
//            .map(key -> ResourceModel.builder()
//                .id(key.id())
//                .arn(key.arn())
//                .name(key.name())
//                .type(key.type())
//                .awsManaged(key.awsManaged())
//                .description(key.description())
//                .build())
//            .collect(Collectors.toList());
//    }
//
//    private static <T> Stream<T> streamOfOrEmpty(final Collection<T> collection) {
//        return Optional.ofNullable(collection)
//            .map(Collection::stream)
//            .orElseGet(Stream::empty);
//    }

//    static ListTargetsForPolicyRequest translateToListTargetsForPolicyRequest(String nextToken, String policyId) {
//        return ListTargetsForPolicyRequest.builder()
//            .maxResults(20)
//            .nextToken(nextToken)
//            .policyId(policyId)
//            .build();
//    }
}
