package software.amazon.organizations.organizationalunit;

import software.amazon.awssdk.services.organizations.model.AccessDeniedException;
import software.amazon.awssdk.services.organizations.model.AccessDeniedForDependencyException;
import software.amazon.awssdk.services.organizations.model.AwsOrganizationsNotInUseException;
import software.amazon.awssdk.services.organizations.model.ConcurrentModificationException;
import software.amazon.awssdk.services.organizations.model.ConstraintViolationException;
import software.amazon.awssdk.services.organizations.model.DuplicateOrganizationalUnitException;
import software.amazon.awssdk.services.organizations.model.InvalidInputException;
import software.amazon.awssdk.services.organizations.model.ParentNotFoundException;
import software.amazon.awssdk.services.organizations.model.OrganizationalUnitNotFoundException;
import software.amazon.awssdk.services.organizations.model.ServiceException;
import software.amazon.awssdk.services.organizations.model.TooManyRequestsException;

import software.amazon.awssdk.services.organizations.model.OrganizationsRequest;

import software.amazon.awssdk.services.organizations.OrganizationsClient;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.HandlerErrorCode;
import software.amazon.cloudformation.proxy.Logger;
import software.amazon.cloudformation.proxy.ProgressEvent;
import software.amazon.cloudformation.proxy.ProxyClient;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;

public abstract class BaseHandlerStd extends BaseHandler<CallbackContext> {
    @Override
    public final ProgressEvent<ResourceModel, CallbackContext> handleRequest(
        final AmazonWebServicesClientProxy awsClientProxy,
        final ResourceHandlerRequest<ResourceModel> request,
        final CallbackContext callbackContext,
        final Logger logger) {
            return handleRequest(
                awsClientProxy,
                request,
                callbackContext != null ? callbackContext : new CallbackContext(false),
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

        if (e instanceof DuplicateOrganizationalUnitException) {
          errorCode = HandlerErrorCode.AlreadyExists;
        } else if (e instanceof AwsOrganizationsNotInUseException || e instanceof OrganizationalUnitNotFoundException || e instanceof ParentNotFoundException) {
          errorCode = HandlerErrorCode.NotFound;
        } else if (e instanceof AccessDeniedException || e instanceof AccessDeniedForDependencyException) {
          errorCode = HandlerErrorCode.AccessDenied;
        } else if (e instanceof ConcurrentModificationException){
          errorCode = HandlerErrorCode.ResourceConflict;
        } else if (e instanceof ConstraintViolationException) {
          errorCode = HandlerErrorCode.ServiceLimitExceeded;
        } else if (e instanceof InvalidInputException) {
          errorCode = HandlerErrorCode.InvalidRequest;
        } else if (e instanceof ServiceException) {
          errorCode = HandlerErrorCode.ServiceInternalError;
        } else if (e instanceof TooManyRequestsException) {
          errorCode = HandlerErrorCode.Throttling;
        }
        logger.log(String.format("[Exception] Failed with exception: [%s]. Message: [%s], ErrorCode: [%s] for OrganizationalUnit [%s].",
            e.getClass().getSimpleName(), e.getMessage(), errorCode, resourceModel.getName()));

        return ProgressEvent.failed(resourceModel, callbackContext, errorCode, e.getMessage());
    }
}
