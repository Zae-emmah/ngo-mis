
package org.ngomis;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.http.HttpStatus;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.config.annotation.*;

@SpringBootApplication
public class Application {

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }

    @Bean
    WebMvcConfigurer cors() {
        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(CorsRegistry r) {
                r.addMapping("/**")
                    .allowedOrigins(System.getenv().getOrDefault("ALLOWED_ORIGINS", "*").split(","))
                    .allowedMethods("*");
            }
        };
    }
}

// =====================================================
// ORGANISATION ENTITY
// =====================================================

@Entity
class Org {

    @Id
    @GeneratedValue
    Long id;

    String name;
    String country;
    String plan = "TRIAL";
    String website = "";
    String address = "";

    @Column(length = 1000)
    String bio = "";

    long expiresAt;

    Org() {}

    Org(String n, String c) {
        name = n;
        country = c == null ? "" : c;
        expiresAt = System.currentTimeMillis() + 14L * 86400000;
    }
}

// =====================================================
// TENANT RECORD ENTITY
// =====================================================

@Entity
class Rec {

    @Id
    @GeneratedValue
    Long id;

    Long orgId;
    String type;

    @Column(length = 4000)
    String data;

    Rec() {}

    Rec(Long o, String t, String d) {
        orgId = o;
        type = t;
        data = d;
    }
}

// =====================================================
// USER ENTITY
// =====================================================

@Entity
class AppUser {

    @Id
    @GeneratedValue
    Long id;

    Long orgId;

    @Column(unique = true)
    String email;

    String name;
    String role;
    String hash;
    String phone = "";
    String otpHash = "";

    boolean active = true;
    boolean verified = false;

    int otpTries;

    long otpExpires;
    long otpSentAt;

    String resetHash;
    Long resetExpires;

    @Column(columnDefinition = "text")
    String avatar = "";

    AppUser() {}

    AppUser(Long o, String e, String n, String r, String h) {
        orgId = o;
        email = e;
        name = n;
        role = r;
        hash = h;
    }
}

// =====================================================
// REPOSITORIES
// =====================================================

interface OrgRepo extends JpaRepository<Org, Long> {}

interface RecRepo extends JpaRepository<Rec, Long> {
    List<Rec> findByOrgIdAndType(Long o, String t);
}

interface UserRepo extends JpaRepository<AppUser, Long> {

    Optional<AppUser> findByEmail(String e);

    List<AppUser> findByOrgId(Long o);
}

// =====================================================
// API CONTROLLER
// =====================================================

@RestController
@RequestMapping("/api")
class Api {

    static final Map<String, int[]> PLANS = new LinkedHashMap<>();

    static {
        PLANS.put("TRIAL", new int[]{0, 3});
        PLANS.put("STARTER", new int[]{29, 10});
        PLANS.put("PRO", new int[]{79, 50});
        PLANS.put("ENTERPRISE", new int[]{199, 500});
    }

    final RecRepo repo;
    final UserRepo users;
    final OrgRepo orgs;

    final ObjectProvider<JavaMailSender> mail;

    final ObjectMapper om = new ObjectMapper();

    final Map<String, long[]> tokens = new ConcurrentHashMap<>();

    final SecureRandom rnd = new SecureRandom();

    Api(
        RecRepo r,
        UserRepo u,
        OrgRepo o,
        ObjectProvider<JavaMailSender> m
    ) {
        repo = r;
        users = u;
        orgs = o;
        mail = m;
    }

    // =================================================
    // GENERAL HELPERS
    // =================================================

    static final org.springframework.security.crypto.password.PasswordEncoder ENC = new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder();
    final Map<String, long[]> fails = new ConcurrentHashMap<>();
    static final Map<String, Set<String>> WRITE = Map.of(
        "projects", Set.of("Program Manager", "Program Officer"),
        "beneficiaries", Set.of("Program Manager", "Program Officer", "M&E Officer"),
        "indicators", Set.of("Program Manager", "M&E Officer"),
        "employees", Set.of("HR Officer"),
        "assets", Set.of("Program Manager", "Finance Officer"),
        "donors", Set.of("Program Manager", "Finance Officer"),
        "org", Set.<String>of());
    static String hp(String raw) { return ENC.encode(raw); }
    static boolean ok(AppUser u, String raw) { return u.hash != null && raw != null && (u.hash.startsWith("$2") ? ENC.matches(raw, u.hash) : u.hash.equals(h(raw))); }
    static void mod(String type) { if (!WRITE.containsKey(type)) throw err(HttpStatus.NOT_FOUND); }
    AppUser writer(String a, String type) { mod(type); AppUser u = writer(a); if (!"Administrator".equals(u.role) && !WRITE.get(type).contains(u.role)) throw err(HttpStatus.FORBIDDEN); return u; }

