package cn.keking.web.filter;

import org.apache.commons.lang3.StringUtils;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * @date 2023/11/30
 */
public class UrlCheckFilter implements Filter {

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {

        final HttpServletRequest httpServletRequest = (HttpServletRequest) request;
        String servletPath = httpServletRequest.getServletPath();

        boolean redirect = false;

        // servletPath 中不能包含 //
        if (servletPath.contains("//")) {
            servletPath = servletPath.replaceAll("//+", "/");
            redirect = true;
        }

        // 不能以 / 结尾，同时考虑 **首页** 的特殊性
        if (servletPath.endsWith("/") && servletPath.length() > 1) {
            servletPath = servletPath.substring(0, servletPath.length() - 1);
            redirect = true;
        }
        if (redirect) {
            // 安全：始终使用相对路径重定向，防止通过 base URL 注入造成开放重定向
            String redirectUrl = httpServletRequest.getContextPath() + servletPath;
            String queryString = httpServletRequest.getQueryString();
            if (StringUtils.isNotBlank(queryString)) {
                redirectUrl = redirectUrl + "?" + queryString;
            }
            ((HttpServletResponse) response).sendRedirect(redirectUrl);
        } else {
            chain.doFilter(request, response);
        }
    }
}
