package software.amazon.organizations.policy;

import com.google.common.collect.Sets;
import software.amazon.awssdk.services.organizations.OrganizationsClient;
import software.amazon.awssdk.services.organizations.model.AttachPolicyRequest;
import software.amazon.awssdk.services.organizations.model.DetachPolicyRequest;
import software.amazon.awssdk.services.organizations.model.DuplicatePolicyAttachmentException;
import software.amazon.awssdk.services.organizations.model.PolicyNotAttachedException;
import software.amazon.awssdk.services.organizations.model.Tag;
import software.amazon.awssdk.services.organizations.model.TagResourceRequest;
import software.amazon.awssdk.services.organizations.model.UntagResourceRequest;
import software.amazon.awssdk.services.organizations.model.UpdatePolicyRequest;
import software.amazon.awssdk.services.organizations.model.UpdatePolicyResponse;
import software.amazon.awssdk.utils.CollectionUtils;
import software.amazon.cloudformation.exceptions.CfnInvalidRequestException;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.HandlerErrorCode;
import software.amazon.cloudformation.proxy.ProgressEvent;
import software.amazon.cloudformation.proxy.ProxyClient;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;
import software.amazon.organizations.utils.OrgsLoggerWrapper;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
 import java.util.stream.Collectors;

public class UpdateHandler extends BaseHandlerStd {
    private OrgsLoggerWrapper log;

    @Override
    public ProgressEvent<ResourceModel, CallbackContext> handleRequest(
        final AmazonWebServicesClientProxy awsClientProxy,
        final ResourceHandlerRequest<ResourceModel> request,
        final CallbackContext callbackContext,
        final ProxyClient<OrganizationsClient> orgsClient,
        final OrgsLoggerWrapper logger) {

        this.log = logger;
        final ResourceModel previousModel = request.getPreviousResourceState();
        final ResourceModel model = request.getDesiredResourceState();

        String policyId = model.getId();

        if (previousModel == null || previousModel.getId() == null || !policyId.equals(previousModel.getId())) {
            return ProgressEvent.failed(model, callbackContext, HandlerErrorCode.NotFound,
                String.format("Policy [%s] cannot be updated as the id was changed!", model.getName()));
        }

        if (!previousModel.getType().equalsIgnoreCase(model.getType())) {
            return ProgressEvent.failed(model, callbackContext, HandlerErrorCode.NotUpdatable,
                String.format("Cannot update policy type after creation for [%s]!", model.getName()));
        }

        String content;
        try {
            content = Translator.convertObjectToString(model.getContent());
        } catch (CfnInvalidRequestException e){
            logger.log(String.format("The policy content did not include a valid JSON. This is an InvalidRequest for management account Id [%s]", request.getAwsAccountId()));
            return ProgressEvent.failed(model, callbackContext, HandlerErrorCode.InvalidRequest,
                "Policy content had invalid JSON!");
        }

        logger.log(String.format("Entered %s update handler with account Id [%s], with Content [%s], Description [%s], Name [%s], Type [%s]",
            ResourceModel.TYPE_NAME, request.getAwsAccountId(), content, model.getDescription(), model.getName(), model.getType()));
        return ProgressEvent.progress(model, callbackContext)
            .then(progress ->{
                    if (progress.getCallbackContext().isPolicyUpdated()) {
                        log.log(String.format("UpdatePolicy has been entered in previous handler invoke for policy [%s]. Skip to next step.", model.getId()));
                        return ProgressEvent.progress(model, callbackContext);
                    }
                    // call UpdatePolicy API
                    logger.log(String.format("Requesting UpdatePolicy w/ id: %s", policyId));
                    return awsClientProxy.initiate("AWS-Organizations-Policy::UpdatePolicy", orgsClient, progress.getResourceModel(), progress.getCallbackContext())
                               .translateToServiceRequest(Translator::translateToUpdateRequest)
                               .makeServiceCall(this::updatePolicy)
                               .handleError((organizationsRequest, e, proxyClient1, model1, context) -> handleErrorInGeneral(organizationsRequest, e, proxyClient1, model1, context, logger, PolicyConstants.Action.UPDATE_POLICY, PolicyConstants.Handler.UPDATE))
                               .done(UpdatePolicyResponse -> {
                                   progress.getCallbackContext().setPolicyUpdated(true);
                                   return ProgressEvent.progress(model, callbackContext);
                               });
                }

            )
            .then(progress -> handleTargets(request, awsClientProxy, model, callbackContext, request.getDesiredResourceState().getTargetIds(), request.getPreviousResourceState().getTargetIds(), policyId, orgsClient, logger))
            .then(progress -> handleTagging(awsClientProxy, model, callbackContext,
                                    convertPolicyTagToOrganizationTag(model.getTags()),
                                    convertPolicyTagToOrganizationTag(previousModel.getTags()),
                                    policyId, orgsClient, logger))
            .then(progress -> new ReadHandler().handleRequest(awsClientProxy, request, callbackContext, orgsClient, logger));
    }

