package com.smartseclab.fuzzghost;

import android.content.Context;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Parcelable;
import android.util.Log;
import android.util.SparseArray;
import android.util.SparseBooleanArray;

import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.Serializable;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import generator.shadows.BundleShadow;
import generator.shadows.FileDescriptorShadow;
import generator.shadows.ParcelableShadow;
import generator.shadows.SparseArrayShadow;
import generator.shadows.SparseBooleanArrayShadow;

/**
 * This class reads the Shadows and based on the information they contain constructs the actual
 * Android objects.
 */
public class ShadowRetriever {

    private String tag = getRetrieverTag();
    private Context context;

    private static String getRetrieverTag() {
        return MainActivity.getApplicationTag() + "Retriever";
    }

    public ShadowRetriever(Context context) {
        this.context = context;
    }

    /**
     * Read an object array and make its components Android objects (if needed).
     * @param o
     * @return
     */
    public Object[] retrieveObjectArray(Object[] o) {
        Object[] output = new Object[o.length];
        for (int i = 0; i < o.length; ++i)
            output[i] = retrieveObject(o[i]);
        return output;
    }

    /**
     * Read an object and make itself and its components Android objects (if needed).
     * @param o
     * @return
     */
    public Object retrieveObject(Object o) {
        if (o instanceof ParcelableShadow)
            return retrieveFromShadow((ParcelableShadow) o);
        if (o instanceof BundleShadow)
            return retrieveFromBundleShadow((BundleShadow) o);
        if (o instanceof FileDescriptorShadow)
            return retrieveFromFileDescriptorShadow((FileDescriptorShadow) o);
        if (o instanceof SparseArrayShadow)
            return retrieveFromSparseArrayShadow((SparseArrayShadow) o);
        if (o.getClass().isArray())
            return retrieveObjectArray((Object[]) o);
        if (o instanceof List)
            return (ArrayList) Arrays.asList(retrieveObjectArray(((ArrayList) o).toArray()));
        if (o instanceof Map)
            retrieveFromHashMap((HashMap) o);
        return o;
    }

    /**
     * If the given object is a Shadow, returns its corresponding Android class.
     * If not, simply returns its class.
     * @param o
     * @return
     * @throws ClassNotFoundException
     */
    public Class retrieveClass(Object o) throws ClassNotFoundException {
        if (o instanceof ParcelableShadow)
            return context.getClassLoader().loadClass(((ParcelableShadow) o).getName());
        if (o instanceof BundleShadow)
            return android.os.Bundle.class;
        if (o instanceof FileDescriptorShadow)
            return java.io.FileDescriptor.class;
        if (o instanceof SparseArrayShadow)
            return android.util.SparseArray.class;
        return o.getClass();
    }

    /**
     * Get all values from the Bundle Shadow and insert them into an actual Bundle.
     *
     * @param bs
     * @return
     */
    private Bundle retrieveFromBundleShadow(BundleShadow bs) {
        Bundle result = new Bundle();

        for (String key : bs.getKeys()) {
            Object value = retrieveObject(bs.getElement(key));
            Class cl = value.getClass();

            //There are (hopefully) no primitives anymore - they have been wrapped in objects.
            if (value instanceof Boolean)
                result.putBoolean(key, (Boolean) value);
            else if (value instanceof Byte)
                result.putByte(key, (Byte) value);
            else if (value instanceof Character)
                result.putChar(key, (Character) value);
            else if (value instanceof Double)
                result.putDouble(key, (Double) value);
            else if (value instanceof Float)
                result.putFloat(key, (Float) value);
            else if (value instanceof Integer)
                result.putInt(key, (Integer) value);
            else if (value instanceof Float)
                result.putFloat(key, (Float) value);
            else if (value instanceof Long)
                result.putLong(key, (Long) value);
            else if (value instanceof Short)
                result.putShort(key, (Short) value);

            else if (cl.isArray())
                putArrayIntoBundle(result, key, value);
            else if (value instanceof List)
                putListIntoBundle(result, key, value);

            else if (value instanceof String)
                result.putString(key, (String) value);
            else if (value instanceof CharSequence)
                result.putCharSequence(key, (CharSequence) value);
            else if (value instanceof Serializable)
                result.putSerializable(key, (Serializable) value);

            else if (value instanceof SparseArrayShadow)
                result.putSparseParcelableArray(key, retrieveFromSparseArrayShadow((SparseArrayShadow) value));
            else if (value instanceof ParcelableShadow)
                putParcelableShadowIntoBundle(result, key, value);
            else if (value instanceof BundleShadow)
                result.putBundle(key, (Bundle) retrieveFromBundleShadow((BundleShadow) value));

        }
        return result;
    }

