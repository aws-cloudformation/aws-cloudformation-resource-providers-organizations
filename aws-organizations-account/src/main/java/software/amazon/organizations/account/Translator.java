package software.amazon.organizations.account;

import software.amazon.awssdk.awscore.AwsRequest;
import software.amazon.awssdk.services.account.model.GetAlternateContactRequest;
import software.amazon.awssdk.services.account.model.PutAlternateContactRequest;
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
    static DescribeCreateAccountStatusRequest translateToDescribeCreateAccountStatusRequest(final ResourceModel model) {
        return DescribeCreateAccountStatusRequest.builder()
                   .createAccountRequestId(model.getCreateAccountRequestId())
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

    static GetAlternateContactRequest translateToGetAlternateContactRequest(final ResourceModel model, final String alternateContactType) {
        return GetAlternateContactRequest.builder()
                   .accountId(model.getAccountId())
                   .alternateContactType(alternateContactType)
                   .build();
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

    static CreateAccountRequest translateToCreateAccountRequest(final ResourceModel model) {
        return CreateAccountRequest.builder()
                   .accountName(model.getAccountName())
                   .email(model.getEmail())
                   .roleName(model.getRoleName())
                   .tags(translateTagsForTagResourceRequest(model.getTags()))
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

    static PutAlternateContactRequest translateToPutAlternateContactTypeRequest(final ResourceModel model, String alternateContactType, AlternateContact alternateContact){
        return PutAlternateContactRequest
                   .builder()
                   .accountId(model.getAccountId())
                   .alternateContactType(alternateContactType)
                   .emailAddress(alternateContact.getEmailAddress())
                   .name(alternateContact.getName())
                   .phoneNumber(alternateContact.getPhoneNumber())
                   .title(alternateContact.getTitle())
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

    static Collection<software.amazon.awssdk.services.organizations.model.Tag> translateTagsForTagResourceRequest(Set<software.amazon.organizations.account.Tag> tags) {
        if (tags == null) return new ArrayList<>();
        final Collection<software.amazon.awssdk.services.organizations.model.Tag> tagsToReturn = new ArrayList<>();
        for (software.amazon.organizations.account.Tag inputTags : tags) {
            software.amazon.awssdk.services.organizations.model.Tag tag = Tag.builder()
                                                                              .key(inputTags.getKey())
                                                                              .value(inputTags.getValue())
                                                                              .build();
            tagsToReturn.add(tag);
        }
        return tagsToReturn;
    }

    static ResourceModel translateFromAllDescribeResponse(final ResourceModel model, final ListTagsForResourceResponse listTagsForResourceResponse) {
        return ResourceModel.builder()
                   .accountId(model.getAccountId())
                   .accountName(model.getAccountName())
                   .email(model.getEmail())
                   .parentIds(model.getParentIds())
                   .alternateContacts(model.getAlternateContacts())
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
