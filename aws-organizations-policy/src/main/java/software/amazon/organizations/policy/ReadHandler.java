package software.amazon.organizations.policy;

import software.amazon.awssdk.services.organizations.OrganizationsClient;
import software.amazon.awssdk.services.organizations.model.DescribePolicyRequest;
import software.amazon.awssdk.services.organizations.model.DescribePolicyResponse;
import software.amazon.awssdk.services.organizations.model.ListTagsForResourceRequest;
import software.amazon.awssdk.services.organizations.model.ListTagsForResourceResponse;
import software.amazon.awssdk.services.organizations.model.ListTargetsForPolicyRequest;
import software.amazon.awssdk.services.organizations.model.ListTargetsForPolicyResponse;
import software.amazon.awssdk.services.organizations.model.PolicyTargetSummary;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.Logger;
import software.amazon.cloudformation.proxy.ProgressEvent;
import software.amazon.cloudformation.proxy.ProxyClient;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;

import java.util.HashSet;
import java.util.Set;

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
            .then(progress -> awsClientProxy.initiate("AWS-Organizations-Policy::DescribePolicy", orgsClient, model, callbackContext)
                .translateToServiceRequest(t -> Translator.translateToReadRequest(model))
                .makeServiceCall(this::describePolicy)
                .handleError((organizationsRequest, e, proxyClient1, model1, context) -> handleError(
                    organizationsRequest, e, proxyClient1, model1, context, logger))
                .done(describePolicyResponse -> {
                    model.setArn(describePolicyResponse.policy().policySummary().arn().toString());
                    model.setDescription(describePolicyResponse.policy().policySummary().description());
                    model.setContent(describePolicyResponse.policy().content());
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
        String policyId = model.getId();
        Set<String> policyTargetIds = new HashSet<>();

        logger.log(String.format("Listing targets for policyId: %s\n", policyId));
        String nextToken = null;
        do {
            // since need to handle policyTarget list pagination manually, use try/catch for error handling
            ListTargetsForPolicyResponse pageResult = null;
            ListTargetsForPolicyRequest listTargetsRequest = null;
            try {
                listTargetsRequest = Translator.translateToListTargetsForPolicyRequest(policyId, nextToken);
                pageResult = awsClientProxy.injectCredentialsAndInvokeV2(
                    listTargetsRequest,
                    orgsClient.client()::listTargetsForPolicy);
            } catch (Exception e) {
                return handleError(listTargetsRequest, e, orgsClient, model, callbackContext, logger);
            }

            for (PolicyTargetSummary targetSummary : pageResult.targets()) {
                policyTargetIds.add(targetSummary.targetId());
            }
            nextToken = pageResult.nextToken();
        } while (nextToken != null);

        model.setTargetIds(policyTargetIds);
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
        logger.log(String.format("Listing tags for policyId: %s.\n", policyId));

        // ListTags currently returns all (max: 50) tags in a single call, so no need for pagination handling
        return awsClientProxy.initiate("AWS-Organizations-Policy::ListTagsForResource", orgsClient, model, callbackContext)
            .translateToServiceRequest(resourceModel -> Translator.translateToListTagsForResourceRequest(policyId))
            .makeServiceCall(this::listTagsForResource)
            .handleError((organizationsRequest, e, orgsClient1, model1, context) -> handleError(
                organizationsRequest, e, orgsClient1, model1, context, logger))
            .done(listTagsForResourceResponse -> {
                model.setTags(Translator.translateTagsFromSdkResponse(listTagsForResourceResponse.tags()));
                return ProgressEvent.defaultSuccessHandler(model);
            });
    }

    protected ListTagsForResourceResponse listTagsForResource(final ListTagsForResourceRequest listTagsForResourceRequest, final ProxyClient<OrganizationsClient> orgsClient) {
        logger.log("Calling listTagsForResource API.\n");
        final ListTagsForResourceResponse response = orgsClient.injectCredentialsAndInvokeV2(listTagsForResourceRequest, orgsClient.client()::listTagsForResource);
        return response;
    }
}
