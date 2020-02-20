package javavulkantutorial;

import org.joml.Matrix4f;
import org.joml.Vector2f;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.io.*;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import static java.util.Objects.requireNonNull;

/**
 * An utility class for dealing with alignments in Uniform Buffer Objects
 *
 * Vulkan expects the data in your structure to be aligned in memory in a specific way, for example:
 *
 * Scalars have to be aligned by N (= 4 bytes given 32 bit floats).
 * A vec2 must be aligned by 2N (= 8 bytes)
 * A vec3 or vec4 must be aligned by 4N (= 16 bytes)
 * A nested structure must be aligned by the base alignment of its members rounded up to a multiple of 16.
 * A mat4 matrix must have the same alignment as a vec4.
 *
 * */
public final class AlignmentUtils {

    private AlignmentUtils() {}

    public static int sizeof(Object obj) {
        return obj == null ? 0 : SIZEOF_CACHE.getOrDefault(obj.getClass(), 0);
    }

    public static int alignof(Object obj) {
        return obj == null ? 0 : SIZEOF_CACHE.getOrDefault(obj.getClass(), Integer.BYTES);
    }

    public static int alignas(int offset, int alignment) {
        return offset % alignment == 0 ? offset : ((offset - 1) | (alignment - 1)) + 1;
    }

    private static final Map<Class<?>, Integer> SIZEOF_CACHE = new HashMap<>();
    static {
        SIZEOF_CACHE.put(Byte.class, Byte.BYTES);
        SIZEOF_CACHE.put(Character.class, Character.BYTES);
        SIZEOF_CACHE.put(Short.class, Short.BYTES);
        SIZEOF_CACHE.put(Integer.class, Integer.BYTES);
        SIZEOF_CACHE.put(Float.class, Float.BYTES);
        SIZEOF_CACHE.put(Long.class, Long.BYTES);
        SIZEOF_CACHE.put(Double.class, Double.BYTES);

        SIZEOF_CACHE.put(Vector2f.class, 2 * Float.BYTES);
        SIZEOF_CACHE.put(Vector3f.class, 3 * Float.BYTES);
        SIZEOF_CACHE.put(Vector4f.class, 4 * Float.BYTES);

        SIZEOF_CACHE.put(Matrix4f.class, SIZEOF_CACHE.get(Vector4f.class));
    }
}
