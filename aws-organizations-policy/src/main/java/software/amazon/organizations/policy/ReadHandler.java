package software.amazon.organizations.policy;

import software.amazon.awssdk.services.organizations.OrganizationsClient;
import software.amazon.awssdk.services.organizations.model.DescribePolicyRequest;
import software.amazon.awssdk.services.organizations.model.DescribePolicyResponse;
import software.amazon.awssdk.services.organizations.model.ListTagsForResourceRequest;
import software.amazon.awssdk.services.organizations.model.ListTagsForResourceResponse;
import software.amazon.awssdk.services.organizations.model.ListTargetsForPolicyRequest;
import software.amazon.awssdk.services.organizations.model.ListTargetsForPolicyResponse;
import software.amazon.awssdk.services.organizations.model.PolicyTargetSummary;
import software.amazon.cloudformation.exceptions.CfnHandlerInternalFailureException;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.HandlerErrorCode;
import software.amazon.cloudformation.proxy.Logger;
import software.amazon.cloudformation.proxy.OperationStatus;
import software.amazon.cloudformation.proxy.ProgressEvent;
import software.amazon.cloudformation.proxy.ProxyClient;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;

import java.util.HashSet;
import java.util.Set;

public class ReadHandler extends BaseHandlerStd {
    private Logger log;

    public ProgressEvent<ResourceModel, CallbackContext> handleRequest(
        final AmazonWebServicesClientProxy awsClientProxy,
        final ResourceHandlerRequest<ResourceModel> request,
        final CallbackContext callbackContext,
        final ProxyClient<OrganizationsClient> orgsClient,
        final Logger logger) {

        this.log = logger;
        final ResourceModel model = request.getDesiredResourceState();
        logger.log(String.format("Entered %s read handler with account Id [%s], policy Id: [%s].", ResourceModel.TYPE_NAME, request.getAwsAccountId(), model.getId()));

        return ProgressEvent.progress(model, callbackContext)
            .then(progress -> awsClientProxy.initiate("AWS-Organizations-Policy::DescribePolicy", orgsClient, model, callbackContext)
                .translateToServiceRequest(t -> Translator.translateToReadRequest(model))
                .makeServiceCall(this::describePolicy)
                .handleError((organizationsRequest, e, proxyClient1, model1, context) ->
                                 handleErrorInGeneral(organizationsRequest, e, proxyClient1, model1, context, logger, PolicyConstants.Action.DESCRIBE_POLICY, PolicyConstants.Handler.READ))
                .done(describePolicyResponse -> {
                    try {
                        model.setContent(Translator.convertStringToObject(describePolicyResponse.policy().content()));
                    } catch (CfnHandlerInternalFailureException e) {
                        String policyId = describePolicyResponse.policy().policySummary().id();
                        String errorMessage = String.format("[Exception] Failed with exception: [%s]. Message: [%s], ErrorCode: [%s] for policy [%s].",
                            e.getClass().getSimpleName(), e.getMessage(), HandlerErrorCode.InternalFailure, policyId);
                        logger.log(errorMessage);
                        return ProgressEvent.failed(model, callbackContext, HandlerErrorCode.InternalFailure, errorMessage);
                    }
                    model.setArn(describePolicyResponse.policy().policySummary().arn().toString());
                    model.setDescription(describePolicyResponse.policy().policySummary().description());
                    model.setId(describePolicyResponse.policy().policySummary().id());
                    model.setName(describePolicyResponse.policy().policySummary().name());
                    model.setType(describePolicyResponse.policy().policySummary().type().toString());
                    model.setAwsManaged(describePolicyResponse.policy().policySummary().awsManaged());
                    return ProgressEvent.progress(model, callbackContext);
                })
            )
            .then(progress -> listTargetsForPolicy(awsClientProxy, request, model, callbackContext, orgsClient, logger))
            .then(progress -> listTagsForPolicy(awsClientProxy, request, model, callbackContext, orgsClient, logger));
    }

    protected DescribePolicyResponse describePolicy(final DescribePolicyRequest describePolicyRequest, final ProxyClient<OrganizationsClient> orgsClient) {
        log.log(String.format("Retrieving policy details for policy [%s].", describePolicyRequest.policyId()));
        final DescribePolicyResponse response = orgsClient.injectCredentialsAndInvokeV2(describePolicyRequest, orgsClient.client()::describePolicy);
        return response;
    }

