package software.amazon.organizations.policy;

import software.amazon.cloudformation.proxy.StdCallbackContext;

@lombok.Getter
@lombok.Setter
@lombok.ToString
@lombok.EqualsAndHashCode(callSuper = true)
public class CallbackContext extends StdCallbackContext {
    private int maxRetryCount = 3;
    // used in CREATE handler re-invoking
    private int retryAttempt = 0;
    private boolean isPolicyCreated = false;
}
