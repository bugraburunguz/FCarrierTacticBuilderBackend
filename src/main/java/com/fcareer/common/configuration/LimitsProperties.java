package com.fcareer.common.configuration;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** İstek gövdesi bütçeleri (BE-SEC-02): bayt cinsinden, yol sınıfı başına. */
@Getter
@Setter
@ConfigurationProperties(prefix = "fcareer.limits")
public class LimitsProperties {
    private long jsonBytes = 1024L * 1024;
    private long captureBytes = 60L * 1024 * 1024;
    /** LE log'u birikir (dışa aktarım başına ~11 MB): birkaç dışa aktarımlı log da yüklenebilsin. spring.servlet.multipart ile uyumlu. */
    private long multipartBytes = 64L * 1024 * 1024;
}
