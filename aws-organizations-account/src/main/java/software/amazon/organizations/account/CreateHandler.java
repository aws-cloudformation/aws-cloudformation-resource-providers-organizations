package software.amazon.organizations.account;

import org.apache.commons.collections4.CollectionUtils;
import software.amazon.awssdk.services.account.AccountClient;
import software.amazon.awssdk.services.organizations.OrganizationsClient;
import software.amazon.awssdk.services.organizations.model.CreateAccountRequest;
import software.amazon.awssdk.services.organizations.model.CreateAccountResponse;
import software.amazon.awssdk.services.organizations.model.DescribeCreateAccountStatusResponse;
import software.amazon.awssdk.services.organizations.model.DuplicateAccountException;
import software.amazon.awssdk.services.organizations.model.ListParentsRequest;
import software.amazon.awssdk.services.organizations.model.ListParentsResponse;
import software.amazon.awssdk.services.organizations.model.MoveAccountRequest;
import software.amazon.awssdk.services.organizations.model.MoveAccountResponse;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.HandlerErrorCode;
import software.amazon.cloudformation.proxy.Logger;
import software.amazon.cloudformation.proxy.ProgressEvent;
import software.amazon.cloudformation.proxy.ProxyClient;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;

import java.util.Set;

import static software.amazon.organizations.account.Translator.translateToPutAlternateContactTypeRequest;

