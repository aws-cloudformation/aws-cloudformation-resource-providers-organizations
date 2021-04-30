package software.amazon.organizations.organization;

import software.amazon.awssdk.services.organizations.OrganizationsClient;
import software.amazon.awssdk.services.organizations.model.AwsOrganizationsNotInUseException;
import software.amazon.awssdk.services.organizations.model.DescribeOrganizationRequest;
import software.amazon.awssdk.services.organizations.model.DescribeOrganizationResponse;

import software.amazon.cloudformation.exceptions.CfnNotFoundException;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.Logger;
import software.amazon.cloudformation.proxy.ProgressEvent;
import software.amazon.cloudformation.proxy.ProxyClient;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;

public class ReadHandler extends BaseHandlerStd {
    private Logger logger;

    protected ProgressEvent<ResourceModel, CallbackContext> handleRequest(
        final AmazonWebServicesClientProxy awsClientProxy,
        final ResourceHandlerRequest<ResourceModel> request,
        final CallbackContext callbackContext,
        final ProxyClient<OrganizationsClient> orgsClient,
        final Logger logger) {

        this.logger = logger;

        final ResourceModel model = request.getDesiredResourceState();

        return awsClientProxy.initiate("AWS-Organizations-Organization::Read", orgsClient, model, callbackContext)
            .translateToServiceRequest(t -> Translator.translateToReadRequest())
            .makeServiceCall(this::describeOrganization)
            .done(describeOrganizationResponse -> ProgressEvent.defaultSuccessHandler(Translator.translateFromReadResponse(describeOrganizationResponse)));
    }

    protected DescribeOrganizationResponse describeOrganization(final DescribeOrganizationRequest describeOrganizationRequest, final ProxyClient<OrganizationsClient> orgsClient) {
        try {
            final DescribeOrganizationResponse response = orgsClient.injectCredentialsAndInvokeV2(describeOrganizationRequest, orgsClient.client()::describeOrganization);
            logger.log(String.format("%s has successfully been read.", ResourceModel.TYPE_NAME));
            return response;
        } catch(AwsOrganizationsNotInUseException e) {
            throw new CfnNotFoundException(ResourceModel.TYPE_NAME, ResourceModel.IDENTIFIER_KEY_ID);
        }
    }
}
