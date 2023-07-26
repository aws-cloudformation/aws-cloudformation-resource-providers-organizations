package software.amazon.organizations.account;

import com.google.common.collect.Sets;
import org.apache.commons.lang3.StringUtils;
import software.amazon.awssdk.services.organizations.OrganizationsClient;
import software.amazon.awssdk.services.organizations.model.DuplicateAccountException;
import software.amazon.awssdk.services.organizations.model.InvalidInputException;
import software.amazon.awssdk.services.organizations.model.ListRootsRequest;
import software.amazon.awssdk.services.organizations.model.ListRootsResponse;
import software.amazon.awssdk.services.organizations.model.MoveAccountRequest;
import software.amazon.awssdk.services.organizations.model.MoveAccountResponse;
import software.amazon.awssdk.services.organizations.model.SourceParentNotFoundException;
import software.amazon.awssdk.services.organizations.model.Tag;
import software.amazon.awssdk.services.organizations.model.TagResourceRequest;
import software.amazon.awssdk.services.organizations.model.UntagResourceRequest;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.HandlerErrorCode;
import software.amazon.cloudformation.proxy.ProgressEvent;
import software.amazon.cloudformation.proxy.ProxyClient;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;
import software.amazon.organizations.utils.OrgsLoggerWrapper;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

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

        if (!StringUtils.equalsIgnoreCase(model.getRoleName(), previousModel.getRoleName())) {
            return ProgressEvent.failed(model, callbackContext, HandlerErrorCode.InvalidRequest,
                "You cannot update IAM role name.");
        }
        else if (!StringUtils.equalsIgnoreCase(model.getAccountName(), previousModel.getAccountName())
                || !StringUtils.equalsIgnoreCase(model.getEmail(), previousModel.getEmail())) {
            return ProgressEvent.failed(model, callbackContext, HandlerErrorCode.InvalidRequest,
                "To modify the account name or account email attributes, you must sign in as the root user and modify the values on the account settings page in the AWS Management Console.");
        }

        logger.log(String.format("Requesting Account Update w/ id: %s", model.getAccountId()));
        return ProgressEvent.progress(model, callbackContext)
                .then(progress -> moveAccount(awsClientProxy, request, previousModel, model, callbackContext, orgsClient, logger))
                .then(progress -> handleTagging(awsClientProxy, request, model, callbackContext,
                        convertAccountTagToOrganizationTag(model.getTags()),
                        convertAccountTagToOrganizationTag(previousModel.getTags()),
                        model.getAccountId(), orgsClient, logger))
                .then(progress -> new ReadHandler().handleRequest(awsClientProxy, request, callbackContext, orgsClient, logger));
    }

    protected ProgressEvent<ResourceModel, CallbackContext> moveAccount(
            final AmazonWebServicesClientProxy awsClientProxy,
            final ResourceHandlerRequest<ResourceModel> request,
            final ResourceModel previousModel,
            final ResourceModel model,
            final CallbackContext callbackContext,
            final ProxyClient<OrganizationsClient> orgsClient,
            final OrgsLoggerWrapper logger) {

        Set<String> previousParentIds = previousModel.getParentIds();
        Set<String> parentIds = model.getParentIds();
        String accountId = model.getAccountId();
        String rootID = null;

        if (previousParentIds != null ^ parentIds != null) {
            logger.log(String.format("%s is missing a parentId for account [%s]. Retrieving root as parent", previousParentIds == null ? "Previous model" : "New model", accountId));
            ListRootsRequest listRootsRequest = Translator.translateToListRootsRequest();
            ListRootsResponse listRootsResponse = awsClientProxy.injectCredentialsAndInvokeV2(listRootsRequest, orgsClient.client()::listRoots);
            rootID = listRootsResponse.roots().iterator().next().id();
        }
        else if ((previousParentIds == null && parentIds == null) || (previousParentIds != null && previousParentIds.equals(parentIds))) {
            logger.log(String.format("Updated parent id is the same for account [%s]. Skip move account.", accountId));
            return ProgressEvent.progress(model, callbackContext);
        }

        // currently only support 1 parent id
        if (parentIds != null && parentIds.size() > 1) {
            String errorMessage = String.format("Can not specify more than one parent id in request for account [%s].", accountId);
            logger.log(errorMessage);
            return ProgressEvent.failed(model, callbackContext, HandlerErrorCode.InvalidRequest, errorMessage);
        }

        final String sourceId = (previousParentIds == null) ? rootID: previousParentIds.iterator().next();
        final String destinationId = (parentIds == null) ? rootID: parentIds.iterator().next();
        return ProgressEvent.progress(model, callbackContext)
                .then(progress ->
                        awsClientProxy.initiate("AWS-Organizations-Account::MoveAccount", orgsClient, progress.getResourceModel(), progress.getCallbackContext())
                                .translateToServiceRequest((moveAccountRequest) -> Translator.translateToMoveAccountRequest(model, destinationId, sourceId))
                                .makeServiceCall(this::moveAccount)
                                .handleError((organizationsRequest, e, proxyClient1, model1, context) -> {
                                    if (e instanceof DuplicateAccountException) {
                                        logger.log(String.format("Got %s when calling %s for "
                                                        + "account id [%s], source id [%s], destination id [%s]. Continue with next step.",
                                                e.getClass().getName(), organizationsRequest.getClass().getName(), model.getAccountId(), sourceId, destinationId));
                                        return ProgressEvent.progress(model1, context);
                                    } else if (e instanceof SourceParentNotFoundException) {
                                        logger.log(String.format("Got %s when calling %s for "
                                                        + "account id [%s], source id [%s], destination id [%s]. Translating to InvalidInputException.",
                                                e.getClass().getName(), organizationsRequest.getClass().getName(), model.getAccountId(), sourceId, destinationId));
                                        InvalidInputException translatedException = InvalidInputException.builder()
                                            .message(e.getMessage())
                                            .build();
                                        return handleErrorInGeneral(organizationsRequest, request, translatedException, proxyClient1, model1, context, logger, AccountConstants.Action.MOVE_ACCOUNT, AccountConstants.Handler.UPDATE);
                                    } else {
                                        return handleErrorInGeneral(organizationsRequest, request, e, proxyClient1, model1, context, logger, AccountConstants.Action.MOVE_ACCOUNT, AccountConstants.Handler.UPDATE);
                                    }
                                })
                                .progress()
                );
    }

    private ProgressEvent<ResourceModel, CallbackContext> handleTagging(
            final AmazonWebServicesClientProxy awsClientProxy,
            final ResourceHandlerRequest<ResourceModel> request,
            final ResourceModel model,
            final CallbackContext callbackContext,
            final Set<Tag> desiredTags,
            final Set<Tag> previousTags,
            final String accountId,
            final ProxyClient<OrganizationsClient> orgsClient,
            final OrgsLoggerWrapper logger
    ) {
        // Includes all old tags that do not exist in new tag list
        final Set<String> tagsToRemove = getTagKeysToRemove(previousTags, desiredTags);

        // Excluded all old tags that do exist in new tag list
        final Set<Tag> tagsToAddOrUpdate = getTagsToAddOrUpdate(previousTags, desiredTags);

        // Delete tags only if tagsToRemove is not empty
        if (!tagsToRemove.isEmpty()) {
            logger.log(String.format("Calling untagResource API for Account [%s].", model.getAccountId()));
            UntagResourceRequest untagResourceRequest = Translator.translateToUntagResourceRequest(tagsToRemove, accountId);
            try {
                awsClientProxy.injectCredentialsAndInvokeV2(untagResourceRequest, orgsClient.client()::untagResource);
            } catch (Exception e) {
                return handleErrorInGeneral(untagResourceRequest, request, e, orgsClient, model, callbackContext, logger, AccountConstants.Action.UNTAG_RESOURCE, AccountConstants.Handler.UPDATE);
            }
        }

        // Add tags only if tagsToAddOrUpdate is not empty.
        if (!tagsToAddOrUpdate.isEmpty()) {
            logger.log(String.format("Calling tagResource API for Account [%s].", model.getAccountId()));
            TagResourceRequest tagResourceRequest = Translator.translateToTagResourceRequest(tagsToAddOrUpdate, accountId);
            try {
                awsClientProxy.injectCredentialsAndInvokeV2(tagResourceRequest, orgsClient.client()::tagResource);
            } catch (Exception e) {
                return handleErrorInGeneral(tagResourceRequest, request, e, orgsClient, model, callbackContext, logger, AccountConstants.Action.TAG_RESOURCE, AccountConstants.Handler.UPDATE);
            }
        }

        return ProgressEvent.progress(model, callbackContext);
    }

    static Set<software.amazon.awssdk.services.organizations.model.Tag> convertAccountTagToOrganizationTag(final Set<software.amazon.organizations.account.Tag> tags) {
        Set<software.amazon.awssdk.services.organizations.model.Tag> tagsToReturn = new HashSet<>();
        if (tags == null) return tagsToReturn;
        for (software.amazon.organizations.account.Tag inputTag : tags) {
            software.amazon.awssdk.services.organizations.model.Tag tag = software.amazon.awssdk.services.organizations.model.Tag.builder()
                    .key(inputTag.getKey())
                    .value(inputTag.getValue())
                    .build();
            tagsToReturn.add(tag);
        }
        return tagsToReturn;
    }

    private Set<String> getTagKeysToRemove(
            Set<Tag> oldTags, Set<Tag> newTags) {
        final Set<String> oldTagKeys = getTagKeySet(oldTags);
        final Set<String> newTagKeys = getTagKeySet(newTags);
        return Sets.difference(oldTagKeys, newTagKeys);
    }

    private Set<String> getTagKeySet(Set<Tag> oldTags) {
        return oldTags.stream().map(Tag::key).collect(Collectors.toSet());
    }

    private Set<Tag> getTagsToAddOrUpdate(
            Set<Tag> oldTags, Set<Tag> newTags) {
        return Sets.difference(newTags, oldTags);
    }

    protected MoveAccountResponse moveAccount(final MoveAccountRequest moveAccountRequest, final ProxyClient<OrganizationsClient> orgsClient) {
        log.log(String.format("Calling moveAccount API for Account [%s] with destinationId [%s],  sourceId [%s].", moveAccountRequest.accountId(), moveAccountRequest.destinationParentId(), moveAccountRequest.sourceParentId()));
        return orgsClient.injectCredentialsAndInvokeV2(moveAccountRequest, orgsClient.client()::moveAccount);
    }
}
