package software.amazon.organizations.account;

import software.amazon.awssdk.services.organizations.OrganizationsClient;
import software.amazon.awssdk.services.organizations.model.AccessDeniedException;
import software.amazon.awssdk.services.organizations.model.AccessDeniedForDependencyException;
import software.amazon.awssdk.services.organizations.model.AccountAlreadyClosedException;
import software.amazon.awssdk.services.organizations.model.AccountNotFoundException;
import software.amazon.awssdk.services.organizations.model.AwsOrganizationsNotInUseException;
import software.amazon.awssdk.services.organizations.model.ChildNotFoundException;
import software.amazon.awssdk.services.organizations.model.ConcurrentModificationException;
import software.amazon.awssdk.services.organizations.model.ConstraintViolationException;
import software.amazon.awssdk.services.organizations.model.DestinationParentNotFoundException;
import software.amazon.awssdk.services.organizations.model.InvalidInputException;
import software.amazon.awssdk.services.organizations.model.OrganizationsRequest;
import software.amazon.awssdk.services.organizations.model.ServiceException;
import software.amazon.awssdk.services.organizations.model.SourceParentNotFoundException;
import software.amazon.awssdk.services.organizations.model.TooManyRequestsException;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.HandlerErrorCode;
import software.amazon.cloudformation.proxy.Logger;
import software.amazon.cloudformation.proxy.ProgressEvent;
import software.amazon.cloudformation.proxy.ProxyClient;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;

import java.util.Random;

public abstract class BaseHandlerStd extends BaseHandler<CallbackContext> {
    protected static final String GOV_CLOUD_PARTITION = "aws-us-gov";
    protected final int MAX_NUMBER_OF_ATTEMPT_FOR_DESCRIBE_CREATE_ACCOUNT_STATUS = 5;
    protected final int MAX_NUMBER_OF_ATTEMPT_FOR_MOVE_ACCOUNT_CALLBACK = 3;
    // CreateAccount Constants
    protected final String CREATE_ACCOUNT_FAILURE_REASON_EMAIL_ALREADY_EXISTS = "EMAIL_ALREADY_EXISTS";
    protected final String CREATE_ACCOUNT_FAILURE_REASON_GOVCLOUD_ACCOUNT_ALREADY_EXISTS = "GOVCLOUD_ACCOUNT_ALREADY_EXISTS";
    protected final String CREATE_ACCOUNT_FAILURE_REASON_ACCOUNT_LIMIT_EXCEEDED = "ACCOUNT_LIMIT_EXCEEDED";
    protected final String CREATE_ACCOUNT_FAILURE_REASON_INVALID_ADDRESS = "INVALID_ADDRESS";
    protected final String CREATE_ACCOUNT_FAILURE_REASON_INVALID_EMAIL = "INVALID_EMAIL";
    protected final String ACCOUNT_CREATION_STATUS_SUCCEEDED = "SUCCEEDED";
    protected final String ACCOUNT_CREATION_STATUS_FAILED = "FAILED";
    // ExponentialBackoffJitter Constants
    private final double RANDOMIZATION_FACTOR = 0.5;
    private final int BASE_DELAY = 3000; // in millisecond

    @Override
    public final ProgressEvent<ResourceModel, CallbackContext> handleRequest(
        final AmazonWebServicesClientProxy awsClientProxy,
        final ResourceHandlerRequest<ResourceModel> request,
        final CallbackContext callbackContext,
        final Logger logger) {
        // Fail directly if this is GovCloud
        final ResourceModel model = request.getDesiredResourceState();
        if (request.getAwsPartition().equals(GOV_CLOUD_PARTITION)) {
            String errMsg = "Can not create account for GovCloud in AWS::Organizations::Account resource type. Please use AWS::Organizations::GovCloudAccount.";
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
        final Exception e,
        final ProxyClient<OrganizationsClient> awsClientProxy,
        final ResourceModel resourceModel,
        final CallbackContext callbackContext,
        final Logger logger
    ) {
        return handleErrorTranslation(resourceModel, callbackContext, e, logger);
    }

    public ProgressEvent<ResourceModel, CallbackContext> handleError(
        final ResourceModel resourceModel,
        final CallbackContext callbackContext,
        final Exception e,
        final Logger logger
    ) {
        return handleErrorTranslation(resourceModel, callbackContext, e, logger);
    }

    public ProgressEvent<ResourceModel, CallbackContext> handleErrorTranslation(
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
        ) {
            errorCode = HandlerErrorCode.NotFound;
        } else if (e instanceof AccessDeniedException || e instanceof AccessDeniedForDependencyException) {
            errorCode = HandlerErrorCode.AccessDenied;
        } else if (e instanceof ConcurrentModificationException) {
            errorCode = HandlerErrorCode.ResourceConflict;
        } else if (e instanceof ConstraintViolationException) {
            errorCode = HandlerErrorCode.ServiceLimitExceeded;
        } else if (e instanceof InvalidInputException || e instanceof DestinationParentNotFoundException) {
            errorCode = HandlerErrorCode.InvalidRequest;
        } else if (e instanceof ServiceException) {
            errorCode = HandlerErrorCode.ServiceInternalError;
        } else if (e instanceof TooManyRequestsException) {
            errorCode = HandlerErrorCode.Throttling;
        } else if (e instanceof SourceParentNotFoundException) {
            errorCode = HandlerErrorCode.InternalFailure;
        }
        logger.log(String.format("[Exception] Failed with exception. Message: [%s], ErrorCode: [%s] for Account [%s].",
            e.getMessage(), errorCode, resourceModel.getAccountName()));
        return ProgressEvent.failed(resourceModel, callbackContext, errorCode, e.getMessage());
    }

    public final int computeDelayBeforeNextRetry(int retryAttempt) {
        Random random = new Random();
        int exponentialBackoff = (int) Math.pow(2, retryAttempt) * BASE_DELAY;
        int jitter = random.nextInt((int) Math.ceil(exponentialBackoff * RANDOMIZATION_FACTOR));
        return exponentialBackoff + jitter;
    }

    public final boolean isRetriableException(Exception e) {
        return (e instanceof ConcurrentModificationException
                    || e instanceof TooManyRequestsException
                    || e instanceof ServiceException);
    }
}
