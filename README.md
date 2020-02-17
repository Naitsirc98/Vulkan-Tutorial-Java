# Vulkan-Tutorial-Java
Java port of the [great tutorial by Alexander Overvoorde](https://vulkan-tutorial.com/). The original code can be found [here](https://github.com/Overv/VulkanTutorial).


![Tutorial Image 3](tutorial-image.jpg) 

I'm going to be using [LWJGL (Lightweight Java Game Library)](https://www.lwjgl.org/), a fantastic low level API for Java with bindings for GLFW, Vulkan, OpenGL, and other C libraries.

These tutorials are written to be easily followed with the original ones. However, I've made some changes to fit the Java and LWJGL styles.

## LWJGL Style

If you don't know LWJGL, it may be difficult to you understand certain concepts and patterns you will see throughout this tutorials. I will briefly explain some of the most important
concepts you need to know to properly follow the code:

## Native handles

Vulkan has its own handles named properly, such as VkImage, VkBuffer or VkCommandPool. These are unsigned integer numbers behind the scene, and because Java
does not have typedefs, we need to use *long* as the type of all of those objects. For that reason, you will see lots of *long* variables.

## Pointers and references

Some structs and functions will take as parameters references and pointers to other variables, for example to output multiple values. Consider this function in C:

```C

int width;
int height;

glfwGetWindowSize(window, &width, &height);

// Now width and height contains the window dimension values

```

We pass in 2 *int* pointers, and the function writes the memory pointed by them. Easy and fast.

But how about in Java? There is no concept of pointer at all. While we can pass a copy of a reference and modify the object's contents inside
a function, we cannot do so with primitives.
We have two options. We can use either an int array, which is effectively an object, or to use [Java NIO Buffers](https://docs.oracle.com/javase/7/docs/api/java/nio/Buffer.html).
Buffers in LWJGL are basically a windowed array, with an internal position and limit. We are going to use these buffers, since we can allocate them off heap, as we will see later.

Then, the above function will look like this with NIO Buffers:

```Java

IntBuffer width = BufferUtils.createIntBuffer(1);
IntBuffer height = BufferUtils.createIntBuffer(1);

glfwGetWindowSize(window, width, height);

// Print the values 
System.out.println("width = " + width.get(0));
System.out.println("height = " + height.get(0));
```

Nice, we now can pass pointers to primitive values, but we are dynamically allocating 2 new objects for just 2 integers.
And what if we only need these 2 variables for a short period of time? We need to wait for the Garbage Collector to get rid of those 
disposable variables.

Lucky for us, LWJGL solves this problem with its own memory management system. You can learn about that [here](https://github.com/LWJGL/lwjgl3-wiki/wiki/1.3.-Memory-FAQ).

### Stack allocation

In C and C++, we can easily allocate objects on the stack:

```C++

VkApplicationInfo appInfo = {};
// ...

```

However, this is not possible in Java.
Fortunately for us, LWJGL allows us to kind of stack allocate variables on the stack. For that, we need a [MemoryStack](https://javadoc.lwjgl.org/org/lwjgl/system/MemoryStack.html) instance.
Since a stack frame is pushed at the beginning of a function and is popped at the end, no matter what happens in the middle, we should
use try-with-resources syntax to imitate this behaviour:

```Java

try(MemoryStack stack = stackPush()) {

  // ...
  
  
} // By this line, stack is popped and all the variables in this stack frame are released

```

Great, now we are able to use stack allocation in Java. Let's see how it looks like:

```Java

private void createInstance() {

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
        createInfo.ppEnabledExtensionNames(glfwGetRequiredInstanceExtensions());
        // same with enabledLayerCount
        createInfo.ppEnabledLayerNames(null);

        // We need to retrieve the pointer of the created instance
        PointerBuffer instancePtr = stack.mallocPointer(1);

        if(vkCreateInstance(createInfo, null, instancePtr) != VK_SUCCESS) {
            throw new RuntimeException("Failed to create instance");
        }

        instance = new VkInstance(instancePtr.get(0), createInfo);
    }
}

```



