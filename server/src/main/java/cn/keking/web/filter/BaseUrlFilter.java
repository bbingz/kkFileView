package cn.keking.web.filter;

import cn.keking.config.ConfigConstants;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.context.request.RequestContextHolder;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;

/**
 * @author chenjh
 * @since 2020/5/13 18:27
 */
public class BaseUrlFilter implements Filter {

    private static final Logger logger = LoggerFactory.getLogger(BaseUrlFilter.class);
    private static String BASE_URL;

    public static String getBaseUrl() {
        String baseUrl;
        try {
            baseUrl = (String) RequestContextHolder.currentRequestAttributes().getAttribute("baseUrl", 0);
        } catch (Exception e) {
            baseUrl = BASE_URL;
        }
        return baseUrl;
    }


    @Override
    public void init(FilterConfig filterConfig) {

    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain filterChain) throws IOException, ServletException {

        String baseUrl;
        String configBaseUrl = ConfigConstants.getBaseUrl();

        final HttpServletRequest servletRequest = (HttpServletRequest) request;
        if (configBaseUrl != null && !ConfigConstants.DEFAULT_VALUE.equalsIgnoreCase(configBaseUrl)) {
            //1、如果配置文件中配置了 baseUrl 且不为 default 则以配置文件为准（安全：忽略X-Base-Url header防止注入）
            baseUrl = configBaseUrl;
        } else {
            //2、未配置base.url时，支持通过 http header 中 X-Base-Url 来动态设置（仅允许http/https协议）
            final String urlInHeader = servletRequest.getHeader("X-Base-Url");
            if (StringUtils.isNotEmpty(urlInHeader) && isValidBaseUrl(urlInHeader)) {
                baseUrl = urlInHeader;
            } else {
                if (StringUtils.isNotEmpty(urlInHeader)) {
                    logger.warn("拒绝不合法的X-Base-Url header: {}", urlInHeader);
                }
                //3、默认动态拼接 baseUrl
                baseUrl = request.getScheme() + "://" + request.getServerName() + ":" + request.getServerPort()
                        + servletRequest.getContextPath() + "/";
            }
        }

        if (!baseUrl.endsWith("/")) {
            baseUrl = baseUrl.concat("/");
        }

        BASE_URL = baseUrl;
        request.setAttribute("baseUrl", baseUrl);
        filterChain.doFilter(request, response);
    }

    /**
     * 验证 base URL 是否合法（仅允许 http/https 协议，防止注入攻击）
     */
    private boolean isValidBaseUrl(String url) {
        if (url == null) {
            return false;
        }
        String lower = url.toLowerCase().trim();
        // 仅允许 http/https 协议
        if (!lower.startsWith("http://") && !lower.startsWith("https://")) {
            return false;
        }
        // 不允许包含换行符等控制字符（防止 header injection）
        for (int i = 0; i < url.length(); i++) {
            char c = url.charAt(i);
            if (c == '\n' || c == '\r' || c == '\0') {
                return false;
            }
        }
        return true;
    }

    @Override
    public void destroy() {

    }
}
