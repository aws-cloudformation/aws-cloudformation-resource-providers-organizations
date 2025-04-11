package software.amazon.organizations.organizationalunit;

import software.amazon.awssdk.services.organizations.model.CreateOrganizationalUnitRequest;
import software.amazon.awssdk.services.organizations.model.DeleteOrganizationalUnitRequest;
import software.amazon.awssdk.services.organizations.model.DescribeOrganizationalUnitRequest;
import software.amazon.awssdk.services.organizations.model.ListOrganizationalUnitsForParentRequest;
import software.amazon.awssdk.services.organizations.model.ListOrganizationalUnitsForParentResponse;
import software.amazon.awssdk.services.organizations.model.ListParentsRequest;
import software.amazon.awssdk.services.organizations.model.ListTagsForResourceRequest;
import software.amazon.awssdk.services.organizations.model.ListTagsForResourceResponse;
import software.amazon.awssdk.services.organizations.model.OrganizationalUnit;
import software.amazon.awssdk.services.organizations.model.Tag;
import software.amazon.awssdk.services.organizations.model.TagResourceRequest;
import software.amazon.awssdk.services.organizations.model.UntagResourceRequest;
import software.amazon.awssdk.services.organizations.model.UpdateOrganizationalUnitRequest;
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

public class Translator {

    static CreateOrganizationalUnitRequest translateToCreateOrganizationalUnitRequest(final ResourceModel model, final ResourceHandlerRequest<ResourceModel> request) {
        return CreateOrganizationalUnitRequest.builder()
                .name(model.getName())
                .parentId(model.getParentId())
                .tags(translateTagsForTagResourceRequest(model.getTags(), request.getDesiredResourceTags()))
                .build();
    }

    static DescribeOrganizationalUnitRequest translateToDescribeOrganizationalUnitRequest(final ResourceModel model) {
        return DescribeOrganizationalUnitRequest.builder()
                .organizationalUnitId(model.getId())
                .build();
    }

    static UpdateOrganizationalUnitRequest translateToUpdateOrganizationalUnitRequest(final ResourceModel model) {
        return UpdateOrganizationalUnitRequest.builder()
                .name(model.getName())
                .organizationalUnitId(model.getId())
                .build();
    }

    static DeleteOrganizationalUnitRequest translateToDeleteOrganizationalUnitRequest(final ResourceModel model) {
        return DeleteOrganizationalUnitRequest.builder()
                .organizationalUnitId(model.getId())
                .build();
    }

    static ListOrganizationalUnitsForParentRequest translateToListOrganizationalUnitsForParentRequest(String nextToken, final ResourceModel model) {
        // Max results set to 20 (the upper limit) to list out all items
        return ListOrganizationalUnitsForParentRequest.builder()
                .maxResults(20)
                .nextToken(nextToken)
                .parentId(model.getParentId())
                .build();
    }

    static ListParentsRequest translateToListParentsRequest(final ResourceModel model) {
        return ListParentsRequest.builder()
                .childId(model.getId())
                .build();
    }

    static TagResourceRequest translateToTagResourceRequest(Set<Tag> tags, String organizationalUnitId) {
        return TagResourceRequest.builder()
                .resourceId(organizationalUnitId)
                .tags(tags)
                .build();
    }

    static UntagResourceRequest translateToUntagResourceRequest(Set<String> tagKeys, String organizationalUnitId) {
        return UntagResourceRequest.builder()
                .resourceId(organizationalUnitId)
                .tagKeys(tagKeys)
                .build();
    }

    static ListTagsForResourceRequest translateToListTagsForResourceRequest(final String id) {
        return ListTagsForResourceRequest.builder()
                .resourceId(id)
                .build();
    }

    static Collection<Tag> translateTagsForTagResourceRequest(Set<software.amazon.organizations.organizationalunit.Tag> tags, Map<String, String> desiredResourceTags) {
        Map<String, String> tagsToReturn = new HashMap<>();

        if (desiredResourceTags != null) {
            tagsToReturn.putAll(desiredResourceTags);
        }

        if (tags != null) {
            tags.forEach(tag -> tagsToReturn.put(tag.getKey(), tag.getValue()));
        }

        return TagsHelper.tagMapToTagSetConverter(tagsToReturn);
    }

    static ResourceModel getResourceModelFromOrganizationalUnit(
            final OrganizationalUnit organizationalUnit) {

        ResourceModel model = ResourceModel.builder()
            .arn(organizationalUnit.arn())
            .id(organizationalUnit.id())
            .name(organizationalUnit.name())
            .build();

        return model;
    }

    static ResourceModel translateFromDescribeResponse(final ResourceModel model, final ListTagsForResourceResponse listTagsForResourceResponse) {
        return ResourceModel.builder()
            .arn(model.getArn())
            .id(model.getId())
            .name(model.getName())
            .parentId(model.getParentId())
            .tags(translateTagsFromSdkResponse(listTagsForResourceResponse.tags()))
            .build();
    }

    static Set<software.amazon.organizations.organizationalunit.Tag> translateTagsFromSdkResponse(List<Tag> tags) {
        if (tags == null) return new HashSet<>();

        final Set<software.amazon.organizations.organizationalunit.Tag> tagsToReturn = new HashSet<>();
        for (Tag inputTags : tags) {
            software.amazon.organizations.organizationalunit.Tag tag = software.amazon.organizations.organizationalunit.Tag.builder()
                        .key(inputTags.key())
                        .value(inputTags.value())
                        .build();
            tagsToReturn.add(tag);
        }

        return tagsToReturn;
    }

    public static List<ResourceModel> translateListAccountsResponseToResourceModel(final ListOrganizationalUnitsForParentResponse listOrganizationalUnitsForParentResponse) {
        return streamOfOrEmpty(listOrganizationalUnitsForParentResponse.organizationalUnits())
                .map(organizationalUnit -> Translator.getResourceModelFromOrganizationalUnit(organizationalUnit)).collect(Collectors.toList());
    }

    private static <T> Stream<T> streamOfOrEmpty(final Collection<T> collection) {
        return Optional.ofNullable(collection)
                .map(Collection::stream)
                .orElseGet(Stream::empty);
    }
}
