package software.amazon.organizations.organizationalunit;

import software.amazon.awssdk.services.organizations.model.CreateOrganizationalUnitRequest;
import software.amazon.awssdk.services.organizations.model.CreateOrganizationalUnitResponse;
import software.amazon.awssdk.services.organizations.model.DeleteOrganizationalUnitRequest;
import software.amazon.awssdk.services.organizations.model.DeleteOrganizationalUnitResponse;
import software.amazon.awssdk.services.organizations.model.DescribeOrganizationalUnitRequest;
import software.amazon.awssdk.services.organizations.model.DescribeOrganizationalUnitResponse;
import software.amazon.awssdk.services.organizations.model.ListOrganizationalUnitsForParentRequest;
import software.amazon.awssdk.services.organizations.model.ListTagsForResourceRequest;
import software.amazon.awssdk.services.organizations.model.ListTagsForResourceResponse;
import software.amazon.awssdk.services.organizations.model.OrganizationalUnit;
import software.amazon.awssdk.services.organizations.model.Tag;
import software.amazon.awssdk.services.organizations.model.TagResourceRequest;
import software.amazon.awssdk.services.organizations.model.UntagResourceRequest;
import software.amazon.awssdk.services.organizations.model.UpdateOrganizationalUnitRequest;
import software.amazon.awssdk.services.organizations.model.UpdateOrganizationalUnitResponse;
import software.amazon.awssdk.services.organizations.OrganizationsClient;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.ProxyClient;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;


import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class Translator {

    static CreateOrganizationalUnitRequest translateToCreateOrganizationalUnitRequest(final ResourceModel model) {
        return CreateOrganizationalUnitRequest.builder()
                .name(model.getName())
                .parentId(model.getParentId())
                .tags(translateTagsForTagResourceRequest(model.getTags()))
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

    static TagResourceRequest translateToTagResourceRequest(Collection<Tag> tags, String organizationalUnitId) {
        return TagResourceRequest.builder()
                .resourceId(organizationalUnitId)
                .tags(tags)
                .build();
    }

    static UntagResourceRequest translateToUntagResourceRequest(List<String> tagKeys, String organizationalUnitId) {
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

    static Collection<Tag> translateTagsForTagResourceRequest(Set<software.amazon.organizations.organizationalunit.Tag> tags) {
        if (tags == null) return new ArrayList<>();

        final Collection<Tag> tagsToReturn = new ArrayList<>();
        for (software.amazon.organizations.organizationalunit.Tag inputTags : tags) {
            Tag tag = Tag.builder()
                        .key(inputTags.getKey())
                        .value(inputTags.getValue())
                        .build();
            tagsToReturn.add(tag);
        }

        return tagsToReturn;
    }

    static ResourceModel getResourceModelFromOrganizationalUnit(
            final ResourceHandlerRequest<ResourceModel> request,
            final AmazonWebServicesClientProxy proxy,
            final ProxyClient<OrganizationsClient> proxyClient,
            final OrganizationalUnit organizationalUnit) {

        ResourceModel model = ResourceModel.builder()
            .arn(organizationalUnit.arn())
            .id(organizationalUnit.id())
            .name(organizationalUnit.name())
            .build();

        return model;
    }

    static ResourceModel translateFromDescribeOrganizationalUnitResponse(final ResourceModel model, final ListTagsForResourceResponse listTagsForResourceResponse) {
        return ResourceModel.builder()
            .arn(model.getArn())
            .id(model.getId())
            .name(model.getName())
            .tags(translateTagsFromSdkResponse(listTagsForResourceResponse.tags()))
            .build();
    }

    static ResourceModel translateFromUpdateOrganizationalUnitResponse(final UpdateOrganizationalUnitResponse updateOrganizationalUnitResponse, final ListTagsForResourceResponse listTagsForResourceResponse) {
        OrganizationalUnit organizationalUnit = updateOrganizationalUnitResponse.organizationalUnit();
        return ResourceModel.builder()
            .arn(organizationalUnit.arn())
            .id(organizationalUnit.id())
            .name(organizationalUnit.name())
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
}
