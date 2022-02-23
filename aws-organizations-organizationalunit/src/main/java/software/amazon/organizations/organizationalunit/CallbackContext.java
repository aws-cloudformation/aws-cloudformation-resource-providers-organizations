package software.amazon.organizations.organizationalunit;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import software.amazon.cloudformation.proxy.StdCallbackContext;

@lombok.Data
@lombok.EqualsAndHashCode(callSuper = true)
@lombok.Builder
@JsonDeserialize(builder = CallbackContext.CallbackContextBuilder.class)
public class CallbackContext extends StdCallbackContext {
    private boolean propagationDelayed;

    @JsonPOJOBuilder(withPrefix = "")
    public static class CallbackContextBuilder {
    }
}