package software.amazon.organizations.account;

import software.amazon.cloudformation.proxy.StdCallbackContext;

import java.util.HashMap;
import java.util.Map;

@lombok.Getter
@lombok.Setter
@lombok.ToString
@lombok.EqualsAndHashCode(callSuper = true)
public class CallbackContext extends StdCallbackContext {
    private Map<String, Integer> actionToRetryAttemptMap = new HashMap<>();
    public int getCurrentRetryAttempt(final AccountConstants.Action actionName, final AccountConstants.Handler handlerName) {
        String key = actionName.toString() + handlerName.toString();
        return this.actionToRetryAttemptMap.getOrDefault(key, 0);
    }
    public void setCurrentRetryAttempt(final AccountConstants.Action actionName, final AccountConstants.Handler handlerName) {
        String key = actionName.toString() + handlerName.toString();
        this.actionToRetryAttemptMap.put(key, getCurrentRetryAttempt(actionName, handlerName)+1);
    }
    // used in CREATE handler
    private boolean isAccountCreated = false;
    private boolean isPreExistenceCheckComplete = false;
    private boolean isDidResourceAlreadyExist = false;
    private String createAccountRequestId;
    private String failureReason;
}
