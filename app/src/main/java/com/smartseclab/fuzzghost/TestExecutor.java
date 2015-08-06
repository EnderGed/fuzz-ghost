package com.smartseclab.fuzzghost;

import android.content.Context;
import android.util.Log;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Random;

/**
 * Created by smartseclab on 7/31/15.
 */
public class TestExecutor {

    Class<?> tClass;
    Object tObject;
    Context context;
    String tag = getExecutorTag();

    public static String getExecutorTag(){
        return MainActivity.getApplicationTag() + "Exe";
    }

    public TestExecutor(Context context, String className) throws UnsupportedClassException, Exception {
        ClassLoader loader = context.getClassLoader();
        this.context = context;
        Class manager = null;
        try {
            if (className.equals("WifiService"))
                manager = loader.loadClass("com.android.net.wifi.WifiManager");
            else if (className.equals("AudioService"))
                manager = loader.loadClass("android.media.AudioManager");
            else {
                Log.e(tag, "OMG! Unsupported class name: " + className);
                throw new UnsupportedClassException();
            }
            Class[] args = new Class[0];
            Method get = manager.getDeclaredMethod("getService", args);
            get.setAccessible(true);
            tObject = get.invoke(manager);
            tClass = tObject.getClass();
            Log.d(tag, "Object of class " + tClass.getName() + " has been successfully created.");
        } catch (Exception e) {
            errorele(e);
            throw e;
        }
    }

    //Return true on success
    public boolean fuzzMethod(String methodName, Class[] argTypes) throws NoSuchMethodException {
        Method method;
        Log.d(tag, "Test deployed [weeeeeooooooeeeeeeooooooo]");
        try {
            Log.d(tag, "Method name: " + methodName);
            Log.d(tag, "Arguments: " + argTypes.length);
            method = tClass.getDeclaredMethod(methodName, argTypes);
            Object[] args = new Object[argTypes.length];
            for (int i = 0; i < argTypes.length; ++i)
                args[i] = getRandomObjectFromClass(context, argTypes[i]);
            method.invoke(tObject, args);
            return true;
        } catch (IllegalAccessException iae) {
            Log.e(tag, "OMG! No permission for " + methodName + " for class " + tClass.getName());
            return false;
        } catch (NoSuchMethodException nsme) {
            throw nsme;
        } catch (Exception e) {
            errorele(e);
            return false;
        }
    }

