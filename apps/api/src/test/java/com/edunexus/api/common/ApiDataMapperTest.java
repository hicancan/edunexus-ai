package com.edunexus.api.common;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ApiDataMapperTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void parseNullableStringMap_extractsNestedOptionText() {
        Map<String, String> parsed =
                ApiDataMapper.parseNullableStringMap(
                        """
                {
                  "A": {"text": "物体的加速度与合力成正比"},
                  "B": {"label": "物体的加速度方向与合力方向相同"},
                  "C": "牛顿第二定律只适用于宏观物体的低速运动"
                }
                """,
                        objectMapper);

        assertEquals("物体的加速度与合力成正比", parsed.get("A"));
        assertEquals("物体的加速度方向与合力方向相同", parsed.get("B"));
        assertEquals("牛顿第二定律只适用于宏观物体的低速运动", parsed.get("C"));
    }

    @Test
    void parseNullableStringMap_extractsPseudoJsonOptionText() {
        Map<String, String> parsed =
                ApiDataMapper.parseNullableStringMap(
                        Map.of(
                                "A", "{'text': '2m/s²'}",
                                "B", "{\"label\":\"3m/s²\"}"),
                        objectMapper);

        assertEquals("2m/s²", parsed.get("A"));
        assertEquals("3m/s²", parsed.get("B"));
    }
}
