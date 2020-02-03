package naitsirc98.javavulkantutorial;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.system.MemoryUtil.NULL;

public class Ch00BaseCode {

    public static void main(String[] args) {

        HelloTriangleApplication app = new HelloTriangleApplication();

        app.run();
    }

    private static class HelloTriangleApplication {

        private static final int WIDTH = 800;
        private static final int HEIGHT = 600;

        // ======= FIELDS ======= //

        private long window;

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

            window = glfwCreateWindow(WIDTH, HEIGHT, "Vulkan", NULL, NULL);

        }

        private void initVulkan() {

        }

        private void mainLoop() {

            while(!glfwWindowShouldClose(window)) {
                glfwPollEvents();
            }

        }

        private void cleanup() {

            glfwDestroyWindow(window);

            glfwTerminate();
        }

    }
}
