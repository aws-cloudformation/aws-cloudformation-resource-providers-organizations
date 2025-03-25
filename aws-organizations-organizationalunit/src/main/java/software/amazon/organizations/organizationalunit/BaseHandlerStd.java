package software.amazon.organizations.organizationalunit;

import software.amazon.awssdk.awscore.exception.AwsErrorDetails;
import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.organizations.model.AccessDeniedException;
import software.amazon.awssdk.services.organizations.model.AccessDeniedForDependencyException;
import software.amazon.awssdk.services.organizations.model.AwsOrganizationsNotInUseException;
import software.amazon.awssdk.services.organizations.model.ChildNotFoundException;
import software.amazon.awssdk.services.organizations.model.ConcurrentModificationException;
import software.amazon.awssdk.services.organizations.model.ConstraintViolationException;
import software.amazon.awssdk.services.organizations.model.DuplicateOrganizationalUnitException;
import software.amazon.awssdk.services.organizations.model.InvalidInputException;
import software.amazon.awssdk.services.organizations.model.OrganizationalUnitNotEmptyException;
import software.amazon.awssdk.services.organizations.model.OrganizationalUnitNotFoundException;
import software.amazon.awssdk.services.organizations.model.ParentNotFoundException;
import software.amazon.awssdk.services.organizations.model.ServiceException;
import software.amazon.awssdk.services.organizations.model.TargetNotFoundException;
import software.amazon.awssdk.services.organizations.model.TooManyRequestsException;

import software.amazon.awssdk.services.organizations.model.OrganizationsRequest;

import software.amazon.awssdk.services.organizations.OrganizationsClient;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.HandlerErrorCode;
import software.amazon.cloudformation.proxy.Logger;
import software.amazon.cloudformation.proxy.ProgressEvent;
import software.amazon.cloudformation.proxy.ProxyClient;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;
import software.amazon.organizations.utils.OrgsLoggerWrapper;

import java.util.List;
import java.util.Random;

public abstract class BaseHandlerStd extends BaseHandler<CallbackContext> {
    // ExponentialBackoffJitter Constants
    private static final double RANDOMIZATION_FACTOR = 0.5;
    private static final int BASE_DELAY = 15; // in seconds
    private static final int MAX_RETRY_ATTEMPT_FOR_RETRIABLE_EXCEPTION = 2;

    protected static final String ALREADY_EXISTS_ERROR_CODE = "AlreadyExists";
    protected static final String ENTITY_ALREADY_EXISTS_ERROR_CODE = "EntityAlreadyExists";

    @Override
    public final ProgressEvent<ResourceModel, CallbackContext> handleRequest(
        final AmazonWebServicesClientProxy awsClientProxy,
        final ResourceHandlerRequest<ResourceModel> request,
        final CallbackContext callbackContext,
        final Logger logger) {
            return handleRequest(
                awsClientProxy,
                request,
                callbackContext != null ? callbackContext : new CallbackContext(),
                awsClientProxy.newProxy(ClientBuilder::getClient),
                new OrgsLoggerWrapper(logger)
        );
    }

    protected abstract ProgressEvent<ResourceModel, CallbackContext> handleRequest(
        final AmazonWebServicesClientProxy awsClientProxy,
        final ResourceHandlerRequest<ResourceModel> request,
        final CallbackContext callbackContext,
        final ProxyClient<OrganizationsClient> proxyClient,
        final OrgsLoggerWrapper logger
    );

    public ProgressEvent<ResourceModel, CallbackContext> handleError(
        final OrganizationsRequest request,
        final Exception e,
        final ProxyClient<OrganizationsClient> proxyClient,
        final ResourceModel resourceModel,
        final CallbackContext callbackContext,
        final OrgsLoggerWrapper logger
    ) {
        return handleErrorTranslation(request, e, proxyClient, resourceModel, callbackContext, logger);
    }

