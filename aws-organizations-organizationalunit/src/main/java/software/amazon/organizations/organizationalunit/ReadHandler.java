package software.amazon.organizations.organizationalunit;

import software.amazon.awssdk.services.organizations.OrganizationsClient;
import software.amazon.awssdk.services.organizations.model.DescribeOrganizationalUnitRequest;
import software.amazon.awssdk.services.organizations.model.DescribeOrganizationalUnitResponse;
import software.amazon.awssdk.services.organizations.model.ListTagsForResourceRequest;
import software.amazon.awssdk.services.organizations.model.ListTagsForResourceResponse;
import software.amazon.awssdk.services.organizations.model.OrganizationalUnit;
import software.amazon.awssdk.services.organizations.model.Tag;
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
                .done(describeOrganizationalUnitResponse -> {
                    model.setArn(describeOrganizationalUnitResponse.organizationalUnit().arn());
                    model.setId(describeOrganizationalUnitResponse.organizationalUnit().id());
                    model.setName(describeOrganizationalUnitResponse.organizationalUnit().name());
                    return ProgressEvent.progress(model, callbackContext);
                })
            )
            .then(progress -> listTagsForOrganizationalUnit(awsClientProxy, request, model, callbackContext, orgsClient, logger));
    }

    protected ProgressEvent<ResourceModel, CallbackContext> listTagsForOrganizationalUnit(
        final AmazonWebServicesClientProxy awsClientProxy,
        final ResourceHandlerRequest<ResourceModel> request,
        final ResourceModel model,
        final CallbackContext callbackContext,
        final ProxyClient<OrganizationsClient> orgsClient,
        final Logger logger) {

            String ouId = model.getId();

            logger.log(String.format("Listing tags for OU id: %s.\n", ouId));
            return awsClientProxy.initiate("AWS-Organizations-OrganizationalUnit::ListTagsForResource", orgsClient, model, callbackContext)
                .translateToServiceRequest(resourceModel -> Translator.translateToListTagsForResourceRequest(ouId))
                .makeServiceCall(this::listTagsForResource)
                .handleError((organizationsRequest, e, orgsClient1, model1, context) -> handleError(
                    organizationsRequest, e, orgsClient1, model1, context, logger))
                .done(listTagsForResourceResponse -> ProgressEvent.defaultSuccessHandler(Translator.translateFromDescribeResponse(model, listTagsForResourceResponse)));
    }

    protected DescribeOrganizationalUnitResponse describeOrganizationalUnit(final DescribeOrganizationalUnitRequest describeOrganizationalUnitRequest, final ProxyClient<OrganizationsClient> orgsClient) {
        logger.log("Calling describeOrganizationalUnit API.");
        final DescribeOrganizationalUnitResponse describeOrganizationalUnitResponse = orgsClient.injectCredentialsAndInvokeV2(describeOrganizationalUnitRequest, orgsClient.client()::describeOrganizationalUnit);
	    return describeOrganizationalUnitResponse;
    }

    protected ListTagsForResourceResponse listTagsForResource(final ListTagsForResourceRequest listTagsForResourceRequest, final ProxyClient<OrganizationsClient> orgsClient) {
        logger.log("Calling listTagsForResource API.");
        final ListTagsForResourceResponse listTagsForResourceResponse = orgsClient.injectCredentialsAndInvokeV2(listTagsForResourceRequest, orgsClient.client()::listTagsForResource);
	    return listTagsForResourceResponse;
    }
}
