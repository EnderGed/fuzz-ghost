package com.smartseclab.fuzzghost;

import android.content.Context;
import android.os.Binder;
import android.os.IBinder;
import android.support.v4.util.ArrayMap;
import android.util.Log;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;
import java.util.logging.Handler;

import generator.shadows.ParcelableShadow;
import generator.shadows.TypeVal;

/**
 * The actual executor of server commands.
 */
public class TestExecutor {

    private Class<?> tClass;
    private Object tObject;
    private Context context;
    private String tag = getExecutorTag();
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

    public TestExecutor(Context context, String className) throws UnsupportedClassException, Exception {
        ClassLoader loader = context.getClassLoader();
        if (!classMap.containsKey(className)) {
            Log.e(tag, "OMG! Unsupported class name: " + className);
            throw new UnsupportedClassException();
        }
        this.context = context;
        Class manager = loader.loadClass(classMap.get(className));
        Method get = manager.getDeclaredMethod("getService", new Class[0]);
        get.setAccessible(true);
        tObject = get.invoke(manager);
        tClass = tObject.getClass();
        Log.d(tag, "Object of class " + tClass.getName() + " has been successfully created.");
    }

    public boolean runMethod(String methodName, Class[] argTypes, Object[] args) throws NoSuchMethodException {
        if (argTypes.length != args.length) {
            Log.e(tag, "Arguments length does not equal types length; terminating.");
            return false;
        }
        Log.d(tag, "Test deployed. *weeeeeooooooeeeeeeooooooo*");
        try {
            Log.d(tag, "Method name: " + methodName);
            Log.d(tag, "Arguments: " + argTypes.length);
            ClassLoader loader = context.getClassLoader();
            for (int i = 0; i < argTypes.length; ++i) {
                if (args[i] instanceof ParcelableShadow) {
                    ParcelableShadow shadow = (ParcelableShadow) args[i];
                    argTypes[i] = loader.loadClass(shadow.getName());
                    args[i] = readFromShadow(shadow);
                }
                Log.d(tag, argTypes[i].getName() + " " + args[i]);
            }
            Method method = tClass.getDeclaredMethod(methodName, argTypes);
            method.setAccessible(true);
            method.invoke(tObject, args);
            return true;
        } catch (IllegalAccessException iae) {
            Log.e(tag, "No permission for " + methodName + " for class " + tClass.getName());
            return false;
        } catch (NoSuchMethodException nsme) {
            throw nsme;
        } catch (Exception e) {
            Log.e(tag, e.getMessage(), e);
            return false;
        }
    }

    private Object readFromShadow(ParcelableShadow ps) {
        Object instance;
        Log.d(tag, "Reading from shadow: " + ps.getName());
        try {
            Class objType = context.getClassLoader().loadClass(ps.getName());
            if (IBinder.class.isAssignableFrom(objType)) {
                return new Binder(); //empty binder might not be enough
            }
            if (objType.isInterface()) {
                InvocationHandler handler = new InvocationHandler() {
                    @Override
                    public Object invoke(Object proxy, Method method, Object[] args)
                            throws Throwable {
                        //This does nothing; should it do anything?
                        return null;
                    }
                };
                return Proxy.newProxyInstance(objType.getClassLoader(),
                        new Class[]{objType}, handler);
            }
            instance = readFromConstructors(ps, objType);
        } catch (ClassNotFoundException cnfe) {
            Log.e(tag, "OMG", cnfe);
            return null;
        }
        for (String key : ps.getKeys()) {
            try {
                Field field = instance.getClass().getField(key);
                field.setAccessible(true);
                field.set(instance, ps.getDatum(key).value);
            } catch (NoSuchFieldException nsfe) {
                Log.d(tag, "No such field: " + key + " in " + ps.getName() + "; omitting.");
            } catch (IllegalAccessException iae) {
                Log.e(tag, iae.getMessage(), iae);
            }
        }
        return instance;
    }

    private Object[] argsFromShadows(ParcelableShadow ps){
        Object[] constructorArgs = ps.getConstructorArgs();
        for (int i = 0; i < constructorArgs.length; ++i)
            if (constructorArgs[i] instanceof ParcelableShadow)
                constructorArgs[i] = readFromShadow((ParcelableShadow) constructorArgs[i]);
        return constructorArgs;
    }

    private Object readFromConstructors(ParcelableShadow ps, Class objType){
        Constructor[] constructors = objType.getDeclaredConstructors();
        if (constructors.length == 0) {
            Log.d(tag, "No constructors of class " + ps.getName() + " found.");
            return readFromPathologicalShadow(ps);
        } else {
            Object[] constructorArgs = argsFromShadows(ps);
            try {
                Constructor constructor =  getConstructorFromArgs(constructors, constructorArgs);
                constructor.setAccessible(true);
                return constructor.newInstance(constructorArgs);
            }
            catch(NoSuchMethodException nsme){
                Log.d(tag, "Invalid constructor arguments for class " + objType.getName() + "; returning null.");
                return null;
            }
            catch (Exception e) {
                Log.e(tag, "OMG", e);
                return null;
            }
        }
    }

    private Constructor getConstructorFromArgs(Constructor[] constructors, Object[] constructorArgs) throws NoSuchMethodException {
        for (Constructor c : constructors) {
            boolean ok = true;
            Class[] paramTypes = c.getParameterTypes();
            if (paramTypes.length != constructorArgs.length)
                continue;
            else
                for (int i = 0; i < constructorArgs.length && ok; ++i)
                    if (!paramTypes[i].isAssignableFrom(constructorArgs[i].getClass()))
                        ok = false;
            if (ok)
                return c;
        }
        throw new NoSuchMethodException();
    }

    //TODO: Handle as many possible pathologies as you can. This will probably require storing a "how to get" field in Shadow.
    private Object readFromPathologicalShadow(ParcelableShadow ps) {
        return null;
    }

    public static String[] getMethodArgs(Context context, String className, String methodName) throws NoSuchMethodException {
        ArrayList<String> argSets = new ArrayList<String>();
        try {
            Class c = context.getClassLoader().loadClass(classMap.get(className));
            Method[] methods = c.getDeclaredMethods();
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
            if (classMap.containsKey(className))
                c = loader.loadClass(classMap.get(className));
            else
                throw new UnsupportedClassException();

            Method[] methods = c.getDeclaredMethods();
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

    public static boolean knowsClass(String className) {
        return classMap.containsKey(className);
    }
}
