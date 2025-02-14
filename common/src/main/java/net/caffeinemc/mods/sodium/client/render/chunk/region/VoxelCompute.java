package net.caffeinemc.mods.sodium.client.render.chunk.region;

import com.mojang.blaze3d.platform.GlStateManager;
import org.apache.commons.io.IOUtils;
import org.lwjgl.opengl.GL20C;
import org.lwjgl.opengl.GL46C;

import java.io.IOException;

public class VoxelCompute {
    private static int compute;

    static {
        compute = GL46C.glCreateProgram();
        int shader = GL46C.glCreateShader(GL46C.GL_COMPUTE_SHADER);
        try {
            GlStateManager.glShaderSource(shader, IOUtils.toString(VoxelCompute.class.getResourceAsStream("/voxel.csh")));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        GL46C.glCompileShader(shader);
        GL46C.glAttachShader(compute, shader);
        GL46C.glLinkProgram(compute);

        String log = GlStateManager.glGetProgramInfoLog(compute, 16384);

        if (!log.isEmpty()) {
            System.out.println("Program link log for voxel: " + log);

        }
        int result = GlStateManager.glGetProgrami(compute, GL20C.GL_LINK_STATUS);

        if (result != GL20C.GL_TRUE) {
            throw new RuntimeException(log);
        }

        GL46C.glDetachShader(compute, shader);
        GL46C.glDeleteShader(shader);
    }

    public static void run(int size, PersistentBufferObject ubo) {
        if (size == 0) return;
        ubo.bind();
        GL46C.glUseProgram(compute);
        GL46C.glDispatchCompute(size, 1, 1);
    }
}
