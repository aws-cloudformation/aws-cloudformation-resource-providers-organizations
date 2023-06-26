package software.amazon.organizations.organization;

import software.amazon.awssdk.services.organizations.OrganizationsClient;
import software.amazon.awssdk.services.organizations.model.Root;
import software.amazon.awssdk.services.organizations.model.DescribeOrganizationRequest;
import software.amazon.awssdk.services.organizations.model.DescribeOrganizationResponse;
import software.amazon.awssdk.services.organizations.model.ListRootsRequest;
import software.amazon.awssdk.services.organizations.model.ListRootsResponse;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.ProgressEvent;
import software.amazon.cloudformation.proxy.ProxyClient;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;
import software.amazon.organizations.utils.OrgsLoggerWrapper;

import java.util.stream.Collectors;

public class ReadHandler extends BaseHandlerStd {
    private OrgsLoggerWrapper log;

    protected ProgressEvent<ResourceModel, CallbackContext> handleRequest(
            final AmazonWebServicesClientProxy awsClientProxy,
            final ResourceHandlerRequest<ResourceModel> request,
            final CallbackContext callbackContext,
            final ProxyClient<OrganizationsClient> orgsClient,
            final OrgsLoggerWrapper logger) {

        this.log = logger;
        logger.log(String.format("Entered %s read handler for Organization resource type with account Id [%s].", ResourceModel.TYPE_NAME, request.getAwsAccountId()));

        final ResourceModel model = request.getDesiredResourceState();
        return ProgressEvent.progress(model, callbackContext)
                .then(progress -> awsClientProxy.initiate("AWS-Organizations-Organization::Read::GetRootId", orgsClient, model, callbackContext)
                        .translateToServiceRequest(t -> Translator.translateToListRootsRequest())
                        .makeServiceCall(this::listRoots)
                        .handleError((organizationsRequest, e, proxyClient1, model1, context) -> handleErrorInGeneral(
                                organizationsRequest, e, request, proxyClient1, model1, context, logger, OrganizationConstants.Action.GETROOT_ID, OrganizationConstants.Handler.READ))
                        .done(listRootsResponse -> {
                            model.setRootId(listRootsResponse.roots().stream().map(Root::id).collect(Collectors.toList()).get(0));
                            return ProgressEvent.progress(model, callbackContext);
                        })
                )
                .then(progress -> awsClientProxy.initiate("AWS-Organizations-Organization::Read::DescribeOrganization", orgsClient, model, callbackContext)
                        .translateToServiceRequest(t -> Translator.translateToReadRequest())
                        .makeServiceCall(this::describeOrganization)
                        .handleError((organizationsRequest, e, proxyClient1, model1, context) -> handleErrorInGeneral(
                                organizationsRequest, e, request, proxyClient1, model1, context, logger, OrganizationConstants.Action.DESCRIBE_ORG, OrganizationConstants.Handler.READ))
                        .done(describeOrganizationResponse -> ProgressEvent.defaultSuccessHandler(Translator.translateFromReadResponse(describeOrganizationResponse, model)))
                );
    }

    protected DescribeOrganizationResponse describeOrganization(final DescribeOrganizationRequest describeOrganizationRequest, final ProxyClient<OrganizationsClient> orgsClient) {
        log.log(String.format("Retrieving organization details."));
        final DescribeOrganizationResponse response = orgsClient.injectCredentialsAndInvokeV2(describeOrganizationRequest, orgsClient.client()::describeOrganization);
        return response;
    }

    protected ListRootsResponse listRoots(final ListRootsRequest listRootsRequest, final ProxyClient<OrganizationsClient> orgsClient) {
        log.log(String.format("Retrieving root id."));
        final ListRootsResponse response = orgsClient.injectCredentialsAndInvokeV2(listRootsRequest, orgsClient.client()::listRoots);
        return response;
    }
}
