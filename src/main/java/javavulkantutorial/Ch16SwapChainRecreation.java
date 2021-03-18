package javavulkantutorial;

import javavulkantutorial.ShaderSPIRVUtils.SPIRV;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.Pointer;
import org.lwjgl.vulkan.*;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.LongBuffer;
import java.util.*;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toSet;
import static javavulkantutorial.ShaderSPIRVUtils.ShaderKind.FRAGMENT_SHADER;
import static javavulkantutorial.ShaderSPIRVUtils.ShaderKind.VERTEX_SHADER;
import static javavulkantutorial.ShaderSPIRVUtils.compileShaderFile;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.glfw.GLFWVulkan.glfwCreateWindowSurface;
import static org.lwjgl.glfw.GLFWVulkan.glfwGetRequiredInstanceExtensions;
import static org.lwjgl.system.Configuration.DEBUG;
import static org.lwjgl.system.MemoryStack.stackGet;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.system.MemoryUtil.NULL;
import static org.lwjgl.vulkan.EXTDebugUtils.*;
import static org.lwjgl.vulkan.KHRSurface.*;
import static org.lwjgl.vulkan.KHRSwapchain.*;
import static org.lwjgl.vulkan.VK10.*;

public class Ch16SwapChainRecreation {

    private static class HelloTriangleApplication {

        private static final int UINT32_MAX = 0xFFFFFFFF;
        private static final long UINT64_MAX = 0xFFFFFFFFFFFFFFFFL;

        private static final int WIDTH = 800;
        private static final int HEIGHT = 600;

        private static final int MAX_FRAMES_IN_FLIGHT = 2;

        private static final boolean ENABLE_VALIDATION_LAYERS = DEBUG.get(true);

        private static final Set<String> VALIDATION_LAYERS;
        static {
            if(ENABLE_VALIDATION_LAYERS) {
                VALIDATION_LAYERS = new HashSet<>();
                VALIDATION_LAYERS.add("VK_LAYER_KHRONOS_validation");
            } else {
                // We are not going to use it, so we don't create it
                VALIDATION_LAYERS = null;
            }
        }

        private static final Set<String> DEVICE_EXTENSIONS = Stream.of(VK_KHR_SWAPCHAIN_EXTENSION_NAME)
                .collect(toSet());



        private static int debugCallback(int messageSeverity, int messageType, long pCallbackData, long pUserData) {

            VkDebugUtilsMessengerCallbackDataEXT callbackData = VkDebugUtilsMessengerCallbackDataEXT.create(pCallbackData);

            System.err.println("Validation layer: " + callbackData.pMessageString());

            return VK_FALSE;
        }

        private static int createDebugUtilsMessengerEXT(VkInstance instance, VkDebugUtilsMessengerCreateInfoEXT createInfo,
                                                        VkAllocationCallbacks allocationCallbacks, LongBuffer pDebugMessenger) {

            if(vkGetInstanceProcAddr(instance, "vkCreateDebugUtilsMessengerEXT") != NULL) {
                return vkCreateDebugUtilsMessengerEXT(instance, createInfo, allocationCallbacks, pDebugMessenger);
            }

            return VK_ERROR_EXTENSION_NOT_PRESENT;
        }

        private static void destroyDebugUtilsMessengerEXT(VkInstance instance, long debugMessenger, VkAllocationCallbacks allocationCallbacks) {

            if(vkGetInstanceProcAddr(instance, "vkDestroyDebugUtilsMessengerEXT") != NULL) {
                vkDestroyDebugUtilsMessengerEXT(instance, debugMessenger, allocationCallbacks);
            }

        }

        private class QueueFamilyIndices {

            // We use Integer to use null as the empty value
            private Integer graphicsFamily;
            private Integer presentFamily;

            private boolean isComplete() {
                return graphicsFamily != null && presentFamily != null;
            }

            public int[] unique() {
                return IntStream.of(graphicsFamily, presentFamily).distinct().toArray();
            }

            public int[] array() {
                return new int[] {graphicsFamily, presentFamily};
            }
        }

        private class SwapChainSupportDetails {

            private VkSurfaceCapabilitiesKHR capabilities;
            private VkSurfaceFormatKHR.Buffer formats;
            private IntBuffer presentModes;

        }

        // ======= FIELDS ======= //

        private long window;

        private VkInstance instance;
        private long debugMessenger;
        private long surface;

        private VkPhysicalDevice physicalDevice;
        private VkDevice device;

        private VkQueue graphicsQueue;
        private VkQueue presentQueue;

        private long swapChain;
        private List<Long> swapChainImages;
        private int swapChainImageFormat;
        private VkExtent2D swapChainExtent;
        private List<Long> swapChainImageViews;
        private List<Long> swapChainFramebuffers;

        private long renderPass;
        private long pipelineLayout;
        private long graphicsPipeline;

        private long commandPool;
        private List<VkCommandBuffer> commandBuffers;

        private List<Frame> inFlightFrames;
        private Map<Integer, Frame> imagesInFlight;
        private int currentFrame;

        boolean framebufferResize;

        // ======= METHODS ======= //

        public void run() {
            initWindow();
            initVulkan();
            mainLoop();
            cleanup();
        }

        private void initWindow() {

            if(!glfwInit()) {
                throw new RuntimeException("Cannot initialize GLFW");
            }

            glfwWindowHint(GLFW_CLIENT_API, GLFW_NO_API);
            glfwWindowHint(GLFW_RESIZABLE, GLFW_TRUE);

            String title = getClass().getEnclosingClass().getSimpleName();

            window = glfwCreateWindow(WIDTH, HEIGHT, title, NULL, NULL);

            if(window == NULL) {
                throw new RuntimeException("Cannot create window");
            }

            // In Java, we don't really need a user pointer here, because
            // we can simply pass an instance method reference to glfwSetFramebufferSizeCallback
            // However, I will show you how can you pass a user pointer to glfw in Java just for learning purposes:
            // long userPointer = JNINativeInterface.NewGlobalRef(this);
            // glfwSetWindowUserPointer(window, userPointer);
            // Please notice that the reference must be freed manually with JNINativeInterface.nDeleteGlobalRef
            glfwSetFramebufferSizeCallback(window, this::framebufferResizeCallback);
        }