    protected ProgressEvent<ResourceModel, CallbackContext> listTargetsForPolicy(
        final AmazonWebServicesClientProxy awsClientProxy,
        final ResourceHandlerRequest<ResourceModel> request,
        final ResourceModel model,
        final CallbackContext callbackContext,
        final ProxyClient<OrganizationsClient> orgsClient,
        final Logger logger
    ) {
        String policyId = model.getId();
        Set<String> policyTargetIds = new HashSet<>();

        logger.log(String.format("Listing targets for policyId: %s", policyId));
        String nextToken = null;
        do {
            // since need to handle policyTarget list pagination manually, use try/catch for error handling
            String finalNextToken = nextToken;
            ProgressEvent<ResourceModel, CallbackContext> listPolicyProgressEvent = awsClientProxy.initiate("AWS-Organizations-Policy::listTargetsForPolicy", orgsClient, model, callbackContext)
                    .translateToServiceRequest(t -> Translator.translateToListTargetsForPolicyRequest(policyId, finalNextToken))
                    .makeServiceCall(this::listTargets)
                    .handleError((organizationsRequest, e, proxyClient1, model1, context) ->
                            handleErrorInGeneral(organizationsRequest, e, proxyClient1, model1, context, logger, PolicyConstants.Action.LIST_TARGETS_FOR_POLICY, PolicyConstants.Handler.READ))
                    .done(listTargetsForPolicyResponse -> {
                        for (PolicyTargetSummary targetSummary : listTargetsForPolicyResponse.targets()) {
                            policyTargetIds.add(targetSummary.targetId());
                        }
                        return ProgressEvent.<ResourceModel, CallbackContext>builder()
                                .nextToken(listTargetsForPolicyResponse.nextToken())
                                .status(OperationStatus.SUCCESS)
                                .build();
                    });
            if( listPolicyProgressEvent.getStatus() != OperationStatus.SUCCESS){
                return listPolicyProgressEvent;
            }
            nextToken = listPolicyProgressEvent.getNextToken();

        } while (nextToken != null);

        model.setTargetIds(policyTargetIds);
        return ProgressEvent.progress(model, callbackContext);
    }

    private  ListTargetsForPolicyResponse listTargets(ListTargetsForPolicyRequest listTargetsForPolicyRequest, ProxyClient<OrganizationsClient> orgsClient) {
        return orgsClient.injectCredentialsAndInvokeV2(listTargetsForPolicyRequest, orgsClient.client()::listTargetsForPolicy);
    }

    protected ProgressEvent<ResourceModel, CallbackContext> listTagsForPolicy(
        final AmazonWebServicesClientProxy awsClientProxy,
        final ResourceHandlerRequest<ResourceModel> request,
        final ResourceModel model,
        final CallbackContext callbackContext,
        final ProxyClient<OrganizationsClient> orgsClient,
        final Logger logger
        ) {

        String policyId = model.getId();
        logger.log(String.format("Listing tags for policyId: %s.", policyId));

        // ListTags currently returns all (max: 50) tags in a single call, so no need for pagination handling
        return awsClientProxy.initiate("AWS-Organizations-Policy::ListTagsForResource", orgsClient, model, callbackContext)
            .translateToServiceRequest(resourceModel -> Translator.translateToListTagsForResourceRequest(policyId))
            .makeServiceCall(this::listTagsForResource)
            .handleError((organizationsRequest, e, orgsClient1, model1, context) ->
                             handleErrorInGeneral(organizationsRequest, e, orgsClient1, model1, context, logger, PolicyConstants.Action.LIST_TAGS_FOR_POLICY, PolicyConstants.Handler.READ))
            .done(listTagsForResourceResponse -> {
                model.setTags(Translator.translateTagsFromSdkResponse(listTagsForResourceResponse.tags()));
                return ProgressEvent.defaultSuccessHandler(model);
            });
    }

    protected ListTagsForResourceResponse listTagsForResource(final ListTagsForResourceRequest listTagsForResourceRequest, final ProxyClient<OrganizationsClient> orgsClient) {
        log.log(String.format("Calling listTagsForResource API for policy [%s].", listTagsForResourceRequest.resourceId()));
        final ListTagsForResourceResponse response = orgsClient.injectCredentialsAndInvokeV2(listTagsForResourceRequest, orgsClient.client()::listTagsForResource);
        return response;
    }
}