    public ProgressEvent<ResourceModel, CallbackContext> handleErrorTranslation(
        final OrganizationsRequest request,
        final Exception e,
        final ProxyClient<OrganizationsClient> proxyClient,
        final ResourceModel resourceModel,
        final CallbackContext callbackContext,
        final OrgsLoggerWrapper logger
    ) {
        HandlerErrorCode errorCode = HandlerErrorCode.GeneralServiceException;

        if (e instanceof DuplicateOrganizationalUnitException) {
          errorCode = HandlerErrorCode.AlreadyExists;
        } else if (e instanceof AwsOrganizationsNotInUseException || e instanceof OrganizationalUnitNotFoundException
          || e instanceof ParentNotFoundException || e instanceof TargetNotFoundException || e instanceof ChildNotFoundException) {
          errorCode = HandlerErrorCode.NotFound;
        } else if (e instanceof AccessDeniedException || e instanceof AccessDeniedForDependencyException) {
          errorCode = HandlerErrorCode.AccessDenied;
        } else if (e instanceof ConcurrentModificationException){
          errorCode = HandlerErrorCode.ResourceConflict;
        } else if (e instanceof ConstraintViolationException) {
          errorCode = HandlerErrorCode.ServiceLimitExceeded;
        } else if (e instanceof InvalidInputException || e instanceof OrganizationalUnitNotEmptyException) {
          errorCode = HandlerErrorCode.InvalidRequest;
        } else if (e instanceof ServiceException || e instanceof SdkClientException) {
          errorCode = HandlerErrorCode.ServiceInternalError;
        } else if (e instanceof TooManyRequestsException) {
          errorCode = HandlerErrorCode.Throttling;
        }
        String ouInfo = resourceModel.getId() == null ? resourceModel.getName() : resourceModel.getId();
        logger.log(String.format("[Exception] Failed with exception: [%s]. Message: [%s], ErrorCode: [%s] for OrganizationalUnit [%s].",
            e.getClass().getSimpleName(), e.getMessage(), errorCode, ouInfo));
        return ProgressEvent.failed(resourceModel, callbackContext, errorCode, e.getMessage());
    }

    public ProgressEvent<ResourceModel, CallbackContext> handleErrorInGeneral(
        final OrganizationsRequest request,
        final Exception e,
        final ProxyClient<OrganizationsClient> proxyClient,
        final ResourceModel resourceModel,
        final CallbackContext callbackContext,
        final OrgsLoggerWrapper logger,
        final Constants.Action actionName,
        final Constants.Handler handlerName
    ) {
        if ((handlerName != Constants.Handler.READ && handlerName != Constants.Handler.LIST)
            && isRetriableException(e)) {
            return handleRetriableException(request, proxyClient, callbackContext, logger, e, resourceModel, actionName, handlerName);
        }
        return handleError(request, e, proxyClient, resourceModel, callbackContext, logger);
    }

    public ProgressEvent<ResourceModel, CallbackContext> handleErrorOnCreate(
        final OrganizationsRequest request,
        final Exception e,
        final ProxyClient<OrganizationsClient> proxyClient,
        final ResourceModel resourceModel,
        final CallbackContext callbackContext,
        final OrgsLoggerWrapper logger,
        final List<String> ignoreErrorCodes
    ) {
        String errorCode = "";
        if (e instanceof AwsServiceException) {
            final AwsErrorDetails awsErrorDetails = ((AwsServiceException) e).awsErrorDetails();
            if (awsErrorDetails != null) {
                errorCode = awsErrorDetails.errorCode();
            }
        }
        if (e instanceof DuplicateOrganizationalUnitException) {
            errorCode = ALREADY_EXISTS_ERROR_CODE;
        }

        // Swallow AlreadyExists and similar errors on createOU call
        if (ignoreErrorCodes.contains(errorCode)) {
            return ProgressEvent.progress(resourceModel, callbackContext);
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
                    || e instanceof TooManyRequestsException
                    || e instanceof ServiceException);
    }

    public final ProgressEvent<ResourceModel, CallbackContext> handleRetriableException(
            final OrganizationsRequest organizationsRequest,
            final ProxyClient<OrganizationsClient> proxyClient,
            final CallbackContext context,
            final OrgsLoggerWrapper logger,
            final Exception e,
            final ResourceModel model,
            final Constants.Action actionName,
            final Constants.Handler handlerName
    ) {
        String ouInfo = model.getId() == null ? model.getName() : model.getId();
        if (actionName != Constants.Action.CREATE_OU) {
            int currentAttempt = context.getCurrentRetryAttempt(actionName, handlerName);
            if (currentAttempt < MAX_RETRY_ATTEMPT_FOR_RETRIABLE_EXCEPTION) {
                context.setCurrentRetryAttempt(actionName, handlerName);
                int callbackDelaySeconds = computeDelayBeforeNextRetry(currentAttempt);
                logger.log(String.format("Got %s when calling %s for "
                                + "organizational unit [%s]. Retrying %s of %s with callback delay %s seconds.",
                        e.getClass().getName(), organizationsRequest.getClass().getName(), ouInfo, currentAttempt + 1, MAX_RETRY_ATTEMPT_FOR_RETRIABLE_EXCEPTION, callbackDelaySeconds));
                return ProgressEvent.defaultInProgressHandler(context, callbackDelaySeconds, model);
            }
        }
        logger.log(String.format("All retry exhausted. Return exception to CloudFormation for ou [%s].", ouInfo));
        return handleError(organizationsRequest, e, proxyClient, model, context, logger);
    }

}
