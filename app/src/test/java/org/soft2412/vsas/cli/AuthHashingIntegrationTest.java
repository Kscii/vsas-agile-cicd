package org.soft2412.vsas.cli;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.soft2412.vsas.security.PasswordHasher;

/**
 * Integration-style tests focused on Task #41 acceptance:
 * - Distinct salts must lead to distinct hashes for the same plaintext password
 * (at storage level).
 *
 * Scope: tests only. No production code changes.
 */
public class AuthHashingIntegrationTest {

    private Path dataDir;
    private Path usersTsv;

    @BeforeEach
    void setup() throws Exception {
        dataDir = Path.of("data");
        usersTsv = dataDir.resolve("users.tsv");
        // Start from a clean state
        if (Files.exists(usersTsv))
            Files.delete(usersTsv);
        File d = dataDir.toFile();
        if (d.exists())
            d.delete();
    }

    @AfterEach
    void tearDown() throws Exception {
        if (Files.exists(usersTsv))
            Files.delete(usersTsv);
        File d = dataDir.toFile();
        if (d.exists())
            d.delete();
    }

    @Test
    void samePasswordDifferentUsers_yieldsDifferentHashesAndSalts() throws Exception {
        String sharedPwd = "P@ssw0rd!";

        // Register first user (alice)
        var out1 = new ByteArrayOutputStream();
        var err1 = new ByteArrayOutputStream();
        int c1 = new RegisterCommand(new PrintStream(out1), new PrintStream(err1), new PasswordHasher())
                .run(new String[] {
                        "--username", "alice",
                        "--password", sharedPwd,
                        "--email", "alice@example.com",
                        "--phone", "0400000000",
                        "--id-key", "K-001"
                });
        assertEquals(0, c1, "alice should register successfully");

        // Register second user (bob) with the SAME plaintext password
        var out2 = new ByteArrayOutputStream();
        var err2 = new ByteArrayOutputStream();
        int c2 = new RegisterCommand(new PrintStream(out2), new PrintStream(err2), new PasswordHasher())
                .run(new String[] {
                        "--username", "bob",
                        "--password", sharedPwd,
                        "--email", "bob@example.com",
                        "--phone", "0400000001",
                        "--id-key", "K-002"
                });
        assertEquals(0, c2, "bob should register successfully");

        // Read back storage and extract rows for both users
        String content = Files.readString(usersTsv, StandardCharsets.UTF_8);
        String[] lines = content.split("\\R");
        assertTrue(lines.length >= 3, "header + at least two user rows expected");

        int idxHash = 5, idxSalt = 6;
        String aliceHash = null, aliceSalt = null;
        String bobHash = null, bobSalt = null;

        for (int i = 1; i < lines.length; i++) { // skip header
            String[] cols = lines[i].split("\\t", -1);
            if (cols.length < 8)
                continue;
            if ("alice".equals(cols[0])) {
                aliceHash = cols[idxHash];
                aliceSalt = cols[idxSalt];
            } else if ("bob".equals(cols[0])) {
                bobHash = cols[idxHash];
                bobSalt = cols[idxSalt];
            }
        }

        assertNotNull(aliceHash, "alice row hash present");
        assertNotNull(aliceSalt, "alice row salt present");
        assertNotNull(bobHash, "bob row hash present");
        assertNotNull(bobSalt, "bob row salt present");

        // Formats
        assertTrue(Pattern.compile("^[0-9a-f]{64}$").matcher(aliceHash).matches(), "alice hash must be 64 hex");
        assertTrue(Pattern.compile("^[0-9a-f]{32,64}$").matcher(aliceSalt).matches(), "alice salt must be 32..64 hex");
        assertTrue(Pattern.compile("^[0-9a-f]{64}$").matcher(bobHash).matches(), "bob hash must be 64 hex");
        assertTrue(Pattern.compile("^[0-9a-f]{32,64}$").matcher(bobSalt).matches(), "bob salt must be 32..64 hex");

        // Key acceptance: same plaintext + distinct salts => distinct hashes
        assertNotEquals(aliceSalt, bobSalt, "salts must differ");
        assertNotEquals(aliceHash, bobHash, "hashes must differ");
    }
}
