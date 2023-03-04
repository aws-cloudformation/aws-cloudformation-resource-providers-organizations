package software.amazon.organizations.organizationalunit;

import software.amazon.awssdk.services.organizations.OrganizationsClient;
import software.amazon.awssdk.services.organizations.model.DescribeOrganizationalUnitRequest;
import software.amazon.awssdk.services.organizations.model.DescribeOrganizationalUnitResponse;
import software.amazon.awssdk.services.organizations.model.ListParentsRequest;
import software.amazon.awssdk.services.organizations.model.ListParentsResponse;
import software.amazon.awssdk.services.organizations.model.ListTagsForResourceRequest;
import software.amazon.awssdk.services.organizations.model.ListTagsForResourceResponse;
import software.amazon.awssdk.services.organizations.model.Parent;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.ProgressEvent;
import software.amazon.cloudformation.proxy.ProxyClient;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;
import software.amazon.organizations.utils.OrgsLoggerWrapper;

public class ReadHandler extends BaseHandlerStd {
    private OrgsLoggerWrapper log;

    protected ProgressEvent<ResourceModel, CallbackContext> handleRequest(
        final AmazonWebServicesClientProxy awsClientProxy,
        final ResourceHandlerRequest<ResourceModel> request,
        final CallbackContext callbackContext,
        final ProxyClient<OrganizationsClient> orgsClient,
        final OrgsLoggerWrapper logger
    ) {

        this.log = logger;
        final ResourceModel model = request.getDesiredResourceState();

        String ouId = model.getId();

        // Call DescribeOrganizationalUnit API
        logger.log(String.format("Requesting DescribeOrganizationalUnit w/ OU id: %s.\n", ouId));
        return ProgressEvent.progress(model, callbackContext)
            .then(progress ->
                awsClientProxy.initiate("AWS-Organizations-OrganizationalUnit::DescribeOrganizationalUnit", orgsClient, progress.getResourceModel(), progress.getCallbackContext())
                .translateToServiceRequest(Translator::translateToDescribeOrganizationalUnitRequest)
                .makeServiceCall(this::describeOrganizationalUnit)
                .handleError((organizationsRequest, e, orgsClient1, model1, context) ->
                                 handleErrorInGeneral(organizationsRequest, e, orgsClient1, model1, context, logger, Constants.Action.DESCRIBE_OU, Constants.Handler.READ))
                .done(describeOrganizationalUnitResponse -> {
                    model.setArn(describeOrganizationalUnitResponse.organizationalUnit().arn());
                    model.setId(describeOrganizationalUnitResponse.organizationalUnit().id());
                    model.setName(describeOrganizationalUnitResponse.organizationalUnit().name());
                    return ProgressEvent.progress(model, callbackContext);
                })
            )
            .then(progress -> listParents(awsClientProxy, request, model, callbackContext, orgsClient, logger))
            .then(progress -> listTagsForOrganizationalUnit(awsClientProxy, request, model, callbackContext, orgsClient, logger));
    }

    protected ProgressEvent<ResourceModel, CallbackContext> listTagsForOrganizationalUnit(
        final AmazonWebServicesClientProxy awsClientProxy,
        final ResourceHandlerRequest<ResourceModel> request,
        final ResourceModel model,
        final CallbackContext callbackContext,
        final ProxyClient<OrganizationsClient> orgsClient,
        final OrgsLoggerWrapper logger) {

            String ouId = model.getId();

            logger.log(String.format("Listing tags for OU id: %s.\n", ouId));
            return awsClientProxy.initiate("AWS-Organizations-OrganizationalUnit::ListTagsForResource", orgsClient, model, callbackContext)
                .translateToServiceRequest(resourceModel -> Translator.translateToListTagsForResourceRequest(ouId))
                .makeServiceCall(this::listTagsForResource)
                .handleError((organizationsRequest, e, orgsClient1, model1, context) ->
                                 handleErrorInGeneral(organizationsRequest, e, orgsClient1, model1, context, logger, Constants.Action.LIST_TAGS_FOR_OU, Constants.Handler.READ))
                .done(listTagsForResourceResponse -> ProgressEvent.defaultSuccessHandler(Translator.translateFromDescribeResponse(model, listTagsForResourceResponse)));
    }

    protected ProgressEvent<ResourceModel, CallbackContext> listParents(
        final AmazonWebServicesClientProxy awsClientProxy,
        final ResourceHandlerRequest<ResourceModel> request,
        final ResourceModel model,
        final CallbackContext callbackContext,
        final ProxyClient<OrganizationsClient> orgsClient,
        final OrgsLoggerWrapper logger) {

            String ouId = model.getId();

            logger.log(String.format("Listing parents for OU id: %s.\n", ouId));
            return awsClientProxy.initiate("AWS-Organizations-OrganizationalUnit::ListParents", orgsClient, model, callbackContext)
                .translateToServiceRequest(Translator::translateToListParentsRequest)
                .makeServiceCall(this::listParents)
                .handleError((organizationsRequest, e, orgsClient1, model1, context) ->
                                 handleErrorInGeneral(organizationsRequest, e, orgsClient1, model1, context, logger, Constants.Action.LIST_PARENTS, Constants.Handler.READ))
                .done(listParentsResponse -> {
                    Parent parent = listParentsResponse.parents().get(0);
                    model.setParentId(parent.id());
                    return ProgressEvent.progress(model, callbackContext);
                });
    }

    protected DescribeOrganizationalUnitResponse describeOrganizationalUnit(final DescribeOrganizationalUnitRequest describeOrganizationalUnitRequest, final ProxyClient<OrganizationsClient> orgsClient) {
        log.log(String.format("Calling describeOrganizationalUnit API for OU [%s].", describeOrganizationalUnitRequest.organizationalUnitId()));
        final DescribeOrganizationalUnitResponse describeOrganizationalUnitResponse = orgsClient.injectCredentialsAndInvokeV2(describeOrganizationalUnitRequest, orgsClient.client()::describeOrganizationalUnit);
        return describeOrganizationalUnitResponse;
    }

    // DescribeOU call doesn't return tags on OU so ListTags call needs to be made separately
    protected ListTagsForResourceResponse listTagsForResource(final ListTagsForResourceRequest listTagsForResourceRequest, final ProxyClient<OrganizationsClient> orgsClient) {
        log.log(String.format("Calling listTagsForResource API for resource [%s].", listTagsForResourceRequest.resourceId()));
        final ListTagsForResourceResponse listTagsForResourceResponse = orgsClient.injectCredentialsAndInvokeV2(listTagsForResourceRequest, orgsClient.client()::listTagsForResource);
        return listTagsForResourceResponse;
    }

    // DescribeOU call doesn't return parentId of OU so ListParents call needs to be made separately
    protected ListParentsResponse listParents(final ListParentsRequest listParentsRequest, final ProxyClient<OrganizationsClient> orgsClient) {
        log.log(String.format("Calling listParents API for OU [%s].", listParentsRequest.childId()));
        final ListParentsResponse listParentsResponse = orgsClient.injectCredentialsAndInvokeV2(listParentsRequest, orgsClient.client()::listParents);
        return listParentsResponse;
    }
}
