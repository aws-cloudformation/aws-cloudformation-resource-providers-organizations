package software.amazon.organizations.policy;

import software.amazon.awssdk.services.organizations.OrganizationsClient;
import software.amazon.awssdk.services.organizations.model.*;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.Logger;
import software.amazon.cloudformation.proxy.ProgressEvent;
import software.amazon.cloudformation.proxy.ProxyClient;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;

import java.util.stream.Collectors;

public class ReadHandler extends BaseHandlerStd {
    private Logger logger;

    public ProgressEvent<ResourceModel, CallbackContext> handleRequest(
        final AmazonWebServicesClientProxy awsClientProxy,
        final ResourceHandlerRequest<ResourceModel> request,
        final CallbackContext callbackContext,
        final ProxyClient<OrganizationsClient> orgsClient,
        final Logger logger) {

        this.logger = logger;
        final ResourceModel model = request.getDesiredResourceState();
        logger.log(String.format("Entered %s read handler with account Id [%s], policy Id: [%s].", ResourceModel.TYPE_NAME, request.getAwsAccountId(), model.getId()));

        return ProgressEvent.progress(model, callbackContext)
            // Describe policy
            .then(progress -> awsClientProxy.initiate("AWS-Organizations-Policy::DescribePolicy", orgsClient, model, callbackContext)
                .translateToServiceRequest(t -> Translator.translateToReadRequest(model))
                .makeServiceCall(this::describePolicy)
                .handleError((organizationsRequest, e, proxyClient1, model1, context) -> handleError(
                    organizationsRequest, e, proxyClient1, model1, context, logger))
                .done(describePolicyResponse -> ProgressEvent.defaultSuccessHandler(Translator.translateFromReadResponse(describePolicyResponse, model)))
            );
    }

    protected DescribePolicyResponse describePolicy(final DescribePolicyRequest describePolicyRequest, final ProxyClient<OrganizationsClient> orgsClient) {
        logger.log(String.format("Retrieving policy details."));
        final DescribePolicyResponse response = orgsClient.injectCredentialsAndInvokeV2(describePolicyRequest, orgsClient.client()::describePolicy);
        return response;
    }
}
