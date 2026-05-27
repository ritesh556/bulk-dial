package com.ritesh.autodialer;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class CsvUtils {

    public static class CsvResult {
        public final List<CallItem> items;
        public final int skippedRows;

        CsvResult(List<CallItem> items, int skippedRows) {
            this.items = items;
            this.skippedRows = skippedRows;
        }
    }

    public static CsvResult parse(InputStream stream) throws IOException {
        List<List<String>> rows = new ArrayList<>();
        BufferedReader reader = new BufferedReader(new InputStreamReader(stream));
        String line;
        while ((line = reader.readLine()) != null) {
            if (!line.trim().isEmpty()) rows.add(parseCsvLine(line));
        }

        List<CallItem> result = new ArrayList<>();
        int skipped = 0;
        if (rows.isEmpty()) return new CsvResult(result, skipped);

        Map<String, Integer> header = new HashMap<>();
        boolean hasHeader = looksLikeHeader(rows.get(0));
        int startRow = hasHeader ? 1 : 0;

        if (hasHeader) {
            List<String> headerRow = rows.get(0);
            for (int i = 0; i < headerRow.size(); i++) {
                header.put(headerRow.get(i).trim().toLowerCase(Locale.US), i);
            }
        }

        for (int r = startRow; r < rows.size(); r++) {
            List<String> row = rows.get(r);
            String name;
            String phone;
            String consent = "true";

            if (hasHeader) {
                name = value(row, firstExisting(header, "name", "full_name", "person"));
                phone = value(row, firstExisting(header, "phone", "number", "mobile", "phone_number"));
                int consentIndex = firstExisting(header, "consent", "permission", "allowed");
                if (consentIndex >= 0) consent = value(row, consentIndex);
            } else if (row.size() == 1) {
                name = "";
                phone = row.get(0);
            } else {
                name = row.get(0);
                phone = row.get(1);
                if (row.size() >= 3) consent = row.get(2);
            }

            if (phone == null) phone = "";
            phone = cleanPhone(phone);

            if (!isConsentYes(consent) || !isValidPhone(phone)) {
                skipped++;
                continue;
            }

            result.add(new CallItem(name, phone));
        }

        return new CsvResult(result, skipped);
    }

    private static boolean looksLikeHeader(List<String> row) {
        for (String cell : row) {
            String s = cell.trim().toLowerCase(Locale.US);
            if (s.equals("phone") || s.equals("number") || s.equals("mobile") ||
                    s.equals("name") || s.equals("consent") || s.equals("permission")) {
                return true;
            }
        }
        return false;
    }

    private static int firstExisting(Map<String, Integer> header, String... names) {
        for (String name : names) {
            if (header.containsKey(name)) return header.get(name);
        }
        return -1;
    }

    private static String value(List<String> row, int index) {
        if (index < 0 || index >= row.size()) return "";
        return row.get(index);
    }

    private static boolean isConsentYes(String value) {
        if (value == null || value.trim().isEmpty()) return true;
        String s = value.trim().toLowerCase(Locale.US);
        return s.equals("true") || s.equals("yes") || s.equals("y") || s.equals("1") || s.equals("allowed");
    }

    private static boolean isValidPhone(String phone) {
        if (phone == null) return false;
        String digits = phone.replaceAll("[^0-9]", "");
        return digits.length() >= 5 && digits.length() <= 15;
    }

    private static String cleanPhone(String raw) {
        String s = raw.trim();
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (Character.isDigit(c)) out.append(c);
            else if (c == '+' && out.length() == 0) out.append(c);
        }
        return out.toString();
    }

    private static List<String> parseCsvLine(String line) {
        List<String> cells = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;

        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    current.append('"');
                    i++;
                } else {
                    inQuotes = !inQuotes;
                }
            } else if (c == ',' && !inQuotes) {
                cells.add(current.toString().trim());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        cells.add(current.toString().trim());
        return cells;
    }
}
