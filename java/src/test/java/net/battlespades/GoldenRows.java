// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades;

import java.util.*;
import java.util.function.Function;

/** Value-based deduplication for explicitly independent golden cases only. */
public final class GoldenRows {
    private GoldenRows() {}

    public static List<String[]> distinct(List<String[]> rows) {
        return distinct(rows, Arrays::asList);
    }

    /** Keys must retain every input (including implicit flags) and expected output. */
    public static List<String[]> distinct(List<String[]> rows, Function<String[], List<String>> key) {
        var unique = new LinkedHashMap<List<String>, String[]>();
        for (var row : rows) unique.putIfAbsent(List.copyOf(key.apply(row)), row);
        return List.copyOf(unique.values());
    }
}
