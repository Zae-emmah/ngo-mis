package org.ngomis;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.*;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.*;
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
  public static void main(String[] a) { SpringApplication.run(Application.class, a); }
  @Bean WebMvcConfigurer cors() { return new WebMvcConfigurer() { public void addCorsMappings(CorsRegistry r) { r.addMapping("/**").allowedOrigins("*").allowedMethods("*"); } }; }
}
@Entity class Org { @Id @GeneratedValue Long id; String name, country, plan = "TRIAL", website = "", address = ""; @Column(length = 1000) String bio = ""; long expiresAt;
  Org() {} Org(String n, String c) { name = n; country = c == null ? "" : c; expiresAt = System.currentTimeMillis() + 14L * 86400000; } }
@Entity class Rec { @Id @GeneratedValue Long id; Long orgId; String type; @Column(length = 4000) String data; Rec() {} Rec(Long o, String t, String d) { orgId = o; type = t; data = d; } }
@Entity class AppUser { @Id @GeneratedValue Long id; Long orgId; @Column(unique = true) String email; String name, role, hash, phone = "", otpHash = ""; boolean active = true, verified = false; int otpTries; long otpExpires, otpSentAt; @Lob String avatar = "";
  AppUser() {} AppUser(Long o, String e, String n, String r, String h) { orgId = o; email = e; name = n; role = r; hash = h; } }
interface OrgRepo extends JpaRepository<Org, Long> {}
interface RecRepo extends JpaRepository<Rec, Long> { List<Rec> findByOrgIdAndType(Long o, String t); }
interface UserRepo extends JpaRepository<AppUser, Long> { Optional<AppUser> findByEmail(String e); List<AppUser> findByOrgId(Long o); }

@RestController @RequestMapping("/api") class Api {
  static final Map<String, int[]> PLANS = new LinkedHashMap<>();
  static { PLANS.put("TRIAL", new int[]{0, 3}); PLANS.put("STARTER", new int[]{29, 10}); PLANS.put("PRO", new int[]{79, 50}); PLANS.put("ENTERPRISE", new int[]{199, 500}); }
  final RecRepo repo; final UserRepo users; final OrgRepo orgs; final ObjectProvider<JavaMailSender> mail; final ObjectMapper om = new ObjectMapper();
  final Map<String, Long> tokens = new ConcurrentHashMap<>(); final SecureRandom rnd = new SecureRandom();
  Api(RecRepo r, UserRepo u, OrgRepo o, ObjectProvider<JavaMailSender> m) { repo = r; users = u; orgs = o; mail = m; }

