package software.amazon.organizations.account;

import org.apache.commons.collections4.CollectionUtils;
import software.amazon.awssdk.services.organizations.model.Account;
import software.amazon.awssdk.services.organizations.OrganizationsClient;
import software.amazon.awssdk.services.organizations.model.CreateAccountRequest;
import software.amazon.awssdk.services.organizations.model.CreateAccountResponse;
import software.amazon.awssdk.services.organizations.model.DescribeCreateAccountStatusResponse;
import software.amazon.awssdk.services.organizations.model.DuplicateAccountException;
import software.amazon.awssdk.services.organizations.model.ListAccountsRequest;
import software.amazon.awssdk.services.organizations.model.ListParentsRequest;
import software.amazon.awssdk.services.organizations.model.ListParentsResponse;
import software.amazon.awssdk.services.organizations.model.MoveAccountRequest;
import software.amazon.awssdk.services.organizations.model.MoveAccountResponse;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.HandlerErrorCode;
import software.amazon.cloudformation.proxy.OperationStatus;
import software.amazon.cloudformation.proxy.ProgressEvent;
import software.amazon.cloudformation.proxy.ProxyClient;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;
import software.amazon.organizations.utils.OrgsLoggerWrapper;

import java.util.Optional;
import java.util.Set;

public class CreateHandler extends BaseHandlerStd {
    private OrgsLoggerWrapper log;

    protected ProgressEvent<ResourceModel, CallbackContext> handleRequest(
        final AmazonWebServicesClientProxy awsClientProxy,
        final ResourceHandlerRequest<ResourceModel> request,
        final CallbackContext callbackContext,
        final ProxyClient<OrganizationsClient> orgsClient,
        final OrgsLoggerWrapper logger) {

        this.log = logger;
        logger.log(String.format("Entered %s create handler with management account Id [%s] and account logical resource identifier [%s].",
            ResourceModel.TYPE_NAME, request.getAwsAccountId(), request.getLogicalResourceIdentifier()));
        final ResourceModel model = request.getDesiredResourceState();
        if (model.getAccountName() == null || model.getEmail() == null) {
            return ProgressEvent.failed(model, callbackContext, HandlerErrorCode.InvalidRequest,
                "Account cannot be created without account name and email!");
        }

        // currently only support 1 parent id
        Set<String> parentIds = model.getParentIds();
        if (parentIds != null && parentIds.size() > 1) {
            String errorMessage = String.format("Can not specify more than one parent id in request.");
            logger.log(errorMessage);
            return ProgressEvent.failed(model, callbackContext, HandlerErrorCode.InvalidRequest, errorMessage);
        }

        return ProgressEvent.progress(request.getDesiredResourceState(), callbackContext)
                .then(progress -> checkIfAccountExists(awsClientProxy, progress, orgsClient))
                .then(progress -> {
                    if (progress.getCallbackContext().isPreExistenceCheckComplete() && progress.getCallbackContext().isResourceAlreadyExists()) {
                        return ProgressEvent.failed(model, callbackContext, HandlerErrorCode.AlreadyExists,
                                String.format("Account with email [%s] already exists.", model.getEmail()));
                    }
                    if (progress.getCallbackContext().isAccountCreated()) {
                        log.log(String.format("Account has already been created in previous handler invoke with account Id: [%s]. Skip create account.", model.getAccountId()));
                        return ProgressEvent.progress(model, callbackContext);
                    }
                    return awsClientProxy.initiate("AWS-Organizations-Account::CreateAccount", orgsClient, progress.getResourceModel(), progress.getCallbackContext())
                            .translateToServiceRequest(Translator::translateToCreateAccountRequest)
                            .makeServiceCall(this::createAccount)
                            .handleError((organizationsRequest, e, proxyClient1, model1, context) -> handleError(organizationsRequest, request, e, proxyClient1, model1, context, logger))
                            .done(CreateAccountResponse -> {
                                callbackContext.setCreateAccountRequestId(CreateAccountResponse.createAccountStatus().id());
                                logger.log(String.format("Successfully initiated new account creation request with CreateAccountRequestId [%s]", callbackContext.getCreateAccountRequestId()));
                                return ProgressEvent.progress(model, callbackContext);
                            });
                })
                   .then(progress -> describeCreateAccountStatus(awsClientProxy, request, model, callbackContext, orgsClient, logger))
                   .then(progress -> moveAccount(awsClientProxy, request, model, callbackContext, orgsClient, logger))
                   .then(progress -> ProgressEvent.success(progress.getResourceModel(), progress.getCallbackContext()));
    }

