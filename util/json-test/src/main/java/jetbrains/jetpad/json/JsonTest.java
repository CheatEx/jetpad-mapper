package jetbrains.jetpad.json;

import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;

public class JsonTest {
  public static void main(String[] args) {
    if (args.length != 1) {
      System.err.println("Wrong number of arguments: " + Arrays.toString(args));
      System.exit(2);
    }

    String testFilePath = args[0];
    File inputFile = new File(testFilePath);
    String input = null;
    try {
      input = FileUtils.readFileToString(inputFile, "utf-8");
    } catch (IOException e) {
      System.err.println("Failed to read file: " + testFilePath);
      System.exit(2);
    }
    try {
      Json.parse(input);
      System.exit(0);
    } catch (JsonParsingException je) {
      System.exit(1);
    } catch (Exception e) {
      System.exit(2);
    }
  }
}