    protected UpdatePolicyResponse updatePolicy(final UpdatePolicyRequest updatePolicyRequest, final ProxyClient<OrganizationsClient> orgsClient) {
        log.log(String.format("Calling updatePolicy API for policy [%s].", updatePolicyRequest.policyId()));
        final UpdatePolicyResponse response = orgsClient.injectCredentialsAndInvokeV2(updatePolicyRequest, orgsClient.client()::updatePolicy);
        return response;
    }

    // handles attaching to targets: adding to new and removing from old targets
    private ProgressEvent<ResourceModel, CallbackContext> handleTargets(
        final ResourceHandlerRequest<ResourceModel> request,
        final AmazonWebServicesClientProxy awsClientProxy,
        final ResourceModel model,
        final CallbackContext callbackContext,
        final Set<String> desiredTargets,
        final Set<String> previousTargets,
        final String policyId,
        final ProxyClient<OrganizationsClient> orgsClient,
        final OrgsLoggerWrapper logger
    ) {
        // filter previous and desired lists to determine which to attach and remove
        final List<String> targetsToAttach = new ArrayList<>();
        if (!CollectionUtils.isNullOrEmpty(desiredTargets)) {
            for (String desired : desiredTargets) {
                if (previousTargets == null || !previousTargets.contains(desired)) {
                    targetsToAttach.add(desired);
                }
            }
        }

        final List<String> targetsToRemove = new ArrayList<>();
        if (!CollectionUtils.isNullOrEmpty(previousTargets)) {
            for (String previous : previousTargets) {
                if (desiredTargets == null || !desiredTargets.contains(previous)) {
                    targetsToRemove.add(previous);
                }
            }
        }

        // make the calls to attach to new targets
        if (!CollectionUtils.isNullOrEmpty(targetsToAttach)) {
            for (String attachTargetId : targetsToAttach) {
                logger.log(String.format("Calling attachPolicy API with targetId: [%s] for policy [%s]", attachTargetId, model.getName()));
                AttachPolicyRequest attachPolicyRequest = Translator.translateToAttachRequest(policyId, attachTargetId);
                try {
                    awsClientProxy.injectCredentialsAndInvokeV2(attachPolicyRequest, orgsClient.client()::attachPolicy);
                } catch (Exception e) {
                    if (e instanceof DuplicatePolicyAttachmentException) {
                        logger.log(String.format("Got %s when calling attachPolicy for "
                            + "policyId [%s], targetId [%s]. Continuing with update...",
                            e.getClass().getName(), policyId, attachTargetId));
                    } else {
                        return handleErrorInGeneral(attachPolicyRequest, e, orgsClient, model, callbackContext, logger, PolicyConstants.Action.ATTACH_POLICY, PolicyConstants.Handler.UPDATE);
                    }
                }
            }
        }

        // make calls to detach from old targets
        if (!CollectionUtils.isNullOrEmpty(targetsToRemove)) {
            for (String removeTargetId : targetsToRemove) {
                logger.log(String.format("Calling detachPolicy API with targetId: [%s] for policy [%s]", removeTargetId, model.getName()));
                DetachPolicyRequest detachPolicyRequest = Translator.translateToDetachRequest(policyId, removeTargetId);
                try {
                    awsClientProxy.injectCredentialsAndInvokeV2(detachPolicyRequest, orgsClient.client()::detachPolicy);
                } catch (Exception e) {
                    if (e instanceof PolicyNotAttachedException) {
                        logger.log(String.format("Got %s when calling detachPolicy for "
                            + "policyId [%s], targetId [%s]. Continuing with update...",
                            e.getClass().getName(), policyId, removeTargetId));
                    } else {
                        return handleErrorInGeneral(detachPolicyRequest, e, orgsClient, model, callbackContext, logger, PolicyConstants.Action.DETACH_POLICY, PolicyConstants.Handler.UPDATE);
                    }
                }
            }
        }

        return ProgressEvent.progress(model, callbackContext);
    }

