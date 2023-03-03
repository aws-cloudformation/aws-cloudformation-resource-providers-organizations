package software.amazon.organizations.resourcepolicy;

import software.amazon.awssdk.services.organizations.OrganizationsClient;
import software.amazon.awssdk.services.organizations.model.DescribeResourcePolicyRequest;
import software.amazon.awssdk.services.organizations.model.DescribeResourcePolicyResponse;
import software.amazon.awssdk.services.organizations.model.PutResourcePolicyRequest;
import software.amazon.awssdk.services.organizations.model.PutResourcePolicyResponse;
import software.amazon.awssdk.services.organizations.model.ResourcePolicyNotFoundException;
import software.amazon.cloudformation.exceptions.CfnInvalidRequestException;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.HandlerErrorCode;
import software.amazon.cloudformation.proxy.ProgressEvent;
import software.amazon.cloudformation.proxy.ProxyClient;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;
import software.amazon.organizations.utils.OrgsLoggerWrapper;

public class CreateHandler extends BaseHandlerStd {
    private OrgsLoggerWrapper log;

    public ProgressEvent<ResourceModel, CallbackContext> handleRequest(
        final AmazonWebServicesClientProxy awsClientProxy,
        final ResourceHandlerRequest<ResourceModel> request,
        final CallbackContext callbackContext,
        final ProxyClient<OrganizationsClient> orgsClient,
        final OrgsLoggerWrapper logger) {

        this.log = logger;
        final ResourceModel model = request.getDesiredResourceState();

        if (model.getContent() == null) {
            logger.log(String.format("The ResourcePolicy create model did not include Content. This is an InvalidRequest for management account Id [%s]", request.getAwsAccountId()));
            return ProgressEvent.failed(model, callbackContext, HandlerErrorCode.InvalidRequest,
                "ResourcePolicy cannot be created without content!");
        }

        String content;
        try {
            content = Translator.convertObjectToString(model.getContent());
        } catch (CfnInvalidRequestException e){
            logger.log(String.format("The ResourcePolicy content did not include a valid JSON. This is an InvalidRequest for management account Id [%s]", request.getAwsAccountId()));
            return ProgressEvent.failed(model, callbackContext, HandlerErrorCode.InvalidRequest,
                "ResourcePolicy content had invalid JSON!");
        }

        logger.log(String.format("Requesting PutResourcePolicy w/ content: %s and management account Id [%s]", content, request.getAwsAccountId()));
        return ProgressEvent.progress(model, callbackContext)
            .then(progress -> describeResourcePolicyProgressEvent(awsClientProxy, request, model, callbackContext, orgsClient, logger))
            .then(progress ->
                awsClientProxy.initiate("AWS-Organizations-ResourcePolicy::CreateResourcePolicy", orgsClient, progress.getResourceModel(), progress.getCallbackContext())
                    .translateToServiceRequest(Translator::translateToCreateRequest)
                    .makeServiceCall(this::putResourcePolicy)
                    .handleError((organizationsRequest, e, proxyClient1, model1, context) ->
                                    handleErrorInGeneral(organizationsRequest, e, proxyClient1, model1, context, logger, ResourcePolicyConstants.Action.CREATE_RESOURCEPOLICY, ResourcePolicyConstants.Handler.CREATE))
                    .done(putResourcePolicyResponse -> {
                        logger.log(String.format("Created resourcePolicy with Id: [%s].", putResourcePolicyResponse.resourcePolicy().resourcePolicySummary().id()));
                        model.setId(putResourcePolicyResponse.resourcePolicy().resourcePolicySummary().id());
                        return ProgressEvent.progress(model, callbackContext);
                    })
            )
            .then(progress -> new ReadHandler().handleRequest(awsClientProxy, request, callbackContext, orgsClient, logger));
    }


    protected ProgressEvent<ResourceModel, CallbackContext> describeResourcePolicyProgressEvent(
        final AmazonWebServicesClientProxy awsClientProxy,
        final ResourceHandlerRequest<ResourceModel> request,
        final ResourceModel model,
        final CallbackContext callbackContext,
        final ProxyClient<OrganizationsClient> orgsClient,
        final OrgsLoggerWrapper logger
        ) {

        logger.log(String.format("Running describeResourcePolicyProgressEvent to ensure there is not an already existing resourcePolicy in the Organization w/management account Id [%s].", request.getAwsAccountId()));

        return ProgressEvent.progress(model, callbackContext)
            .then(progress ->
                awsClientProxy.initiate("AWS-Organizations-ResourcePolicy::Create::DescribeResourcePolicy", orgsClient, progress.getResourceModel(), progress.getCallbackContext())
                .translateToServiceRequest(Translator::translateToReadRequest)
                .makeServiceCall(this::describeResourcePolicy)
                .handleError((organizationsRequest, e, orgsClient1, model1, context) -> {
                    if (e instanceof ResourcePolicyNotFoundException) {
                        return ProgressEvent.progress(model1,context);
                    } else {
                        return handleErrorInGeneral(organizationsRequest, e, orgsClient1, model1, context, logger, ResourcePolicyConstants.Action.DESCRIBE_RESOURCEPOLICY, ResourcePolicyConstants.Handler.CREATE);
                    }
                })
                .done(describeResourcePolicyResponse -> {
                    if (describeResourcePolicyResponse != null) {
                        logger.log(String.format("Create resourcePolicy w/management account Id [%s] failed due to an already existing resourcePolicy in the Organization.", request.getAwsAccountId()));
                        return ProgressEvent.failed(model, callbackContext, HandlerErrorCode.AlreadyExists,
                            "There is already a ResourcePolicy in the Organization!");
                    }
                    return ProgressEvent.progress(model, callbackContext);
                })
            );
    }

    protected DescribeResourcePolicyResponse describeResourcePolicy(final DescribeResourcePolicyRequest describeResourcePolicyRequest, final ProxyClient<OrganizationsClient> orgsClient) {
        log.log("Calling describeResourcePolicy API.");
        return orgsClient.injectCredentialsAndInvokeV2(describeResourcePolicyRequest, orgsClient.client()::describeResourcePolicy);
    }

    protected PutResourcePolicyResponse putResourcePolicy(final PutResourcePolicyRequest putResourcePolicyRequest, final ProxyClient<OrganizationsClient> orgsClient) {
        log.log(String.format("Calling putResourcePolicy API for ResourcePolicy content [%s].", putResourcePolicyRequest.content()));
        return orgsClient.injectCredentialsAndInvokeV2(putResourcePolicyRequest, orgsClient.client()::putResourcePolicy);
    }
}
