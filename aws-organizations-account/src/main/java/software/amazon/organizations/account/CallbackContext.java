package software.amazon.organizations.account;

import software.amazon.cloudformation.exceptions.CfnGeneralServiceException;
import software.amazon.cloudformation.proxy.StdCallbackContext;

import java.util.HashMap;
import java.util.Map;

@lombok.Getter
@lombok.Setter
@lombok.ToString
@lombok.EqualsAndHashCode(callSuper = true)
public class CallbackContext extends StdCallbackContext {
    private Map<String, Integer> actionToRetryAttemptMapForCreate = new HashMap<>();
    private Map<String, Integer> actionToRetryAttemptMapForDelete = new HashMap<>();
    private Map<String, Integer> actionToRetryAttemptMapForUpdate = new HashMap<>();
    private Map<String, Integer> actionToRetryAttemptMapForRead = new HashMap<>();
    private Map<String, Integer> actionToRetryAttemptMapForList = new HashMap<>();
    public int getCurrentRetryAttempt(final AccountConstants.Action actionName, final AccountConstants.Handler handlerName) {
        String action = actionName.toString();
        if (handlerName == AccountConstants.Handler.CREATE) {
            return this.actionToRetryAttemptMapForCreate.getOrDefault(action, 0);
        } else if (handlerName == AccountConstants.Handler.DELETE) {
            return this.actionToRetryAttemptMapForDelete.getOrDefault(action, 0);
        } else if (handlerName == AccountConstants.Handler.UPDATE) {
            return this.actionToRetryAttemptMapForUpdate.getOrDefault(action, 0);
        } else if (handlerName == AccountConstants.Handler.READ) {
            return this.actionToRetryAttemptMapForRead.getOrDefault(action, 0);
        } else if (handlerName == AccountConstants.Handler.LIST) {
            return this.actionToRetryAttemptMapForList.getOrDefault(action, 0);
        } else {
            throw new CfnGeneralServiceException(String.format("Error in getting current retry attempt from callback context for action: %s!", action));
        }
    }
    public void setCurrentRetryAttempt(final AccountConstants.Action actionName, final AccountConstants.Handler handlerName) {
        String action = actionName.toString();
        if (handlerName == AccountConstants.Handler.CREATE) {
            this.actionToRetryAttemptMapForCreate.put(action, getCurrentRetryAttempt(actionName, handlerName)+1);
        } else if (handlerName == AccountConstants.Handler.DELETE) {
            this.actionToRetryAttemptMapForDelete.put(action, getCurrentRetryAttempt(actionName, handlerName)+1);
        } else if (handlerName == AccountConstants.Handler.UPDATE) {
            this.actionToRetryAttemptMapForUpdate.put(action, getCurrentRetryAttempt(actionName, handlerName)+1);
        } else if (handlerName == AccountConstants.Handler.READ) {
            this.actionToRetryAttemptMapForRead.put(action, getCurrentRetryAttempt(actionName, handlerName)+1);
        } else if (handlerName == AccountConstants.Handler.LIST){
            this.actionToRetryAttemptMapForList.put(action, getCurrentRetryAttempt(actionName, handlerName)+1);
        }
    }
    // used in CREATE handler
    private boolean isAccountCreated = false;
    private String createAccountRequestId;
    private String failureReason;
}