        private void framebufferResizeCallback(long window, int width, int height) {
            // HelloTriangleApplication app = MemoryUtil.memGlobalRefToObject(glfwGetWindowUserPointer(window));
            // app.framebufferResize = true;
            framebufferResize = true;
        }

        private void initVulkan() {
            createInstance();
            setupDebugMessenger();
            createSurface();
            pickPhysicalDevice();
            createLogicalDevice();
            createCommandPool();
            createSwapChainObjects();
            createSyncObjects();
        }

        private void mainLoop() {

            while(!glfwWindowShouldClose(window)) {
                glfwPollEvents();
                drawFrame();
            }

            // Wait for the device to complete all operations before release resources
            vkDeviceWaitIdle(device);
        }

        private void cleanupSwapChain() {

            swapChainFramebuffers.forEach(framebuffer -> vkDestroyFramebuffer(device, framebuffer, null));

            vkFreeCommandBuffers(device, commandPool, asPointerBuffer(commandBuffers));

            vkDestroyPipeline(device, graphicsPipeline, null);

            vkDestroyPipelineLayout(device, pipelineLayout, null);

            vkDestroyRenderPass(device, renderPass, null);

            swapChainImageViews.forEach(imageView -> vkDestroyImageView(device, imageView, null));

            vkDestroySwapchainKHR(device, swapChain, null);
        }

        private void cleanup() {

            cleanupSwapChain();

            inFlightFrames.forEach(frame -> {

                vkDestroySemaphore(device, frame.renderFinishedSemaphore(), null);
                vkDestroySemaphore(device, frame.imageAvailableSemaphore(), null);
                vkDestroyFence(device, frame.fence(), null);
            });
            inFlightFrames.clear();

            vkDestroyCommandPool(device, commandPool, null);

            vkDestroyDevice(device, null);

            if(ENABLE_VALIDATION_LAYERS) {
                destroyDebugUtilsMessengerEXT(instance, debugMessenger, null);
            }

            vkDestroySurfaceKHR(instance, surface, null);

            vkDestroyInstance(instance, null);

            glfwDestroyWindow(window);

            glfwTerminate();
        }

        private void recreateSwapChain() {

            try(MemoryStack stack = stackPush()) {

                IntBuffer width = stack.ints(0);
                IntBuffer height = stack.ints(0);

                while(width.get(0) == 0 && height.get(0) == 0) {
                    glfwGetFramebufferSize(window, width, height);
                    glfwWaitEvents();
                }
            }

            vkDeviceWaitIdle(device);

            cleanupSwapChain();

            createSwapChainObjects();
        }

        private void createSwapChainObjects() {
            createSwapChain();
            createImageViews();
            createRenderPass();
            createGraphicsPipeline();
            createFramebuffers();
            createCommandBuffers();
        }

        private void createInstance() {

            if(ENABLE_VALIDATION_LAYERS && !checkValidationLayerSupport()) {
                throw new RuntimeException("Validation requested but not supported");
            }

            try(MemoryStack stack = stackPush()) {

                // Use calloc to initialize the structs with 0s. Otherwise, the program can crash due to random values

                VkApplicationInfo appInfo = VkApplicationInfo.callocStack(stack);

                appInfo.sType(VK_STRUCTURE_TYPE_APPLICATION_INFO);
                appInfo.pApplicationName(stack.UTF8Safe("Hello Triangle"));
                appInfo.applicationVersion(VK_MAKE_VERSION(1, 0, 0));
                appInfo.pEngineName(stack.UTF8Safe("No Engine"));
                appInfo.engineVersion(VK_MAKE_VERSION(1, 0, 0));
                appInfo.apiVersion(VK_API_VERSION_1_0);

                VkInstanceCreateInfo createInfo = VkInstanceCreateInfo.callocStack(stack);

                createInfo.sType(VK_STRUCTURE_TYPE_INSTANCE_CREATE_INFO);
                createInfo.pApplicationInfo(appInfo);
                // enabledExtensionCount is implicitly set when you call ppEnabledExtensionNames
                createInfo.ppEnabledExtensionNames(getRequiredExtensions());

                if(ENABLE_VALIDATION_LAYERS) {

                    createInfo.ppEnabledLayerNames(asPointerBuffer(VALIDATION_LAYERS));

                    VkDebugUtilsMessengerCreateInfoEXT debugCreateInfo = VkDebugUtilsMessengerCreateInfoEXT.callocStack(stack);
                    populateDebugMessengerCreateInfo(debugCreateInfo);
                    createInfo.pNext(debugCreateInfo.address());
                }

                // We need to retrieve the pointer of the created instance
                PointerBuffer instancePtr = stack.mallocPointer(1);

                if(vkCreateInstance(createInfo, null, instancePtr) != VK_SUCCESS) {
                    throw new RuntimeException("Failed to create instance");
                }

                instance = new VkInstance(instancePtr.get(0), createInfo);
            }
        }

        private void populateDebugMessengerCreateInfo(VkDebugUtilsMessengerCreateInfoEXT debugCreateInfo) {
            debugCreateInfo.sType(VK_STRUCTURE_TYPE_DEBUG_UTILS_MESSENGER_CREATE_INFO_EXT);
            debugCreateInfo.messageSeverity(VK_DEBUG_UTILS_MESSAGE_SEVERITY_VERBOSE_BIT_EXT | VK_DEBUG_UTILS_MESSAGE_SEVERITY_WARNING_BIT_EXT | VK_DEBUG_UTILS_MESSAGE_SEVERITY_ERROR_BIT_EXT);
            debugCreateInfo.messageType(VK_DEBUG_UTILS_MESSAGE_TYPE_GENERAL_BIT_EXT | VK_DEBUG_UTILS_MESSAGE_TYPE_VALIDATION_BIT_EXT | VK_DEBUG_UTILS_MESSAGE_TYPE_PERFORMANCE_BIT_EXT);
            debugCreateInfo.pfnUserCallback(HelloTriangleApplication::debugCallback);
        }

