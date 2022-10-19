package software.amazon.organizations.organizationalunit;

import com.amazonaws.util.StringUtils;
import software.amazon.awssdk.services.organizations.OrganizationsClient;
import software.amazon.awssdk.services.organizations.model.CreateOrganizationalUnitRequest;
import software.amazon.awssdk.services.organizations.model.CreateOrganizationalUnitResponse;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.HandlerErrorCode;
import software.amazon.cloudformation.proxy.Logger;
import software.amazon.cloudformation.proxy.ProgressEvent;
import software.amazon.cloudformation.proxy.ProxyClient;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;

public class CreateHandler extends BaseHandlerStd {
    private Logger log;

    public ProgressEvent<ResourceModel, CallbackContext> handleRequest(
        final AmazonWebServicesClientProxy awsClientProxy,
        final ResourceHandlerRequest<ResourceModel> request,
        final CallbackContext callbackContext,
        final ProxyClient<OrganizationsClient> orgsClient,
        final Logger logger) {

        this.log = logger;
        final ResourceModel model = request.getDesiredResourceState();

        String name = model.getName();
        String parentId = model.getParentId();

        logger.log(String.format("Requesting CreateOrganizationalUnit w/ name: %s and parentId: %s.", name, parentId));
        return ProgressEvent.progress(model, callbackContext)
                   .then(progress -> createOrganizationalUnit(awsClientProxy, request, model, callbackContext, orgsClient, logger))
                   .then(progress -> new ReadHandler().handleRequest(awsClientProxy, request, callbackContext, orgsClient, logger));
    }

    protected ProgressEvent<ResourceModel, CallbackContext> createOrganizationalUnit(
        final AmazonWebServicesClientProxy awsClientProxy,
        final ResourceHandlerRequest<ResourceModel> request,
        final ResourceModel model,
        final CallbackContext callbackContext,
        final ProxyClient<OrganizationsClient> orgsClient,
        final Logger logger) {

        ProgressEvent<ResourceModel, CallbackContext> progressEvent = null;
        // Only try MAX_RETRY_ATTEMPT_FOR_CREATE_OU times for retriable exceptions in createOU, otherwise, return to CFN exception
        // all attempts adding together must be within 1min
        for (int attempt = 0; attempt < MAX_RETRY_ATTEMPT_FOR_CREATE_OU; attempt++) {
            logger.log(String.format("Enter createOrganizationalUnit with attempt %s for ou [%s].", attempt + 1, model.getName()));
            progressEvent = awsClientProxy.initiate("AWS-Organizations-OrganizationalUnit::CreateOrganizationalUnit", orgsClient, model, callbackContext)
                                .translateToServiceRequest(Translator::translateToCreateOrganizationalUnitRequest)
                                .makeServiceCall(this::createOrganizationalUnit)
                                .stabilize(this::stabilized)
                                .handleError((organizationsRequest, e, proxyClient1, model1, context) ->
                                                 handleErrorInGeneral(organizationsRequest, e, proxyClient1, model1, context, logger, Constants.Action.CREATE_OU, Constants.Handler.CREATE)
                                )
                                .success();

            // break loop if progressEvent failed with non-retriable exceptions
            if (!progressEvent.isSuccess()) {
                if (!progressEvent.getErrorCode().name().equals(HandlerErrorCode.ResourceConflict.name())
                        && !progressEvent.getErrorCode().name().equals(HandlerErrorCode.ServiceInternalError.name())
                        && !progressEvent.getErrorCode().name().equals(HandlerErrorCode.Throttling.name())) {
                    logger.log(String.format("ProgressEvent in createOrganizationalUnit failed with non-retriable exceptions for ou [%s].", model.getName()));
                    return progressEvent;
                }
            }
            // break loop if resource has been created
            if (progressEvent.isSuccess() && model.getId() != null) {
                logger.log(String.format("Created ou with Id: [%s] for ou name [%s].", model.getId(), model.getName()));
                return ProgressEvent.progress(model, callbackContext);
            }
            try {
                int wait = computeDelayBeforeNextRetry(attempt) * 1000;
                logger.log(String.format("Wait %s millisecond before next attempt for ou [%s].", wait, model.getName()));
                Thread.sleep(wait);
            } catch (InterruptedException e) {
                log.log(e.getMessage());
            }
        }
        // return failed progressEvent
        logger.log(String.format("CreateOrganizationalUnit retry cannot use callback delay. Return exception to CloudFormation for ou [%s].", model.getName()));
        return ProgressEvent.failed(model, callbackContext, progressEvent.getErrorCode(), progressEvent.getMessage());
    }

    protected CreateOrganizationalUnitResponse createOrganizationalUnit(final CreateOrganizationalUnitRequest createOrganizationalUnitRequest, final ProxyClient<OrganizationsClient> orgsClient) {
        log.log(String.format("Calling createOrganizationalUnit API for OU [%s].", createOrganizationalUnitRequest.name()));
        final CreateOrganizationalUnitResponse createOrganizationalUnitResponse = orgsClient.injectCredentialsAndInvokeV2(createOrganizationalUnitRequest, orgsClient.client()::createOrganizationalUnit);
        return createOrganizationalUnitResponse;
    }

    private Boolean stabilized(CreateOrganizationalUnitRequest createOrganizationalUnitRequest, CreateOrganizationalUnitResponse createOrganizationalUnitResponse, ProxyClient<OrganizationsClient> orgsClient, ResourceModel model, CallbackContext callbackContext) {
        if (!StringUtils.isNullOrEmpty(createOrganizationalUnitResponse.organizationalUnit().id())) {
            model.setId(createOrganizationalUnitResponse.organizationalUnit().id());
            return true;
        }
        return false;
    }
}
