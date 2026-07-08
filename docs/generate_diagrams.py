#!/usr/bin/env python3
"""
Generates the three architecture diagrams embedded in README.md as SVG files
under docs/images/. Every element is traced from the real code:

  - auth-flow.svg   : AuthController / AuthService / RefreshTokenService /
                      JwtService / JwtAuthenticationFilter (register -> login ->
                      authenticated request -> refresh rotation -> logout).
  - authz-model.svg : SecurityConfig request matchers + @PreAuthorize on
                      UserController + Role enum + tokenVersion revocation.
  - erd.svg         : db/migration V1..V9 + User / RefreshToken / AuditLog
                      entities.

Run: python3 docs/generate_diagrams.py
"""

import html

FONT = "font-family='DejaVu Sans, Segoe UI, Helvetica, Arial, sans-serif'"
MONO = "font-family='DejaVu Sans Mono, Consolas, monospace'"


def esc(s):
    return html.escape(str(s), quote=True)


def svg_header(w, h, title):
    return (
        f"<svg xmlns='http://www.w3.org/2000/svg' width='{w}' height='{h}' "
        f"viewBox='0 0 {w} {h}' role='img' aria-label='{esc(title)}'>\n"
        f"<rect width='{w}' height='{h}' fill='#ffffff'/>\n"
        "<defs>"
        "<marker id='arrow' markerWidth='9' markerHeight='9' refX='8' refY='3' "
        "orient='auto' markerUnits='strokeWidth'>"
        "<path d='M0,0 L8,3 L0,6 z' fill='#334155'/></marker>"
        "<marker id='arrowback' markerWidth='9' markerHeight='9' refX='8' refY='3' "
        "orient='auto' markerUnits='strokeWidth'>"
        "<path d='M0,0 L8,3 L0,6 z' fill='#0d9488'/></marker>"
        "</defs>\n"
    )


def box(x, y, w, h, text, fill="#eef2ff", stroke="#6366f1", tcolor="#1e293b",
        fs=13, rx=8, bold=False):
    weight = "bold" if bold else "normal"
    lines = text.split("\n")
    out = (f"<rect x='{x}' y='{y}' width='{w}' height='{h}' rx='{rx}' "
           f"fill='{fill}' stroke='{stroke}' stroke-width='1.5'/>\n")
    n = len(lines)
    start = y + h / 2 - (n - 1) * (fs + 3) / 2 + fs / 2 - 1
    for i, ln in enumerate(lines):
        out += (f"<text x='{x + w/2}' y='{start + i*(fs+3)}' {FONT} "
                f"font-size='{fs}' font-weight='{weight}' fill='{tcolor}' "
                f"text-anchor='middle'>{esc(ln)}</text>\n")
    return out


def text(x, y, s, fs=13, color="#1e293b", anchor="start", bold=False, mono=False):
    weight = "bold" if bold else "normal"
    f = MONO if mono else FONT
    return (f"<text x='{x}' y='{y}' {f} font-size='{fs}' font-weight='{weight}' "
            f"fill='{color}' text-anchor='{anchor}'>{esc(s)}</text>\n")


def line(x1, y1, x2, y2, color="#334155", w=1.5, dash=None, marker="arrow"):
    d = f" stroke-dasharray='{dash}'" if dash else ""
    m = f" marker-end='url(#{marker})'" if marker else ""
    return (f"<line x1='{x1}' y1='{y1}' x2='{x2}' y2='{y2}' stroke='{color}' "
            f"stroke-width='{w}'{d}{m}/>\n")