    private ProgressEvent<ResourceModel, CallbackContext> checkIfAccountExists(
            final AmazonWebServicesClientProxy awsClientProxy,
            ProgressEvent<ResourceModel, CallbackContext> progress,
            final ProxyClient<OrganizationsClient> orgsClient) {

        ResourceModel model = progress.getResourceModel();
        final CallbackContext context = progress.getCallbackContext();
        String nextToken = null;

        do {
            final String currentToken = nextToken;

            ProgressEvent<ResourceModel, CallbackContext> currentProgress = awsClientProxy.initiate("AWS-Organizations-Account::ListAccounts", orgsClient, model, context)
                    .translateToServiceRequest(resourceModel -> ListAccountsRequest.builder()
                            .nextToken(currentToken)
                            .build())
                    .makeServiceCall((listAccountsRequest, proxyClient) -> proxyClient.injectCredentialsAndInvokeV2(listAccountsRequest, proxyClient.client()::listAccounts))
                    .done((listAccountsRequest, listAccountsResponse, proxyClient, resourceModel, ctx) -> {
                        Optional<Account> existingAccount = listAccountsResponse.accounts().stream()
                                .filter(account -> account.email().equals(model.getEmail()))
                                .findFirst();

                        if (existingAccount.isPresent()) {
                            model.setAccountId(existingAccount.get().id());
                            context.setResourceAlreadyExists(true);
                            log.log(String.format("Failing PreExistenceCheck: Account with email [%s] already exists with Id: [%s]", model.getEmail(), model.getAccountId()));
                        }

                        context.setPreExistenceCheckComplete(true);
                        return ProgressEvent.<ResourceModel, CallbackContext>builder()
                                .resourceModel(model)
                                .callbackContext(context)
                                .nextToken(listAccountsResponse.nextToken())
                                .status(OperationStatus.IN_PROGRESS)
                                .build();
                    });

            nextToken = currentProgress.getNextToken();

        } while(nextToken != null);

        context.setPreExistenceCheckComplete(true);
        return ProgressEvent.<ResourceModel, CallbackContext>builder()
                .resourceModel(model)
                .callbackContext(context)
                .status(OperationStatus.IN_PROGRESS)
                .build();
    }