    /**
     * Retrieve a ParcelableShadow and insert it into the Bundle.
     *
     * @param result
     * @param key
     * @param value
     */
    private void putParcelableShadowIntoBundle(Bundle result, String key, Object value) {
        if (value instanceof ParcelableShadow) {
            ParcelableShadow ps = (ParcelableShadow) value;
            switch (ps.getName()) {
                case "android.os.Binder":
                case "android.os.IBinder":
                    result.putBinder(key, (Binder) retrieveFromShadow(ps));
                    break;
                default:
                    result.putParcelable(key, (Parcelable) retrieveFromShadow(ps));
                    break;
            }
        }
    }

    /**
     * Insert an array into the Bundle. Primitive types might have got wrapped during the generation.
     * Thus, it is necessary to unwrap them. The code is not very sparse - this could be made shorter
     * if reflection was used; such thing would however affect the performance.
     *
     * @param result
     * @param key
     * @param value
     */
    private void putArrayIntoBundle(Bundle result, String key, Object value) {
        Class cl = value.getClass();
        if (cl.isArray()) {
            Class type = cl.getComponentType();
            if (type.equals(boolean.class))
                result.putBooleanArray(key, (boolean[]) value);
            else if (Boolean.class.isAssignableFrom(type)) {
                Boolean[] values = (Boolean[]) value;
                boolean[] array = new boolean[values.length];
                for (int i = 0; i < values.length; ++i)
                    array[i] = values[i];
                result.putBooleanArray(key, array);
            } else if (type.equals(byte.class))
                result.putByteArray(key, (byte[]) value);
            else if (Byte.class.isAssignableFrom(type)) {
                Byte[] values = (Byte[]) value;
                byte[] array = new byte[values.length];
                for (int i = 0; i < values.length; ++i)
                    array[i] = values[i];
                result.putByteArray(key, array);
            } else if (type.equals(char.class))
                result.putCharArray(key, (char[]) value);
            else if (Character.class.isAssignableFrom(type)) {
                Character[] values = (Character[]) value;
                char[] array = new char[values.length];
                for (int i = 0; i < values.length; ++i)
                    array[i] = values[i];
                result.putCharArray(key, array);
            } else if (type.equals(double.class))
                result.putDoubleArray(key, (double[]) value);
            else if (Double.class.isAssignableFrom(type)) {
                Double[] values = (Double[]) value;
                double[] array = new double[values.length];
                for (int i = 0; i < values.length; ++i)
                    array[i] = values[i];
                result.putDoubleArray(key, array);
            } else if (type.equals(float.class))
                result.putFloatArray(key, (float[]) value);
            else if (Float.class.isAssignableFrom(type)) {
                Float[] values = (Float[]) value;
                float[] array = new float[values.length];
                for (int i = 0; i < values.length; ++i)
                    array[i] = values[i];
                result.putFloatArray(key, array);
            } else if (type.equals(int.class))
                result.putIntArray(key, (int[]) value);
            else if (Integer.class.isAssignableFrom(type)) {
                Integer[] values = (Integer[]) value;
                int[] array = new int[values.length];
                for (int i = 0; i < values.length; ++i)
                    array[i] = values[i];
                result.putIntArray(key, array);
            } else if (type.equals(long.class))
                result.putLongArray(key, (long[]) value);
            else if (Long.class.isAssignableFrom(type)) {
                Long[] values = (Long[]) value;
                long[] array = new long[values.length];
                for (int i = 0; i < values.length; ++i)
                    array[i] = values[i];
                result.putLongArray(key, array);
            } else if (type.equals(short.class))
                result.putShortArray(key, (short[]) value);
            else if (Short.class.isAssignableFrom(type)) {
                Short[] values = (Short[]) value;
                short[] array = new short[values.length];
                for (int i = 0; i < values.length; ++i)
                    array[i] = values[i];
                result.putShortArray(key, array);
            } else if (String.class.isAssignableFrom(type))
                result.putStringArray(key, (String[]) value);
            else if (CharSequence.class.isAssignableFrom(type))
                result.putCharSequenceArray(key, (CharSequence[]) value);
            else if (type.equals(ParcelableShadow.class))
                result.putParcelableArray(key, (Parcelable[]) retrieveObjectArray((Object[]) value));
        }
    }

    /**
     * Retrieve List Shadows and insert them into the Bundle.
     *
     * @param result
     * @param key
     * @param value
     */
    private void putListIntoBundle(Bundle result, String key, Object value) {
        if (value instanceof List) {
            ArrayList<Object> al = (ArrayList) value;
            if (al.isEmpty())
                result.putParcelableArrayList(key, (ArrayList) value);
            else {
                Object element = al.get(0);
                if (element instanceof Integer)
                    result.putIntegerArrayList(key, (ArrayList) value);
                else if (element instanceof ParcelableShadow) {
                    result.putParcelableArrayList(key, (ArrayList) Arrays.asList((Parcelable[]) retrieveObjectArray(((ArrayList) al).toArray())));
                } else if (element instanceof String)
                    result.putStringArrayList(key, (ArrayList) value);
                else if (element instanceof CharSequence)
                    result.putCharSequenceArrayList(key, (ArrayList) value);
            }
        }
    }

