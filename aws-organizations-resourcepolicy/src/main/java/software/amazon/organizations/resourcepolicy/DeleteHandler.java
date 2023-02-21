package software.amazon.organizations.resourcepolicy;

import software.amazon.awssdk.services.organizations.OrganizationsClient;
import software.amazon.awssdk.services.organizations.model.DeleteResourcePolicyRequest;
import software.amazon.awssdk.services.organizations.model.DeleteResourcePolicyResponse;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.Logger;
import software.amazon.cloudformation.proxy.ProgressEvent;
import software.amazon.cloudformation.proxy.ProxyClient;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;

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
        String id = model.getId();

        logger.log(String.format("Requesting DeleteResourcePolicy w/ resourcePolicy Id : %s.\n", id));
        return ProgressEvent.progress(model, callbackContext)
                   .then(progress ->
                             awsClientProxy.initiate("AWS-Organizations-ResourcePolicy::DeleteResourcePolicy", orgsClient, progress.getResourceModel(), progress.getCallbackContext())
                                 .translateToServiceRequest(Translator::translateToDeleteRequest)
                                 .makeServiceCall(this::deleteResourcePolicy)
                                 .handleError((organizationsRequest, e, proxyClient1, model1, context) ->
                                                    handleErrorInGeneral(organizationsRequest, e, proxyClient1, model1, context, logger, ResourcePolicyConstants.Action.DELETE_RESOURCEPOLICY, ResourcePolicyConstants.Handler.DELETE))
                                 .done((deleteResourcePolicyResponse) -> ProgressEvent.defaultSuccessHandler(null))
                   );
    }

    protected DeleteResourcePolicyResponse deleteResourcePolicy(final DeleteResourcePolicyRequest deleteResourcePolicyRequest, final ProxyClient<OrganizationsClient> orgsClient) {
        log.log("Calling deleteResourcePolicy API.");
        return orgsClient.injectCredentialsAndInvokeV2(deleteResourcePolicyRequest, orgsClient.client()::deleteResourcePolicy);
    }
}