public class CreateHandler extends BaseHandlerStd {
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
        if (model.getAccountName() == null || model.getEmail() == null) {
            return ProgressEvent.failed(model, callbackContext, HandlerErrorCode.InvalidRequest,
                "Account cannot be created without account name and email!");
        }
        logger.log(String.format("Entered %s create handler with caller account Id [%s], AccountName [%s], Email [%s]",
            ResourceModel.TYPE_NAME, request.getAwsAccountId(), model.getAccountName(), model.getEmail()));
        return ProgressEvent.progress(request.getDesiredResourceState(), callbackContext)
                   .then(progress -> {
                           if (progress.getCallbackContext().isAccountCreated()) {
                               log.log(String.format("Account has already been created in previous handler invoke, account Id: [%s]. Skip create account.", model.getAccountId()));
                               return ProgressEvent.progress(model, callbackContext);
                           }
                           return awsClientProxy.initiate("AWS-Organizations-Account::CreateAccount", orgsClient, progress.getResourceModel(), progress.getCallbackContext())
                                      .translateToServiceRequest(Translator::translateToCreateAccountRequest)
                                      .makeServiceCall(this::createAccount)
                                      .handleError((organizationsRequest, e, proxyClient1, model1, context) -> handleError(organizationsRequest, e, proxyClient1, model1, context, logger))
                                      .done(CreateAccountResponse -> {
                                          model.setCreateAccountRequestId(CreateAccountResponse.createAccountStatus().id());
                                          logger.log(String.format("Successfully Initiated new account creation request with Id [%s]", model.getCreateAccountRequestId()));
                                          return ProgressEvent.progress(model, callbackContext);
                                      });
                       }

                   )
                   .then(progress -> describeCreateAccountStatus(awsClientProxy, request, model, callbackContext, orgsClient, logger))
                   .then(progress -> moveAccount(awsClientProxy, request, model, callbackContext, orgsClient, logger))
                   .then(progress -> model.getAlternateContacts() != null && model.getAlternateContacts().getBilling() != null ?
                                         putAlternateContactByType(awsClientProxy, model, callbackContext, accountClientProxyClient, logger, ALTERNATE_CONTACT_TYPE_BILLING, model.getAlternateContacts().getBilling()) :
                                         ProgressEvent.defaultInProgressHandler(progress.getCallbackContext(), 0, progress.getResourceModel())
                   )
                   .then(progress -> model.getAlternateContacts() != null && model.getAlternateContacts().getOperations() != null ?
                                         putAlternateContactByType(awsClientProxy, model, callbackContext, accountClientProxyClient, logger, ALTERNATE_CONTACT_TYPE_OPERATIONS, model.getAlternateContacts().getOperations()) :
                                         ProgressEvent.defaultInProgressHandler(progress.getCallbackContext(), 0, progress.getResourceModel())
                   )
                   .then(progress -> model.getAlternateContacts() != null && model.getAlternateContacts().getSecurity() != null ?
                                         putAlternateContactByType(awsClientProxy, model, callbackContext, accountClientProxyClient, logger, ALTERNATE_CONTACT_TYPE_SECURITY, model.getAlternateContacts().getOperations()) :
                                         ProgressEvent.defaultInProgressHandler(progress.getCallbackContext(), 0, progress.getResourceModel())
                   )
                   .then(progress -> new ReadHandler().handleRequest(awsClientProxy, request, callbackContext, orgsClient, accountClientProxyClient, logger));
    }

    protected ProgressEvent<ResourceModel, CallbackContext> describeCreateAccountStatus(
        final AmazonWebServicesClientProxy awsClientProxy,
        final ResourceHandlerRequest<ResourceModel> request,
        final ResourceModel model,
        final CallbackContext callbackContext,
        final ProxyClient<OrganizationsClient> orgsClient,
        final Logger logger) {
        if (callbackContext.isAccountCreated()) {
            log.log(String.format("Account has already been created in previous handler invoke, account name: [%s]. Skip describeCreateAccountStatus.", model.getAccountName()));
            return ProgressEvent.progress(model, callbackContext);
        }

        for (int attempt = 0; attempt < MAX_NUMBER_OF_ATTEMPT_FOR_DESCRIBE_CREATE_ACCOUNT_STATUS; attempt++) {
            try {
                int wait = computeDelayBeforeNextRetry(attempt);
                logger.log(String.format("Enter describeCreateAccountStatus with request id: [%s] Wait time for propagation: %s millisecond.", model.getCreateAccountRequestId(), wait));
                Thread.sleep(wait);
            } catch (InterruptedException e) {
                log.log(e.getMessage());
            }
            final ProgressEvent<ResourceModel, CallbackContext> progressEvent = awsClientProxy
                .initiate("AWS-Organizations-Account::DescribeCreateAccountStatus-Attempt" + attempt, orgsClient, model, callbackContext)
                .translateToServiceRequest((resourceModel) -> Translator.translateToDescribeCreateAccountStatusRequest(model))
                .makeServiceCall((describeCreateAccountStatusRequest, client) -> {
                    DescribeCreateAccountStatusResponse describeCreateAccountStatusResponse = orgsClient.injectCredentialsAndInvokeV2(describeCreateAccountStatusRequest,
                        orgsClient.client()::describeCreateAccountStatus);
                    String state = describeCreateAccountStatusResponse.createAccountStatus().state().toString();
                    logger.log(String.format("DescribeCreateAccountStatus returns status [%s] for request id [%s].", state, model.getCreateAccountRequestId()));
                    if (state.equals(ACCOUNT_CREATION_STATUS_IN_PROGRESS)) {
                    } else if (state.equals(ACCOUNT_CREATION_STATUS_FAILED)) {
                        model.setFailureReason(describeCreateAccountStatusResponse.createAccountStatus().failureReasonAsString());
                    } else {
                        model.setAccountId(describeCreateAccountStatusResponse.createAccountStatus().accountId());
                        callbackContext.setAccountCreated(true);
                    }
                    return describeCreateAccountStatusResponse;
                })
                .handleError((organizationsRequest, e, proxyClient1, model1, context) -> handleError(organizationsRequest, e, proxyClient1, model1, context, logger))
                .success();
            // cases to exist for loop (retry)
            // case 1: exceptions from DescribeCreateAccountStatus API
            if (!progressEvent.isSuccess()) {
                return progressEvent;
            }
            // case 2: create account already failed with a failure reason
            if (model.getFailureReason() != null) {
                return handleAccountCreationError(model,callbackContext,logger);
            }
            // case 3: create account succeed
            if (model.getAccountId() != null) {
                logger.log(String.format("Successfully created account with id: [%s]", model.getAccountId()));
                return ProgressEvent.progress(model,callbackContext);
            }
        }
        String errMsg = String.format("DescribeCreateAccountStatus keeps returning IN_PROGRESS state after %s attempts for account creation: [%s]", MAX_NUMBER_OF_ATTEMPT_FOR_DESCRIBE_CREATE_ACCOUNT_STATUS, model.getCreateAccountRequestId());
        return ProgressEvent.failed(model, callbackContext, HandlerErrorCode.ServiceInternalError, errMsg);
    }

    private ProgressEvent<ResourceModel, CallbackContext> handleAccountCreationError(ResourceModel model, CallbackContext callbackContext, Logger logger) {
        String failureReason = model.getFailureReason();
        String errMsg = String.format("Account creation failed with reason [%s] for request id: %s", failureReason, model.getCreateAccountRequestId());
        logger.log(errMsg);

        switch (failureReason) {
            case CREATE_ACCOUNT_FAILURE_REASON_EMAIL_ALREADY_EXISTS:
            case CREATE_ACCOUNT_FAILURE_REASON_GOVCLOUD_ACCOUNT_ALREADY_EXISTS:
                return ProgressEvent.failed(model,callbackContext,HandlerErrorCode.AlreadyExists, errMsg);
            case CREATE_ACCOUNT_FAILURE_REASON_ACCOUNT_LIMIT_EXCEEDED:
                return ProgressEvent.failed(model,callbackContext,HandlerErrorCode.ServiceLimitExceeded, errMsg);
            case CREATE_ACCOUNT_FAILURE_REASON_INVALID_ADDRESS:
            case CREATE_ACCOUNT_FAILURE_REASON_INVALID_EMAIL:
                return ProgressEvent.failed(model,callbackContext,HandlerErrorCode.InvalidRequest, errMsg);
        }
        return ProgressEvent.failed(model,callbackContext,HandlerErrorCode.GeneralServiceException, errMsg);
    }

    protected ProgressEvent<ResourceModel, CallbackContext> moveAccount(
        final AmazonWebServicesClientProxy awsClientProxy,
        final ResourceHandlerRequest<ResourceModel> request,
        final ResourceModel model,
        final CallbackContext callbackContext,
        final ProxyClient<OrganizationsClient> orgsClient,
        final Logger logger) {

        Set<String> parentIds = model.getParentIds();
        String accountId = model.getAccountId();
        if (CollectionUtils.isEmpty(parentIds)) {
            logger.log(String.format("No parent id found in request for account [%s]. Skip move account.", accountId));
            return ProgressEvent.progress(model, callbackContext);
        }
        // currently only support 1 parent id
        if (parentIds.size() > 1) {
            String errorMessage = String.format("Can not specify more than one parent id in request for account [%s].", accountId);
            logger.log(errorMessage);
            return ProgressEvent.failed(model, callbackContext, HandlerErrorCode.InvalidRequest, errorMessage);
        }

        String destinationId = parentIds.iterator().next();
        String sourceId = getMoveAccountSourceId(awsClientProxy, orgsClient, accountId);
        return ProgressEvent.progress(model, callbackContext)
                   .then(progress ->
                             awsClientProxy.initiate("AWS-Organizations-Account::MoveAccount", orgsClient, progress.getResourceModel(), progress.getCallbackContext())
                                 .translateToServiceRequest((moveAccountRequest) -> Translator.translateToMoveAccountRequest(model, destinationId, sourceId))
                                 .makeServiceCall(this::moveAccount)
                                 .handleError((organizationsRequest, e, proxyClient1, model1, context) -> {
                                     if (e instanceof DuplicateAccountException) {
                                         log.log(String.format("Got %s when calling %s for "
                                                                   + "account id [%s], source id [%s], destination id [%s]. Continue with next step.",
                                             e.getClass().getName(), organizationsRequest.getClass().getName(), model.getAccountId(), sourceId, destinationId));
                                         return ProgressEvent.progress(model1, context);
                                     } else if (isRetriableException(e)) {
                                         final int currentAttempt = context.getCurrentAttemptToCheckAccountCreationStatus();
                                         if (currentAttempt < MAX_NUMBER_OF_ATTEMPT_FOR_MOVE_ACCOUNT_CALLBACK) {
                                             int callbackDelaySeconds = computeDelayBeforeNextRetry(currentAttempt);
                                             logger.log(String.format("Got %s when calling %s for "
                                                                          + "Account [%s]. Retrying %s of %s with callback delay %s seconds.",
                                                 e.getClass().getName(), organizationsRequest.getClass().getName(), model.getAccountId(), currentAttempt+1, MAX_NUMBER_OF_ATTEMPT_FOR_MOVE_ACCOUNT_CALLBACK, callbackDelaySeconds));
                                             context.setCurrentAttemptToCheckAccountCreationStatus(currentAttempt+1);
                                             return ProgressEvent.defaultInProgressHandler(context, callbackDelaySeconds, model);
                                         }
                                         else {
                                             logger.log(String.format("All retry attempts exhausted for account Id [%s], return exception to CloudFormation for further handling.", model.getAccountId()));
                                             return handleError(organizationsRequest, e, proxyClient1, model, context, logger);
                                         }
                                     } else {
                                         return handleError(organizationsRequest, e, proxyClient1, model1, context, logger);
                                     }
                                 })
                                 .progress()
                   );
    }

    protected ProgressEvent<ResourceModel, CallbackContext> putAlternateContactByType(
        final AmazonWebServicesClientProxy awsClientProxy,
        final ResourceModel model,
        final CallbackContext callbackContext,
        final ProxyClient<AccountClient> accountClient,
        final Logger logger,
        final String alternateContactType,
        final AlternateContact alternateContact
    ) {
        logger.log(String.format("Put alternate contact for [%s] with alternate contact [%s].", model.getAccountId(), alternateContact.toString()));
        return ProgressEvent.progress(model, callbackContext)
                   .then(progress ->
                             awsClientProxy.initiate(String.format("AWS-Organizations-Account::PutAlternateContact-", alternateContactType), accountClient, progress.getResourceModel(), progress.getCallbackContext())
                                 .translateToServiceRequest(model1 -> translateToPutAlternateContactTypeRequest(model1, alternateContactType, alternateContact))
                                 .makeServiceCall((request, client) -> accountClient.injectCredentialsAndInvokeV2(request,
                                     accountClient.client()::putAlternateContact))
                                 .handleError((request, e, proxyClient1, model1, context) -> handleAccountError(
                                     request, e, proxyClient1, model1, context, logger))
                                 .progress()
                   );
    }

    protected String getMoveAccountSourceId(
        final AmazonWebServicesClientProxy awsClientProxy,
        final ProxyClient<OrganizationsClient> orgsClient,
        final String childId
    ) {
        ListParentsRequest listParentsRequest = Translator.translateToListParentsRequest(childId);
        ListParentsResponse listParentsResponse = awsClientProxy.injectCredentialsAndInvokeV2(listParentsRequest, orgsClient.client()::listParents);
        return listParentsResponse.parents().get(0).id();
    }

    protected CreateAccountResponse createAccount(final CreateAccountRequest createAccountRequest, final ProxyClient<OrganizationsClient> orgsClient) {
        log.log(String.format("Calling createAccount API for AccountName [%s].", createAccountRequest.accountName()));
        final CreateAccountResponse createAccountResponse = orgsClient.injectCredentialsAndInvokeV2(createAccountRequest, orgsClient.client()::createAccount);
        return createAccountResponse;
    }

    protected MoveAccountResponse moveAccount(final MoveAccountRequest moveAccountRequest, final ProxyClient<OrganizationsClient> orgsClient) {
        log.log(String.format("Calling moveAccount API for Account [%s] with destinationId [%s],  sourceId [%s].", moveAccountRequest.accountId(), moveAccountRequest.destinationParentId(), moveAccountRequest.sourceParentId()));
        return orgsClient.injectCredentialsAndInvokeV2(moveAccountRequest, orgsClient.client()::moveAccount);
    }
}