    /**
     * Tries to create a File Descriptor of a file named by fileName field in the File Descriptor
     * Shadow. Returns an invalid descriptor (file stream is closed before the return statement).
     * If no valid file found, returns an empty invalid File Descriptor.
     *
     * @param fds
     * @return
     */
    private FileDescriptor retrieveFromFileDescriptorShadow(FileDescriptorShadow fds) {
        try {
            FileInputStream fis = new FileInputStream(new File(fds.getFileName()));
            fis.close();
            return fis.getFD();
        } catch (Exception e) {
            return new FileDescriptor();
        }
    }

    /**
     * Reads a Sparse Array Shadow and retrieves its content.
     *
     * @param sas
     * @return
     */
    private SparseArray retrieveFromSparseArrayShadow(SparseArrayShadow sas) {
        SparseArray sa = new SparseArray();
        for (int i = 0; i < sas.size(); ++i)
            sa.put(sas.keyAt(i), retrieveObject(sas.valueAt(i)));
        return sa;
    }

    /**
     * Reads a Sparse Boolean Array Shadow and retrieves its content.
     *
     * @param sbas
     * @return
     */
    private SparseBooleanArray readFromSparseBooleanArrayShadow(SparseBooleanArrayShadow sbas) {
        SparseBooleanArray sba = new SparseBooleanArray();
        for (int i = 0; i < sbas.size(); ++i)
            sba.put(sbas.keyAt(i), sbas.valueAt(i));
        return sba;
    }

    /**
     * Converts all Hash Map objecys into valid Android stuff if needed.
     * @param map
     * @return
     */
    private void retrieveFromHashMap(HashMap map) {
        for (Object key : map.keySet())
            map.put(key, retrieveObject(map.get(key)));
    }

    /**
     * Reads a Parcelable Shadow and retrieves its content.
     *
     * @param ps
     * @return
     */
    private Object retrieveFromShadow(ParcelableShadow ps) {
        Object instance;
        Log.d(tag, "Reading from shadow: " + ps.getName());
        try {
            Class objType = context.getClassLoader().loadClass(ps.getName());
            if (IBinder.class.isAssignableFrom(objType))
                return new Binder(); //empty binder might not be enough though
            if (objType.isInterface())
                return getEmptyInterfaceProxy(objType);
            instance = readFromConstructors(ps, objType);
        } catch (ClassNotFoundException cnfe) {
            Log.e(tag, "OMG", cnfe);
            return null;
        }
        for (String key : ps.getKeys()) {
            try {
                Field field = instance.getClass().getField(key);
                field.setAccessible(true);
                field.set(instance, retrieveObject(ps.getDatum(key)));
            } catch (NoSuchFieldException nsfe) {
                Log.d(tag, "No such field: " + key + " in " + ps.getName() + "; omitting.");
            } catch (IllegalAccessException iae) {
                Log.e(tag, iae.getMessage(), iae);
            }
        }
        return instance;
    }

    /**
     * Returns an empty Proxy (all methods do nothing and return null) of a given interface.
     *
     * @param objType
     * @return
     */
    private Object getEmptyInterfaceProxy(Class objType) {
        InvocationHandler handler = new InvocationHandler() {
            @Override
            public Object invoke(Object proxy, Method method, Object[] args)
                    throws Throwable {
                return null;
            }
        };
        return Proxy.newProxyInstance(objType.getClassLoader(),
                new Class[]{objType}, handler);
    }

    /**
     * Use trickier methods than calling the constructor to obtain the Parcelable object.
     * At present, this method just returns null.
     * @param ps
     * @return
     */
    /**
     * TODO: Handle as many possible pathologies as you can. This will probably require storing a "how to get" field in Shadow.
     */
    private Object readFromPathologicalShadow(ParcelableShadow ps) {
        return null;
    }

    /**
     * Reads and retrieves the Shadow's constructor args. Then, looks for an appropriate constructor
     * and calls it to create an Android object instance.
     *
     * @param ps
     * @param objType
     * @return
     */
    private Object readFromConstructors(ParcelableShadow ps, Class objType) {
        Constructor[] constructors = objType.getDeclaredConstructors();
        if (constructors.length == 0) {
            Log.d(tag, "No constructors of class " + ps.getName() + " found.");
            return readFromPathologicalShadow(ps);
        } else {
            Object[] constructorArgs = retrieveObjectArray(ps.getConstructorArgs());
            try {
                Constructor constructor = getConstructorFromArgs(constructors, constructorArgs);
                constructor.setAccessible(true);
                return constructor.newInstance(constructorArgs);
            } catch (NoSuchMethodException nsme) {
                Log.d(tag, "Invalid constructor arguments for class " + objType.getName() + "; returning null.");
                return null;
            } catch (Exception e) {
                Log.e(tag, "OMG", e);
                return null;
            }
        }
    }

    /**
     * Knowing arguments and having a Constructor array, get an appropriate Constructor.
     *
     * @param constructors
     * @param constructorArgs
     * @return
     * @throws NoSuchMethodException
     */
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
}