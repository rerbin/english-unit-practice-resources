package com.englishpaper.reader;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Pure-Java read-aloud scoring: normalization, word match rate and phoneme coaching hints. No Android deps so it is JVM-testable. */
public final class ReadAloudScorer {
    private ReadAloudScorer() { }

    public static String normalize(String s) {
        if (s == null) return "";
        StringBuilder b = new StringBuilder();
        for (char c : s.toLowerCase().toCharArray()) {
            if (c >= 'a' && c <= 'z') b.append(c);
            else if (c >= '0' && c <= '9') b.append(c);
            else if (c == ' ' || c == '-' || c == '\'') b.append(' ');
        }
        return b.toString().replaceAll("\\s+", " ").trim();
    }

    public static List<String> words(String s) {
        List<String> out = new ArrayList<>();
        for (String w : normalize(s).split(" ")) if (!w.isEmpty()) out.add(w);
        return out;
    }

    /** 1.0 when identical; otherwise LCS word ratio in [0,1]. */
    public static double wordMatchRate(String target, String heard) {
        List<String> a = words(target), b = words(heard);
        if (a.isEmpty() || b.isEmpty()) return 0;
        int n = a.size(), m = b.size();
        int[][] dp = new int[n + 1][m + 1];
        for (int i = n - 1; i >= 0; i--) for (int j = m - 1; j >= 0; j--)
            dp[i][j] = a.get(i).equals(b.get(j)) ? dp[i + 1][j + 1] + 1 : Math.max(dp[i + 1][j], dp[i][j + 1]);
        return (double) dp[0][0] / Math.max(n, m);
    }

    /** First word pair that differs and is covered by the phoneme dict; returns coaching hint or null. */
    public static String phonemeHint(String target, String heard, Map<String, String[]> dict) {
        List<String> a = words(target), b = words(heard);
        int n = Math.min(a.size(), b.size());
        for (int i = 0; i < n; i++) {
            String t = a.get(i), h = b.get(i);
            if (t.equals(h)) continue;
            String[] tp = dict.get(t), hp = dict.get(h);
            if (tp == null || hp == null) return "机器听到：" + h + "（应为 " + t + "）";
            String[] diff = firstPhonemeDiff(tp, hp);
            return diff == null ? "机器听到：" + h + "（应为 " + t + "）"
                    : "机器听到：" + h + "；" + t + " 的 " + diff[0] + " 被读成了 " + diff[1];
        }
        if (b.size() > a.size()) return "机器听到多了：" + String.join(" ", b.subList(a.size(), b.size()));
        if (a.size() > b.size()) return "机器听到少了：" + String.join(" ", a.subList(b.size(), a.size()));
        return null;
    }

    /** Needleman-Wunsch style alignment over phoneme arrays; returns {expected, actual} of first mismatch or null. */
    public static String[] firstPhonemeDiff(String[] t, String[] h) {
        int n = t.length, m = h.length;
        int[][] dp = new int[n + 1][m + 1];
        for (int i = 0; i <= n; i++) dp[i][m] = (n - i) * -1;
        for (int j = 0; j <= m; j++) dp[n][j] = (m - j) * -1;
        for (int i = n - 1; i >= 0; i--) for (int j = m - 1; j >= 0; j--) {
            int best = dp[i + 1][j + 1] + (t[i].equals(h[j]) ? 0 : -1);
            best = Math.max(best, dp[i + 1][j] - 1);
            best = Math.max(best, dp[i][j + 1] - 1);
            dp[i][j] = best;
        }
        int i = 0, j = 0;
        while (i < n && j < m) {
            if (!t[i].equals(h[j]) && dp[i][j] == dp[i + 1][j + 1] - 1) return new String[]{t[i], h[j]};
            if (t[i].equals(h[j]) && dp[i][j] == dp[i + 1][j + 1]) { i++; j++; continue; }
            if (dp[i][j] == dp[i + 1][j] - 1) { i++; continue; }
            j++;
        }
        while (i < n) { if (j >= m) return new String[]{t[i], "（缺失）"}; i++; }
        while (j < m) return new String[]{"（缺失）", h[j]};
        return null;
    }
}