# ---------------------------------------------------------------------------
# 1. AUTH FLOW (sequence)
# ---------------------------------------------------------------------------
def auth_flow():
    W, H = 1180, 900
    s = svg_header(W, H, "Authentication flow")
    s += text(24, 34, "Authentication & Token Lifecycle", 20, "#0f172a", bold=True)
    s += text(24, 56, "Traced from AuthController / AuthService / RefreshTokenService / "
                      "JwtService / JwtAuthenticationFilter", 12, "#475569")

    actors = [
        ("Client", "Client", 90),
        ("JwtAuthFilter", "JwtAuthFilter\n+ RateLimitFilter", 300),
        ("Auth", "Auth / User\nService", 540),
        ("Argon2", "Argon2 +\nUserRepository", 760),
        ("RefreshTokenSvc", "RefreshTokenSvc\n+ JwtService", 990),
    ]
    top, bot = 84, 860
    xs = {}
    for key, label, x in actors:
        xs[key] = x
        s += box(x - 78, top, 156, 46, label, fill="#0f172a", stroke="#0f172a",
                 tcolor="#ffffff", fs=12, bold=True)
        s += line(x, top + 46, x, bot, color="#cbd5e1", w=1.2, marker=None, dash="4 4")

    y = 150

    def msg(a, b, label, yy, color="#334155", marker="arrow", dashed=False):
        x1, x2 = xs[a], xs[b]
        out = line(x1, yy, x2, yy, color=color, marker=marker,
                   dash="5 3" if dashed else None)
        mid = (x1 + x2) / 2
        out += text(mid, yy - 6, label, 11, "#0f172a", anchor="middle")
        return out

    def note(yy, label, color="#0d9488"):
        return text(24, yy, label, 12, color, bold=True)

    s += note(y, "① REGISTER  POST /api/v1/auth/register")
    y += 24
    s += msg("Client", "Auth", "RegisterRequest (@Valid: 12+ char password)", y); y += 34
    s += msg("Auth", "Argon2", "Argon2 encode(password) + saveAndFlush(user, ROLE_USER)", y); y += 34
    s += msg("Auth", "RefreshTokenSvc", "issueTokens: access+refresh JWT (tokenVersion=0)", y); y += 34
    s += msg("RefreshTokenSvc", "Auth", "store SHA-256(refresh) row", y, color="#0d9488",
             marker="arrowback", dashed=True); y += 30
    s += msg("Auth", "Client", "201 { accessToken, refreshToken, user }", y, color="#0d9488",
             marker="arrowback", dashed=True); y += 40

    s += note(y, "② LOGIN  POST /api/v1/auth/login   (constant-time; audited)")
    y += 24
    s += msg("Client", "Auth", "LoginRequest { email, password }", y); y += 32
    s += msg("Auth", "Argon2", "findByEmail; ALWAYS Argon2 matches (dummy hash if unknown)", y); y += 32
    s += text(555, y, "reject uniformly if unknown / disabled / locked; lockout after N fails",
              11, "#b91c1c"); y += 26
    s += msg("Auth", "RefreshTokenSvc", "on success: clear failures + issueTokens", y); y += 30
    s += msg("Auth", "Client", "200 { accessToken (15m), refreshToken (7d) }", y,
             color="#0d9488", marker="arrowback", dashed=True); y += 40

    s += note(y, "③ AUTHENTICATED REQUEST  e.g. GET /api/v1/users/me")
    y += 24
    s += msg("Client", "JwtAuthFilter", "Authorization: Bearer <access>", y); y += 32
    s += msg("JwtAuthFilter", "Argon2", "verify HS256 sig (kid) + type=access", y); y += 30
    s += text(305, y, "AND tokenVersion == user.tokenVersion AND enabled AND non-locked",
              11, "#b91c1c"); y += 26
    s += msg("JwtAuthFilter", "Client", "401 if any check fails (fail-closed)", y,
             color="#0d9488", marker="arrowback", dashed=True); y += 40

    s += note(y, "④ REFRESH ROTATION  POST /api/v1/auth/refresh")
    y += 24
    s += msg("Client", "RefreshTokenSvc", "{ refreshToken }", y); y += 30
    s += text(770, y, "validate sig+type+tokenVersion; stored hash non-revoked & non-expired",
              11, "#b91c1c", anchor="middle"); y += 26
    s += msg("RefreshTokenSvc", "Client", "revoke old hash → issue NEW access+refresh", y,
             color="#0d9488", marker="arrowback", dashed=True); y += 40

    s += note(y, "⑤ LOGOUT  POST /api/v1/auth/logout  (revocation)")
    y += 24
    s += msg("Client", "Auth", "Bearer <access> (authenticated)", y); y += 30
    s += msg("Auth", "RefreshTokenSvc", "deleteAllForUser + user.tokenVersion++", y); y += 28
    s += text(540, y, "→ every previously-issued access token now fails the tokenVersion check",
              11, "#b91c1c", anchor="middle"); y += 20

    s += "</svg>\n"
    return s


