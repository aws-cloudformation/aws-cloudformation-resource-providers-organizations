package software.amazon.organizations.organization;

import software.amazon.cloudformation.proxy.StdCallbackContext;

import java.util.HashMap;
import java.util.Map;

@lombok.Getter
@lombok.Setter
@lombok.ToString
@lombok.EqualsAndHashCode(callSuper = true, exclude = "actionToRetryAttemptMap")
public class CallbackContext extends StdCallbackContext {
    private Map<String, Integer> actionToRetryAttemptMap = new HashMap<>();

    // Manually implement the setter with a defensive copy
    public void setActionToRetryAttemptMap(Map<String, Integer> actionToRetryAttemptMap) {
        this.actionToRetryAttemptMap = new HashMap<>(actionToRetryAttemptMap);
    }

    // Manually implement the getter with a defensive copy
    public Map<String, Integer> getActionToRetryAttemptMap() {
        return new HashMap<>(actionToRetryAttemptMap);
    }

    // Used to set Propagation Delay in the CreateHandler call chain.
    public boolean propagationDelay = false;
    // used in CREATE handler re-invoking
    private boolean isOrgCreated = false;

    public int getCurrentRetryAttempt(final OrganizationConstants.Action actionName, final OrganizationConstants.Handler handlerName) {
        String key = actionName.toString() + handlerName.toString();
        return this.actionToRetryAttemptMap.getOrDefault(key, 0);
    }

    public void setCurrentRetryAttempt(final OrganizationConstants.Action actionName, final OrganizationConstants.Handler handlerName) {
        String key = actionName.toString() + handlerName.toString();
        this.actionToRetryAttemptMap.put(key, getCurrentRetryAttempt(actionName, handlerName) + 1);
    }
}
