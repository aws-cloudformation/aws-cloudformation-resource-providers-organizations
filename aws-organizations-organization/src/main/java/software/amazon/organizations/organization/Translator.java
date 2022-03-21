package software.amazon.organizations.organization;

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
            .rootIds(model.getRootIds())
            .build();
    }

    static DeleteOrganizationRequest translateToDeleteRequest() {
        return DeleteOrganizationRequest.builder().build();
    }
}
