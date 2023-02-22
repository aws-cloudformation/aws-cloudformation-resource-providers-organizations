package software.amazon.organizations.resourcepolicy;

import software.amazon.cloudformation.proxy.StdCallbackContext;

import java.util.HashMap;
import java.util.Map;

@lombok.Getter
@lombok.Setter
@lombok.ToString
@lombok.EqualsAndHashCode(callSuper = true)
public class CallbackContext extends StdCallbackContext {
    private Map<String, Integer> actionToRetryAttemptMap = new HashMap<>();
    public int getCurrentRetryAttempt(final ResourcePolicyConstants.Action actionName, final ResourcePolicyConstants.Handler handlerName) {
        String key = actionName.toString() + handlerName.toString();
        return this.actionToRetryAttemptMap.getOrDefault(key, 0);
    }
    public void setCurrentRetryAttempt(final ResourcePolicyConstants.Action actionName, final ResourcePolicyConstants.Handler handlerName) {
        String key = actionName.toString() + handlerName.toString();
        this.actionToRetryAttemptMap.put(key, getCurrentRetryAttempt(actionName, handlerName)+1);
    }
}
