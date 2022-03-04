package software.amazon.organizations.policy;

import software.amazon.awssdk.services.organizations.model.*;

import java.util.ArrayList;
import java.util.List;

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
                .description(model.getDescription())
                .name(model.getName())
                .type(model.getType())
                .build();
        }
        // convert type
        List<software.amazon.awssdk.services.organizations.model.Tag> tags = new ArrayList<>();
        model.getTags().forEach(tag -> {
            tags.add(software.amazon.awssdk.services.organizations.model.Tag.builder().key(tag.getKey()).value(tag.getValue()).build());
        });
        return CreatePolicyRequest.builder()
            .content(model.getContent())
            .description(model.getDescription())
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

    static ResourceModel translateFromReadResponse(final DescribePolicyResponse describePolicyResponse, final ResourceModel model) {
        return ResourceModel.builder()
            .targetIds(model.getTargetIds())
            .arn(describePolicyResponse.policy().policySummary().arn())
            .description(describePolicyResponse.policy().policySummary().description())
            .content(describePolicyResponse.policy().content())
            .id(model.getId())
            .name(describePolicyResponse.policy().policySummary().name())
            .type(describePolicyResponse.policy().policySummary().type().toString())
            .awsManaged(describePolicyResponse.policy().policySummary().awsManaged())
            .build();
    }

    static DeletePolicyRequest translateToDeleteRequest(final ResourceModel model) {
        return DeletePolicyRequest.builder().policyId(model.getId()).build();
    }

//    static ListTargetsForPolicyRequest translateToListTargetsForPolicyRequest(String nextToken, String policyId) {
//        return ListTargetsForPolicyRequest.builder()
//            .maxResults(20)
//            .nextToken(nextToken)
//            .policyId(policyId)
//            .build();
//    }
}
