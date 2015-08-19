package com.smartseclab.fuzzghost;

import android.content.Context;
import android.support.v4.util.ArrayMap;
import android.util.Log;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;

import generator.shadows.BundleShadow;
import generator.shadows.FileDescriptorShadow;
import generator.shadows.ParcelableShadow;
import generator.shadows.SparseArrayShadow;

/**
 * The actual executor of server commands.
 */
public class TestExecutor {

    private Class<?> tClass;
    private Object tObject;
    private Context context;
    private String tag = getExecutorTag();
    private ShadowRetriever retriever;
    private static final Map<String, String> classMap = createMap();


    private static Map<String, String> createMap() {
        ArrayMap<String, String> map = new ArrayMap<String, String>();
        map.put("WifiService", "com.android.net.wifi.WifiManager");
        map.put("AudioService", "android.media.AudioManager");
        return Collections.unmodifiableMap(map);
    }

    private static String getExecutorTag() {
        return MainActivity.getApplicationTag() + "Executor";
    }

    /**
     * Grabs an appropriate Android Service Manager and obtains the service.
     *
     * @param context
     * @param className
     * @throws UnsupportedClassException
     * @throws Exception
     */
    public TestExecutor(Context context, String className) throws UnsupportedClassException, Exception {
        if (!knowsClass(className)) {
            Log.e(tag, "OMG! Unsupported class name: " + className);
            throw new UnsupportedClassException();
        }
        this.context = context;
        Class manager = context.getClassLoader().loadClass(classMap.get(className));
        Method get = manager.getDeclaredMethod("getService", new Class[0]);
        get.setAccessible(true);
        tObject = get.invoke(manager);
        tClass = tObject.getClass();
        retriever = new ShadowRetriever(context);
        Log.d(tag, "Object of class " + tClass.getName() + " has been successfully created.");
    }

    /**
     * Run the described method with given arguments. Retrieve actual objects from Shadows first.
     *
     * @param methodName
     * @param argTypes
     * @param args
     * @return
     * @throws NoSuchMethodException
     */
    public Object runMethod(String methodName, Class[] argTypes, Object[] args) throws NoSuchMethodException, ClassNotFoundException, ArgNamesAndValuesNotEqualException {
        if (argTypes.length != args.length) {
            Log.e(tag, "Arguments length does not equal types length; terminating.");
            throw new ArgNamesAndValuesNotEqualException();
        }
        Log.d(tag, "Test started. *sirens*");
        Log.d(tag, "Method name: " + methodName);
        Log.d(tag, "Arguments: " + argTypes.length);
        for (int i = 0; i < args.length; ++i) {
            argTypes[i] = retriever.readClass(args[i]);
            args[i] = retriever.readObject(args[i]);
        }
        return execute(tClass.getDeclaredMethod(methodName, argTypes), args);
    }

    /**
     * Executes the method with the given argument. Returns the result, the "Void" string
     * or the Exception name concatenated with message.
     *
     * @param method
     * @param args
     * @return
     */
    private Object execute(Method method, Object[] args) {
        try {
            method.setAccessible(true);
            if (!method.getReturnType().equals(Void.TYPE))
                return method.invoke(tObject, args);
            method.invoke(tObject, args);
            return "Void";
        } catch (Exception e) {
            return e.getClass().getName() + ": " + e.getMessage();
        }
    }

    /**
     * Return the argument types of all classe's methods with the given name.
     *
     * @param context
     * @param className
     * @param methodName
     * @return
     * @throws NoSuchMethodException
     */
    public static String[] getMethodArgs(Context context, String className, String
            methodName) throws NoSuchMethodException {
        ArrayList<String> argSets = new ArrayList<String>();
        try {
            for (Method m : context.getClassLoader().loadClass(classMap.get(className)).getDeclaredMethods())
                if (m.getName().equals(methodName))
                    argSets.add(concatMethodInfo(m));
        } catch (ClassNotFoundException cnfe) {
            cnfe.printStackTrace();
            return null;
        }
        if (argSets.isEmpty())
            throw new NoSuchMethodException();
        return argSets.toArray(new String[argSets.size()]);
    }

    /**
     * Returns all methods from a class alongside with its arguments as a String array.
     *
     * @param context
     * @param className
     * @return
     * @throws Exception
     */
    public static String[] getClassMethods(Context context, String className) throws Exception {
        ClassLoader loader = context.getClassLoader();
        try {
            Class c = null;
            if (knowsClass(className))
                c = loader.loadClass(classMap.get(className));
            else
                throw new UnsupportedClassException();

            Method[] methods = c.getDeclaredMethods();
            String[] result = new String[methods.length + 1];
            result[0] = "Class " + className + " methods:\n";
            for (int i = 0; i < methods.length; ++i)
                result[i + 1] = concatMethodInfo(methods[i]);
            return result;
        } catch (Exception e) {
            throw e;
        }
    }

    /**
     * Concatenate all class names from a class array to create a single String.
     *
     * @param method
     * @return
     */
    private static String concatMethodInfo(Method method) {
        StringBuilder sb = new StringBuilder(method.getReturnType().getName());
        sb.append(" ");
        sb.append(method.getName());
        sb.append("( ");
        Class[] params = method.getParameterTypes();
        if (params.length == 0)
            sb.append("void ");
        else
            for (Class c : method.getParameterTypes()) {
                sb.append(c.getName());
                sb.append(" ");
            }
        sb.append(")\n");
        return sb.toString();
    }

    /**
     * Do I know the class which I am going to test?
     *
     * @param className
     * @return
     */
    public static boolean knowsClass(String className) {
        return classMap.containsKey(className);
    }
}