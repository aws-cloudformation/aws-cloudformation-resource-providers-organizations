package software.amazon.organizations.policy;

import software.amazon.awssdk.services.organizations.OrganizationsClient;

import software.amazon.awssdk.services.organizations.model.AccessDeniedException;
import software.amazon.awssdk.services.organizations.model.AwsOrganizationsNotInUseException;
import software.amazon.awssdk.services.organizations.model.ConcurrentModificationException;
import software.amazon.awssdk.services.organizations.model.ConstraintViolationException;
import software.amazon.awssdk.services.organizations.model.DuplicatePolicyAttachmentException;
import software.amazon.awssdk.services.organizations.model.DuplicatePolicyException;
import software.amazon.awssdk.services.organizations.model.InvalidInputException;
import software.amazon.awssdk.services.organizations.model.MalformedPolicyDocumentException;
import software.amazon.awssdk.services.organizations.model.OrganizationsRequest;
import software.amazon.awssdk.services.organizations.model.PolicyChangesInProgressException;
import software.amazon.awssdk.services.organizations.model.PolicyInUseException;
import software.amazon.awssdk.services.organizations.model.PolicyNotAttachedException;
import software.amazon.awssdk.services.organizations.model.PolicyNotFoundException;
import software.amazon.awssdk.services.organizations.model.PolicyTypeNotAvailableForOrganizationException;
import software.amazon.awssdk.services.organizations.model.PolicyTypeNotEnabledException;
import software.amazon.awssdk.services.organizations.model.ServiceException;
import software.amazon.awssdk.services.organizations.model.TargetNotFoundException;
import software.amazon.awssdk.services.organizations.model.TooManyRequestsException;

import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.Logger;
import software.amazon.cloudformation.proxy.ProgressEvent;
import software.amazon.cloudformation.proxy.ProxyClient;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;
import software.amazon.cloudformation.proxy.HandlerErrorCode;

import java.util.Random;


// Placeholder for the functionality that could be shared across Create/Read/Update/Delete/List Handlers

public abstract class BaseHandlerStd extends BaseHandler<CallbackContext> {
    // ExponentialBackoffJitter Constants
    private final double RANDOMIZATION_FACTOR = 0.5;
    private final int BASE_DELAY = 3;
    private final int MAX_RETRY_ATTEMPT_FOR_RETRIABLE_EXCEPTION = 3;
    final int MAX_RETRY_ATTEMPT_FOR_CREATE_POLICY = 3;

    @Override
    public final ProgressEvent<ResourceModel, CallbackContext> handleRequest(
        final AmazonWebServicesClientProxy proxy,
        final ResourceHandlerRequest<ResourceModel> request,
        final CallbackContext callbackContext,
        final Logger logger) {
        return handleRequest(
            proxy,
            request,
            callbackContext != null ? callbackContext : new CallbackContext(),
            proxy.newProxy(ClientBuilder::getClient),
            logger
        );
    }

    protected abstract ProgressEvent<ResourceModel, CallbackContext> handleRequest(
        final AmazonWebServicesClientProxy proxy,
        final ResourceHandlerRequest<ResourceModel> request,
        final CallbackContext callbackContext,
        final ProxyClient<OrganizationsClient> proxyClient,
        final Logger logger
    );

    public ProgressEvent<ResourceModel, CallbackContext> handleError(
        final OrganizationsRequest request,
        final Exception e,
        final ProxyClient<OrganizationsClient> proxyClient,
        final ResourceModel resourceModel,
        final CallbackContext callbackContext,
        final Logger logger
    ) {
        HandlerErrorCode errorCode = HandlerErrorCode.GeneralServiceException;
        if (e instanceof DuplicatePolicyException || e instanceof DuplicatePolicyAttachmentException) {
            errorCode = HandlerErrorCode.AlreadyExists;
        } else if (e instanceof AwsOrganizationsNotInUseException || e instanceof PolicyNotFoundException
            || e instanceof TargetNotFoundException || e instanceof PolicyNotAttachedException) {
            errorCode = HandlerErrorCode.NotFound;
        } else if (e instanceof AccessDeniedException) {
            errorCode = HandlerErrorCode.AccessDenied;
        } else if (e instanceof ConcurrentModificationException || e instanceof PolicyChangesInProgressException) {
            errorCode = HandlerErrorCode.ResourceConflict;
        } else if (e instanceof ConstraintViolationException) {
            errorCode = HandlerErrorCode.ServiceLimitExceeded;
        } else if (e instanceof InvalidInputException || e instanceof MalformedPolicyDocumentException
            || e instanceof PolicyTypeNotAvailableForOrganizationException || e instanceof PolicyTypeNotEnabledException
            || e instanceof PolicyInUseException) {
            errorCode = HandlerErrorCode.InvalidRequest;
        } else if (e instanceof ServiceException) {
            errorCode = HandlerErrorCode.ServiceInternalError;
        } else if (e instanceof TooManyRequestsException) {
            errorCode = HandlerErrorCode.Throttling;
        }
        String policyInfo = resourceModel.getId() == null ? resourceModel.getName() : resourceModel.getId();
        logger.log(String.format("[Exception] Failed with exception: [%s]. Message: [%s], ErrorCode: [%s] for policy [%s].",
            e.getClass().getSimpleName(), e.getMessage(), errorCode, policyInfo));
        return ProgressEvent.failed(resourceModel, callbackContext, errorCode,e.getMessage());
    }