        private void setupDebugMessenger() {

            if(!ENABLE_VALIDATION_LAYERS) {
                return;
            }

            try(MemoryStack stack = stackPush()) {

                VkDebugUtilsMessengerCreateInfoEXT createInfo = VkDebugUtilsMessengerCreateInfoEXT.callocStack(stack);

                populateDebugMessengerCreateInfo(createInfo);

                LongBuffer pDebugMessenger = stack.longs(VK_NULL_HANDLE);

                if(createDebugUtilsMessengerEXT(instance, createInfo, null, pDebugMessenger) != VK_SUCCESS) {
                    throw new RuntimeException("Failed to set up debug messenger");
                }

                debugMessenger = pDebugMessenger.get(0);
            }
        }

        private void createSurface() {

            try(MemoryStack stack = stackPush()) {

                LongBuffer pSurface = stack.longs(VK_NULL_HANDLE);

                if(glfwCreateWindowSurface(instance, window, null, pSurface) != VK_SUCCESS) {
                    throw new RuntimeException("Failed to create window surface");
                }

                surface = pSurface.get(0);
            }
        }

        private void pickPhysicalDevice() {

            try(MemoryStack stack = stackPush()) {

                IntBuffer deviceCount = stack.ints(0);

                vkEnumeratePhysicalDevices(instance, deviceCount, null);

                if(deviceCount.get(0) == 0) {
                    throw new RuntimeException("Failed to find GPUs with Vulkan support");
                }

                PointerBuffer ppPhysicalDevices = stack.mallocPointer(deviceCount.get(0));

                vkEnumeratePhysicalDevices(instance, deviceCount, ppPhysicalDevices);

                for(int i = 0;i < ppPhysicalDevices.capacity();i++) {

                    VkPhysicalDevice device = new VkPhysicalDevice(ppPhysicalDevices.get(i), instance);

                    if(isDeviceSuitable(device)) {
                        physicalDevice = device;
                        return;
                    }
                }

                throw new RuntimeException("Failed to find a suitable GPU");
            }
        }

        private void createLogicalDevice() {

            try(MemoryStack stack = stackPush()) {

                QueueFamilyIndices indices = findQueueFamilies(physicalDevice);

                int[] uniqueQueueFamilies = indices.unique();

                VkDeviceQueueCreateInfo.Buffer queueCreateInfos = VkDeviceQueueCreateInfo.callocStack(uniqueQueueFamilies.length, stack);

                for(int i = 0;i < uniqueQueueFamilies.length;i++) {
                    VkDeviceQueueCreateInfo queueCreateInfo = queueCreateInfos.get(i);
                    queueCreateInfo.sType(VK_STRUCTURE_TYPE_DEVICE_QUEUE_CREATE_INFO);
                    queueCreateInfo.queueFamilyIndex(uniqueQueueFamilies[i]);
                    queueCreateInfo.pQueuePriorities(stack.floats(1.0f));
                }

                VkPhysicalDeviceFeatures deviceFeatures = VkPhysicalDeviceFeatures.callocStack(stack);

                VkDeviceCreateInfo createInfo = VkDeviceCreateInfo.callocStack(stack);

                createInfo.sType(VK_STRUCTURE_TYPE_DEVICE_CREATE_INFO);
                createInfo.pQueueCreateInfos(queueCreateInfos);
                // queueCreateInfoCount is automatically set

                createInfo.pEnabledFeatures(deviceFeatures);

                createInfo.ppEnabledExtensionNames(asPointerBuffer(DEVICE_EXTENSIONS));

                if(ENABLE_VALIDATION_LAYERS) {
                    createInfo.ppEnabledLayerNames(asPointerBuffer(VALIDATION_LAYERS));
                }

                PointerBuffer pDevice = stack.pointers(VK_NULL_HANDLE);

                if(vkCreateDevice(physicalDevice, createInfo, null, pDevice) != VK_SUCCESS) {
                    throw new RuntimeException("Failed to create logical device");
                }

                device = new VkDevice(pDevice.get(0), physicalDevice, createInfo);

                PointerBuffer pQueue = stack.pointers(VK_NULL_HANDLE);

                vkGetDeviceQueue(device, indices.graphicsFamily, 0, pQueue);
                graphicsQueue = new VkQueue(pQueue.get(0), device);

                vkGetDeviceQueue(device, indices.presentFamily, 0, pQueue);
                presentQueue = new VkQueue(pQueue.get(0), device);
            }
        }

        private void createSwapChain() {

            try(MemoryStack stack = stackPush()) {

                SwapChainSupportDetails swapChainSupport = querySwapChainSupport(physicalDevice, stack);

                VkSurfaceFormatKHR surfaceFormat = chooseSwapSurfaceFormat(swapChainSupport.formats);
                int presentMode = chooseSwapPresentMode(swapChainSupport.presentModes);
                VkExtent2D extent = chooseSwapExtent(swapChainSupport.capabilities);

                IntBuffer imageCount = stack.ints(swapChainSupport.capabilities.minImageCount() + 1);

                if(swapChainSupport.capabilities.maxImageCount() > 0 && imageCount.get(0) > swapChainSupport.capabilities.maxImageCount()) {
                    imageCount.put(0, swapChainSupport.capabilities.maxImageCount());
                }

                VkSwapchainCreateInfoKHR createInfo = VkSwapchainCreateInfoKHR.callocStack(stack);

                createInfo.sType(VK_STRUCTURE_TYPE_SWAPCHAIN_CREATE_INFO_KHR);
                createInfo.surface(surface);

                // Image settings
                createInfo.minImageCount(imageCount.get(0));
                createInfo.imageFormat(surfaceFormat.format());
                createInfo.imageColorSpace(surfaceFormat.colorSpace());
                createInfo.imageExtent(extent);
                createInfo.imageArrayLayers(1);
                createInfo.imageUsage(VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT);

                QueueFamilyIndices indices = findQueueFamilies(physicalDevice);

                if(!indices.graphicsFamily.equals(indices.presentFamily)) {
                    createInfo.imageSharingMode(VK_SHARING_MODE_CONCURRENT);
                    createInfo.pQueueFamilyIndices(stack.ints(indices.graphicsFamily, indices.presentFamily));
                } else {
                    createInfo.imageSharingMode(VK_SHARING_MODE_EXCLUSIVE);
                }

                createInfo.preTransform(swapChainSupport.capabilities.currentTransform());
                createInfo.compositeAlpha(VK_COMPOSITE_ALPHA_OPAQUE_BIT_KHR);
                createInfo.presentMode(presentMode);
                createInfo.clipped(true);

                createInfo.oldSwapchain(VK_NULL_HANDLE);

                LongBuffer pSwapChain = stack.longs(VK_NULL_HANDLE);

                if(vkCreateSwapchainKHR(device, createInfo, null, pSwapChain) != VK_SUCCESS) {
                    throw new RuntimeException("Failed to create swap chain");
                }

                swapChain = pSwapChain.get(0);

                vkGetSwapchainImagesKHR(device, swapChain, imageCount, null);

                LongBuffer pSwapchainImages = stack.mallocLong(imageCount.get(0));

                vkGetSwapchainImagesKHR(device, swapChain, imageCount, pSwapchainImages);

                swapChainImages = new ArrayList<>(imageCount.get(0));

                for(int i = 0;i < pSwapchainImages.capacity();i++) {
                    swapChainImages.add(pSwapchainImages.get(i));
                }

                swapChainImageFormat = surfaceFormat.format();
                swapChainExtent = VkExtent2D.create().set(extent);
            }
        }

