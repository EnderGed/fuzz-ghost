package com.smartseclab.fuzzghost;

import android.content.Context;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.util.SparseArray;
import android.util.SparseBooleanArray;

import java.io.FileDescriptor;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.List;

import generator.shadows.BundleShadow;
import generator.shadows.FileDescriptorShadow;
import generator.shadows.ParcelableShadow;
import generator.shadows.SparseArrayShadow;
import generator.shadows.SparseBooleanArrayShadow;

/**
 * This class reads the Shadows and based on the information they contain constructs the actual
 * Android objects.
 */
/*
TODO: Write this class.
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

    public Object readObject(Object o) {
        if (o instanceof ParcelableShadow)
            return readFromShadow((ParcelableShadow) o);
        if (o instanceof BundleShadow)
            return readFromBundleShadow((BundleShadow) o);
        if (o instanceof FileDescriptorShadow)
            return readFromFileDescriptorShadow((FileDescriptorShadow) o);
        if (o instanceof SparseArrayShadow)
            return readFromSparseArrayShadow((SparseArrayShadow) o);
        return o;
    }

    public Class readClass(Object o) throws ClassNotFoundException {
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

    /*
    TODO: Fix this. This requires rewriting of BundleShadow. (perhaps the key should contain the type name?)
     */
    public Bundle readFromBundleShadow(BundleShadow bs) {
        Bundle result = new Bundle();

        for(String key : bs.getKeys()) {
            Object value = readObject(bs.getElement(key));
            Class cl = value.getClass();/*
            if(value instanceof Binder)
                result.putBinder(key, (Binder)value);
            if(value instanceof Boolean)
                result.putBoolean(key, (Boolean)value);
            if(value instanceof boolean[])
                result.putBooleanArray(key, (boolean[])value);
            if(value instanceof Bundle)
                result.putBundle(key, (Bundle)value);
            if(value instanceof Byte)
                result.putByte(key, (Byte)value);
            if(value instanceof byte[])
                result.putByteArray(key, (byte[])value);
           // if(value instanceof char)
            //    result.putChar(key, (Char)value);
            if(value instanceof char[])
                result.putCharArray(key, (char[])value);
            if(value instanceof CharSequence)
                result.putCharSequence(key, (CharSequence)value);
            if(value instanceof CharSequence[])
                result.putCharSequenceArray(key, (CharSequence[])value);
            if(value instanceof List){
                result.putCharSequenceArrayList(key, (List<CharSequence>)value);
                if(value instanceof List<Integer>)
                    result.putIntegerArrayList(key, (List<Integer>)value);
            }
            if(value instanceof Double)
                result.putDouble(key, (Double)value);
            if(value instanceof Double[])
                result.putDoubleArray(key, (Double[])value);
            if(value instanceof Float)
                result.putFloat(key, (Float)value);
            if(value instanceof Float[])
                result.putFloatArray(key, (Float[])value);
            if(value instanceof IBinder)
                result.putIBinder(key, (IBinder)value);
            if(value instanceof Int)
                result.putInt(key, (Int)value);
            if(value instanceof Int[])
                result.putIntArray(key, (Int[])value);
            if(value instanceof List<Integer>)
                result.putIntegerArrayList(key, (List<Integer>)value);
            if(value instanceof Long)
                result.putLong(key, (Long)value);
            if(value instanceof Long[])
                result.putLongArray(key, (Long[])value);
            if(value instanceof Parcelable)
                result.putParcelable(key, (Parcelable)value);
            if(value instanceof Parcelable[])
                result.putParcelableArray(key, (Parcelable[])value);
            if(value instanceof ParcelableArrayList)
                result.putParcelableArrayList(key, (ParcelableArrayList)value);
            if(value instanceof Serializable)
                result.putSerializable(key, (Serializable)value);
            if(value instanceof Short)
                result.putShort(key, (Short)value);
            if(value instanceof Short[])
                result.putShortArray(key, (Short[])value);
            if(value instanceof SparseParcelable[])
                result.putSparseParcelableArray(key, (SparseParcelable[])value);
            if(value instanceof String)
                result.putString(key, (String)value);
            if(value instanceof String[])
                result.putStringArray(key, (String[])value);
            if(value instanceof StringArrayList)
                result.putStringArrayList(key, (StringArrayList)value);*/
        }
        return result;
    }

    public FileDescriptor readFromFileDescriptorShadow(FileDescriptorShadow fds) {
        return null;
    }

    public SparseArray readFromSparseArrayShadow(SparseArrayShadow sas) {
        return null;
    }

    public SparseBooleanArray readFromSparseBooleanArrayShadow(SparseBooleanArrayShadow sbas) {
        return null;
    }

    public Object readFromShadow(ParcelableShadow ps) {
        Object instance;
        Log.d(tag, "Reading from shadow: " + ps.getName());
        try {
            Class objType = context.getClassLoader().loadClass(ps.getName());
            if (IBinder.class.isAssignableFrom(objType))
                return new Binder(); //empty binder might not be enough though
            if (objType.isInterface())
                return getInterfaceProxy(objType);
            instance = readFromConstructors(ps, objType);
        } catch (ClassNotFoundException cnfe) {
            Log.e(tag, "OMG", cnfe);
            return null;
        }
        for (String key : ps.getKeys()) {
            try {
                Field field = instance.getClass().getField(key);
                field.setAccessible(true);
                field.set(instance, ps.getDatum(key));
            } catch (NoSuchFieldException nsfe) {
                Log.d(tag, "No such field: " + key + " in " + ps.getName() + "; omitting.");
            } catch (IllegalAccessException iae) {
                Log.e(tag, iae.getMessage(), iae);
            }
        }
        return instance;
    }

    private Object getInterfaceProxy(Class objType) {
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

    //TODO: Handle as many possible pathologies as you can. This will probably require storing a "how to get" field in Shadow.
    private Object readFromPathologicalShadow(ParcelableShadow ps) {
        return null;
    }

    private Object[] argsFromShadows(ParcelableShadow ps) {
        Object[] constructorArgs = ps.getConstructorArgs();
        for (int i = 0; i < constructorArgs.length; ++i)
            if (constructorArgs[i] instanceof ParcelableShadow)
                constructorArgs[i] = readFromShadow((ParcelableShadow) constructorArgs[i]);
        return constructorArgs;
    }

    private Object readFromConstructors(ParcelableShadow ps, Class objType) {
        Constructor[] constructors = objType.getDeclaredConstructors();
        if (constructors.length == 0) {
            Log.d(tag, "No constructors of class " + ps.getName() + " found.");
            return readFromPathologicalShadow(ps);
        } else {
            Object[] constructorArgs = argsFromShadows(ps);
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
