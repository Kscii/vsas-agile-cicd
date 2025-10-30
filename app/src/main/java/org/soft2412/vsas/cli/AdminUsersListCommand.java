package org.soft2412.vsas.cli;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import org.soft2412.vsas.model.User;
import org.soft2412.vsas.service.SessionService;

/**
 * Admin-only: list users with optional AND-filters and fixed-width table.
 *
 * Columns (fixed): username, email, phone, idKey, role, createdAt
 * Filters: --username <u>, --id-key <k>, --role admin|user
 *
 * Exit codes:
 * 0 -> success
 * 1 -> permission/validation error (non-admin, invalid role)
 * 2 -> usage error (unknown flag or missing value)
 * 3 -> I/O error (failed to read users.tsv)
 */

public final class AdminUsersListCommand implements Command {

    private static final int W_USERNAME = 16;
    private static final int W_EMAIL = 28;
    private static final int W_PHONE = 14;
    private static final int W_IDKEY = 16;
    private static final int W_ROLE = 6;
    private static final int W_CREATED = 20;

    private static final String HEADER_FMT = "%-" + W_USERNAME + "s  %-" + W_EMAIL + "s  %-" + W_PHONE + "s  %-"
            + W_IDKEY + "s  %-"
            + W_ROLE + "s  %-" + W_CREATED + "s";
    private static final String ROW_FMT = HEADER_FMT;

    private final PrintStream out;
    private final PrintStream err;
    private final SessionService sessions;

    public AdminUsersListCommand() {
        this(System.out, System.err, new SessionService());
    }

    AdminUsersListCommand(PrintStream out, PrintStream err, SessionService sessions) {
        this.out = Objects.requireNonNull(out, "out");
        this.err = Objects.requireNonNull(err, "err");
        this.sessions = Objects.requireNonNull(sessions, "sessions");
    }

    @Override
    public int run(String[] args) {
        String usernameFilter = null;
        String idKeyFilter = null;
        String roleFilter = null;

        String[] safe = args == null ? new String[0] : args;
        for (int i = 0; i < safe.length; i++) {
            String a = safe[i];
            switch (a) {
                case "--username":
                    if (i + 1 >= safe.length) {
                        err.println("Missing value for --username");
                        return 2;
                    }
                    usernameFilter = safe[++i].trim();
                    break;
                case "--id-key":
                    if (i + 1 >= safe.length) {
                        err.println("Missing value for --id-key");
                        return 2;
                    }
                    idKeyFilter = safe[++i].trim();
                    break;
                case "--role":
                    if (i + 1 >= safe.length) {
                        err.println("Missing value for --role");
                        return 2;
                    }
                    String v = safe[++i].trim();
                    String norm = normalizeRole(v);
                    if (norm == null) {
                        err.println("Invalid role: " + nullToEmpty(v));
                        return 1;
                    }
                    roleFilter = norm;
                    break;
                default:
                    err.println("Unknown option: " + a);
                    return 2;
            }
        }

        Optional<User> currentOpt = sessions.currentUser();
        if (currentOpt.isEmpty()) {
            err.println("Forbidden: admin login required.");
            return 1;
        }
        User current = currentOpt.get();
        if (!isAdmin(current.role())) {
            err.println("Forbidden: admin role required.");
            return 1;
        }

        Path usersPath = resolveUsersPath();
        List<Row> rows;
        try {
            rows = readAll(usersPath);
        } catch (IOException io) {
            err.println("I/O error: " + io.getMessage());
            return 3;
        }

        List<Row> filtered = new ArrayList<>(rows.size());
        for (Row r : rows) {
            if (usernameFilter != null && !usernameFilter.equals(r.username))
                continue;
            if (idKeyFilter != null && !idKeyFilter.equals(r.idKey))
                continue;
            if (roleFilter != null && !roleFilter.equalsIgnoreCase(r.role))
                continue;
            filtered.add(r);
        }

        out.println(formatHeader());
        for (Row r : filtered) {
            out.println(formatRow(r));
        }

        return 0;
    }

    @Override
    public String name() {
        return "admin users list";
    }

    @Override
    public String description() {
        return "List users (admin only)";
    }

