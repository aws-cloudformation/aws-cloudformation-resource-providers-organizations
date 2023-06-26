package software.amazon.organizations.organization;

import software.amazon.awssdk.services.organizations.OrganizationsClient;
import software.amazon.awssdk.services.organizations.model.CreateOrganizationResponse;
import software.amazon.awssdk.services.organizations.model.CreateOrganizationRequest;

import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.ProgressEvent;
import software.amazon.cloudformation.proxy.ProxyClient;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;
import software.amazon.organizations.utils.OrgsLoggerWrapper;

public class CreateHandler extends BaseHandlerStd {

    private OrgsLoggerWrapper log;

    protected ProgressEvent<ResourceModel, CallbackContext> handleRequest(
            final AmazonWebServicesClientProxy awsClientProxy,
            final ResourceHandlerRequest<ResourceModel> request,
            final CallbackContext callbackContext,
            final ProxyClient<OrganizationsClient> orgsClient,
            final OrgsLoggerWrapper logger) {

        this.log = logger;
        final ResourceModel model = request.getDesiredResourceState();
        if (request.getDesiredResourceState().getFeatureSet() == null) {
            model.setFeatureSet("ALL");
        }
        logger.log(String.format("Entered %s create handler with account Id [%s] and feature set: [%s]", ResourceModel.TYPE_NAME, request.getAwsAccountId(), model.getFeatureSet()));

        return ProgressEvent.progress(model, callbackContext)
                .then(progress ->
                        awsClientProxy.initiate("AWS-Organizations-Organization::CreateOrganization", orgsClient, progress.getResourceModel(), progress.getCallbackContext())
                                .translateToServiceRequest(Translator::translateToCreateRequest)
                                .makeServiceCall(this::createOrganization)
                                .handleError((organizationsRequest, e, proxyClient1, model1, context) -> handleErrorInGeneral(
                                        organizationsRequest, e, request, proxyClient1, model1, context, logger, OrganizationConstants.Action.CREATE_ORG, OrganizationConstants.Handler.CREATE))

                                .progress()
                )
                .then(progress -> new ReadHandler().handleRequest(awsClientProxy, request, callbackContext, orgsClient, logger));
    }

    protected CreateOrganizationResponse createOrganization(final CreateOrganizationRequest createOrganizationRequest, final ProxyClient<OrganizationsClient> orgsClient) {
        log.log(String.format("Start creating organization."));
        final CreateOrganizationResponse createOrganizationResponse = orgsClient.injectCredentialsAndInvokeV2(createOrganizationRequest, orgsClient.client()::createOrganization);
        return createOrganizationResponse;
    }
}
