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
import software.amazon.awssdk.services.organizations.model.UnsupportedApiEndpointException;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.HandlerErrorCode;
import software.amazon.cloudformation.proxy.Logger;
import software.amazon.cloudformation.proxy.ProgressEvent;
import software.amazon.cloudformation.proxy.ProxyClient;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;

import java.util.Random;


// Placeholder for the functionality that could be shared across Create/Read/Update/Delete/List Handlers

public abstract class BaseHandlerStd extends BaseHandler<CallbackContext> {
    // ExponentialBackoffJitter Constants
    private final double RANDOMIZATION_FACTOR = 0.5;
    private final int BASE_DELAY = 15; // in seconds
    private final int MAX_RETRY_ATTEMPT_FOR_RETRIABLE_EXCEPTION = 2;

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
        if (e instanceof DuplicatePolicyException) {
            errorCode = HandlerErrorCode.AlreadyExists;
        } else if (e instanceof AwsOrganizationsNotInUseException || e instanceof PolicyNotFoundException
            || e instanceof TargetNotFoundException) {
            errorCode = HandlerErrorCode.NotFound;
        } else if (e instanceof AccessDeniedException) {
            errorCode = HandlerErrorCode.AccessDenied;
        } else if (e instanceof ConcurrentModificationException || e instanceof PolicyChangesInProgressException) {
            errorCode = HandlerErrorCode.ResourceConflict;
        } else if (e instanceof ConstraintViolationException) {
            errorCode = HandlerErrorCode.ServiceLimitExceeded;
        } else if (e instanceof InvalidInputException || e instanceof MalformedPolicyDocumentException
            || e instanceof PolicyTypeNotAvailableForOrganizationException || e instanceof PolicyTypeNotEnabledException
            || e instanceof PolicyInUseException || e instanceof UnsupportedApiEndpointException) {
            errorCode = HandlerErrorCode.InvalidRequest;
        } else if (e instanceof ServiceException) {
            errorCode = HandlerErrorCode.ServiceInternalError;
        } else if (e instanceof TooManyRequestsException) {
            errorCode = HandlerErrorCode.Throttling;
        } else if (e instanceof DuplicatePolicyAttachmentException || e instanceof PolicyNotAttachedException) {
            errorCode = HandlerErrorCode.InternalFailure;
        }
        String policyInfo = resourceModel.getId() == null ? resourceModel.getName() : resourceModel.getId();
        logger.log(String.format("[Exception] Failed with exception: [%s]. Message: [%s], ErrorCode: [%s] for policy [%s].",
            e.getClass().getSimpleName(), e.getMessage(), errorCode, policyInfo));
        return ProgressEvent.failed(resourceModel, callbackContext, errorCode,e.getMessage());
    }

    // combines handle of retriable exception and non-retriable exception
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
        if ((handlerName != PolicyConstants.Handler.READ && handlerName != PolicyConstants.Handler.LIST)
            && isRetriableException(e)) {
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
        if (actionName != PolicyConstants.Action.CREATE_POLICY) {
            int currentAttempt = context.getCurrentRetryAttempt(actionName, handlerName);
            if (currentAttempt < MAX_RETRY_ATTEMPT_FOR_RETRIABLE_EXCEPTION) {
                context.setCurrentRetryAttempt(actionName, handlerName);
                int callbackDelaySeconds = computeDelayBeforeNextRetry(currentAttempt);
                logger.log(String.format("Got %s when calling %s for "
                                + "policy [%s]. Retrying %s of %s with callback delay %s seconds.",
                        e.getClass().getName(), organizationsRequest.getClass().getName(), model.getName(), currentAttempt + 1, MAX_RETRY_ATTEMPT_FOR_RETRIABLE_EXCEPTION, callbackDelaySeconds));
                return ProgressEvent.defaultInProgressHandler(context, callbackDelaySeconds, model);
            }
        }
        logger.log(String.format("All retry attempts exhausted for policy [%s], return CloudFormation exception.", model.getName()));
        return handleError(organizationsRequest, e, proxyClient, model, context, logger);
    }
}
