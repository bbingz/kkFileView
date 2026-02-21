package cn.keking.utils;

import cn.keking.config.ConfigConstants;
import org.apache.hc.client5.http.impl.DefaultRedirectStrategy;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.http.HttpException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.Set;

/**
 * 安全的HTTP重定向策略，在跟随重定向前校验目标host是否在黑名单中，
 * 防止通过可信域名302重定向到内网地址的SSRF攻击。
 */
public class SafeRedirectStrategy extends DefaultRedirectStrategy {

    private static final Logger LOGGER = LoggerFactory.getLogger(SafeRedirectStrategy.class);

    @Override
    public URI getLocationURI(HttpRequest request, HttpResponse response, HttpContext context)
            throws HttpException {
        URI redirectUri = super.getLocationURI(request, response, context);
        String host = redirectUri.getHost();
        if (host != null) {
            host = host.toLowerCase();
            Set<String> notTrustHosts = ConfigConstants.getNotTrustHostSet();
            if (notTrustHosts != null && isBlockedHost(notTrustHosts, host)) {
                LOGGER.warn("SSRF防护：阻止重定向到不信任主机: {}", host);
                throw new HttpException("Redirect to untrusted host blocked: " + host);
            }
        }
        return redirectUri;
    }

    private boolean isBlockedHost(Set<String> patterns, String host) {
        for (String pattern : patterns) {
            if (pattern.endsWith("*")) {
                String prefix = pattern.substring(0, pattern.length() - 1);
                if (host.startsWith(prefix)) {
                    return true;
                }
            } else if (pattern.equals(host)) {
                return true;
            }
        }
        return false;
    }
}
