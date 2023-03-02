package io.dataease.plugins.datasource.prometheus.engine.okhttp.base;


import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.Flushable;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Utils {
    private static final Logger LOGGER = LoggerFactory.getLogger(Utils.class);
    private static final String ALL_CHARS = "abcdefghijklmnopqrstuvwxyz0123456789";

    public static <T> T checkNotNull(T ref, String message) {
        if (ref == null) {
            throw new NullPointerException(message);
        }
        return ref;
    }

    public static boolean isNullOrEmpty(String str) {
        return str == null || str.isEmpty();
    }

    public static boolean isNotNullOrEmpty(String str) {
        return !isNullOrEmpty(str);
    }

    public static String getProperty(Map<String, Object> properties, String propertyName, String defaultValue) {
        String answer = (String) properties.get(propertyName);
        if (!Utils.isNullOrEmpty(answer)) {
            return answer;
        }

        return getSystemPropertyOrEnvVar(propertyName, defaultValue);
    }

    public static String getProperty(Map<String, Object> properties, String propertyName) {
        return getProperty(properties, propertyName, null);
    }

    public static String getSystemPropertyOrEnvVar(String systemPropertyName, String envVarName, String defaultValue) {
        String answer = System.getProperty(systemPropertyName);
        if (isNotNullOrEmpty(answer)) {
            return answer;
        }

        answer = System.getenv(envVarName);
        if (isNotNullOrEmpty(answer)) {
            return answer;
        }

        return defaultValue;
    }

    public static String convertSystemPropertyNameToEnvVar(String systemPropertyName) {
        return systemPropertyName.toUpperCase().replaceAll("[.-]", "_");
    }

    public static String getEnvVar(String envVarName, String defaultValue) {
        String answer = System.getenv(envVarName);
        return isNotNullOrEmpty(answer) ? answer : defaultValue;
    }

    public static String getSystemPropertyOrEnvVar(String systemPropertyName, String defaultValue) {
        return getSystemPropertyOrEnvVar(systemPropertyName, convertSystemPropertyNameToEnvVar(systemPropertyName), defaultValue);
    }

    public static String getSystemPropertyOrEnvVar(String systemPropertyName) {
        return getSystemPropertyOrEnvVar(systemPropertyName, (String) null);
    }

    public static Boolean getSystemPropertyOrEnvVar(String systemPropertyName, Boolean defaultValue) {
        String result = getSystemPropertyOrEnvVar(systemPropertyName, defaultValue.toString());
        return Boolean.parseBoolean(result);
    }

    public static int getSystemPropertyOrEnvVar(String systemPropertyName, int defaultValue) {
        String result = getSystemPropertyOrEnvVar(systemPropertyName, new Integer(defaultValue).toString());
        return Integer.parseInt(result);
    }

    public static String join(final Object[] array) {
        return join(array, ',');
    }

    public static String join(final Object[] array, final char separator) {
        if (array == null) {
            return null;
        }
        if (array.length == 0) {
            return "";
        }
        final StringBuilder buf = new StringBuilder();
        for (int i = 0; i < array.length; i++) {
            if (i > 0) {
                buf.append(separator);
            }
            if (array[i] != null) {
                buf.append(array[i]);
            }
        }
        return buf.toString();
    }


    /**
     * Wait until an other thread signals the completion of a task.
     * If an exception is passed, it will be propagated to the caller.
     *
     * @param queue    The communication channel.
     * @param amount   The amount of time to wait.
     * @param timeUnit The time unit.
     */
    public static boolean waitUntilReady(BlockingQueue<Object> queue, long amount, TimeUnit timeUnit) {
        try {
            Object obj = queue.poll(amount, timeUnit);
            if (obj instanceof Boolean) {
                return (Boolean) obj;
            } else if (obj instanceof Throwable) {
                throw (Throwable) obj;
            }
            return false;
        } catch (Throwable t) {
            throw new RuntimeException("", t);
        }
    }

    /**
     * Closes the specified {@link ExecutorService}.
     *
     * @param executorService The executorService.
     * @return True if shutdown is complete.
     */
    public static boolean shutdownExecutorService(ExecutorService executorService) {
        if (executorService == null) {
            return false;
        }
        //If it hasn't already shutdown, do shutdown.
        if (!executorService.isShutdown()) {
            executorService.shutdown();
        }

        try {
            //Wait for clean termination
            if (executorService.awaitTermination(5, TimeUnit.SECONDS)) {
                return true;
            }

            //If not already terminated (via shutdownNow) do shutdownNow.
            if (!executorService.isTerminated()) {
                executorService.shutdownNow();
            }

            if (executorService.awaitTermination(5, TimeUnit.SECONDS)) {
                return true;
            }

            if (LOGGER.isDebugEnabled()) {
                List<Runnable> tasks = executorService.shutdownNow();
                if (!tasks.isEmpty()) {
                    LOGGER.debug("ExecutorService was not cleanly shutdown, after waiting for 10 seconds. Number of remaining tasks:" + tasks.size());
                }
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
            //Preserve interrupted status
            Thread.currentThread().interrupt();
        }
        return false;
    }

    /**
     * Closes and flushes the specified {@link Closeable} items.
     *
     * @param closeables An {@link Iterable} of {@link Closeable} items.
     */
    public static void closeQuietly(Iterable<Closeable> closeables) {
        for (Closeable c : closeables) {
            try {
                //Check if we also need to flush
                if (c instanceof Flushable) {
                    ((Flushable) c).flush();
                }

                if (c != null) {
                    c.close();
                }
            } catch (IOException e) {
                LOGGER.debug("Error closing:" + c);
            }
        }
    }

    /**
     * Closes and flushes the specified {@link Closeable} items.
     *
     * @param closeables An array of {@link Closeable} items.
     */
    public static void closeQuietly(Closeable... closeables) {
        closeQuietly(Arrays.asList(closeables));
    }


    /**
     * Replaces all occurrencies of the from text with to text without any regular expressions
     */
    public static String replaceAllWithoutRegex(String text, String from, String to) {
        if (text == null) {
            return null;
        }
        int idx = 0;
        while (true) {
            idx = text.indexOf(from, idx);
            if (idx >= 0) {
                text = text.substring(0, idx) + to + text.substring(idx + from.length());

                // lets start searching after the end of the `to` to avoid possible infinite recursion
                idx += to.length();
            } else {
                break;
            }
        }
        return text;
    }

    public static String randomString(int length) {
        Random random = new Random();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < length; i++) {
            int index = random.nextInt(ALL_CHARS.length());
            sb.append(ALL_CHARS.charAt(index));
        }
        return sb.toString();
    }

    public static String randomString(String prefix, int length) {
        Random random = new Random();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < length - prefix.length(); i++) {
            int index = random.nextInt(ALL_CHARS.length());
            sb.append(ALL_CHARS.charAt(index));
        }
        return sb.toString();
    }

    /**
     * 排序
     *
     * @param list
     * @param entityAttrName 需要排序的字段名称
     * @param sortType       升/降序排序
     * @param <T>
     * @return
     */
    public static <T> List<T> sort(List<T> list, final String entityAttrName, final SortType sortType) {
        if (list == null || list.isEmpty() || StringUtils.isBlank(entityAttrName) || sortType == null) {
            return list;
        }

        Collections.sort(list, new Comparator<T>() {
            @Override
            public int compare(T o1, T o2) {
                try {
                    Field field = o1.getClass().getDeclaredField(entityAttrName);
                    field.setAccessible(true);

                    switch (sortType) {
                        case ASC:
                            return (int) ((long) field.get(o1) - (long) field.get(o2));
                        case DESC:
                            return (int) ((long) field.get(o2) - (long) field.get(o1));
                    }
                } catch (NoSuchFieldException e) {
                    e.printStackTrace();
                } catch (IllegalAccessException e) {
                    e.printStackTrace();
                }
                return 0;
            }
        });

        return list;
    }


    /**
     * 判断一个字符串是否是数字(包含整数,小数)
     *
     * @param str 带判断的字符串
     * @return 判断结果
     */
    public static boolean allIsNumber(String str) {
        // 01 去掉字符串两边的空格
        str = StringUtils.trim(str);

        // 02 判断字符串是否为空（null 或 ”“ 就被认定为空）
        if (StringUtils.isNotBlank(str)) {
            // 03 判断字符串是否全部是数字
            Pattern pattern = Pattern.compile("[0-9]*\\.?[0-9]+");
            Matcher isNum = pattern.matcher(str);
            if (isNum.matches()) {
                return true;
            } else {
                return false;
            }
        } else {
            return false;
        }
    }

    /**
     * @param s: 传递的字符串
     * @Description:base64编码
     * @Author: hch
     * @Date: 2020/6/28 16:18
     * @return: java.lang.String :编码后的值
     **/

    public static String Base64Encode(String s) {
        if (StringUtils.isBlank(s)) {
            return null;
        }
        Base64.Encoder encoder = Base64.getEncoder();
        try {
            byte[] textByte = s.getBytes("UTF-8");
            return encoder.encodeToString(textByte);
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
            return null;
        }

    }

    /**
     * @param s: 传递来的字符串
     * @Description: base64 解码
     * @Author: hch
     * @Date: 2020/6/28 16:21
     * @return: java.lang.String  解码之后的值
     **/

    public static String Base64Decode(String s) {
        if (StringUtils.isBlank(s)) {
            return null;
        }
        Base64.Decoder decoder = Base64.getDecoder();
        try {
            return new String(decoder.decode(s), "UTF-8");
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
            return null;
        }

    }

}
