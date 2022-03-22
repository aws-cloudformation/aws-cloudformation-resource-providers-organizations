package software.amazon.organizations.organization;

import software.amazon.awssdk.services.organizations.OrganizationsClient;

import software.amazon.awssdk.services.organizations.model.AccessDeniedException;
import software.amazon.awssdk.services.organizations.model.AccessDeniedForDependencyException;
import software.amazon.awssdk.services.organizations.model.ConcurrentModificationException;
import software.amazon.awssdk.services.organizations.model.ConstraintViolationException;
import software.amazon.awssdk.services.organizations.model.InvalidInputException;
import software.amazon.awssdk.services.organizations.model.ServiceException;
import software.amazon.awssdk.services.organizations.model.TooManyRequestsException;
import software.amazon.awssdk.services.organizations.model.AlreadyInOrganizationException;
import software.amazon.awssdk.services.organizations.model.AwsOrganizationsNotInUseException;
import software.amazon.awssdk.services.organizations.model.OrganizationsRequest;

import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.Logger;
import software.amazon.cloudformation.proxy.ProgressEvent;
import software.amazon.cloudformation.proxy.ProxyClient;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;
import software.amazon.cloudformation.proxy.HandlerErrorCode;


// Placeholder for the functionality that could be shared across Create/Read/Update/Delete/List Handlers

public abstract class BaseHandlerStd extends BaseHandler<CallbackContext> {
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
        } else if (e instanceof InvalidInputException) {
            errorCode = HandlerErrorCode.InvalidRequest;
        } else if (e instanceof ServiceException) {
            errorCode = HandlerErrorCode.ServiceInternalError;
        } else if (e instanceof TooManyRequestsException) {
            errorCode = HandlerErrorCode.Throttling;
        }
        logger.log(String.format("[Exception] Failed with exception: [%s]. Message: %s, ", e.getClass().getSimpleName(), e.getMessage()));
        return ProgressEvent.defaultFailureHandler(e, errorCode);
    }
}
