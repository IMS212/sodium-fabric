package net.caffeinemc.sodium.render.shader;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;

import com.google.common.base.Joiner;
import com.google.common.collect.Sets;
import com.google.gson.JsonObject;
import net.caffeinemc.gfx.api.shader.ShaderType;
import net.caffeinemc.sodium.SodiumClientMod;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.GLImportProcessor;
import net.minecraft.client.render.Shader;
import net.minecraft.util.FileNameUtil;
import net.minecraft.util.Identifier;
import net.minecraft.util.JsonHelper;
import org.apache.commons.io.IOUtils;

public interface ShaderLoader<T> {

    GLImportProcessor processor = new GLImportProcessor() {
        private final Set<String> visitedImports = Sets.newHashSet();

        public String loadImport(boolean inline, String name) {
            String var10000 = "shaders/include/";
            name = FileNameUtil.normalizeToPosix(var10000 + name);
            if (!this.visitedImports.add(name)) {
                return null;
            } else {
                Identifier identifier = new Identifier(name);

                try {
                    Reader reader = MinecraftClient.getInstance().getResourceManager().openAsReader(identifier);

                    String var5;
                    try {
                        var5 = IOUtils.toString(reader);
                    } catch (Throwable var8) {
                        if (reader != null) {
                            try {
                                reader.close();
                            } catch (Throwable var7) {
                                var8.addSuppressed(var7);
                            }
                        }

                        throw var8;
                    }

                    if (reader != null) {
                        reader.close();
                    }

                    return var5;
                } catch (IOException var9) {
                    SodiumClientMod.logger().error("Could not open GLSL import {}: {}", name, var9.getMessage());
                    return "#error " + var9.getMessage();
                }
            }
        }
    };
    ShaderLoader<String> MINECRAFT_ASSETS = new ShaderLoader<>() {
        @Override
        public String getShaderSource(String name, ShaderType type) {
            Identifier identifier = new Identifier("shaders/core/" + name + ".json");

            try (Reader reader = MinecraftClient.getInstance().getResourceManager().openAsReader(identifier)) {
                JsonObject jsonObject = JsonHelper.deserialize(reader);
                Reader reader2 = MinecraftClient.getInstance().getResourceManager().openAsReader(new Identifier("shaders/core/" + JsonHelper.getString(jsonObject, type.name().toLowerCase(Locale.ROOT)) + "." + (type == ShaderType.FRAGMENT ? "fsh" : "vsh")));
                String value = String.join("\n", processor.readSource(IOUtils.toString(reader2)));
                value = value.replaceAll("#line \\d \\d", "");
                reader2.close();
                return value;
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    };

    String getShaderSource(T name, ShaderType type);

    String getShaderSource(String name, ShaderType type);
}
