package cn.keking.service;

import cn.keking.utils.LocalOfficeUtils;
import org.apache.commons.lang3.StringUtils;
import org.jodconverter.core.office.InstalledOfficeManagerHolder;
import org.jodconverter.core.office.OfficeException;
import org.jodconverter.core.office.OfficeUtils;
import org.jodconverter.core.util.OSUtils;
import org.jodconverter.local.office.LocalOfficeManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.convert.DurationStyle;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;

/**
 * 创建文件转换器
 *
 * @author chenjh
 * @since 2022-12-15
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class OfficePluginManager {

    private final Logger logger = LoggerFactory.getLogger(OfficePluginManager.class);

    private LocalOfficeManager officeManager;

    @Value("${office.plugin.server.ports:2001,2002}")
    private String serverPorts;

    @Value("${office.plugin.task.timeout:5m}")
    private String timeOut;

    @Value("${office.plugin.task.taskexecutiontimeout:5m}")
    private String taskExecutionTimeout;

    @Value("${office.plugin.task.maxtasksperprocess:5}")
    private int maxTasksPerProcess;

    /**
     * 启动Office组件进程
     */
    @PostConstruct
    public void startOfficeManager() throws OfficeException {
        File officeHome = LocalOfficeUtils.getDefaultOfficeHome();
        if (officeHome == null) {
            throw new RuntimeException("找不到office组件，请确认'office.home'配置是否有误");
        }
        boolean killOffice = killProcess();
        if (killOffice) {
            logger.warn("检测到有正在运行的office进程，已自动结束该进程");
        }
        try {
            String[] portsString = serverPorts.split(",");
            int[] ports = Arrays.stream(portsString).mapToInt(Integer::parseInt).toArray();
            long timeout = DurationStyle.detectAndParse(timeOut).toMillis();
            long taskexecutiontimeout = DurationStyle.detectAndParse(taskExecutionTimeout).toMillis();
            officeManager = LocalOfficeManager.builder()
                    .officeHome(officeHome)
                    .portNumbers(ports)
                    .processTimeout(timeout)
                    .maxTasksPerProcess(maxTasksPerProcess)
                    .taskExecutionTimeout(taskexecutiontimeout)
                    .build();
            officeManager.start();
            InstalledOfficeManagerHolder.setInstance(officeManager);
        } catch (Exception e) {
            logger.error("启动office组件失败，请检查office组件是否可用");
            throw e;
        }
    }

    private boolean killProcess() {
        boolean flag = false;
        try {
            if (OSUtils.IS_OS_WINDOWS) {
                ProcessBuilder pb = new ProcessBuilder("tasklist");
                pb.redirectErrorStream(true);
                Process p = pb.start();
                String s = readProcessOutput(p);
                if (s.contains("soffice.bin")) {
                    new ProcessBuilder("taskkill", "/im", "soffice.bin", "/f").start();
                    flag = true;
                }
            } else {
                // macOS 和 Linux 统一使用 pgrep/pkill，避免 shell 管道注入风险
                ProcessBuilder pb = new ProcessBuilder("pgrep", "-f", "soffice.bin");
                pb.redirectErrorStream(true);
                Process p = pb.start();
                String s = readProcessOutput(p);
                int exitCode = p.waitFor();
                if (exitCode == 0 && !s.trim().isEmpty()) {
                    // pgrep 找到了进程，使用 pkill 终止
                    new ProcessBuilder("pkill", "-f", "soffice.bin").start();
                    flag = true;
                }
            }
        } catch (IOException | InterruptedException e) {
            logger.error("检测office进程异常", e);
        }
        return flag;
    }

    private String readProcessOutput(Process process) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        InputStream is = process.getInputStream();
        byte[] b = new byte[256];
        int len;
        while ((len = is.read(b)) > 0) {
            baos.write(b, 0, len);
        }
        return baos.toString();
    }

    @PreDestroy
    public void destroyOfficeManager() {
        if (null != officeManager && officeManager.isRunning()) {
            logger.info("Shutting down office process");
            OfficeUtils.stopQuietly(officeManager);
        }
    }

}
