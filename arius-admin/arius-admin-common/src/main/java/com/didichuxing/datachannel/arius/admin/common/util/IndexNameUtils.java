package com.didichuxing.datachannel.arius.admin.common.util;

import com.didichuxing.datachannel.arius.admin.common.bean.entity.template.IndexTemplatePhyWithLogic;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class IndexNameUtils {

    private IndexNameUtils(){}

    private static final String VERSION_TAG = "_v";

    private static final Long ONE_DAY = 24 * 60 * 60 * 1000L;

    public static String removeVersion(String indexName) {
        if (indexName == null || indexName.length() < VERSION_TAG.length()) {
            return indexName;
        }

        int i = indexName.lastIndexOf(VERSION_TAG);
        if (i < 0) {
            return indexName;
        }


        String numStr = indexName.substring(i + VERSION_TAG.length());
        try {
            if (numStr.startsWith("+") || numStr.startsWith("-")) {
                return indexName;
            }

            Long.valueOf(numStr);
        } catch (Exception t) {
            return indexName;
        }

        return indexName.substring(0, i);
    }

    public static String genCurrentDailyIndexName(String templateName){
        return templateName + "_" + DateTimeUtil.getFormatDayByOffset(0);
    }

    public static String genCurrentMonthlyIndexName(String templateName){
        return templateName + "_" + DateTimeUtil.getFormatMonthByOffset(0);
    }

    public static String genIndexNameWithVersion(String indexName, Integer version) {
        if(version == null || version <= 0) {
            return indexName;
        }
        return indexName + "_v" + version;
    }

    public static String genDailyIndexNameWithVersion(String templateName, int offsetDay, Integer version){
        return genIndexNameWithVersion(genDailyIndexName(templateName, offsetDay), version);
    }

    public static String genCurrentMonthlyIndexNameWithVersion(String templateName, Integer version){
        return genIndexNameWithVersion(genCurrentMonthlyIndexName(templateName), version);
    }

    public static String genDailyIndexName(String templateName, int offsetDay){
        return templateName + "_" + DateTimeUtil.getFormatDayByOffset(offsetDay);
    }

    //startDate???endDate ??????
    public static String genDailyIndexName(String templateName, Long startDate, Long endDate){
        if(startDate > endDate){
            return templateName + "_" + DateTimeUtil.getFormatDayByOffset(0);
        }

        long currentDate     = System.currentTimeMillis();
        long offsetFromStart = (currentDate - startDate)/ONE_DAY + 1;
        long offsetFromEnd   = (currentDate - endDate)/ONE_DAY;

        List<String> indexList = new ArrayList<>();
        for(; offsetFromStart >= offsetFromEnd; offsetFromStart--){
            indexList.add(templateName + "_" + DateTimeUtil.getFormatDayByOffset((int)offsetFromStart));
        }

        return StringUtils.join(indexList, ",");
    }

    public static boolean indexExpMatch(String index, String exp) {

        if (StringUtils.isBlank(index)) {
            return false;
        }

        if (StringUtils.isBlank(exp)) {
            return false;
        }

        int indexPointer = 0;
        int expPointer = 0;

        while (expPointer < exp.length()) {
            char expC = exp.charAt(expPointer);

            if (expC == '*') {
                expPointer++;
                boolean expPointerEnd = true;
                while (expPointer < exp.length()) {
                    expC = exp.charAt(expPointer);
                    if (expC != '*') {
                        expPointerEnd = false;
                        break;
                    }

                    expPointer++;
                }

                // * is the last char in exp
                if (expPointerEnd) {
                    return true;
                }

                int nextStar = exp.indexOf('*', expPointer);
                String expInter = null;
                if (nextStar < 0) {
                    expInter = exp.substring(expPointer);
                } else {
                    expInter = exp.substring(expPointer, nextStar);
                }

                int indexPos = index.indexOf(expInter, indexPointer);
                if (indexPos <= 0) {
                    return false;
                }

                expPointer = expPointer + expInter.length();
                indexPointer = indexPos + expInter.length();

            } else if (indexPointer < index.length()) {
                char indexC = index.charAt(indexPointer);
                if (indexC != expC) {
                    // not the same, failed
                    return false;
                }

                indexPointer++;
                expPointer++;
            } else {
                return false;
            }
        }

        if (indexPointer < index.length()) {
            // index has chars left
            return false;
        } else {
            // index also to end
            return true;
        }
    }

    /**
     * ????????????????????????????????????????????????
     *
     * @param searchIndexName
     * @param templateExp
     * @return
     */
    public static boolean isIndexNameMatchTemplateExp(final String searchIndexName,final String templateExp) {

        if (StringUtils.isBlank(searchIndexName) || StringUtils.isBlank(templateExp)) {
            return false;
        }

        String tmpTemplateExp = templateExp;

        // ????????????????????????*?????????????????????????????????
        if (!tmpTemplateExp.endsWith("*")) {
            return isSearchIndexNameMatchNoExpTemplate(searchIndexName, tmpTemplateExp);
        }

        // 1. ???????????????????????????????????????*
        tmpTemplateExp = StringUtils.removeEnd(tmpTemplateExp , "*");
        // 2. ???*????????????????????????????????????
        String[] indexSplits = StringUtils.split(searchIndexName , "*");

        if (indexSplits == null || indexSplits.length == 0) {
            return false;
        }

        // ????????????????????????????????????*
        if (!searchIndexName.endsWith("*") && indexSplits.length <= 1) {

            String trimSearchIndexName = StringUtils.removeEnd(searchIndexName , "*");
            // ??????_vx???????????????
            String lastStr = removeIndexNameVersionIfHas(trimSearchIndexName);

            // ?????????????????????????????????????????????
            String leftStr = lastStr.replace(tmpTemplateExp, "");
            if (StringUtils.isBlank(leftStr)) {
                return true;
            }

            // ??????????????????
            if (isNumbericOrSpecialChar(leftStr)) {
                return true;
            }

            // ??????_vx???????????????
            tmpTemplateExp = removeIndexNameVersionIfHas(tmpTemplateExp);
            if (lastStr.equals(tmpTemplateExp)) {
                return true;
            }

            return false;

            // ?????????????????????????????????*?????????btb_b2b.crius*hna*_2019-03-07
        } else {
            String lastStr = indexSplits[indexSplits.length - 1];
            // ??????_vx???????????????
            lastStr = removeIndexNameVersionIfHas(lastStr);
            indexSplits[indexSplits.length - 1] = lastStr;

            // lastStr?????????,??????????????????????????????
            if (isNumbericOrSpecialChar(lastStr)
                    && isMatchIndexPartPositiveSequence(tmpTemplateExp, indexSplits, indexSplits.length - 1)) {
                return true;
            }

            // ??????lastStr????????????
            lastStr = removeIndexNameDateIfHas(lastStr);
            if (StringUtils.isBlank(lastStr)) {
                if (isMatchIndexPartPositiveSequence(tmpTemplateExp, indexSplits, indexSplits.length - 1)) {
                    return true;
                } else {
                    return false;
                }

            } else {
                indexSplits[indexSplits.length - 1] = lastStr;
                if (isMatchIndexPartPositiveSequence(tmpTemplateExp, indexSplits, indexSplits.length)) {
                    return true;
                } else {
                    return false;
                }
            }
        }
    }

    /**
     * ???????????????????????????????????????????????????
     *
     * ??????templateExp???ABCDEFG,partIndexName???AB,DE,FG????????????
     *
     * @param templateExp
     * @param partIndexName
     * @return
     */
    public static boolean isMatchIndexPartPositiveSequence(String templateExp, String[] partIndexName, int compareCount) {
        // ????????????????????????
        List<Integer> indexList = Lists.newArrayList();

        for (int i = 0; i < partIndexName.length && i < compareCount; ++i) {
            indexList.add(templateExp.indexOf(partIndexName[i]));
        }

        Integer lastIndex = null;
        for (int i = 0; i < indexList.size(); ++i) {
            // ????????????????????????????????????????????????
            if (indexList.get(i) < 0) {
                return false;
            }
            if (lastIndex == null) {
                lastIndex = indexList.get(i);
            } else {
                if (indexList.get(i) >= lastIndex) {
                    lastIndex = indexList.get(i);
                } else {
                    // ??????????????????????????????????????????????????????
                    return false;
                }
            }
        }

        // ????????????????????????, ????????????????????????????????????
        if (indexList.size() == 1 && indexList.get(0) > 0) {
            return false;
        }

        return true;
    }

    /**
     * ??????????????????????????????????????????????????????
     *
     * @param lastIndexNamePart
     * @return
     */
    public static String removeIndexNameVersionIfHas(String lastIndexNamePart) {
        // ????????????_vx???????????????
        int index = lastIndexNamePart.lastIndexOf("_v");
        if (index > 0) {
            // ??????_v
            String endStr = lastIndexNamePart.substring(index + 2);
            // _v?????????????????????
            if (StringUtils.isNumeric(endStr)) {
                // ?????????,???????????????
                return lastIndexNamePart.substring(0, index);
            }
        }

        return lastIndexNamePart;
    }

    /**
     * ?????????????????????????????????
     *
     * @param lastIndexNamePart
     * @return
     */
    public static boolean isNumbericOrSpecialChar(String lastIndexNamePart) {
        for (int i = 0; i < lastIndexNamePart.length(); ++i) {
            char c = lastIndexNamePart.charAt(i);
            // ???????????????-,_
            if (Character.isDigit(c) || c == '-' || c == '_') {
                continue;
            } else {
                return false;
            }
        }

        return true;
    }

    /**
     * ???????????????????????????????????????
     *
     * @param lastIndexNamePart
     * @return
     */
    public static String removeIndexNameDateIfHas(String lastIndexNamePart) {

        int index = lastIndexNamePart.lastIndexOf("_");
        if (index >= 0) {
            String endStr = lastIndexNamePart.substring(index + 1);
            // ?????????????????????????????????
            if (isNumbericOrSpecialChar(endStr)) {
                return lastIndexNamePart.substring(0, index);
            }
        } else {
            if (isNumbericOrSpecialChar(lastIndexNamePart)) {
                return "";
            }
        }

        return lastIndexNamePart;
    }

    /**
     * ??????????????????????????????????????????????????????????????????
     *
     * @param searchIndexName
     * @param templateExp  ?????????????????????*
     * @return
     */
    public static boolean isSearchIndexNameMatchNoExpTemplate(final String searchIndexName, final String templateExp) {

        if (StringUtils.isBlank(searchIndexName) || StringUtils.isBlank(templateExp)) {
            return false;
        }

        // 1. ???*????????????????????????????????????
        String[] indexSplits = StringUtils.split(searchIndexName , "*");

        if (indexSplits == null || indexSplits.length == 0) {
            return false;
        }

        // ????????????????????????????????????*
        if (!searchIndexName.endsWith("*") && indexSplits.length <= 1) {
            String trimSearchIndexName = StringUtils.removeEnd(searchIndexName , "*");
            // ?????????????????????????????????????????????????????????arius.dsl.template -> arius.dsl.template
            if (trimSearchIndexName.equals(templateExp)) {
                return true;
            } else {
                return false;
            }

        } else {
            // ??????*??????????????????????????????

            // ??????_vx???????????????
            String lastStr = removeIndexNameVersionIfHas(indexSplits[indexSplits.length - 1]);
            indexSplits[indexSplits.length - 1] = lastStr;

            // *???????????????
            if (StringUtils.isBlank(lastStr)) {
                // ????????????????????????
                if (isMatchIndexPartPositiveSequence(templateExp, indexSplits, indexSplits.length - 1)) {
                    return true;
                } else {
                    return false;
                }
            }

            // ??????lastStr??????_??????????????????????????????_
            if (lastStr.contains("_") && !templateExp.contains("_")) {
                return false;
            }

            // lastStr?????????
            if (isNumbericOrSpecialChar(lastStr)) {
                return false;
            }

            // ????????????????????????
            if (isMatchIndexPartPositiveSequence(templateExp, indexSplits, indexSplits.length)) {
                return true;
            } else {
                return false;
            }

        }
    }

    /**
     * ????????????????????????????????????????????????????????????
     *
     * @param indexName
     * @param indexTemplateList
     * @return
     */
    public static Set<String> matchIndexTemplateBySearchIndexName(String indexName, List<IndexTemplatePhyWithLogic> indexTemplateList) {
        Set<String> matchIndexTemplateNameSet = Sets.newTreeSet();
        String tmpIndexName = indexName;
        if (tmpIndexName != null && tmpIndexName.endsWith("*")) {
            tmpIndexName = StringUtils.removeEnd(tmpIndexName, "*");
        }

        for (IndexTemplatePhyWithLogic indexTemplate : indexTemplateList) {

            // ?????????????????????
            if (IndexNameUtils.isIndexNameMatchTemplateExp(indexName, indexTemplate.getExpression())) {
                matchIndexTemplateNameSet.add(indexTemplate.getName());
            }
        }

        return matchIndexTemplateNameSet;
    }
}
