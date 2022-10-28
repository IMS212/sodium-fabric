package me.cortex.testbed;

import static me.cortex.testbed.VKUtil.*;
import static org.lwjgl.opengl.ARBDirectStateAccess.glGetTextureImage;
import static org.lwjgl.opengl.GL.createCapabilities;
import static org.lwjgl.opengl.GL11.GL_RGBA;
import static org.lwjgl.opengl.GL11.GL_UNSIGNED_BYTE;
import static org.lwjgl.opengl.GL11.glFinish;
import static org.lwjgl.opengl.GL11C.*;
import static org.lwjgl.system.MemoryUtil.*;
import static org.lwjgl.vulkan.EXTDebugReport.*;
import static org.lwjgl.vulkan.EXTDescriptorIndexing.VK_EXT_DESCRIPTOR_INDEXING_EXTENSION_NAME;
import static org.lwjgl.vulkan.KHRBufferDeviceAddress.VK_KHR_BUFFER_DEVICE_ADDRESS_EXTENSION_NAME;
import static org.lwjgl.vulkan.KHRExternalFenceCapabilities.VK_KHR_EXTERNAL_FENCE_CAPABILITIES_EXTENSION_NAME;
import static org.lwjgl.vulkan.KHRExternalFenceFd.VK_KHR_EXTERNAL_FENCE_FD_EXTENSION_NAME;
import static org.lwjgl.vulkan.KHRExternalMemory.VK_KHR_EXTERNAL_MEMORY_EXTENSION_NAME;
import static org.lwjgl.vulkan.KHRExternalMemoryCapabilities.VK_KHR_EXTERNAL_MEMORY_CAPABILITIES_EXTENSION_NAME;
import static org.lwjgl.vulkan.KHRExternalMemoryFd.VK_KHR_EXTERNAL_MEMORY_FD_EXTENSION_NAME;
import static org.lwjgl.vulkan.KHRExternalSemaphore.VK_KHR_EXTERNAL_SEMAPHORE_EXTENSION_NAME;
import static org.lwjgl.vulkan.KHRExternalSemaphoreCapabilities.VK_KHR_EXTERNAL_SEMAPHORE_CAPABILITIES_EXTENSION_NAME;
import static org.lwjgl.vulkan.KHRExternalSemaphoreFd.VK_KHR_EXTERNAL_SEMAPHORE_FD_EXTENSION_NAME;
import static org.lwjgl.vulkan.KHRGetMemoryRequirements2.VK_KHR_GET_MEMORY_REQUIREMENTS_2_EXTENSION_NAME;
import static org.lwjgl.vulkan.KHRGetPhysicalDeviceProperties2.VK_KHR_GET_PHYSICAL_DEVICE_PROPERTIES_2_EXTENSION_NAME;
import static org.lwjgl.vulkan.KHRShaderFloatControls.VK_KHR_SHADER_FLOAT_CONTROLS_EXTENSION_NAME;
import static org.lwjgl.vulkan.KHRSpirv14.VK_KHR_SPIRV_1_4_EXTENSION_NAME;
import static org.lwjgl.vulkan.KHRSwapchain.*;
import static org.lwjgl.vulkan.KHRSurface.*;
import static org.lwjgl.vulkan.VK10.*;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.glfw.GLFWVulkan.*;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.LongBuffer;

import me.cortex.vulkanitelib.VContextBuilder;
import me.cortex.vulkanitelib.VVkContext;
import me.cortex.vulkanitelib.VVkDevice;
import me.cortex.vulkanitelib.memory.image.VGlVkImage;
import me.cortex.vulkanitelib.memory.image.VVkFramebuffer;
import me.cortex.vulkanitelib.other.VVkCommandBuffer;
import me.cortex.vulkanitelib.other.VVkCommandPool;
import me.cortex.vulkanitelib.other.VVkQueue;
import me.cortex.vulkanitelib.pipelines.VVkPipeline;
import me.cortex.vulkanitelib.pipelines.VVkRenderPass;
import me.cortex.vulkanitelib.pipelines.builders.GraphicsPipelineBuilder;
import me.cortex.vulkanitelib.pipelines.builders.RenderPassBuilder;
import me.cortex.vulkanitelib.utils.ShaderUtils;
import org.lwjgl.PointerBuffer;
import org.lwjgl.glfw.GLFWFramebufferSizeCallback;
import org.lwjgl.glfw.GLFWKeyCallback;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GLCapabilities;
import org.lwjgl.opengl.GLUtil;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.vulkan.*;

/**
 * Renders a simple triangle on a cornflower blue background on a GLFW window with Vulkan.
 *
 * @author Kai Burjack
 */
public class TriangleDemo {
    private static boolean debug = System.getProperty("NDEBUG") == null;

    private static String[] layers = {
            "VK_LAYER_LUNARG_standard_validation",
            "VK_LAYER_KHRONOS_validation",
    };

    /**
     * This is just -1L, but it is nicer as a symbolic constant.
     */
    private static final long UINT64_MAX = 0xFFFFFFFFFFFFFFFFL;

    private static long setupDebugging(VkInstance instance, int flags, VkDebugReportCallbackEXT callback) {
        VkDebugReportCallbackCreateInfoEXT dbgCreateInfo = VkDebugReportCallbackCreateInfoEXT.calloc()
                .sType$Default()
                .pfnCallback(callback)
                .flags(flags);
        LongBuffer pCallback = memAllocLong(1);
        int err = vkCreateDebugReportCallbackEXT(instance, dbgCreateInfo, null, pCallback);
        long callbackHandle = pCallback.get(0);
        memFree(pCallback);
        dbgCreateInfo.free();
        if (err != VK_SUCCESS) {
            throw new AssertionError("Failed to create VkInstance: " + translateVulkanResult(err));
        }
        return callbackHandle;
    }

    private static class ColorFormatAndSpace {
        int colorFormat;
        int colorSpace;
    }

