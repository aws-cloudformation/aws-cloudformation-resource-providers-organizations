package software.amazon.organizations.organization;

import software.amazon.awssdk.services.organizations.OrganizationsClient;
import software.amazon.awssdk.services.organizations.model.DeleteOrganizationRequest;
import software.amazon.awssdk.services.organizations.model.DeleteOrganizationResponse;
import software.amazon.awssdk.services.organizations.model.AwsOrganizationsNotInUseException;

import software.amazon.cloudformation.exceptions.CfnNotFoundException;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.Logger;
import software.amazon.cloudformation.proxy.OperationStatus;
import software.amazon.cloudformation.proxy.ProgressEvent;
import software.amazon.cloudformation.proxy.ProxyClient;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;

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

        return ProgressEvent.progress(model, callbackContext)
            .then(progress -> awsClientProxy.initiate("AWS-Organizations-Organization::Delete", orgsClient, model, progress.getCallbackContext())
                    .translateToServiceRequest(t -> Translator.translateToDeleteRequest())
                    .makeServiceCall(this::deleteOrganization)
                    .done((deleteRequest) -> ProgressEvent.<ResourceModel, CallbackContext>builder().status(OperationStatus.SUCCESS).build()));
    }

    protected DeleteOrganizationResponse deleteOrganization(final DeleteOrganizationRequest deleteOrganizationRequest, final ProxyClient<OrganizationsClient> orgsClient) {
        try {
            final DeleteOrganizationResponse deleteOrganizationResponse = orgsClient.injectCredentialsAndInvokeV2(deleteOrganizationRequest, orgsClient.client()::deleteOrganization);
            logger.log(String.format("%s successfully deleted.", ResourceModel.TYPE_NAME));
            return deleteOrganizationResponse;
        } catch(AwsOrganizationsNotInUseException e) {
            throw new CfnNotFoundException(ResourceModel.TYPE_NAME, ResourceModel.IDENTIFIER_KEY_ID);
        }
    }
}