    public static String errorele(Exception e) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        e.printStackTrace(pw);
        Log.e("CTServerExe", "OMG", e);
        return sw.toString();
    }

    private static Object getRandomObjectFromClass(Context context, Class cl) {
        String tag = getExecutorTag();
        Log.d(tag, "Create object from class " + cl.getName());
        if (cl.equals(Boolean.class) || cl.equals(boolean.class)) {
            return (new Random()).nextInt(2) == 0;
        }
        if (cl.isPrimitive()) {
            return (new Random()).nextInt();
        }
        if (cl.equals(Byte.class)) {
            byte[] buffer = new byte[1];
            (new Random()).nextBytes(buffer);
            return buffer[0];
        }
        if (cl.equals(Character.class)) {
            byte[] buffer = new byte[1];
            (new Random()).nextBytes(buffer);
            return (char) buffer[0];
        }
        if (cl.equals(Double.class)) {
            return (new Random()).nextDouble();
        }
        if (cl.equals(Float.class)) {
            return (new Random()).nextFloat();
        }
        if (cl.equals(Integer.class)) {
            return (new Random()).nextInt();
        }
        if (cl.equals(Long.class)) {
            return (new Random()).nextLong();
        }
        if (cl.equals(Short.class)) {
            return (short) ((new Random()).nextInt());
        }
        if (cl.equals(String.class)) {
            byte[] buffer = new byte[(new Random()).nextInt(512)];
            (new Random()).nextBytes(buffer);
            return buffer.toString();
        }
        if (cl.isArray()) {
            Object[] objs = new Object[(new Random()).nextInt(10)];
            if (objs.length != 0)
                for (Object obj : objs)
                    obj = getRandomObjectFromClass(context, cl.getComponentType());
            return objs;
        }
        if (cl.equals(Context.class)) {
            return context;
        }
        try {
            Constructor[] constructors = cl.getConstructors();
            if (constructors.length == 0) {
                Log.d(tag, "No public constructor for class " + cl.getName() + "; returning null");
                return null;
            }
            int index = (new Random()).nextInt(constructors.length);
            Class[] cTypes = constructors[index].getParameterTypes();
            Object[] cArgs = new Object[cTypes.length];
            String info = "Attempting to create new instance of class " + cl.getName() + " from constructor no. " + index + ": ";
            for (Class cType : cTypes)
                info += cType.getName() + " ";
            Log.d(tag, info);
            for (int i = 0; i < cTypes.length; ++i)
                cArgs[i] = getRandomObjectFromClass(context, cTypes[i]);
            return constructors[index].newInstance(cArgs);
        } catch (Exception e) {
            // errorele(e);
            Log.d(tag, "Could not create object of class " + cl.getName() + " due to an \"" + e.getMessage() + "\" exception; returning null");
            return null;
        }
    }

    private static Object getEmptyObjectFromClass(Class cl) {
        if (cl.equals(Boolean.class)) {
            return false;
        }
        if (cl.isPrimitive()) {
            return 0;
        }
        if (cl.equals(String.class)) {
            return "";
        }
        return null;
    }

    public static Class getClassByName(String className) throws ClassNotFoundException {
        switch (className) {
            case "boolean":
                return boolean.class;
            case "byte":
                return byte.class;
            case "char":
                return char.class;
            case "double":
                return double.class;
            case "float":
                return float.class;
            case "int":
                return int.class;
            case "long":
                return long.class;
            case "short":
                return short.class;
            default:
                try {
                    return Class.forName(className);
                } catch (ClassNotFoundException cnfe) {
                    throw cnfe;
                }
        }
    }

    public static String[] getMethodArgs(Context context, String className, String methodName) throws NoSuchMethodException {
        Class c = null;
        ClassLoader loader = context.getClassLoader();
        ArrayList<String> argSets = new ArrayList<String>();
        try {
            if (className.equals("WifiService"))
                c = loader.loadClass("com.android.server.wifi.WifiService");
            else if (className.equals("AudioService"))
                c = loader.loadClass("android.media.AudioService");
            Method[] methods = c.getMethods();
            for (Method m : methods) {
                if (m.getName().equals(methodName)) {
                    Class[] parameters = m.getParameterTypes();
                    StringBuilder sb = new StringBuilder("");
                    for (Class p : parameters) {
                        sb.append(p.getName());
                        sb.append(" ");
                    }
                    argSets.add(sb.toString());
                }
            }
        } catch (ClassNotFoundException cnfe) {
            cnfe.printStackTrace();
            return null;
        }
        if (argSets.isEmpty())
            throw new NoSuchMethodException();
        return argSets.toArray(new String[argSets.size()]);
    }

    public static String[] getClassMethods(Context context, String className) throws Exception {
        ClassLoader loader = context.getClassLoader();
        try {
            Class c = null;
            if (className.equals("WifiService"))
                c = loader.loadClass("com.android.server.wifi.WifiService");
            else if (className.equals("AudioService"))
                c = loader.loadClass("android.media.AudioService");
            else
                throw new UnsupportedClassException();
            Method[] methods = c.getMethods();
            String[] result = new String[methods.length + 1];
            result[0] = "Class " + className + " methods:\n";
            for (int i = 0; i < methods.length; ++i) {
                StringBuilder sb = new StringBuilder(methods[i].getReturnType().getName());
                sb.append(" ");
                sb.append(methods[i].getName());
                sb.append("( ");
                Class[] args = methods[i].getParameterTypes();
                if (args.length == 0)
                    sb.append("void ");
                else
                    for (Class arg : args) {
                        sb.append(arg.getName());
                        sb.append(" ");
                    }
                sb.append(")\n");
                result[i + 1] = sb.toString();
            }
            return result;
        } catch (Exception e) {
            throw e;
        }
    }
}
