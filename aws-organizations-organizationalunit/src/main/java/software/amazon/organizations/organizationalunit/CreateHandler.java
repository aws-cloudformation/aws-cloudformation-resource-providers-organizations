package software.amazon.organizations.organizationalunit;

import com.amazonaws.util.StringUtils;
import software.amazon.awssdk.services.organizations.OrganizationsClient;
import software.amazon.awssdk.services.organizations.model.CreateOrganizationalUnitRequest;
import software.amazon.awssdk.services.organizations.model.CreateOrganizationalUnitResponse;
import software.amazon.awssdk.services.organizations.model.OrganizationalUnit;
import software.amazon.awssdk.services.organizations.model.ListOrganizationalUnitsForParentRequest;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.HandlerErrorCode;
import software.amazon.cloudformation.proxy.OperationStatus;
import software.amazon.cloudformation.proxy.ProgressEvent;
import software.amazon.cloudformation.proxy.ProxyClient;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;
import software.amazon.organizations.utils.OrgsLoggerWrapper;

import java.util.Arrays;
import java.util.Optional;

public class CreateHandler extends BaseHandlerStd {
    private OrgsLoggerWrapper log;
    private static final int CALLBACK_DELAY = 1;

    public ProgressEvent<ResourceModel, CallbackContext> handleRequest(
        final AmazonWebServicesClientProxy awsClientProxy,
        final ResourceHandlerRequest<ResourceModel> request,
        final CallbackContext callbackContext,
        final ProxyClient<OrganizationsClient> orgsClient,
        final OrgsLoggerWrapper logger
        ) {

        this.log = logger;
        final ResourceModel model = request.getDesiredResourceState();

        String name = model.getName();
        String parentId = model.getParentId();

        logger.log(String.format("Requesting CreateOrganizationalUnit w/ name: %s and parentId: %s.", name, parentId));
        return ProgressEvent.progress(model, callbackContext)
                .then(progress -> callbackContext.isPreExistenceCheckComplete() ? progress : checkIfOrganizationalUnitExists(awsClientProxy, progress, orgsClient))
                .then(progress -> {
                    if (progress.getCallbackContext().isPreExistenceCheckComplete() && progress.getCallbackContext().isResourceAlreadyExists()) {
                        String message = String.format("Failing PreExistenceCheck: OrganizationalUnit with name [%s] already exists in parent [%s].", name, parentId);
                        log.log(message);
                        return ProgressEvent.failed(model, callbackContext, HandlerErrorCode.AlreadyExists, message);
                    }
                    if (progress.getCallbackContext().isOuCreated()) {
                        // skip to read handler
                        log.log(String.format("OrganizationalUnit has already been created in previous handler invoke, ou id: [%s]. Skip to read handler.", model.getId()));
                        return ProgressEvent.progress(model, callbackContext);
                    }
                    return awsClientProxy.initiate("AWS-Organizations-OrganizationalUnit::CreateOrganizationalUnit", orgsClient, progress.getResourceModel(), progress.getCallbackContext())
                            .translateToServiceRequest(x -> Translator.translateToCreateOrganizationalUnitRequest(x, request))
                            .makeServiceCall(this::createOrganizationalUnit)
                            .stabilize(this::stabilized)
                            .handleError((organizationsRequest, e, proxyClient1, model1, context) ->
                                    handleErrorOnCreate(organizationsRequest, e, proxyClient1, model1, context, logger, Arrays.asList(ALREADY_EXISTS_ERROR_CODE, ENTITY_ALREADY_EXISTS_ERROR_CODE)))
                            .done(CreateOrganizationalUnitResponse -> {
                                logger.log(String.format("Created OrganizationalUnit with Id: [%s]", CreateOrganizationalUnitResponse.organizationalUnit().id()));
                                progress.getCallbackContext().setOuCreated(true);
                                return ProgressEvent.defaultInProgressHandler(callbackContext, CALLBACK_DELAY, model);
                            });
                })
                .then(progress -> new ReadHandler().handleRequest(awsClientProxy, request, callbackContext, orgsClient, logger));
    }

    private ProgressEvent<ResourceModel, CallbackContext> checkIfOrganizationalUnitExists(
            final AmazonWebServicesClientProxy awsClientProxy,
            ProgressEvent<ResourceModel, CallbackContext> progress,
            final ProxyClient<OrganizationsClient> orgsClient) {

        ResourceModel model = progress.getResourceModel();
        final CallbackContext context = progress.getCallbackContext();
        String nextToken = null;

        do {
            final String currentToken = nextToken;

            ProgressEvent<ResourceModel, CallbackContext> currentProgress = awsClientProxy.initiate("AWS-Organizations-OrganizationalUnit::ListOrganizationalUnitsForParent", orgsClient, model, context)
                .translateToServiceRequest(resourceModel -> ListOrganizationalUnitsForParentRequest.builder()
                        .parentId(resourceModel.getParentId())
                        .nextToken(currentToken)
                        .build())
                .makeServiceCall((listOURequest, proxyClient) -> proxyClient.injectCredentialsAndInvokeV2(listOURequest, proxyClient.client()::listOrganizationalUnitsForParent))
                .done((listOURequest, listOUResponse, proxyClient, resourceModel, ctx) -> {
                    Optional<OrganizationalUnit> existingOU = listOUResponse.organizationalUnits().stream()
                            .filter(ou -> ou.name().equals(model.getName()))
                            .findFirst();

                    if (existingOU.isPresent()) {
                        model.setId(existingOU.get().id());
                        context.setResourceAlreadyExists(true);
                        log.log(String.format("OrganizationalUnit [%s] already exists with Id: [%s]", model.getName(), model.getId()));
                    }

                    context.setPreExistenceCheckComplete(true);
                    return ProgressEvent.<ResourceModel, CallbackContext>builder()
                            .resourceModel(model)
                            .callbackContext(context)
                            .nextToken(listOUResponse.nextToken())
                            .status(OperationStatus.IN_PROGRESS)
                            .build();
                });

            nextToken = currentProgress.getNextToken();

        } while (nextToken != null && !context.isResourceAlreadyExists());

        context.setPreExistenceCheckComplete(true);
        int callbackDelaySeconds = 0;
        if (context.isResourceAlreadyExists()) {
            log.log("PreExistenceCheck complete! Requested resource was found.");
        } else {
            callbackDelaySeconds = CALLBACK_DELAY;
            log.log("PreExistenceCheck complete! Requested resource was not found.");
        }
        return ProgressEvent.<ResourceModel, CallbackContext>builder()
                .resourceModel(model)
                .callbackContext(context)
                .callbackDelaySeconds(callbackDelaySeconds)
                .status(OperationStatus.IN_PROGRESS)
                .build();
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
