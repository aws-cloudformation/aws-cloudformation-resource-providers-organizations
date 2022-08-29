package software.amazon.organizations.account;

import software.amazon.awssdk.services.account.AccountClient;
import software.amazon.awssdk.services.organizations.model.CloseAccountRequest;
import software.amazon.awssdk.services.organizations.model.CloseAccountResponse;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.Logger;
import software.amazon.cloudformation.proxy.ProgressEvent;
import software.amazon.cloudformation.proxy.ProxyClient;
import software.amazon.awssdk.services.organizations.OrganizationsClient;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;

public class DeleteHandler extends BaseHandlerStd {
    private Logger log;

    protected ProgressEvent<ResourceModel, CallbackContext> handleRequest(
        final AmazonWebServicesClientProxy awsClientProxy,
        final ResourceHandlerRequest<ResourceModel> request,
        final CallbackContext callbackContext,
        final ProxyClient<OrganizationsClient> orgsClient,
        final ProxyClient<AccountClient> accountClientProxyClient,
        final Logger logger) {

        this.log = logger;
        final ResourceModel model = request.getDesiredResourceState();
        String accountId = model.getAccountId();

        logger.log(String.format("Requesting CloseAccount w/ account id: %s.\n", accountId));
        return ProgressEvent.progress(model, callbackContext)
                   .then(progress ->
                         awsClientProxy.initiate("AWS-Organizations-Account::CloseAccount", orgsClient, progress.getResourceModel(), progress.getCallbackContext())
                             .translateToServiceRequest(Translator::translateToCloseAccountRequest)
                             .makeServiceCall(this::closeAccount)
                             .handleError((organizationsRequest, e, orgsClient1, model1, context) -> handleError(
                                 organizationsRequest, e, orgsClient1, model1, context, logger))
                             .done((deleteRequest) ->  ProgressEvent.defaultSuccessHandler(null))
                   );
    }

    protected CloseAccountResponse closeAccount(final CloseAccountRequest closeAccountRequest, final ProxyClient<OrganizationsClient> orgsClient) {
        log.log(String.format("Calling closeAccount API."));
        final CloseAccountResponse closeAccountResponse = orgsClient.injectCredentialsAndInvokeV2(closeAccountRequest, orgsClient.client()::closeAccount);
        return closeAccountResponse;
    }
}