# ---------------------------------------------------------------------------
# 2. AUTHORIZATION MODEL
# ---------------------------------------------------------------------------
def authz_model():
    W, H = 1180, 740
    s = svg_header(W, H, "Authorization model")
    s += text(24, 34, "Authorization Model (as implemented)", 20, "#0f172a", bold=True)
    s += text(24, 56, "SecurityConfig request matchers + @PreAuthorize(UserController) + "
                      "Role enum + tokenVersion", 12, "#475569")

    # Roles
    s += box(40, 90, 250, 70,
             "Roles (entity/enums/Role.java)\nROLE_USER  •  ROLE_ADMIN",
             fill="#fef9c3", stroke="#ca8a04", fs=13, bold=False)
    s += text(46, 182, "Granted authority = role.name(); role claim embedded in access JWT.",
              11, "#475569")
    s += text(46, 200, "Every request re-loads the user; @PreAuthorize is evaluated server-side.",
              11, "#475569")

    col1, col2, col3 = 40, 430, 810
    cw = 340
    top = 230

    s += box(col1, top, cw, 34, "PUBLIC  (permitAll)", fill="#dcfce7",
             stroke="#16a34a", fs=13, bold=True)
    pub = [
        "POST /auth/register",
        "POST /auth/login",
        "POST /auth/refresh",
        "GET  /actuator/health, /actuator/info",
        "GET  /swagger-ui/**, /v3/api-docs/** (dev/test)",
    ]
    yy = top + 50
    for p in pub:
        s += text(col1 + 12, yy, p, 12, "#166534", mono=True); yy += 24

    s += box(col2, top, cw, 34, "AUTHENTICATED  (any valid token)",
             fill="#e0f2fe", stroke="#0284c7", fs=13, bold=True)
    usr = [
        "POST /auth/logout",
        "GET  /users/me",
        "PUT  /users/me            (self; no role field)",
        "PUT  /users/me/password   (needs current pw)",
    ]
    yy = top + 50
    for p in usr:
        s += text(col2 + 12, yy, p, 12, "#075985", mono=True); yy += 24
    s += text(col2 + 12, yy + 6, "Principal from @AuthenticationPrincipal —", 11, "#475569")
    s += text(col2 + 12, yy + 22, "no user-supplied id, so no cross-user IDOR.", 11, "#475569")

    s += box(col3, top, cw, 34, "ADMIN  @PreAuthorize hasRole('ADMIN')",
             fill="#fee2e2", stroke="#dc2626", fs=13, bold=True)
    adm = [
        "GET    /users            (paginated)",
        "GET    /users/search",
        "GET    /users/{id}",
        "GET    /users/role/{role}",
        "POST   /users",
        "PUT    /users/{id}",
        "PATCH  /users/{id}/role",
        "PATCH  /users/{id}/status",
        "DELETE /users/{id}   (soft-delete)",
        "GET    /actuator/metrics/**, /prometheus",
    ]
    yy = top + 50
    for p in adm:
        s += text(col3 + 12, yy, p, 11.5, "#991b1b", mono=True); yy += 22

    # Revocation note
    s += box(40, 600, 1110, 96,
             "Token-version revocation (invalidates already-issued access tokens):\n"
             "password change • role change • account enable/disable • soft-delete • logout\n"
             "all call user.tokenVersion++ and revoke stored refresh tokens; JwtService rejects "
             "tokens whose tokenVersion != current.",
             fill="#f1f5f9", stroke="#64748b", fs=12.5)
    s += "</svg>\n"
    return s


