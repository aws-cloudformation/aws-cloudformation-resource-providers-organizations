package software.amazon.organizations.organization;

import software.amazon.awssdk.services.organizations.OrganizationsClient;
import software.amazon.awssdk.services.organizations.model.AlreadyInOrganizationException;
import software.amazon.awssdk.services.organizations.model.CreateOrganizationResponse;
import software.amazon.awssdk.services.organizations.model.CreateOrganizationRequest;

import software.amazon.cloudformation.exceptions.CfnAlreadyExistsException;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.Logger;
import software.amazon.cloudformation.proxy.ProgressEvent;
import software.amazon.cloudformation.proxy.ProxyClient;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;

public class CreateHandler extends BaseHandlerStd {
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
            .then(progress -> awsClientProxy.initiate("AWS-Organizations-Organization::Create", orgsClient, progress.getResourceModel(), progress.getCallbackContext())
                .translateToServiceRequest(Translator::translateToCreateRequest)
                .makeServiceCall(this::createOrganization)
                .progress()
            )
            .then(progress -> new ReadHandler().handleRequest(awsClientProxy, request, callbackContext, orgsClient, logger));
    }

    protected CreateOrganizationResponse createOrganization(final CreateOrganizationRequest createOrganizationRequest, final ProxyClient<OrganizationsClient> orgsClient) {
        try {
            final CreateOrganizationResponse createOrganizationResponse = orgsClient.injectCredentialsAndInvokeV2(createOrganizationRequest, orgsClient.client()::createOrganization);
            logger.log(String.format("%s successfully created.", ResourceModel.TYPE_NAME));
            return createOrganizationResponse;
        } catch(AlreadyInOrganizationException e) {
            throw new CfnAlreadyExistsException(ResourceModel.TYPE_NAME, ResourceModel.IDENTIFIER_KEY_ID);
        }
    }
}
