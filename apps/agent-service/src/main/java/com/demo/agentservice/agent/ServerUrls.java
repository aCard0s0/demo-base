package com.demo.agentservice.agent;

import com.demo.outbound.UrlPolicy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Where an owner's MCP server may point: the {@link UrlPolicy} of {@code libs/outbound-urls}, fed the deployment's
 * own servers from {@code agents.trusted-server-urls}. It is read per check rather than once: a short property,
 * and a test can then trust a port chosen after startup.
 */
@Component
public class ServerUrls extends UrlPolicy {

    @Autowired
    public ServerUrls(Environment env) {
        this(env, InetAddress::getAllByName);
    }

    public ServerUrls(Environment env, Resolver resolver) {
        super(() -> configured(env), resolver);
    }

    /** The property, comma separated, stripped, blanks dropped. */
    static Set<String> configured(Environment env) {
        return Arrays.stream(env.getProperty("agents.trusted-server-urls", "").split(","))
                .map(String::strip).filter(s -> !s.isEmpty())
                .collect(Collectors.toSet());
    }
}
