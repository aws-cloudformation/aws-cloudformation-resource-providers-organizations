package software.amazon.organizations.policy;

import software.amazon.awssdk.services.organizations.OrganizationsClient;
import software.amazon.awssdk.services.organizations.model.ListPoliciesRequest;
import software.amazon.awssdk.services.organizations.model.ListPoliciesResponse;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.HandlerErrorCode;
import software.amazon.cloudformation.proxy.Logger;
import software.amazon.cloudformation.proxy.ProgressEvent;
import software.amazon.cloudformation.proxy.OperationStatus;
import software.amazon.cloudformation.proxy.ProxyClient;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;

import java.util.ArrayList;
import java.util.List;


public class ListHandler extends BaseHandlerStd {
    private Logger log;

    @Override
    public ProgressEvent<ResourceModel, CallbackContext> handleRequest(
        final AmazonWebServicesClientProxy awsClientProxy,
        final ResourceHandlerRequest<ResourceModel> request,
        final CallbackContext callbackContext,
        final ProxyClient<OrganizationsClient> orgsClient,
        final Logger logger) {

        this.log = logger;
        logger.log(String.format("Entered %s list handler with accountId [%s]", ResourceModel.TYPE_NAME, request.getAwsAccountId()));

        final ResourceModel model = request.getDesiredResourceState();
        if (model == null || model.getType() == null) {
            return ProgressEvent.failed(ResourceModel.builder().build(), callbackContext, HandlerErrorCode.InvalidRequest,
                "Policies cannot be listed without specifying a type in the provided resource model!");
        }

        final List<ResourceModel> models = new ArrayList<>();

        return awsClientProxy.initiate("AWS-Organizations-Policy::ListPolicy", orgsClient, model, callbackContext)
            .translateToServiceRequest(t -> Translator.translateToListPoliciesRequest(model, request.getNextToken()))
            .makeServiceCall(this::listPolicies)
            .handleError((organizationsRequest, e, proxyClient1, model1, context) ->
                             handleErrorInGeneral(organizationsRequest, e, proxyClient1, model1, context, logger, PolicyConstants.Action.LIST_POLICIES, PolicyConstants.Handler.LIST))
            .done(listPoliciesResponse -> {
                String nextToken = listPoliciesResponse.nextToken();
                models.addAll(Translator.translateListPoliciesResponseToResourceModels(listPoliciesResponse));
                return ProgressEvent.<ResourceModel, CallbackContext>builder()
                    .resourceModels(models)
                    .nextToken(nextToken)
                    .status(OperationStatus.SUCCESS)
                    .build();
            });
    }

    protected ListPoliciesResponse listPolicies(final ListPoliciesRequest listPoliciesRequest, final ProxyClient<OrganizationsClient> orgsClient) {
        log.log("Start calling listPolicies");
        final ListPoliciesResponse listPoliciesResponse = orgsClient.injectCredentialsAndInvokeV2(listPoliciesRequest, orgsClient.client()::listPolicies);
        return listPoliciesResponse;
    }
}
