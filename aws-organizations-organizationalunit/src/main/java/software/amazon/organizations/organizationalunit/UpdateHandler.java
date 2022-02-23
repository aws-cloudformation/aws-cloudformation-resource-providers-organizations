package software.amazon.organizations.organizationalunit;

import software.amazon.awssdk.services.organizations.OrganizationsClient;
import software.amazon.awssdk.services.organizations.model.UpdateOrganizationalUnitRequest;
import software.amazon.awssdk.services.organizations.model.UpdateOrganizationalUnitResponse;
import software.amazon.awssdk.services.organizations.model.OrganizationalUnit;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.Logger;
import software.amazon.cloudformation.proxy.OperationStatus;
import software.amazon.cloudformation.proxy.ProgressEvent;
import software.amazon.cloudformation.proxy.ProxyClient;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;

public class UpdateHandler extends BaseHandlerStd {
    private Logger logger;

    public ProgressEvent<ResourceModel, CallbackContext> handleRequest(
        final AmazonWebServicesClientProxy awsClientProxy,
        final ResourceHandlerRequest<ResourceModel> request,
        final CallbackContext callbackContext,
        final ProxyClient<OrganizationsClient> orgsClient,
        final Logger logger) {

        this.logger = logger;
        final ResourceModel model = request.getDesiredResourceState();

        String ouId = model.getId();
        String name = model.getName();

        // Call UpdateOrganizationalUnit API
        logger.log(String.format("Requesting UpdateOrganizationalUnit w/ id: %s and new name: %s.\n", ouId, name));
        return ProgressEvent.progress(model, callbackContext)
            .then(progress ->
                awsClientProxy.initiate("AWS-Organizations-OrganizationalUnit::UpdateOrganizationalUnit", orgsClient, progress.getResourceModel(), progress.getCallbackContext())
                .translateToServiceRequest(Translator::translateToUpdateOrganizationalUnitRequest)
                .makeServiceCall(this::updateOrganizationalUnit)
                .handleError((organizationsRequest, e, orgsClient1, model1, context) -> handleError(
                    organizationsRequest, e, orgsClient1, model1, context, logger))
                .done(updateOrganizationalUnitResponse -> ProgressEvent.defaultSuccessHandler(Translator.translateFromUpdateOrganizationalUnitResponse(updateOrganizationalUnitResponse, model)))
            );
    }

    protected UpdateOrganizationalUnitResponse updateOrganizationalUnit(final UpdateOrganizationalUnitRequest updateOrganizationalUnitRequest, final ProxyClient<OrganizationsClient> orgsClient) {
        logger.log(String.format("Calling updateOrganizationalUnit API."));
	    final UpdateOrganizationalUnitResponse updateOrganizationalUnitResponse = orgsClient.injectCredentialsAndInvokeV2(updateOrganizationalUnitRequest, orgsClient.client()::updateOrganizationalUnit);
	    return updateOrganizationalUnitResponse;
	}
}
