package software.amazon.organizations.policy;

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
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.HandlerErrorCode;
import software.amazon.cloudformation.proxy.Logger;
import software.amazon.cloudformation.proxy.ProgressEvent;
import software.amazon.cloudformation.proxy.ProxyClient;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class UpdateHandler extends BaseHandlerStd {
    private Logger log;

    @Override
    public ProgressEvent<ResourceModel, CallbackContext> handleRequest(
        final AmazonWebServicesClientProxy awsClientProxy,
        final ResourceHandlerRequest<ResourceModel> request,
        final CallbackContext callbackContext,
        final ProxyClient<OrganizationsClient> orgsClient,
        final Logger logger) {

        this.log = logger;
        final ResourceModel previousModel = request.getPreviousResourceState();
        final ResourceModel model = request.getDesiredResourceState();

        String policyId = model.getId();

        if (previousModel == null || previousModel.getId() == null || !policyId.equals(previousModel.getId())) {
            return ProgressEvent.failed(model, callbackContext, HandlerErrorCode.NotUpdatable,
                "Policy cannot be updated as the id was changed!");
        }

        if (!previousModel.getType().equalsIgnoreCase(model.getType())) {
            return ProgressEvent.failed(model, callbackContext, HandlerErrorCode.NotUpdatable,
                "Cannot update policy type after creation!");
        }

        // call UpdatePolicy API
        logger.log(String.format("Requesting UpdatePolicy w/ id: %s", policyId));
        return ProgressEvent.progress(model, callbackContext)
            .then(progress ->
                awsClientProxy.initiate("AWS-Organizations-Policy::UpdatePolicy", orgsClient, progress.getResourceModel(), progress.getCallbackContext())
                    .translateToServiceRequest(Translator::translateToUpdateRequest)
                    .makeServiceCall(this::updatePolicy)
                    .handleError((organizationsRequest, e, orgsClient1, model1, context) -> handleError(
                        organizationsRequest, e, orgsClient1, model1, context, logger))
                    .progress()
            )
            .then(progress -> handleTargets(request, awsClientProxy, model, callbackContext, request.getDesiredResourceState().getTargetIds(), request.getPreviousResourceState().getTargetIds(), policyId, orgsClient, logger))
            .then(progress -> handleTagging(awsClientProxy, model, callbackContext, request.getDesiredResourceState().getTags(), request.getPreviousResourceState().getTags(), policyId, orgsClient, logger))
            .then(progress -> new ReadHandler().handleRequest(awsClientProxy, request, callbackContext, orgsClient, logger));
    }

    protected UpdatePolicyResponse updatePolicy(final UpdatePolicyRequest updatePolicyRequest, final ProxyClient<OrganizationsClient> orgsClient) {
        log.log("Calling updatePolicy API.");
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
        final Logger logger
    ) {
        // filter previous and desired lists to determine which to attach and remove
        final List<String> targetsToAttach = new ArrayList<>();
        for (String desired : desiredTargets) {
            if (!previousTargets.contains(desired)) {
                targetsToAttach.add(desired);
            }
        }

        final List<String> targetsToRemove = new ArrayList<>();
        for (String previous : previousTargets) {
            if (!desiredTargets.contains(previous)) {
                targetsToRemove.add(previous);
            }
        }

        // make the calls to attach to new targets
        if (!CollectionUtils.isNullOrEmpty(targetsToAttach)) {
            for (String attachTargetId : targetsToAttach) {
                logger.log(String.format("Calling attachPolicy API with targetId: [%s]", attachTargetId));
                AttachPolicyRequest attachPolicyRequest = Translator.translateToAttachRequest(policyId, attachTargetId);
                try {
                    awsClientProxy.injectCredentialsAndInvokeV2(attachPolicyRequest, orgsClient.client()::attachPolicy);
                } catch (Exception e) {
                    if (e instanceof DuplicatePolicyAttachmentException) {
                        logger.log(String.format("Got %s when calling attachPolicy for "
                            + "policyId [%s], targetId [%s]. Continuing with update...",
                            e.getClass().getName(), policyId, attachTargetId));
                    } else {
                        return handleError(attachPolicyRequest, e, orgsClient, model, callbackContext, logger);
                    }
                }
            }
        }

        // make calls to detach from old targets
        if (!CollectionUtils.isNullOrEmpty(targetsToRemove)) {
            for (String removeTargetId : targetsToRemove) {
                logger.log(String.format("Calling detachPolicy API with targetId: [%s]", removeTargetId));
                DetachPolicyRequest detachPolicyRequest = Translator.translateToDetachRequest(policyId, removeTargetId);
                try {
                    awsClientProxy.injectCredentialsAndInvokeV2(detachPolicyRequest, orgsClient.client()::detachPolicy);
                } catch (Exception e) {
                    if (e instanceof PolicyNotAttachedException) {
                        logger.log(String.format("Got %s when calling detachPolicy for "
                            + "policyId [%s], targetId [%s]. Continuing with update...",
                            e.getClass().getName(), policyId, removeTargetId));
                    } else {
                        return handleError(detachPolicyRequest, e, orgsClient, model, callbackContext, logger);
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
            final Set<software.amazon.organizations.policy.Tag> desiredTags,
            final Set<software.amazon.organizations.policy.Tag> previousTags,
            final String policyId,
            final ProxyClient<OrganizationsClient> orgsClient,
            final Logger logger
    ) {
        final Set<Tag> newTags = desiredTags == null ? Collections.emptySet() :
            convertPolicyTagToOrganizationTag(desiredTags);

        final Set<Tag> existingTags = previousTags == null ? Collections.emptySet() :
            convertPolicyTagToOrganizationTag(previousTags);

        // Includes all old tags that do not exist in new tag list
        final List<String> tagsToRemove = getTagsToRemove(existingTags, newTags);

        // Excluded all old tags that do exist in new tag list
        final Collection<Tag> tagsToAddOrUpdate = getTagsToAddOrUpdate(existingTags, newTags);

        // Delete tags only if tagsToRemove is not empty
        if (!CollectionUtils.isNullOrEmpty(tagsToRemove)) {
            logger.log("Calling untagResource API.");
            UntagResourceRequest untagResourceRequest = Translator.translateToUntagResourceRequest(tagsToRemove, policyId);
            try {
                awsClientProxy.injectCredentialsAndInvokeV2(untagResourceRequest, orgsClient.client()::untagResource);
            } catch (Exception e) {
                return handleError(untagResourceRequest, e, orgsClient, model, callbackContext, logger);
            }
        }

        // Add tags only if tagsToAddOrUpdate is not empty.
        if (!CollectionUtils.isNullOrEmpty(tagsToAddOrUpdate)) {
            logger.log("Calling tagResource API.");
            TagResourceRequest tagResourceRequest = Translator.translateToTagResourceRequest(tagsToAddOrUpdate, policyId);
            try {
                awsClientProxy.injectCredentialsAndInvokeV2(tagResourceRequest, orgsClient.client()::tagResource);
            } catch (Exception e) {
                return handleError(tagResourceRequest, e, orgsClient, model, callbackContext, logger);
            }
        }

        return ProgressEvent.progress(model, callbackContext);
    }

    static Set<Tag> convertPolicyTagToOrganizationTag(final Set<software.amazon.organizations.policy.Tag> tags) {
        final Set<Tag> tagsToReturn = new HashSet<>();
        for (software.amazon.organizations.policy.Tag inputTag : tags) {
            Tag tag = Tag.builder()
                .key(inputTag.getKey())
                .value(inputTag.getValue())
                .build();
            tagsToReturn.add(tag);
        }
        return tagsToReturn;
    }

    static List<String> getTagsToRemove(Set<Tag> existingTags, Set<Tag> newTags) {
        List<String> tagsToRemove = new ArrayList<>();

        Set<String> newTagsKeys = new HashSet<>();
        for (Tag tag : newTags) {
            newTagsKeys.add(tag.key());
        }

        // Check if the existingTag key is not in newTags keys. If so add that key to the list of those to remove
        for (Tag tag : existingTags) {
            if (!newTagsKeys.contains(tag.key())) {
                tagsToRemove.add(tag.key());
            }
        }

        return tagsToRemove;
    }

    static Collection<Tag> getTagsToAddOrUpdate(Set<Tag> existingTags, Set<Tag> newTags) {
        Collection<Tag> tagsToAddOrUpdate = new ArrayList<>();

        HashMap<String, Tag> keyToExistingTag = new HashMap<>();
        for (Tag tag : existingTags) {
            keyToExistingTag.put(tag.key(), tag);
        }

        HashMap<String, Tag> keyToNewTag = new HashMap<>();
        for (Tag tag : newTags) {
            keyToNewTag.put(tag.key(), tag);
        }

        // Find the new keys and add corresponding tag
        for (String key : keyToNewTag.keySet()) {
            if (!keyToExistingTag.containsKey(key)) {
                tagsToAddOrUpdate.add(keyToNewTag.get(key));
            }
        }

        // Find the keys w/ different values and add corresponding tag
        for (String key : keyToNewTag.keySet()) {
            if (keyToExistingTag.containsKey(key)) {
                if (!keyToNewTag.get(key).value().equals(keyToExistingTag.get(key).value())) {
                    tagsToAddOrUpdate.add(keyToNewTag.get(key));
                }
            }
        }

        return tagsToAddOrUpdate;
    }
}
