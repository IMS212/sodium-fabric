package me.cortex.testbed;

import me.cortex.vulkanitelib.VContextBuilder;
import me.cortex.vulkanitelib.VVkContext;
import me.cortex.vulkanitelib.VVkDevice;
import me.cortex.vulkanitelib.descriptors.VVkDescriptorSetLayout;
import me.cortex.vulkanitelib.descriptors.builders.DescriptorSetLayoutBuilder;
import me.cortex.vulkanitelib.memory.buffer.VVkBuffer;
import me.cortex.vulkanitelib.memory.image.VGlVkImage;
import me.cortex.vulkanitelib.memory.image.VVkFramebuffer;
import me.cortex.vulkanitelib.memory.image.VVkImage;
import me.cortex.vulkanitelib.other.VVkCommandBuffer;
import me.cortex.vulkanitelib.other.VVkCommandPool;
import me.cortex.vulkanitelib.other.VVkQueue;
import me.cortex.vulkanitelib.pipelines.VVkPipeline;
import me.cortex.vulkanitelib.pipelines.VVkRenderPass;
import me.cortex.vulkanitelib.pipelines.VVkShader;
import me.cortex.vulkanitelib.pipelines.builders.GraphicsPipelineBuilder;
import me.cortex.vulkanitelib.pipelines.builders.RenderPassBuilder;
import org.lwjgl.PointerBuffer;
import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.opengl.GL11C;
import org.lwjgl.opengl.GLCapabilities;
import org.lwjgl.opengl.GLUtil;
import org.lwjgl.system.Callback;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.vulkan.*;

import java.nio.ByteBuffer;
import java.nio.LongBuffer;

import static me.cortex.vulkanitelib.utils.VVkUtils._CHECK_;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.glfw.GLFW.glfwSwapInterval;
import static org.lwjgl.opengl.ARBDirectStateAccess.*;
import static org.lwjgl.opengl.EXTFramebufferObject.*;
import static org.lwjgl.opengl.EXTSemaphore.*;
import static org.lwjgl.opengl.EXTSemaphoreFD.GL_HANDLE_TYPE_OPAQUE_FD_EXT;
import static org.lwjgl.opengl.EXTSemaphoreFD.glImportSemaphoreFdEXT;
import static org.lwjgl.opengl.GL.createCapabilities;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL30.*;
import static org.lwjgl.util.vma.Vma.vmaMapMemory;
import static org.lwjgl.util.vma.Vma.vmaUnmapMemory;
import static org.lwjgl.vulkan.EXTDescriptorIndexing.VK_EXT_DESCRIPTOR_INDEXING_EXTENSION_NAME;
import static org.lwjgl.vulkan.KHRAccelerationStructure.VK_KHR_ACCELERATION_STRUCTURE_EXTENSION_NAME;
import static org.lwjgl.vulkan.KHRBufferDeviceAddress.VK_KHR_BUFFER_DEVICE_ADDRESS_EXTENSION_NAME;
import static org.lwjgl.vulkan.KHRDeferredHostOperations.VK_KHR_DEFERRED_HOST_OPERATIONS_EXTENSION_NAME;
import static org.lwjgl.vulkan.KHRExternalFenceCapabilities.VK_KHR_EXTERNAL_FENCE_CAPABILITIES_EXTENSION_NAME;
import static org.lwjgl.vulkan.KHRExternalFenceFd.VK_KHR_EXTERNAL_FENCE_FD_EXTENSION_NAME;
import static org.lwjgl.vulkan.KHRExternalMemory.VK_KHR_EXTERNAL_MEMORY_EXTENSION_NAME;
import static org.lwjgl.vulkan.KHRExternalMemoryCapabilities.VK_KHR_EXTERNAL_MEMORY_CAPABILITIES_EXTENSION_NAME;
import static org.lwjgl.vulkan.KHRExternalMemoryFd.VK_KHR_EXTERNAL_MEMORY_FD_EXTENSION_NAME;
import static org.lwjgl.vulkan.KHRExternalSemaphore.VK_KHR_EXTERNAL_SEMAPHORE_EXTENSION_NAME;
import static org.lwjgl.vulkan.KHRExternalSemaphoreCapabilities.VK_KHR_EXTERNAL_SEMAPHORE_CAPABILITIES_EXTENSION_NAME;
import static org.lwjgl.vulkan.KHRExternalSemaphoreFd.VK_KHR_EXTERNAL_SEMAPHORE_FD_EXTENSION_NAME;
import static org.lwjgl.vulkan.KHRExternalSemaphoreFd.vkGetSemaphoreFdKHR;
import static org.lwjgl.vulkan.KHRGetMemoryRequirements2.VK_KHR_GET_MEMORY_REQUIREMENTS_2_EXTENSION_NAME;
import static org.lwjgl.vulkan.KHRGetPhysicalDeviceProperties2.VK_KHR_GET_PHYSICAL_DEVICE_PROPERTIES_2_EXTENSION_NAME;
import static org.lwjgl.vulkan.KHRRayTracingPipeline.VK_KHR_RAY_TRACING_PIPELINE_EXTENSION_NAME;
import static org.lwjgl.vulkan.KHRShaderFloatControls.VK_KHR_SHADER_FLOAT_CONTROLS_EXTENSION_NAME;
import static org.lwjgl.vulkan.KHRSpirv14.VK_KHR_SPIRV_1_4_EXTENSION_NAME;
import static org.lwjgl.vulkan.KHRSwapchain.VK_IMAGE_LAYOUT_PRESENT_SRC_KHR;
import static org.lwjgl.vulkan.VK10.*;
import static org.lwjgl.vulkan.VK11.VK_EXTERNAL_SEMAPHORE_HANDLE_TYPE_OPAQUE_FD_BIT;