        private void createImageViews() {

            swapChainImageViews = new ArrayList<>(swapChainImages.size());

            try(MemoryStack stack = stackPush()) {

                LongBuffer pImageView = stack.mallocLong(1);

                for(long swapChainImage : swapChainImages) {

                    VkImageViewCreateInfo createInfo = VkImageViewCreateInfo.callocStack(stack);

                    createInfo.sType(VK_STRUCTURE_TYPE_IMAGE_VIEW_CREATE_INFO);
                    createInfo.image(swapChainImage);
                    createInfo.viewType(VK_IMAGE_VIEW_TYPE_2D);
                    createInfo.format(swapChainImageFormat);

                    createInfo.components().r(VK_COMPONENT_SWIZZLE_IDENTITY);
                    createInfo.components().g(VK_COMPONENT_SWIZZLE_IDENTITY);
                    createInfo.components().b(VK_COMPONENT_SWIZZLE_IDENTITY);
                    createInfo.components().a(VK_COMPONENT_SWIZZLE_IDENTITY);

                    createInfo.subresourceRange().aspectMask(VK_IMAGE_ASPECT_COLOR_BIT);
                    createInfo.subresourceRange().baseMipLevel(0);
                    createInfo.subresourceRange().levelCount(1);
                    createInfo.subresourceRange().baseArrayLayer(0);
                    createInfo.subresourceRange().layerCount(1);

                    if (vkCreateImageView(device, createInfo, null, pImageView) != VK_SUCCESS) {
                        throw new RuntimeException("Failed to create image views");
                    }

                    swapChainImageViews.add(pImageView.get(0));
                }

            }
        }

        private void createRenderPass() {

            try(MemoryStack stack = stackPush()) {

                VkAttachmentDescription.Buffer colorAttachment = VkAttachmentDescription.callocStack(1, stack);
                colorAttachment.format(swapChainImageFormat);
                colorAttachment.samples(VK_SAMPLE_COUNT_1_BIT);
                colorAttachment.loadOp(VK_ATTACHMENT_LOAD_OP_CLEAR);
                colorAttachment.storeOp(VK_ATTACHMENT_STORE_OP_STORE);
                colorAttachment.stencilLoadOp(VK_ATTACHMENT_LOAD_OP_DONT_CARE);
                colorAttachment.stencilStoreOp(VK_ATTACHMENT_STORE_OP_DONT_CARE);
                colorAttachment.initialLayout(VK_IMAGE_LAYOUT_UNDEFINED);
                colorAttachment.finalLayout(VK_IMAGE_LAYOUT_PRESENT_SRC_KHR);

                VkAttachmentReference.Buffer colorAttachmentRef = VkAttachmentReference.callocStack(1, stack);
                colorAttachmentRef.attachment(0);
                colorAttachmentRef.layout(VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL);

                VkSubpassDescription.Buffer subpass = VkSubpassDescription.callocStack(1, stack);
                subpass.pipelineBindPoint(VK_PIPELINE_BIND_POINT_GRAPHICS);
                subpass.colorAttachmentCount(1);
                subpass.pColorAttachments(colorAttachmentRef);

                VkSubpassDependency.Buffer dependency = VkSubpassDependency.callocStack(1, stack);
                dependency.srcSubpass(VK_SUBPASS_EXTERNAL);
                dependency.dstSubpass(0);
                dependency.srcStageMask(VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT);
                dependency.srcAccessMask(0);
                dependency.dstStageMask(VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT);
                dependency.dstAccessMask(VK_ACCESS_COLOR_ATTACHMENT_READ_BIT | VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT);

                VkRenderPassCreateInfo renderPassInfo = VkRenderPassCreateInfo.callocStack(stack);
                renderPassInfo.sType(VK_STRUCTURE_TYPE_RENDER_PASS_CREATE_INFO);
                renderPassInfo.pAttachments(colorAttachment);
                renderPassInfo.pSubpasses(subpass);
                renderPassInfo.pDependencies(dependency);

                LongBuffer pRenderPass = stack.mallocLong(1);

                if(vkCreateRenderPass(device, renderPassInfo, null, pRenderPass) != VK_SUCCESS) {
                    throw new RuntimeException("Failed to create render pass");
                }

                renderPass = pRenderPass.get(0);
            }
        }