    static String s(String v) { return v == null ? "" : v; }

    static ResponseStatusException err(HttpStatus s) {
        return new ResponseStatusException(s);
    }

    AppUser me(String a) {

        if (a == null || !a.startsWith("Bearer ")) {
            throw err(HttpStatus.UNAUTHORIZED);
        }

        long[] tk = tokens.get(a.substring(7));

        if (tk == null || tk[1] < System.currentTimeMillis()) {
            tokens.remove(a.substring(7));
            throw err(HttpStatus.UNAUTHORIZED);
        }

        Long id = tk[0];

        return users.findById(id)
            .filter(x -> x.active)
            .orElseThrow(() -> err(HttpStatus.UNAUTHORIZED));
    }

    Org org(AppUser u) {
        return orgs.findById(u.orgId).orElseThrow();
    }

    boolean expired(Org o) {
        return o.expiresAt < System.currentTimeMillis();
    }

    long seats(Long orgId) {
        return users.findByOrgId(orgId)
            .stream()
            .filter(x -> x.active)
            .count();
    }

    AppUser writer(String a) {

        AppUser u = me(a);

        if (expired(org(u))) {
            throw err(HttpStatus.PAYMENT_REQUIRED);
        }

        return u;
    }

    AppUser admin(String a) {

        AppUser u = me(a);

        if (!"Administrator".equals(u.role)) {
            throw err(HttpStatus.FORBIDDEN);
        }

        return u;
    }

    Map<String, Object> pub(AppUser u) {

        return Map.of(
            "id", u.id,
            "name", u.name,
            "email", u.email,
            "role", u.role,
            "phone", s(u.phone),
            "active", u.active
        );
    }