    public ProgressEvent<ResourceModel, CallbackContext> handleErrorInGeneral(
        final OrganizationsRequest request,
        final Exception e,
        final ProxyClient<OrganizationsClient> proxyClient,
        final ResourceModel resourceModel,
        final CallbackContext callbackContext,
        final Logger logger,
        final PolicyConstants.Action actionName,
        final PolicyConstants.Handler handlerName
    ) {
        if (isRetriableException(e)) {
            return handleRetriableException(request, proxyClient, callbackContext, logger, e, resourceModel, actionName, handlerName);
        }
        return handleError(request, e, proxyClient, resourceModel, callbackContext, logger);
    }

    public final int computeDelayBeforeNextRetry(int retryAttempt) {
        Random random = new Random();
        int exponentialBackoff = (int) Math.pow(2, retryAttempt) * BASE_DELAY;
        int jitter = random.nextInt((int) Math.ceil(exponentialBackoff * RANDOMIZATION_FACTOR));
        return exponentialBackoff + jitter;
    }

    public final boolean isRetriableException (Exception e){
        return (e instanceof ConcurrentModificationException
            || e instanceof PolicyChangesInProgressException
            || e instanceof TooManyRequestsException
            || e instanceof ServiceException);
    }

//    private int getCurrentAttempt(
//        final PolicyConstants.Action actionName,
//        final PolicyConstants.Handler handlerName,
//        final CallbackContext context
//    ){
//        if (handlerName == PolicyConstants.Handler.CREATE) {
////            if (actionName == PolicyConstants.Action.CREATE_POLICY) return context.getRetryCreatePolicyAttempt();
//            if (actionName == PolicyConstants.Action.ATTACH_POLICY) return context.getRetryAttachPolicyAttemptInCreate();
//        } else if (handlerName == PolicyConstants.Handler.UPDATE) {
//            if (actionName == PolicyConstants.Action.UPDATE_POLICY) return context.getRetryUpdatePolicyAttempt();
//            if (actionName == PolicyConstants.Action.ATTACH_POLICY) return context.getRetryAttachPolicyAttemptInUpdate();
//            if (actionName == PolicyConstants.Action.DETACH_POLICY) return context.getRetryDetachPolicyAttemptInUpdate();
//            if (actionName == PolicyConstants.Action.TAG_RESOURCE) return context.getRetryTagResourceAttemptInUpdate();
//            if (actionName == PolicyConstants.Action.UNTAG_RESOURCE) return context.getRetryUnTagResourceAttemptInUpdate();
//        } else if (handlerName == PolicyConstants.Handler.DELETE) {
//            if (actionName == PolicyConstants.Action.DELETE_POLICY) return context.getRetryDeletePolicyAttempt();
//            if (actionName == PolicyConstants.Action.DETACH_POLICY) return context.getRetryDetachPolicyAttemptInDelete();
//        } else if (handlerName == PolicyConstants.Handler.READ) {
//            if (actionName == PolicyConstants.Action.DESCRIBE_POLICY) return context.getRetryDescribePolicyAttemptInRead();
//            if (actionName == PolicyConstants.Action.LIST_TAGS_FOR_POLICY) return context.getRetryListTagsForPolicyAttemptInRead();
//            if (actionName == PolicyConstants.Action.LIST_TARGETS_FOR_POLICY) return context.getRetryListTargetsForPolicyAttemptInRead();
//        } else if (handlerName == PolicyConstants.Handler.LIST) {
//            if (actionName == PolicyConstants.Action.LIST_POLICIES) return context.getRetryListPoliciesAttemptInList();
//        }
//        throw new CfnGeneralServiceException("Error in getting current retry attempt from callback context!");
//    }
//
//    private CallbackContext setCurrentAttempt(
//        final PolicyConstants.Action actionName,
//        final PolicyConstants.Handler handlerName,
//        final CallbackContext context
//    ){
//        if (handlerName == PolicyConstants.Handler.CREATE) {
////            if (actionName == PolicyConstants.Action.CREATE_POLICY) context.setRetryCreatePolicyAttempt(context.getRetryCreatePolicyAttempt()+1);
//            if (actionName == PolicyConstants.Action.ATTACH_POLICY) context.setRetryAttachPolicyAttemptInCreate(context.getRetryAttachPolicyAttemptInCreate()+1);
//        } else if (handlerName == PolicyConstants.Handler.UPDATE) {
//            if (actionName == PolicyConstants.Action.UPDATE_POLICY) context.setRetryUpdatePolicyAttempt(context.getRetryUpdatePolicyAttempt()+1);
//            if (actionName == PolicyConstants.Action.ATTACH_POLICY) context.setRetryAttachPolicyAttemptInUpdate(context.getRetryAttachPolicyAttemptInUpdate()+1);
//            if (actionName == PolicyConstants.Action.DETACH_POLICY) context.setRetryDetachPolicyAttemptInUpdate(context.getRetryDetachPolicyAttemptInUpdate()+1);
//            if (actionName == PolicyConstants.Action.TAG_RESOURCE) context.setRetryTagResourceAttemptInUpdate(context.getRetryTagResourceAttemptInUpdate()+1);
//            if (actionName == PolicyConstants.Action.UNTAG_RESOURCE) context.setRetryUnTagResourceAttemptInUpdate(context.getRetryUnTagResourceAttemptInUpdate()+1);
//        } else if (handlerName == PolicyConstants.Handler.DELETE) {
//            if (actionName == PolicyConstants.Action.DELETE_POLICY) context.setRetryDeletePolicyAttempt(context.getRetryDeletePolicyAttempt()+1);
//            if (actionName == PolicyConstants.Action.DETACH_POLICY) context.setRetryDetachPolicyAttemptInDelete(context.getRetryDetachPolicyAttemptInDelete()+1);
//        } else if (handlerName == PolicyConstants.Handler.READ) {
//            if (actionName == PolicyConstants.Action.DESCRIBE_POLICY)  context.setRetryDescribePolicyAttemptInRead(context.getRetryDescribePolicyAttemptInRead()+1);
//            if (actionName == PolicyConstants.Action.LIST_TAGS_FOR_POLICY) context.setRetryListTagsForPolicyAttemptInRead(context.getRetryListTagsForPolicyAttemptInRead()+1);
//            if (actionName == PolicyConstants.Action.LIST_TARGETS_FOR_POLICY) context.setRetryListTargetsForPolicyAttemptInRead(context.getRetryListTargetsForPolicyAttemptInRead()+1);
//        } else if (handlerName == PolicyConstants.Handler.LIST) {
//            if (actionName == PolicyConstants.Action.LIST_POLICIES) context.setRetryListPoliciesAttemptInList(context.getRetryListPoliciesAttemptInList()+1);
//        }
//        return context;
//    }