public class BaseTest {
    public static void main(String[] args) {
        //System.loadLibrary("renderdoc");
        GLFWErrorCallback errCallback;
        Callback debugProc;
        glfwSetErrorCallback(errCallback = new GLFWErrorCallback() {
            GLFWErrorCallback delegate = GLFWErrorCallback.createPrint(System.err);

            @Override
            public void invoke(int error, long description) {
                if (error == GLFW_VERSION_UNAVAILABLE)
                    System.err.println("This demo requires OpenGL 2.0 or higher.");
                delegate.invoke(error, description);
            }

            @Override
            public void free() {
                delegate.free();
            }
        });
        glfwInit();

        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 4);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 5);
        glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE);
        long window = glfwCreateWindow(1024, 1024, "HELLO VK GL", 0, 0);
        glfwMakeContextCurrent(window);
        glfwSwapInterval(1);  // Enable vsync
        GLCapabilities capabilities = createCapabilities();
        debugProc = GLUtil.setupDebugMessageCallback();

        VContextBuilder builder = new VContextBuilder(true);
        builder.setVersion(1,1)
                .addInstanceExtensions(
                    VK_KHR_GET_PHYSICAL_DEVICE_PROPERTIES_2_EXTENSION_NAME,
                    VK_KHR_EXTERNAL_MEMORY_CAPABILITIES_EXTENSION_NAME,
                    VK_KHR_EXTERNAL_SEMAPHORE_CAPABILITIES_EXTENSION_NAME,
                    VK_KHR_EXTERNAL_FENCE_CAPABILITIES_EXTENSION_NAME)
                .addDeviceExtensions(
                        VK_KHR_EXTERNAL_MEMORY_FD_EXTENSION_NAME,
                        VK_KHR_EXTERNAL_SEMAPHORE_FD_EXTENSION_NAME,
                        VK_KHR_EXTERNAL_FENCE_FD_EXTENSION_NAME,
                        VK_KHR_GET_MEMORY_REQUIREMENTS_2_EXTENSION_NAME,
                        VK_KHR_EXTERNAL_MEMORY_EXTENSION_NAME,
                        VK_KHR_EXTERNAL_SEMAPHORE_EXTENSION_NAME,
                        VK_EXT_DESCRIPTOR_INDEXING_EXTENSION_NAME,
                        VK_KHR_BUFFER_DEVICE_ADDRESS_EXTENSION_NAME,
                        VK_KHR_SPIRV_1_4_EXTENSION_NAME,
                        VK_KHR_SHADER_FLOAT_CONTROLS_EXTENSION_NAME,
                        "VK_KHR_shader_non_semantic_info"
                        //VK_KHR_DEFERRED_HOST_OPERATIONS_EXTENSION_NAME
                );

        VVkContext context = builder.create();
        VVkDevice device = context.getDevice();

        VVkRenderPass renderPass = device.build(new RenderPassBuilder()
                .attachment(VK_FORMAT_B8G8R8A8_UNORM, VK_IMAGE_LAYOUT_PRESENT_SRC_KHR, VK_ATTACHMENT_LOAD_OP_LOAD)
                //.attachment(VK_FORMAT_D24_UNORM_S8_UINT, VK_IMAGE_LAYOUT_GENERAL)
                .subpass(VK_PIPELINE_BIND_POINT_GRAPHICS, -1,0));

        VVkDescriptorSetLayout descriptorSetLayout = device.build(new DescriptorSetLayoutBuilder()
        );

        VVkShader vertexShader = device.compileShader(
                """
                        #version 420 core
                        void main(void) {
                            //const array of positions for the triangle
                            const vec3 positions[3] = vec3[3](
                                vec3(1.f,1.f, 0.0f),
                                vec3(-1.f,1.f, 0.0f),
                                vec3(0.f,-1.f, 0.0f)
                            );
                            //output the position of each vertex
                            gl_Position = vec4(positions[gl_VertexIndex], 1.0f);
                        }
                        """, VK_SHADER_STAGE_VERTEX_BIT);
        VVkShader fragShader = device.compileShader("""
                #version 420 core
                                                                        
                layout(location=0) out vec4 color;
                                                                        
                void main(void) {
                int a =  0;
                while (a!=4||a!=48)
                a =a*492 + 111;
                  color = vec4(0.2, 0.4, a, 1.0);
                }
                """, VK_SHADER_STAGE_FRAGMENT_BIT);
        /*
        GraphicsPipelineBuilder gpb = new GraphicsPipelineBuilder()
                .set(renderPass)//Can alternatively pass in the builder instead and object would get auto built
                .add(descriptorSetLayout)

                //.addVertexInput(0, 64)
                //    .attribute(0, VK_FORMAT_R8G8B8A8_UNORM, 0)
                //    .attribute(1, VK_FORMAT_R8G8B8A8_UNORM, 32)
                //    .end()
                .add(fragShader).add(vertexShader)//Entry defaults to main
                //.addPushConstant(VK_SHADER_STAGE_VERTEX_BIT|VK_SHADER_STAGE_FRAGMENT_BIT, 0, 4*4*4)
                .addDynamicStates()
                .rasterization(false, false, VK_CULL_MODE_NONE)
                .inputAssembly(VK_PRIMITIVE_TOPOLOGY_TRIANGLE_LIST)
                .multisampling()
                .addViewport(0,0,800,800,0,1)
                .addScissor(0,0,800,800)
                .depthStencil();//TODO: this


        GraphicsPipelineBuilder.ColourBlendingBuilder cbb = gpb.colourBlending(1,0,0,1f);//TODO: make this nicer
        if (false) {
            cbb.attachment(VK_BLEND_FACTOR_ZERO, VK_BLEND_FACTOR_ZERO, VK_BLEND_OP_ADD, VK_BLEND_FACTOR_ZERO, VK_BLEND_FACTOR_ZERO, VK_BLEND_OP_ADD);
        } else {
            cbb.attachment();
        }
         */
        GraphicsPipelineBuilder gpb = new GraphicsPipelineBuilder()
                .set(renderPass)
                .inputAssembly(VK_PRIMITIVE_TOPOLOGY_TRIANGLE_LIST)
                .rasterization(false, false, VK_CULL_MODE_NONE)
                .colourBlending().attachment().end()
                .addViewport(0,0,800,800,0,1)
                .addScissor(0,0,800,800)
                .addDynamicStates()
                .multisampling()
                .add(device.compileShader("""
                #version 420 core
                                
                layout(location=0) in vec2 position;
                                
                void main(void) {
                  //gl_Position = vec4(position, 0.0, 1.0);
                                
                  //const array of positions for the triangle
                  const vec3 positions[3] = vec3[3](
                  vec3(1.f,1.f, 0.0f),
                  vec3(-1.f,1.f, 0.0f),
                  vec3(0.f,-1.f, 0.0f)
                  );
                  //output the position of each vertex
                  gl_Position = vec4(positions[gl_VertexIndex], 1.0f);
                }
                """, VK_SHADER_STAGE_VERTEX_BIT))
                .add(device.compileShader("""
                #version 420 core
                layout(location=0) out vec4 color;
                
                void main(void) {
                int a =  0;
                  color = vec4(0.2, 0.4, a, 1.0);
                }""", VK_SHADER_STAGE_FRAGMENT_BIT));
        VVkPipeline graphicsPipeline = device.build(gpb);

        //gpb.add(vertexShader, VK_SHADER_STAGE_VERTEX_BIT).add(fragShader, VK_SHADER_STAGE_FRAGMENT_BIT);//Entry defaults to main



       // VVkBuffer testBuffer = device.allocator.createBuffer(40, VK_BUFFER_USAGE_UNIFORM_BUFFER_BIT, VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT, 0);

        /*
        DescriptorUpdateBuilder dub = new DescriptorUpdateBuilder(descriptorSetLayout);//descriptorSetLayout is only needed to get the type, if that can be set in a different way, dont need it
        dub.buffer(0,0).update(testBuffer, 0, 40);//Constant update
        dub.buffer(0,0).update(testBuffer);//Constant update
        dub.image(1,0).update(sampler, view);
        dub.buffer(2,0).update((index, updater)-> {//Updater is of type VkDescriptorBufferInfo
            updater.buffer(inflightUniforms[index]);
        });
        dub.buffer(3,0).updateZipped(inflightUniforms);// where inflightUniforms is an array
        VBakedDescriptorWriter bdw = dub.bake();
        VVkDescriptorSetsPooled dsp = descriptorSetLayout.createDescriptorSets(numOfSets);
        dsp.update(bdw);
        //device.updateDescriptorSets(bdw, dsp);//Can also pass in unbaked version

         */



        /*
        VVkImage colour = device.allocator.create2DImage();

        VVkFramebuffer framebuffer = device.createFramebuffer(renderPass, images are required to match render pass description);


        //USE TEMPLATES TO MAKE THE pipeline render pass, cause the type would be the type supplied or something cool
        commandBuffer.beginRenderPass(framebuffer);//Also sets viewport + other options (could maybe make it configurable or something via a builder)
        commandBuffer.bind(graphicsPipeline);
        commandBuffer.bind(dsp, ...);
        commandBuffer.bindIndex(indexBuffer, VK_INDEX_TYPE_UINT32);
        commandBuffer.bindVertexs(vertexBuffers...);
        //commandBuffer.bindVertexsOffset;//do this somehow
        commandBuffer.endRenderPass();

        commandBuffer.bind(computePipeline);
        commandBuffer.bind(dsp,...);
        commandBuffer.dispatch(1,1,1);

        commandBuffer.end();

        VVkSemaphore signal;
        VVkSemaphore wait;
        queue.batchSubmit().buffer(commandBuffer).wait(wait, VK_PIPELINE_STAGE_RAY_TRACING_SHADER_BIT_KHR).signal(signal).submit();
        queue.submit(commandBuffer, wait, VK_PIPELINE_STAGE_RAY_TRACING_SHADER_BIT_KHR, signal, optionalFence);


        //vkCmdUpdateBuffer

         */
        //VVkImage gc2 = device.allocator.create2DImage(800,800, 1, VK_FORMAT_R8G8B8A8_UNORM, VK_IMAGE_USAGE_TRANSFER_DST_BIT, VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT, MemoryUtil.memAddress(dummyData));
        VGlVkImage gd = device.exportedAllocator.createShared2DImage(800,800, 1, VK_FORMAT_D24_UNORM_S8_UINT, GL_DEPTH24_STENCIL8, VK_IMAGE_USAGE_DEPTH_STENCIL_ATTACHMENT_BIT|VK_IMAGE_USAGE_SAMPLED_BIT|VK_IMAGE_USAGE_TRANSFER_SRC_BIT , VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT);

        VGlVkImage gc = device.exportedAllocator.createShared2DImage(800,800, 1, VK_FORMAT_B8G8R8A8_UNORM, GL_RGBA8, VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT|VK_IMAGE_USAGE_SAMPLED_BIT|VK_IMAGE_USAGE_TRANSFER_SRC_BIT|VK_IMAGE_USAGE_TRANSFER_DST_BIT, VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT);

        /*
        VVkBuffer tb = device.allocator.createBuffer(dummyData.capacity(), VK_BUFFER_USAGE_TRANSFER_SRC_BIT, VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT);
        PointerBuffer pba = MemoryUtil.memAllocPointer(1);
        _CHECK_(vmaMapMemory(tb.memory.allocator.allocator, tb.memory.allocation, pba));
        MemoryUtil.memCopy(MemoryUtil.memAddress(dummyData), pba.get(0), 800*800*4);
        vmaUnmapMemory(tb.memory.allocator.allocator, tb.memory.allocation);
         */
        VVkQueue queue = device.fetchQueue();
        VVkCommandPool commandPool = device.createCommandPool(0, VK_COMMAND_POOL_CREATE_TRANSIENT_BIT);
        VVkCommandBuffer otb = commandPool.createCommandBuffer();
        otb.begin(VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT);

        VVkFramebuffer framebuffer = device.createFramebuffer(renderPass, gc.createView(VK_IMAGE_ASPECT_COLOR_BIT));
        otb.beginRenderPass(framebuffer);//TODO: need to fis with clear values

        otb.bind(graphicsPipeline);

        vkCmdDraw(otb.buffer, 3, 1, 0, 0);

        otb.endRenderPass();
        otb.end();
        queue.submit(otb);
        /*
        try (MemoryStack stack = MemoryStack.stackPush()) {


            int glSemaphore = glGenSemaphoresEXT();
            VkExportSemaphoreCreateInfo esci = VkExportSemaphoreCreateInfo.calloc(stack)
                    .sType$Default()
                    .handleTypes(VK_EXTERNAL_SEMAPHORE_HANDLE_TYPE_OPAQUE_FD_BIT);
            VkSemaphoreCreateInfo sci = VkSemaphoreCreateInfo.calloc(stack)
                    .sType$Default()
                    .pNext(esci);
            long[] out = new long[1];
            vkCreateSemaphore(device.device, sci, null, out);
            long vkSemaphore = out[0];
            PointerBuffer pb = stack.callocPointer(1);
            VkSemaphoreGetFdHandleInfoKHR sgwhi = VkSemaphoreGetFdHandleInfoKHR.calloc(stack)
                    .sType$Default()
                    .semaphore(vkSemaphore)
                    .handleType(VK_EXTERNAL_SEMAPHORE_HANDLE_TYPE_OPAQUE_FD_BIT);
            vkGetSemaphoreFdHandleKHR(device.device, sgwhi, pb);
            glImportSemaphoreFdHandleEXT(glSemaphore, GL_HANDLE_TYPE_OPAQUE_FD_EXT, pb.get(0));

            LongBuffer pFence = stack.mallocLong(1);
            _CHECK_(vkCreateFence(device.device, VkFenceCreateInfo
                            .calloc(stack)
                            .sType$Default(), null, pFence));
            _CHECK_(vkQueueSubmit(queue.queue, VkSubmitInfo
                            .calloc(stack)
                            .sType$Default()
                            .pCommandBuffers(stack.pointers(commandBuffer.buffer))
                            .pSignalSemaphores(stack.longs(vkSemaphore))
                            .pWaitSemaphores(stack.longs(vkWait))
                            .pWaitDstStageMask(stack.ints(VK_PIPELINE_STAGE_ALL_GRAPHICS_BIT|VK_PIPELINE_STAGE_ALL_COMMANDS_BIT )), pFence.get(0)),
                    "Failed to submit command buffer");
            glSignalSemaphoreEXT(glSemaphore, new int[]{}, new int[]{gc.glId}, new int[]{GL_LAYOUT_GENERAL_EXT, GL_LAYOUT_COLOR_ATTACHMENT_EXT});
            while (vkGetFenceStatus(device.device, pFence.get(0)) != VK_SUCCESS);
            System.out.println("AAA");

        }*/
        glFinish();
        /*
        data = MemoryUtil.memAlloc(800*800*4);
        //Save image
        glGetTextureImage(gc.glId,0,GL_RGBA, GL_UNSIGNED_BYTE, data);
        glFinish();
        for (int i = 0 ; i < data.capacity();i++) {
            if (data.get(i)!=0) {
                System.out.println("AAAs");
            }
        }*/

       //BLIT TESTING
        int fbo = glCreateFramebuffers();


        glNamedFramebufferTexture(fbo, GL_COLOR_ATTACHMENT0, gc.glId, 0);
        glfwPollEvents();
        //glNamedFramebufferTexture(fbo, GL_DEPTH_STENCIL_ATTACHMENT, gd.glId, 0);
        glfwPollEvents();
        /*
        glBindFramebuffer(GL_FRAMEBUFFER, fbo);
        glClearColor(0,0,0.4f,1);
        glClear(GL_COLOR_BUFFER_BIT);
        glBindFramebuffer(GL_FRAMEBUFFER, 0);
        glFinish();
        */
        glFinish();


        while (!glfwWindowShouldClose(window)) {
            glfwPollEvents();
            {
                glBindFramebuffer(GL_READ_FRAMEBUFFER, fbo);
                glBindFramebuffer(GL_DRAW_FRAMEBUFFER, 0);
                glBlitFramebuffer(0, 0, 800, 800,
                        0, 0, 800, 800,
                        GL_COLOR_BUFFER_BIT,
                        GL_LINEAR);
                glBindFramebuffer(GL_READ_FRAMEBUFFER, 0);
            }
            glfwSwapBuffers(window);
            glFinish();
        }
    }

}