    static String h(String s) {

        try {

            return HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256")
                    .digest(s.getBytes(StandardCharsets.UTF_8))
            );

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @SuppressWarnings("unchecked")
    Map<String, Object> out(Rec r) {

        try {

            Map<String, Object> m =
                om.readValue(r.data, Map.class);

            m.put("id", r.id);

            return m;

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    String js(Map<String, Object> m) {

        try {

            m.remove("id");

            return om.writeValueAsString(m);

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    List<Map<String, Object>> rows(Long orgId, String type) {

        return repo.findByOrgIdAndType(orgId, type)
            .stream()
            .map(this::out)
            .toList();
    }

    double num(Object o) {

        try {
            return Double.parseDouble(String.valueOf(o));
        } catch (Exception e) {
            return 0;
        }
    }

    String cut(String s, int n) {

        s = s == null ? "" : s.trim();

        return s.length() > n ? s.substring(0, n) : s;
    }

    // =================================================
    // EMAIL VERIFICATION
    // =================================================

    void sendOtp(AppUser u) {
        String code = String.format("%06d", rnd.nextInt(1_000_000));
        long now = System.currentTimeMillis();
        u.otpHash = h(code + u.email);
        u.otpExpires = now + 10 * 60 * 1000L;
        u.otpTries = 0;
        u.otpSentAt = now;
        users.save(u);
        sendMail(u.email, "NGO-MIS - Email Verification", "Hello " + u.name + ",\n\nYour NGO-MIS verification code is: " + code + "\n\nIt expires in 10 minutes. If you did not create this account, ignore this email.");
    }

    void sendMail(String to, String subject, String text) {
        String key = System.getenv("BREVO_API_KEY"), from = System.getenv("MAIL_FROM");
        if (key == null || from == null) {
            System.err.println("BREVO_API_KEY / MAIL_FROM not set");
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Email service is not configured.");
        }
        try {
            String body = om.writeValueAsString(Map.of(
                "sender", Map.of("name", "NGO-MIS", "email", from),
                "to", List.of(Map.of("email", to)),
                "subject", subject, "textContent", text));
            var res = java.net.http.HttpClient.newHttpClient().send(
                java.net.http.HttpRequest.newBuilder(java.net.URI.create("https://api.brevo.com/v3/smtp/email"))
                    .header("api-key", key).header("content-type", "application/json")
                    .POST(java.net.http.HttpRequest.BodyPublishers.ofString(body)).build(),
                java.net.http.HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() >= 300) throw new RuntimeException("Brevo status " + res.statusCode());
        } catch (Exception e) {
            System.err.println("Email delivery failed: " + e.getMessage());
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "We could not send the email.");
        }
    }

    // =================================================
    // TENANT DATA
    // =================================================

    @GetMapping("/{type}")
    List<Map<String, Object>> list(
        @RequestHeader(value = "Authorization", required = false) String a,
        @PathVariable String type
    ) {

        AppUser u = me(a);
        mod(type);
        return rows(u.orgId, type);
    }

    @PostMapping("/{type}")
    Map<String, Object> add(
        @RequestHeader(value = "Authorization", required = false) String a,
        @PathVariable String type,
        @RequestBody Map<String, Object> b
    ) {

        AppUser u = writer(a, type);

        return out(repo.save(
            new Rec(u.orgId, type, js(b))
        ));
    }

    @PutMapping("/{type}/{id}")
    Map<String, Object> upd(
        @RequestHeader(value = "Authorization", required = false) String a,
        @PathVariable String type,
        @PathVariable Long id,
        @RequestBody Map<String, Object> b
    ) {

        AppUser u = writer(a, type);

        Rec r = repo.findById(id)
            .filter(x -> u.orgId.equals(x.orgId) && type.equals(x.type))
            .orElseThrow(() -> err(HttpStatus.NOT_FOUND));

        r.data = js(b);

        return out(repo.save(r));
    }

    @DeleteMapping("/{type}/{id}")
    void del(
        @RequestHeader(value = "Authorization", required = false) String a,
        @PathVariable String type,
        @PathVariable Long id
    ) {

        AppUser u = writer(a, type);

        repo.findById(id)
            .filter(x -> u.orgId.equals(x.orgId) && type.equals(x.type))
            .ifPresent(repo::delete);
    }

    // =================================================
    // REGISTRATION
    // =================================================

    @PostMapping("/auth/register")
    ResponseEntity2 register(
        @RequestBody Map<String, String> b
    ) {

        String on = b.get("orgName");
        String n = b.get("name");
        String e = b.get("email");
        String p = b.get("password");

        if (
            on == null || on.isBlank() ||
            n == null || n.isBlank() ||
            e == null || e.isBlank() ||
            p == null || p.length() < 8
        ) {
            return new ResponseEntity2("invalid");
        }

        e = e.trim().toLowerCase(Locale.ROOT);

        if (users.findByEmail(e).isPresent()) {
            return new ResponseEntity2("exists");
        }

        Org o = orgs.save(
            new Org(on, b.get("country"))
        );

        AppUser u = users.save(
            new AppUser(
                o.id,
                e,
                n,
                "Administrator",
                hp(p)
            )
        );

        sendOtp(u);

        return new ResponseEntity2("ok");
    }

    // =================================================
    // VERIFY EMAIL
    // =================================================

    @PostMapping("/auth/verify")
    ResponseEntity2 verify(
        @RequestBody Map<String, String> b
    ) {

        String email = String.valueOf(b.get("email"))
            .trim()
            .toLowerCase(Locale.ROOT);

        String code = b.get("code");

        AppUser u = users.findByEmail(email)
            .orElseThrow(() -> err(HttpStatus.BAD_REQUEST));

        if (u.verified) {
            return new ResponseEntity2("ok");
        }

        if (u.otpTries >= 5) {
            throw err(HttpStatus.TOO_MANY_REQUESTS);
        }

        if (
            code == null ||
            System.currentTimeMillis() > u.otpExpires ||
            s(u.otpHash).isEmpty() ||
            !u.otpHash.equals(h(code + u.email))
        ) {

            u.otpTries++;

            users.save(u);

            throw err(HttpStatus.BAD_REQUEST);
        }

        u.verified = true;
        u.otpHash = "";
        u.otpExpires = 0;

        users.save(u);

        return new ResponseEntity2("ok");
    }

    // =================================================
    // RESEND VERIFICATION EMAIL
    // =================================================

    @PostMapping("/auth/resend")
    ResponseEntity2 resend(
        @RequestBody Map<String, String> b
    ) {

        String email = String.valueOf(b.get("email"))
            .trim()
            .toLowerCase(Locale.ROOT);

        Optional<AppUser> o = users.findByEmail(email);

        if (o.isEmpty() || o.get().verified) {
            return new ResponseEntity2("ok");
        }

        if (
            System.currentTimeMillis() -
            o.get().otpSentAt < 60000
        ) {

            throw err(HttpStatus.TOO_MANY_REQUESTS);
        }

        sendOtp(o.get());

        return new ResponseEntity2("ok");
    }

    // =================================================
    // SIGN IN
    // =================================================

    @PostMapping("/auth/signin")
    Map<String, Object> signin(
        @RequestBody Map<String, String> b
    ) {

        String email = String.valueOf(b.get("email"))
            .trim()
            .toLowerCase(Locale.ROOT);

        long[] lk = fails.get(email);
        if (lk != null && lk[1] <= System.currentTimeMillis()) { fails.remove(email); lk = null; }
        if (lk != null && lk[0] >= 5) throw err(HttpStatus.TOO_MANY_REQUESTS);

        AppUser u = users.findByEmail(email)
            .filter(x -> x.active && ok(x, String.valueOf(b.get("password"))))
            .orElseThrow(() -> {
                long[] g = fails.computeIfAbsent(email, k -> new long[2]);
                g[0]++; g[1] = System.currentTimeMillis() + 10 * 60000L;
                return err(HttpStatus.UNAUTHORIZED);
            });

        fails.remove(email);
        if (!u.hash.startsWith("$2")) { u.hash = hp(String.valueOf(b.get("password"))); users.save(u); }

        if (!u.verified) {
            throw err(HttpStatus.FORBIDDEN);
        }

        String t = UUID.randomUUID().toString();

        tokens.put(t, new long[]{u.id, System.currentTimeMillis() + 12L * 3600000L});

        Org o = org(u);

        return Map.of(
            "token", t,
            "name", u.name,
            "email", u.email,
            "role", u.role,
            "phone", s(u.phone),
            "orgName", o.name,
            "plan", o.plan,
            "avatar", s(u.avatar)
        );
    }

    @PostMapping("/auth/forgot")
    ResponseEntity2 forgot(@RequestBody Map<String, String> b) {
        String email = String.valueOf(b.get("email")).trim().toLowerCase(Locale.ROOT);
        users.findByEmail(email).filter(x -> x.active && x.verified).ifPresent(u -> {
            String t = UUID.randomUUID().toString().replace("-", "");
            u.resetHash = h(t);
            u.resetExpires = System.currentTimeMillis() + 3600000L;
            users.save(u);
            String base = System.getenv().getOrDefault("FRONTEND_URL", "http://localhost:5173");
            try {
                sendMail(u.email, "NGO-MIS - Reset your password", "Use this link to choose a new password (valid for 1 hour):\n\n" + base + "/reset?email=" + java.net.URLEncoder.encode(u.email, java.nio.charset.StandardCharsets.UTF_8) + "&token=" + t + "\n\nIf you did not ask for this, ignore this email.");
            } catch (Exception e) { System.err.println("Reset email failed: " + e.getMessage()); }
        });
        return new ResponseEntity2("ok");
    }

    @PostMapping("/auth/reset")
    ResponseEntity2 reset(@RequestBody Map<String, String> b) {
        String email = String.valueOf(b.get("email")).trim().toLowerCase(Locale.ROOT);
        String np = b.get("password"), t = String.valueOf(b.get("token"));
        if (np == null || np.length() < 8) throw err(HttpStatus.BAD_REQUEST);
        AppUser u = users.findByEmail(email).orElseThrow(() -> err(HttpStatus.BAD_REQUEST));
        if (u.resetHash == null || u.resetExpires == null || u.resetExpires < System.currentTimeMillis() || !u.resetHash.equals(h(t))) throw err(HttpStatus.BAD_REQUEST);
        u.hash = hp(np); u.resetHash = null; u.resetExpires = null; users.save(u);
        tokens.values().removeIf(v -> v[0] == u.id);
        return new ResponseEntity2("ok");
    }

    @PostMapping("/auth/password")
    ResponseEntity2 pw(
        @RequestHeader(value = "Authorization", required = false) String a,
        @RequestBody Map<String, String> b
    ) {

        AppUser u = me(a);

        if (!ok(u, b.get("current")) || b.get("next") == null || b.get("next").length() < 8) {
            throw err(HttpStatus.FORBIDDEN);
        }

        u.hash = hp(b.get("next"));

        users.save(u);

        return new ResponseEntity2("ok");
    }

    @PutMapping("/profile")
    Map<String, Object> profile(
        @RequestHeader(value = "Authorization", required = false) String a,
        @RequestBody Map<String, String> b
    ) {

        AppUser u = me(a);

        u.name = b.getOrDefault("name", u.name);
        u.phone = b.getOrDefault("phone", s(u.phone));

        return pub(users.save(u));
    }

    // =================================================
    // ACCOUNT PROFILE
    // =================================================

    @GetMapping("/account")
    Map<String, Object> account(
        @RequestHeader(value = "Authorization", required = false) String a
    ) {

        AppUser u = me(a);
        Org o = org(u);

        return Map.of(
            "name", u.name,
            "email", u.email,
            "role", u.role,
            "avatar", s(u.avatar),
            "org", Map.of(
                "name", o.name,
                "bio", s(o.bio),
                "website", s(o.website),
                "address", s(o.address),
                "country", s(o.country)
            )
        );
    }

    @PutMapping("/account/avatar")
    ResponseEntity2 avatar(
        @RequestHeader(value = "Authorization", required = false) String a,
        @RequestBody Map<String, String> b
    ) {

        AppUser u = me(a);

        String d = b.getOrDefault("avatar", "");

        if (
            !d.isEmpty() &&
            (!d.startsWith("data:image/") || d.length() > 400_000)
        ) {
            throw err(HttpStatus.BAD_REQUEST);
        }

        u.avatar = d;

        users.save(u);

        return new ResponseEntity2("ok");
    }

    @PutMapping("/account/org")
    ResponseEntity2 saveOrg(
        @RequestHeader(value = "Authorization", required = false) String a,
        @RequestBody Map<String, String> b
    ) {

        AppUser u = admin(a);
        Org o = org(u);

        if (
            b.get("name") != null &&
            !b.get("name").isBlank()
        ) {
            o.name = cut(b.get("name"), 120);
        }

        o.bio = cut(b.get("bio"), 1000);
        o.website = cut(b.get("website"), 200);
        o.address = cut(b.get("address"), 200);
        o.country = cut(b.get("country"), 80);

        orgs.save(o);

        return new ResponseEntity2("ok");
    }

    // =================================================
    // ORGANISATION MEMBERS
    // =================================================

    @GetMapping("/users")
    List<Map<String, Object>> allUsers(
        @RequestHeader(value = "Authorization", required = false) String a
    ) {

        return users.findByOrgId(admin(a).orgId)
            .stream()
            .map(this::pub)
            .toList();
    }

    @PostMapping("/users/invite")
    Map<String, String> invite(
        @RequestHeader(value = "Authorization", required = false) String a,
        @RequestBody Map<String, String> b
    ) {

        AppUser u = admin(a);
        Org o = org(u);

        if (b.get("email") == null || b.get("name") == null) {
            throw err(HttpStatus.BAD_REQUEST);
        }

        if (
            expired(o) ||
            seats(u.orgId) >= PLANS.get(o.plan)[1]
        ) {
            throw err(HttpStatus.PAYMENT_REQUIRED);
        }

        String email = b.get("email").trim().toLowerCase(Locale.ROOT);

        if (users.findByEmail(email).isPresent()) {
            throw err(HttpStatus.CONFLICT);
        }

        String t = UUID.randomUUID().toString().substring(0, 8);

        AppUser nu = new AppUser(
            u.orgId,
            email,
            b.get("name"),
            b.getOrDefault("role", "Program Officer"),
            hp(t)
        );

        nu.verified = true;

        users.save(nu);

        return Map.of("tempPassword", t);
    }

    @PutMapping("/users/{id}")
    Map<String, Object> updUser(
        @RequestHeader(value = "Authorization", required = false) String a,
        @PathVariable Long id,
        @RequestBody Map<String, Object> b
    ) {

        AppUser adm = admin(a);

        AppUser t = users.findById(id)
            .filter(x -> adm.orgId.equals(x.orgId))
            .orElseThrow(() -> err(HttpStatus.NOT_FOUND));

        if (t.id.equals(adm.id)) {
            throw err(HttpStatus.BAD_REQUEST);
        }

        if (b.get("role") != null) {
            t.role = b.get("role").toString();
        }

        if (b.get("active") != null) {
            t.active = (Boolean) b.get("active");
        }

        return pub(users.save(t));
    }

    // =================================================
    // SUBSCRIPTIONS
    // =================================================

    @GetMapping("/plans")
    List<Map<String, Object>> plans() {

        List<Map<String, Object>> l = new ArrayList<>();

        PLANS.forEach((k, v) ->
            l.add(Map.of(
                "id", k,
                "price", v[0],
                "users", v[1]
            ))
        );

        return l;
    }

    @GetMapping("/subscription")
    Map<String, Object> sub(
        @RequestHeader(value = "Authorization", required = false) String a
    ) {

        AppUser u = me(a);
        Org o = org(u);

        return Map.of(
            "org", o.name,
            "plan", o.plan,
            "expiresAt", o.expiresAt,
            "expired", expired(o),
            "userLimit", PLANS.get(o.plan)[1],
            "userCount", seats(u.orgId)
        );
    }

    @PostMapping("/subscription/subscribe")
    Map<String, Object> subscribe(
        @RequestHeader(value = "Authorization", required = false) String a,
        @RequestBody Map<String, String> b
    ) {

        if (!"true".equals(System.getenv("ALLOW_DEMO_SUBSCRIBE"))) {
            throw new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED, "Online payments are not enabled yet.");
        }

        AppUser u = admin(a);
        Org o = org(u);

        String p = b.get("plan");

        if (
            p == null ||
            !PLANS.containsKey(p) ||
            p.equals("TRIAL")
        ) {
            throw err(HttpStatus.BAD_REQUEST);
        }

        if (seats(u.orgId) > PLANS.get(p)[1]) {
            throw err(HttpStatus.CONFLICT);
        }

        long base = Math.max(
            System.currentTimeMillis(),
            o.expiresAt
        );

        o.plan = p;
        o.expiresAt = base + 30L * 86400000;

        orgs.save(o);

        return sub(a);
    }

    // =================================================
    // AI REPORT
    // =================================================

    @SuppressWarnings("unchecked")
    @PostMapping("/ai/report")
    Map<String, String> report(
        @RequestHeader(value = "Authorization", required = false) String a,
        @RequestBody Map<String, Object> b
    ) {

        Long oid = me(a).orgId;

        List<String> sec =
            (List<String>) b.getOrDefault("sections", List.of());

        var pr = rows(oid, "projects");
        var be = rows(oid, "beneficiaries");
        var in = rows(oid, "indicators");

        double bud = pr.stream()
            .mapToDouble(p -> num(p.get("budget")))
            .sum();

        long risk = pr.stream()
            .filter(p -> "At Risk".equals(p.get("status")))
            .count();

        StringBuilder s = new StringBuilder(
            "Monthly Report - " + b.get("period") + "\n\n"
        );

        if (sec.contains("Executive Summary")) {

            s.append(
                "EXECUTIVE SUMMARY\n" +
                pr.size() + " projects reached " +
                be.size() + " beneficiaries. " +
                risk + " project(s) at risk.\n\n"
            );
        }

        if (sec.contains("Project Progress")) {

            s.append("PROJECT PROGRESS\n");

            pr.forEach(p ->
                s.append(
                    "- " + p.get("name") +
                    ": " + p.get("status") + "\n"
                )
            );

            s.append("\n");
        }

        if (sec.contains("Financial Summary")) {

            s.append(
                "FINANCIAL SUMMARY\n" +
                "Total budget: $" +
                String.format("%,.0f", bud) +
                "\n\n"
            );
        }

        if (sec.contains("M&E Indicators")) {

            s.append("M&E INDICATORS\n");

            in.forEach(i ->
                s.append(
                    "- " + i.get("name") +
                    ": " + i.get("current") +
                    "/" + i.get("target") + "\n"
                )
            );

            s.append("\n");
        }

        if (sec.contains("Challenges & Risks")) {

            s.append(
                "CHALLENGES & RISKS\n" +
                (risk > 0
                    ? "Review at-risk projects for delays."
                    : "No major risks flagged.") +
                "\n"
            );
        }

        return Map.of("text", s.toString());
    }
}

// =====================================================
// STANDARD API RESPONSE
// =====================================================

record ResponseEntity2(String status) {}
