package cn.keking.utils;

import cn.keking.config.ConfigConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.util.HtmlUtils;

import java.io.File;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class KkFileUtils {

    private static final Logger LOGGER = LoggerFactory.getLogger(KkFileUtils.class);

    public static final String DEFAULT_FILE_ENCODING = "UTF-8";

    private static final List<String> illegalFileStrList = new ArrayList<>();

    static {
        illegalFileStrList.add("../");
        illegalFileStrList.add("./");
        illegalFileStrList.add("..\\");
        illegalFileStrList.add(".\\");
        illegalFileStrList.add("\\..");
        illegalFileStrList.add("\\.");
        illegalFileStrList.add("..");
        illegalFileStrList.add("...");
    }

    /**
     * 检查文件名是否合规
     *
     * @param fileName 文件名
     * @return 合规结果, true:不合规，false:合规
     */
    public static boolean isIllegalFileName(String fileName) {
        for (String str : illegalFileStrList) {
            if (fileName.contains(str)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 检查是否是数字
     *
     * @param str 文件名
     * @return 合规结果, true:不合规，false:合规
     */
    public static boolean isInteger(String str) {
        if (StringUtils.hasText(str)) {
            boolean strResult = str.matches("-?[0-9]+.?[0-9]*");
            return strResult;
        }
        return false;
    }

    /**
     * 判断url是否是http资源
     *
     * @param url url
     * @return 是否http
     */
    public static boolean isHttpUrl(URL url) {
        return url.getProtocol().toLowerCase().startsWith("file") || url.getProtocol().toLowerCase().startsWith("http");
    }

    /**
     * 判断url是否是ftp资源
     *
     * @param url url
     * @return 是否ftp
     */
    public static boolean isFtpUrl(URL url) {
        return "ftp".equalsIgnoreCase(url.getProtocol());
    }

    /**
     * 删除单个文件
     *
     * @param fileName 要删除的文件的文件名
     * @return 单个文件删除成功返回true，否则返回false
     */
    public static boolean deleteFileByName(String fileName) {
        File file = new File(fileName);
        // 如果文件路径所对应的文件存在，并且是一个文件，则直接删除
        if (file.exists() && file.isFile()) {
            if (file.delete()) {
                LOGGER.info("删除单个文件" + fileName + "成功！");
                return true;
            } else {
                LOGGER.info("删除单个文件" + fileName + "失败！");
                return false;
            }
        } else {
            LOGGER.info("删除单个文件失败：" + fileName + "不存在！");
            return false;
        }
    }


    public static String htmlEscape(String input) {
        if (StringUtils.hasText(input)) {
            return HtmlUtils.htmlEscape(input, "UTF-8");
        }
        return input;
    }


    /**
     * 通过文件名获取文件后缀
     *
     * @param fileName 文件名称
     * @return 文件后缀
     */
    public static String suffixFromFileName(String fileName) {
        return fileName.substring(fileName.lastIndexOf(".") + 1).toLowerCase();
    }


    /**
     * 根据文件路径删除文件
     *
     * @param filePath 绝对路径
     */
    public static void deleteFileByPath(String filePath) {
        File file = new File(filePath);
        if (file.exists() && !file.delete()) {
            LOGGER.warn("压缩包源文件删除失败:{}！", filePath);
        }
    }

    /**
     * 删除目录及目录下的文件
     *
     * @param dir 要删除的目录的文件路径
     * @return 目录删除成功返回true，否则返回false
     */
    public static boolean deleteDirectory(String dir) {
        // 如果dir不以文件分隔符结尾，自动添加文件分隔符
        if (!dir.endsWith(File.separator)) {
            dir = dir + File.separator;
        }
        File dirFile = new File(dir);
        // 如果dir对应的文件不存在，或者不是一个目录，则退出
        if ((!dirFile.exists()) || (!dirFile.isDirectory())) {
            LOGGER.info("删除目录失败：" + dir + "不存在！");
            return false;
        }
        boolean flag = true;
        // 删除文件夹中的所有文件包括子目录
        File[] files = dirFile.listFiles();
        for (int i = 0; i < Objects.requireNonNull(files).length; i++) {
            // 删除子文件
            if (files[i].isFile()) {
                flag = KkFileUtils.deleteFileByName(files[i].getAbsolutePath());
                if (!flag) {
                    break;
                }
            } else if (files[i].isDirectory()) {
                // 删除子目录
                flag = KkFileUtils.deleteDirectory(files[i].getAbsolutePath());
                if (!flag) {
                    break;
                }
            }
        }

        if (!dirFile.delete() || !flag) {
            LOGGER.info("删除目录失败！");
            return false;
        }
        return true;
    }

    /**
     * 判断文件是否允许（上传/下载均调用此方法）
     * 采用白名单机制，覆盖 FileType.java 中定义的所有可预览文件类型
     * 仅排除操作系统可执行文件（exe, dll, dat 等）
     *
     * @param file 文件名
     * @return 是否允许
     */
    private static final java.util.Set<String> ALLOWED_TYPES = new java.util.HashSet<>(java.util.Arrays.asList(
            // Office 文档（41种）
            "doc", "docx", "docm", "dot", "dotx", "dotm", "wps", "wpt",
            "xls", "xlsx", "xlsm", "xlt", "xltx", "xltm", "xlam", "xla", "csv", "tsv", "et", "ett",
            "ppt", "pptx", "dps",
            "vsd", "vsdx", "rtf", "odt", "ods", "odp", "ott", "ots", "otp", "sxi", "fodt", "fods",
            "wmf", "emf", "tga", "psd", "pages", "eps",
            // PDF
            "pdf",
            // 图片（8种）
            "jpg", "jpeg", "png", "gif", "bmp", "ico", "jfif", "webp",
            // 压缩包（7种）
            "rar", "zip", "jar", "7z", "7-zip", "tar", "gzip", "gz", "bz2",
            // 文本/代码（含 simText + CODES，排除 ftl 模板）
            "txt", "md", "xml", "xbrl", "json", "properties", "yml", "yaml", "log", "gitignore",
            "java", "py", "python", "c", "cpp", "h", "cs", "go", "rb", "lua", "php",
            "js", "css", "html", "htm", "asp", "aspx", "jsp", "sql",
            "sh", "bat", "m", "bas", "prg", "cmd",
            // CAD（12种）
            "dwg", "dxf", "dwf", "dwt", "dwfx", "iges", "igs", "dng", "ifc", "stl", "cf2", "plt",
            // 3D 模型（20种）
            "obj", "3ds", "ply", "off", "3dm", "fbx", "dae", "wrl", "3mf",
            "glb", "o3dv", "gltf", "stp", "step", "bim", "fcstd", "brep",
            // TIFF
            "tif", "tiff",
            // 媒体（含 media + convertMedias）
            "mp3", "wav", "wma", "amr", "ogg", "flac", "aac", "m4a",
            "mp4", "avi", "mov", "wmv", "mkv", "3gp", "rm", "rmvb", "flv", "mpd", "m3u8", "ts", "mpeg",
            // 特殊文档格式
            "ofd", "eml", "xmind", "svg", "epub", "bpmn", "dcm", "drawio"
    ));

    public static boolean isAllowedUpload(String file) {
        String fileType = suffixFromFileName(file);
        if (ObjectUtils.isEmpty(fileType)) {
            return false;
        }
        if (!ALLOWED_TYPES.contains(fileType.toLowerCase())) {
            LOGGER.warn("拒绝不支持的文件类型: {}", fileType);
            return false;
        }
        return true;
    }

    /**
     * 判断文件是否存在
     *
     * @param filePath 文件路径
     * @return 是否存在 true:存在，false:不存在
     */
    public static boolean isExist(String filePath) {
        File file = new File(filePath);
        return file.exists();
    }

}
