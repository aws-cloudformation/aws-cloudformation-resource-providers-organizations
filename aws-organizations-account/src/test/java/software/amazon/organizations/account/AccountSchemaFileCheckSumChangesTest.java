package software.amazon.organizations.account;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.commons.codec.binary.Hex;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import static org.assertj.core.api.Assertions.assertThat;

public class AccountSchemaFileCheckSumChangesTest extends AbstractTestBase {
    private byte[] hashedAccountSchema;
    private static byte[] accountSchema;
    private static Logger logger = LoggerFactory.getLogger(AccountSchemaFileCheckSumChangesTest.class);

    @BeforeAll
    public static void setup() {
        // Read the account JSON file in a byte array. Since this file is of fixed size, we can read it without looping.
        try {
            accountSchema = Files.readAllBytes(Paths.get(ACCOUNT_JSON_SCHEMA_FILE_NAME));
        } catch (NoSuchFileException e) {
            logger.info("Account schema json file not found. {}", e.toString());
        } catch (IOException e) {
            logger.info(e.toString());
        }
    }

    // This test is to make sure we don't modify the Account resource schema json file. It matches the HEX string of SHA-256
    // representation of Account schema file. If we ever need to modify the Account schema file, we should calculate the new Hex string.
    // We can get the string by logging actualHexString variable in test below and update the variable AccountSCHEMA_SHA256_HEXSTRING.
    @Test
    public void checkIfAccountSchemaFileCheckSumMatches() {
        // Get the SHA-256 representation of Account schema file to a byte array.
        try {
            hashedAccountSchema = MessageDigest.getInstance("SHA-256").digest(accountSchema);
        } catch (NoSuchAlgorithmException e) {
            logger.info("No such algorithm found. {}", e.toString());
        }
        // Convert the byte array to a Hex String for matching.
        String actualHexString = Hex.encodeHexString(hashedAccountSchema, false); // false to return upper case
        assertThat(actualHexString).isEqualTo(ACCOUNT_SCHEMA_SHA256_HEXSTRING);
    }
}
