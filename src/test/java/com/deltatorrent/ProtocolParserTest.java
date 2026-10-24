package com.deltatorrent;

import com.deltatorrent.utils.ProtocolParser;
import junit.framework.TestCase;

public class ProtocolParserTest extends TestCase {
  private ProtocolParser parser;
  private static final String NAME = "JohnDoe";
  private static final String IP = "192.168.1.15";
  private static final String PORT = "5001";

  @Override
  protected void setUp() throws Exception {
    parser = new ProtocolParser();
  }

  public void testMustRegisterSuccesfullyAFileOnTheNetwork() {
    String registerCommand = "REGISTER|" + NAME + "|" + IP + "|" + PORT + "|StarWars.mkv";
    String answer = parser.treat(registerCommand);

    assertEquals("REGISTER_OK", answer);
  }

  public void testReturnTheAddressOfAOwnerOfAFile() {
    parser.treat("REGISTER|" + NAME + "|" + IP + "|" + PORT + "|AmericanPyscho.mkv");

    String searchCommand = "SEARCH|AmericanPyscho";
    String answer = parser.treat(searchCommand);

    assertEquals("RESULTS|" + IP + ":" + PORT, answer);
  }

  public void testMustReturnEmptyIfTheFileDoesnotExists() {
    String answer = parser.treat("SEARCH|Shrek.mp4");

    assertEquals("RESULTS|NONE", answer);
  }

  public void testMustReturnAnErrorByTypingACommandThatDoesnotExists() {
    String answer = parser.treat("WRONGCOMMAND|Shrek.mp4");

    assertEquals("ERROR|Not a command", answer);
  }
}
