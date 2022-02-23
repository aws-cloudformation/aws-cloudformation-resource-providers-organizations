package software.amazon.organizations.organizationalunit;

import software.amazon.awssdk.services.organizations.OrganizationsClient;
import software.amazon.awssdk.services.organizations.model.DescribeOrganizationalUnitRequest;
import software.amazon.awssdk.services.organizations.model.DescribeOrganizationalUnitResponse;
import software.amazon.awssdk.services.organizations.model.OrganizationalUnit;
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

        String ouId = model.getId();

        // Call DescribeOrganizationalUnit API
        logger.log(String.format("Requesting DescribeOrganizationalUnit w/ OU id: %s.\n", ouId));
        return ProgressEvent.progress(model, callbackContext)
            .then(progress ->
                awsClientProxy.initiate("AWS-Organizations-OrganizationalUnit::DescribeOrganizationalUnit", orgsClient, progress.getResourceModel(), progress.getCallbackContext())
                .translateToServiceRequest(Translator::translateToDescribeOrganizationalUnitRequest)
                .makeServiceCall(this::describeOrganizationalUnit)
                .handleError((organizationsRequest, e, orgsClient1, model1, context) -> handleError(
                    organizationsRequest, e, orgsClient1, model1, context, logger))
                .done(describeOrganizationalUnitResponse -> ProgressEvent.defaultSuccessHandler(Translator.translateFromDescribeOrganizationalUnitResponse(describeOrganizationalUnitResponse, model)))
            );
    }

    protected DescribeOrganizationalUnitResponse describeOrganizationalUnit(final DescribeOrganizationalUnitRequest describeOrganizationalUnitRequest, final ProxyClient<OrganizationsClient> orgsClient) {
        logger.log(String.format("Calling describeOrganizationalUnit API."));
        final DescribeOrganizationalUnitResponse describeOrganizationalUnitResponse = orgsClient.injectCredentialsAndInvokeV2(describeOrganizationalUnitRequest, orgsClient.client()::describeOrganizationalUnit);
	    return describeOrganizationalUnitResponse;
    }
}
