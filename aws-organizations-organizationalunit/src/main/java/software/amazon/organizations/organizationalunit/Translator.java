package software.amazon.organizations.organizationalunit;

import software.amazon.awssdk.services.organizations.model.CreateOrganizationalUnitRequest;
import software.amazon.awssdk.services.organizations.model.CreateOrganizationalUnitResponse;
import software.amazon.awssdk.services.organizations.model.DeleteOrganizationalUnitRequest;
import software.amazon.awssdk.services.organizations.model.DeleteOrganizationalUnitResponse;
import software.amazon.awssdk.services.organizations.model.DescribeOrganizationalUnitRequest;
import software.amazon.awssdk.services.organizations.model.DescribeOrganizationalUnitResponse;
import software.amazon.awssdk.services.organizations.model.ListOrganizationalUnitsForParentRequest;
import software.amazon.awssdk.services.organizations.model.OrganizationalUnit;
import software.amazon.awssdk.services.organizations.model.Tag;
import software.amazon.awssdk.services.organizations.model.UpdateOrganizationalUnitRequest;
import software.amazon.awssdk.services.organizations.model.UpdateOrganizationalUnitResponse;
import software.amazon.awssdk.services.organizations.OrganizationsClient;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.ProxyClient;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

public class Translator {

    static CreateOrganizationalUnitRequest translateToCreateOrganizationalUnitRequest(final ResourceModel model) {
        return CreateOrganizationalUnitRequest.builder()
                .name(model.getName())
                .parentId(model.getParentId())
                .tags(translateTags(model.getTags()))
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

    private static Collection<Tag> translateTags(Set<software.amazon.organizations.organizationalunit.Tag> tags) {
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

    static ResourceModel translateFromCreateOrganizationalUnitResponse(final CreateOrganizationalUnitResponse createOrganizationalUnitResponse, final ResourceModel model) {
        OrganizationalUnit organizationalUnit = createOrganizationalUnitResponse.organizationalUnit();
        return ResourceModel.builder()
            .arn(organizationalUnit.arn())
            .id(organizationalUnit.id())
            .name(organizationalUnit.name())
            .build();
    }

    static ResourceModel translateFromDescribeOrganizationalUnitResponse(final DescribeOrganizationalUnitResponse describeOrganizationalUnitResponse, final ResourceModel model) {
        OrganizationalUnit organizationalUnit = describeOrganizationalUnitResponse.organizationalUnit();
        return ResourceModel.builder()
            .arn(organizationalUnit.arn())
            .id(organizationalUnit.id())
            .name(organizationalUnit.name())
            .build();
    }

    static ResourceModel translateFromUpdateOrganizationalUnitResponse(final UpdateOrganizationalUnitResponse updateOrganizationalUnitResponse, final ResourceModel model) {
        OrganizationalUnit organizationalUnit = updateOrganizationalUnitResponse.organizationalUnit();
        return ResourceModel.builder()
            .arn(organizationalUnit.arn())
            .id(organizationalUnit.id())
            .name(organizationalUnit.name())
            .build();
    }
}
