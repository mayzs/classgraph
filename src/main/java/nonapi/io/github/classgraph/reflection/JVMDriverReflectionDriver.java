/*
 * This file is part of ClassGraph.
 *
 * Author: Luke Hutchison
 *
 * Hosted at: https://github.com/classgraph/classgraph
 *
 * --
 *
 * The MIT License (MIT)
 *
 * Copyright (c) 2021 Luke Hutchison
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated
 * documentation files (the "Software"), to deal in the Software without restriction, including without
 * limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 * the Software, and to permit persons to whom the Software is furnished to do so, subject to the following
 * conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT
 * LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO
 * EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN
 * AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE
 * OR OTHER DEALINGS IN THE SOFTWARE.
 */
package nonapi.io.github.classgraph.reflection;

import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Reflection driver for the <a href="https://github.com/toolfactory/jvm-driver">jvm-driver</a> library, if it is
 * available at runtime. This library allows access to non-public fields and methods, circumventing encapsulation
 * and visibility controls, via a range of JVM-native tactics.
 */
class JVMDriverReflectionDriver extends ReflectionDriver {
    private Object driver;
    private final Method getDeclaredMethods;
    private final Method getDeclaredConstructors;
    private final Method getDeclaredFields;
    private final Method getField;
    private final Method setField;
    private final Method invokeMethod;
    private final Method setAccessibleMethod;

    private static interface ClassFinder {
        Class<?> findClass(String className) throws Exception;
    }

    private ClassFinder classFinder;

    JVMDriverReflectionDriver() throws Exception {
        // Construct a jvm-driver Driver instance via reflection, so that there is no runtime dependency
        final StandardReflectionDriver drv = new StandardReflectionDriver();
        //        driverClass = drv.findClass("io.github.toolfactory.jvm.Driver");
        //        Class<?> driverFactoryClass = drv.findClass("io.github.toolfactory.jvm.Driver$Factory");
        //        Object driverInstance = drv.invokeStaticMethod(drv.findMethod(driverFactoryClass, "getNew"));
        //        if (driverInstance == null) {
        //            throw new IllegalArgumentException("Could not load jvm-driver library");
        //        }
        final Class<?> driverClass = drv.findClass("io.github.toolfactory.jvm.DefaultDriver");
        driver = driverClass.newInstance();

        // Look up needed methods
        indexMethods(drv.enumerateDriverMethods(driverClass));
        getDeclaredMethods = findDriverMethod("getDeclaredMethods", Class.class);
        getDeclaredConstructors = findDriverMethod("getDeclaredConstructors", Class.class);
        getDeclaredFields = findDriverMethod("getDeclaredFields", Class.class);
        getField = findDriverMethod("getFieldValue", Object.class, Field.class);
        setField = findDriverMethod("setFieldValue", Object.class, Field.class, Object.class);
        invokeMethod = findDriverMethod("invoke", Object.class, Method.class, Object[].class);
        setAccessibleMethod = findDriverMethod("setAccessible", AccessibleObject.class, boolean.class);
        try {
            // JDK 8
            final Method forName0_method = findMethod(Class.class, "forName0", String.class, boolean.class,
                    ClassLoader.class);
            final ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
            classFinder = new ClassFinder() {
                @Override
                public Class<?> findClass(final String className) throws Exception {
                    return (Class<?>) forName0_method.invoke(null, className, true, classLoader);
                }
            };
        } catch (final Throwable t1) {
            // JDK 16
            try {
                final Method forName0_method = findMethod(Class.class, "forName0", String.class, boolean.class,
                        ClassLoader.class, Class.class);
                final ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
                classFinder = new ClassFinder() {
                    @Override
                    public Class<?> findClass(final String className) throws Exception {
                        return (Class<?>) forName0_method.invoke(null, className, true, classLoader,
                                JVMDriverReflectionDriver.class);
                    }
                };
            } catch (final Throwable t2) {
                // Fallback if the above fails: just use Class.forName. 
                // This won't find private non-exported classes in other modules.
                final Method forName_method = findMethod(Class.class, "forName", String.class);
                classFinder = new ClassFinder() {
                    @Override
                    public Class<?> findClass(final String className) throws Exception {
                        return (Class<?>) forName_method.invoke(null, className);
                    }
                };
            }
        }
    }

    @Override
    boolean makeAccessible(final AccessibleObject accessibleObject) {
        try {
            setAccessibleMethod.invoke(driver, accessibleObject, true);
        } catch (final Throwable t) {
            return false;
        }
        return true;
    }

    @Override
    Class<?> findClass(final String className) throws Exception {
        return classFinder.findClass(className);
    }

    @Override
    Method[] getDeclaredMethods(final Class<?> cls) throws Exception {
        return (Method[]) getDeclaredMethods.invoke(driver, cls);
    }

    @SuppressWarnings("unchecked")
    @Override
    <T> Constructor<T>[] getDeclaredConstructors(final Class<T> cls) throws Exception {
        return (Constructor<T>[]) getDeclaredConstructors.invoke(driver, cls);
    }

    @Override
    Field[] getDeclaredFields(final Class<?> cls) throws Exception {
        return (Field[]) getDeclaredFields.invoke(driver, cls);
    }

    @Override
    Object getField(final Object object, final Field field) throws Exception {
        return getField.invoke(driver, object, field);
    }

    @Override
    void setField(final Object object, final Field field, final Object value) throws Exception {
        setField.invoke(driver, object, field, value);
    }

    @Override
    Object getStaticField(final Field field) throws Exception {
        return getField.invoke(driver, null, field);
    }

    @Override
    void setStaticField(final Field field, final Object value) throws Exception {
        setField.invoke(driver, null, field, value);
    }

    @Override
    Object invokeMethod(final Object object, final Method method, final Object... args) throws Exception {
        return invokeMethod.invoke(driver, object, method, args);
    }

    @Override
    Object invokeStaticMethod(final Method method, final Object... args) throws Exception {
        return invokeMethod.invoke(driver, null, method, args);
    }
}