# ---------------------------------------------------------------------------
# 3. ENTITY-RELATIONSHIP DIAGRAM
# ---------------------------------------------------------------------------
def erd():
    W, H = 1180, 760
    s = svg_header(W, H, "Entity relationship diagram")
    s += text(24, 34, "Entity-Relationship Diagram", 20, "#0f172a", bold=True)
    s += text(24, 56, "From db/migration V1..V9 and User / RefreshToken / AuditLog entities",
              12, "#475569")

    def table(x, y, title, rows, w=360, accent="#6366f1"):
        rh = 24
        h = 34 + rh * len(rows)
        out = (f"<rect x='{x}' y='{y}' width='{w}' height='{h}' rx='8' "
               f"fill='#ffffff' stroke='{accent}' stroke-width='2'/>\n")
        out += (f"<rect x='{x}' y='{y}' width='{w}' height='34' rx='8' fill='{accent}'/>\n")
        out += (f"<rect x='{x}' y='{y+18}' width='{w}' height='16' fill='{accent}'/>\n")
        out += text(x + w / 2, y + 22, title, 13.5, "#ffffff", anchor="middle", bold=True)
        yy = y + 34
        for i, (col, typ, tag) in enumerate(rows):
            if i % 2:
                out += (f"<rect x='{x+2}' y='{yy}' width='{w-4}' height='{rh}' "
                        f"fill='#f8fafc'/>\n")
            tcol = "#b45309" if tag == "PK" else ("#0d9488" if tag == "FK" else
                   ("#7c3aed" if tag == "UQ" else "#334155"))
            out += text(x + 12, yy + 16, col, 11.5, "#0f172a", mono=True, bold=(tag == "PK"))
            out += text(x + 172, yy + 16, typ, 11, "#64748b", mono=True)
            if tag:
                out += text(x + w - 12, yy + 16, tag, 10.5, tcol, anchor="end", bold=True)
            yy += rh
        return out, h

    users = [
        ("id", "BIGSERIAL", "PK"),
        ("tenant_id", "VARCHAR(50)", "UQ*"),
        ("first_name", "VARCHAR(50)", ""),
        ("last_name", "VARCHAR(50)", ""),
        ("email", "CITEXT", "UQ"),
        ("password", "VARCHAR(255)", ""),
        ("token_version", "INTEGER", ""),
        ("failed_login_attempts", "INTEGER", ""),
        ("locked_until", "TIMESTAMPTZ", ""),
        ("role", "VARCHAR(50)", ""),
        ("enabled", "BOOLEAN", ""),
        ("account_non_locked", "BOOLEAN", ""),
        ("row_version", "BIGINT", ""),
        ("created_at / updated_at", "TIMESTAMPTZ", ""),
    ]
    refresh = [
        ("id", "BIGSERIAL", "PK"),
        ("tenant_id", "VARCHAR(50)", ""),
        ("user_id", "BIGINT", "FK"),
        ("token_hash", "VARCHAR(64)", "UQ"),
        ("expires_at", "TIMESTAMPTZ", ""),
        ("revoked_at", "TIMESTAMPTZ", ""),
        ("created_at / updated_at", "TIMESTAMPTZ", ""),
    ]
    audit = [
        ("id", "BIGSERIAL", "PK"),
        ("tenant_id", "VARCHAR(50)", ""),
        ("user_id", "BIGINT", ""),
        ("action", "VARCHAR", ""),
        ("entity_type", "VARCHAR", ""),
        ("entity_id", "BIGINT", ""),
        ("ip_address", "VARCHAR", ""),
        ("user_agent", "VARCHAR", ""),
        ("created_at", "TIMESTAMPTZ", ""),
    ]

    ut, uh = table(60, 90, "users", users, accent="#6366f1")
    rt, rh_ = table(760, 90, "refresh_tokens", refresh, accent="#0d9488")
    at, ah = table(760, 430, "audit_log", audit, accent="#b45309")
    s += ut + rt + at

    # relationships
    s += line(420, 190, 760, 190, color="#0d9488", w=2)
    s += text(590, 182, "1 .. N  (FK user_id, ON DELETE CASCADE)", 11, "#0d9488",
              anchor="middle")
    s += line(420, 300, 760, 470, color="#b45309", w=2, dash="5 4")
    s += text(600, 372, "user_id (actor; no FK constraint)", 11, "#b45309",
              anchor="middle")

    s += text(60, 700, "PK primary key   FK foreign key   UQ unique   "
                       "UQ* email uniqueness: UNIQUE(lower(email)) [V3] and partial "
                       "UNIQUE(tenant_id,email) WHERE enabled [V8]", 11, "#475569")
    s += text(60, 720, "GIN pg_trgm indexes on lower(first_name|last_name|email) back "
                       "keyword search [V6].", 11, "#475569")
    s += "</svg>\n"
    return s


for name, gen in [("auth-flow", auth_flow), ("authz-model", authz_model), ("erd", erd)]:
    with open(f"docs/images/{name}.svg", "w") as f:
        f.write(gen())
    print(f"wrote docs/images/{name}.svg")
