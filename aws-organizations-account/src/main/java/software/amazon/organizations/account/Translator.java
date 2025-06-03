package software.amazon.organizations.account;

import software.amazon.awssdk.awscore.AwsRequest;
import software.amazon.awssdk.services.organizations.model.AccountStatus;
import software.amazon.awssdk.services.organizations.model.CloseAccountRequest;
import software.amazon.awssdk.services.organizations.model.CreateAccountRequest;
import software.amazon.awssdk.services.organizations.model.DescribeAccountRequest;
import software.amazon.awssdk.services.organizations.model.DescribeCreateAccountStatusRequest;
import software.amazon.awssdk.services.organizations.model.ListAccountsRequest;
import software.amazon.awssdk.services.organizations.model.ListAccountsResponse;
import software.amazon.awssdk.services.organizations.model.ListParentsRequest;
import software.amazon.awssdk.services.organizations.model.ListRootsRequest;
import software.amazon.awssdk.services.organizations.model.ListTagsForResourceRequest;
import software.amazon.awssdk.services.organizations.model.ListTagsForResourceResponse;
import software.amazon.awssdk.services.organizations.model.MoveAccountRequest;
import software.amazon.awssdk.services.organizations.model.Tag;
import software.amazon.awssdk.services.organizations.model.TagResourceRequest;
import software.amazon.awssdk.services.organizations.model.UntagResourceRequest;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static software.amazon.organizations.account.TagsHelper.buildTag;

/**
 * This class is a centralized placeholder for
 * - api request construction
 * - object translation to/from aws sdk
 * - resource model construction for read/list handlers
 */

public class Translator {
    static DescribeCreateAccountStatusRequest translateToDescribeCreateAccountStatusRequest(final CallbackContext callbackContext) {
        return DescribeCreateAccountStatusRequest.builder()
                   .createAccountRequestId(callbackContext.getCreateAccountRequestId())
                   .build();
    }

    static ListTagsForResourceRequest translateToListTagsForResourceRequest(final ResourceModel model) {
        return ListTagsForResourceRequest.builder()
                   .resourceId(model.getAccountId())
                   .build();
    }

    static ListAccountsRequest translateToListAccounts(final String nextToken) {
        return ListAccountsRequest.builder()
                   .nextToken(nextToken)
                   .build();
    }

    static List<ResourceModel> translateListAccountsResponseToResourceModel(final ListAccountsResponse listAccountsResponse) {
        return streamOfOrEmpty(listAccountsResponse.accounts())
                   .filter(account -> account.status().equals(AccountStatus.ACTIVE))
                   .map(account -> ResourceModel.builder()
                                       .email(account.email())
                                       .accountId(account.id())
                                       .accountName(account.name())
                                       .build())
                   .collect(Collectors.toList());
    }

    private static <T> Stream<T> streamOfOrEmpty(final Collection<T> collection) {
        return Optional.ofNullable(collection)
                   .map(Collection::stream)
                   .orElseGet(Stream::empty);
    }

    static ListParentsRequest translateToListParentsRequest(final ResourceModel model) {
        return ListParentsRequest.builder()
                   .childId(model.getAccountId())
                   .build();
    }

    static ListRootsRequest translateToListRootsRequest() {
        return ListRootsRequest.builder()
                   .build();
    }

    static CreateAccountRequest translateToCreateAccountRequest(final ResourceModel model, final ResourceHandlerRequest<ResourceModel> request) {
        return CreateAccountRequest.builder()
                   .accountName(model.getAccountName())
                   .email(model.getEmail())
                   .roleName(model.getRoleName())
                   .tags(translateTagsForTagResourceRequest(model.getTags(), request.getDesiredResourceTags()))
                   .build();
    }

    static TagResourceRequest translateToTagResourceRequest(Set<Tag> tags, String accountId) {
        return TagResourceRequest.builder()
                   .resourceId(accountId)
                   .tags(tags)
                   .build();
    }

    static UntagResourceRequest translateToUntagResourceRequest(Set<String> tagKeys, String accountId) {
        return UntagResourceRequest.builder()
                   .resourceId(accountId)
                   .tagKeys(tagKeys)
                   .build();
    }

    static CloseAccountRequest translateToCloseAccountRequest(final ResourceModel model) {
        return CloseAccountRequest.builder().accountId(model.getAccountId()).build();
    }

    static MoveAccountRequest translateToMoveAccountRequest(final ResourceModel model, String destinationParentId, String sourceParentId) {
        return MoveAccountRequest.builder()
                   .accountId(model.getAccountId())
                   .sourceParentId(sourceParentId)
                   .destinationParentId(destinationParentId)
                   .build();
    }

    static ListParentsRequest translateToListParentsRequest(final String childId) {
        return ListParentsRequest.builder().childId(childId).build();
    }

    static DescribeAccountRequest translateToDescribeAccountRequest(final ResourceModel model) {
        return DescribeAccountRequest.builder()
                   .accountId(model.getAccountId())
                   .build();
    }

    static Collection<Tag> translateTagsForTagResourceRequest(Set<software.amazon.organizations.account.Tag> tags, Map<String, String> desiredResourceTags) {
        final Collection<Tag> tagsToReturn = new ArrayList<>();

        if (tags != null) {
            for (software.amazon.organizations.account.Tag inputTags : tags) {
                Tag tag = buildTag(inputTags.getKey(), inputTags.getValue());
                tagsToReturn.add(tag);
            }
        }

        if (desiredResourceTags != null) {
            for (Map.Entry<String, String> resourceTag : desiredResourceTags.entrySet()) {
                Tag tag = buildTag(resourceTag.getKey(), resourceTag.getValue());
                tagsToReturn.add(tag);
            }
        }

        return tagsToReturn;
    }

    static ResourceModel translateFromAllDescribeResponse(final ResourceModel model, final ListTagsForResourceResponse listTagsForResourceResponse) {
        return ResourceModel.builder()
                   .accountId(model.getAccountId())
                   .accountName(model.getAccountName())
                   .email(model.getEmail())
                   .arn(model.getArn())
                   .status(model.getStatus())
                   .joinedTimestamp(model.getJoinedTimestamp())
                   .joinedMethod(model.getJoinedMethod())
                   .parentIds(model.getParentIds())
                   .tags(translateTagsFromSdkResponse(listTagsForResourceResponse.tags()))
                   .roleName(model.getRoleName())
                   .build();
    }

    static Set<software.amazon.organizations.account.Tag> translateTagsFromSdkResponse(List<Tag> tags) {
        if (tags == null) return new HashSet<>();

        final Set<software.amazon.organizations.account.Tag> tagsToReturn = new HashSet<>();
        for (Tag inputTag : tags) {
            software.amazon.organizations.account.Tag tag = software.amazon.organizations.account.Tag.builder()
                                                                .key(inputTag.key())
                                                                .value(inputTag.value())
                                                                .build();
            tagsToReturn.add(tag);
        }
        return tagsToReturn;
    }

    /**
     * Request to list resources
     *
     * @param nextToken token passed to the aws service list resources request
     * @return awsRequest the aws service request to list resources within aws account
     */
    static AwsRequest translateToListRequest(final String nextToken) {
        final AwsRequest awsRequest = null;
        // TODO: construct a request
        // e.g. https://github.com/aws-cloudformation/aws-cloudformation-resource-providers-logs/blob/2077c92299aeb9a68ae8f4418b5e932b12a8b186/aws-logs-loggroup/src/main/java/com/aws/logs/loggroup/Translator.java#L26-L31
        return awsRequest;
    }
}