    private static boolean isAdmin(String role) {
        return "ADMIN".equalsIgnoreCase(nullToEmpty(role));
    }

    private static String normalizeRole(String v) {
        if (v == null)
            return null;
        String lower = v.trim().toLowerCase(Locale.ROOT);
        if (lower.isEmpty())
            return null;
        if ("admin".equals(lower))
            return "ADMIN";
        if ("user".equals(lower))
            return "USER";
        return null;
    }

    private static String nullToEmpty(String v) {
        return v == null ? "" : v;
    }

    private static String formatHeader() {
        return String.format(
                HEADER_FMT, "username", "email", "phone", "idKey", "role", "createdAt");
    }

    private static String formatRow(Row r) {
        String roleDisplay = "ADMIN".equalsIgnoreCase(r.role) ? "admin"
                : ("USER".equalsIgnoreCase(r.role) ? "user" : r.role);

        return String.format(
                ROW_FMT,
                cut(r.username, W_USERNAME),
                cut(r.email, W_EMAIL),
                cut(r.phone, W_PHONE),
                cut(r.idKey, W_IDKEY),
                cut(roleDisplay, W_ROLE),
                cut(r.createdAt, W_CREATED));
    }

    private static String cut(String s, int w) {
        String v = s == null ? "" : s;
        if (v.length() <= w)
            return v;
        return v.substring(0, w);
    }

    private static List<Row> readAll(Path usersPath) throws IOException {
        List<Row> list = new ArrayList<>();
        if (!Files.exists(usersPath)) {
            return list;
        }
        try (BufferedReader br = Files.newBufferedReader(usersPath, StandardCharsets.UTF_8)) {
            String header = br.readLine();
            if (header == null) {
                return list;
            }
            String[] cols = header.split("\t", -1);
            int iUser = indexOf(cols, "username");
            int iEmail = indexOf(cols, "email");
            int iPhone = indexOf(cols, "phone");
            int iIdKey = indexOf(cols, "idKey");
            int iRole = indexOf(cols, "role");
            int iCreated = indexOf(cols, "createdAt");

            if (iUser < 0 || iEmail < 0 || iPhone < 0 || iIdKey < 0 || iRole < 0 || iCreated < 0) {
                throw new IOException("users.tsv header invalid");
            }

            String line;
            while ((line = br.readLine()) != null) {
                if (line.isEmpty())
                    continue;
                String[] p = line.split("\t", -1);
                String username = field(p, iUser);
                String email = field(p, iEmail);
                String phone = field(p, iPhone);
                String idKey = field(p, iIdKey);
                String role = field(p, iRole);
                String created = field(p, iCreated);

                String createdOut = created;
                try {
                    if (created == null || created.isBlank()) {
                        createdOut = Instant.now().toString();
                    } else {
                        Instant.parse(created);
                    }
                } catch (Exception ignore) {
                    createdOut = Instant.now().toString();
                }

                list.add(new Row(username, email, phone, idKey, role, createdOut));
            }
        }
        return list;
    }

    private static int indexOf(String[] cols, String target) {
        for (int i = 0; i < cols.length; i++) {
            if (target.equals(cols[i]))
                return i;
        }
        return -1;
    }

    private static String field(String[] arr, int idx) {
        if (idx < 0 || idx >= arr.length)
            return "";
        String v = arr[idx];
        return v == null ? "" : v;
    }

    private static final class Row {
        final String username;
        final String email;
        final String phone;
        final String idKey;
        final String role;
        final String createdAt;

        Row(String username, String email, String phone, String idKey, String role, String createdAt) {
            this.username = username;
            this.email = email;
            this.phone = phone;
            this.idKey = idKey;
            this.role = role;
            this.createdAt = createdAt;
        }
    }

    private static Path resolveUsersPath() {
        Path[] candidates = new Path[] {
                Path.of("..", "data", "users.tsv"),
                Path.of("data", "users.tsv"),
                Path.of("..", "..", "data", "users.tsv")
        };
        for (Path p : candidates) {
            try {
                if (Files.exists(p)) {
                    return p.toAbsolutePath().normalize();
                }
            } catch (Exception ignore) {
            }
        }
        return Path.of("data", "users.tsv").toAbsolutePath().normalize();
    }
}
