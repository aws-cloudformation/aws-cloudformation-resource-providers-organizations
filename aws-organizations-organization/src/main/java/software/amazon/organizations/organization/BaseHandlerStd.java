package software.amazon.organizations.organization;

import software.amazon.awssdk.services.organizations.OrganizationsClient;

import software.amazon.awssdk.services.organizations.model.AccessDeniedException;
import software.amazon.awssdk.services.organizations.model.AccessDeniedForDependencyException;
import software.amazon.awssdk.services.organizations.model.ConcurrentModificationException;
import software.amazon.awssdk.services.organizations.model.ConstraintViolationException;
import software.amazon.awssdk.services.organizations.model.InvalidInputException;
import software.amazon.awssdk.services.organizations.model.OrganizationNotEmptyException;
import software.amazon.awssdk.services.organizations.model.ServiceException;
import software.amazon.awssdk.services.organizations.model.TooManyRequestsException;
import software.amazon.awssdk.services.organizations.model.AlreadyInOrganizationException;
import software.amazon.awssdk.services.organizations.model.AwsOrganizationsNotInUseException;
import software.amazon.awssdk.services.organizations.model.OrganizationsRequest;

import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.Logger;
import software.amazon.cloudformation.proxy.ProgressEvent;
import software.amazon.cloudformation.proxy.ProxyClient;
import software.amazon.cloudformation.proxy.HandlerErrorCode;
import software.amazon.organizations.utils.OrgsLoggerWrapper;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;

import java.util.Random;

