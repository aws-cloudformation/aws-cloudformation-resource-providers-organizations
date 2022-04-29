package software.amazon.organizations.organizationalunit;

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

public class OrgUnitSchemaFileCheckSumChangesTest extends AbstractTestBase {

  private byte[] hashedOuSchema;
  private static byte[] ouSchema;
  private static Logger logger = LoggerFactory.getLogger(OrgUnitSchemaFileCheckSumChangesTest.class);

  @BeforeAll
  public static void setup() {
    // Read the policy JSON file in a byte array. Since this file is of fixed size, we can read it without looping.
    try {
      ouSchema = Files.readAllBytes(Paths.get(OU_JSON_SCHEMA_FILE_NAME));
    }
    catch (NoSuchFileException e) {
      logger.info("Organization Unit schema json file not found. {}", e.toString());
    } catch (IOException e) {
      logger.info(e.toString());
    }
  }

  // This test is to make sure we don't modify the Organization Unit resource schema json file. It matches the HEX string of SHA-256
  // representation of OU schema file. If we ever need to modify the OU schema file, we should calculate the new Hex string.
  // We can get the string by logging actualHexString variable in test below and update the variable OUSCHEMA_SHA256_HEXSTRING.
  @Test
  public void checkIfOrgUnitSchemaFileCheckSumMatches() {
    // Get the SHA-256 representation of Organization Unit schema file to a byte array.
    try {
      hashedOuSchema = MessageDigest.getInstance("SHA-256").digest(ouSchema);
    }
    catch (NoSuchAlgorithmException e) {
      logger.info("No such algorithm found. {}", e.toString());
    }
    // Convert the byte array to a Hex String for matching.
    String actualHexString = DatatypeConverter.printHexBinary(hashedOuSchema);
    assertThat(actualHexString).isEqualTo(OU_SCHEMA_SHA256_HEXSTRING);
  }
}
