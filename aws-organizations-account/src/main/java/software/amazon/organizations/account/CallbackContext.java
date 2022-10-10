package software.amazon.organizations.account;

import software.amazon.cloudformation.proxy.StdCallbackContext;

@lombok.Getter
@lombok.Setter
@lombok.ToString
@lombok.EqualsAndHashCode(callSuper = true)
public class CallbackContext extends StdCallbackContext {
    private int maxRetryAttempt = 5;
    // used in CREATE handler
    private boolean isAccountCreated = false;
    private int currentAttemptToMoveAccount = 0;
    private String createAccountRequestId;
    private String failureReason;
    // used in DELETE handler
    private int currentAttemptToCloseAccount = 0;
    private boolean isAccountSuspended = false;
}