        private void createGraphicsPipeline() {

            try(MemoryStack stack = stackPush()) {

                // Let's compile the GLSL shaders into SPIR-V at runtime using the shaderc library
                // Check ShaderSPIRVUtils class to see how it can be done
                SPIRV vertShaderSPIRV = compileShaderFile("shaders/09_shader_base.vert", VERTEX_SHADER);
                SPIRV fragShaderSPIRV = compileShaderFile("shaders/09_shader_base.frag", FRAGMENT_SHADER);

                long vertShaderModule = createShaderModule(vertShaderSPIRV.bytecode());
                long fragShaderModule = createShaderModule(fragShaderSPIRV.bytecode());

                ByteBuffer entryPoint = stack.UTF8("main");

                VkPipelineShaderStageCreateInfo.Buffer shaderStages = VkPipelineShaderStageCreateInfo.callocStack(2, stack);

                VkPipelineShaderStageCreateInfo vertShaderStageInfo = shaderStages.get(0);

                vertShaderStageInfo.sType(VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO);
                vertShaderStageInfo.stage(VK_SHADER_STAGE_VERTEX_BIT);
                vertShaderStageInfo.module(vertShaderModule);
                vertShaderStageInfo.pName(entryPoint);

                VkPipelineShaderStageCreateInfo fragShaderStageInfo = shaderStages.get(1);

                fragShaderStageInfo.sType(VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO);
                fragShaderStageInfo.stage(VK_SHADER_STAGE_FRAGMENT_BIT);
                fragShaderStageInfo.module(fragShaderModule);
                fragShaderStageInfo.pName(entryPoint);

                // ===> VERTEX STAGE <===

                VkPipelineVertexInputStateCreateInfo vertexInputInfo = VkPipelineVertexInputStateCreateInfo.callocStack(stack);
                vertexInputInfo.sType(VK_STRUCTURE_TYPE_PIPELINE_VERTEX_INPUT_STATE_CREATE_INFO);

                // ===> ASSEMBLY STAGE <===

                VkPipelineInputAssemblyStateCreateInfo inputAssembly = VkPipelineInputAssemblyStateCreateInfo.callocStack(stack);
                inputAssembly.sType(VK_STRUCTURE_TYPE_PIPELINE_INPUT_ASSEMBLY_STATE_CREATE_INFO);
                inputAssembly.topology(VK_PRIMITIVE_TOPOLOGY_TRIANGLE_LIST);
                inputAssembly.primitiveRestartEnable(false);

                // ===> VIEWPORT & SCISSOR

                VkViewport.Buffer viewport = VkViewport.callocStack(1, stack);
                viewport.x(0.0f);
                viewport.y(0.0f);
                viewport.width(swapChainExtent.width());
                viewport.height(swapChainExtent.height());
                viewport.minDepth(0.0f);
                viewport.maxDepth(1.0f);

                VkRect2D.Buffer scissor = VkRect2D.callocStack(1, stack);
                scissor.offset(VkOffset2D.callocStack(stack).set(0, 0));
                scissor.extent(swapChainExtent);

                VkPipelineViewportStateCreateInfo viewportState = VkPipelineViewportStateCreateInfo.callocStack(stack);
                viewportState.sType(VK_STRUCTURE_TYPE_PIPELINE_VIEWPORT_STATE_CREATE_INFO);
                viewportState.pViewports(viewport);
                viewportState.pScissors(scissor);

                // ===> RASTERIZATION STAGE <===

                VkPipelineRasterizationStateCreateInfo rasterizer = VkPipelineRasterizationStateCreateInfo.callocStack(stack);
                rasterizer.sType(VK_STRUCTURE_TYPE_PIPELINE_RASTERIZATION_STATE_CREATE_INFO);
                rasterizer.depthClampEnable(false);
                rasterizer.rasterizerDiscardEnable(false);
                rasterizer.polygonMode(VK_POLYGON_MODE_FILL);
                rasterizer.lineWidth(1.0f);
                rasterizer.cullMode(VK_CULL_MODE_BACK_BIT);
                rasterizer.frontFace(VK_FRONT_FACE_CLOCKWISE);
                rasterizer.depthBiasEnable(false);

                // ===> MULTISAMPLING <===

                VkPipelineMultisampleStateCreateInfo multisampling = VkPipelineMultisampleStateCreateInfo.callocStack(stack);
                multisampling.sType(VK_STRUCTURE_TYPE_PIPELINE_MULTISAMPLE_STATE_CREATE_INFO);
                multisampling.sampleShadingEnable(false);
                multisampling.rasterizationSamples(VK_SAMPLE_COUNT_1_BIT);

                // ===> COLOR BLENDING <===

                VkPipelineColorBlendAttachmentState.Buffer colorBlendAttachment = VkPipelineColorBlendAttachmentState.callocStack(1, stack);
                colorBlendAttachment.colorWriteMask(VK_COLOR_COMPONENT_R_BIT | VK_COLOR_COMPONENT_G_BIT | VK_COLOR_COMPONENT_B_BIT | VK_COLOR_COMPONENT_A_BIT);
                colorBlendAttachment.blendEnable(false);

                VkPipelineColorBlendStateCreateInfo colorBlending = VkPipelineColorBlendStateCreateInfo.callocStack(stack);
                colorBlending.sType(VK_STRUCTURE_TYPE_PIPELINE_COLOR_BLEND_STATE_CREATE_INFO);
                colorBlending.logicOpEnable(false);
                colorBlending.logicOp(VK_LOGIC_OP_COPY);
                colorBlending.pAttachments(colorBlendAttachment);
                colorBlending.blendConstants(stack.floats(0.0f, 0.0f, 0.0f, 0.0f));

                // ===> PIPELINE LAYOUT CREATION <===

                VkPipelineLayoutCreateInfo pipelineLayoutInfo = VkPipelineLayoutCreateInfo.callocStack(stack);
                pipelineLayoutInfo.sType(VK_STRUCTURE_TYPE_PIPELINE_LAYOUT_CREATE_INFO);

                LongBuffer pPipelineLayout = stack.longs(VK_NULL_HANDLE);

                if(vkCreatePipelineLayout(device, pipelineLayoutInfo, null, pPipelineLayout) != VK_SUCCESS) {
                    throw new RuntimeException("Failed to create pipeline layout");
                }

                pipelineLayout = pPipelineLayout.get(0);

                VkGraphicsPipelineCreateInfo.Buffer pipelineInfo = VkGraphicsPipelineCreateInfo.callocStack(1, stack);
                pipelineInfo.sType(VK_STRUCTURE_TYPE_GRAPHICS_PIPELINE_CREATE_INFO);
                pipelineInfo.pStages(shaderStages);
                pipelineInfo.pVertexInputState(vertexInputInfo);
                pipelineInfo.pInputAssemblyState(inputAssembly);
                pipelineInfo.pViewportState(viewportState);
                pipelineInfo.pRasterizationState(rasterizer);
                pipelineInfo.pMultisampleState(multisampling);
                pipelineInfo.pColorBlendState(colorBlending);
                pipelineInfo.layout(pipelineLayout);
                pipelineInfo.renderPass(renderPass);
                pipelineInfo.subpass(0);
                pipelineInfo.basePipelineHandle(VK_NULL_HANDLE);
                pipelineInfo.basePipelineIndex(-1);

                LongBuffer pGraphicsPipeline = stack.mallocLong(1);

                if(vkCreateGraphicsPipelines(device, VK_NULL_HANDLE, pipelineInfo, null, pGraphicsPipeline) != VK_SUCCESS) {
                    throw new RuntimeException("Failed to create graphics pipeline");
                }

                graphicsPipeline = pGraphicsPipeline.get(0);

                // ===> RELEASE RESOURCES <===

                vkDestroyShaderModule(device, vertShaderModule, null);
                vkDestroyShaderModule(device, fragShaderModule, null);

                vertShaderSPIRV.free();
                fragShaderSPIRV.free();
            }
        }

