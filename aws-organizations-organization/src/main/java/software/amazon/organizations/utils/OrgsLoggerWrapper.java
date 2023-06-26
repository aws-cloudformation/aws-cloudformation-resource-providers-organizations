package software.amazon.organizations.utils;

import software.amazon.cloudformation.proxy.Logger;

import java.util.UUID;

public class OrgsLoggerWrapper {
    String requestId;
    Logger logger;

    public OrgsLoggerWrapper(Logger logger) {
        this.requestId = UUID.randomUUID().toString();
        this.logger = logger;
    }

    public void log(String s) {
        logger.log(String.format("[%s] %s", requestId, s));
    }
}
