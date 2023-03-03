package software.amazon.organizations.policy;

import org.apache.commons.collections4.CollectionUtils;
import software.amazon.awssdk.services.organizations.OrganizationsClient;
import software.amazon.awssdk.services.organizations.model.AttachPolicyRequest;
import software.amazon.awssdk.services.organizations.model.AttachPolicyResponse;
import software.amazon.awssdk.services.organizations.model.CreatePolicyRequest;
import software.amazon.awssdk.services.organizations.model.CreatePolicyResponse;
import software.amazon.awssdk.services.organizations.model.DuplicatePolicyAttachmentException;
import software.amazon.cloudformation.exceptions.CfnInvalidRequestException;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.HandlerErrorCode;
import software.amazon.cloudformation.proxy.ProgressEvent;
import software.amazon.cloudformation.proxy.ProxyClient;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;
import software.amazon.organizations.utils.OrgsLoggerWrapper;

import java.util.Set;

public class CreateHandler extends BaseHandlerStd {
    private OrgsLoggerWrapper log;

    public ProgressEvent<ResourceModel, CallbackContext> handleRequest(
        final AmazonWebServicesClientProxy awsClientProxy,
        final ResourceHandlerRequest<ResourceModel> request,
        final CallbackContext callbackContext,
        final ProxyClient<OrganizationsClient> orgsClient,
        final OrgsLoggerWrapper logger) {

        this.log = logger;

        final ResourceModel model = request.getDesiredResourceState();
        if (model.getName() == null || model.getType() == null || model.getContent() == null) {
            return ProgressEvent.failed(model, callbackContext, HandlerErrorCode.InvalidRequest,
                "Policy cannot be created without name, type, and content!");
        }

        String content;
        try {
            content = Translator.convertObjectToString(model.getContent());
        } catch (CfnInvalidRequestException e){
            logger.log(String.format("The policy content did not include a valid JSON. This is an InvalidRequest for management account Id [%s]", request.getAwsAccountId()));
            return ProgressEvent.failed(model, callbackContext, HandlerErrorCode.InvalidRequest,
                "Policy content had invalid JSON!");
        }

        logger.log(String.format("Entered %s create handler with account Id [%s], with Content [%s], Description [%s], Name [%s], Type [%s]",
            ResourceModel.TYPE_NAME, request.getAwsAccountId(), model.getContent(), model.getDescription(), model.getName(), model.getType()));
        return ProgressEvent.progress(model, callbackContext)
            .then(progress -> {
                if (progress.getCallbackContext().isPolicyCreated()) {
                    // skip to attach policy
                    log.log(String.format("Policy has already been created in previous handler invoke, policy id: [%s]. Skip to attach policy.", model.getId()));
                    return ProgressEvent.progress(model, callbackContext);
                }
                return awsClientProxy.initiate("AWS-Organizations-Policy::CreatePolicy", orgsClient, progress.getResourceModel(), progress.getCallbackContext())
                    .translateToServiceRequest(Translator::translateToCreateRequest)
                    .makeServiceCall(this::createPolicy)
                    .handleError((organizationsRequest, e, proxyClient1, model1, context) ->
                                     handleError(organizationsRequest, e, proxyClient1, model1, context, logger))
                    .done(CreatePolicyResponse -> {
                        logger.log(String.format("Created policy with Id: [%s] for policy name [%s].", CreatePolicyResponse.policy().policySummary().id(), model.getName()));
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
        log.log(String.format("Start creating policy for policy name [%s].", createPolicyRequest.name()));
        final CreatePolicyResponse createPolicyResponse = orgsClient.injectCredentialsAndInvokeV2(createPolicyRequest, orgsClient.client()::createPolicy);
        return createPolicyResponse;
    }

    protected ProgressEvent<ResourceModel, CallbackContext> attachPolicyToTargets(
        final AmazonWebServicesClientProxy awsClientProxy,
        final ResourceHandlerRequest<ResourceModel> request,
        final ResourceModel model,
        final CallbackContext callbackContext,
        final ProxyClient<OrganizationsClient> orgsClient,
        final OrgsLoggerWrapper logger) {

        Set<String> targets = model.getTargetIds();
        String policyName = model.getName();
        if (CollectionUtils.isEmpty(targets)) {
            logger.log(String.format("No target id found in request for policy [%s]. Skip attaching policy.", policyName));
            return ProgressEvent.progress(model, callbackContext);
        }
        logger.log(String.format("Target Ids found in request for policy [%s]. Start attaching policy to provided targets.", policyName));
        for (final String targetId : targets) {
            logger.log(String.format("Start attaching policy to targetId [%s] for policy [%s].", targetId, policyName));
            final ProgressEvent<ResourceModel, CallbackContext> progressEvent = awsClientProxy
                .initiate("AWS-Organizations-Policy::AttachPolicy", orgsClient, model, callbackContext)
                .translateToServiceRequest((resourceModel) -> Translator.translateToAttachRequest(model.getId(), targetId))
                .makeServiceCall(this::attachPolicy)
                .handleError((organizationsRequest, e, proxyClient1, model1, context) -> {
                    if (e instanceof DuplicatePolicyAttachmentException) {
                        log.log(String.format("Got DuplicatePolicyAttachmentException when calling attachPolicy for "
                                                     + "policy name [%s], targetId [%s]. Continuing with attach...",
                            e.getClass().getName(), policyName, targetId));
                        return ProgressEvent.progress(model1,context);
                    } else {
                        return handleErrorInGeneral(organizationsRequest, e, proxyClient1, model1, context, logger, PolicyConstants.Action.ATTACH_POLICY, PolicyConstants.Handler.CREATE);
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
