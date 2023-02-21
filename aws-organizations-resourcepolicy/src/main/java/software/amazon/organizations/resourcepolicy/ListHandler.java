package software.amazon.organizations.resourcepolicy;

import software.amazon.awssdk.services.organizations.OrganizationsClient;
import software.amazon.awssdk.services.organizations.model.DescribeResourcePolicyRequest;
import software.amazon.awssdk.services.organizations.model.DescribeResourcePolicyResponse;
import software.amazon.awssdk.services.organizations.model.ResourcePolicyNotFoundException;
import software.amazon.cloudformation.exceptions.CfnHandlerInternalFailureException;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.HandlerErrorCode;
import software.amazon.cloudformation.proxy.Logger;
import software.amazon.cloudformation.proxy.ProgressEvent;
import software.amazon.cloudformation.proxy.OperationStatus;
import software.amazon.cloudformation.proxy.ProxyClient;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;

import java.util.ArrayList;
import java.util.List;

public class ListHandler extends BaseHandlerStd {
    private Logger log;

    public ProgressEvent<ResourceModel, CallbackContext> handleRequest(
        final AmazonWebServicesClientProxy awsClientProxy,
        final ResourceHandlerRequest<ResourceModel> request,
        final CallbackContext callbackContext,
        final ProxyClient<OrganizationsClient> orgsClient,
        final Logger logger) {

        this.log = logger;
        logger.log(String.format("Entered %s list handler with accountId [%s]", ResourceModel.TYPE_NAME, request.getAwsAccountId()));

        final ResourceModel model = request.getDesiredResourceState();
        if (model == null) {
            return ProgressEvent.failed(ResourceModel.builder().build(), callbackContext, HandlerErrorCode.InvalidRequest,
                    "ResourcePolicies cannot be empty!");
        }

        final List<ResourceModel> models = new ArrayList<>();
        String nextToken = null; // DescribeResourcePolicy API - no nextToken generated

        logger.log(String.format("Requesting DescribeResourcePolicy with management account Id [%s] and resourcePolicy Id [%s].", request.getAwsAccountId(), model.getId()));
        return awsClientProxy.initiate("AWS-Organizations-ResourcePolicy::Read::ListResourcePolicy", orgsClient, model, callbackContext)
            .translateToServiceRequest(Translator::translateToReadRequest)
            .makeServiceCall(this::describeResourcePolicy)
            .handleError((organizationsRequest, e, orgsClient1, model1, context) -> {
                if (e instanceof ResourcePolicyNotFoundException) {
                    return ProgressEvent.<ResourceModel, CallbackContext>builder()
                        .resourceModels(models)
                        .nextToken(nextToken)
                        .status(OperationStatus.SUCCESS)
                        .build();
                } else {
                    return handleErrorInGeneral(organizationsRequest, e, orgsClient1, model1, context, logger, ResourcePolicyConstants.Action.DESCRIBE_RESOURCEPOLICY, ResourcePolicyConstants.Handler.LIST);
                }
            })
            .done(describeResourcePolicyResponse -> {
                try {
                    model.setContent(Translator.convertStringToObject(describeResourcePolicyResponse.resourcePolicy().content(), logger));
                } catch (CfnHandlerInternalFailureException e) {
                    return ProgressEvent.failed(model, callbackContext, HandlerErrorCode.InternalFailure,
                                "Internal handler failure");
                }
                model.setId(describeResourcePolicyResponse.resourcePolicy().resourcePolicySummary().id());
                model.setArn(describeResourcePolicyResponse.resourcePolicy().resourcePolicySummary().arn());
                models.add(model);
                return ProgressEvent.<ResourceModel, CallbackContext>builder()
                    .resourceModels(models)
                    .nextToken(nextToken)
                    .status(OperationStatus.SUCCESS)
                    .build();
        });
    }

    protected DescribeResourcePolicyResponse describeResourcePolicy(final DescribeResourcePolicyRequest describeResourcePolicyRequest, final ProxyClient<OrganizationsClient> orgsClient) {
        log.log("Calling describeResourcePolicy API.");
        return orgsClient.injectCredentialsAndInvokeV2(describeResourcePolicyRequest, orgsClient.client()::describeResourcePolicy);
    }
}
