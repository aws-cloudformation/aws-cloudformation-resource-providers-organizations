package software.amazon.organizations.policy;

import org.apache.commons.collections4.CollectionUtils;
import software.amazon.awssdk.services.organizations.OrganizationsClient;
import software.amazon.awssdk.services.organizations.model.DeletePolicyRequest;
import software.amazon.awssdk.services.organizations.model.DeletePolicyResponse;
import software.amazon.awssdk.services.organizations.model.DetachPolicyRequest;
import software.amazon.awssdk.services.organizations.model.DetachPolicyResponse;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.Logger;
import software.amazon.cloudformation.proxy.ProgressEvent;
import software.amazon.cloudformation.proxy.OperationStatus;
import software.amazon.cloudformation.proxy.ProxyClient;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;

import java.util.Set;

public class DeleteHandler extends BaseHandlerStd {
    private Logger logger;

    protected ProgressEvent<ResourceModel, CallbackContext> handleRequest(
        final AmazonWebServicesClientProxy awsClientProxy,
        final ResourceHandlerRequest<ResourceModel> request,
        final CallbackContext callbackContext,
        final ProxyClient<OrganizationsClient> orgsClient,
        final Logger logger) {

        this.logger = logger;
        final ResourceModel model = request.getDesiredResourceState();
        logger.log(String.format("Entered %s delete handler with policy Id: [%s]", ResourceModel.TYPE_NAME, model.getId()));

        return ProgressEvent.progress(model, callbackContext)
            .then(progress -> detachPolicyFromTargets(awsClientProxy, request, model, callbackContext, orgsClient, logger))
            .then(progress ->
                awsClientProxy.initiate("AWS-Organizations-Policy::DeletePolicy", orgsClient, model, progress.getCallbackContext())
                    .translateToServiceRequest(t -> Translator.translateToDeleteRequest(model))
                    .makeServiceCall(this::deletePolicy)
                    .handleError((organizationsRequest, e, proxyClient1, model1, context) -> handleError(
                        organizationsRequest, e, proxyClient1, model1, context, logger))
                    .done((deleteRequest) -> ProgressEvent.<ResourceModel, CallbackContext>builder().status(OperationStatus.SUCCESS).build()));

    }

    protected DeletePolicyResponse deletePolicy(final DeletePolicyRequest deletePolicyRequest, final ProxyClient<OrganizationsClient> orgsClient) {
        logger.log(String.format("Attempt to delete policy."));
        final DeletePolicyResponse deletePolicyResponse = orgsClient.injectCredentialsAndInvokeV2(deletePolicyRequest, orgsClient.client()::deletePolicy);
        return deletePolicyResponse;
    }

    protected DetachPolicyResponse detachPolicy(final DetachPolicyRequest detachPolicyRequest, final ProxyClient<OrganizationsClient> orgsClient) {
        logger.log(String.format("Start detaching policy."));
        final DetachPolicyResponse response = orgsClient.injectCredentialsAndInvokeV2(detachPolicyRequest, orgsClient.client()::detachPolicy);
        return response;
    }

    protected ProgressEvent<ResourceModel, CallbackContext> detachPolicyFromTargets(
        final AmazonWebServicesClientProxy awsClientProxy,
        final ResourceHandlerRequest<ResourceModel> request,
        final ResourceModel model,
        final CallbackContext callbackContext,
        final ProxyClient<OrganizationsClient> orgsClient,
        final Logger logger) {

        Set<String> targets = model.getTargetIds();
        if (CollectionUtils.isEmpty(targets)) {
            logger.log("No target id found in request. Skip detaching policy.\n");
            return ProgressEvent.progress(model, callbackContext);
        }
        logger.log("Target Ids found in request. Start detaching policy to provided targets.\n");
        targets.forEach(targetId -> {
            awsClientProxy.initiate("AWS-Organizations-Policy::DetachPolicy", orgsClient, model, callbackContext)
                .translateToServiceRequest(resourceModel -> Translator.translateToDetachRequest(model.getId(), targetId))
                .makeServiceCall(this::detachPolicy)
                .handleError((organizationsRequest, e, proxyClient1, model1, context) -> handleError(
                    organizationsRequest, e, proxyClient1, model1, context, logger))
                .done(CreatePolicyResponse -> ProgressEvent.progress(model, callbackContext));
        });
        return ProgressEvent.progress(model, callbackContext);
    }

}
