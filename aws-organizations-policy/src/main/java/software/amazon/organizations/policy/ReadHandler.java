package software.amazon.organizations.policy;

import software.amazon.awssdk.services.organizations.OrganizationsClient;
import software.amazon.awssdk.services.organizations.model.*;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.Logger;
import software.amazon.cloudformation.proxy.ProgressEvent;
import software.amazon.cloudformation.proxy.ProxyClient;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;

import java.util.HashSet;
import java.util.Set;
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
                .done(describePolicyResponse -> ProgressEvent.progress(Translator.translateFromReadResponse(describePolicyResponse, model, logger), callbackContext))
            )
            // listTargetsForPolicy
            .then(progress -> listTargetsForPolicy(awsClientProxy, request, model, callbackContext, orgsClient, logger))
            // listTagsForResource
            .then(progress -> listTagsForPolicy(awsClientProxy, request, model, callbackContext, orgsClient, logger));
    }

    protected DescribePolicyResponse describePolicy(final DescribePolicyRequest describePolicyRequest, final ProxyClient<OrganizationsClient> orgsClient) {
        logger.log(String.format("Retrieving policy details."));
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
        logger.log("model1: " + model);

        String policyId = model.getId();

        logger.log(String.format("Listing targets for policyId: %s\n", policyId));
        Set<String> policyTargetIds = new HashSet<>();
        String nextToken = null;
        do {
            ListTargetsForPolicyResponse pageResult = awsClientProxy.injectCredentialsAndInvokeV2(
                Translator.translateToListTargetsForPolicyRequest(policyId, nextToken),
                orgsClient.client()::listTargetsForPolicy);
            for (PolicyTargetSummary targetSummary : pageResult.targets()) {
                policyTargetIds.add(targetSummary.targetId());
            }
            nextToken = pageResult.nextToken();
        } while (nextToken != null);

//        return ProgressEvent.progress(addTargetIdsToModel(model, policyTargetIds), callbackContext);
        model.setTargetIds(policyTargetIds);
        logger.log("model2: " + model);
        return ProgressEvent.progress(model, callbackContext);
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

        logger.log("model3: " + model);

        logger.log(String.format("Listing tags for policyId: %s.\n", policyId));
        return awsClientProxy.initiate("AWS-Organizations-Policy::ListTagsForResource", orgsClient, model, callbackContext)
            .translateToServiceRequest(resourceModel -> Translator.translateToListTagsForResourceRequest(policyId))
            .makeServiceCall(this::listTagsForResource)
            .handleError((organizationsRequest, e, orgsClient1, model1, context) -> handleError(
                organizationsRequest, e, orgsClient1, model1, context, logger))
            .done(listTagsForResourceResponse -> ProgressEvent.defaultSuccessHandler(Translator.translateFromListTagsResponse(model, listTagsForResourceResponse, logger)));
    }

    protected ResourceModel addTargetIdsToModel(ResourceModel model, Set<String> policyTargetIds) {
        return ResourceModel.builder()
            .arn(model.getArn())
            .id(model.getId())
            .name(model.getName())
            .description(model.getDescription())
            .targetIds(policyTargetIds)
            .tags(model.getTags())
            .build();
    }

    protected ListTagsForResourceResponse listTagsForResource(final ListTagsForResourceRequest listTagsForResourceRequest, final ProxyClient<OrganizationsClient> orgsClient) {
        logger.log("Calling listTagsForResource API.\n");
        final ListTagsForResourceResponse response = orgsClient.injectCredentialsAndInvokeV2(listTagsForResourceRequest, orgsClient.client()::listTagsForResource);
        return response;
    }
}
