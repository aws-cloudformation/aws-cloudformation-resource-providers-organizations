package software.amazon.organizations.organization;

import software.amazon.awssdk.services.organizations.OrganizationsClient;
import software.amazon.awssdk.services.organizations.model.AwsOrganizationsNotInUseException;
import software.amazon.awssdk.services.organizations.model.DescribeOrganizationRequest;
import software.amazon.awssdk.services.organizations.model.DescribeOrganizationResponse;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;
import software.amazon.cloudformation.proxy.ProgressEvent;
import software.amazon.cloudformation.proxy.OperationStatus;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.HandlerErrorCode;
import software.amazon.cloudformation.proxy.ProxyClient;
import software.amazon.organizations.utils.OrgsLoggerWrapper;

import java.util.ArrayList;
import java.util.List;

public class ListHandler extends BaseHandlerStd {

    private OrgsLoggerWrapper log;

    protected ProgressEvent<ResourceModel, CallbackContext> handleRequest(
            final AmazonWebServicesClientProxy awsClientProxy,
            final ResourceHandlerRequest<ResourceModel> request,
            final CallbackContext callbackContext,
            final ProxyClient<OrganizationsClient> orgsClient,
            final OrgsLoggerWrapper logger) {

        this.log = logger;
        logger.log(String.format("Entered %s list handler with account Id [%s].", ResourceModel.TYPE_NAME, request.getAwsAccountId()));

        final ResourceModel model = request.getDesiredResourceState();
        if (model == null) {
            return ProgressEvent.failed(ResourceModel.builder().build(), callbackContext, HandlerErrorCode.InvalidRequest,
                    "Organization cannot be empty!");
        }

        final List<ResourceModel> models = new ArrayList<>();
        String nextToken = null;

        return awsClientProxy.initiate("AWS-Organizations-Organization::Read::ListOrganization", orgsClient, model, callbackContext)
                .translateToServiceRequest(t -> Translator.translateToReadRequest())
                .makeServiceCall(this::describeOrganization)
                .handleError((organizationsRequest, e, proxyClient1, model1, context) -> {
                    if (e instanceof AwsOrganizationsNotInUseException) {
                        logger.log(String.format("Caught AwsOrganizationsNotInUseException for accountId [%s], continue to return model with null objects.", request.getAwsAccountId()));

                        return ProgressEvent.<ResourceModel, CallbackContext>builder()
                                .resourceModels(models)
                                .nextToken(nextToken)
                                .status(OperationStatus.SUCCESS)
                                .build();
                    } else {
                        return handleErrorInGeneral(organizationsRequest, e, request, orgsClient, model1, context, logger, OrganizationConstants.Action.DESCRIBE_ORG, OrganizationConstants.Handler.LIST);
                    }
                })
                .done(describeOrganizationResponse -> ProgressEvent.<ResourceModel, CallbackContext>builder()
                        .resourceModels(Translator.translatetoListReadResponse(describeOrganizationResponse, model, models))
                        .nextToken(nextToken)
                        .status(OperationStatus.SUCCESS)
                        .build());
    }

    protected DescribeOrganizationResponse describeOrganization(final DescribeOrganizationRequest describeOrganizationRequest, final ProxyClient<OrganizationsClient> orgsClient) {
        log.log(String.format("Retrieving organization details."));
        final DescribeOrganizationResponse response = orgsClient.injectCredentialsAndInvokeV2(describeOrganizationRequest, orgsClient.client()::describeOrganization);
        return response;
    }
}
