package software.amazon.organizations.account;

import software.amazon.awssdk.services.organizations.OrganizationsClient;
import software.amazon.awssdk.services.organizations.model.AccessDeniedException;
import software.amazon.awssdk.services.organizations.model.AccessDeniedForDependencyException;
import software.amazon.awssdk.services.organizations.model.AccountAlreadyClosedException;
import software.amazon.awssdk.services.organizations.model.AccountNotFoundException;
import software.amazon.awssdk.services.organizations.model.AwsOrganizationsNotInUseException;
import software.amazon.awssdk.services.organizations.model.ChildNotFoundException;
import software.amazon.awssdk.services.organizations.model.ConcurrentModificationException;
import software.amazon.awssdk.services.organizations.model.ConflictException;
import software.amazon.awssdk.services.organizations.model.ConstraintViolationException;
import software.amazon.awssdk.services.organizations.model.CreateAccountStatusNotFoundException;
import software.amazon.awssdk.services.organizations.model.DestinationParentNotFoundException;
import software.amazon.awssdk.services.organizations.model.DuplicateAccountException;
import software.amazon.awssdk.services.organizations.model.FinalizingOrganizationException;
import software.amazon.awssdk.services.organizations.model.InvalidInputException;
import software.amazon.awssdk.services.organizations.model.OrganizationsRequest;
import software.amazon.awssdk.services.organizations.model.ServiceException;
import software.amazon.awssdk.services.organizations.model.SourceParentNotFoundException;
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

public abstract class BaseHandlerStd extends BaseHandler<CallbackContext> {
    protected static final String GOV_CLOUD_PARTITION = "aws-us-gov";
    // CreateAccount Constants
    protected final String CREATE_ACCOUNT_FAILURE_REASON_EMAIL_ALREADY_EXISTS = "EMAIL_ALREADY_EXISTS";
    protected final String CREATE_ACCOUNT_FAILURE_REASON_GOVCLOUD_ACCOUNT_ALREADY_EXISTS = "GOVCLOUD_ACCOUNT_ALREADY_EXISTS";
    protected final String CREATE_ACCOUNT_FAILURE_REASON_ACCOUNT_LIMIT_EXCEEDED = "ACCOUNT_LIMIT_EXCEEDED";
    protected final String CREATE_ACCOUNT_FAILURE_REASON_INVALID_ADDRESS = "INVALID_ADDRESS";
    protected final String CREATE_ACCOUNT_FAILURE_REASON_INVALID_EMAIL = "INVALID_EMAIL";
    protected final String ACCOUNT_CREATION_STATUS_SUCCEEDED = "SUCCEEDED";
    protected final String ACCOUNT_CREATION_STATUS_FAILED = "FAILED";
    // ExponentialBackoffJitter Constants
    protected final double RANDOMIZATION_FACTOR = 0.5;
    protected final double RANDOMIZATION_FACTOR_FOR_DESCRIBE_CREATE_ACCOUNT_STATUS = 0.2;
    protected final int BASE_DELAY = 15; // in second
    protected final int BASE_DELAY_FOR_DESCRIBE_CREATE_ACCOUNT_STATUS = 2500; // in millisecond
    private final int MAX_RETRY_ATTEMPT_FOR_RETRIABLE_EXCEPTION = 2;
    protected final int MAX_NUMBER_OF_ATTEMPT_FOR_DESCRIBE_CREATE_ACCOUNT_STATUS = 5;

    @Override
    public final ProgressEvent<ResourceModel, CallbackContext> handleRequest(
        final AmazonWebServicesClientProxy awsClientProxy,
        final ResourceHandlerRequest<ResourceModel> request,
        final CallbackContext callbackContext,
        final Logger logger) {
        // Fail directly if this is GovCloud
        final ResourceModel model = request.getDesiredResourceState();
        if (request.getAwsPartition().equals(GOV_CLOUD_PARTITION)) {
            String errMsg = "Can not create account for GovCloud in AWS::Organizations::Account resource type.";
            logger.log(errMsg);
            return ProgressEvent.failed(model, callbackContext, HandlerErrorCode.InvalidRequest, errMsg);
        }
        return handleRequest(
            awsClientProxy,
            request,
            callbackContext != null ? callbackContext : new CallbackContext(),
            awsClientProxy.newProxy(ClientBuilder::getClient),
            logger
        );
    }

    protected abstract ProgressEvent<ResourceModel, CallbackContext> handleRequest(
        final AmazonWebServicesClientProxy awsClientProxy,
        final ResourceHandlerRequest<ResourceModel> request,
        final CallbackContext callbackContext,
        final ProxyClient<OrganizationsClient> proxyClient,
        final Logger logger
    );

    public ProgressEvent<ResourceModel, CallbackContext> handleError(
        final OrganizationsRequest request,
        final ResourceHandlerRequest<ResourceModel> handlerRequest,
        final Exception e,
        final ProxyClient<OrganizationsClient> awsClientProxy,
        final ResourceModel resourceModel,
        final CallbackContext callbackContext,
        final Logger logger
    ) {
        return handleErrorTranslation(handlerRequest, resourceModel, callbackContext, e, logger);
    }

    public ProgressEvent<ResourceModel, CallbackContext> handleError(
        final ResourceHandlerRequest<ResourceModel> handlerRequest,
        final ResourceModel resourceModel,
        final CallbackContext callbackContext,
        final Exception e,
        final Logger logger
    ) {
        return handleErrorTranslation(handlerRequest, resourceModel, callbackContext, e, logger);
    }

