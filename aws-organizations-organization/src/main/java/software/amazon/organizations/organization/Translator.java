package software.amazon.organizations.organization;

import software.amazon.awssdk.services.organizations.model.CreateOrganizationRequest;
import software.amazon.awssdk.services.organizations.model.DeleteOrganizationRequest;
import software.amazon.awssdk.services.organizations.model.DescribeOrganizationRequest;
import software.amazon.awssdk.services.organizations.model.DescribeOrganizationResponse;
import software.amazon.awssdk.services.organizations.model.Organization;

/**
 * This class is a centralized placeholder for
 *  - api request construction
 *  - object translation to/from aws sdk
 *  - resource model construction for read/list handlers
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

  static ResourceModel translateFromReadResponse(final DescribeOrganizationResponse describeOrganizationResponse) {
    Organization organization = describeOrganizationResponse.organization();
    return ResourceModel.builder()
      .id(organization.id())
      .arn(organization.arn())
      .featureSet(organization.featureSetAsString())
      .masterAccountArn(organization.masterAccountArn())
      .masterAccountId(organization.masterAccountId())
      .masterAccountEmail(organization.masterAccountEmail())
      .build();
  }

  static DeleteOrganizationRequest translateToDeleteRequest() {
    return DeleteOrganizationRequest.builder().build();
  }
}
