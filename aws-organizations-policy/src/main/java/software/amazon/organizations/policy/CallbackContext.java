package software.amazon.organizations.policy;

import software.amazon.cloudformation.proxy.StdCallbackContext;

import java.util.HashMap;
import java.util.Map;

@lombok.Getter
@lombok.Setter
@lombok.ToString
@lombok.EqualsAndHashCode(callSuper = true)
public class CallbackContext extends StdCallbackContext {
    private Map<String, Integer> actionToRetryAttemptMap = new HashMap<>();
    public int getCurrentRetryAttempt(final PolicyConstants.Action actionName, final PolicyConstants.Handler handlerName) {
        String key = actionName.toString() + handlerName.toString();
        return this.actionToRetryAttemptMap.getOrDefault(key, 0);
    }
    public void setCurrentRetryAttempt(final PolicyConstants.Action actionName, final PolicyConstants.Handler handlerName) {
        String key = actionName.toString() + handlerName.toString();
        this.actionToRetryAttemptMap.put(key, getCurrentRetryAttempt(actionName, handlerName)+1);
    }
    // used in CREATE handler re-invoking
    private boolean isPolicyCreated = false;
    private boolean isPreExistenceCheckComplete = false;
    private boolean didResourceAlreadyExist = false;
    // used in DELETE handler re-invoking
    private boolean isPolicyDetachedInDelete = false;
    // used in UPDATE handler re-invoking
    private boolean isPolicyUpdated = false;
}
