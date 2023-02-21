package software.amazon.organizations.resourcepolicy;

import software.amazon.awssdk.services.organizations.OrganizationsClient;
import software.amazon.awssdk.services.organizations.model.DescribeResourcePolicyRequest;
import software.amazon.awssdk.services.organizations.model.DescribeResourcePolicyResponse;
import software.amazon.awssdk.services.organizations.model.ListTagsForResourceRequest;
import software.amazon.awssdk.services.organizations.model.ListTagsForResourceResponse;
import software.amazon.cloudformation.exceptions.CfnHandlerInternalFailureException;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.HandlerErrorCode;
import software.amazon.cloudformation.proxy.Logger;
import software.amazon.cloudformation.proxy.ProgressEvent;
import software.amazon.cloudformation.proxy.ProxyClient;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;

public class ReadHandler extends BaseHandlerStd {
    private Logger log;

    protected ProgressEvent<ResourceModel, CallbackContext> handleRequest(
        final AmazonWebServicesClientProxy awsClientProxy,
        final ResourceHandlerRequest<ResourceModel> request,
        final CallbackContext callbackContext,
        final ProxyClient<OrganizationsClient> orgsClient,
        final Logger logger) {

        this.log = logger;
        final ResourceModel model = request.getDesiredResourceState();

        // Call DescribeResourcePolicy API
        logger.log(String.format("Requesting DescribeResourcePolicy with management account Id [%s] and resourcePolicy Id [%s].", request.getAwsAccountId(), model.getId()));
        return ProgressEvent.progress(model, callbackContext)
            .then(progress ->
                awsClientProxy.initiate("AWS-Organizations-ResourcePolicy::Read::DescribeResourcePolicy", orgsClient, progress.getResourceModel(), progress.getCallbackContext())
                .translateToServiceRequest(Translator::translateToReadRequest)
                .makeServiceCall(this::describeResourcePolicy)
                .handleError((organizationsRequest, e, orgsClient1, model1, context) ->
                                 handleErrorInGeneral(organizationsRequest, e, orgsClient1, model1, context, logger, ResourcePolicyConstants.Action.DESCRIBE_RESOURCEPOLICY, ResourcePolicyConstants.Handler.READ))
                .done(describeResourcePolicyResponse -> {
                    try {
                        model.setContent(Translator.convertStringToObject(describeResourcePolicyResponse.resourcePolicy().content(), logger));
                    } catch (CfnHandlerInternalFailureException e) {
                        return ProgressEvent.failed(model, callbackContext, HandlerErrorCode.InternalFailure,
                                    "Internal handler failure");
                    }
                    model.setId(describeResourcePolicyResponse.resourcePolicy().resourcePolicySummary().id());
                    model.setArn(describeResourcePolicyResponse.resourcePolicy().resourcePolicySummary().arn());
                    return ProgressEvent.progress(model, callbackContext);
                })
            )
            .then(progress -> listTagsForResourcePolicy(awsClientProxy, request, model, callbackContext, orgsClient, logger));
    }

    protected ProgressEvent<ResourceModel, CallbackContext> listTagsForResourcePolicy(
        final AmazonWebServicesClientProxy awsClientProxy,
        final ResourceHandlerRequest<ResourceModel> request,
        final ResourceModel model,
        final CallbackContext callbackContext,
        final ProxyClient<OrganizationsClient> orgsClient,
        final Logger logger
        ) {

        String resourcePolicyId = model.getId();
        logger.log(String.format("Listing tags for resourcePolicyId: %s.", resourcePolicyId));

        // ListTags currently returns all (max: 50) tags in a single call, so no need for pagination handling
        return awsClientProxy.initiate("AWS-Organizations-ResourcePolicy::ListTagsForResource", orgsClient, model, callbackContext)
            .translateToServiceRequest(resourceModel -> Translator.translateToListTagsForResourceRequest(resourcePolicyId))
            .makeServiceCall(this::listTagsForResource)
            .handleError((organizationsRequest, e, orgsClient1, model1, context) ->
                             handleErrorInGeneral(organizationsRequest, e, orgsClient1, model1, context, logger, ResourcePolicyConstants.Action.LIST_TAGS_FOR_RESOURCEPOLICY, ResourcePolicyConstants.Handler.READ))
            .done(listTagsForResourceResponse -> {
                model.setTags(Translator.translateTagsFromSdkResponse(listTagsForResourceResponse.tags()));
                return ProgressEvent.defaultSuccessHandler(model);
            });
    }

    protected DescribeResourcePolicyResponse describeResourcePolicy(final DescribeResourcePolicyRequest describeResourcePolicyRequest, final ProxyClient<OrganizationsClient> orgsClient) {
        log.log("Calling describeResourcePolicy API.");
        return orgsClient.injectCredentialsAndInvokeV2(describeResourcePolicyRequest, orgsClient.client()::describeResourcePolicy);
    }

    // DescribeResourcePolicy call doesn't return tags on ResourcePolicy so ListTags call needs to be made separately
    protected ListTagsForResourceResponse listTagsForResource(final ListTagsForResourceRequest listTagsForResourceRequest, final ProxyClient<OrganizationsClient> orgsClient) {
        log.log(String.format("Calling listTagsForResource API for resource [%s].", listTagsForResourceRequest.resourceId()));
        return orgsClient.injectCredentialsAndInvokeV2(listTagsForResourceRequest, orgsClient.client()::listTagsForResource);
    }
}