    public ProgressEvent<ResourceModel, CallbackContext> handleErrorInGeneral(
        final OrganizationsRequest request,
        final ResourceHandlerRequest<ResourceModel> handlerRequest,
        final Exception e,
        final ProxyClient<OrganizationsClient> proxyClient,
        final ResourceModel resourceModel,
        final CallbackContext callbackContext,
        final Logger logger,
        final AccountConstants.Action actionName,
        final AccountConstants.Handler handlerName
    ) {
        if (isRetriableException(e)) {
            return handleRetriableException(request, handlerRequest, proxyClient, callbackContext, logger, e, resourceModel, actionName, handlerName);
        }
        return handleError(request, handlerRequest, e, proxyClient, resourceModel, callbackContext, logger);
    }

    public ProgressEvent<ResourceModel, CallbackContext> handleErrorTranslation(
        final ResourceHandlerRequest<ResourceModel> handlerRequest,
        final ResourceModel resourceModel,
        final CallbackContext callbackContext,
        final Exception e,
        final Logger logger
    ) {
        HandlerErrorCode errorCode = HandlerErrorCode.GeneralServiceException;
        if (e instanceof AwsOrganizationsNotInUseException
                || e instanceof AccountNotFoundException
                || e instanceof ChildNotFoundException
                || e instanceof AccountAlreadyClosedException
                || e instanceof TargetNotFoundException
        ) {
            errorCode = HandlerErrorCode.NotFound;
        } else if (e instanceof AccessDeniedException || e instanceof AccessDeniedForDependencyException) {
            errorCode = HandlerErrorCode.AccessDenied;
        } else if (e instanceof ConcurrentModificationException) {
            errorCode = HandlerErrorCode.ResourceConflict;
        } else if (e instanceof ConstraintViolationException) {
            errorCode = HandlerErrorCode.ServiceLimitExceeded;
        } else if (e instanceof InvalidInputException
                       || e instanceof DestinationParentNotFoundException
                       || e instanceof FinalizingOrganizationException
                       || e instanceof UnsupportedApiEndpointException
                       || e instanceof ConflictException
        ) {
            errorCode = HandlerErrorCode.InvalidRequest;
        } else if (e instanceof ServiceException) {
            errorCode = HandlerErrorCode.ServiceInternalError;
        } else if (e instanceof TooManyRequestsException) {
            errorCode = HandlerErrorCode.Throttling;
        } else if (e instanceof SourceParentNotFoundException || e instanceof CreateAccountStatusNotFoundException || e instanceof DuplicateAccountException) {
            errorCode = HandlerErrorCode.InternalFailure;
        }
        String accountInfo = resourceModel.getAccountId() == null ? handlerRequest.getLogicalResourceIdentifier() : resourceModel.getAccountId();
        logger.log(String.format("[Exception] Failed with exception [%s]. Message: [%s], ErrorCode: [%s] for Account [%s].",
            e.getClass().getSimpleName(), e.getMessage(), errorCode, accountInfo));
        return ProgressEvent.failed(resourceModel, callbackContext, errorCode, e.getMessage());
    }

    public final int computeDelayBeforeNextRetry(int retryAttempt, int baseDelay, double randomizationFactor) {
        Random random = new Random();
        int exponentialBackoff = (int) Math.pow(2, retryAttempt) * baseDelay;
        int jitter = random.nextInt((int) Math.ceil(exponentialBackoff * randomizationFactor));
        return exponentialBackoff + jitter;
    }

    public final boolean isRetriableException(Exception e) {
        return (e instanceof ConcurrentModificationException
                    || e instanceof TooManyRequestsException
                    || e instanceof ServiceException
        );
    }

    public final ProgressEvent<ResourceModel, CallbackContext> handleRetriableException(
        final OrganizationsRequest organizationsRequest,
        final ResourceHandlerRequest<ResourceModel> handlerRequest,
        final ProxyClient<OrganizationsClient> proxyClient,
        final CallbackContext context,
        final Logger logger,
        final Exception e,
        final ResourceModel model,
        final AccountConstants.Action actionName,
        final AccountConstants.Handler handlerName
    ) {
        try {
            String accountInfo = model.getAccountId() == null ? handlerRequest.getLogicalResourceIdentifier() : model.getAccountId();
            if (actionName != AccountConstants.Action.CREATE_ACCOUNT) {
                int currentAttempt = context.getCurrentRetryAttempt(actionName, handlerName);
                if (currentAttempt < MAX_RETRY_ATTEMPT_FOR_RETRIABLE_EXCEPTION) {
                    int callbackDelaySeconds = computeDelayBeforeNextRetry(currentAttempt, BASE_DELAY, RANDOMIZATION_FACTOR); // in seconds
                    context.setCurrentRetryAttempt(actionName, handlerName);
                    logger.log(String.format("Got %s when calling %s for "
                                                 + "account [%s]. Retrying %s of %s with callback delay %s seconds.",
                        e.getClass().getName(), organizationsRequest.getClass().getName(), accountInfo, currentAttempt + 1, MAX_RETRY_ATTEMPT_FOR_RETRIABLE_EXCEPTION, callbackDelaySeconds));
                    return ProgressEvent.defaultInProgressHandler(context, callbackDelaySeconds, model);
                } else {
                    logger.log(String.format("All retry attempts exhausted for account [%s], return exception to CloudFormation for further handling.", accountInfo));
                    return handleError(organizationsRequest, handlerRequest, e, proxyClient, model, context, logger);
                }
            }
            return handleError(organizationsRequest, handlerRequest, e, proxyClient, model, context, logger);
        } catch (Exception exception) {
            return ProgressEvent.failed(model, context, HandlerErrorCode.GeneralServiceException, exception.getMessage());
        }
    }
}
