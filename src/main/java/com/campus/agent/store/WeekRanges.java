package com.campus.agent.store;

import java.util.ArrayList;
import java.util.List;

/** 解析 "1-16"、"1-8,10-16"、"5"、"1-16单/双" 形式的周次表达式。 */
public final class WeekRanges {

    private WeekRanges() {
    }

    public static boolean contains(String expression, int week) {
        if (expression == null || expression.isBlank()) return true;
        List<int[]> ranges = new ArrayList<>();
        boolean oddOnly = false, evenOnly = false;
        for (String part : expression.split(",")) {
            String p = part.trim();
            if (p.endsWith("单")) { oddOnly = true; p = p.substring(0, p.length() - 1); }
            if (p.endsWith("双")) { evenOnly = true; p = p.substring(0, p.length() - 1); }
            int dash = p.indexOf('-');
            if (dash > 0) {
                ranges.add(new int[]{Integer.parseInt(p.substring(0, dash)), Integer.parseInt(p.substring(dash + 1))});
            } else {
                ranges.add(new int[]{Integer.parseInt(p), Integer.parseInt(p)});
            }
        }
        boolean inRange = ranges.stream().anyMatch(r -> week >= r[0] && week <= r[1]);
        if (oddOnly) return inRange && week % 2 == 1;
        if (evenOnly) return inRange && week % 2 == 0;
        return inRange;
    }
}