public abstract class BaseHandlerStd extends BaseHandler<CallbackContext> {
    private final double RANDOMIZATION_FACTOR = 0.5;
    private final int BASE_DELAY = 15; //in seconds

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
                new OrgsLoggerWrapper(logger)
        );
    }

    protected abstract ProgressEvent<ResourceModel, CallbackContext> handleRequest(
            final AmazonWebServicesClientProxy proxy,
            final ResourceHandlerRequest<ResourceModel> request,
            final CallbackContext callbackContext,
            final ProxyClient<OrganizationsClient> proxyClient,
            final OrgsLoggerWrapper logger
    );

    public ProgressEvent<ResourceModel, CallbackContext> handleError(
            final OrganizationsRequest request,
            final Exception e,
            final ResourceHandlerRequest<ResourceModel> handlerRequest,
            final ProxyClient<OrganizationsClient> proxyClient,
            final ResourceModel resourceModel,
            final CallbackContext callbackContext,
            final OrgsLoggerWrapper logger
    ) {
        return handleErrorTranslation(request, e, handlerRequest, proxyClient, resourceModel, callbackContext, logger);
    }

    public ProgressEvent<ResourceModel, CallbackContext> handleErrorTranslation(
            final OrganizationsRequest request,
            final Exception e,
            final ResourceHandlerRequest<ResourceModel> handlerRequest,
            final ProxyClient<OrganizationsClient> proxyClient,
            final ResourceModel resourceModel,
            final CallbackContext callbackContext,
            final OrgsLoggerWrapper logger
    ) {
        HandlerErrorCode errorCode = HandlerErrorCode.GeneralServiceException;
        if (e instanceof AlreadyInOrganizationException) {
            errorCode = HandlerErrorCode.AlreadyExists;
        } else if (e instanceof AwsOrganizationsNotInUseException) {
            errorCode = HandlerErrorCode.NotFound;
        } else if (e instanceof AccessDeniedException || e instanceof AccessDeniedForDependencyException) {
            errorCode = HandlerErrorCode.AccessDenied;
        } else if (e instanceof ConcurrentModificationException) {
            errorCode = HandlerErrorCode.ResourceConflict;
        } else if (e instanceof ConstraintViolationException) {
            errorCode = HandlerErrorCode.ServiceLimitExceeded;
        } else if (e instanceof InvalidInputException || e instanceof OrganizationNotEmptyException) {
            errorCode = HandlerErrorCode.InvalidRequest;
        } else if (e instanceof ServiceException) {
            errorCode = HandlerErrorCode.ServiceInternalError;
        } else if (e instanceof TooManyRequestsException) {
            errorCode = HandlerErrorCode.Throttling;
        }
        String orgInfo = resourceModel.getId() == null ? handlerRequest.getLogicalResourceIdentifier() : resourceModel.getId();
        logger.log(String.format("[Exception] Failed with exception: [%s]. Message: %s, ErrorCode: [%s] for Organization [%s]. ", e.getClass().getSimpleName(), e.getMessage(), errorCode, orgInfo));
        return ProgressEvent.failed(resourceModel, callbackContext, errorCode, e.getMessage());
    }

    public final boolean isRetriableException(Exception e) {
        return (e instanceof ConcurrentModificationException
                || e instanceof TooManyRequestsException
                || e instanceof ServiceException
        );
    }

    public ProgressEvent<ResourceModel, CallbackContext> handleErrorInGeneral(
            final OrganizationsRequest request,
            final Exception e,
            final ResourceHandlerRequest<ResourceModel> handlerRequest,
            final ProxyClient<OrganizationsClient> proxyClient,
            final ResourceModel resourceModel,
            final CallbackContext callbackContext,
            final OrgsLoggerWrapper logger,
            final OrganizationConstants.Action actionName,
            final OrganizationConstants.Handler handlerName
    ) {
        if ((handlerName != OrganizationConstants.Handler.READ && handlerName != OrganizationConstants.Handler.LIST)
                && isRetriableException(e)) {
            return handleRetriableException(request, handlerRequest, proxyClient, callbackContext, logger, e, resourceModel, actionName, handlerName);
        }
        return handleError(request, e, handlerRequest, proxyClient, resourceModel, callbackContext, logger);
    }

    public final int computeDelayBeforeNextRetry(int retryAttempt) {
        Random random = new Random();
        int exponentialBackoff = (int) Math.pow(2, retryAttempt) * BASE_DELAY;
        int jitter = random.nextInt((int) Math.ceil(exponentialBackoff * RANDOMIZATION_FACTOR));
        return exponentialBackoff + jitter;
    }

    public final ProgressEvent<ResourceModel, CallbackContext> handleRetriableException(
            final OrganizationsRequest organizationsRequest,
            final ResourceHandlerRequest<ResourceModel> handlerRequest,
            final ProxyClient<OrganizationsClient> proxyClient,
            final CallbackContext context,
            final OrgsLoggerWrapper logger,
            final Exception e,
            final ResourceModel model,
            final OrganizationConstants.Action actionName,
            final OrganizationConstants.Handler handlerName
    ) {
        String orgInfo = model.getId() == null ? handlerRequest.getLogicalResourceIdentifier() : model.getId();
        if (actionName != OrganizationConstants.Action.CREATE_ORG) {
            int currentAttempt = context.getCurrentRetryAttempt(actionName, handlerName);
            if (currentAttempt < MAX_RETRY_ATTEMPT_FOR_RETRIABLE_EXCEPTION) {
                context.setCurrentRetryAttempt(actionName, handlerName);
                int callbackDelaySeconds = computeDelayBeforeNextRetry(currentAttempt);
                logger.log(String.format("Got %s when calling %s for "
                                + "organization [%s]. Retrying %s of %s with callback delay %s seconds.",
                        e.getClass().getName(), organizationsRequest.getClass().getName(), orgInfo, currentAttempt + 1, MAX_RETRY_ATTEMPT_FOR_RETRIABLE_EXCEPTION, callbackDelaySeconds));
                return ProgressEvent.defaultInProgressHandler(context, callbackDelaySeconds, model);
            }
        }
        logger.log(String.format("All retry exhausted. Return exception to CloudFormation for Organization [%s].", orgInfo));
        return handleError(organizationsRequest, e, handlerRequest, proxyClient, model, context, logger);
    }
}
