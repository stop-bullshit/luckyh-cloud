package com.luckyh.cloud.common.util;

import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Date;

/**
 * 日期时间工具类
 */
public class DateUtils {
    
    public static final String DEFAULT_DATE_FORMAT = "yyyy-MM-dd";
    public static final String DEFAULT_DATETIME_FORMAT = "yyyy-MM-dd HH:mm:ss";
    public static final String DEFAULT_TIME_FORMAT = "HH:mm:ss";
    
    /**
     * 格式化日期
     */
    public static String formatDate(Date date) {
        return formatDate(date, DEFAULT_DATE_FORMAT);
    }
    
    /**
     * 格式化日期时间
     */
    public static String formatDateTime(Date date) {
        return formatDate(date, DEFAULT_DATETIME_FORMAT);
    }
    
    /**
     * 格式化日期
     */
    public static String formatDate(Date date, String pattern) {
        if (date == null) {
            return null;
        }
        SimpleDateFormat sdf = new SimpleDateFormat(pattern);
        return sdf.format(date);
    }
    
    /**
     * 格式化LocalDateTime
     */
    public static String formatDateTime(LocalDateTime dateTime) {
        return formatDateTime(dateTime, DEFAULT_DATETIME_FORMAT);
    }
    
    /**
     * 格式化LocalDateTime
     */
    public static String formatDateTime(LocalDateTime dateTime, String pattern) {
        if (dateTime == null) {
            return null;
        }
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(pattern);
        return dateTime.format(formatter);
    }
    
    /**
     * 解析日期字符串
     */
    public static Date parseDate(String dateStr) {
        return parseDate(dateStr, DEFAULT_DATE_FORMAT);
    }
    
    /**
     * 解析日期时间字符串
     */
    public static Date parseDateTime(String dateTimeStr) {
        return parseDate(dateTimeStr, DEFAULT_DATETIME_FORMAT);
    }
    
    /**
     * 解析日期字符串
     */
    public static Date parseDate(String dateStr, String pattern) {
        if (dateStr == null || dateStr.trim().isEmpty()) {
            return null;
        }
        try {
            SimpleDateFormat sdf = new SimpleDateFormat(pattern);
            return sdf.parse(dateStr);
        } catch (Exception e) {
            throw new RuntimeException("日期解析失败: " + dateStr, e);
        }
    }
    
    /**
     * 解析LocalDateTime
     */
    public static LocalDateTime parseLocalDateTime(String dateTimeStr) {
        return parseLocalDateTime(dateTimeStr, DEFAULT_DATETIME_FORMAT);
    }
    
    /**
     * 解析LocalDateTime
     */
    public static LocalDateTime parseLocalDateTime(String dateTimeStr, String pattern) {
        if (dateTimeStr == null || dateTimeStr.trim().isEmpty()) {
            return null;
        }
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(pattern);
        return LocalDateTime.parse(dateTimeStr, formatter);
    }
    
    /**
     * 获取当前时间戳
     */
    public static long getCurrentTimestamp() {
        return System.currentTimeMillis();
    }
    
    /**
     * 获取当前日期时间字符串
     */
    public static String getCurrentDateTime() {
        return formatDateTime(new Date());
    }
    
    /**
     * 获取当前日期字符串
     */
    public static String getCurrentDate() {
        return formatDate(new Date());
    }
}