        private void createFramebuffers() {

            swapChainFramebuffers = new ArrayList<>(swapChainImageViews.size());

            try(MemoryStack stack = stackPush()) {

                LongBuffer attachments = stack.mallocLong(1);
                LongBuffer pFramebuffer = stack.mallocLong(1);

                // Lets allocate the create info struct once and just update the pAttachments field each iteration
                VkFramebufferCreateInfo framebufferInfo = VkFramebufferCreateInfo.callocStack(stack);
                framebufferInfo.sType(VK_STRUCTURE_TYPE_FRAMEBUFFER_CREATE_INFO);
                framebufferInfo.renderPass(renderPass);
                framebufferInfo.width(swapChainExtent.width());
                framebufferInfo.height(swapChainExtent.height());
                framebufferInfo.layers(1);

                for(long imageView : swapChainImageViews) {

                    attachments.put(0, imageView);

                    framebufferInfo.pAttachments(attachments);

                    if(vkCreateFramebuffer(device, framebufferInfo, null, pFramebuffer) != VK_SUCCESS) {
                        throw new RuntimeException("Failed to create framebuffer");
                    }

                    swapChainFramebuffers.add(pFramebuffer.get(0));
                }
            }
        }

        private void createCommandPool() {

            try(MemoryStack stack = stackPush()) {

                QueueFamilyIndices queueFamilyIndices = findQueueFamilies(physicalDevice);

                VkCommandPoolCreateInfo poolInfo = VkCommandPoolCreateInfo.callocStack(stack);
                poolInfo.sType(VK_STRUCTURE_TYPE_COMMAND_POOL_CREATE_INFO);
                poolInfo.queueFamilyIndex(queueFamilyIndices.graphicsFamily);

                LongBuffer pCommandPool = stack.mallocLong(1);

                if (vkCreateCommandPool(device, poolInfo, null, pCommandPool) != VK_SUCCESS) {
                    throw new RuntimeException("Failed to create command pool");
                }

                commandPool = pCommandPool.get(0);
            }
        }

        private void createCommandBuffers() {

            final int commandBuffersCount = swapChainFramebuffers.size();

            commandBuffers = new ArrayList<>(commandBuffersCount);

            try(MemoryStack stack = stackPush()) {

                VkCommandBufferAllocateInfo allocInfo = VkCommandBufferAllocateInfo.callocStack(stack);
                allocInfo.sType(VK_STRUCTURE_TYPE_COMMAND_BUFFER_ALLOCATE_INFO);
                allocInfo.commandPool(commandPool);
                allocInfo.level(VK_COMMAND_BUFFER_LEVEL_PRIMARY);
                allocInfo.commandBufferCount(commandBuffersCount);

                PointerBuffer pCommandBuffers = stack.mallocPointer(commandBuffersCount);

                if(vkAllocateCommandBuffers(device, allocInfo, pCommandBuffers) != VK_SUCCESS) {
                    throw new RuntimeException("Failed to allocate command buffers");
                }

                for(int i = 0;i < commandBuffersCount;i++) {
                    commandBuffers.add(new VkCommandBuffer(pCommandBuffers.get(i), device));
                }

                VkCommandBufferBeginInfo beginInfo = VkCommandBufferBeginInfo.callocStack(stack);
                beginInfo.sType(VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO);

                VkRenderPassBeginInfo renderPassInfo = VkRenderPassBeginInfo.callocStack(stack);
                renderPassInfo.sType(VK_STRUCTURE_TYPE_RENDER_PASS_BEGIN_INFO);

                renderPassInfo.renderPass(renderPass);

                VkRect2D renderArea = VkRect2D.callocStack(stack);
                renderArea.offset(VkOffset2D.callocStack(stack).set(0, 0));
                renderArea.extent(swapChainExtent);
                renderPassInfo.renderArea(renderArea);

                VkClearValue.Buffer clearValues = VkClearValue.callocStack(1, stack);
                clearValues.color().float32(stack.floats(0.0f, 0.0f, 0.0f, 1.0f));
                renderPassInfo.pClearValues(clearValues);

                for(int i = 0;i < commandBuffersCount;i++) {

                    VkCommandBuffer commandBuffer = commandBuffers.get(i);

                    if(vkBeginCommandBuffer(commandBuffer, beginInfo) != VK_SUCCESS) {
                        throw new RuntimeException("Failed to begin recording command buffer");
                    }

                    renderPassInfo.framebuffer(swapChainFramebuffers.get(i));


                    vkCmdBeginRenderPass(commandBuffer, renderPassInfo, VK_SUBPASS_CONTENTS_INLINE);
                    {
                        vkCmdBindPipeline(commandBuffer, VK_PIPELINE_BIND_POINT_GRAPHICS, graphicsPipeline);

                        vkCmdDraw(commandBuffer, 3, 1, 0, 0);
                    }
                    vkCmdEndRenderPass(commandBuffer);


                    if(vkEndCommandBuffer(commandBuffer) != VK_SUCCESS) {
                        throw new RuntimeException("Failed to record command buffer");
                    }

                }

            }
        }

