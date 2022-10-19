package software.amazon.organizations.policy;

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
    public int getCurrentRetryAttempt(final PolicyConstants.Action actionName, final PolicyConstants.Handler handlerName) {
        String action = actionName.toString();
        if (handlerName == PolicyConstants.Handler.CREATE) {
            return this.actionToRetryAttemptMapForCreate.getOrDefault(action, 0);
        } else if (handlerName == PolicyConstants.Handler.DELETE) {
            return this.actionToRetryAttemptMapForDelete.getOrDefault(action, 0);
        } else if (handlerName == PolicyConstants.Handler.UPDATE) {
            return this.actionToRetryAttemptMapForUpdate.getOrDefault(action, 0);
        } else if (handlerName == PolicyConstants.Handler.READ) {
            return this.actionToRetryAttemptMapForRead.getOrDefault(action, 0);
        } else if (handlerName == PolicyConstants.Handler.LIST) {
            return this.actionToRetryAttemptMapForList.getOrDefault(action, 0);
        } else {
            throw new CfnGeneralServiceException(String.format("Error in getting current retry attempt from callback context for action: %s!", action));
        }
    }
    public void setCurrentRetryAttempt(final PolicyConstants.Action actionName, final PolicyConstants.Handler handlerName) {
        String action = actionName.toString();
        if (handlerName == PolicyConstants.Handler.CREATE) {
            this.actionToRetryAttemptMapForCreate.put(action, getCurrentRetryAttempt(actionName, handlerName)+1);
        } else if (handlerName == PolicyConstants.Handler.DELETE) {
            this.actionToRetryAttemptMapForDelete.put(action, getCurrentRetryAttempt(actionName, handlerName)+1);
        } else if (handlerName == PolicyConstants.Handler.UPDATE) {
            this.actionToRetryAttemptMapForUpdate.put(action, getCurrentRetryAttempt(actionName, handlerName)+1);
        } else if (handlerName == PolicyConstants.Handler.READ) {
            this.actionToRetryAttemptMapForRead.put(action, getCurrentRetryAttempt(actionName, handlerName)+1);
        } else if (handlerName == PolicyConstants.Handler.LIST) {
            this.actionToRetryAttemptMapForList.put(action, getCurrentRetryAttempt(actionName, handlerName)+1);
        }
    }
    // used in CREATE handler re-invoking
//    private int retryAttachPolicyAttemptInCreate = 0;
    private boolean isPolicyCreated = false;
    // used in DELETE handler re-invoking
//    private int retryDetachPolicyAttemptInDelete = 0;
//    private int retryDeletePolicyAttempt = 0;
    private boolean isPolicyDetachedInDelete = false;
    // used in UPDATE handler re-invoking
//    private int retryUpdatePolicyAttempt = 0;
//    private int retryAttachPolicyAttemptInUpdate = 0;
//    private int retryDetachPolicyAttemptInUpdate = 0;
//    private int retryTagResourceAttemptInUpdate = 0;
//    private int retryUnTagResourceAttemptInUpdate = 0;
    private boolean isPolicyUpdated = false;
//    // used in READ handler re-invoking
//    private int retryDescribePolicyAttemptInRead = 0;
//    private int retryListTargetsForPolicyAttemptInRead = 0;
//    private int retryListTagsForPolicyAttemptInRead = 0;
//    // used in LIST handler re-invoking
//    private int retryListPoliciesAttemptInList = 0;
}
