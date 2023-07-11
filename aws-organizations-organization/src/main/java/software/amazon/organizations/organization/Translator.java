package software.amazon.organizations.organization;

import software.amazon.awssdk.services.organizations.model.CreateOrganizationRequest;
import software.amazon.awssdk.services.organizations.model.DescribeOrganizationRequest;
import software.amazon.awssdk.services.organizations.model.ListRootsRequest;
import software.amazon.awssdk.services.organizations.model.DeleteOrganizationRequest;
import software.amazon.awssdk.services.organizations.model.DescribeOrganizationResponse;
import software.amazon.awssdk.services.organizations.model.Organization;

import java.util.List;

public class Translator {
    static CreateOrganizationRequest translateToCreateRequest(final ResourceModel model) {
        return CreateOrganizationRequest.builder()
                .featureSet(model.getFeatureSet())
                .build();
    }
    static DescribeOrganizationRequest translateToReadRequest() {
        return DescribeOrganizationRequest.builder().build();
    }
    static ListRootsRequest translateToListRootsRequest() {
        return ListRootsRequest.builder().build();
    }

    static ResourceModel translateFromReadResponse(final DescribeOrganizationResponse describeOrganizationResponse, final ResourceModel model) {
        Organization organization = describeOrganizationResponse.organization();
        return ResourceModel.builder()
                .id(organization.id())
                .arn(organization.arn())
                .featureSet(organization.featureSetAsString())
                .managementAccountArn(organization.masterAccountArn())
                .managementAccountId(organization.masterAccountId())
                .managementAccountEmail(organization.masterAccountEmail())
                .rootId(model.getRootId())
                .build();
    }

    static List<ResourceModel> translatetoListReadResponse(final DescribeOrganizationResponse describeOrganizationResponse, final ResourceModel model, final List<ResourceModel> models) {
        models.add(translateFromReadResponse(describeOrganizationResponse, model));
        return models;
    }

    static DeleteOrganizationRequest translateToDeleteRequest() {
        return DeleteOrganizationRequest.builder().build();
    }
}
