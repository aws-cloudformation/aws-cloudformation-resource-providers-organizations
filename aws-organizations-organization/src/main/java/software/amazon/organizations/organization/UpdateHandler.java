package software.amazon.organizations.organization;

import software.amazon.awssdk.services.organizations.OrganizationsClient;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.ProxyClient;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;
import software.amazon.cloudformation.proxy.ProgressEvent;
import software.amazon.cloudformation.proxy.HandlerErrorCode;
import software.amazon.organizations.utils.OrgsLoggerWrapper;


public class UpdateHandler extends BaseHandlerStd {
    private OrgsLoggerWrapper log;
    private final String FEATURE_MODE_ALL = "ALL";

    protected ProgressEvent<ResourceModel, CallbackContext> handleRequest(
            final AmazonWebServicesClientProxy awsClientProxy,
            final ResourceHandlerRequest<ResourceModel> request,
            final CallbackContext callbackContext,
            final ProxyClient<OrganizationsClient> orgsClient,
            final OrgsLoggerWrapper logger) {

        this.log = logger;
        logger.log(String.format("Entered %s update handler for Organization resource type with account Id [%s].", ResourceModel.TYPE_NAME, request.getAwsAccountId()));

        final ResourceModel previousModel = request.getPreviousResourceState();
        final ResourceModel model = request.getDesiredResourceState();
        // default feature set if not provided is ALL
        if (previousModel.getFeatureSet() == null) {
            previousModel.setFeatureSet(FEATURE_MODE_ALL);
        }
        if (model.getFeatureSet() == null) {
            model.setFeatureSet(FEATURE_MODE_ALL);
        }

        if (!model.getFeatureSet().equals(previousModel.getFeatureSet())) {
            String errorMsg = "Update between ALL and CONSOLIDATED_BILLING feature set mode is not supported.";
            logger.log(errorMsg);
            return ProgressEvent.failed(ResourceModel.builder().build(), callbackContext, HandlerErrorCode.InvalidRequest, errorMsg);
        }
        return ProgressEvent.defaultSuccessHandler(model);
    }
}
