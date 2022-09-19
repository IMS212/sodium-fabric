package net.caffeinemc.sodium.render.shader;

import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.caffeinemc.gfx.api.shader.ShaderType;
import net.minecraft.client.gl.GLImportProcessor;
import net.minecraft.util.Identifier;

public class ShaderParser {

    public static <T> String parseSodiumShader(ShaderLoader<T> loader, ShaderType type,  T name) {
        String src = loader.getShaderSource(name, type);

        List<String> lines = parseSodiumShader(loader, type, src);

        return String.join("\n", lines);
    }

    public static <T> String parseSodiumShader(ShaderLoader<T> loader, T name, ShaderType type, ShaderConstants constants) {
        String src = loader.getShaderSource(name, type);

        List<String> lines = parseSodiumShader(loader, type, src);

        return String.join("\n", lines);
    }

    public static List<String> parseSodiumShader(ShaderLoader<?> loader, ShaderType type, String src) {
        List<String> builder = new LinkedList<>();
        String line;

        try (BufferedReader reader = new BufferedReader(new StringReader(src))) {
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("#import")) {
                    builder.addAll(resolveImport(loader, type, line));
                } else {
                    builder.add(line);
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to read shader sources", e);
        }

        return builder;
    }

    private static final Pattern IMPORT_PATTERN = Pattern.compile("#import <(?<name>.*)>");

    private static List<String> resolveImport(ShaderLoader<?> loader, ShaderType type, String line) {
        Matcher matcher = IMPORT_PATTERN.matcher(line);

        if (!matcher.matches()) {
            throw new IllegalArgumentException("Malformed import statement (expected format: " + IMPORT_PATTERN + ")");
        }

        String name = matcher.group("name");
        String source = loader.getShaderSource(name, type);

        return ShaderParser.parseSodiumShader(loader, type, source);
    }
}
