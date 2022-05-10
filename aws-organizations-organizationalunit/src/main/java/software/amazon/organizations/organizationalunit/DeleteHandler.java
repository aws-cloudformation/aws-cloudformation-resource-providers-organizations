package software.amazon.organizations.organizationalunit;

import software.amazon.awssdk.services.organizations.OrganizationsClient;
import software.amazon.awssdk.services.organizations.model.DeleteOrganizationalUnitRequest;
import software.amazon.awssdk.services.organizations.model.DeleteOrganizationalUnitResponse;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.Logger;
import software.amazon.cloudformation.proxy.ProgressEvent;
import software.amazon.cloudformation.proxy.ProxyClient;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;

public class DeleteHandler extends BaseHandlerStd {
    private Logger log;

    public ProgressEvent<ResourceModel, CallbackContext> handleRequest(
        final AmazonWebServicesClientProxy awsClientProxy,
        final ResourceHandlerRequest<ResourceModel> request,
        final CallbackContext callbackContext,
        final ProxyClient<OrganizationsClient> orgsClient,
        final Logger logger) {

        this.log = logger;
        final ResourceModel model = request.getDesiredResourceState();

        String ouId = model.getId();

        // Call DeleteOrganizationalUnit API
        logger.log(String.format("Requesting DeleteOrganizationalUnit w/ id: %s.\n", ouId));
        return ProgressEvent.progress(model, callbackContext)
            .then(progress ->
                awsClientProxy.initiate("AWS-Organizations-OrganizationalUnit::DeleteOrganizationalUnit", orgsClient, progress.getResourceModel(), progress.getCallbackContext())
                .translateToServiceRequest(Translator::translateToDeleteOrganizationalUnitRequest)
                .makeServiceCall(this::deleteOrganizationalUnit)
                .handleError((organizationsRequest, e, orgsClient1, model1, context) -> handleError(
                    organizationsRequest, e, orgsClient1, model1, context, logger))
                .done((deleteRequest) ->  ProgressEvent.defaultSuccessHandler(null))
            );
    }

    protected DeleteOrganizationalUnitResponse deleteOrganizationalUnit(final DeleteOrganizationalUnitRequest deleteOrganizationalUnitRequest, final ProxyClient<OrganizationsClient> orgsClient) {
        log.log(String.format("Calling deleteOrganizationalUnit API for OU [%s].", deleteOrganizationalUnitRequest.organizationalUnitId()));
        final DeleteOrganizationalUnitResponse deleteOrganizationalUnitResponse = orgsClient.injectCredentialsAndInvokeV2(deleteOrganizationalUnitRequest, orgsClient.client()::deleteOrganizationalUnit);
        return deleteOrganizationalUnitResponse;
    }
}