        private void createSyncObjects() {

            inFlightFrames = new ArrayList<>(MAX_FRAMES_IN_FLIGHT);
            imagesInFlight = new HashMap<>(swapChainImages.size());

            try(MemoryStack stack = stackPush()) {

                VkSemaphoreCreateInfo semaphoreInfo = VkSemaphoreCreateInfo.callocStack(stack);
                semaphoreInfo.sType(VK_STRUCTURE_TYPE_SEMAPHORE_CREATE_INFO);

                VkFenceCreateInfo fenceInfo = VkFenceCreateInfo.callocStack(stack);
                fenceInfo.sType(VK_STRUCTURE_TYPE_FENCE_CREATE_INFO);
                fenceInfo.flags(VK_FENCE_CREATE_SIGNALED_BIT);

                LongBuffer pImageAvailableSemaphore = stack.mallocLong(1);
                LongBuffer pRenderFinishedSemaphore = stack.mallocLong(1);
                LongBuffer pFence = stack.mallocLong(1);

                for(int i = 0;i < MAX_FRAMES_IN_FLIGHT;i++) {

                    if(vkCreateSemaphore(device, semaphoreInfo, null, pImageAvailableSemaphore) != VK_SUCCESS
                    || vkCreateSemaphore(device, semaphoreInfo, null, pRenderFinishedSemaphore) != VK_SUCCESS
                    || vkCreateFence(device, fenceInfo, null, pFence) != VK_SUCCESS) {

                        throw new RuntimeException("Failed to create synchronization objects for the frame " + i);
                    }

                    inFlightFrames.add(new Frame(pImageAvailableSemaphore.get(0), pRenderFinishedSemaphore.get(0), pFence.get(0)));
                }

            }
        }

        private void drawFrame() {

            try(MemoryStack stack = stackPush()) {

                Frame thisFrame = inFlightFrames.get(currentFrame);

                vkWaitForFences(device, thisFrame.pFence(), true, UINT64_MAX);

                IntBuffer pImageIndex = stack.mallocInt(1);

                int vkResult = vkAcquireNextImageKHR(device, swapChain, UINT64_MAX, thisFrame.imageAvailableSemaphore(), VK_NULL_HANDLE, pImageIndex);

                if(vkResult == VK_ERROR_OUT_OF_DATE_KHR) {
                    recreateSwapChain();
                    return;
                }

                final int imageIndex = pImageIndex.get(0);

                if(imagesInFlight.containsKey(imageIndex)) {
                    vkWaitForFences(device, imagesInFlight.get(imageIndex).fence(), true, UINT64_MAX);
                }

                imagesInFlight.put(imageIndex, thisFrame);

                VkSubmitInfo submitInfo = VkSubmitInfo.callocStack(stack);
                submitInfo.sType(VK_STRUCTURE_TYPE_SUBMIT_INFO);

                submitInfo.waitSemaphoreCount(1);
                submitInfo.pWaitSemaphores(thisFrame.pImageAvailableSemaphore());
                submitInfo.pWaitDstStageMask(stack.ints(VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT));

                submitInfo.pSignalSemaphores(thisFrame.pRenderFinishedSemaphore());

                submitInfo.pCommandBuffers(stack.pointers(commandBuffers.get(imageIndex)));

                vkResetFences(device, thisFrame.pFence());

                if(vkQueueSubmit(graphicsQueue, submitInfo, thisFrame.fence()) != VK_SUCCESS) {
                    throw new RuntimeException("Failed to submit draw command buffer");
                }

                VkPresentInfoKHR presentInfo = VkPresentInfoKHR.callocStack(stack);
                presentInfo.sType(VK_STRUCTURE_TYPE_PRESENT_INFO_KHR);

                presentInfo.pWaitSemaphores(thisFrame.pRenderFinishedSemaphore());

                presentInfo.swapchainCount(1);
                presentInfo.pSwapchains(stack.longs(swapChain));

                presentInfo.pImageIndices(pImageIndex);

                vkResult = vkQueuePresentKHR(presentQueue, presentInfo);

                if(vkResult == VK_ERROR_OUT_OF_DATE_KHR || vkResult == VK_SUBOPTIMAL_KHR || framebufferResize) {
                    framebufferResize = false;
                    recreateSwapChain();
                } else if(vkResult != VK_SUCCESS) {
                    throw new RuntimeException("Failed to present swap chain image");
                }

                currentFrame = (currentFrame + 1) % MAX_FRAMES_IN_FLIGHT;
            }
        }

        private long createShaderModule(ByteBuffer spirvCode) {

            try(MemoryStack stack = stackPush()) {

                VkShaderModuleCreateInfo createInfo = VkShaderModuleCreateInfo.callocStack(stack);

                createInfo.sType(VK_STRUCTURE_TYPE_SHADER_MODULE_CREATE_INFO);
                createInfo.pCode(spirvCode);

                LongBuffer pShaderModule = stack.mallocLong(1);

                if(vkCreateShaderModule(device, createInfo, null, pShaderModule) != VK_SUCCESS) {
                    throw new RuntimeException("Failed to create shader module");
                }

                return pShaderModule.get(0);
            }
        }

        private VkSurfaceFormatKHR chooseSwapSurfaceFormat(VkSurfaceFormatKHR.Buffer availableFormats) {
            return availableFormats.stream()
                    .filter(availableFormat -> availableFormat.format() == VK_FORMAT_B8G8R8_UNORM)
                    .filter(availableFormat -> availableFormat.colorSpace() == VK_COLOR_SPACE_SRGB_NONLINEAR_KHR)
                    .findAny()
                    .orElse(availableFormats.get(0));
        }

        private int chooseSwapPresentMode(IntBuffer availablePresentModes) {

            for(int i = 0;i < availablePresentModes.capacity();i++) {
                if(availablePresentModes.get(i) == VK_PRESENT_MODE_MAILBOX_KHR) {
                    return availablePresentModes.get(i);
                }
            }

            return VK_PRESENT_MODE_FIFO_KHR;
        }

