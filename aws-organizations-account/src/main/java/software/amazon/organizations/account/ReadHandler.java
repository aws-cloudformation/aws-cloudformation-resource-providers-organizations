package software.amazon.organizations.account;

import software.amazon.awssdk.services.organizations.OrganizationsClient;
import software.amazon.awssdk.services.organizations.model.DescribeAccountRequest;
import software.amazon.awssdk.services.organizations.model.DescribeAccountResponse;
import software.amazon.awssdk.services.organizations.model.ListParentsRequest;
import software.amazon.awssdk.services.organizations.model.ListParentsResponse;
import software.amazon.awssdk.services.organizations.model.ListTagsForResourceRequest;
import software.amazon.awssdk.services.organizations.model.ListTagsForResourceResponse;
import software.amazon.awssdk.services.organizations.model.Parent;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.Logger;
import software.amazon.cloudformation.proxy.ProgressEvent;
import software.amazon.cloudformation.proxy.ProxyClient;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;

import java.util.HashSet;
import java.util.Set;

public class ReadHandler extends BaseHandlerStd {
    private Logger log;

    protected ProgressEvent<ResourceModel, CallbackContext> handleRequest(
        final AmazonWebServicesClientProxy awsClientProxy,
        final ResourceHandlerRequest<ResourceModel> request,
        final CallbackContext callbackContext,
        final ProxyClient<OrganizationsClient> orgsClient,
        final Logger logger) {

        this.log = logger;
        final ResourceModel model = request.getDesiredResourceState();
        String accountId = model.getAccountId();
        logger.log(String.format("Requesting DescribeAccount w/ Account id: %s.\n", accountId));
        return ProgressEvent.progress(model, callbackContext)
                   .then(progress ->
                             awsClientProxy.initiate("AWS-Organizations-Account::DescribeAccount", orgsClient, progress.getResourceModel(), progress.getCallbackContext())
                                 .translateToServiceRequest(Translator::translateToDescribeAccountRequest)
                                 .makeServiceCall(this::describeAccount)
                                 .handleError((organizationsRequest, e, orgsClient1, model1, context) -> handleError(
                                     organizationsRequest, e, orgsClient1, model1, context, logger))
                                 .done(describeAccountResponse -> {
                                     model.setAccountId(describeAccountResponse.account().id());
                                     model.setAccountName(describeAccountResponse.account().name());
                                     model.setEmail(describeAccountResponse.account().email());
                                     return ProgressEvent.progress(model, callbackContext);
                                 })
                   )
                   .then(progress -> listParents(awsClientProxy, request, model, callbackContext, orgsClient, logger))
                   .then(progress -> listTagsForAccount(awsClientProxy, request, model, callbackContext, orgsClient, logger));
    }

    protected ProgressEvent<ResourceModel, CallbackContext> listParents(
        final AmazonWebServicesClientProxy awsClientProxy,
        final ResourceHandlerRequest<ResourceModel> request,
        final ResourceModel model,
        final CallbackContext callbackContext,
        final ProxyClient<OrganizationsClient> orgsClient,
        final Logger logger) {

        String accountId = model.getAccountId();
        logger.log(String.format("Listing parents for account id: %s.\n", accountId));
        return awsClientProxy.initiate("AWS-Organizations-Account::ListParents", orgsClient, model, callbackContext)
                   .translateToServiceRequest(Translator::translateToListParentsRequest)
                   .makeServiceCall(this::listParents)
                   .handleError((organizationsRequest, e, orgsClient1, model1, context) -> handleError(
                       organizationsRequest, e, orgsClient1, model1, context, logger))
                   .done(listParentsResponse -> {
                       Parent parent = listParentsResponse.parents().get(0);
                       Set<String> parentIds = new HashSet<>();
                       parentIds.add(parent.id());
                       model.setParentIds(parentIds);
                       return ProgressEvent.progress(model, callbackContext);
                   });
    }

    protected ProgressEvent<ResourceModel, CallbackContext> listTagsForAccount(
        final AmazonWebServicesClientProxy awsClientProxy,
        final ResourceHandlerRequest<ResourceModel> request,
        final ResourceModel model,
        final CallbackContext callbackContext,
        final ProxyClient<OrganizationsClient> orgsClient,
        final Logger logger) {

        String accountId = model.getAccountId();
        logger.log(String.format("Listing tags for account id: %s.\n", accountId));
        return awsClientProxy.initiate("AWS-Organizations-Account::ListTagsForResource", orgsClient, model, callbackContext)
                   .translateToServiceRequest(resourceModel -> Translator.translateToListTagsForResourceRequest(model))
                   .makeServiceCall(this::listTagsForResource)
                   .handleError((organizationsRequest, e, orgsClient1, model1, context) -> handleError(
                       organizationsRequest, e, orgsClient1, model1, context, logger))
                   .done(listTagsForResourceResponse -> ProgressEvent.defaultSuccessHandler(Translator.translateFromAllDescribeResponse(model, listTagsForResourceResponse)));
    }

    protected ListTagsForResourceResponse listTagsForResource(final ListTagsForResourceRequest listTagsForResourceRequest, final ProxyClient<OrganizationsClient> orgsClient) {
        log.log(String.format("Calling ListTagsForResource API for resource [%s].", listTagsForResourceRequest.resourceId()));
        return orgsClient.injectCredentialsAndInvokeV2(listTagsForResourceRequest, orgsClient.client()::listTagsForResource);
    }

    protected DescribeAccountResponse describeAccount(final DescribeAccountRequest describeAccountRequest, final ProxyClient<OrganizationsClient> orgsClient) {
        log.log(String.format("Calling DescribeAccount API for AccountId [%s].", describeAccountRequest.accountId()));
        return orgsClient.injectCredentialsAndInvokeV2(describeAccountRequest, orgsClient.client()::describeAccount);
    }

    protected ListParentsResponse listParents(final ListParentsRequest listParentsRequest, final ProxyClient<OrganizationsClient> orgsClient) {
        log.log(String.format("Calling ListParents API for AccountId [%s].", listParentsRequest.childId()));
        return orgsClient.injectCredentialsAndInvokeV2(listParentsRequest, orgsClient.client()::listParents);
    }
}
