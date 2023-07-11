package software.amazon.organizations.organization;

import java.security.MessageDigest;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.io.IOException;
import java.nio.file.NoSuchFileException;
import java.security.NoSuchAlgorithmException;

import org.junit.jupiter.api.BeforeAll;

import javax.xml.bind.DatatypeConverter;

import static org.assertj.core.api.Assertions.assertThat;

public class OrganizationSchemaFileCheckSumChangesTest extends AbstractTestBase {

    private byte[] hashedOrganizationSchema;
    private static byte[] organizationSchema;
    private static Logger logger = LoggerFactory.getLogger(OrganizationSchemaFileCheckSumChangesTest.class);

    @BeforeAll
    public static void setup() {
        // Read the organization JSON file in a byte array. Since this file is of fixed size, we can read it without looping.
        try {
            organizationSchema = Files.readAllBytes(Paths.get(ORGANIZATION_JSON_SCHEMA_FILE_NAME));
        } catch (NoSuchFileException e) {
            logger.info("Organization  schema json file not found. {}", e.toString());
        } catch (IOException e) {
            logger.info(e.toString());
        }
    }

    // This test is to make sure we don't modify the Organization  resource schema json file. It matches the HEX string of SHA-256
    // representation of Organization schema file. If we ever need to modify the OU schema file, we should calculate the new Hex string.
    // We can get the string by logging actualHexString variable in test below and update the variable OrganizationSCHEMA_SHA256_HEXSTRING.
    @Test
    public void checkIfOrganizationSchemaFileCheckSumMatches() {
        // Get the SHA-256 representation of Organization  schema file to a byte array.
        try {
            hashedOrganizationSchema = MessageDigest.getInstance("SHA-256").digest(organizationSchema);
        } catch (NoSuchAlgorithmException e) {
            logger.info("No such algorithm found. {}", e.toString());
        }
        // Convert the byte array to a Hex String for matching.
        String actualHexString = DatatypeConverter.printHexBinary(hashedOrganizationSchema);
        assertThat(actualHexString).isEqualTo(ORGANIZATION_SCHEMA_SHA256_HEXSTRING);
    }
}
