package software.amazon.organizations.policy;

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

public class PolicySchemaFileCheckSumChangesTest extends AbstractTestBase {

  private byte[] hashedPolicySchema;
  private static byte[] policySchema;
  private static Logger logger = LoggerFactory.getLogger(PolicySchemaFileCheckSumChangesTest.class);

  @BeforeAll
  public static void setup() {
    // Read the policy JSON file in a byte array. Since this file is of fixed size, we can read it without looping.
    try {
      policySchema = Files.readAllBytes(Paths.get(POLICY_JSON_SCHEMA_FILE_NAME));
    }
    catch (NoSuchFileException e) {
      logger.info("Policy schema json file not found. {}", e.toString());
    } catch (IOException e) {
      logger.info(String.format(e.toString()));
    }
  }

  // This test is to make sure we don't modify the Policy resource schema json file. It matches the HEX string of SHA-256
  // representation of OU schema file. If we ever need to modify the POLICY schema file, we should calculate the new Hex string.
  // We can get the string by logging actualHexString variable in test below and update the variable POLICY_SCHEMA_SHA256_HEXSTRING.
  @Test
  public void checkIfPolicySchemaFileCheckSumMatches() {
    // Get the SHA-256 representation of Policy schema file to a byte array.
    try {
      hashedPolicySchema = MessageDigest.getInstance("SHA-256").digest(policySchema);
    }
    catch (NoSuchAlgorithmException e) {
      logger.info("No such algorithm found. {}", e.toString());
    }
    // Convert the byte array to a Hex String for matching.
    String actualHexString = DatatypeConverter.printHexBinary(hashedPolicySchema);
    assertThat(actualHexString).isEqualTo(POLICY_SCHEMA_SHA256_HEXSTRING);
  }
}