    private static ColorFormatAndSpace getColorFormatAndSpace(VkPhysicalDevice physicalDevice, long surface) {
        IntBuffer pQueueFamilyPropertyCount = memAllocInt(1);
        vkGetPhysicalDeviceQueueFamilyProperties(physicalDevice, pQueueFamilyPropertyCount, null);
        int queueCount = pQueueFamilyPropertyCount.get(0);
        VkQueueFamilyProperties.Buffer queueProps = VkQueueFamilyProperties.calloc(queueCount);
        vkGetPhysicalDeviceQueueFamilyProperties(physicalDevice, pQueueFamilyPropertyCount, queueProps);
        memFree(pQueueFamilyPropertyCount);

        // Iterate over each queue to learn whether it supports presenting:
        IntBuffer supportsPresent = memAllocInt(queueCount);
        for (int i = 0; i < queueCount; i++) {
            supportsPresent.position(i);
            int err = vkGetPhysicalDeviceSurfaceSupportKHR(physicalDevice, i, surface, supportsPresent);
            if (err != VK_SUCCESS) {
                throw new AssertionError("Failed to physical device surface support: " + translateVulkanResult(err));
            }
        }

        // Search for a graphics and a present queue in the array of queue families, try to find one that supports both
        int graphicsQueueNodeIndex = Integer.MAX_VALUE;
        int presentQueueNodeIndex = Integer.MAX_VALUE;
        for (int i = 0; i < queueCount; i++) {
            if ((queueProps.get(i).queueFlags() & VK_QUEUE_GRAPHICS_BIT) != 0) {
                if (graphicsQueueNodeIndex == Integer.MAX_VALUE) {
                    graphicsQueueNodeIndex = i;
                }
                if (supportsPresent.get(i) == VK_TRUE) {
                    graphicsQueueNodeIndex = i;
                    presentQueueNodeIndex = i;
                    break;
                }
            }
        }
        queueProps.free();
        if (presentQueueNodeIndex == Integer.MAX_VALUE) {
            // If there's no queue that supports both present and graphics try to find a separate present queue
            for (int i = 0; i < queueCount; ++i) {
                if (supportsPresent.get(i) == VK_TRUE) {
                    presentQueueNodeIndex = i;
                    break;
                }
            }
        }
        memFree(supportsPresent);

        // Generate error if could not find both a graphics and a present queue
        if (graphicsQueueNodeIndex == Integer.MAX_VALUE) {
            throw new AssertionError("No graphics queue found");
        }
        if (presentQueueNodeIndex == Integer.MAX_VALUE) {
            throw new AssertionError("No presentation queue found");
        }
        if (graphicsQueueNodeIndex != presentQueueNodeIndex) {
            throw new AssertionError("Presentation queue != graphics queue");
        }

        // Get list of supported formats
        IntBuffer pFormatCount = memAllocInt(1);
        int err = vkGetPhysicalDeviceSurfaceFormatsKHR(physicalDevice, surface, pFormatCount, null);
        int formatCount = pFormatCount.get(0);
        if (err != VK_SUCCESS) {
            throw new AssertionError("Failed to query number of physical device surface formats: " + translateVulkanResult(err));
        }

        VkSurfaceFormatKHR.Buffer surfFormats = VkSurfaceFormatKHR.calloc(formatCount);
        err = vkGetPhysicalDeviceSurfaceFormatsKHR(physicalDevice, surface, pFormatCount, surfFormats);
        memFree(pFormatCount);
        if (err != VK_SUCCESS) {
            throw new AssertionError("Failed to query physical device surface formats: " + translateVulkanResult(err));
        }

        int colorFormat;
        if (formatCount == 1 && surfFormats.get(0).format() == VK_FORMAT_UNDEFINED) {
            colorFormat = VK_FORMAT_B8G8R8A8_UNORM;
        } else {
            colorFormat = surfFormats.get(0).format();
        }
        int colorSpace = surfFormats.get(0).colorSpace();
        surfFormats.free();

        ColorFormatAndSpace ret = new ColorFormatAndSpace();
        ret.colorFormat = colorFormat;
        ret.colorSpace = colorSpace;
        return ret;
    }

    private static long createCommandPool(VkDevice device, int queueNodeIndex) {
        VkCommandPoolCreateInfo cmdPoolInfo = VkCommandPoolCreateInfo.calloc()
                .sType$Default()
                .queueFamilyIndex(queueNodeIndex)
                .flags(VK_COMMAND_POOL_CREATE_RESET_COMMAND_BUFFER_BIT);
        LongBuffer pCmdPool = memAllocLong(1);
        int err = vkCreateCommandPool(device, cmdPoolInfo, null, pCmdPool);
        long commandPool = pCmdPool.get(0);
        cmdPoolInfo.free();
        memFree(pCmdPool);
        if (err != VK_SUCCESS) {
            throw new AssertionError("Failed to create command pool: " + translateVulkanResult(err));
        }
        return commandPool;
    }

    private static VkQueue createDeviceQueue(VkDevice device, int queueFamilyIndex) {
        PointerBuffer pQueue = memAllocPointer(1);
        vkGetDeviceQueue(device, queueFamilyIndex, 0, pQueue);
        long queue = pQueue.get(0);
        memFree(pQueue);
        return new VkQueue(queue, device);
    }

    private static VkCommandBuffer createCommandBuffer(VkDevice device, long commandPool) {
        VkCommandBufferAllocateInfo cmdBufAllocateInfo = VkCommandBufferAllocateInfo.calloc()
                .sType$Default()
                .commandPool(commandPool)
                .level(VK_COMMAND_BUFFER_LEVEL_PRIMARY)
                .commandBufferCount(1);
        PointerBuffer pCommandBuffer = memAllocPointer(1);
        int err = vkAllocateCommandBuffers(device, cmdBufAllocateInfo, pCommandBuffer);
        cmdBufAllocateInfo.free();
        long commandBuffer = pCommandBuffer.get(0);
        memFree(pCommandBuffer);
        if (err != VK_SUCCESS) {
            throw new AssertionError("Failed to allocate command buffer: " + translateVulkanResult(err));
        }
        return new VkCommandBuffer(commandBuffer, device);
    }

