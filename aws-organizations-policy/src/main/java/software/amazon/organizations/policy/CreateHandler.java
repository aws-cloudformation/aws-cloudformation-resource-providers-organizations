package software.amazon.organizations.policy;

import org.apache.commons.collections4.CollectionUtils;
import software.amazon.awssdk.services.organizations.OrganizationsClient;
import software.amazon.awssdk.services.organizations.model.AttachPolicyRequest;
import software.amazon.awssdk.services.organizations.model.AttachPolicyResponse;
import software.amazon.awssdk.services.organizations.model.CreatePolicyRequest;
import software.amazon.awssdk.services.organizations.model.CreatePolicyResponse;
import software.amazon.awssdk.services.organizations.model.DuplicatePolicyAttachmentException;
import software.amazon.awssdk.services.organizations.model.ListPoliciesRequest;
import software.amazon.awssdk.services.organizations.model.PolicySummary;
import software.amazon.cloudformation.exceptions.CfnInvalidRequestException;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.HandlerErrorCode;
import software.amazon.cloudformation.proxy.OperationStatus;
import software.amazon.cloudformation.proxy.ProgressEvent;
import software.amazon.cloudformation.proxy.ProxyClient;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;
import software.amazon.organizations.utils.OrgsLoggerWrapper;

import java.util.Arrays;
import java.util.Optional;
import java.util.Set;

public class CreateHandler extends BaseHandlerStd {
    private OrgsLoggerWrapper log;
    private static final int CALLBACK_DELAY = 1;

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
            ResourceModel.TYPE_NAME, request.getAwsAccountId(), content, model.getDescription(), model.getName(), model.getType()));
        return ProgressEvent.progress(model, callbackContext)
            .then(progress -> callbackContext.isPreExistenceCheckComplete() ? progress : checkIfPolicyExists(awsClientProxy, progress, orgsClient))
            .then(progress -> {
                if (progress.getCallbackContext().isPreExistenceCheckComplete() && progress.getCallbackContext().isResourceAlreadyExists()) {
                    String message = String.format("Policy already exists for policy name [%s].", model.getName());
                    log.log(message);
                    return ProgressEvent.failed(model, callbackContext, HandlerErrorCode.AlreadyExists, message);
                }
                if (progress.getCallbackContext().isPolicyCreated()) {
                    // skip to attach policy
                    log.log(String.format("Policy has already been created in previous handler invoke, policy id: [%s]. Skip to attach policy.", model.getId()));
                    return ProgressEvent.progress(model, callbackContext);
                }
                return awsClientProxy.initiate("AWS-Organizations-Policy::CreatePolicy", orgsClient, progress.getResourceModel(), progress.getCallbackContext())
                    .translateToServiceRequest(x -> Translator.translateToCreateRequest(x, request))
                    .makeServiceCall(this::createPolicy)
                    .handleError((organizationsRequest, e, proxyClient1, model1, context) ->
                            handleErrorOnCreate(organizationsRequest, e, proxyClient1, model1, context, logger, Arrays.asList(ALREADY_EXISTS_ERROR_CODE, ENTITY_ALREADY_EXISTS_ERROR_CODE)))
                    .done(CreatePolicyResponse -> {
                        logger.log(String.format("Created policy with Id: [%s] for policy name [%s].", CreatePolicyResponse.policy().policySummary().id(), model.getName()));
                        model.setId(CreatePolicyResponse.policy().policySummary().id());
                        progress.getCallbackContext().setPolicyCreated(true);
                        return ProgressEvent.defaultInProgressHandler(callbackContext, CALLBACK_DELAY, model);
                    });
                }
            )
            .then(progress -> attachPolicyToTargets(awsClientProxy, request, model, callbackContext, orgsClient, logger))
            .then(progress -> new ReadHandler().handleRequest(awsClientProxy, request, callbackContext, orgsClient, logger));
    }

    private ProgressEvent<ResourceModel, CallbackContext> checkIfPolicyExists(
            final AmazonWebServicesClientProxy awsClientProxy,
            ProgressEvent<ResourceModel, CallbackContext> progress,
            final ProxyClient<OrganizationsClient> orgsClient) {

        final ResourceModel model = progress.getResourceModel();
        final CallbackContext context = progress.getCallbackContext();
        String nextToken = null;

        do {
            final String currentToken = nextToken;

            ProgressEvent<ResourceModel, CallbackContext> currentProgress = awsClientProxy.initiate("AWS-Organizations-Policy::ListPolicies", orgsClient, model, context)
                    .translateToServiceRequest(resourceModel -> ListPoliciesRequest.builder()
                            .filter(resourceModel.getType())
                            .nextToken(currentToken)
                            .build())
                    .makeServiceCall((listPoliciesRequest, proxyClient) -> proxyClient.injectCredentialsAndInvokeV2(listPoliciesRequest, proxyClient.client()::listPolicies))
                    .done((listPoliciesRequest, listPoliciesResponse, proxyClient, resourceModel, ctx) -> {

                        Optional<PolicySummary> existingPolicy = listPoliciesResponse.policies().stream()
                                .filter(policy -> policy.name().equals(model.getName()))
                                .findFirst();

                        if (existingPolicy.isPresent()) {
                            model.setId(existingPolicy.get().id());
                            context.setResourceAlreadyExists(true);
                            log.log(String.format("Failing PreExistenceCheck: Policy [%s] already exists with Id: [%s]",
                                    model.getName(), model.getId()));
                        }

                        return ProgressEvent.<ResourceModel, CallbackContext>builder()
                                .resourceModel(model)
                                .callbackContext(context)
                                .nextToken(listPoliciesResponse.nextToken())
                                .status(OperationStatus.IN_PROGRESS)
                                .build();
                    });

            nextToken = currentProgress.getNextToken();

        } while (nextToken != null && !context.isResourceAlreadyExists());

        context.setPreExistenceCheckComplete(true);
        int callbackDelaySeconds = 0;
        if (context.isResourceAlreadyExists()) {
            log.log("PreExistenceCheck complete! Requested resource was found.");
        } else {
            callbackDelaySeconds = CALLBACK_DELAY;
            log.log("PreExistenceCheck complete! Requested resource was not found.");
        }
        return ProgressEvent.<ResourceModel, CallbackContext>builder()
                .resourceModel(model)
                .callbackContext(context)
                .callbackDelaySeconds(callbackDelaySeconds)
                .status(OperationStatus.IN_PROGRESS)
                .build();
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
                        log.log(String.format("Got %s when calling attachPolicy for "
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
