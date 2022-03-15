package software.amazon.organizations.organizationalunit;

import software.amazon.awssdk.services.organizations.OrganizationsClient;
import software.amazon.awssdk.services.organizations.model.ListOrganizationalUnitsForParentRequest;
import software.amazon.awssdk.services.organizations.model.ListOrganizationalUnitsForParentResponse;
import software.amazon.awssdk.services.organizations.model.OrganizationalUnit;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.Logger;
import software.amazon.cloudformation.proxy.OperationStatus;
import software.amazon.cloudformation.proxy.ProgressEvent;
import software.amazon.cloudformation.proxy.ProxyClient;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;

import java.util.List;
import java.util.stream.Collectors;

public class ListHandler extends BaseHandlerStd {
    private Logger logger;

    public ProgressEvent<ResourceModel, CallbackContext> handleRequest(
        final AmazonWebServicesClientProxy awsClientProxy,
        final ResourceHandlerRequest<ResourceModel> request,
        final CallbackContext callbackContext,
        final ProxyClient<OrganizationsClient> orgsClient,
        final Logger logger) {

        this.logger = logger;

        // Call ListOrganizationalUnitsForParent API
        logger.log("Requesting ListOrganizationalUnitsForParent");
        final ListOrganizationalUnitsForParentResponse listOrganizationalUnitsForParentResponse = awsClientProxy.injectCredentialsAndInvokeV2(
                Translator.translateToListOrganizationalUnitsForParentRequest(request.getNextToken(), request.getDesiredResourceState()), orgsClient.client()::listOrganizationalUnitsForParent);

        final List<ResourceModel> models = listOrganizationalUnitsForParentResponse.organizationalUnits().stream().map(organizationalUnit ->
                Translator.getResourceModelFromOrganizationalUnit(organizationalUnit)).collect(Collectors.toList());

        return ProgressEvent.<ResourceModel, CallbackContext>builder()
                .resourceModels(models)
                .nextToken(listOrganizationalUnitsForParentResponse.nextToken())
                .status(OperationStatus.SUCCESS)
                .build();

    }
}