    private static class Swapchain {
        long swapchainHandle;
        long[] images;
        long[] imageViews;
    }
    private static VGlVkImage[] dodgyImgRef;
    private static Swapchain createSwapChain(VkDevice device, VkPhysicalDevice physicalDevice, long surface, long oldSwapChain, VkCommandBuffer commandBuffer, int newWidth,
                                             int newHeight, int colorFormat, int colorSpace) {
        int err;
        // Get physical device surface properties and formats
        VkSurfaceCapabilitiesKHR surfCaps = VkSurfaceCapabilitiesKHR.calloc();
        err = vkGetPhysicalDeviceSurfaceCapabilitiesKHR(physicalDevice, surface, surfCaps);
        if (err != VK_SUCCESS) {
            throw new AssertionError("Failed to get physical device surface capabilities: " + translateVulkanResult(err));
        }

        IntBuffer pPresentModeCount = memAllocInt(1);
        err = vkGetPhysicalDeviceSurfacePresentModesKHR(physicalDevice, surface, pPresentModeCount, null);
        int presentModeCount = pPresentModeCount.get(0);
        if (err != VK_SUCCESS) {
            throw new AssertionError("Failed to get number of physical device surface presentation modes: " + translateVulkanResult(err));
        }

        IntBuffer pPresentModes = memAllocInt(presentModeCount);
        err = vkGetPhysicalDeviceSurfacePresentModesKHR(physicalDevice, surface, pPresentModeCount, pPresentModes);
        memFree(pPresentModeCount);
        if (err != VK_SUCCESS) {
            throw new AssertionError("Failed to get physical device surface presentation modes: " + translateVulkanResult(err));
        }

        // Try to use mailbox mode. Low latency and non-tearing
        int swapchainPresentMode = VK_PRESENT_MODE_FIFO_KHR;
        for (int i = 0; i < presentModeCount; i++) {
            if (pPresentModes.get(i) == VK_PRESENT_MODE_MAILBOX_KHR) {
                swapchainPresentMode = VK_PRESENT_MODE_MAILBOX_KHR;
                break;
            }
            if ((swapchainPresentMode != VK_PRESENT_MODE_MAILBOX_KHR) && (pPresentModes.get(i) == VK_PRESENT_MODE_IMMEDIATE_KHR)) {
                swapchainPresentMode = VK_PRESENT_MODE_IMMEDIATE_KHR;
            }
        }
        memFree(pPresentModes);

        // Determine the number of images
        int desiredNumberOfSwapchainImages = surfCaps.minImageCount();
        if ((surfCaps.maxImageCount() > 0) && (desiredNumberOfSwapchainImages > surfCaps.maxImageCount())) {
            desiredNumberOfSwapchainImages = surfCaps.maxImageCount();
        }

        VkExtent2D currentExtent = surfCaps.currentExtent();
        int currentWidth = currentExtent.width();
        int currentHeight = currentExtent.height();
        if (currentWidth != -1 && currentHeight != -1) {
            width = currentWidth;
            height = currentHeight;
        } else {
            width = newWidth;
            height = newHeight;
        }

        int preTransform;
        if ((surfCaps.supportedTransforms() & VK_SURFACE_TRANSFORM_IDENTITY_BIT_KHR) != 0) {
            preTransform = VK_SURFACE_TRANSFORM_IDENTITY_BIT_KHR;
        } else {
            preTransform = surfCaps.currentTransform();
        }
        surfCaps.free();

        VkSwapchainCreateInfoKHR swapchainCI = VkSwapchainCreateInfoKHR.calloc()
                .sType$Default()
                .surface(surface)
                .minImageCount(desiredNumberOfSwapchainImages)
                .imageFormat(colorFormat)
                .imageColorSpace(colorSpace)
                .imageUsage(VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT)
                .preTransform(preTransform)
                .imageArrayLayers(1)
                .imageSharingMode(VK_SHARING_MODE_EXCLUSIVE)
                .presentMode(swapchainPresentMode)
                .oldSwapchain(oldSwapChain)
                .clipped(true)
                .compositeAlpha(VK_COMPOSITE_ALPHA_OPAQUE_BIT_KHR);
        swapchainCI.imageExtent()
                .width(width)
                .height(height);
        LongBuffer pSwapChain = memAllocLong(1);
        err = vkCreateSwapchainKHR(device, swapchainCI, null, pSwapChain);
        swapchainCI.free();
        long swapChain = pSwapChain.get(0);
        memFree(pSwapChain);
        if (err != VK_SUCCESS) {
            throw new AssertionError("Failed to create swap chain: " + translateVulkanResult(err));
        }

        // If we just re-created an existing swapchain, we should destroy the old swapchain at this point.
        // Note: destroying the swapchain also cleans up all its associated presentable images once the platform is done with them.
        if (oldSwapChain != VK_NULL_HANDLE) {
            vkDestroySwapchainKHR(device, oldSwapChain, null);
        }

        IntBuffer pImageCount = memAllocInt(1);
        err = vkGetSwapchainImagesKHR(device, swapChain, pImageCount, null);
        int imageCount = pImageCount.get(0);
        if (err != VK_SUCCESS) {
            throw new AssertionError("Failed to get number of swapchain images: " + translateVulkanResult(err));
        }

        LongBuffer pSwapchainImages = memAllocLong(imageCount);
        err = vkGetSwapchainImagesKHR(device, swapChain, pImageCount, pSwapchainImages);
        if (err != VK_SUCCESS) {
            throw new AssertionError("Failed to get swapchain images: " + translateVulkanResult(err));
        }
        memFree(pImageCount);

        long[] images = new long[imageCount];
        long[] imageViews = new long[imageCount];
        LongBuffer pBufferView = memAllocLong(1);
        VkImageViewCreateInfo colorAttachmentView = VkImageViewCreateInfo.calloc()
                .sType$Default()
                .format(colorFormat)
                .viewType(VK_IMAGE_VIEW_TYPE_2D);

        colorAttachmentView.subresourceRange()
                .aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
                .levelCount(1)
                .layerCount(1);
        /*
        for (int i = 0; i < imageCount; i++) {
            images[i] = pSwapchainImages.get(i);
            colorAttachmentView.image(images[i]);
            err = vkCreateImageView(device, colorAttachmentView, null, pBufferView);
            imageViews[i] = pBufferView.get(0);
            if (err != VK_SUCCESS) {
                throw new AssertionError("Failed to create image view: " + translateVulkanResult(err));
            }
        }*/

        dodgyImgRef = new VGlVkImage[imageCount];

        {
            VVkQueue queue = vd.fetchQueue();
            VVkCommandPool commandPool = vd.createCommandPool(0, VK_COMMAND_POOL_CREATE_TRANSIENT_BIT);
            VVkCommandBuffer otb = commandPool.createCommandBuffer();
            otb.begin(VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT);


            for (int i = 0; i < imageCount; i++) {
                VGlVkImage image = vd.exportedAllocator.createShared2DImage(width, height, 1, VK_FORMAT_B8G8R8A8_UNORM, GL_RGBA8, VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT|VK_IMAGE_USAGE_SAMPLED_BIT|VK_IMAGE_USAGE_TRANSFER_SRC_BIT|VK_IMAGE_USAGE_TRANSFER_DST_BIT, VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT);
                images[i] = image.image;
                imageViews[i] = image.createView(VK_IMAGE_ASPECT_COLOR_BIT).view;/*
                vkCmdPipelineBarrier(otb.buffer, VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT, VK_PIPELINE_STAGE_ALL_GRAPHICS_BIT,0,
                        null,null, VkImageMemoryBarrier
                                .calloc(1)
                                .sType$Default()
                                .srcAccessMask(0)
                                .dstAccessMask(VK_ACCESS_COLOR_ATTACHMENT_READ_BIT|VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT)
                                .oldLayout(VK_IMAGE_LAYOUT_UNDEFINED)
                                .newLayout(VK_IMAGE_LAYOUT_UNDEFINED)
                                .image(image.image)
                                .subresourceRange(r1 ->
                                        r1.aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
                                                .layerCount(1)
                                                .levelCount(1)));*/
                dodgyImgRef[i] = image;
            }
            otb.end();
            queue.submit(otb);

        }
        colorAttachmentView.free();
        memFree(pBufferView);
        memFree(pSwapchainImages);

        Swapchain ret = new Swapchain();
        ret.images = images;
        ret.imageViews = imageViews;
        ret.swapchainHandle = swapChain;
        return ret;
    }

