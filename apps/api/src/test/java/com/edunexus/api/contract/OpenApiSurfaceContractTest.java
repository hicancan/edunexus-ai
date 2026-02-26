package com.edunexus.api.contract;

import com.edunexus.api.controller.AdminController;
import com.edunexus.api.controller.AuthController;
import com.edunexus.api.controller.StudentController;
import com.edunexus.api.controller.TeacherController;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenApiSurfaceContractTest {

    @Test
    void openApiPathsAndMethods_shouldMatchControllerAnnotations() throws Exception {
        Path openapiFile = Path.of("..", "..", "doc", "06-API契约-openapi.yaml").normalize();
        assertTrue(Files.exists(openapiFile), "OpenAPI file must exist: " + openapiFile);

        ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());
        JsonNode root = yamlMapper.readTree(Files.newInputStream(openapiFile));
        JsonNode pathsNode = root.path("paths");

        Set<String> expected = new TreeSet<>();
        pathsNode.fieldNames().forEachRemaining(path -> {
            JsonNode methodsNode = pathsNode.get(path);
            for (String method : new String[]{"get", "post", "put", "patch", "delete"}) {
                if (methodsNode.has(method)) {
                    expected.add(method.toUpperCase() + " /api/v1" + path);
                }
            }
        });

        Set<String> actual = new TreeSet<>();
        collectMappings(AuthController.class, actual);
        collectMappings(StudentController.class, actual);
        collectMappings(TeacherController.class, actual);
        collectMappings(AdminController.class, actual);

        assertEquals(expected, actual);
    }

    private void collectMappings(Class<?> controllerClass, Set<String> mappings) {
        RequestMapping root = controllerClass.getAnnotation(RequestMapping.class);
        String prefix = firstPath(root == null ? null : root.value(), root == null ? null : root.path());
        for (Method method : controllerClass.getDeclaredMethods()) {
            if (method.isSynthetic()) {
                continue;
            }
            if (method.isAnnotationPresent(GetMapping.class)) {
                mappings.add("GET " + prefix + firstPath(method.getAnnotation(GetMapping.class).value(), method.getAnnotation(GetMapping.class).path()));
            }
            if (method.isAnnotationPresent(PostMapping.class)) {
                mappings.add("POST " + prefix + firstPath(method.getAnnotation(PostMapping.class).value(), method.getAnnotation(PostMapping.class).path()));
            }
            if (method.isAnnotationPresent(PutMapping.class)) {
                mappings.add("PUT " + prefix + firstPath(method.getAnnotation(PutMapping.class).value(), method.getAnnotation(PutMapping.class).path()));
            }
            if (method.isAnnotationPresent(PatchMapping.class)) {
                mappings.add("PATCH " + prefix + firstPath(method.getAnnotation(PatchMapping.class).value(), method.getAnnotation(PatchMapping.class).path()));
            }
            if (method.isAnnotationPresent(DeleteMapping.class)) {
                mappings.add("DELETE " + prefix + firstPath(method.getAnnotation(DeleteMapping.class).value(), method.getAnnotation(DeleteMapping.class).path()));
            }
        }
    }

    private String firstPath(String[] value, String[] path) {
        if (value != null && value.length > 0 && !value[0].isBlank()) {
            return normalize(value[0]);
        }
        if (path != null && path.length > 0 && !path[0].isBlank()) {
            return normalize(path[0]);
        }
        return "";
    }

    private String normalize(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return value.startsWith("/") ? value : "/" + value;
    }
}
