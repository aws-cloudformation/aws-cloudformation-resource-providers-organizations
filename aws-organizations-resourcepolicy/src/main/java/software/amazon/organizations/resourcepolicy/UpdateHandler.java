package software.amazon.organizations.resourcepolicy;

import software.amazon.awssdk.services.organizations.OrganizationsClient;
import software.amazon.awssdk.services.organizations.model.Tag;
import software.amazon.awssdk.services.organizations.model.TagResourceRequest;
import software.amazon.awssdk.services.organizations.model.UntagResourceRequest;
import software.amazon.awssdk.services.organizations.model.PutResourcePolicyRequest;
import software.amazon.awssdk.services.organizations.model.PutResourcePolicyResponse;
import software.amazon.cloudformation.exceptions.CfnInvalidRequestException;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.HandlerErrorCode;
import software.amazon.cloudformation.proxy.ProgressEvent;
import software.amazon.cloudformation.proxy.ProxyClient;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;
import software.amazon.organizations.utils.OrgsLoggerWrapper;

import java.util.Set;

public class UpdateHandler extends BaseHandlerStd {
    private OrgsLoggerWrapper log;

    protected ProgressEvent<ResourceModel, CallbackContext> handleRequest(
        final AmazonWebServicesClientProxy awsClientProxy,
        final ResourceHandlerRequest<ResourceModel> request,
        final CallbackContext callbackContext,
        final ProxyClient<OrganizationsClient> orgsClient,
        final OrgsLoggerWrapper logger) {

        this.log = logger;
        final ResourceModel previousModel = request.getPreviousResourceState();
        final ResourceModel model = request.getDesiredResourceState();

        String resourcePolicyId = model.getId();

        if (previousModel == null) {
            return ProgressEvent.failed(model, callbackContext, HandlerErrorCode.NotFound,
                String.format("ResourcePolicy [%s] cannot be updated as no previous ResourcePolicy exists.", resourcePolicyId));
        } else if (model.getContent() == null) {
            logger.log(String.format("ResourcePolicy [%s] update does not include Content. This is an InvalidRequest [%s]", resourcePolicyId, request.getAwsAccountId()));
            return ProgressEvent.failed(model, callbackContext, HandlerErrorCode.InvalidRequest,
                "ResourcePolicy cannot be updated without Content!");
        }

        String content;
        try {
            content = Translator.convertObjectToString(model.getContent());
        } catch (CfnInvalidRequestException e){
            logger.log(String.format("The ResourcePolicy content did not include a valid JSON. This is an InvalidRequest for management account Id [%s]", request.getAwsAccountId()));
            return ProgressEvent.failed(model, callbackContext, HandlerErrorCode.InvalidRequest,
                "ResourcePolicy content had invalid JSON!");
        }

        Set<software.amazon.organizations.resourcepolicy.Tag> previousTags = previousModel.getTags();
        Set<Tag> allPreviousTags = TagsHelper.mergeTags(
                TagsHelper.convertResourcePolicyTagToOrganizationTag(previousTags),
                request.getPreviousResourceTags());

        Set<software.amazon.organizations.resourcepolicy.Tag> newTags = model.getTags();
        Set<Tag> allNewTags = TagsHelper.mergeTags(
                TagsHelper.convertResourcePolicyTagToOrganizationTag(newTags),
                request.getDesiredResourceTags());

        return ProgressEvent.progress(model, callbackContext)
            .then(progress -> {
                logger.log(String.format("Requesting PutResourcePolicy w/ content: %s and management account Id [%s]", content, request.getAwsAccountId()));
                return awsClientProxy.initiate("AWS-Organizations-ResourcePolicy::UpdateResourcePolicy", orgsClient, progress.getResourceModel(), progress.getCallbackContext())
                    .translateToServiceRequest(Translator::translateToUpdateRequest)
                    .makeServiceCall(this::putResourcePolicy)
                    .handleError((organizationsRequest, e, proxyClient1, model1, context) ->
                                    handleErrorInGeneral(organizationsRequest, e, proxyClient1, model1, context, logger, ResourcePolicyConstants.Action.UPDATE_RESOURCEPOLICY, ResourcePolicyConstants.Handler.UPDATE))
                    .done(putResourcePolicyResponse -> {
                        logger.log(String.format("Updated ResourcePolicy [%s].", putResourcePolicyResponse.resourcePolicy().resourcePolicySummary().id()));
                        return ProgressEvent.progress(model, callbackContext);
                    });
            })
            .then(progress -> handleTagging(awsClientProxy, model, callbackContext, allNewTags, allPreviousTags, resourcePolicyId, orgsClient, logger))
            .then(progress -> new ReadHandler().handleRequest(awsClientProxy, request, callbackContext, orgsClient, logger));
    }

    private ProgressEvent<ResourceModel, CallbackContext> handleTagging(
            final AmazonWebServicesClientProxy awsClientProxy,
            final ResourceModel model,
            final CallbackContext callbackContext,
            final Set<Tag> newTags,
            final Set<Tag> previousTags,
            final String resourcePolicyId,
            final ProxyClient<OrganizationsClient> orgsClient,
            final OrgsLoggerWrapper logger
    ) {
        // Includes all old tags that do not exist in new tag list
        final Set<String> tagsToRemove = TagsHelper.getTagKeysToRemove(previousTags, newTags);

        // Excluded all old tags that do exist in new tag list
        final Set<Tag> tagsToAddOrUpdate = TagsHelper.getTagsToAddOrUpdate(previousTags, newTags);

        // Delete tags only if tagsToRemove is not empty
        if (!tagsToRemove.isEmpty()) {
            logger.log(String.format("Calling untagResource API for ResourcePolicy [%s].", model.getId()));
            UntagResourceRequest untagResourceRequest = Translator.translateToUntagResourceRequest(tagsToRemove, resourcePolicyId);
            try {
                awsClientProxy.injectCredentialsAndInvokeV2(untagResourceRequest, orgsClient.client()::untagResource);
            } catch (Exception e) {
                return handleErrorInGeneral(untagResourceRequest, e, orgsClient, model, callbackContext, logger, ResourcePolicyConstants.Action.UNTAG_RESOURCE, ResourcePolicyConstants.Handler.UPDATE);
            }
        }

        // Add tags only if tagsToAddOrUpdate is not empty.
        if (!tagsToAddOrUpdate.isEmpty()) {
            logger.log(String.format("Calling tagResource API for ResourcePolicy [%s].", model.getId()));
            TagResourceRequest tagResourceRequest = Translator.translateToTagResourceRequest(tagsToAddOrUpdate, resourcePolicyId);
            try {
                awsClientProxy.injectCredentialsAndInvokeV2(tagResourceRequest, orgsClient.client()::tagResource);
            } catch (Exception e) {
                return handleErrorInGeneral(tagResourceRequest, e, orgsClient, model, callbackContext, logger, ResourcePolicyConstants.Action.TAG_RESOURCE, ResourcePolicyConstants.Handler.UPDATE);
            }
        }
        return ProgressEvent.progress(model, callbackContext);
    }

    protected PutResourcePolicyResponse putResourcePolicy(final PutResourcePolicyRequest putResourcePolicyRequest, final ProxyClient<OrganizationsClient> orgsClient) {
        log.log(String.format("Calling putResourcePolicy API for ResourcePolicy content [%s].", putResourcePolicyRequest.content()));
        return orgsClient.injectCredentialsAndInvokeV2(putResourcePolicyRequest, orgsClient.client()::putResourcePolicy);
    }

}