    private static long[] createFramebuffers(VkDevice device, Swapchain swapchain, long renderPass, int width, int height) {
        LongBuffer attachments = memAllocLong(1);
        VkFramebufferCreateInfo fci = VkFramebufferCreateInfo.calloc()
                .sType$Default()
                .pAttachments(attachments)
                .height(height)
                .width(width)
                .layers(1)
                .renderPass(renderPass);
        // Create a framebuffer for each swapchain image
        long[] framebuffers = new long[swapchain.images.length];
        LongBuffer pFramebuffer = memAllocLong(1);
        for (int i = 0; i < swapchain.images.length; i++) {
            attachments.put(0, swapchain.imageViews[i]);
            int err = vkCreateFramebuffer(device, fci, null, pFramebuffer);
            long framebuffer = pFramebuffer.get(0);
            if (err != VK_SUCCESS) {
                throw new AssertionError("Failed to create framebuffer: " + translateVulkanResult(err));
            }
            framebuffers[i] = framebuffer;
        }
        memFree(attachments);
        memFree(pFramebuffer);
        fci.free();
        return framebuffers;
    }

    private static void submitCommandBuffer(VkQueue queue, VkCommandBuffer commandBuffer) {
        if (commandBuffer == null || commandBuffer.address() == NULL)
            return;
        VkSubmitInfo submitInfo = VkSubmitInfo.calloc()
                .sType$Default();
        PointerBuffer pCommandBuffers = memAllocPointer(1)
                .put(commandBuffer)
                .flip();
        submitInfo.pCommandBuffers(pCommandBuffers);
        int err = vkQueueSubmit(queue, submitInfo, VK_NULL_HANDLE);
        memFree(pCommandBuffers);
        submitInfo.free();
        if (err != VK_SUCCESS) {
            throw new AssertionError("Failed to submit command buffer: " + translateVulkanResult(err));
        }
    }

    private static boolean getMemoryType(VkPhysicalDeviceMemoryProperties deviceMemoryProperties, int typeBits, int properties, IntBuffer typeIndex) {
        int bits = typeBits;
        for (int i = 0; i < 32; i++) {
            if ((bits & 1) == 1) {
                if ((deviceMemoryProperties.memoryTypes(i).propertyFlags() & properties) == properties) {
                    typeIndex.put(0, i);
                    return true;
                }
            }
            bits >>= 1;
        }
        return false;
    }

    private static class Vertices {
        long verticesBuf;
        VkPipelineVertexInputStateCreateInfo createInfo;
    }