    public final ProgressEvent<ResourceModel, CallbackContext> handleRetriableException(
        final OrganizationsRequest organizationsRequest,
        final ProxyClient<OrganizationsClient> proxyClient,
        final CallbackContext context,
        final Logger logger,
        final Exception e,
        final ResourceModel model,
        final PolicyConstants.Action actionName,
        final PolicyConstants.Handler handlerName
    ) {
        try {
            if (actionName != PolicyConstants.Action.CREATE_POLICY) {
                int currentAttempt = context.getCurrentRetryAttempt(actionName, handlerName);
                if (currentAttempt < MAX_RETRY_ATTEMPT_FOR_RETRIABLE_EXCEPTION) {
                    int callbackDelaySeconds = computeDelayBeforeNextRetry(currentAttempt);
                    context.setCurrentRetryAttempt(actionName, handlerName);
                    logger.log(String.format("Got %s when calling %s for "
                                                 + "policy [%s]. Retrying %s of %s with callback delay %s seconds.",
                        e.getClass().getName(), organizationsRequest.getClass().getName(), model.getName(), currentAttempt+1, MAX_RETRY_ATTEMPT_FOR_RETRIABLE_EXCEPTION, callbackDelaySeconds));
                    return ProgressEvent.defaultInProgressHandler(context,callbackDelaySeconds, model);
                } else {
                    logger.log(String.format("All retry attempts exhausted for policy [%s], return CloudFormation exception.", model.getName()));
                    return handleError(organizationsRequest, e, proxyClient, model, context, logger);
                }
            }
            return handleError(organizationsRequest, e, proxyClient, model, context, logger);
        } catch (Exception exception){
            return ProgressEvent.failed(model, context, HandlerErrorCode.GeneralServiceException, exception.getMessage());
        }
    }
}
