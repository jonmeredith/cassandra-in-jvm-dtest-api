/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.cassandra.distributed.shared;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Arrays;
import java.util.function.Predicate;

public class InstanceClassLoader extends URLClassLoader
{
    private static final Predicate<String> sharePackage = name ->
                                                          name.startsWith("org.apache.cassandra.distributed.api.")
                                                          || name.startsWith("org.apache.cassandra.distributed.shared.")
                                                          || name.startsWith("sun.")
                                                          || name.startsWith("oracle.")
                                                          || name.startsWith("com.intellij.")
                                                          || name.startsWith("com.sun.")
                                                          || name.startsWith("com.oracle.")
                                                          || name.startsWith("java.")
                                                          || name.startsWith("javax.")
                                                          || name.startsWith("jdk.")
                                                          || name.startsWith("netscape.")
                                                          || name.startsWith("org.xml.sax.");

    private volatile boolean isClosed = false;
    private final URL[] urls;
    private final int generation; // used to help debug class loader leaks, by helping determine which classloaders should have been collected
    private final int id;
    private final ClassLoader sharedClassLoader;

    public InstanceClassLoader(int generation, int id, URL[] urls, ClassLoader sharedClassLoader)
    {
        super(urls, null);
        this.urls = urls;
        this.sharedClassLoader = sharedClassLoader;
        this.generation = generation;
        this.id = id;
    }

    public int getClusterGeneration()
    {
        return generation;
    }

    public int getInstanceId()
    {
        return id;
    }

    @Override
    public Class<?> loadClass(String name) throws ClassNotFoundException
    {
        if (sharePackage.test(name))
            return sharedClassLoader.loadClass(name);

        return loadClassInternal(name);
    }

    Class<?> loadClassInternal(String name) throws ClassNotFoundException
    {
        if (isClosed)
            throw new IllegalStateException(String.format("Can't load %s. Instance class loader is already closed.", name));

        synchronized (getClassLoadingLock(name))
        {
            // First, check if the class has already been loaded
            Class<?> c = findLoadedClass(name);

            if (c == null)
                c = findClass(name);

            return c;
        }
    }

    /**
     * @return true iff this class was loaded by an InstanceClassLoader, and as such is used by a dtest node
     */
    public static boolean wasLoadedByAnInstanceClassLoader(Class<?> clazz)
    {
        return clazz.getClassLoader().getClass().getName().equals(InstanceClassLoader.class.getName());
    }

    public String toString()
    {
        return "InstanceClassLoader{" +
               "generation=" + generation +
               ", id = " + id +
               ", urls=" + Arrays.toString(urls) +
               '}';
    }

    public void close() throws IOException
    {
        isClosed = true;
        super.close();
    }
}
