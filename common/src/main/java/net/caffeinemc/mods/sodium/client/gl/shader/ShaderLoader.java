package net.caffeinemc.mods.sodium.client.gl.shader;

import com.google.common.base.Objects;
import net.caffeinemc.mods.sodium.client.services.PlatformRuntimeInformation;
import org.apache.commons.io.IOUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ShaderLoader {
    private static final Logger LOGGER = LoggerFactory.getLogger("Sodium-ShaderLoader");

    private static final boolean OPTION_DEBUG_SHADERS =
            Objects.equal(System.getProperty("sodium.debug.shaders.dump", "false"), "true");


    public static LoadedShader loadShaderSource(ShaderType type, Identifier name, ShaderConstants constants) {
        var parsedShader = ShaderParser.parseShader(getShaderSource(name), constants);

        if (OPTION_DEBUG_SHADERS) {
            LOGGER.info("Loaded shader {} with constants {}", name, constants);
            LOGGER.info(parsedShader.src());
        }

        return new LoadedShader(type, name, parsedShader);
    }

    public static String getShaderSource(Identifier name) {
        String path = String.format("/assets/%s/shaders/%s", name.getNamespace(), name.getPath());

        try (InputStream in = ShaderLoader.class.getResourceAsStream(path)) {
            if (in == null) {
                throw new RuntimeException("Shader not found: " + path);
            }

            return IOUtils.toString(in, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("Failed to read shader source for " + path, e);
        }
    }

    public record LoadedShader(ShaderType type, Identifier name, ShaderParser.ParsedShader parsedShader) {
    }
}
