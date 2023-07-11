package software.amazon.organizations.organization;

import software.amazon.awssdk.services.organizations.OrganizationsClient;
import software.amazon.awssdk.services.organizations.model.DeleteOrganizationRequest;
import software.amazon.awssdk.services.organizations.model.DeleteOrganizationResponse;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.OperationStatus;
import software.amazon.cloudformation.proxy.ProgressEvent;
import software.amazon.cloudformation.proxy.ProxyClient;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;
import software.amazon.organizations.utils.OrgsLoggerWrapper;


public class DeleteHandler extends BaseHandlerStd {

    private OrgsLoggerWrapper log;

    protected ProgressEvent<ResourceModel, CallbackContext> handleRequest(
            final AmazonWebServicesClientProxy awsClientProxy,
            final ResourceHandlerRequest<ResourceModel> request,
            final CallbackContext callbackContext,
            final ProxyClient<OrganizationsClient> orgsClient,
            final OrgsLoggerWrapper logger) {

        this.log = logger;
        final ResourceModel model = request.getDesiredResourceState();
        logger.log(String.format("Entered %s delete handler with organization Id: [%s]", ResourceModel.TYPE_NAME, model.getId()));

        return ProgressEvent.progress(model, callbackContext)
                .then(progress ->
                        awsClientProxy.initiate("AWS-Organizations-Organization::DeleteOrganization", orgsClient, model, progress.getCallbackContext())
                                .translateToServiceRequest(t -> Translator.translateToDeleteRequest())
                                .makeServiceCall(this::deleteOrganization)
                                .handleError((organizationsRequest, e, proxyClient1, model1, context) -> handleErrorInGeneral(
                                        organizationsRequest, e, request, proxyClient1, model1, context, logger, OrganizationConstants.Action.DELETE_ORG, OrganizationConstants.Handler.DELETE))
                                .done((deleteRequest) -> ProgressEvent.<ResourceModel, CallbackContext>builder().status(OperationStatus.SUCCESS).build()));
    }

    protected DeleteOrganizationResponse deleteOrganization(final DeleteOrganizationRequest deleteOrganizationRequest, final ProxyClient<OrganizationsClient> orgsClient) {
        log.log(String.format("Attempt to delete organization."));
        final DeleteOrganizationResponse deleteOrganizationResponse = orgsClient.injectCredentialsAndInvokeV2(deleteOrganizationRequest, orgsClient.client()::deleteOrganization);
        return deleteOrganizationResponse;
    }
}