    private static Vertices createVertices(VkPhysicalDeviceMemoryProperties deviceMemoryProperties, VkDevice device) {
        ByteBuffer vertexBuffer = memAlloc(3 * 2 * 4);
        FloatBuffer fb = vertexBuffer.asFloatBuffer();
        // The triangle will showup upside-down, because Vulkan does not do proper viewport transformation to
        // account for inverted Y axis between the window coordinate system and clip space/NDC
        fb.put(-0.5f).put(-0.5f);
        fb.put( 0.5f).put(-0.5f);
        fb.put( 0.0f).put( 0.5f);

        VkMemoryAllocateInfo memAlloc = VkMemoryAllocateInfo.calloc()
                .sType$Default();
        VkMemoryRequirements memReqs = VkMemoryRequirements.calloc();

        int err;

        // Generate vertex buffer
        //  Setup
        VkBufferCreateInfo bufInfo = VkBufferCreateInfo.calloc()
                .sType$Default()
                .size(vertexBuffer.remaining())
                .usage(VK_BUFFER_USAGE_VERTEX_BUFFER_BIT);
        LongBuffer pBuffer = memAllocLong(1);
        err = vkCreateBuffer(device, bufInfo, null, pBuffer);
        long verticesBuf = pBuffer.get(0);
        memFree(pBuffer);
        bufInfo.free();
        if (err != VK_SUCCESS) {
            throw new AssertionError("Failed to create vertex buffer: " + translateVulkanResult(err));
        }

        vkGetBufferMemoryRequirements(device, verticesBuf, memReqs);
        memAlloc.allocationSize(memReqs.size());
        IntBuffer memoryTypeIndex = memAllocInt(1);
        getMemoryType(deviceMemoryProperties, memReqs.memoryTypeBits(), VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT, memoryTypeIndex);
        memAlloc.memoryTypeIndex(memoryTypeIndex.get(0));
        memFree(memoryTypeIndex);
        memReqs.free();

        LongBuffer pMemory = memAllocLong(1);
        err = vkAllocateMemory(device, memAlloc, null, pMemory);
        long verticesMem = pMemory.get(0);
        memFree(pMemory);
        if (err != VK_SUCCESS) {
            throw new AssertionError("Failed to allocate vertex memory: " + translateVulkanResult(err));
        }

        PointerBuffer pData = memAllocPointer(1);
        err = vkMapMemory(device, verticesMem, 0, memAlloc.allocationSize(), 0, pData);
        memAlloc.free();
        long data = pData.get(0);
        memFree(pData);
        if (err != VK_SUCCESS) {
            throw new AssertionError("Failed to map vertex memory: " + translateVulkanResult(err));
        }

        MemoryUtil.memCopy(memAddress(vertexBuffer), data, vertexBuffer.remaining());
        memFree(vertexBuffer);
        vkUnmapMemory(device, verticesMem);
        err = vkBindBufferMemory(device, verticesBuf, verticesMem, 0);
        if (err != VK_SUCCESS) {
            throw new AssertionError("Failed to bind memory to vertex buffer: " + translateVulkanResult(err));
        }

        // Binding description
        VkVertexInputBindingDescription.Buffer bindingDescriptor = VkVertexInputBindingDescription.calloc(1)
                .binding(0) // <- we bind our vertex buffer to point 0
                .stride(2 * 4)
                .inputRate(VK_VERTEX_INPUT_RATE_VERTEX);

        // Attribute descriptions
        // Describes memory layout and shader attribute locations
        VkVertexInputAttributeDescription.Buffer attributeDescriptions = VkVertexInputAttributeDescription.calloc(1);
        // Location 0 : Position
        attributeDescriptions.get(0)
                .binding(0) // <- binding point used in the VkVertexInputBindingDescription
                .location(0) // <- location in the shader's attribute layout (inside the shader source)
                .format(VK_FORMAT_R32G32_SFLOAT)
                .offset(0);

        // Assign to vertex buffer
        VkPipelineVertexInputStateCreateInfo vi = VkPipelineVertexInputStateCreateInfo.calloc()
                .sType$Default()
                .pVertexBindingDescriptions(bindingDescriptor)
                .pVertexAttributeDescriptions(attributeDescriptions);

        Vertices ret = new Vertices();
        ret.createInfo = vi;
        ret.verticesBuf = verticesBuf;
        return ret;
    }

