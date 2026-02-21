package cn.keking.config;

import cn.keking.web.filter.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.HashSet;
import java.util.Set;

/**
 * @author: chenjh
 * @since: 2019/4/16 20:04
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final static Logger LOGGER = LoggerFactory.getLogger(WebConfig.class);

    /**
     * 访问外部文件配置
     */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // fileDir 必须映射为静态资源：Office/CAD/TIFF 转换后的 PDF/HTML/SVG/JPG 均存储在此目录，
        // 前端通过 baseUrl + relativePath 加载。移除此映射会导致所有转换预览 404。
        // 上传安全由 isAllowedUpload() 白名单 + file.upload.disable 控制，不依赖此处拦截。
        String filePath = ConfigConstants.getFileDir();
        LOGGER.info("Add resource locations: {}", filePath);
        registry.addResourceHandler("/**").addResourceLocations("classpath:/META-INF/resources/",
                "classpath:/resources/", "classpath:/static/", "classpath:/public/", "file:" + filePath);
    }

    @Bean
    public FilterRegistrationBean<ChinesePathFilter> getChinesePathFilter() {
        ChinesePathFilter filter = new ChinesePathFilter();
        FilterRegistrationBean<ChinesePathFilter> registrationBean = new FilterRegistrationBean<>();
        registrationBean.setFilter(filter);
        registrationBean.setOrder(10);
        return registrationBean;
    }

    @Bean
    public FilterRegistrationBean<TrustHostFilter> getTrustHostFilter() {
        Set<String> filterUri = new HashSet<>();
        filterUri.add("/onlinePreview");
        filterUri.add("/picturesPreview");
        filterUri.add("/getCorsFile");
        TrustHostFilter filter = new TrustHostFilter();
        FilterRegistrationBean<TrustHostFilter> registrationBean = new FilterRegistrationBean<>();
        registrationBean.setFilter(filter);
        registrationBean.setUrlPatterns(filterUri);
        return registrationBean;
    }

    @Bean
    public FilterRegistrationBean<TrustDirFilter> getTrustDirFilter() {
        Set<String> filterUri = new HashSet<>();
        filterUri.add("/onlinePreview");
        filterUri.add("/picturesPreview");
        filterUri.add("/getCorsFile");
        TrustDirFilter filter = new TrustDirFilter();
        FilterRegistrationBean<TrustDirFilter> registrationBean = new FilterRegistrationBean<>();
        registrationBean.setFilter(filter);
        registrationBean.setUrlPatterns(filterUri);
        return registrationBean;
    }

    @Bean
    public FilterRegistrationBean<BaseUrlFilter> getBaseUrlFilter() {
        Set<String> filterUri = new HashSet<>();
        BaseUrlFilter filter = new BaseUrlFilter();
        FilterRegistrationBean<BaseUrlFilter> registrationBean = new FilterRegistrationBean<>();
        registrationBean.setFilter(filter);
        registrationBean.setUrlPatterns(filterUri);
        registrationBean.setOrder(20);
        return registrationBean;
    }

    @Bean
    public FilterRegistrationBean<UrlCheckFilter> getUrlCheckFilter() {
        UrlCheckFilter filter = new UrlCheckFilter();
        FilterRegistrationBean<UrlCheckFilter> registrationBean = new FilterRegistrationBean<>();
        registrationBean.setFilter(filter);
        registrationBean.setOrder(30);
        return registrationBean;
    }

    @Bean
    public FilterRegistrationBean<SecurityHeadersFilter> getSecurityHeadersFilter() {
        SecurityHeadersFilter filter = new SecurityHeadersFilter();
        FilterRegistrationBean<SecurityHeadersFilter> registrationBean = new FilterRegistrationBean<>();
        registrationBean.setFilter(filter);
        registrationBean.setOrder(1);
        return registrationBean;
    }

    @Bean
    public FilterRegistrationBean<AttributeSetFilter> getWatermarkConfigFilter() {
        Set<String> filterUri = new HashSet<>();
        filterUri.add("/index");
        filterUri.add("/");
        filterUri.add("/onlinePreview");
        filterUri.add("/picturesPreview");
        AttributeSetFilter filter = new AttributeSetFilter();
        FilterRegistrationBean<AttributeSetFilter> registrationBean = new FilterRegistrationBean<>();
        registrationBean.setFilter(filter);
        registrationBean.setUrlPatterns(filterUri);
        return registrationBean;
    }
}
