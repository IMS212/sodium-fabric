package net.caffeinemc.mods.sodium.service;

import com.google.common.collect.ImmutableMap;
import com.sun.jna.platform.unix.LibC;
import net.minecraftforge.fml.loading.moddiscovery.AbstractJarFileModLocator;
import org.lwjgl.system.Configuration;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.stream.Stream;

public class ModLocator extends AbstractJarFileModLocator {
    @Override
    public Stream<Path> scanCandidates() {
        URL jarLocation = getClass().getProtectionDomain().getCodeSource().getLocation();
        try {
            Path path = Path.of(jarLocation.toURI()).resolve("META-INF").resolve("jarjar");
            return Files.walk(path).filter(p -> p.getFileName().toString().startsWith("sodium") || p.getFileName().toString().startsWith("interval-tree") || p.getFileName().toString().equalsIgnoreCase("jarjar"));
        } catch (URISyntaxException | IOException e) {
            throw new RuntimeException(e);
        }
    }
    // I honestly have no idea why this works.
    private static Path getJarInJar(Path name) throws IOException, URISyntaxException {
        // Code taken from JarInJarDependencyLocator#loadModFileFrom
        URI filePathUri = new URI("jij:" + name.toAbsolutePath().toUri().getRawSchemeSpecificPart()).normalize();
        Map<String, ?> outerFsArgs = ImmutableMap.of("packagePath", name);
        FileSystem zipFS = FileSystems.newFileSystem(filePathUri, outerFsArgs);
        return zipFS.getPath("/");
    }
    @Override
    public String name() {
        return "sodium-locator";
    }

    @Override
    public void initArguments(Map<String, ?> arguments) {

    }
}