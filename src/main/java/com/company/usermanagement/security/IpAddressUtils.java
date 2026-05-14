package com.company.usermanagement.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.web.util.matcher.IpAddressMatcher;
import org.springframework.util.StringUtils;

import java.util.List;

public final class IpAddressUtils {

    private static final String X_FORWARDED_FOR = "X-Forwarded-For";

    private IpAddressUtils() {
    }

    public static String extractClientIp(
            HttpServletRequest request,
            List<String> trustedProxyCidrs) {

        String remoteAddr = request.getRemoteAddr();
        String forwardedFor = request.getHeader(X_FORWARDED_FOR);

        if (!isTrustedProxy(remoteAddr, trustedProxyCidrs) || !StringUtils.hasText(forwardedFor)) {
            return remoteAddr;
        }

        String candidate = forwardedFor.split(",")[0].trim();
        if (StringUtils.hasText(candidate)) {
            return candidate;
        }
        return remoteAddr;
    }

    private static boolean isTrustedProxy(String remoteAddr, List<String> trustedProxyCidrs) {
        if (!StringUtils.hasText(remoteAddr) || trustedProxyCidrs == null || trustedProxyCidrs.isEmpty()) {
            return false;
        }
        for (String cidr : trustedProxyCidrs) {
            if (new IpAddressMatcher(cidr).matches(remoteAddr)) {
                return true;
            }
        }
        return false;
    }
}
