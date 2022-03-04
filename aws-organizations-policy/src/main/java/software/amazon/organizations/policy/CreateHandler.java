package software.amazon.organizations.policy;

import org.apache.commons.collections4.CollectionUtils;
import software.amazon.awssdk.services.organizations.OrganizationsClient;
import software.amazon.awssdk.services.organizations.model.AttachPolicyRequest;
import software.amazon.awssdk.services.organizations.model.AttachPolicyResponse;
import software.amazon.awssdk.services.organizations.model.CreatePolicyRequest;
import software.amazon.awssdk.services.organizations.model.CreatePolicyResponse;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.Logger;
import software.amazon.cloudformation.proxy.ProgressEvent;
import software.amazon.cloudformation.proxy.ProxyClient;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;

import java.util.Set;

public class CreateHandler extends BaseHandlerStd {
    private Logger logger;

    public ProgressEvent<ResourceModel, CallbackContext> handleRequest(
        final AmazonWebServicesClientProxy awsClientProxy,
        final ResourceHandlerRequest<ResourceModel> request,
        final CallbackContext callbackContext,
        final ProxyClient<OrganizationsClient> orgsClient,
        final Logger logger) {

        this.logger = logger;
        final ResourceModel model = request.getDesiredResourceState();
        logger.log(String.format("Entered %s create handler with account Id [%s], ", ResourceModel.TYPE_NAME, request.getAwsAccountId()));
        logger.log(String.format("Create policy with Content [%s], Description [%s], Name [%s], Type [%s]", model.getContent(), model.getDescription(), model.getName(), model.getType()));
        return ProgressEvent.progress(model, callbackContext)
            // Create Policy
            .then(progress ->
                awsClientProxy.initiate("AWS-Organizations-Policy::CreatePolicy", orgsClient, progress.getResourceModel(), progress.getCallbackContext())
                    .translateToServiceRequest(Translator::translateToCreateRequest)
                    .makeServiceCall(this::createPolicy)
                    .handleError((organizationsRequest, e, proxyClient1, model1, context) -> handleError(
                        organizationsRequest, e, proxyClient1, model1, context, logger))
                    .done(CreatePolicyResponse -> {
                        logger.log(String.format("Created policy with Id: [%s]", CreatePolicyResponse.policy().policySummary().id()));
                        model.setId(CreatePolicyResponse.policy().policySummary().id());
                        return ProgressEvent.progress(model, callbackContext);
                    })
            )
            // Attach Policy
            .then(progress -> attachPolicyToTargets(awsClientProxy, request, model, callbackContext, orgsClient, logger))
            // TODO: TagResource
            // Read Handler
            .then(progress -> new ReadHandler().handleRequest(awsClientProxy, request, callbackContext, orgsClient, logger));
    }

    protected CreatePolicyResponse createPolicy(final CreatePolicyRequest createPolicyRequest, final ProxyClient<OrganizationsClient> orgsClient) {
        logger.log(String.format("Start creating policy."));
        final CreatePolicyResponse createPolicyResponse = orgsClient.injectCredentialsAndInvokeV2(createPolicyRequest, orgsClient.client()::createPolicy);
        return createPolicyResponse;
    }

    protected AttachPolicyResponse attachPolicy(final AttachPolicyRequest attachPolicyRequest, final ProxyClient<OrganizationsClient> orgsClient) {
        logger.log(String.format("Start attaching policy."));
        final AttachPolicyResponse attachPolicyResponse = orgsClient.injectCredentialsAndInvokeV2(attachPolicyRequest, orgsClient.client()::attachPolicy);
        return attachPolicyResponse;
    }

    protected ProgressEvent<ResourceModel, CallbackContext> attachPolicyToTargets(
        final AmazonWebServicesClientProxy awsClientProxy,
        final ResourceHandlerRequest<ResourceModel> request,
        final ResourceModel model,
        final CallbackContext callbackContext,
        final ProxyClient<OrganizationsClient> orgsClient,
        final Logger logger) {

        Set<String> targets = model.getTargetIds();
        if (CollectionUtils.isEmpty(targets)) {
            logger.log("No target id found in request. Skip attaching policy.\n");
            return ProgressEvent.progress(model, callbackContext);
        }
        logger.log("Target Ids found in request. Start attaching policy to provided targets.\n");
        targets.forEach(targetId -> {
            awsClientProxy.initiate("AWS-Organizations-Policy::AttachPolicy", orgsClient, model, callbackContext)
                .translateToServiceRequest(resourceModel -> Translator.translateToAttachRequest(model.getId(), targetId))
                .makeServiceCall(this::attachPolicy)
                .handleError((organizationsRequest, e, proxyClient1, model1, context) -> handleError(
                    organizationsRequest, e, proxyClient1, model1, context, logger))
                .done(CreatePolicyResponse -> ProgressEvent.progress(model, callbackContext));
        });
        return ProgressEvent.progress(model, callbackContext);
    }
}
