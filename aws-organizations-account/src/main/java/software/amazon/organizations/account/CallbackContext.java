package software.amazon.organizations.account;

import software.amazon.cloudformation.proxy.StdCallbackContext;

@lombok.Getter
@lombok.Setter
@lombok.ToString
@lombok.EqualsAndHashCode(callSuper = true)
public class CallbackContext extends StdCallbackContext {
    // used in CREATE handler
    private boolean isAccountCreated = false;
    private int currentAttemptToCheckAccountCreationStatus = 0;
}