  static ResponseStatusException err(HttpStatus s) { return new ResponseStatusException(s); }
  AppUser me(String a) {
    if (a == null || !a.startsWith("Bearer ")) throw err(HttpStatus.UNAUTHORIZED);
    Long id = tokens.get(a.substring(7)); if (id == null) throw err(HttpStatus.UNAUTHORIZED);
    return users.findById(id).filter(x -> x.active).orElseThrow(() -> err(HttpStatus.UNAUTHORIZED)); }
  Org org(AppUser u) { return orgs.findById(u.orgId).orElseThrow(); }
  boolean expired(Org o) { return o.expiresAt < System.currentTimeMillis(); }
  long seats(Long orgId) { return users.findByOrgId(orgId).stream().filter(x -> x.active).count(); }
  AppUser writer(String a) { AppUser u = me(a); if (expired(org(u))) throw err(HttpStatus.PAYMENT_REQUIRED); return u; }
  AppUser admin(String a) { AppUser u = me(a); if (!"Administrator".equals(u.role)) throw err(HttpStatus.FORBIDDEN); return u; }
  Map<String, Object> pub(AppUser u) { return Map.of("id", u.id, "name", u.name, "email", u.email, "role", u.role, "phone", u.phone, "active", u.active); }
  static String h(String s) { try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes())); } catch (Exception e) { throw new RuntimeException(e); } }
  @SuppressWarnings("unchecked") Map<String, Object> out(Rec r) { try { Map<String, Object> m = om.readValue(r.data, Map.class); m.put("id", r.id); return m; } catch (Exception e) { throw new RuntimeException(e); } }
  String js(Map<String, Object> m) { try { m.remove("id"); return om.writeValueAsString(m); } catch (Exception e) { throw new RuntimeException(e); } }
  List<Map<String, Object>> rows(Long orgId, String type) { return repo.findByOrgIdAndType(orgId, type).stream().map(this::out).toList(); }
  double num(Object o) { try { return Double.parseDouble(String.valueOf(o)); } catch (Exception e) { return 0; } }
  String cut(String s, int n) { s = s == null ? "" : s.trim(); return s.length() > n ? s.substring(0, n) : s; }

  void sendOtp(AppUser u) {
    String code = String.format("%06d", rnd.nextInt(1_000_000)); long now = System.currentTimeMillis();
    u.otpHash = h(code + u.email); u.otpExpires = now + 10 * 60000L; u.otpTries = 0; u.otpSentAt = now; users.save(u);
    try {
      JavaMailSender s = mail.getIfAvailable(); if (s == null) throw new IllegalStateException("no mail config");
      SimpleMailMessage m = new SimpleMailMessage(); m.setTo(u.email); m.setSubject("Your CiviCore verification code");
      m.setText("Your CiviCore verification code is " + code + ". It expires in 10 minutes.\n\nIf you did not register, ignore this email."); s.send(m);
    } catch (Exception e) { System.out.println("[DEV] Email not sent (" + e.getMessage() + "). OTP for " + u.email + ": " + code); } }

  // ---- tenant data ----
  @GetMapping("/{type}") List<Map<String, Object>> list(@RequestHeader(value = "Authorization", required = false) String a, @PathVariable String type) { return rows(me(a).orgId, type); }
  @PostMapping("/{type}") Map<String, Object> add(@RequestHeader(value = "Authorization", required = false) String a, @PathVariable String type, @RequestBody Map<String, Object> b) {
    AppUser u = writer(a); return out(repo.save(new Rec(u.orgId, type, js(b)))); }
  @PutMapping("/{type}/{id}") Map<String, Object> upd(@RequestHeader(value = "Authorization", required = false) String a, @PathVariable String type, @PathVariable Long id, @RequestBody Map<String, Object> b) {
    AppUser u = writer(a); Rec r = repo.findById(id).filter(x -> u.orgId.equals(x.orgId)).orElseThrow(() -> err(HttpStatus.NOT_FOUND)); r.data = js(b); return out(repo.save(r)); }
  @DeleteMapping("/{type}/{id}") void del(@RequestHeader(value = "Authorization", required = false) String a, @PathVariable String type, @PathVariable Long id) {
    AppUser u = writer(a); repo.findById(id).filter(x -> u.orgId.equals(x.orgId)).ifPresent(repo::delete); }

  // ---- auth + verification ----
  @PostMapping("/auth/register") ResponseEntity2 register(@RequestBody Map<String, String> b) {
    String on = b.get("orgName"), n = b.get("name"), e = b.get("email"), p = b.get("password");
    if (on == null || on.isBlank() || n == null || n.isBlank() || e == null || e.isBlank() || p == null || p.length() < 8) return new ResponseEntity2("invalid");
    if (users.findByEmail(e).isPresent()) return new ResponseEntity2("exists");
    Org o = orgs.save(new Org(on, b.get("country")));
    AppUser u = users.save(new AppUser(o.id, e, n, "Administrator", h(p))); sendOtp(u); return new ResponseEntity2("ok"); }
  @PostMapping("/auth/verify") ResponseEntity2 verify(@RequestBody Map<String, String> b) {
    AppUser u = users.findByEmail(String.valueOf(b.get("email"))).orElseThrow(() -> err(HttpStatus.BAD_REQUEST));
    if (u.verified) return new ResponseEntity2("ok");
    if (u.otpTries >= 5) throw err(HttpStatus.TOO_MANY_REQUESTS);
    if (System.currentTimeMillis() > u.otpExpires || u.otpHash.isEmpty() || !u.otpHash.equals(h(b.get("code") + u.email))) { u.otpTries++; users.save(u); throw err(HttpStatus.BAD_REQUEST); }
    u.verified = true; u.otpHash = ""; users.save(u); return new ResponseEntity2("ok"); }
  @PostMapping("/auth/resend") ResponseEntity2 resend(@RequestBody Map<String, String> b) {
    Optional<AppUser> o = users.findByEmail(String.valueOf(b.get("email")));
    if (o.isEmpty() || o.get().verified) return new ResponseEntity2("ok");
    if (System.currentTimeMillis() - o.get().otpSentAt < 60000) throw err(HttpStatus.TOO_MANY_REQUESTS);
    sendOtp(o.get()); return new ResponseEntity2("ok"); }
  @PostMapping("/auth/signin") Map<String, Object> signin(@RequestBody Map<String, String> b) {
    AppUser u = users.findByEmail(String.valueOf(b.get("email"))).filter(x -> x.active && x.hash.equals(h(String.valueOf(b.get("password"))))).orElseThrow(() -> err(HttpStatus.UNAUTHORIZED));
    if (!u.verified) throw err(HttpStatus.FORBIDDEN);
    String t = UUID.randomUUID().toString(); tokens.put(t, u.id); Org o = org(u);
    return Map.of("token", t, "name", u.name, "email", u.email, "role", u.role, "phone", u.phone, "orgName", o.name, "plan", o.plan, "avatar", u.avatar); }
  @PostMapping("/auth/forgot") ResponseEntity2 forgot(@RequestBody Map<String, String> b) { return new ResponseEntity2("ok"); }
  @PostMapping("/auth/password") ResponseEntity2 pw(@RequestHeader(value = "Authorization", required = false) String a, @RequestBody Map<String, String> b) {
    AppUser u = me(a); if (!u.hash.equals(h(b.get("current")))) throw err(HttpStatus.FORBIDDEN);
    u.hash = h(b.get("next")); users.save(u); return new ResponseEntity2("ok"); }
  @PutMapping("/profile") Map<String, Object> profile(@RequestHeader(value = "Authorization", required = false) String a, @RequestBody Map<String, String> b) {
    AppUser u = me(a); u.name = b.getOrDefault("name", u.name); u.phone = b.getOrDefault("phone", u.phone); return pub(users.save(u)); }

  // ---- account: avatar + organisation profile ----
  @GetMapping("/account") Map<String, Object> account(@RequestHeader(value = "Authorization", required = false) String a) {
    AppUser u = me(a); Org o = org(u);
    return Map.of("name", u.name, "email", u.email, "role", u.role, "avatar", u.avatar,
      "org", Map.of("name", o.name, "bio", o.bio, "website", o.website, "address", o.address, "country", o.country)); }
  @PutMapping("/account/avatar") ResponseEntity2 avatar(@RequestHeader(value = "Authorization", required = false) String a, @RequestBody Map<String, String> b) {
    AppUser u = me(a); String d = b.getOrDefault("avatar", "");
    if (!d.isEmpty() && (!d.startsWith("data:image/") || d.length() > 400_000)) throw err(HttpStatus.BAD_REQUEST);
    u.avatar = d; users.save(u); return new ResponseEntity2("ok"); }
  @PutMapping("/account/org") ResponseEntity2 saveOrg(@RequestHeader(value = "Authorization", required = false) String a, @RequestBody Map<String, String> b) {
    AppUser u = admin(a); Org o = org(u);
    if (b.get("name") != null && !b.get("name").isBlank()) o.name = cut(b.get("name"), 120);
    o.bio = cut(b.get("bio"), 1000); o.website = cut(b.get("website"), 200); o.address = cut(b.get("address"), 200); o.country = cut(b.get("country"), 80);
    orgs.save(o); return new ResponseEntity2("ok"); }

  // ---- members (administrator only) ----
  @GetMapping("/users") List<Map<String, Object>> allUsers(@RequestHeader(value = "Authorization", required = false) String a) {
    return users.findByOrgId(admin(a).orgId).stream().map(this::pub).toList(); }
  @PostMapping("/users/invite") Map<String, String> invite(@RequestHeader(value = "Authorization", required = false) String a, @RequestBody Map<String, String> b) {
    AppUser u = admin(a); Org o = org(u);
    if (b.get("email") == null || b.get("name") == null) throw err(HttpStatus.BAD_REQUEST);
    if (expired(o) || seats(u.orgId) >= PLANS.get(o.plan)[1]) throw err(HttpStatus.PAYMENT_REQUIRED);
    if (users.findByEmail(b.get("email")).isPresent()) throw err(HttpStatus.CONFLICT);
    String t = UUID.randomUUID().toString().substring(0, 8);
    AppUser nu = new AppUser(u.orgId, b.get("email"), b.get("name"), b.getOrDefault("role", "Program Officer"), h(t)); nu.verified = true; users.save(nu);
    return Map.of("tempPassword", t); }
  @PutMapping("/users/{id}") Map<String, Object> updUser(@RequestHeader(value = "Authorization", required = false) String a, @PathVariable Long id, @RequestBody Map<String, Object> b) {
    AppUser adm = admin(a); AppUser t = users.findById(id).filter(x -> adm.orgId.equals(x.orgId)).orElseThrow(() -> err(HttpStatus.NOT_FOUND));
    if (t.id.equals(adm.id)) throw err(HttpStatus.BAD_REQUEST);
    if (b.get("role") != null) t.role = b.get("role").toString();
    if (b.get("active") != null) t.active = (Boolean) b.get("active");
    return pub(users.save(t)); }

  // ---- subscription ----
  @GetMapping("/plans") List<Map<String, Object>> plans() {
    List<Map<String, Object>> l = new ArrayList<>(); PLANS.forEach((k, v) -> l.add(Map.of("id", k, "price", v[0], "users", v[1]))); return l; }
  @GetMapping("/subscription") Map<String, Object> sub(@RequestHeader(value = "Authorization", required = false) String a) {
    AppUser u = me(a); Org o = org(u);
    return Map.of("org", o.name, "plan", o.plan, "expiresAt", o.expiresAt, "expired", expired(o), "userLimit", PLANS.get(o.plan)[1], "userCount", seats(u.orgId)); }
  @PostMapping("/subscription/subscribe") Map<String, Object> subscribe(@RequestHeader(value = "Authorization", required = false) String a, @RequestBody Map<String, String> b) {
    AppUser u = admin(a); Org o = org(u); String p = b.get("plan");
    if (p == null || !PLANS.containsKey(p) || p.equals("TRIAL")) throw err(HttpStatus.BAD_REQUEST);
    if (seats(u.orgId) > PLANS.get(p)[1]) throw err(HttpStatus.CONFLICT);
    long base = Math.max(System.currentTimeMillis(), o.expiresAt);
    o.plan = p; o.expiresAt = base + 30L * 86400000; orgs.save(o); return sub(a); }

  // ---- AI report ----
  @SuppressWarnings("unchecked") @PostMapping("/ai/report") Map<String, String> report(@RequestHeader(value = "Authorization", required = false) String a, @RequestBody Map<String, Object> b) {
    Long oid = me(a).orgId; List<String> sec = (List<String>) b.getOrDefault("sections", List.of());
    var pr = rows(oid, "projects"); var be = rows(oid, "beneficiaries"); var in = rows(oid, "indicators");
    double bud = pr.stream().mapToDouble(p -> num(p.get("budget"))).sum();
    long risk = pr.stream().filter(p -> "At Risk".equals(p.get("status"))).count();
    StringBuilder s = new StringBuilder("Monthly Report - " + b.get("period") + "\n\n");
    if (sec.contains("Executive Summary")) s.append("EXECUTIVE SUMMARY\n" + pr.size() + " projects reached " + be.size() + " beneficiaries. " + risk + " project(s) at risk.\n\n");
    if (sec.contains("Project Progress")) { s.append("PROJECT PROGRESS\n"); pr.forEach(p -> s.append("- " + p.get("name") + ": " + p.get("status") + "\n")); s.append("\n"); }
    if (sec.contains("Financial Summary")) s.append("FINANCIAL SUMMARY\nTotal budget: $" + String.format("%,.0f", bud) + "\n\n");
    if (sec.contains("M&E Indicators")) { s.append("M&E INDICATORS\n"); in.forEach(i -> s.append("- " + i.get("name") + ": " + i.get("current") + "/" + i.get("target") + "\n")); s.append("\n"); }
    if (sec.contains("Challenges & Risks")) s.append("CHALLENGES & RISKS\n" + (risk > 0 ? "Review at-risk projects for delays." : "No major risks flagged.") + "\n");
    return Map.of("text", s.toString()); }
}
record ResponseEntity2(String status) {}