package software.amazon.organizations.account;

import software.amazon.awssdk.services.organizations.OrganizationsClient;
import software.amazon.awssdk.services.organizations.model.ListAccountsRequest;
import software.amazon.awssdk.services.organizations.model.ListAccountsResponse;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.HandlerErrorCode;
import software.amazon.cloudformation.proxy.Logger;
import software.amazon.cloudformation.proxy.OperationStatus;
import software.amazon.cloudformation.proxy.ProgressEvent;
import software.amazon.cloudformation.proxy.ProxyClient;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;

import java.util.ArrayList;
import java.util.List;

public class ListHandler extends BaseHandlerStd {
    private Logger log;

    protected ProgressEvent<ResourceModel, CallbackContext> handleRequest(
            final AmazonWebServicesClientProxy awsClientProxy,
            final ResourceHandlerRequest<ResourceModel> request,
            final CallbackContext callbackContext,
            final ProxyClient<OrganizationsClient> orgsClient,
            final Logger logger) {

        this.log = logger;
        logger.log(String.format("Entered %s list handler with accountId [%s]", ResourceModel.TYPE_NAME, request.getAwsAccountId()));

        final ResourceModel model = request.getDesiredResourceState();

        if (model == null) {
            return ProgressEvent.failed(ResourceModel.builder().build(), callbackContext, HandlerErrorCode.InvalidRequest,
                    "Accounts model cannot be empty!");
        }

        final List<ResourceModel> models = new ArrayList<>();

        return awsClientProxy.initiate("AWS-Organizations-Account::ListAccounts", orgsClient, model, callbackContext)
                .translateToServiceRequest(t -> Translator.translateToListAccounts(request.getNextToken()))
                .makeServiceCall(this::listAccounts)
                .handleError((organizationsRequest, e, proxyClient1, model1, context) ->
                                 handleErrorInGeneral(organizationsRequest, request, e, proxyClient1, model1, context, logger, AccountConstants.Action.LIST_ACCOUNTS, AccountConstants.Handler.LIST))
                .done(ListAccountsResponse -> {
                    models.addAll(Translator.translateListAccountsResponseToResourceModel(ListAccountsResponse));
                    return ProgressEvent.<ResourceModel, CallbackContext>builder()
                            .resourceModels(models)
                            .nextToken(ListAccountsResponse.nextToken())
                            .status(OperationStatus.SUCCESS)
                            .build();
                });
    }

    protected ListAccountsResponse listAccounts(final ListAccountsRequest listAccountsRequest, final ProxyClient<OrganizationsClient> orgsClient) {
        log.log("Start calling listAccounts");
        return orgsClient.injectCredentialsAndInvokeV2(listAccountsRequest, orgsClient.client()::listAccounts);
    }
}
