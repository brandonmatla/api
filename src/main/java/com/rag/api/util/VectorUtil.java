package com.rag.api.util;

import java.util.List;
import java.util.stream.Collectors;

public class VectorUtil {

    public static String toPgVector(List<Double> vector) {
        return vector.stream()
                .map(String::valueOf)
                .collect(Collectors.joining(",", "[", "]"));
    }
}