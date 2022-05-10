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

import software.amazon.cloudformation.exceptions.CfnGeneralServiceException;
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
    private final int BASE_DELAY = 5;

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
        logger.log(String.format("[Exception] Failed with exception: [%s]. Message: [%s], ErrorCode: [%s] for policy [%s].",
            e.getClass().getSimpleName(), e.getMessage(), errorCode, resourceModel.getName()));
        return ProgressEvent.failed(resourceModel, callbackContext, errorCode,e.getMessage());
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

    private int getCurrentAttempt(final PolicyConstants.Action actionName, final CallbackContext context){
        if (actionName == PolicyConstants.Action.DELETE_POLICY) {
            return context.getRetryDeleteAttempt();
        } else if (actionName == PolicyConstants.Action.ATTACH_POLICY) {
            return context.getRetryAttachAttempt();
        } else if (actionName == PolicyConstants.Action.DETACH_POLICY) {
            return context.getRetryDetachAttempt();
        } else {
            throw new CfnGeneralServiceException("Error in getting current retry attempt from callback context!");
        }
    }

    private CallbackContext setCurrentAttempt(final PolicyConstants.Action actionName, final CallbackContext context){
        if (actionName == PolicyConstants.Action.DELETE_POLICY) {
            context.setRetryDeleteAttempt(context.getRetryDeleteAttempt() + 1);
        } else if (actionName == PolicyConstants.Action.ATTACH_POLICY) {
            context.setRetryAttachAttempt(context.getRetryAttachAttempt() + 1);
        } else if (actionName == PolicyConstants.Action.DETACH_POLICY) {
            context.setRetryDetachAttempt(context.getRetryDetachAttempt() + 1);
        }
        return context;
    }

    public final ProgressEvent<ResourceModel, CallbackContext> handleRetriableException(
        final OrganizationsRequest organizationsRequest,
        final ProxyClient<OrganizationsClient> proxyClient,
        final CallbackContext context,
        final Logger logger,
        final Exception e,
        final ResourceModel model,
        final PolicyConstants.Action actionName
    ) {
        try {
            final int currentAttempt = getCurrentAttempt(actionName,context);
            if (currentAttempt < context.getMaxRetryCount()) {
                int callbackDelaySeconds = computeDelayBeforeNextRetry(currentAttempt);
                logger.log(String.format("Got %s when calling %s for "
                                             + "policy [%s]. Retrying %s of %s with callback delay %s seconds.",
                    e.getClass().getName(), organizationsRequest.getClass().getName(), model.getName(), currentAttempt+1, context.getMaxRetryCount(), callbackDelaySeconds));
                setCurrentAttempt(actionName,context);
                return ProgressEvent.defaultInProgressHandler(context,callbackDelaySeconds, model);
            } else {
                logger.log(String.format("All retry attempts exhausted for policy [%s], return exception to CloudFormation for further handling.", model.getName()));
                return handleError(organizationsRequest, e, proxyClient, model, context, logger);
            }
        } catch (Exception exception){
            return ProgressEvent.failed(model, context, HandlerErrorCode.GeneralServiceException, exception.getMessage());
        }
    }
}
