package software.amazon.organizations.policy;

import org.apache.commons.collections4.CollectionUtils;
import software.amazon.awssdk.services.organizations.OrganizationsClient;
import software.amazon.awssdk.services.organizations.model.AttachPolicyRequest;
import software.amazon.awssdk.services.organizations.model.AttachPolicyResponse;
import software.amazon.awssdk.services.organizations.model.CreatePolicyRequest;
import software.amazon.awssdk.services.organizations.model.CreatePolicyResponse;
import software.amazon.awssdk.services.organizations.model.DuplicatePolicyAttachmentException;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.HandlerErrorCode;
import software.amazon.cloudformation.proxy.Logger;
import software.amazon.cloudformation.proxy.ProgressEvent;
import software.amazon.cloudformation.proxy.ProxyClient;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;

import java.util.Set;

public class CreateHandler extends BaseHandlerStd {
    private Logger log;

    public ProgressEvent<ResourceModel, CallbackContext> handleRequest(
        final AmazonWebServicesClientProxy awsClientProxy,
        final ResourceHandlerRequest<ResourceModel> request,
        final CallbackContext callbackContext,
        final ProxyClient<OrganizationsClient> orgsClient,
        final Logger logger) {

        this.log = logger;
        final ResourceModel model = request.getDesiredResourceState();
        if (model.getName() == null || model.getType() == null || model.getContent() == null) {
            return ProgressEvent.failed(model, callbackContext, HandlerErrorCode.InvalidRequest,
                "Policy cannot be created without name, type, and content!");
        }

        logger.log(String.format("Entered %s create handler with account Id [%s], with Content [%s], Description [%s], Name [%s], Type [%s]",
            ResourceModel.TYPE_NAME, request.getAwsAccountId(), model.getContent(), model.getDescription(), model.getName(), model.getType()));
        return ProgressEvent.progress(model, callbackContext)
            .then(progress -> {
                if (progress.getCallbackContext().isPolicyCreated()) {
                    // skip to attach policy
                    log.log(String.format("Policy has already been created in previous handler invoke, policy Id: [%s]. Skip to attach policy.", model.getId()));
                    return ProgressEvent.progress(model, callbackContext);
                }
                return awsClientProxy.initiate("AWS-Organizations-Policy::CreatePolicy", orgsClient, progress.getResourceModel(), progress.getCallbackContext())
                    .translateToServiceRequest(Translator::translateToCreateRequest)
                    .makeServiceCall(this::createPolicy)
                    .handleError((organizationsRequest, e, proxyClient1, model1, context) -> handleError(
                        organizationsRequest, e, proxyClient1, model1, context, logger))
                    .done(CreatePolicyResponse -> {
                        logger.log(String.format("Created policy with Id: [%s]", CreatePolicyResponse.policy().policySummary().id()));
                        model.setId(CreatePolicyResponse.policy().policySummary().id());
                        progress.getCallbackContext().setPolicyCreated(true);
                        return ProgressEvent.progress(model, callbackContext);
                    });
                }
            )
            .then(progress -> attachPolicyToTargets(awsClientProxy, request, model, callbackContext, orgsClient, logger))
            .then(progress -> new ReadHandler().handleRequest(awsClientProxy, request, callbackContext, orgsClient, logger));
    }

    protected CreatePolicyResponse createPolicy(final CreatePolicyRequest createPolicyRequest, final ProxyClient<OrganizationsClient> orgsClient) {
        log.log(String.format("Start creating policy."));
        final CreatePolicyResponse createPolicyResponse = orgsClient.injectCredentialsAndInvokeV2(createPolicyRequest, orgsClient.client()::createPolicy);
        return createPolicyResponse;
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
            logger.log("No target id found in request. Skip attaching policy.");
            return ProgressEvent.progress(model, callbackContext);
        }
        logger.log("Target Ids found in request. Start attaching policy to provided targets.");
        for (final String targetId : targets) {
            logger.log(String.format("Start attaching policy to targetId [%s].", targetId));
            final ProgressEvent<ResourceModel, CallbackContext> progressEvent = awsClientProxy
                .initiate("AWS-Organizations-Policy::AttachPolicy", orgsClient, model, callbackContext)
                .translateToServiceRequest((resourceModel) -> Translator.translateToAttachRequest(model.getId(), targetId))
                .makeServiceCall(this::attachPolicy)
                .handleError((organizationsRequest, e, proxyClient1, model1, context) -> {
                    // retry on exceptions that map to CloudFormation retriable exceptions
                    // reference: https://docs.aws.amazon.com/cloudformation-cli/latest/userguide/resource-type-test-contract-errors.html
                    if (isRetriableException(e)) {
                        int currentAttempt = context.getRetryAttempt();
                        if (currentAttempt != context.getMaxRetryCount()) {
                            int callbackDelaySeconds = computeDelayBeforeNextRetry(currentAttempt);
                            logger.log(String.format("Got %s when calling attachPolicy for "
                                                         + "policyId [%s], targetId [%s]. Retrying %s of %s with callback delay %s seconds.",
                                e.getClass().getName(), model1.getId(), targetId, currentAttempt+1, context.getMaxRetryCount(), callbackDelaySeconds));
                            context.setRetryAttempt(context.getRetryAttempt() + 1);
                            return ProgressEvent.defaultInProgressHandler(context,callbackDelaySeconds,model1);
                        } else {
                            logger.log("All retry attempts exhausted, return exception to CloudFormation for further handling.");
                            return handleError(organizationsRequest, e, proxyClient1, model1, context, logger);
                        }
                    } else if (e instanceof DuplicatePolicyAttachmentException) {
                        logger.log(String.format("Got DuplicatePolicyAttachmentException when calling attachPolicy for "
                                                     + "policyId [%s], targetId [%s]. Continuing with attach...",
                            e.getClass().getName(), model.getId(), targetId));
                        return ProgressEvent.progress(model1,context);
                    } else {
                        return handleError(organizationsRequest, e, proxyClient1, model1, context, logger);
                    }
                })
                .success();
            if (!progressEvent.isSuccess()) {
                return progressEvent;
            }
        }
        return ProgressEvent.progress(model, callbackContext);
    }

    protected AttachPolicyResponse attachPolicy(final AttachPolicyRequest attachPolicyRequest, final ProxyClient<OrganizationsClient> orgsClient) {
        final AttachPolicyResponse attachPolicyResponse = orgsClient.injectCredentialsAndInvokeV2(attachPolicyRequest, orgsClient.client()::attachPolicy);
        return attachPolicyResponse;
    }
}