    protected ProgressEvent<ResourceModel, CallbackContext> describeCreateAccountStatus(
        final AmazonWebServicesClientProxy awsClientProxy,
        final ResourceHandlerRequest<ResourceModel> request,
        final ResourceModel model,
        final CallbackContext callbackContext,
        final ProxyClient<OrganizationsClient> orgsClient,
        final OrgsLoggerWrapper logger) {
        // skip if account is created
        if (callbackContext.isAccountCreated()) {
            log.log(String.format("Account has already been created in previous handler invoke with account id: [%s]. Skip describeCreateAccountStatus.", model.getAccountId()));
            return ProgressEvent.progress(model, callbackContext);
        }

        // all attempts add up should be less than 60s because single progress needs to return within 60s
        for (int attempt = 0; attempt < MAX_NUMBER_OF_ATTEMPT_FOR_DESCRIBE_CREATE_ACCOUNT_STATUS; attempt++) {
            try {
                int wait = computeDelayBeforeNextRetry(attempt, BASE_DELAY_FOR_DESCRIBE_CREATE_ACCOUNT_STATUS, RANDOMIZATION_FACTOR_FOR_DESCRIBE_CREATE_ACCOUNT_STATUS);
                logger.log(String.format("Enter describeCreateAccountStatus with CreateAccountRequestId [%s] and attempt %s. Wait %s millisecond for propagation.", callbackContext.getCreateAccountRequestId(), attempt + 1, wait));
                Thread.sleep(wait); // ms
            } catch (InterruptedException e) {
                log.log(e.getMessage());
            }
            final ProgressEvent<ResourceModel, CallbackContext> progressEvent = awsClientProxy
                                                                                    .initiate("AWS-Organizations-Account::DescribeCreateAccountStatus-Attempt" + attempt, orgsClient, model, callbackContext)
                                                                                    .translateToServiceRequest((resourceModel) -> Translator.translateToDescribeCreateAccountStatusRequest(callbackContext))
                                                                                    .makeServiceCall((describeCreateAccountStatusRequest, client) -> {
                                                                                        DescribeCreateAccountStatusResponse describeCreateAccountStatusResponse = orgsClient.injectCredentialsAndInvokeV2(describeCreateAccountStatusRequest,
                                                                                            orgsClient.client()::describeCreateAccountStatus);
                                                                                        String state = describeCreateAccountStatusResponse.createAccountStatus().state().toString();
                                                                                        logger.log(String.format("DescribeCreateAccountStatus returns status [%s] for request id [%s].", state, callbackContext.getCreateAccountRequestId()));
                                                                                        if (state.equals(ACCOUNT_CREATION_STATUS_SUCCEEDED)) {
                                                                                            model.setAccountId(describeCreateAccountStatusResponse.createAccountStatus().accountId());
                                                                                            callbackContext.setAccountCreated(true);
                                                                                            callbackContext.setFailureReason(null);
                                                                                        } else if (state.equals(ACCOUNT_CREATION_STATUS_FAILED)) {
                                                                                            callbackContext.setFailureReason(describeCreateAccountStatusResponse.createAccountStatus().failureReasonAsString());
                                                                                            model.setAccountId(null);
                                                                                        }
                                                                                        return describeCreateAccountStatusResponse;
                                                                                    })
                                                                                    .handleError((organizationsRequest, e, proxyClient1, model1, context) -> handleError(organizationsRequest, request, e, proxyClient1, model1, context, logger))
                                                                                    .success();
            // cases to exist loop
            // case 1: exceptions from DescribeCreateAccountStatus API, and exceptions are non-retriable
            if (!progressEvent.isSuccess()) {
                if (!progressEvent.getErrorCode().name().equals(HandlerErrorCode.ResourceConflict.name())
                        && !progressEvent.getErrorCode().name().equals(HandlerErrorCode.ServiceInternalError.name())
                        && !progressEvent.getErrorCode().name().equals(HandlerErrorCode.Throttling.name())) {
                    logger.log("ProgressEvent in createAccount failed with non-retriable exceptions.");
                    return progressEvent;
                }
            }
            // case 2: create account already failed with a failure reason
            if (callbackContext.getFailureReason() != null) {
                return handleAccountCreationError(model, callbackContext, logger);
            }
            // case 3: create account succeed
            if (model.getAccountId() != null) {
                logger.log(String.format("Successfully created account with id: [%s].", model.getAccountId()));
                model.setStatus("ACTIVE");
                return ProgressEvent.progress(model, callbackContext);
            }
        }
        String errMsg = String.format("DescribeCreateAccountStatus returns IN_PROGRESS state before time out." +
                                          "Please check account creation status with CreateAccountRequestID [%s] and import account to CloudFormation if it is created successfully.",
            callbackContext.getCreateAccountRequestId());
        HandlerErrorCode errorCode = HandlerErrorCode.NotStabilized;
        logger.log(String.format("[Exception] Failed in describeCreateAccountStatus. Message: [%s], ErrorCode: [%s] for Account [%s].",
            errMsg, errorCode, request.getAwsAccountId()));
        return ProgressEvent.failed(model, callbackContext, errorCode, errMsg);
    }