    // handles tagging: creating new, modifying existing, and deleting old
    private ProgressEvent<ResourceModel, CallbackContext> handleTagging(
            final AmazonWebServicesClientProxy awsClientProxy,
            final ResourceModel model,
            final CallbackContext callbackContext,
            final Set<Tag> desiredTags,
            final Set<Tag> previousTags,
            final String policyId,
            final ProxyClient<OrganizationsClient> orgsClient,
            final OrgsLoggerWrapper logger
    ) {
        // Includes all old tags that do not exist in new tag list
        final Set<String> tagsToRemove = getTagKeysToRemove(previousTags, desiredTags);

        // Excluded all old tags that do exist in new tag list
        final Set<Tag> tagsToAddOrUpdate = getTagsToAddOrUpdate(previousTags, desiredTags);

        // Delete tags only if tagsToRemove is not empty
        if (!tagsToRemove.isEmpty()) {
            logger.log(String.format("Calling untagResource API for policy [%s].", model.getName()));
            UntagResourceRequest untagResourceRequest = Translator.translateToUntagResourceRequest(tagsToRemove, policyId);
            try {
                awsClientProxy.injectCredentialsAndInvokeV2(untagResourceRequest, orgsClient.client()::untagResource);
            } catch (Exception e) {
                return handleErrorInGeneral(untagResourceRequest, e, orgsClient, model, callbackContext, logger, PolicyConstants.Action.UNTAG_RESOURCE, PolicyConstants.Handler.UPDATE);
            }
        }

        // Add tags only if tagsToAddOrUpdate is not empty.
        if (!tagsToAddOrUpdate.isEmpty()) {
            logger.log(String.format("Calling tagResource API for policy [%s].", model.getName()));
            TagResourceRequest tagResourceRequest = Translator.translateToTagResourceRequest(tagsToAddOrUpdate, policyId);
            try {
                awsClientProxy.injectCredentialsAndInvokeV2(tagResourceRequest, orgsClient.client()::tagResource);
            } catch (Exception e) {
                return handleErrorInGeneral(tagResourceRequest, e, orgsClient, model, callbackContext, logger, PolicyConstants.Action.TAG_RESOURCE, PolicyConstants.Handler.UPDATE);
            }
        }

        return ProgressEvent.progress(model, callbackContext);
    }

    static Set<Tag> convertPolicyTagToOrganizationTag(final Set<software.amazon.organizations.policy.Tag> tags) {
        final Set<Tag> tagsToReturn = new HashSet<>();
        if (tags == null) return tagsToReturn;
        for (software.amazon.organizations.policy.Tag inputTag : tags) {
            Tag tag = Tag.builder()
                .key(inputTag.getKey())
                .value(inputTag.getValue())
                .build();
            tagsToReturn.add(tag);
        }
        return tagsToReturn;
    }

    static Set<String> getTagKeysToRemove(
           Set<Tag> oldTags, Set<Tag> newTags) {
        final Set<String> oldTagKeys = getTagKeySet(oldTags);
        final Set<String> newTagKeys = getTagKeySet(newTags);
        return Sets.difference(oldTagKeys, newTagKeys);
    }

    static Set<String> getTagKeySet(Set<Tag> oldTags) {
        return oldTags.stream().map(Tag::key).collect(Collectors.toSet());
    }

    static Set<Tag> getTagsToAddOrUpdate(
           Set<Tag> oldTags, Set<Tag> newTags) {
        return Sets.difference(newTags, oldTags);
    }

}