        private VkExtent2D chooseSwapExtent(VkSurfaceCapabilitiesKHR capabilities) {

            if(capabilities.currentExtent().width() != UINT32_MAX) {
                return capabilities.currentExtent();
            }

            IntBuffer width = stackGet().ints(0);
            IntBuffer height = stackGet().ints(0);

            glfwGetFramebufferSize(window, width, height);

            VkExtent2D actualExtent = VkExtent2D.mallocStack().set(width.get(0), height.get(0));

            VkExtent2D minExtent = capabilities.minImageExtent();
            VkExtent2D maxExtent = capabilities.maxImageExtent();

            actualExtent.width(clamp(minExtent.width(), maxExtent.width(), actualExtent.width()));
            actualExtent.height(clamp(minExtent.height(), maxExtent.height(), actualExtent.height()));

            return actualExtent;
        }

        private int clamp(int min, int max, int value) {
            return Math.max(min, Math.min(max, value));
        }

        private boolean isDeviceSuitable(VkPhysicalDevice device) {

            QueueFamilyIndices indices = findQueueFamilies(device);

            boolean extensionsSupported = checkDeviceExtensionSupport(device);
            boolean swapChainAdequate = false;

            if(extensionsSupported) {
                try(MemoryStack stack = stackPush()) {
                    SwapChainSupportDetails swapChainSupport = querySwapChainSupport(device, stack);
                    swapChainAdequate = swapChainSupport.formats.hasRemaining() && swapChainSupport.presentModes.hasRemaining();
                }
            }

            return indices.isComplete() && extensionsSupported && swapChainAdequate;
        }

        private boolean checkDeviceExtensionSupport(VkPhysicalDevice device) {

            try(MemoryStack stack = stackPush()) {

                IntBuffer extensionCount = stack.ints(0);

                vkEnumerateDeviceExtensionProperties(device, (String)null, extensionCount, null);

                VkExtensionProperties.Buffer availableExtensions = VkExtensionProperties.mallocStack(extensionCount.get(0), stack);

                vkEnumerateDeviceExtensionProperties(device, (String)null, extensionCount, availableExtensions);

                return availableExtensions.stream()
                        .map(VkExtensionProperties::extensionNameString)
                        .collect(toSet())
                        .containsAll(DEVICE_EXTENSIONS);
            }
        }

        private SwapChainSupportDetails querySwapChainSupport(VkPhysicalDevice device, MemoryStack stack) {

            SwapChainSupportDetails details = new SwapChainSupportDetails();

            details.capabilities = VkSurfaceCapabilitiesKHR.mallocStack(stack);
            vkGetPhysicalDeviceSurfaceCapabilitiesKHR(device, surface, details.capabilities);

            IntBuffer count = stack.ints(0);

            vkGetPhysicalDeviceSurfaceFormatsKHR(device, surface, count, null);

            if(count.get(0) != 0) {
                details.formats = VkSurfaceFormatKHR.mallocStack(count.get(0), stack);
                vkGetPhysicalDeviceSurfaceFormatsKHR(device, surface, count, details.formats);
            }

            vkGetPhysicalDeviceSurfacePresentModesKHR(device,surface, count, null);

            if(count.get(0) != 0) {
                details.presentModes = stack.mallocInt(count.get(0));
                vkGetPhysicalDeviceSurfacePresentModesKHR(device, surface, count, details.presentModes);
            }

            return details;
        }

        private QueueFamilyIndices findQueueFamilies(VkPhysicalDevice device) {

            QueueFamilyIndices indices = new QueueFamilyIndices();

            try(MemoryStack stack = stackPush()) {

                IntBuffer queueFamilyCount = stack.ints(0);

                vkGetPhysicalDeviceQueueFamilyProperties(device, queueFamilyCount, null);

                VkQueueFamilyProperties.Buffer queueFamilies = VkQueueFamilyProperties.mallocStack(queueFamilyCount.get(0), stack);

                vkGetPhysicalDeviceQueueFamilyProperties(device, queueFamilyCount, queueFamilies);

                IntBuffer presentSupport = stack.ints(VK_FALSE);

                for(int i = 0;i < queueFamilies.capacity() || !indices.isComplete();i++) {

                    if((queueFamilies.get(i).queueFlags() & VK_QUEUE_GRAPHICS_BIT) != 0) {
                        indices.graphicsFamily = i;
                    }

                    vkGetPhysicalDeviceSurfaceSupportKHR(device, i, surface, presentSupport);

                    if(presentSupport.get(0) == VK_TRUE) {
                        indices.presentFamily = i;
                    }
                }

                return indices;
            }
        }

        private PointerBuffer asPointerBuffer(Collection<String> collection) {

            MemoryStack stack = stackGet();

            PointerBuffer buffer = stack.mallocPointer(collection.size());

            collection.stream()
                    .map(stack::UTF8)
                    .forEach(buffer::put);

            return buffer.rewind();
        }

        private PointerBuffer asPointerBuffer(List<? extends Pointer> list) {

            MemoryStack stack = stackGet();

            PointerBuffer buffer = stack.mallocPointer(list.size());

            list.forEach(buffer::put);

            return buffer.rewind();
        }

        private PointerBuffer getRequiredExtensions() {

            PointerBuffer glfwExtensions = glfwGetRequiredInstanceExtensions();

            if(ENABLE_VALIDATION_LAYERS) {

                MemoryStack stack = stackGet();

                PointerBuffer extensions = stack.mallocPointer(glfwExtensions.capacity() + 1);

                extensions.put(glfwExtensions);
                extensions.put(stack.UTF8(VK_EXT_DEBUG_UTILS_EXTENSION_NAME));

                // Rewind the buffer before returning it to reset its position back to 0
                return extensions.rewind();
            }

            return glfwExtensions;
        }

        private boolean checkValidationLayerSupport() {

            try(MemoryStack stack = stackPush()) {

                IntBuffer layerCount = stack.ints(0);

                vkEnumerateInstanceLayerProperties(layerCount, null);

                VkLayerProperties.Buffer availableLayers = VkLayerProperties.mallocStack(layerCount.get(0), stack);

                vkEnumerateInstanceLayerProperties(layerCount, availableLayers);

                Set<String> availableLayerNames = availableLayers.stream()
                        .map(VkLayerProperties::layerNameString)
                        .collect(toSet());

                return availableLayerNames.containsAll(VALIDATION_LAYERS);
            }
        }

    }

    public static void main(String[] args) {

        HelloTriangleApplication app = new HelloTriangleApplication();

        app.run();
    }

}
