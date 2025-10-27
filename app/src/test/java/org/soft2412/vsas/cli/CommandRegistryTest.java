package org.soft2412.vsas.cli;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;

class CommandRegistryTest {

  @Test
  void resolvePrefersMostSpecificPathAndRetainsRemainingArgs() {
    CommandRegistry registry = CommandRegistry.withBuiltins();

    CommandResolution resolution =
        registry
            .resolve(new String[] {"scroll", "delete", "--id", "S-1"})
            .orElseThrow(() -> new AssertionError("command should resolve"));

    assertEquals("scroll delete", resolution.registration().canonicalName());
    assertArrayEquals(new String[] {"--id", "S-1"}, resolution.remainingArgs());
  }

  @Test
  void findByTokensIsCaseInsensitive() {
    CommandRegistry registry = CommandRegistry.withBuiltins();

    assertTrue(registry.findByTokens(List.of("HELP")).isPresent());
    assertEquals(
        "help",
        registry
            .findByTokens(List.of("help"))
            .orElseThrow(() -> new AssertionError("help command missing"))
            .canonicalName());
  }

  @Test
  void resolveUnknownCommandReturnsEmpty() {
    CommandRegistry registry = CommandRegistry.withBuiltins();
    assertTrue(registry.resolve(new String[] {"__nope__"}).isEmpty());
  }
}