    private static VVkPipeline createPipeline(VkDevice device, VVkRenderPass rp) throws IOException {
        GraphicsPipelineBuilder gpb = new GraphicsPipelineBuilder()
                .set(rp)
                .inputAssembly(VK_PRIMITIVE_TOPOLOGY_TRIANGLE_LIST)
                .rasterization(false, false, VK_CULL_MODE_NONE)
                .colourBlending().attachment().end()
                .addViewport()
                .addScissor()
                .addDynamicStates(VK_DYNAMIC_STATE_VIEWPORT, VK_DYNAMIC_STATE_SCISSOR)
                .multisampling()
                .add(vd.compileShader("""
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
                .add(vd.compileShader("""
                #version 420 core
                layout(location=0) out vec4 color;
                
                void main(void) {
                int a =  0;
                  color = vec4(0, 0, 0.5, 0.0);
                }""", VK_SHADER_STAGE_FRAGMENT_BIT));
        return vd.build(gpb);
    }

    private static VkCommandBuffer[] createRenderCommandBuffers(VkDevice device, long commandPool, VVkRenderPass drp, long[] framebuffers, long renderPass, int width, int height,
                                                                long pipeline, long verticesBuf) {
        // Create the render command buffers (one command buffer per framebuffer image)
        VkCommandBufferAllocateInfo cmdBufAllocateInfo = VkCommandBufferAllocateInfo.calloc()
                .sType$Default()
                .commandPool(commandPool)
                .level(VK_COMMAND_BUFFER_LEVEL_PRIMARY)
                .commandBufferCount(framebuffers.length);
        PointerBuffer pCommandBuffer = memAllocPointer(framebuffers.length);
        int err = vkAllocateCommandBuffers(device, cmdBufAllocateInfo, pCommandBuffer);
        if (err != VK_SUCCESS) {
            throw new AssertionError("Failed to allocate render command buffer: " + translateVulkanResult(err));
        }
        VkCommandBuffer[] renderCommandBuffers = new VkCommandBuffer[framebuffers.length];
        for (int i = 0; i < framebuffers.length; i++) {
            renderCommandBuffers[i] = new VkCommandBuffer(pCommandBuffer.get(i), device);
        }
        memFree(pCommandBuffer);
        cmdBufAllocateInfo.free();

        // Create the command buffer begin structure
        VkCommandBufferBeginInfo cmdBufInfo = VkCommandBufferBeginInfo.calloc()
                .flags(VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT)
                .sType$Default();

        // Specify clear color (cornflower blue)
        VkClearValue.Buffer clearValues = VkClearValue.calloc(1);
        clearValues.color()
                .float32(0, 0/255.0f)
                .float32(1, 0/255.0f)
                .float32(2, 0/255.0f)
                .float32(3, 1.0f);

        // Specify everything to begin a render pass
        VkRenderPassBeginInfo renderPassBeginInfo = VkRenderPassBeginInfo.calloc()
                .sType$Default()
                .renderPass(renderPass)
                .pClearValues(clearValues);
        VkRect2D renderArea = renderPassBeginInfo.renderArea();
        renderArea.offset().set(0, 0);
        renderArea.extent().set(width, height);

        for (int i = 0; i < 1; ++i) {
            // Set target frame buffer
            VVkFramebuffer fb = vd.createFramebuffer(drp, dodgyImgRef[0].createView(VK_IMAGE_ASPECT_COLOR_BIT));
            renderPassBeginInfo.framebuffer(fb.framebuffer);

            VVkCommandBuffer otb = new VVkCommandBuffer(drp.device, null, renderCommandBuffers[0]);
            err = vkBeginCommandBuffer(renderCommandBuffers[i], cmdBufInfo);
            if (err != VK_SUCCESS) {
                throw new AssertionError("Failed to begin render command buffer: " + translateVulkanResult(err));
            }

            //vkCmdBeginRenderPass(renderCommandBuffers[i], renderPassBeginInfo, VK_SUBPASS_CONTENTS_INLINE);

            try (MemoryStack stack = MemoryStack.stackPush()) {
                VkRenderPassBeginInfo beginInfo = VkRenderPassBeginInfo.calloc(stack)
                        .sType$Default()
                        .framebuffer(fb.framebuffer)
                        .renderPass(fb.renderPass.renderpass)
                        .pClearValues(clearValues);
                vkCmdBeginRenderPass(otb.buffer, beginInfo, VK_SUBPASS_CONTENTS_INLINE);//TODO: move VK_SUBPASS_CONTENTS_INLINE somewhere else
            }
            /*
            // Update dynamic viewport state
            VkViewport.Buffer viewport = VkViewport.calloc(1)
                    .height(height)
                    .width(width)
                    .minDepth(0.0f)
                    .maxDepth(1.0f);
            vkCmdSetViewport(renderCommandBuffers[i], 0, viewport);
            viewport.free();

            // Update dynamic scissor state
            VkRect2D.Buffer scissor = VkRect2D.calloc(1);
            scissor.extent().set(width, height);
            scissor.offset().set(0, 0);
            vkCmdSetScissor(renderCommandBuffers[i], 0, scissor);
            scissor.free();
            */
            // Bind the rendering pipeline (including the shaders)
            // vkCmdBindPipeline(renderCommandBuffers[i], VK_PIPELINE_BIND_POINT_GRAPHICS, pipeline);

            // Bind triangle vertices
            LongBuffer offsets = memAllocLong(1);
            offsets.put(0, 0L);
            LongBuffer pBuffers = memAllocLong(1);
            pBuffers.put(0, verticesBuf);
            //vkCmdBindVertexBuffers(renderCommandBuffers[i], 0, pBuffers, offsets);
            memFree(pBuffers);
            memFree(offsets);

            // Draw triangle
            //vkCmdDraw(renderCommandBuffers[i], 3, 1, 0, 0);

            vkCmdEndRenderPass(renderCommandBuffers[i]);

            err = vkEndCommandBuffer(renderCommandBuffers[i]);
            if (err != VK_SUCCESS) {
                throw new AssertionError("Failed to begin render command buffer: " + translateVulkanResult(err));
            }
        }

        renderPassBeginInfo.free();
        clearValues.free();
        cmdBufInfo.free();
        return renderCommandBuffers;
    }


    private static long[] mbf(VVkRenderPass drp) {
        return new long[]{vd.createFramebuffer(drp, dodgyImgRef[0].createView(VK_IMAGE_ASPECT_COLOR_BIT)).framebuffer};
    }

    /*
     * All resources that must be reallocated on window resize.
     */
    private static Swapchain swapchain;
    private static long[] framebuffers;
    private static int width, height;
    private static VkCommandBuffer[] renderCommandBuffers;
    public static VVkDevice vd;
    public static void main(String[] args) throws IOException {
        if (!glfwInit()) {
            throw new RuntimeException("Failed to initialize GLFW");
        }
        if (!glfwVulkanSupported()) {
            throw new AssertionError("GLFW failed to find the Vulkan loader");
        }

        VContextBuilder builder = new VContextBuilder(true);
        builder.setVersion(1,1)
                .addInstanceExtensions(
                        VK_KHR_GET_PHYSICAL_DEVICE_PROPERTIES_2_EXTENSION_NAME,
                        VK_KHR_EXTERNAL_MEMORY_CAPABILITIES_EXTENSION_NAME,
                        VK_KHR_EXTERNAL_SEMAPHORE_CAPABILITIES_EXTENSION_NAME,
                        VK_KHR_EXTERNAL_FENCE_CAPABILITIES_EXTENSION_NAME,
                        "VK_KHR_win32_surface",
                        "VK_KHR_surface",
                        VK_EXT_DEBUG_REPORT_EXTENSION_NAME)
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
                        "VK_KHR_shader_non_semantic_info",
                        VK_KHR_SWAPCHAIN_EXTENSION_NAME
                        //VK_KHR_DEFERRED_HOST_OPERATIONS_EXTENSION_NAME
                );

        VVkContext context = builder.create();
        vd = context.getDevice();

        // Create the Vulkan instance
        final VkInstance instance = context.getInstance();
        /*
        final VkDebugReportCallbackEXT debugCallback = new VkDebugReportCallbackEXT() {
            public int invoke(int flags, int objectType, long object, long location, int messageCode, long pLayerPrefix, long pMessage, long pUserData) {
                System.err.println("ERROR OCCURED: " + VkDebugReportCallbackEXT.getString(pMessage));
                return 0;
            }
        };
        final long debugCallbackHandle = setupDebugging(instance, VK_DEBUG_REPORT_ERROR_BIT_EXT | VK_DEBUG_REPORT_WARNING_BIT_EXT, debugCallback);
         */
        final VkPhysicalDevice physicalDevice = context.getPhysicalDevice();

        VkPhysicalDeviceMemoryProperties memoryProperties = VkPhysicalDeviceMemoryProperties.calloc();
        vkGetPhysicalDeviceMemoryProperties(physicalDevice, memoryProperties);

        final VkDevice device = vd.device;
        int queueFamilyIndex = 0;

        // Create GLFW window
        glfwDefaultWindowHints();
        glfwWindowHint(GLFW_CLIENT_API, GLFW_OPENGL_API);
        glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE);

        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 4);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 5);
        glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE);

        long windowGL = glfwCreateWindow(800, 600, "GLFW Vulkan Demo", NULL, NULL);
        glfwMakeContextCurrent(windowGL);
        GLCapabilities capabilities = createCapabilities();
        GLUtil.setupDebugMessageCallback();
        glfwWindowHint(GLFW_CLIENT_API, GLFW_NO_API);
        long window = glfwCreateWindow(800, 600, "GLFW Vulkan Demo", NULL, NULL);

        GLFWKeyCallback keyCallback;
        glfwSetKeyCallback(window, keyCallback = new GLFWKeyCallback() {
            public void invoke(long window, int key, int scancode, int action, int mods) {
                if (action != GLFW_RELEASE)
                    return;
                if (key == GLFW_KEY_ESCAPE)
                    glfwSetWindowShouldClose(window, true);
            }
        });
        LongBuffer pSurface = memAllocLong(1);
        int err = glfwCreateWindowSurface(instance, window, null, pSurface);
        final long surface = pSurface.get(0);
        if (err != VK_SUCCESS) {
            throw new AssertionError("Failed to create surface: " + translateVulkanResult(err));
        }


        // Create static Vulkan resources
        final ColorFormatAndSpace colorFormatAndSpace = getColorFormatAndSpace(physicalDevice, surface);
        final long commandPool = createCommandPool(device, queueFamilyIndex);
        final VkCommandBuffer setupCommandBuffer = createCommandBuffer(device, commandPool);
        final VkQueue queue = createDeviceQueue(device, queueFamilyIndex);
        final VVkRenderPass drp = vd.build(new RenderPassBuilder().attachment(colorFormatAndSpace.colorFormat, VK_IMAGE_LAYOUT_PRESENT_SRC_KHR).subpass(VK_PIPELINE_BIND_POINT_GRAPHICS, -1, 0));
        final long renderPass = drp.renderpass;
        final long renderCommandPool = createCommandPool(device, queueFamilyIndex);
        final Vertices vertices = createVertices(memoryProperties, device);
        final long pipeline = createPipeline(device, drp).pipeline;


        final class SwapchainRecreator {
            boolean mustRecreate = true;
            void recreate() {
                // Begin the setup command buffer (the one we will use for swapchain/framebuffer creation)
                VkCommandBufferBeginInfo cmdBufInfo = VkCommandBufferBeginInfo.calloc()
                        .sType$Default();
                int err = vkBeginCommandBuffer(setupCommandBuffer, cmdBufInfo);
                cmdBufInfo.free();
                if (err != VK_SUCCESS) {
                    throw new AssertionError("Failed to begin setup command buffer: " + translateVulkanResult(err));
                }
                long oldChain = swapchain != null ? swapchain.swapchainHandle : VK_NULL_HANDLE;
                // Create the swapchain (this will also add a memory barrier to initialize the framebuffer images)
                swapchain = createSwapChain(device, physicalDevice, surface, oldChain, setupCommandBuffer,
                        width, height, colorFormatAndSpace.colorFormat, colorFormatAndSpace.colorSpace);
                err = vkEndCommandBuffer(setupCommandBuffer);
                if (err != VK_SUCCESS) {
                    throw new AssertionError("Failed to end setup command buffer: " + translateVulkanResult(err));
                }
                submitCommandBuffer(queue, setupCommandBuffer);
                vkQueueWaitIdle(queue);

                if (framebuffers != null) {
                    for (int i = 0; i < framebuffers.length; i++)
                        vkDestroyFramebuffer(device, framebuffers[i], null);
                }
                //framebuffers = createFramebuffers(device, swapchain, renderPass, width, height);
                framebuffers = mbf(drp);
                // Create render command buffers
                if (renderCommandBuffers != null) {
                    vkResetCommandPool(device, renderCommandPool, VK_FLAGS_NONE);
                }
                renderCommandBuffers = createRenderCommandBuffers(device, renderCommandPool, drp, framebuffers, renderPass, width, height, pipeline,
                        vertices.verticesBuf);

                mustRecreate = false;
            }
        }
        final SwapchainRecreator swapchainRecreator = new SwapchainRecreator();

        // Handle canvas resize
        GLFWFramebufferSizeCallback framebufferSizeCallback = new GLFWFramebufferSizeCallback() {
            public void invoke(long window, int width, int height) {
                if (width <= 0 || height <= 0)
                    return;
                TriangleDemo.width = width;
                TriangleDemo.height = height;
                swapchainRecreator.mustRecreate = true;
            }
        };
        glfwSetFramebufferSizeCallback(window, framebufferSizeCallback);
        glfwShowWindow(window);

        // Pre-allocate everything needed in the render loop

        IntBuffer pImageIndex = memAllocInt(1);
        int currentBuffer = 0;
        PointerBuffer pCommandBuffers = memAllocPointer(1);
        LongBuffer pSwapchains = memAllocLong(1);
        LongBuffer pImageAcquiredSemaphore = memAllocLong(1);
        LongBuffer pRenderCompleteSemaphore = memAllocLong(1);

        // Info struct to create a semaphore
        VkSemaphoreCreateInfo semaphoreCreateInfo = VkSemaphoreCreateInfo.calloc()
                .sType$Default();

        // Info struct to submit a command buffer which will wait on the semaphore
        IntBuffer pWaitDstStageMask = memAllocInt(1);
        pWaitDstStageMask.put(0, VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT);
        VkSubmitInfo submitInfo = VkSubmitInfo.calloc()
                .sType$Default()
                //.waitSemaphoreCount(pImageAcquiredSemaphore.remaining())
                //.pWaitSemaphores(pImageAcquiredSemaphore)
                //.pWaitDstStageMask(pWaitDstStageMask)
                .pCommandBuffers(pCommandBuffers)
                //.pSignalSemaphores(pRenderCompleteSemaphore)
                ;

        // Info struct to present the current swapchain image to the display
        VkPresentInfoKHR presentInfo = VkPresentInfoKHR.calloc()
                .sType$Default()
                .pWaitSemaphores(pRenderCompleteSemaphore)
                .swapchainCount(pSwapchains.remaining())
                .pSwapchains(pSwapchains)
                .pImageIndices(pImageIndex);

        // The render loop
        while (!glfwWindowShouldClose(window)) {
            // Handle window messages. Resize events happen exactly here.
            // So it is safe to use the new swapchain images and framebuffers afterwards.
            glfwPollEvents();
            if (swapchainRecreator.mustRecreate)
                swapchainRecreator.recreate();

            // Create a semaphore to wait for the swapchain to acquire the next image
            err = vkCreateSemaphore(device, semaphoreCreateInfo, null, pImageAcquiredSemaphore);
            if (err != VK_SUCCESS) {
                throw new AssertionError("Failed to create image acquired semaphore: " + translateVulkanResult(err));
            }

            // Create a semaphore to wait for the render to complete, before presenting
            err = vkCreateSemaphore(device, semaphoreCreateInfo, null, pRenderCompleteSemaphore);
            if (err != VK_SUCCESS) {
                throw new AssertionError("Failed to create render complete semaphore: " + translateVulkanResult(err));
            }
            {

                {
                    VVkQueue queue2 = vd.fetchQueue();
                    VVkCommandPool commandPool2 = vd.createCommandPool(0, VK_COMMAND_POOL_CREATE_RESET_COMMAND_BUFFER_BIT);
                    VVkCommandBuffer otb = commandPool2.createCommandBuffer();
                    otb.begin(VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT);
                    VVkFramebuffer framebuffer = vd.createFramebuffer(drp, dodgyImgRef[0].createView(VK_IMAGE_ASPECT_COLOR_BIT));
                    otb.beginRenderPass(framebuffer);//TODO: need to fis with clear values
                    otb.endRenderPass();
                    otb.end();
                    queue2.submit(otb);
                    ByteBuffer data = MemoryUtil.memAlloc(800*800*4);
                    //Save image
                    glGetTextureImage( dodgyImgRef[0].glId,0,GL_RGBA, GL_UNSIGNED_BYTE, data);
                    glFinish();
                    for (int i = 0 ; i < data.capacity();i++) {
                        if (data.get(i)!=0) {
                            System.out.println("AAAs");
                        }
                    }
                }
                ByteBuffer data = MemoryUtil.memAlloc(800*800*4);
                //Save image
                glGetTextureImage(dodgyImgRef[0].glId,0,GL_RGBA, GL_UNSIGNED_BYTE, data);
                glFinish();
                for (int i = 0 ; i < data.capacity();i++) {
                    if (data.get(i)!=0) {
                        System.out.println("aa"+data.get(i));
                    }
                }
                // Select the command buffer for the current framebuffer image/attachment
                pCommandBuffers.put(0, renderCommandBuffers[0]);

                // Submit to the graphics queue
                err = vkQueueSubmit(queue, submitInfo, VK_NULL_HANDLE);
                if (err != VK_SUCCESS) {
                    throw new AssertionError("Failed to submit render queue: " + translateVulkanResult(err));
                }

                // Create and submit post present barrier
                vkQueueWaitIdle(queue);


                // Destroy this semaphore (we will create a new one in the next frame)
                vkDestroySemaphore(device, pImageAcquiredSemaphore.get(0), null);
                vkDestroySemaphore(device, pRenderCompleteSemaphore.get(0), null);
                data = MemoryUtil.memAlloc(800*800*4);
                //Save image
                glGetTextureImage(dodgyImgRef[currentBuffer].glId,0,GL_RGBA, GL_UNSIGNED_BYTE, data);
                glFinish();
                for (int i = 0 ; i < data.capacity();i++) {
                    if (data.get(i)!=0) {
                        System.out.println(data.get(i));
                        break;
                    }
                }
                System.exit(0);
                if (true)
                    continue;

            }

            // Get next image from the swap chain (back/front buffer).
            // This will setup the imageAquiredSemaphore to be signalled when the operation is complete
            err = vkAcquireNextImageKHR(device, swapchain.swapchainHandle, UINT64_MAX, pImageAcquiredSemaphore.get(0), VK_NULL_HANDLE, pImageIndex);
            currentBuffer = pImageIndex.get(0);
            if (err != VK_SUCCESS) {
                throw new AssertionError("Failed to acquire next swapchain image: " + translateVulkanResult(err));
            }

            // Select the command buffer for the current framebuffer image/attachment
            pCommandBuffers.put(0, renderCommandBuffers[currentBuffer]);

            // Submit to the graphics queue
            err = vkQueueSubmit(queue, submitInfo, VK_NULL_HANDLE);
            if (err != VK_SUCCESS) {
                throw new AssertionError("Failed to submit render queue: " + translateVulkanResult(err));
            }

            // Present the current buffer to the swap chain
            // This will display the image
            pSwapchains.put(0, swapchain.swapchainHandle);
            err = vkQueuePresentKHR(queue, presentInfo);
            if (err != VK_SUCCESS) {
                throw new AssertionError("Failed to present the swapchain image: " + translateVulkanResult(err));
            }
            // Create and submit post present barrier
            vkQueueWaitIdle(queue);

            // Destroy this semaphore (we will create a new one in the next frame)
            vkDestroySemaphore(device, pImageAcquiredSemaphore.get(0), null);
            vkDestroySemaphore(device, pRenderCompleteSemaphore.get(0), null);
        }
        presentInfo.free();
        memFree(pWaitDstStageMask);
        submitInfo.free();
        memFree(pImageAcquiredSemaphore);
        memFree(pRenderCompleteSemaphore);
        semaphoreCreateInfo.free();
        memFree(pSwapchains);
        memFree(pCommandBuffers);

        //vkDestroyDebugReportCallbackEXT(instance, debugCallbackHandle, null);

        framebufferSizeCallback.free();
        keyCallback.free();
        glfwDestroyWindow(window);
        glfwTerminate();

        // We don't bother disposing of all Vulkan resources.
        // Let the OS process manager take care of it.
    }

}

