package software.amazon.organizations.policy;

import org.apache.commons.collections4.CollectionUtils;
import software.amazon.awssdk.services.organizations.OrganizationsClient;
import software.amazon.awssdk.services.organizations.model.DeletePolicyRequest;
import software.amazon.awssdk.services.organizations.model.DeletePolicyResponse;
import software.amazon.awssdk.services.organizations.model.DetachPolicyRequest;
import software.amazon.awssdk.services.organizations.model.PolicyNotAttachedException;
import software.amazon.awssdk.services.organizations.model.TargetNotFoundException;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.Logger;
import software.amazon.cloudformation.proxy.OperationStatus;
import software.amazon.cloudformation.proxy.ProgressEvent;
import software.amazon.cloudformation.proxy.ProxyClient;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;

import java.util.Set;

public class DeleteHandler extends BaseHandlerStd {
    private Logger log;

    protected ProgressEvent<ResourceModel, CallbackContext> handleRequest(
        final AmazonWebServicesClientProxy awsClientProxy,
        final ResourceHandlerRequest<ResourceModel> request,
        final CallbackContext callbackContext,
        final ProxyClient<OrganizationsClient> orgsClient,
        final Logger logger) {

        this.log = logger;
        final ResourceModel model = request.getDesiredResourceState();
        logger.log(String.format("Entered %s delete handler with policy Id: [%s], policy name: [%s].", ResourceModel.TYPE_NAME, model.getId(), model.getName()));

        return ProgressEvent.progress(model, callbackContext)
            .then(progress -> detachPolicyFromTargets(awsClientProxy, request, model, callbackContext, orgsClient, logger))
            .then(progress -> deletePolicyProcess(awsClientProxy, request, model, callbackContext, orgsClient, logger));
    }

    protected DeletePolicyResponse deletePolicy(final DeletePolicyRequest deletePolicyRequest, final ProxyClient<OrganizationsClient> orgsClient) {
        log.log(String.format("Attempt to delete policy for [%s].", deletePolicyRequest.policyId()));
        final DeletePolicyResponse deletePolicyResponse = orgsClient.injectCredentialsAndInvokeV2(deletePolicyRequest, orgsClient.client()::deletePolicy);
        return deletePolicyResponse;
    }

    protected ProgressEvent<ResourceModel, CallbackContext> deletePolicyProcess(
        final AmazonWebServicesClientProxy awsClientProxy,
        final ResourceHandlerRequest<ResourceModel> request,
        final ResourceModel model,
        final CallbackContext callbackContext,
        final ProxyClient<OrganizationsClient> orgsClient,
        final Logger logger
    ){
        final ProgressEvent<ResourceModel, CallbackContext> progressEvent = awsClientProxy
            .initiate("AWS-Organizations-Policy::DeletePolicy", orgsClient, model, callbackContext)
            .translateToServiceRequest((resourceModel) -> Translator.translateToDeleteRequest(model))
            .makeServiceCall(this::deletePolicy)
            .handleError((organizationsRequest, e, proxyClient1, model1, context) -> {
                // retry on exceptions that map to CloudFormation retriable exceptions
                // reference: https://docs.aws.amazon.com/cloudformation-cli/latest/userguide/resource-type-test-contract-errors.html
                if (isRetriableException(e)) {
                    return handleRetriableException(organizationsRequest, proxyClient1, context, logger, e, model1, PolicyConstants.Action.DELETE_POLICY);
                } else {
                    return handleError(organizationsRequest, e, proxyClient1, model1, context, logger);
                }
            })
            .success();

        if (!progressEvent.isSuccess()) { return progressEvent; }

        return ProgressEvent.<ResourceModel, CallbackContext>builder().status(OperationStatus.SUCCESS).build();
    }

    protected ProgressEvent<ResourceModel, CallbackContext> detachPolicyFromTargets(
        final AmazonWebServicesClientProxy awsClientProxy,
        final ResourceHandlerRequest<ResourceModel> request,
        final ResourceModel model,
        final CallbackContext callbackContext,
        final ProxyClient<OrganizationsClient> orgsClient,
        final Logger logger) {

        Set<String> targets = model.getTargetIds();
        String policyName = model.getName();
        if (CollectionUtils.isEmpty(targets)) {
            logger.log(String.format("No target id found in request. Skip detaching policy for [%s].", policyName));
            return ProgressEvent.progress(model, callbackContext);
        }
        if (callbackContext.isPolicyDetached()){
            logger.log(String.format("All policy detached from previous invoke. Skip to delete policy for policy [%s].", policyName));
            return ProgressEvent.progress(model, callbackContext);
        }
        logger.log(String.format("Target Ids found in request for policy [%s]. Start detaching policy to provided targets.", policyName));
        for (final String targetId : targets) {
            logger.log(String.format("Start detaching policy from targetId [%s] for policy [%s].", targetId, policyName));
            DetachPolicyRequest detachPolicyRequest = Translator.translateToDetachRequest(model.getId(), targetId);
            try {
                awsClientProxy.injectCredentialsAndInvokeV2(detachPolicyRequest, orgsClient.client()::detachPolicy);
            } catch (Exception e) {
                if (e instanceof PolicyNotAttachedException || e instanceof TargetNotFoundException) {
                    logger.log(String.format("Got %s when calling detachPolicy for "
                        + "policyId [%s], targetId [%s]. Continuing with delete...",
                        e.getClass().getName(), model.getId(), targetId));
                } else if (isRetriableException(e)) {
                    return handleRetriableException(detachPolicyRequest, orgsClient, callbackContext, logger, e, model, PolicyConstants.Action.DETACH_POLICY);
                }
                else {
                    return handleError(detachPolicyRequest, e, orgsClient, model, callbackContext, logger);
                }
            }
        }
        callbackContext.setPolicyDetached(true);
        return ProgressEvent.progress(model, callbackContext);
    }
}
