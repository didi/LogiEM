package com.didi.arius.gateway.common.utils;

import com.didi.arius.gateway.common.consts.QueryConsts;
import com.didi.arius.gateway.common.exception.InvalidParameterException;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import org.apache.commons.lang3.StringUtils;
import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class DateUtil {

    private DateUtil(){}

    protected static final List<String> timePatterns = Arrays.asList( "yyyy-MM-dd", "yyyy-MM-dd HH:mm:ss", "yyyy-MM-dd HH:mm:ss.SSS Z", "yyyy-MM-dd'T'HH:mm:ssZ");

    private static final long MILLIS_ZONE_OFFSET = LocalDateTime.of(1970, 1, 1, 0, 0, 0,
            0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    private static LoadingCache<Long, Map<String, String>> dayFormatCache = CacheBuilder.newBuilder().concurrencyLevel(20).expireAfterWrite(5,
            TimeUnit.MINUTES).initialCapacity(60).maximumSize(100).recordStats().build(new CacheLoader<Long, Map<String, String>>() {

        @Override
        public Map<String, String> load(Long key) {
            return new ConcurrentHashMap<>();
        }
    });

    public static long transformToMillis(String date) throws InvalidParameterException {
        long messageTime = 0;

        if (StringUtils.isNumeric(date)) {
            if (date.length() == 13) {
                messageTime = Long.parseLong(date);
            } else if (date.length() == 10) {
                messageTime = Long.parseLong(date);
                messageTime = messageTime * 1000;
            }
        } else if (!StringUtils.isEmpty(date)) {
            for (String timePattern : timePatterns) {
                try {
                    messageTime = DateTime.parse(date, DateTimeFormat.forPattern(timePattern)).getMillis();
                    break;
                } catch (Exception e) {
                    //pass
                }
            }
        }

        if (messageTime == 0) {
            throw new InvalidParameterException("date format error, date=" + date);
        }

        return messageTime;
    }

    public static String transformToDateFormat(long time, String dateFormat) {
        // ???????????????????????????????????????
        long key = (time - MILLIS_ZONE_OFFSET) / QueryConsts.DAY_MILLIS;
        String dateFormatTime = null;
        dateFormat = dateFormat.replace('Y', 'y');

        try {
            // ??????????????????
            Map<String, String> format2DayValueMap = dayFormatCache.get(key);

            // ????????????????????????????????????????????????, ???????????????????????????????????????dayFormatCache?????????load?????????????????????key
            if (null == format2DayValueMap) {

                format2DayValueMap = new ConcurrentHashMap<>();
                // ??????????????????????????????????????????????????????map???

                Instant instant = Instant.ofEpochMilli(time);
                LocalDateTime dateTime = LocalDateTime.ofInstant(instant, ZoneId.systemDefault());
                dateFormatTime = DateTimeFormatter.ofPattern(dateFormat).format(dateTime);

                format2DayValueMap.put(dateFormat, dateFormatTime);
                // ???????????????
                dayFormatCache.put(key, format2DayValueMap);
            } else {
                // ????????????????????????????????????
                if (format2DayValueMap.containsKey(dateFormat)) {
                    dateFormatTime = format2DayValueMap.get(dateFormat);
                } else {
                    // ??????????????????????????????????????????????????????map???
                    Instant instant = Instant.ofEpochMilli(time);
                    LocalDateTime dateTime = LocalDateTime.ofInstant(instant, ZoneId.systemDefault());

                    dateFormatTime = DateTimeFormatter.ofPattern(dateFormat).format(dateTime);

                    // ??????????????????map???
                    format2DayValueMap.put(dateFormat, dateFormatTime);
                }
            }
        } catch (Exception e) {
            //pass
        }

        return dateFormatTime;
    }

    public static List<String> getDateFormatSuffix(long start, long end, String dateFormat) {
        List<String> suffixes = new ArrayList<>();

        if (start > end) {
            return suffixes;
        }

        DateTime startDate = new DateTime(start);
        DateTime endDate = new DateTime(end);

        String startSuffix = startDate.toString(dateFormat);
        String endSuffix = endDate.toString(dateFormat);

        suffixes.add(startSuffix);

        String lastSuffix = startSuffix;
        if (dateFormat.endsWith("dd")) {
            while (startDate.plusDays(1).getMillis() < endDate.getMillis()) {
                startDate = startDate.plusDays(1);

                String suffix = startDate.toString(dateFormat);
                suffixes.add(suffix);

                lastSuffix = suffix;
            }
        } else if (dateFormat.endsWith("MM")) {
            while (startDate.plusMonths(1).getMillis() < endDate.getMillis()) {
                startDate = startDate.plusMonths(1);

                String suffix = startDate.toString(dateFormat);
                suffixes.add(suffix);

                lastSuffix = suffix;
            }
        } else if (dateFormat.toLowerCase().endsWith("yy")) {
            while (startDate.plusYears(1).getMillis() < endDate.getMillis()) {
                startDate = startDate.plusYears(1);

                String suffix = startDate.toString(dateFormat);
                suffixes.add(suffix);

                lastSuffix = suffix;
            }
        }

        if (!endSuffix.equals(lastSuffix)) {
            suffixes.add(endSuffix);
        }

        return suffixes;
    }
}
