package example.sas;

import java.io.IOException;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.web.filter.OncePerRequestFilter;

/** Session-local login handoffs: two tabs do not share one mutable SavedRequest. */
@Controller
public class LoginExperience {
    record Flow(String client, String resume, Instant expires) implements java.io.Serializable {}
    final Policies policies;
    public LoginExperience(Policies policies) { this.policies = policies; }
    @SuppressWarnings("unchecked") Map<String,Flow> flows(HttpSession session) {
        synchronized(session) {
            var flows = (Map<String,Flow>) session.getAttribute("demo.flows");
            if (flows == null) { flows = new ConcurrentHashMap<>(); session.setAttribute("demo.flows", flows); }
            return flows;
        }
    }
    String start(HttpServletRequest request, String client, String resume) {
        policies.get(client);
        var flows = flows(request.getSession());
        flows.entrySet().removeIf(e -> e.getValue().expires.isBefore(Instant.now()));
        if (flows.size() >= 20) throw new IllegalStateException("too many login tabs");
        String id = UUID.randomUUID().toString();
        flows.put(id, new Flow(client, resume, Instant.now().plusSeconds(300)));
        return id;
    }
    public AuthenticationEntryPoint entryPoint() {
        return (request,response,error) -> {
            if (!request.getRequestURI().equals("/oauth2/authorize")) { response.sendError(401); return; }
            String flow = start(request, request.getParameter("client_id"), request.getRequestURI() + "?" + request.getQueryString());
            response.sendRedirect("/login?flow=" + flow);
        };
    }
    Flow require(HttpServletRequest request, String id) {
        if (id == null || request.getSession(false) == null) throw new IllegalArgumentException("unknown login flow");
        Flow flow = flows(request.getSession()).get(id);
        if (flow == null || flow.expires.isBefore(Instant.now())) throw new IllegalArgumentException("expired login flow");
        return flow;
    }
    @GetMapping("/login") String login(@RequestParam(required=false) String flow, HttpServletRequest request, Model model) {
        if (flow == null) return "redirect:/login?flow=" + start(request,"alpha-web","/demo/signed-in");
        Flow context = require(request,flow);
        var policy = policies.get(context.client);
        if (!policy.enabled() || !Set.of("standard","compact").contains(policy.theme())
            || !Set.of("/assets/alpha.svg","/assets/beta.svg").contains(policy.icon())) throw new IllegalArgumentException("invalid presentation");
        model.addAttribute("policy",policy); model.addAttribute("flow",flow);
        return policy.theme();
    }
    @GetMapping("/demo/signed-in") @ResponseBody String signedIn() { return "Signed in to SAS demo"; }
    @ExceptionHandler(IllegalArgumentException.class) @ResponseStatus(org.springframework.http.HttpStatus.BAD_REQUEST)
    @ResponseBody String invalid() { return "Invalid login context"; }
    public OncePerRequestFilter verificationFilter() {
        return new OncePerRequestFilter() {
            protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain) throws ServletException, IOException {
                if (req.getMethod().equals("POST") && req.getRequestURI().equals("/login")) {
                    try {
                        Flow flow = require(req,req.getParameter("flow"));
                        var p = policies.get(flow.client);
                        if (!p.enabled() || req.getParameter("username") == null || !req.getParameter("username").startsWith(p.tenant()+":")) throw new IllegalArgumentException();
                        // Deliberately local test doubles, not SMS or external CAPTCHA verification.
                        if (p.otp() && !"123456".equals(req.getParameter("otp"))) throw new IllegalArgumentException();
                        if (p.captcha() && !"demo-pass".equals(req.getParameter("captcha"))) throw new IllegalArgumentException();
                    } catch (IllegalArgumentException e) { res.sendError(401); return; }
                }
                chain.doFilter(req,res);
            }
        };
    }
    public void success(HttpServletRequest req, HttpServletResponse res, org.springframework.security.core.Authentication auth) throws IOException {
        String id = req.getParameter("flow");
        Flow flow = flows(req.getSession()).remove(id);
        if (flow == null) { res.sendError(400); return; }
        res.sendRedirect(flow.resume);
    }
}
