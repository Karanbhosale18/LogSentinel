package com.digiplus.loganalyzer.config;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Forwards client-side routes to {@code index.html} so that React Router handles
 * them. Without this, refreshing or navigating directly to {@code /logs/42} would
 * produce a 404 because there is no server-side route for it.
 *
 * <p>The negative lookahead excludes paths that should be handled by Spring:
 * {@code /api}, {@code /hello}, and static asset extensions.
 */
@Controller
public class SpaForwardingController {

    /**
     * Match any path that is NOT an API call, a known backend endpoint, or a
     * static file (by extension), and forward it to the SPA entry point.
     */
    @GetMapping(value = { "/", "/{path:^(?!api|assets|index\\.html|favicon\\.ico).*$}/**" })
    public String forward() {
        return "forward:/index.html";
    }
}
