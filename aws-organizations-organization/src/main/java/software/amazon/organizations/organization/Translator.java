package software.amazon.organizations.organization;

import software.amazon.awssdk.services.organizations.model.CreateOrganizationRequest;
import software.amazon.awssdk.services.organizations.model.DeleteOrganizationRequest;
import software.amazon.awssdk.services.organizations.model.DescribeOrganizationRequest;
import software.amazon.awssdk.services.organizations.model.DescribeOrganizationResponse;
import software.amazon.awssdk.services.organizations.model.ListRootsRequest;
import software.amazon.awssdk.services.organizations.model.ListRootsResponse;
import software.amazon.awssdk.services.organizations.model.Organization;

import java.util.ArrayList;
import java.util.List;

/**
 * This class is a centralized placeholder for
 * - api request construction
 * - object translation to/from aws sdk
 * - resource model construction for read/list handlers
 */

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

    static ResourceModel translateFromListRootsResponse(final ListRootsResponse listRootsResponse, final ResourceModel model) {
        if (!listRootsResponse.hasRoots()) {
            return model;
        }
        List<String> rootIds = new ArrayList<>();
        String nextToken;
        do {
            listRootsResponse.roots().forEach(root -> rootIds.add(root.id()));
            nextToken = listRootsResponse.nextToken();
        } while (nextToken != null);
        model.setRootIds(rootIds);
        return model;
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
            .rootIds(model.getRootIds())
            .build();
    }

    static DeleteOrganizationRequest translateToDeleteRequest() {
        return DeleteOrganizationRequest.builder().build();
    }
}