    private ProgressEvent<ResourceModel, CallbackContext> handleAccountCreationError(ResourceModel model, CallbackContext callbackContext, OrgsLoggerWrapper logger) {
        String failureReason = callbackContext.getFailureReason();
        String errMsg = String.format("Account creation failed with reason [%s] for request id: %s", failureReason, callbackContext.getCreateAccountRequestId());
        logger.log(errMsg);

        HandlerErrorCode errorCode = HandlerErrorCode.GeneralServiceException;
        if (failureReason.equals(CREATE_ACCOUNT_FAILURE_REASON_EMAIL_ALREADY_EXISTS) ||
                failureReason.equals(CREATE_ACCOUNT_FAILURE_REASON_GOVCLOUD_ACCOUNT_ALREADY_EXISTS)
        ) {
            errorCode = HandlerErrorCode.AlreadyExists;
        } else if (failureReason.equals(CREATE_ACCOUNT_FAILURE_REASON_ACCOUNT_LIMIT_EXCEEDED)) {
            errorCode = HandlerErrorCode.ServiceLimitExceeded;
        } else if (failureReason.equals(CREATE_ACCOUNT_FAILURE_REASON_INTERNAL_FAILURE)) {
            errorCode = HandlerErrorCode.ServiceInternalError;
        } else if (failureReason.equals(CREATE_ACCOUNT_FAILURE_REASON_INVALID_ADDRESS) ||
                failureReason.equals(CREATE_ACCOUNT_FAILURE_REASON_INVALID_EMAIL) ||
                failureReason.equals(CREATE_ACCOUNT_FAILURE_REASON_FAILED_BUSINESS_VALIDATION) ||
                failureReason.equals(CREATE_ACCOUNT_FAILURE_REASON_IDENTITY_INVALID_BUSINESS_VALIDATION) ||
                failureReason.equals(CREATE_ACCOUNT_FAILURE_REASON_INVALID_PAYMENT_INSTRUMENT) ||
                failureReason.equals(CREATE_ACCOUNT_FAILURE_REASON_MISSING_BUSINESS_VALIDATION) ||
                failureReason.equals(CREATE_ACCOUNT_FAILURE_REASON_MISSING_PAYMENT_INSTRUMENT) ||
                failureReason.equals(CREATE_ACCOUNT_FAILURE_REASON_PENDING_BUSINESS_VALIDATION) ||
                failureReason.equals(CREATE_ACCOUNT_FAILURE_REASON_UNKNOWN_BUSINESS_VALIDATION) ||
                failureReason.equals(CREATE_ACCOUNT_FAILURE_REASON_CONCURRENT_ACCOUNT_MODIFICATION)
        ) {
            errorCode = HandlerErrorCode.InvalidRequest;
        }
        logger.log(String.format("[Exception] ProgressEvent failed in account creation, translated FailureReason: [%s] to CloudFormation error code: [%s].", failureReason, errorCode));
        return ProgressEvent.failed(model, callbackContext, errorCode, errMsg);
    }

    protected ProgressEvent<ResourceModel, CallbackContext> moveAccount(
        final AmazonWebServicesClientProxy awsClientProxy,
        final ResourceHandlerRequest<ResourceModel> request,
        final ResourceModel model,
        final CallbackContext callbackContext,
        final ProxyClient<OrganizationsClient> orgsClient,
        final OrgsLoggerWrapper logger) {

        Set<String> parentIds = model.getParentIds();
        String accountId = model.getAccountId();
        if (CollectionUtils.isEmpty(parentIds)) {
            logger.log(String.format("No parent id found in request for account [%s]. Skip move account.", accountId));
            return ProgressEvent.progress(model, callbackContext);
        }

        String destinationId = parentIds.iterator().next();
        String sourceId = getParentIdForAccount(awsClientProxy, orgsClient, accountId);
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
                                     }
                                     return handleErrorInGeneral(organizationsRequest, request, e, orgsClient, model, callbackContext, logger, AccountConstants.Action.MOVE_ACCOUNT, AccountConstants.Handler.CREATE);
                                 })
                                 .progress()
                   );
    }

    protected String getParentIdForAccount(
        final AmazonWebServicesClientProxy awsClientProxy,
        final ProxyClient<OrganizationsClient> orgsClient,
        final String childId
    ) {
        ListParentsRequest listParentsRequest = Translator.translateToListParentsRequest(childId);
        ListParentsResponse listParentsResponse = awsClientProxy.injectCredentialsAndInvokeV2(listParentsRequest, orgsClient.client()::listParents);
        return listParentsResponse.parents().get(0).id();
    }

    protected CreateAccountResponse createAccount(final CreateAccountRequest createAccountRequest, final ProxyClient<OrganizationsClient> orgsClient) {
        log.log("Calling createAccount API.");
        return orgsClient.injectCredentialsAndInvokeV2(createAccountRequest, orgsClient.client()::createAccount);
    }

    protected MoveAccountResponse moveAccount(final MoveAccountRequest moveAccountRequest, final ProxyClient<OrganizationsClient> orgsClient) {
        log.log(String.format("Calling moveAccount API for Account [%s] with destinationId [%s],  sourceId [%s].", moveAccountRequest.accountId(), moveAccountRequest.destinationParentId(), moveAccountRequest.sourceParentId()));
        return orgsClient.injectCredentialsAndInvokeV2(moveAccountRequest, orgsClient.client()::moveAccount);
    }
}
