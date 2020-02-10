package javavulkantutorial;

import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;

import java.nio.IntBuffer;
import java.nio.LongBuffer;
import java.util.HashSet;
import java.util.Set;

import static java.util.stream.Collectors.toSet;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.glfw.GLFWVulkan.glfwGetRequiredInstanceExtensions;
import static org.lwjgl.system.Configuration.DEBUG;
import static org.lwjgl.system.MemoryStack.stackGet;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.system.MemoryUtil.NULL;
import static org.lwjgl.vulkan.EXTDebugUtils.*;
import static org.lwjgl.vulkan.VK10.*;

public class Ch03PhysicalDeviceSelection {

    private static class HelloTriangleApplication {

        private static final int WIDTH = 800;
        private static final int HEIGHT = 600;

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

            private boolean isComplete() {
                return graphicsFamily != null;
            }

        }

        // ======= FIELDS ======= //

        private long window;
        private VkInstance instance;
        private long debugMessenger;
        private VkPhysicalDevice physicalDevice;

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
            glfwWindowHint(GLFW_RESIZABLE, GLFW_FALSE);

            String title = getClass().getEnclosingClass().getSimpleName();

            window = glfwCreateWindow(WIDTH, HEIGHT, title, NULL, NULL);

            if(window == NULL) {
                throw new RuntimeException("Cannot create window");
            }
        }

        private void initVulkan() {
            createInstance();
            setupDebugMessenger();
            pickPhysicalDevice();
        }

        private void mainLoop() {

            while(!glfwWindowShouldClose(window)) {
                glfwPollEvents();
            }

        }

        private void cleanup() {

            if(ENABLE_VALIDATION_LAYERS) {
                destroyDebugUtilsMessengerEXT(instance, debugMessenger, null);
            }

            vkDestroyInstance(instance, null);

            glfwDestroyWindow(window);

            glfwTerminate();
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

                    createInfo.ppEnabledLayerNames(validationLayersAsPointerBuffer());

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

        private void pickPhysicalDevice() {

            try(MemoryStack stack = stackPush()) {

                IntBuffer deviceCount = stack.ints(0);

                vkEnumeratePhysicalDevices(instance, deviceCount, null);

                if(deviceCount.get(0) == 0) {
                    throw new RuntimeException("Failed to find GPUs with Vulkan support");
                }

                PointerBuffer ppPhysicalDevices = stack.mallocPointer(deviceCount.get(0));

                vkEnumeratePhysicalDevices(instance, deviceCount, ppPhysicalDevices);

                VkPhysicalDevice device = null;

                for(int i = 0;i < ppPhysicalDevices.capacity();i++) {

                    device = new VkPhysicalDevice(ppPhysicalDevices.get(i), instance);

                    if(isDeviceSuitable(device)) {
                        break;
                    }
                }

                if(device == null) {
                    throw new RuntimeException("Failed to find a suitable GPU");
                }

                physicalDevice = device;
            }
        }

        private boolean isDeviceSuitable(VkPhysicalDevice device) {

            QueueFamilyIndices indices = findQueueFamilies(device);

            return indices.isComplete();
        }

        private QueueFamilyIndices findQueueFamilies(VkPhysicalDevice device) {

            QueueFamilyIndices indices = new QueueFamilyIndices();

            try(MemoryStack stack = stackPush()) {

                IntBuffer queueFamilyCount = stack.ints(0);

                vkGetPhysicalDeviceQueueFamilyProperties(device, queueFamilyCount, null);

                VkQueueFamilyProperties.Buffer queueFamilies = VkQueueFamilyProperties.mallocStack(queueFamilyCount.get(0), stack);

                vkGetPhysicalDeviceQueueFamilyProperties(device, queueFamilyCount, queueFamilies);

                indices.graphicsFamily = queueFamilies.stream()
                        .map(VkQueueFamilyProperties::queueFlags)
                        .filter(queueFlags -> (queueFlags & VK_QUEUE_GRAPHICS_BIT) != 0)
                        .findAny().orElse(null);

                return indices;
            }
        }

        private PointerBuffer validationLayersAsPointerBuffer() {

            MemoryStack stack = stackGet();

            PointerBuffer buffer = stack.mallocPointer(VALIDATION_LAYERS.size());

            VALIDATION_LAYERS.stream()
                    .map(stack::UTF8)
                    .forEach(buffer::put);

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
